#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
#  setup-kiosk.sh — Configura un sistema Debian 13+ (Trixie) come kiosk
#
#  Eseguire da root su un'installazione Debian 13 MINIMALE (no desktop).
#  Lo script:
#    1. Installa Java 17, Maven, Cage, driver GPU/touch
#    2. Crea l'utente "kiosk" (non-root, senza password)
#    3. Compila e installa l'app in /opt/kiosk
#    4. Configura auto-login su TTY1 → Cage → App
#    5. GRUB con 2 secondi di timeout per accesso recovery
#
#  Uso:
#    scp -r <progetto> root@target:/tmp/kiosk-app
#    ssh root@target 'bash /tmp/kiosk-app/setup-kiosk.sh'
#
#  Per manutenzione: al boot premi un tasto in GRUB → recovery mode
#  oppure ssh root@<ip> se la rete è configurata
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

# ─── Configurazione ──────────────────────────────────────────────────
KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"  # directory di questo script (= il progetto)

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[kiosk]${NC} $*"; }
ok()    { echo -e "${GREEN}[  OK ]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

# ─── Verifica root ────────────────────────────────────────────────────
[[ $EUID -eq 0 ]] || fail "Questo script deve essere eseguito come root."

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║           KIOSK SETUP — Debian 13+ (Trixie)             ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# ═══════════════════════════════════════════════════════════════════════
#  1. PACCHETTI DI SISTEMA
# ═══════════════════════════════════════════════════════════════════════
info "Aggiornamento pacchetti..."
apt-get update -qq

info "Installazione pacchetti obbligatori..."
apt-get install -y --no-install-recommends \
    default-jre \
    cage \
    libgl1-mesa-dri \
    libinput-tools \
    dbus \
    dbus-user-session \
    libgtk-3-0 \
    xwayland \
    fonts-dejavu-core \
    fonts-noto-color-emoji \
    ca-certificates \
    curl \
    pciutils

info "Installazione pacchetti opzionali (VMware/VirtualBox)..."
for pkg in open-vm-tools virtualbox-guest-utils unclutter; do
    apt-get install -y --no-install-recommends "$pkg" 2>/dev/null \
        && info "  Installato: $pkg" \
        || info "  Non disponibile (ignorato): $pkg"
done

# ═══════════════════════════════════════════════════════════════════════
#  1b. DRIVER GPU — rilevamento automatico hardware
# ═══════════════════════════════════════════════════════════════════════
info "Rilevamento GPU in corso..."
GPU_VENDOR=""
if lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio"; then
    GPU_VENDOR="vm"
elif lspci 2>/dev/null | grep -qi "nvidia"; then
    GPU_VENDOR="nvidia"
elif lspci 2>/dev/null | grep -qi "intel.*graphics\|intel.*uhd\|intel.*hd\|intel.*iris"; then
    GPU_VENDOR="intel"
elif lspci 2>/dev/null | grep -qi "amd\|ati\|radeon"; then
    GPU_VENDOR="amd"
fi

info "GPU rilevata: ${GPU_VENDOR:-sconosciuta}"

case "$GPU_VENDOR" in
    nvidia)
        info "Installazione driver NVIDIA..."
        # Abilita contrib e non-free per firmware NVIDIA
        apt-get install -y --no-install-recommends \
            nvidia-driver nvidia-vulkan-icd 2>/dev/null \
        || apt-get install -y --no-install-recommends \
            xserver-xorg-video-nouveau mesa-vulkan-drivers 2>/dev/null \
        || info "  Driver NVIDIA non installati — usando nouveau/software"
        ok "Driver NVIDIA configurati."
        ;;
    intel)
        info "Installazione driver Intel..."
        apt-get install -y --no-install-recommends \
            xserver-xorg-video-intel \
            intel-media-va-driver \
            mesa-vulkan-drivers \
            libvulkan1 2>/dev/null \
        || apt-get install -y --no-install-recommends \
            mesa-vulkan-drivers libvulkan1 2>/dev/null \
        || true
        ok "Driver Intel configurati."
        ;;
    amd)
        info "Installazione driver AMD..."
        apt-get install -y --no-install-recommends \
            xserver-xorg-video-amdgpu \
            firmware-amd-graphics \
            mesa-vulkan-drivers \
            libvulkan1 2>/dev/null \
        || apt-get install -y --no-install-recommends \
            mesa-vulkan-drivers libvulkan1 2>/dev/null \
        || true
        ok "Driver AMD configurati."
        ;;
    vm)
        info "VM rilevata — verrà usato renderer software (nessun driver GPU necessario)."
        ;;
    *)
        info "GPU sconosciuta — installazione driver generici Mesa..."
        apt-get install -y --no-install-recommends \
            mesa-vulkan-drivers libvulkan1 2>/dev/null || true
        ;;
esac

ok "Pacchetti installati."

# ═══════════════════════════════════════════════════════════════════════
#  2. UTENTE KIOSK
# ═══════════════════════════════════════════════════════════════════════
if id "$KIOSK_USER" &>/dev/null; then
    info "Utente '$KIOSK_USER' già esistente."
else
    info "Creazione utente '$KIOSK_USER'..."
    useradd -m -s /bin/bash -G video,input,render "$KIOSK_USER"
    # Nessuna password — l'utente non può fare login interattivo da console
    passwd -d "$KIOSK_USER"
    ok "Utente '$KIOSK_USER' creato."
fi

# ═══════════════════════════════════════════════════════════════════════
#  3. COPIA APP (JAR pre-compilato + JavaFX bundled)
# ═══════════════════════════════════════════════════════════════════════
info "Copia dell'app in $APP_DIR..."
mkdir -p "$APP_DIR/lib"

# Copia jar principale
cp "$SRC_DIR/dist/demo-1.jar" "$APP_DIR/"

# Copia le librerie JavaFX bundled
cp "$SRC_DIR/dist/lib/"*.jar "$APP_DIR/lib/"

chown -R "$KIOSK_USER:$KIOSK_USER" "$APP_DIR"
ok "App copiata in $APP_DIR."

# ═══════════════════════════════════════════════════════════════════════
#  3b. TEMA CURSORE TRASPARENTE
# ═══════════════════════════════════════════════════════════════════════
info "Creazione tema cursore trasparente..."
mkdir -p /usr/share/icons/blank-cursor/cursors

# Genera un file XCursor 1x1 completamente trasparente con Python3
python3 - << 'PYEOF'
import struct, os

# Formato XCursor: file_header + TOC + image_chunk
# File header (16 bytes)
magic       = b'Xcur'
header_size = struct.pack('<I', 16)
file_ver    = struct.pack('<I', 0x10000)
ntoc        = struct.pack('<I', 1)

# Chunk per immagine 1x1 trasparente
CHUNK_TYPE    = 0xFFFD0002
nominal_size  = 24
chunk_offset  = 16 + 12  # dopo header (16) e una voce TOC (12)

# TOC entry (12 bytes)
toc = struct.pack('<III', CHUNK_TYPE, nominal_size, chunk_offset)

# Image chunk (9 * uint32 + 1 pixel ARGB)
img = struct.pack('<IIIIIIIII',
    9 * 4,         # header_size del chunk
    CHUNK_TYPE,    # type
    nominal_size,  # subtype = dimensione nominale
    1,             # version
    1,             # width
    1,             # height
    0,             # xhot
    0,             # yhot
    50,            # delay (ms)
) + b'\x00\x00\x00\x00'  # 1 pixel ARGB trasparente

data = magic + header_size + file_ver + ntoc + toc + img

# Cursore di base
cursor_path = '/usr/share/icons/blank-cursor/cursors/left_ptr'
with open(cursor_path, 'wb') as f:
    f.write(data)

# Symlink per tutti i nomi cursore comuni
names = [
    'default', 'arrow', 'pointer', 'hand', 'hand1', 'hand2',
    'crosshair', 'cross', 'text', 'xterm', 'wait', 'watch',
    'grabbing', 'grab', 'fleur', 'move',
    'n-resize', 's-resize', 'e-resize', 'w-resize',
    'ne-resize', 'nw-resize', 'se-resize', 'sw-resize',
    'col-resize', 'row-resize', 'all-scroll',
    'X_cursor', 'right_ptr', 'top_left_arrow',
]
for name in names:
    dst = f'/usr/share/icons/blank-cursor/cursors/{name}'
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Tema cursore trasparente creato.')
PYEOF

# index.theme necessario perché Wayland lo cerchi
cat > /usr/share/icons/blank-cursor/index.theme << 'THEME'
[Icon Theme]
Name=blank-cursor
Comment=Transparent cursor theme
Inherits=hicolor
THEME

ok "Tema cursore trasparente installato."

# ═══════════════════════════════════════════════════════════════════════
#  4. SCRIPT DI LANCIO
# ═══════════════════════════════════════════════════════════════════════
info "Creazione script di lancio..."

cat > "$APP_DIR/run-kiosk.sh" << 'LAUNCHER'
#!/usr/bin/env bash
# Avvio kiosk app — Java + JavaFX bundled (Wayland/Cage)
exec java \
    --module-path /opt/kiosk/lib \
    --add-modules javafx.controls,javafx.fxml \
    -Djava.awt.headless=false \
    -Dprism.order=es2,sw \
    -Dprism.verbose=false \
    -Dglass.platform=gtk \
    -Djdk.gtk.version=3 \
    -Djavafx.animation.fullspeed=true \
    -cp /opt/kiosk/demo-1.jar \
    com.example.App
LAUNCHER
chmod +x "$APP_DIR/run-kiosk.sh"
chown "$KIOSK_USER:$KIOSK_USER" "$APP_DIR/run-kiosk.sh"

# ═══════════════════════════════════════════════════════════════════════
#  5. AUTO-LOGIN + CAGE AL BOOT
# ═══════════════════════════════════════════════════════════════════════
info "Configurazione auto-login su TTY1..."

# Override systemd getty per auto-login sull'utente kiosk
mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin $KIOSK_USER --noclear %I \$TERM
EOF

info "Configurazione .bash_profile per avviare Cage..."

# Il .bash_profile dell'utente kiosk avvia Cage solo su TTY1
KIOSK_HOME=$(eval echo ~$KIOSK_USER)
cat > "$KIOSK_HOME/.bash_profile" << 'PROFILE'
# Se siamo su TTY1 (auto-login), avvia il kiosk
if [ "$(tty)" = "/dev/tty1" ]; then
    # 2 secondi silenzioso: premi Ctrl+C per entrare nel terminale
    sleep 2 || true

    # Imposta XDG_RUNTIME_DIR (necessario per Wayland/Cage)
    export XDG_RUNTIME_DIR="/run/user/$(id -u)"
    mkdir -p "$XDG_RUNTIME_DIR"
    chmod 0700 "$XDG_RUNTIME_DIR"

    # Cursore trasparente a livello di sistema (Wayland/Cage)
    export XCURSOR_THEME=blank-cursor
    export XCURSOR_SIZE=24

    LOG="/opt/kiosk/kiosk.log"
    echo "[$(date)] Avvio cage..." >> "$LOG"

    # Rileva se siamo su VM (VMware/VirtualBox) e forza software rendering
    if lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio"; then
        export LIBGL_ALWAYS_SOFTWARE=1
        export WLR_RENDERER=pixman
        export WLR_RENDERER_ALLOW_SOFTWARE=1
        export WLR_NO_HARDWARE_CURSORS=1
        echo "[$(date)] VM rilevata — renderer software attivato" >> "$LOG"
    fi

    # Loop di riavvio automatico 24/7
    # Ctrl+Alt+H nell'app crea /opt/kiosk/.stop → uscita pulita senza restart
    rm -f /opt/kiosk/.stop
    RESTART_DELAY=3

    while true; do
        # Rotazione log: se supera 5MB tronca tenendo gli ultimi 500 righe
        if [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 5242880 ]; then
            tail -500 "$LOG" > "${LOG}.tmp" && mv "${LOG}.tmp" "$LOG"
            echo "[$(date)] Log ruotato." >> "$LOG"
        fi

        echo "[$(date)] --- Avvio kiosk ---" >> "$LOG"
        dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh >> "$LOG" 2>&1
        EXIT_CODE=$?
        echo "[$(date)] Cage terminato (codice: $EXIT_CODE)" >> "$LOG"

        # Se esiste il flag di stop (è stato premuto Ctrl+Alt+H) → esci
        if [ -f /opt/kiosk/.stop ]; then
            rm -f /opt/kiosk/.stop
            echo "[$(date)] Uscita intenzionale." >> "$LOG"
            break
        fi

        # Altrimenti è un crash: aspetta e riavvia
        echo "[$(date)] Crash rilevato — riavvio tra ${RESTART_DELAY}s..." >> "$LOG"
        sleep "$RESTART_DELAY"
    done

    echo ""
    echo "Kiosk fermato. Premi Invio per il terminale."
    read -r _
fi
PROFILE
chown "$KIOSK_USER:$KIOSK_USER" "$KIOSK_HOME/.bash_profile"

ok "Auto-login configurato."

# ═══════════════════════════════════════════════════════════════════════
#  6. GRUB — 2 secondi di timeout
# ═══════════════════════════════════════════════════════════════════════
info "Configurazione GRUB (2s timeout)..."

if [[ -f /etc/default/grub ]]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    sed -i 's/^GRUB_TIMEOUT_STYLE=.*/GRUB_TIMEOUT_STYLE=menu/' /etc/default/grub
    # Se GRUB_TIMEOUT_STYLE non esiste, aggiungilo
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB configurato (2s timeout)."
else
    info "GRUB non trovato (forse usi systemd-boot o altro). Salta."
fi

# ═══════════════════════════════════════════════════════════════════════
#  7. DISABILITA GUI E SERVIZI INUTILI
# ═══════════════════════════════════════════════════════════════════════
info "Disabilitazione GUI (se installata)..."

# Porta il target di default a multi-user (niente grafica systemd)
systemctl set-default multi-user.target
ok "Default target impostato a multi-user.target."

# Rimuove i display manager se presenti
for dm in gdm3 lightdm sddm xdm wdm nodm; do
    if dpkg -l "$dm" &>/dev/null; then
        info "  Rimozione display manager: $dm"
        apt-get remove --purge -y "$dm" 2>/dev/null || true
    fi
done

# Rimuove pacchetti desktop environment comuni se presenti
for de in task-gnome-desktop task-kde-desktop task-xfce-desktop \
          task-lxde-desktop task-lxqt-desktop task-cinnamon-desktop \
          gnome-shell plasma-desktop xfce4 lxde lxqt; do
    if dpkg -l "$de" &>/dev/null; then
        info "  Rimozione DE: $de"
        apt-get remove --purge -y "$de" 2>/dev/null || true
    fi
done
apt-get autoremove --purge -y 2>/dev/null || true

info "Disabilitazione servizi non necessari..."
for svc in ModemManager bluetooth cups avahi-daemon; do
    systemctl disable "$svc" 2>/dev/null && \
        info "  Disabilitato: $svc" || true
done

# Disabilita altri TTY (solo TTY1 serve)
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done
ok "Servizi configurati."

# ═══════════════════════════════════════════════════════════════════════
#  8. PERMESSI HARDWARE
# ═══════════════════════════════════════════════════════════════════════
info "Configurazione permessi hardware..."

# L'utente kiosk deve accedere a DRM, input devices, ecc.
cat > /etc/udev/rules.d/99-kiosk.rules << 'UDEV'
# Permette all'utente kiosk di accedere a GPU (DRM)
SUBSYSTEM=="drm", TAG+="uaccess"
# Permette accesso ai dispositivi di input (touch, mouse, tastiera)
SUBSYSTEM=="input", TAG+="uaccess"
UDEV

ok "Regole udev create."

# ═══════════════════════════════════════════════════════════════════════
#  RIEPILOGO
# ═══════════════════════════════════════════════════════════════════════
systemctl daemon-reload

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║                    SETUP COMPLETATO                       ║"
echo "╠═══════════════════════════════════════════════════════════╣"
echo "║                                                           ║"
echo "║  Al prossimo REBOOT:                                      ║"
echo "║    1. GRUB appare per 2 secondi (per recovery)            ║"
echo "║    2. Auto-login su TTY1 come utente 'kiosk'              ║"
echo "║    3. 2 secondi per premere Ctrl+C → terminale            ║"
echo "║    4. Cage si avvia → App fullscreen                      ║"
echo "║    5. Nessun pannello, nessuna gesture, niente DE         ║"
echo "║                                                           ║"
echo "║  Per manutenzione:                                        ║"
echo "║    - SSH: ssh root@<ip>                                   ║"
echo "║    - GRUB: tieni Shift al boot → recovery mode            ║"
echo "║    - TTY1: Ctrl+C nei primi 2s dopo login                 ║"
echo "║    - TTY2: Alt+F2 (se non mascherato)                     ║"
echo "║                                                           ║"
echo "║  App:   /opt/kiosk/                                       ║"
echo "║  User:  kiosk (no password, gruppi: video,input,render)   ║"
echo "║                                                           ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""
echo "Riavvia con: reboot"
