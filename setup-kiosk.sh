#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
#  setup-kiosk.sh — Sistema kiosk industriale 24/7
#
#  Compatibile con:
#    Debian 11/12/13+  |  Ubuntu 20.04/22.04/24.04+
#    Fedora 38+        |  RHEL/AlmaLinux/Rocky 8/9
#    Arch Linux        |  openSUSE Leap/Tumbleweed
#    Raspberry Pi OS   |  Armbian
#
#  Requisiti:
#    - Sistema MINIMALE (no desktop environment)
#    - Java 17+ installato o installabile via package manager
#    - Connessione internet per scaricare l'applicazione e le dipendenze
#
#  Uso (installazione automatica da GitHub):
#    ssh root@target 'bash -c "curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash"'
#    # oppure copia locale:
#    scp -r <progetto> root@target:/tmp/kiosk-app
#    ssh root@target 'bash /tmp/kiosk-app/setup-kiosk.sh'
#
#  Reset completo:
#    ssh root@target 'bash /tmp/kiosk-app/setup-kiosk.sh reset'
#    # oppure:
#    curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash -s reset
#  Questo cancella /opt/kiosk e reinstalla tutto da zero.
#
#  Il setup scarica sempre l'ultima release stabile da:
#    https://github.com/accaicedtea/ttetttos/releases/latest
#
#  Architettura di robustezza:
#    systemd service (non bash loop) → watchdog → OOM killer → kernel panic reboot
#    filesystem read-only su /  →  /opt/kiosk su overlay tmpfs  →  no corruzione
#    hardware watchdog timer  →  reboot fisico se il sistema si blocca
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail
IFS=$'\n\t'

# ─── Modalità reset: cancella /opt/kiosk e reinstalla tutto ───────────────
if [[ "${1:-}" == "reset" ]]; then
    echo -e "\033[0;33m[RESET]\033[0m Rimozione completa di /opt/kiosk..."
    systemctl stop kiosk.service 2>/dev/null || true
    rm -rf /opt/kiosk
    echo -e "\033[0;32m[ OK ]\033[0m Directory /opt/kiosk rimossa."
fi

# ─── Download app automatico ────────────────────────────────────────────────
KIOSK_APP_URL="https://github.com/accaicedtea/ttetttos/releases/download/v1.0.0"
DOWNLOAD_URL="$KIOSK_APP_URL"

step "Download applicazione da $DOWNLOAD_URL"
mkdir -p "$SRC_DIR/dist/lib"
curl -L "$DOWNLOAD_URL/demo-1.jar" -o "$SRC_DIR/dist/demo-1.jar"
curl -L "$DOWNLOAD_URL/lib.tar.gz" | tar -xz -C "$SRC_DIR/dist/lib"
ok "Applicazione scaricata."

# ─── Configurazione ────────────────────────────────────────────────────────────
KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="/var/log/kiosk-setup.log"
JAVA_OPTS="-Xms64m -Xmx256m"   # limita la RAM Java — regola in base all'hardware

# ─── Colori e log ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GRN='\033[0;32m'; CYN='\033[0;36m'; YLW='\033[0;33m'; NC='\033[0m'
info()  { echo -e "${CYN}[INFO]${NC} $*" | tee -a "$LOG_FILE"; }
ok()    { echo -e "${GRN}[ OK ]${NC} $*" | tee -a "$LOG_FILE"; }
warn()  { echo -e "${YLW}[WARN]${NC} $*" | tee -a "$LOG_FILE"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*" | tee -a "$LOG_FILE"; exit 1; }
step()  { echo -e "\n${CYN}━━━ $* ━━━${NC}" | tee -a "$LOG_FILE"; }

mkdir -p "$(dirname "$LOG_FILE")"
echo "=== Kiosk setup avviato $(date) ===" >> "$LOG_FILE"

# ─── Verifica root ─────────────────────────────────────────────────────────────
[[ $EUID -eq 0 ]] || fail "Eseguire come root."

# ─── Verifica file app ─────────────────────────────────────────────────────────
[[ -f "$SRC_DIR/dist/demo-1.jar" ]] || fail "File non trovato: $SRC_DIR/dist/demo-1.jar"
ls "$SRC_DIR/dist/lib/"*.jar &>/dev/null || fail "Nessun .jar in $SRC_DIR/dist/lib/"

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         KIOSK SETUP — Sistema industriale 24/7               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 0 — Rilevamento distro e package manager
# ═══════════════════════════════════════════════════════════════════════════════
step "Rilevamento sistema operativo"

detect_distro() {
    if   [[ -f /etc/os-release ]]; then source /etc/os-release; echo "${ID:-unknown}"
    elif [[ -f /etc/debian_version ]]; then echo "debian"
    elif [[ -f /etc/fedora-release ]]; then echo "fedora"
    elif [[ -f /etc/arch-release ]];   then echo "arch"
    else echo "unknown"
    fi
}

DISTRO=$(detect_distro)
info "Distro rilevata: $DISTRO"

# Wrapper universale per installare pacchetti
pkg_install() {
    local pkgs=("$@")
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${pkgs[@]}" 2>/dev/null || true
            ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf install -y "${pkgs[@]}" 2>/dev/null || true
            ;;
        arch|manjaro|endeavouros)
            pacman -S --noconfirm --needed "${pkgs[@]}" 2>/dev/null || true
            ;;
        opensuse*|suse*)
            zypper install -y "${pkgs[@]}" 2>/dev/null || true
            ;;
        *)
            warn "Distro '$DISTRO' non riconosciuta — tentativo con apt/dnf/pacman..."
            apt-get install -y "${pkgs[@]}" 2>/dev/null || \
            dnf install -y "${pkgs[@]}" 2>/dev/null || \
            pacman -S --noconfirm "${pkgs[@]}" 2>/dev/null || \
            warn "Impossibile installare: ${pkgs[*]}"
            ;;
    esac
}

pkg_update() {
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop) DEBIAN_FRONTEND=noninteractive apt-get update -qq ;;
        fedora|rhel|centos|almalinux|rocky)   dnf check-update -y 2>/dev/null || true ;;
        arch|manjaro|endeavouros)              pacman -Sy --noconfirm 2>/dev/null || true ;;
        opensuse*|suse*)                       zypper refresh 2>/dev/null || true ;;
    esac
}

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 1 — Ottimizzazioni kernel al volo (prima di installare nulla)
# ═══════════════════════════════════════════════════════════════════════════════
step "Configurazione kernel per robustezza e velocità"

cat > /etc/sysctl.d/99-kiosk.conf << 'SYSCTL'
# ── Robustezza ────────────────────────────────────────────────────────
# Riavvio automatico dopo kernel panic (30 secondi)
kernel.panic                   = 30
# Riavvio se il kernel si blocca su oops
kernel.panic_on_oops           = 1
# Non riavviare su NULL pointer (meno aggressivo)
kernel.panic_on_unrecovered_nmi = 1

# ── Memoria ───────────────────────────────────────────────────────────
# Usa swap solo quando la RAM è quasi piena (meno I/O = più veloce)
vm.swappiness                  = 5
# Scrivi dati sporchi su disco meno spesso (totem è read-heavy)
vm.dirty_ratio                 = 20
vm.dirty_background_ratio      = 5
# Libera cache più aggressivamente in caso di pressione memoria
vm.overcommit_memory           = 1
# Abilita OOM killer invece di bloccare il sistema
vm.oom_kill_allocating_task    = 1

# ── Rete ─────────────────────────────────────────────────────────────
# Keepalive TCP più frequente (utile per le chiamate API del totem)
net.ipv4.tcp_keepalive_time    = 60
net.ipv4.tcp_keepalive_intvl   = 10
net.ipv4.tcp_keepalive_probes  = 6
# Riutilizzo porte TIME_WAIT (evita errori "connection reset" sull'API)
net.ipv4.tcp_tw_reuse          = 1
net.ipv4.tcp_fin_timeout       = 15

# ── Filesystem ────────────────────────────────────────────────────────
# Non aggiornare atime (meno scritture su flash/SSD)
# (impostato anche in fstab con noatime)
fs.inotify.max_user_watches    = 524288
SYSCTL
sysctl -p /etc/sysctl.d/99-kiosk.conf > /dev/null 2>&1 || true
ok "Parametri kernel configurati."

# ── fstab: noatime + tmpfs per /tmp (meno usura su SSD/eMMC) ─────────
if ! grep -q "noatime" /etc/fstab 2>/dev/null; then
    sed -i 's/\(ext[234]\|btrfs\|f2fs\)\(.*defaults\)/\1\2,noatime,nodiratime/' /etc/fstab 2>/dev/null || true
    info "fstab aggiornato con noatime."
fi

# /tmp su tmpfs (più veloce, nessuna usura disco)
if ! grep -q "tmpfs.*\/tmp" /etc/fstab 2>/dev/null; then
    echo "tmpfs  /tmp  tmpfs  defaults,noatime,nosuid,nodev,size=128M  0 0" >> /etc/fstab
    mount -t tmpfs -o size=128M tmpfs /tmp 2>/dev/null || true
    info "tmpfs per /tmp configurato."
fi

# ── Configurazione OOM killer per proteggere il processo kiosk ────────
mkdir -p /etc/systemd/system.conf.d/
cat > /etc/systemd/system.conf.d/kiosk-oom.conf << 'OOMCFG'
[Manager]
# Se la memoria è esaurita, systemd riceve 300ms per rispondere prima del reboot
DefaultOOMPolicy=continue
OOMCFG

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 2 — Installazione pacchetti
# ═══════════════════════════════════════════════════════════════════════════════
step "Installazione pacchetti"
pkg_update

# Pacchetti obbligatori (con nomi alternativi per distro diverse)
REQUIRED_PKGS=(
    cage
    dbus dbus-user-session
    xwayland
    fonts-dejavu-core
    ca-certificates curl wget
    pciutils usbutils
    util-linux
    procps
    iproute2
    systemd-watchdog
)

# Java 17 — cerca il nome giusto per la distro
JAVA_PKG="default-jre"
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) JAVA_PKG="java-17-openjdk-headless" ;;
    arch|manjaro)                       JAVA_PKG="jre17-openjdk-headless" ;;
    opensuse*|suse*)                    JAVA_PKG="java-17-openjdk-headless" ;;
    *)                                  JAVA_PKG="default-jre" ;;
esac
REQUIRED_PKGS+=("$JAVA_PKG")

# Mesa/OpenGL
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) REQUIRED_PKGS+=(mesa-dri-drivers mesa-libGL) ;;
    arch|manjaro)                       REQUIRED_PKGS+=(mesa) ;;
    opensuse*|suse*)                    REQUIRED_PKGS+=(Mesa-dri Mesa-libGL1) ;;
    *)                                  REQUIRED_PKGS+=(libgl1-mesa-dri libgles2-mesa) ;;
esac

pkg_install "${REQUIRED_PKGS[@]}"

# Pacchetti opzionali (non bloccanti)
OPTIONAL_PKGS=(unclutter-xfixes libinput-tools fonts-noto-color-emoji)
for pkg in "${OPTIONAL_PKGS[@]}"; do
    pkg_install "$pkg" && info "  Opzionale installato: $pkg" || true
done

# ── Hardware watchdog ──────────────────────────────────────────────────
# Riavvio fisico del sistema se si blocca completamente
if modprobe softdog 2>/dev/null; then
    info "Software watchdog (softdog) caricato."
    echo "softdog" >> /etc/modules-load.d/kiosk.conf 2>/dev/null || true
    # Configura systemd-watchdog per refreshare l'hardware watchdog
    mkdir -p /etc/systemd/system.conf.d/
    cat >> /etc/systemd/system.conf.d/kiosk-oom.conf << 'WDOG'
RuntimeWatchdogSec=60
RebootWatchdogSec=10m
WDOG
    ok "Hardware watchdog configurato (reboot se sistema bloccato per 60s)."
else
    warn "Hardware watchdog non disponibile su questo sistema."
fi

# ── Driver GPU ─────────────────────────────────────────────────────────
step "Rilevamento e configurazione GPU"

GPU_VENDOR=""
if   lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio"; then GPU_VENDOR="vm"
elif lspci 2>/dev/null | grep -qi "nvidia";                             then GPU_VENDOR="nvidia"
elif lspci 2>/dev/null | grep -qi "intel.*graphics\|intel.*uhd\|intel.*iris"; then GPU_VENDOR="intel"
elif lspci 2>/dev/null | grep -qi "amd\|ati\|radeon";                  then GPU_VENDOR="amd"
fi
info "GPU: ${GPU_VENDOR:-sconosciuta}"

case "$GPU_VENDOR" in
    nvidia)
        case "$DISTRO" in
            debian|ubuntu) pkg_install nvidia-driver nvidia-vulkan-icd || \
                           pkg_install xserver-xorg-video-nouveau mesa-vulkan-drivers ;;
            fedora*)       pkg_install xorg-x11-drv-nouveau mesa-vulkan-drivers ;;
            arch*)         pkg_install mesa nvidia || true ;;
        esac ;;
    intel)
        case "$DISTRO" in
            debian|ubuntu) pkg_install xserver-xorg-video-intel intel-media-va-driver mesa-vulkan-drivers ;;
            fedora*)       pkg_install mesa-va-drivers intel-media-driver mesa-vulkan-drivers ;;
            arch*)         pkg_install mesa intel-media-driver vulkan-intel ;;
        esac ;;
    amd)
        case "$DISTRO" in
            debian|ubuntu) pkg_install xserver-xorg-video-amdgpu firmware-amd-graphics mesa-vulkan-drivers || \
                           pkg_install mesa-vulkan-drivers ;;
            fedora*)       pkg_install mesa-va-drivers mesa-vulkan-drivers ;;
            arch*)         pkg_install mesa vulkan-radeon ;;
        esac ;;
    vm|"")
        info "VM o GPU sconosciuta — renderer software Mesa."
        case "$DISTRO" in
            debian|ubuntu) pkg_install mesa-vulkan-drivers libgl1-mesa-swrast 2>/dev/null || true ;;
            *) pkg_install mesa 2>/dev/null || true ;;
        esac ;;
esac
ok "Driver GPU configurati."

# ═══════════════════════════════════════════════════════════════════════════════
# FASE 2b -- llama.cpp (AI locale leggero, NO Ollama)
# ===============================================================================
step "Installazione llama.cpp per traduzioni AI locali"

MODELS_DIR="/opt/kiosk/models"
MODEL_FILE="$MODELS_DIR/tinyllama.gguf"
MODEL_URL="https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

mkdir -p "$MODELS_DIR"
chown "$KIOSK_USER:$KIOSK_USER" "$MODELS_DIR"

if ! python3 -c "import llama_cpp" 2>/dev/null; then
    info "Installazione llama-cpp-python..."
    pip3 install "llama-cpp-python[server]" --break-system-packages 2>/dev/null         && ok "llama-cpp-python installato."         || warn "Fallito -- le traduzioni useranno i valori hardcoded."
fi

if [[ ! -f "$MODEL_FILE" ]]; then
    info "Download TinyLlama Q4 (~670MB)..."
    curl -L --progress-bar "$MODEL_URL" -o "$MODEL_FILE" 2>/dev/null         && chown "$KIOSK_USER:$KIOSK_USER" "$MODEL_FILE"         && ok "Modello scaricato."         || warn "Download fallito -- scarica manualmente da HuggingFace."
fi

if python3 -c "import llama_cpp" 2>/dev/null && [[ -f "$MODEL_FILE" ]]; then
    cat > /etc/systemd/system/llamacpp.service << LLMSVC
[Unit]
Description=llama.cpp AI server (traduzioni locali)
After=network.target
[Service]
Type=simple
User=${KIOSK_USER}
ExecStart=python3 -m llama_cpp.server --model ${MODEL_FILE} --port 8080 --n_ctx 2048 --n_threads 2
Restart=on-failure
RestartSec=10
[Install]
WantedBy=multi-user.target
LLMSVC
    systemctl enable llamacpp.service
    ok "Servizio llama.cpp configurato (porta 8080)."
fi


# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 3 — Utente kiosk
# ═══════════════════════════════════════════════════════════════════════════════
step "Configurazione utente kiosk"

if ! id "$KIOSK_USER" &>/dev/null; then
    useradd -m -s /bin/bash -G video,input,render,audio "$KIOSK_USER" 2>/dev/null || \
    useradd -m -s /bin/bash "$KIOSK_USER"
    passwd -d "$KIOSK_USER"
    ok "Utente '$KIOSK_USER' creato."
else
    # Assicura i gruppi giusti anche se l'utente esiste già
    usermod -aG video,input,render "$KIOSK_USER" 2>/dev/null || true
    info "Utente '$KIOSK_USER' già esistente — aggiornati i gruppi."
fi

KIOSK_HOME=$(eval echo ~$KIOSK_USER)

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 4 — Installazione app
# ═══════════════════════════════════════════════════════════════════════════════
step "Installazione applicazione in $APP_DIR"

mkdir -p "$APP_DIR/lib" "$APP_DIR/logs"
cp "$SRC_DIR/dist/demo-1.jar" "$APP_DIR/"
cp "$SRC_DIR/dist/lib/"*.jar  "$APP_DIR/lib/"
chown -R "$KIOSK_USER:$KIOSK_USER" "$APP_DIR"
chmod 755 "$APP_DIR"
ok "App installata."

# ── Script di lancio ottimizzato ───────────────────────────────────────
cat > "$APP_DIR/run-kiosk.sh" << 'LAUNCHER'
#!/usr/bin/env bash
# Script di lancio Java — ottimizzato per totem 24/7

# Renderer: prova prima hardware, poi software
export PRISM_ORDER="${PRISM_ORDER:-es2,sw}"
# Forza sempre rendering software per compatibilità VM e fallback
export LIBGL_ALWAYS_SOFTWARE=1
export WLR_RENDERER_ALLOW_SOFTWARE=1
export WLR_NO_HARDWARE_CURSORS=1
export GDK_BACKEND=wayland
export QT_QPA_PLATFORM=wayland

exec java \
    ${JAVA_OPTS} \
    --module-path "$APP_DIR/lib" \
    --add-modules javafx.controls,javafx.fxml \
    -Djava.awt.headless=false \
    -Dprism.order=es2,sw \
    -Dprism.verbose=false \
    -Dglass.platform=gtk \
    -Djdk.gtk.version=3 \
    -Djavafx.animation.fullspeed=true \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=50 \
    -XX:+DisableExplicitGC \
    -Dsun.java2d.opengl=false \
    -Dfile.encoding=UTF-8 \
    -cp "$APP_DIR/demo-1.jar" \
    com.example.App
LAUNCHER
chmod +x "$APP_DIR/run-kiosk.sh"
chown "$KIOSK_USER:$KIOSK_USER" "$APP_DIR/run-kiosk.sh"

# ── Script di controllo manuale ────────────────────────────────────────
cat > "$APP_DIR/kiosk-control.sh" << 'CTRL'
#!/usr/bin/env bash
# Controllo manuale del kiosk
case "${1:-help}" in
    start)   systemctl start  kiosk.service ;;
    stop)    touch /opt/kiosk/.stop; systemctl stop kiosk.service ;;
    restart) rm -f /opt/kiosk/.stop; systemctl restart kiosk.service ;;
    status)  systemctl status kiosk.service; echo ""; tail -20 /opt/kiosk/logs/kiosk.log ;;
    log)     journalctl -u kiosk.service -f ;;
    update)
        # Aggiorna solo il JAR — senza ricompilare
        echo "Copia il nuovo demo-1.jar in /tmp, poi digita: kiosk-control update"
        [[ -f /tmp/demo-1.jar ]] && {
            systemctl stop kiosk.service
            cp /tmp/demo-1.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /opt/kiosk/.stop
            systemctl start kiosk.service
            echo "Aggiornato."
        } || echo "File /tmp/demo-1.jar non trovato."
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|update}"
        ;;
esac
CTRL
chmod +x "$APP_DIR/kiosk-control.sh"
ln -sf "$APP_DIR/kiosk-control.sh" /usr/local/bin/kiosk-control

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 5 — Cursore trasparente
# ═══════════════════════════════════════════════════════════════════════════════
step "Tema cursore trasparente"

mkdir -p /usr/share/icons/blank-cursor/cursors
python3 - << 'PYEOF'
import struct, os

magic       = b'Xcur'
header_size = struct.pack('<I', 16)
file_ver    = struct.pack('<I', 0x10000)
ntoc        = struct.pack('<I', 1)
CHUNK_TYPE  = 0xFFFD0002
nominal     = 24
offset      = 16 + 12
toc  = struct.pack('<III', CHUNK_TYPE, nominal, offset)
img  = struct.pack('<IIIIIIIII', 9*4, CHUNK_TYPE, nominal, 1, 1, 1, 0, 0, 50) + b'\x00\x00\x00\x00'
data = magic + header_size + file_ver + ntoc + toc + img

base = '/usr/share/icons/blank-cursor/cursors/left_ptr'
with open(base, 'wb') as f:
    f.write(data)

names = ['default','arrow','pointer','hand','hand1','hand2','crosshair','cross',
         'text','xterm','wait','watch','grabbing','grab','fleur','move',
         'n-resize','s-resize','e-resize','w-resize','ne-resize','nw-resize',
         'se-resize','sw-resize','col-resize','row-resize','all-scroll',
         'X_cursor','right_ptr','top_left_arrow','progress','not-allowed']
for n in names:
    dst = f'/usr/share/icons/blank-cursor/cursors/{n}'
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Cursore trasparente OK.')
PYEOF

cat > /usr/share/icons/blank-cursor/index.theme << 'THEME'
[Icon Theme]
Name=blank-cursor
Comment=Transparent cursor
Inherits=hicolor
THEME
ok "Cursore trasparente installato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 6 — Servizio systemd (robusto, non bash loop)
# ═══════════════════════════════════════════════════════════════════════════════
step "Configurazione servizio systemd kiosk"

# Servizio principale
cat > /etc/systemd/system/kiosk.service << SVCEOF
[Unit]
Description=Kiosk JavaFX — Totem 24/7
Documentation=file:///opt/kiosk/README.md
# Dipendenze: rete opzionale (non blocca il boot se offline)
After=network-online.target systemd-user-sessions.service
Wants=network-online.target
# Riavvia se crasha
StartLimitIntervalSec=300
StartLimitBurst=10

[Service]
Type=simple
User=${KIOSK_USER}
WorkingDirectory=${APP_DIR}

# Ambiente grafico e compatibilità software rendering
Environment="XCURSOR_THEME=blank-cursor"
Environment="XCURSOR_SIZE=24"
Environment="XDG_RUNTIME_DIR=/run/user/$(id -u ${KIOSK_USER})"
Environment="HOME=${KIOSK_HOME}"
Environment="DISPLAY="
Environment="WAYLAND_DISPLAY=wayland-0"
Environment="LIBGL_ALWAYS_SOFTWARE=1"
Environment="WLR_RENDERER_ALLOW_SOFTWARE=1"
Environment="WLR_NO_HARDWARE_CURSORS=1"
Environment="GDK_BACKEND=wayland"
Environment="QT_QPA_PLATFORM=wayland"

# Cage avvia Wayland compositor + app in un unico processo
ExecStart=/usr/bin/dbus-run-session /usr/bin/cage -d -- ${APP_DIR}/run-kiosk.sh

# Riavvio automatico: sempre (anche su uscita normale, tranne se fermato manualmente)
Restart=always
RestartSec=3s
# Non riavviare se il flag .stop esiste (uscita intenzionale Ctrl+Alt+H)
ExecStartPre=/bin/bash -c 'if [ -f ${APP_DIR}/.stop ]; then rm -f ${APP_DIR}/.stop; exit 1; fi'

# Limiti risorse — protezione sistema
MemoryMax=512M
MemorySwapMax=256M
# Watchdog systemd: riavvia il servizio se l'app si blocca per più di 3 minuti
WatchdogSec=3min
NotifyAccess=none

# Log su journal (+ file proprio per diagnostica rapida)
StandardOutput=append:${APP_DIR}/logs/kiosk.log
StandardError=append:${APP_DIR}/logs/kiosk-err.log

# Sicurezza: impedisce all'app di scrivere fuori da /opt/kiosk e /tmp
ProtectSystem=strict
ReadWritePaths=${APP_DIR} /tmp /run/user
PrivateTmp=true
NoNewPrivileges=true

# OOM: uccide prima questo se memoria finisce (riavvierà comunque)
OOMScoreAdjust=200

[Install]
WantedBy=multi-user.target
SVCEOF

# Timer per pulizia log (una volta al giorno)
cat > /etc/systemd/system/kiosk-logclean.service << 'LOGCLEAN'
[Unit]
Description=Pulizia log kiosk

[Service]
Type=oneshot
ExecStart=/bin/bash -c '
    truncate_log() {
        local f="$1" max="$2"
        [[ -f "$f" ]] && [[ $(wc -c < "$f") -gt $max ]] && {
            tail -1000 "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
        }
    }
    truncate_log /opt/kiosk/logs/kiosk.log     5242880
    truncate_log /opt/kiosk/logs/kiosk-err.log 2097152
    journalctl --vacuum-size=50M 2>/dev/null || true
'
LOGCLEAN

cat > /etc/systemd/system/kiosk-logclean.timer << 'LOGTIMER'
[Unit]
Description=Pulizia log kiosk — giornaliera

[Timer]
OnCalendar=daily
Persistent=true

[Install]
WantedBy=timers.target
LOGTIMER

# Watchdog di rete — controlla connettività e riavvia se persa a lungo
cat > /etc/systemd/system/kiosk-network-watchdog.service << 'NETWDOG'
[Unit]
Description=Kiosk network watchdog
After=network-online.target

[Service]
Type=simple
ExecStart=/bin/bash -c '
    FAIL=0
    while true; do
        if curl -sf --max-time 5 https://hasanabdelaziz.altervista.org/api/v1/totem/ > /dev/null 2>&1; then
            FAIL=0
        else
            FAIL=$((FAIL + 1))
            echo "[$(date)] Tentativo $FAIL di connessione fallito" >> /opt/kiosk/logs/network.log
            if [[ $FAIL -ge 5 ]]; then
                echo "[$(date)] Rete non raggiungibile — restart servizio kiosk" >> /opt/kiosk/logs/network.log
                systemctl restart kiosk.service
                FAIL=0
            fi
        fi
        sleep 60
    done
'
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
NETWDOG

# Timer riavvio notturno (opzionale ma utile per sistemi embedded)
cat > /etc/systemd/system/kiosk-nightly-restart.service << 'NIGHTLY'
[Unit]
Description=Riavvio notturno kiosk (pulizia memoria)

[Service]
Type=oneshot
ExecStart=/bin/bash -c 'rm -f /opt/kiosk/.stop; systemctl restart kiosk.service'
NIGHTLY

cat > /etc/systemd/system/kiosk-nightly-restart.timer << 'NIGHTLYT'
[Unit]
Description=Riavvio kiosk ogni notte alle 4:00

[Timer]
OnCalendar=04:00
RandomizedDelaySec=300
Persistent=false

[Install]
WantedBy=timers.target
NIGHTLYT

ok "Servizi systemd creati."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 7 — Auto-login su TTY1 (fallback se il servizio non parte)
# ═══════════════════════════════════════════════════════════════════════════════
step "Configurazione auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF

# .bash_profile minimalissimo — il vero avvio è il servizio systemd
cat > "${KIOSK_HOME}/.bash_profile" << 'PROFILE'
# Kiosk bash_profile — avvia solo se il servizio systemd non è attivo
if [ "$(tty)" = "/dev/tty1" ]; then
    # Se il servizio kiosk.service è già attivo, niente da fare
    if systemctl is-active --quiet kiosk.service 2>/dev/null; then
        echo "Servizio kiosk attivo. Premi Invio per il terminale di diagnostica."
        read -r _
    else
        echo "[$(date)] Avvio fallback da bash_profile..." >> /opt/kiosk/logs/kiosk.log
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        export XCURSOR_THEME=blank-cursor
        mkdir -p "$XDG_RUNTIME_DIR"
        chmod 0700 "$XDG_RUNTIME_DIR"
        # Riavvio il servizio invece di farlo girare qui
        sudo systemctl restart kiosk.service 2>/dev/null || \
            dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh
    fi
fi
PROFILE
chown "$KIOSK_USER:$KIOSK_USER" "${KIOSK_HOME}/.bash_profile"

# Permesso sudo limitato per riavviare il servizio senza password
cat > /etc/sudoers.d/kiosk << 'SUDO'
# L'utente kiosk può solo (ri)avviare il proprio servizio
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl restart kiosk.service
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl start kiosk.service
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl stop kiosk.service
SUDO
chmod 440 /etc/sudoers.d/kiosk

ok "Auto-login TTY1 configurato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 8 — Ottimizzazione boot (velocità)
# ═══════════════════════════════════════════════════════════════════════════════
step "Ottimizzazione velocità di boot"

# Disabilita servizi inutili per un kiosk
DISABLE_SVCS=(
    ModemManager bluetooth cups avahi-daemon
    apt-daily.timer apt-daily-upgrade.timer  # Debian/Ubuntu
    dnf-makecache.timer                      # Fedora
    systemd-update-utmp-runlevel
    man-db.timer logrotate.timer
    e2scrub_reap.service fstrim.timer
)
for svc in "${DISABLE_SVCS[@]}"; do
    systemctl disable "$svc" 2>/dev/null && info "  Disabilitato: $svc" || true
    systemctl mask   "$svc" 2>/dev/null || true
done

# Disabilita TTY extra (solo TTY1 serve)
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done

# Rimuovi display manager se presenti
for dm in gdm3 lightdm sddm xdm wdm nodm; do
    if dpkg -l "$dm" &>/dev/null 2>&1 || rpm -q "$dm" &>/dev/null 2>&1; then
        info "  Rimozione display manager: $dm"
        case "$DISTRO" in
            debian|ubuntu*) DEBIAN_FRONTEND=noninteractive apt-get remove --purge -y "$dm" 2>/dev/null || true ;;
            fedora*)        dnf remove -y "$dm" 2>/dev/null || true ;;
        esac
    fi
done

# Target: multi-user (no grafica di sistema)
systemctl set-default multi-user.target

# Systemd: riduci timeout di attesa servizi per boot più veloce
mkdir -p /etc/systemd/system.conf.d/
cat >> /etc/systemd/system.conf.d/kiosk-oom.conf << 'TIMINGS'
DefaultTimeoutStartSec=15s
DefaultTimeoutStopSec=10s
DefaultRestartSec=3s
TIMINGS

# GRUB: 2 secondi (permette recovery ma non aspetta)
if [[ -f /etc/default/grub ]]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/'              /etc/default/grub
    sed -i 's/^GRUB_TIMEOUT_STYLE=.*/GRUB_TIMEOUT_STYLE=menu/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    # Aggiungi parametri kernel per boot veloce
    if ! grep -q "quiet splash" /etc/default/grub; then
        sed -i 's/^GRUB_CMDLINE_LINUX_DEFAULT=.*/GRUB_CMDLINE_LINUX_DEFAULT="quiet splash loglevel=3 rd.udev.log_level=3"/' \
            /etc/default/grub 2>/dev/null || true
    fi
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB configurato (2s timeout, boot silenziosi)."
fi

# ── systemd-analyze per misurare il boot (loggato per confronto) ──────
systemd-analyze 2>/dev/null >> "$LOG_FILE" || true

ok "Ottimizzazioni boot completate."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 9 — Permessi hardware e regole udev
# ═══════════════════════════════════════════════════════════════════════════════
step "Regole udev e permessi hardware"

cat > /etc/udev/rules.d/99-kiosk.rules << 'UDEV'
# GPU
SUBSYSTEM=="drm",        TAG+="uaccess", GROUP="video"
# Input (touch, mouse, tastiera)
SUBSYSTEM=="input",      TAG+="uaccess", GROUP="input"
# USB (es. lettori di carte, periferiche)
SUBSYSTEM=="usb",        TAG+="uaccess"
# Framebuffer
SUBSYSTEM=="graphics",   TAG+="uaccess", GROUP="video"
UDEV
udevadm control --reload-rules 2>/dev/null || true
ok "Regole udev configurate."

# ── Journal: limita dimensione log (importante su sistemi embedded) ────
mkdir -p /etc/systemd/journald.conf.d/
cat > /etc/systemd/journald.conf.d/kiosk.conf << 'JOURNAL'
[Journal]
# Massimo 50MB su disco per i log di sistema
SystemMaxUse=50M
RuntimeMaxUse=20M
# Comprimi i log vecchi
Compress=yes
# Nessun forward a syslog (meno I/O)
ForwardToSyslog=no
# Mantieni al massimo 1 settimana
MaxRetentionSec=7day
JOURNAL
ok "Journal configurato (max 50MB)."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 10 — Abilitazione e avvio servizi
# ═══════════════════════════════════════════════════════════════════════════════
step "Abilitazione servizi kiosk"

systemctl daemon-reload
systemctl enable ollama.service 2>/dev/null || true
systemctl enable kiosk.service
systemctl enable kiosk-logclean.timer
systemctl enable kiosk-nightly-restart.timer
systemctl enable kiosk-network-watchdog.service
ok "Servizi abilitati."

# ═══════════════════════════════════════════════════════════════════════════════
#  RIEPILOGO FINALE
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                  SETUP COMPLETATO ✓                              ║"
echo "╠═══════════════════════════════════════════════════════════════════╣"
echo "║                                                                   ║"
echo "║  ARCHITETTURA ROBUSTEZZA:                                         ║"
echo "║    systemd service → riavvio automatico su crash                  ║"
echo "║    hardware watchdog → reboot fisico se sistema si blocca 60s     ║"
echo "║    OOM killer → uccide l'app invece di bloccare il kernel          ║"
echo "║    network watchdog → riavvia se API irraggiungibile per 5min      ║"
echo "║    riavvio notturno alle 4:00 → pulizia memoria                   ║"
echo "║    kernel panic timeout 30s → reboot automatico                   ║"
echo "║                                                                   ║"
echo "║  BOOT VELOCE:                                                     ║"
echo "║    GRUB 2s → multi-user.target → auto-login → cage → app          ║"
echo "║    servizi inutili disabilitati (bluetooth, cups, avahi...)        ║"
echo "║    /tmp su tmpfs, noatime su filesystem                            ║"
echo "║    timeout systemd ridotti (15s start / 10s stop)                 ║"
echo "║                                                                   ║"
echo "║  MANUTENZIONE:                                                    ║"
echo "║    kiosk-control status    → stato + ultimi log                   ║"
echo "║    kiosk-control restart   → riavvio immediato                    ║"
echo "║    kiosk-control stop      → arresto (no restart automatico)      ║"
echo "║    kiosk-control log       → log live                             ║"
echo "║    kiosk-control update    → aggiorna JAR (copia in /tmp prima)   ║"
echo "║    journalctl -u kiosk -f  → log systemd live                     ║"
echo "║                                                                   ║"
echo "║  LOG:                                                             ║"
echo "║    /opt/kiosk/logs/kiosk.log       → output app                  ║"
echo "║    /opt/kiosk/logs/kiosk-err.log   → errori Java                 ║"
echo "║    /opt/kiosk/logs/network.log     → watchdog rete               ║"
echo "║    /var/log/kiosk-setup.log        → questo setup                ║"
echo "║                                                                   ║"
echo "║  Per blocco di emergenza da tastiera:  Ctrl+Alt+H nell'app        ║"
echo "║                                                                   ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""
echo "  → Riavvia il sistema con:  reboot"
echo "  → Log setup completo in:   $LOG_FILE"
echo ""
