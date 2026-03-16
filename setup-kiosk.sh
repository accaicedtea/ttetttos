#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
#  setup-kiosk.sh — Sistema kiosk industriale 24/7
#
#  Uso diretto (zero prerequisiti):
#    curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash
#
#  Oppure con modalita:
#    sudo bash setup-kiosk.sh          # installazione normale
#    sudo bash setup-kiosk.sh reset    # RESET COMPLETO: cancella tutto e reinstalla
#    sudo bash setup-kiosk.sh update   # aggiorna solo il JAR
#
#  Il setup scarica automaticamente l'app da GitHub Releases.
#  Non servono file locali.
# ═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ─── Repo GitHub ──────────────────────────────────────────────────────────────
GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
GITHUB_BRANCH="main"
RELEASE_TAG="v1.0.0"

APP_JAR_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/demo-1.jar"
APP_LIB_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/lib.tar.gz"

# ─── Configurazione ───────────────────────────────────────────────────────────
KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
LOG_FILE="/var/log/kiosk-setup.log"
JAVA_OPTS="-Xms64m -Xmx256m"
API_KEY="${TOTEM_API_KEY:-api_key_totem_1}"

# ─── Colori e log ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GRN='\033[0;32m'; CYN='\033[0;36m'; YLW='\033[0;33m'; NC='\033[0m'
info()  { echo -e "${CYN}[INFO]${NC} $*" | tee -a "${LOG_FILE}"; }
ok()    { echo -e "${GRN}[ OK ]${NC} $*" | tee -a "${LOG_FILE}"; }
warn()  { echo -e "${YLW}[WARN]${NC} $*" | tee -a "${LOG_FILE}"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*" | tee -a "${LOG_FILE}"; exit 1; }
step()  { echo -e "\n${CYN}━━━ $* ━━━${NC}" | tee -a "${LOG_FILE}"; }

mkdir -p "$(dirname "${LOG_FILE}")"
echo "" >> "${LOG_FILE}"
echo "=== Kiosk setup avviato $(date) MODE=${1:-install} ===" >> "${LOG_FILE}"

[[ $EUID -eq 0 ]] || fail "Eseguire come root: sudo bash $0"

# ═══════════════════════════════════════════════════════════════════════════════
#  MODALITA RESET — Cancella TUTTO e riparte da zero
# ═══════════════════════════════════════════════════════════════════════════════
if [[ "${1:-}" == "reset" ]]; then
    step "RESET COMPLETO DEL SISTEMA KIOSK"
    warn "Questo cancella utenti, servizi, app e configurazioni kiosk."
    warn "Il sistema di base (OS, rete) rimane intatto."
    echo ""

    # Ferma e disabilita tutti i servizi kiosk
    for svc in kiosk kiosk-logclean kiosk-logclean.timer \
                kiosk-nightly-restart kiosk-nightly-restart.timer \
                kiosk-network-watchdog llamacpp ollama; do
        systemctl stop    "${svc}.service" 2>/dev/null || true
        systemctl stop    "${svc}.timer"   2>/dev/null || true
        systemctl disable "${svc}.service" 2>/dev/null || true
        systemctl disable "${svc}.timer"   2>/dev/null || true
        systemctl mask    "${svc}.service" 2>/dev/null || true
        rm -f "/etc/systemd/system/${svc}.service" \
              "/etc/systemd/system/${svc}.timer"
    done
    systemctl daemon-reload

    # Rimuovi utente kiosk e sua home
    if id "${KIOSK_USER}" &>/dev/null; then
        pkill -u "${KIOSK_USER}" 2>/dev/null || true
        userdel -r "${KIOSK_USER}" 2>/dev/null && info "Utente ${KIOSK_USER} rimosso." || true
    fi

    # Rimuovi file di configurazione
    rm -rf "${APP_DIR}"
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -f /etc/systemd/system/getty@tty1.service.d/autologin.conf
    rm -rf /etc/systemd/journald.conf.d/kiosk.conf
    rm -rf /etc/systemd/system.conf.d/kiosk-oom.conf
    rm -f /etc/modules-load.d/kiosk.conf
    rm -rf /usr/share/icons/blank-cursor

    # Rimuovi Ollama se installato
    command -v ollama &>/dev/null && {
        info "Rimozione Ollama..."
        systemctl stop ollama 2>/dev/null || true
        rm -f /usr/local/bin/ollama
        rm -rf /usr/share/ollama ~/.ollama
    }

    # Rimuovi llama-cpp-python
    pip3 uninstall -y llama-cpp-python 2>/dev/null || true
    rm -f /etc/systemd/system/llamacpp.service

    # Ripristina default target
    systemctl set-default multi-user.target 2>/dev/null || true
    systemctl daemon-reload

    # Smascherano TTY
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done

    ok "Reset completo. Riavvio del setup..."
    echo ""
fi

# ═══════════════════════════════════════════════════════════════════════════════
#  MODALITA UPDATE — Aggiorna solo il JAR
# ═══════════════════════════════════════════════════════════════════════════════
if [[ "${1:-}" == "update" ]]; then
    step "Aggiornamento JAR"
    systemctl stop kiosk.service 2>/dev/null || true
    curl -fsSL "${APP_JAR_URL}" -o "${APP_DIR}/demo-1.jar" && {
        chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar"
        rm -f "${APP_DIR}/.stop"
        systemctl start kiosk.service
        ok "App aggiornata e riavviata."
    } || fail "Download JAR fallito."
    exit 0
fi

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         KIOSK SETUP — Sistema industriale 24/7               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 0 — Rilevamento distro
# ═══════════════════════════════════════════════════════════════════════════════
step "Rilevamento sistema operativo"

detect_distro() {
    if   [[ -f /etc/os-release ]]; then source /etc/os-release; echo "${ID:-unknown}"
    elif [[ -f /etc/debian_version ]]; then echo "debian"
    elif [[ -f /etc/fedora-release ]]; then echo "fedora"
    elif [[ -f /etc/arch-release ]];   then echo "arch"
    else echo "unknown"; fi
}
DISTRO=$(detect_distro)
info "Distro: $DISTRO"

pkg_install() {
    local pkgs=("$@")
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${pkgs[@]}" 2>/dev/null || true ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf install -y "${pkgs[@]}" 2>/dev/null || true ;;
        arch|manjaro|endeavouros)
            pacman -S --noconfirm --needed "${pkgs[@]}" 2>/dev/null || true ;;
        opensuse*|suse*)
            zypper install -y "${pkgs[@]}" 2>/dev/null || true ;;
        *)  apt-get install -y "${pkgs[@]}" 2>/dev/null || \
            dnf install -y "${pkgs[@]}" 2>/dev/null || \
            warn "Impossibile installare: ${pkgs[*]}" ;;
    esac
}

pkg_update() {
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get update -qq ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf check-update -y 2>/dev/null || true ;;
        arch|manjaro|endeavouros)
            pacman -Sy --noconfirm 2>/dev/null || true ;;
        opensuse*|suse*)
            zypper refresh 2>/dev/null || true ;;
    esac
}

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 1 — Parametri kernel
# ═══════════════════════════════════════════════════════════════════════════════
step "Ottimizzazione kernel"

cat > /etc/sysctl.d/99-kiosk.conf << 'SYSCTL'
kernel.panic                    = 30
kernel.panic_on_oops            = 1
kernel.panic_on_unrecovered_nmi = 1
vm.swappiness                   = 5
vm.dirty_ratio                  = 20
vm.dirty_background_ratio       = 5
vm.overcommit_memory            = 1
vm.oom_kill_allocating_task     = 1
net.ipv4.tcp_keepalive_time     = 60
net.ipv4.tcp_keepalive_intvl    = 10
net.ipv4.tcp_keepalive_probes   = 6
net.ipv4.tcp_tw_reuse           = 1
net.ipv4.tcp_fin_timeout        = 15
fs.inotify.max_user_watches     = 524288
SYSCTL
sysctl -p /etc/sysctl.d/99-kiosk.conf > /dev/null 2>&1 || true

if ! grep -q "noatime" /etc/fstab 2>/dev/null; then
    sed -i 's/\(ext[234]\|btrfs\|f2fs\)\(.*defaults\)/\1\2,noatime,nodiratime/' \
        /etc/fstab 2>/dev/null || true
fi
if ! grep -q "tmpfs.*\/tmp" /etc/fstab 2>/dev/null; then
    echo "tmpfs /tmp tmpfs defaults,noatime,nosuid,nodev,size=128M 0 0" >> /etc/fstab
    mount -t tmpfs -o size=128M tmpfs /tmp 2>/dev/null || true
fi

mkdir -p /etc/systemd/system.conf.d/
cat > /etc/systemd/system.conf.d/kiosk-oom.conf << 'OOMCFG'
[Manager]
DefaultOOMPolicy=continue
DefaultTimeoutStartSec=15s
DefaultTimeoutStopSec=10s
DefaultRestartSec=3s
OOMCFG

if modprobe softdog 2>/dev/null; then
    echo "softdog" >> /etc/modules-load.d/kiosk.conf 2>/dev/null || true
    cat >> /etc/systemd/system.conf.d/kiosk-oom.conf << 'WDOG'
RuntimeWatchdogSec=60
RebootWatchdogSec=10m
WDOG
    ok "Hardware watchdog attivo."
fi
ok "Kernel configurato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 2 — Pacchetti
# ═══════════════════════════════════════════════════════════════════════════════
step "Installazione pacchetti"
pkg_update

# Determina pacchetto Java
JAVA_PKG="default-jre"
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) JAVA_PKG="java-21-openjdk-headless" ;;
    arch|manjaro)                       JAVA_PKG="jre21-openjdk-headless" ;;
    opensuse*|suse*)                    JAVA_PKG="java-21-openjdk-headless" ;;
    *)                                  JAVA_PKG="default-jre" ;;
esac

# Determina pacchetto Mesa
MESA_PKGS=()
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky)
        MESA_PKGS=(mesa-dri-drivers mesa-libGL mesa-libEGL) ;;
    arch|manjaro)
        MESA_PKGS=(mesa) ;;
    opensuse*|suse*)
        MESA_PKGS=(Mesa-dri Mesa-libGL1 Mesa-libEGL1) ;;
    *)
        MESA_PKGS=(libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa \
                   libgles2-mesa mesa-utils) ;;
esac

pkg_install \
    cage xwayland \
    dbus dbus-user-session \
    fonts-dejavu-core \
    ca-certificates curl wget \
    pciutils usbutils \
    util-linux procps iproute2 \
    lm-sensors python3 python3-pip \
    "${JAVA_PKG}" \
    "${MESA_PKGS[@]}"

# Font emoji
pkg_install fonts-noto-color-emoji 2>/dev/null || true
pkg_install unclutter-xfixes       2>/dev/null || true

ok "Pacchetti installati."

# ─── Driver GPU ───────────────────────────────────────────────────────────────
step "Rilevamento GPU"

GPU_VENDOR=""
if   lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio"; then GPU_VENDOR="vm"
elif lspci 2>/dev/null | grep -qi "nvidia";                            then GPU_VENDOR="nvidia"
elif lspci 2>/dev/null | grep -qi "intel.*graphics\|intel.*uhd\|intel.*iris"; then GPU_VENDOR="intel"
elif lspci 2>/dev/null | grep -qi "amd\|ati\|radeon";                 then GPU_VENDOR="amd"
fi
info "GPU: ${GPU_VENDOR:-sconosciuta}"

case "$GPU_VENDOR" in
    nvidia)
        case "$DISTRO" in
            debian|ubuntu) pkg_install nvidia-driver 2>/dev/null || \
                           pkg_install xserver-xorg-video-nouveau ;;
            arch*) pkg_install mesa nvidia 2>/dev/null || true ;;
        esac ;;
    intel)
        case "$DISTRO" in
            debian|ubuntu) pkg_install xserver-xorg-video-intel \
                                        intel-media-va-driver 2>/dev/null || true ;;
            arch*) pkg_install mesa intel-media-driver vulkan-intel 2>/dev/null || true ;;
        esac ;;
    amd)
        case "$DISTRO" in
            debian|ubuntu) pkg_install xserver-xorg-video-amdgpu \
                                        firmware-amd-graphics 2>/dev/null || true ;;
            arch*) pkg_install mesa vulkan-radeon 2>/dev/null || true ;;
        esac ;;
    vm|"")
        info "VM / GPU sconosciuta — software rendering."
        case "$DISTRO" in
            debian|ubuntu)
                pkg_install libgl1-mesa-swrast libgl1-mesa-dri 2>/dev/null || true ;;
            *) pkg_install mesa 2>/dev/null || true ;;
        esac ;;
esac
ok "GPU configurata."


# FASE 2b — llama.cpp SALTATO (traduzioni predefinite in I18n.java)
mkdir -p "${APP_DIR}/models"

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 3 — Utente kiosk
# ═══════════════════════════════════════════════════════════════════════════════
step "Utente kiosk"

if ! id "${KIOSK_USER}" &>/dev/null; then
    useradd -m -s /bin/bash -G video,input,render,audio "${KIOSK_USER}" 2>/dev/null || \
    useradd -m -s /bin/bash "${KIOSK_USER}"
    passwd -d "${KIOSK_USER}"
    ok "Utente '${KIOSK_USER}' creato."
else
    usermod -aG video,input,render,audio "${KIOSK_USER}" 2>/dev/null || true
    info "Utente '${KIOSK_USER}' esistente — gruppi aggiornati."
fi

KIOSK_HOME=$(eval echo ~"${KIOSK_USER}")
KIOSK_UID=$(id -u "${KIOSK_USER}")

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 4 — Download e installazione app
# ═══════════════════════════════════════════════════════════════════════════════
step "Download e installazione app da GitHub"

mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/models"

# Scarica JAR principale
info "Download demo-1.jar..."
if curl -fsSL "${APP_JAR_URL}" -o "${APP_DIR}/demo-1.jar" 2>/dev/null; then
    ok "JAR scaricato."
else
    warn "Download JAR da GitHub fallito — cercando file locale..."
    # Fallback: file locale nella stessa directory dello script
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo ".")"
    if [[ -f "${SCRIPT_DIR}/dist/demo-1.jar" ]]; then
        cp "${SCRIPT_DIR}/dist/demo-1.jar" "${APP_DIR}/"
        ok "JAR copiato da file locale."
    else
        fail "JAR non trovato. Scarica manualmente in ${APP_DIR}/demo-1.jar"
    fi
fi

# Scarica librerie JavaFX + dipendenze
info "Download librerie..."
if curl -fsSL "${APP_LIB_URL}" 2>/dev/null | tar -xz -C "${APP_DIR}/lib/"; then
    ok "Librerie scaricate."
else
    warn "Download lib.tar.gz fallito — cercando file locali..."
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo ".")"
    if ls "${SCRIPT_DIR}/dist/lib/"*.jar &>/dev/null 2>&1; then
        cp "${SCRIPT_DIR}/dist/lib/"*.jar "${APP_DIR}/lib/"
        ok "Librerie copiate da file locali."
    else
        warn "Nessuna libreria trovata — l'app potrebbe non avviarsi."
    fi
fi

chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}"
chmod 755 "${APP_DIR}"
ok "App installata in ${APP_DIR}."

# ─── Download JavaFX SDK (nativi Linux inclusi) ───────────────────────────────
# Non servono moduli separati — JavaFX SDK contiene tutto inclusi i .so nativi
JAVAFX_VERSION="21.0.2"
JAVAFX_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-sdk.zip"
JAVAFX_ZIP="/tmp/javafx-sdk.zip"
JAVAFX_DIR="${APP_DIR}/javafx-sdk"

if [[ ! -d "${JAVAFX_DIR}" ]]; then
    info "Download JavaFX SDK ${JAVAFX_VERSION}..."
    pkg_install unzip 2>/dev/null || true
    mkdir -p "${JAVAFX_DIR}"

    if curl -fsSL "${JAVAFX_URL}" -o "${JAVAFX_ZIP}"; then
        # Estrai SOLO i JAR e i .so necessari — salta libjfxwebkit.so (~50MB inutile)
        unzip -q "${JAVAFX_ZIP}"             "javafx-sdk-${JAVAFX_VERSION}/lib/*.jar"             "javafx-sdk-${JAVAFX_VERSION}/lib/libglass.so"             "javafx-sdk-${JAVAFX_VERSION}/lib/libjavafx_font*.so"             "javafx-sdk-${JAVAFX_VERSION}/lib/libjavafx_iio.so"             "javafx-sdk-${JAVAFX_VERSION}/lib/libprism_es2.so"             "javafx-sdk-${JAVAFX_VERSION}/lib/libprism_sw.so"             "javafx-sdk-${JAVAFX_VERSION}/lib/libdecora_sse.so"             -d "/tmp/javafx-extract/" 2>/dev/null || true

        # Copia solo i file estratti
        cp /tmp/javafx-extract/javafx-sdk-${JAVAFX_VERSION}/lib/*.jar            /tmp/javafx-extract/javafx-sdk-${JAVAFX_VERSION}/lib/*.so            "${JAVAFX_DIR}/" 2>/dev/null || true

        rm -rf "${JAVAFX_ZIP}" /tmp/javafx-extract/
        chown -R "${KIOSK_USER}:${KIOSK_USER}" "${JAVAFX_DIR}"
        ok "JavaFX SDK installato ($(du -sh ${JAVAFX_DIR} | cut -f1))."
    else
        warn "Download JavaFX SDK fallito."
        rmdir "${JAVAFX_DIR}" 2>/dev/null || true
    fi
else
    info "JavaFX SDK gia presente: ${JAVAFX_DIR}"
fi

# ─── Script di lancio ─────────────────────────────────────────────────────────
cat > "${APP_DIR}/run-kiosk.sh" << LAUNCHER
#!/usr/bin/env bash
# Launcher JavaFX per kiosk — supporta Wayland reale, Xwayland e VM

# Imposta DISPLAY per Xwayland se non siamo in Wayland puro
export DISPLAY="\${DISPLAY:-:0}"
export WAYLAND_DISPLAY="\${WAYLAND_DISPLAY:-}"

# Rendering: prova OpenGL hardware, poi software
export PRISM_ORDER="\${PRISM_ORDER:-es2,sw}"

# Rilevamento VM: forza software rendering
if lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio"; then
    export LIBGL_ALWAYS_SOFTWARE=1
    export WLR_RENDERER=pixman
    export WLR_RENDERER_ALLOW_SOFTWARE=1
    export WLR_NO_HARDWARE_CURSORS=1
fi

# API key totem
export TOTEM_API_KEY="${API_KEY}"

exec java \\
    ${JAVA_OPTS} \\
    --module-path "${APP_DIR}/lib" \\
    --add-modules javafx.controls,javafx.fxml \\
    --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \\
    -Djava.awt.headless=false \\
    -Dprism.order=es2,sw \\
    -Dprism.verbose=false \\
    -Dglass.platform=gtk \\
    -Djdk.gtk.version=3 \\
    -Djavafx.animation.fullspeed=true \\
    -XX:+UseG1GC \\
    -XX:MaxGCPauseMillis=50 \\
    -XX:+DisableExplicitGC \\
    -Dfile.encoding=UTF-8 \\
    -cp "${APP_DIR}/demo-1.jar" \\
    com.example.App
LAUNCHER
chmod +x "${APP_DIR}/run-kiosk.sh"
chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/run-kiosk.sh"

# ─── Script controllo ─────────────────────────────────────────────────────────
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRL'
#!/usr/bin/env bash
case "${1:-help}" in
    start)   systemctl start  kiosk.service ;;
    stop)    touch /opt/kiosk/.stop; systemctl stop kiosk.service ;;
    restart) rm -f /opt/kiosk/.stop; systemctl restart kiosk.service ;;
    status)  systemctl status kiosk.service; echo ""; tail -20 /opt/kiosk/logs/kiosk.log ;;
    log)     journalctl -u kiosk.service -f ;;
    update)
        echo "Download ultimo JAR da GitHub..."
        curl -fsSL "https://github.com/accaicedtea/ttetttos/releases/download/v1.0.0/demo-1.jar" \
            -o /tmp/kiosk-update.jar && {
            systemctl stop kiosk.service
            cp /tmp/kiosk-update.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /opt/kiosk/.stop /tmp/kiosk-update.jar
            systemctl start kiosk.service
            echo "Aggiornato."
        } || echo "Download fallito."
        ;;
    reset)
        echo "Esegui: curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash -s reset"
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|update|reset}"
        ;;
esac
CTRL
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 5 — Cursore trasparente
# ═══════════════════════════════════════════════════════════════════════════════
step "Cursore trasparente"

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
img  = struct.pack('<IIIIIIIII', 9*4, CHUNK_TYPE, nominal, 1, 1, 1, 0, 0, 50) \
       + b'\x00\x00\x00\x00'
data = magic + header_size + file_ver + ntoc + toc + img

base = '/usr/share/icons/blank-cursor/cursors/left_ptr'
with open(base, 'wb') as f:
    f.write(data)

names = [
    'default','arrow','pointer','hand','hand1','hand2','crosshair','cross',
    'text','xterm','wait','watch','grabbing','grab','fleur','move',
    'n-resize','s-resize','e-resize','w-resize','ne-resize','nw-resize',
    'se-resize','sw-resize','col-resize','row-resize','all-scroll',
    'X_cursor','right_ptr','top_left_arrow','progress','not-allowed',
]
for n in names:
    dst = f'/usr/share/icons/blank-cursor/cursors/{n}'
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Cursore OK.')
PYEOF

cat > /usr/share/icons/blank-cursor/index.theme << 'THEME'
[Icon Theme]
Name=blank-cursor
Comment=Transparent cursor
Inherits=hicolor
THEME
ok "Cursore trasparente installato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 6 — Servizi systemd
# ═══════════════════════════════════════════════════════════════════════════════
step "Servizi systemd"

# ── Servizio principale kiosk ─────────────────────────────────────────────────
# FIX VM / Xwayland: usa weston o Xwayland come fallback se cage non funziona
cat > /etc/systemd/system/kiosk.service << SVCEOF
[Unit]
Description=Kiosk JavaFX Totem 24/7
After=network-online.target systemd-user-sessions.service
Wants=network-online.target
StartLimitIntervalSec=300
StartLimitBurst=10

[Service]
Type=simple
User=${KIOSK_USER}
WorkingDirectory=${APP_DIR}

# Ambiente di base
Environment="HOME=${KIOSK_HOME}"
Environment="XCURSOR_THEME=blank-cursor"
Environment="XCURSOR_SIZE=24"
Environment="XDG_RUNTIME_DIR=/run/user/${KIOSK_UID}"
Environment="TOTEM_API_KEY=${API_KEY}"

# Software rendering (compatibile con VM e hardware senza accelerazione)
Environment="LIBGL_ALWAYS_SOFTWARE=1"
Environment="WLR_RENDERER=pixman"
Environment="WLR_RENDERER_ALLOW_SOFTWARE=1"
Environment="WLR_NO_HARDWARE_CURSORS=1"

# Wayland via Cage (compositor minimale per kiosk)
ExecStartPre=/bin/bash -c 'if [ -f ${APP_DIR}/.stop ]; then rm -f ${APP_DIR}/.stop; exit 1; fi'
ExecStartPre=/bin/bash -c 'mkdir -p /run/user/${KIOSK_UID} && chmod 700 /run/user/${KIOSK_UID} && chown ${KIOSK_USER}:${KIOSK_USER} /run/user/${KIOSK_UID}'
ExecStart=/usr/bin/dbus-run-session /usr/bin/cage -d -- ${APP_DIR}/run-kiosk.sh

Restart=always
RestartSec=3s
MemoryMax=512M
MemorySwapMax=256M
WatchdogSec=3min
NotifyAccess=none
OOMScoreAdjust=200

StandardOutput=append:${APP_DIR}/logs/kiosk.log
StandardError=append:${APP_DIR}/logs/kiosk-err.log

ProtectSystem=strict
ReadWritePaths=${APP_DIR} /tmp /run/user
PrivateTmp=true
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
SVCEOF

# ── Timer pulizia log ─────────────────────────────────────────────────────────
cat > /etc/systemd/system/kiosk-logclean.service << 'LOGCLEAN'
[Unit]
Description=Pulizia log kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c '
    f=/opt/kiosk/logs/kiosk.log
    [[ -f "$f" ]] && [[ $(wc -c < "$f") -gt 5242880 ]] && tail -1000 "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
    journalctl --vacuum-size=50M 2>/dev/null || true
'
LOGCLEAN
cat > /etc/systemd/system/kiosk-logclean.timer << 'LOGTIMER'
[Unit]
Description=Pulizia log kiosk giornaliera
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
LOGTIMER

# ── Riavvio notturno ──────────────────────────────────────────────────────────
cat > /etc/systemd/system/kiosk-nightly-restart.service << 'NIGHTLY'
[Unit]
Description=Riavvio notturno kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c 'rm -f /opt/kiosk/.stop; systemctl restart kiosk.service'
NIGHTLY
cat > /etc/systemd/system/kiosk-nightly-restart.timer << 'NIGHTLYT'
[Unit]
Description=Riavvio kiosk alle 04:00
[Timer]
OnCalendar=04:00
RandomizedDelaySec=300
Persistent=false
[Install]
WantedBy=timers.target
NIGHTLYT

# ── Network watchdog ──────────────────────────────────────────────────────────
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
            FAIL=$((FAIL+1))
            echo "[$(date)] Ping fallito ($FAIL)" >> /opt/kiosk/logs/network.log
            if [[ $FAIL -ge 5 ]]; then
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

ok "Servizi systemd creati."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 7 — Auto-login TTY1
# ═══════════════════════════════════════════════════════════════════════════════
step "Auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF

cat > "${KIOSK_HOME}/.bash_profile" << 'PROFILE'
if [ "$(tty)" = "/dev/tty1" ]; then
    if systemctl is-active --quiet kiosk.service 2>/dev/null; then
        echo "Kiosk attivo. Premi Invio per terminale."
        read -r _
    else
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
        export XCURSOR_THEME=blank-cursor
        sudo systemctl restart kiosk.service 2>/dev/null || \
            dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh
    fi
fi
PROFILE
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bash_profile"

cat > /etc/sudoers.d/kiosk << 'SUDO'
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl restart kiosk.service
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl start kiosk.service
kiosk ALL=(ALL) NOPASSWD: /bin/systemctl stop kiosk.service
SUDO
chmod 440 /etc/sudoers.d/kiosk
ok "Auto-login configurato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 8 — Boot veloce
# ═══════════════════════════════════════════════════════════════════════════════
step "Ottimizzazione boot"

for svc in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer \
           dnf-makecache.timer man-db.timer logrotate.timer \
           e2scrub_reap.service fstrim.timer; do
    systemctl disable "${svc}" 2>/dev/null || true
    systemctl mask    "${svc}" 2>/dev/null || true
done

for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done

systemctl set-default multi-user.target

if [[ -f /etc/default/grub ]]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    sed -i 's/^GRUB_TIMEOUT_STYLE=.*/GRUB_TIMEOUT_STYLE=menu/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB: 2s timeout."
fi
ok "Boot ottimizzato."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 9 — Permessi hardware
# ═══════════════════════════════════════════════════════════════════════════════
step "Permessi hardware"

cat > /etc/udev/rules.d/99-kiosk.rules << 'UDEV'
SUBSYSTEM=="drm",      TAG+="uaccess", GROUP="video"
SUBSYSTEM=="input",    TAG+="uaccess", GROUP="input"
SUBSYSTEM=="usb",      TAG+="uaccess"
SUBSYSTEM=="graphics", TAG+="uaccess", GROUP="video"
UDEV
udevadm control --reload-rules 2>/dev/null || true

mkdir -p /etc/systemd/journald.conf.d/
cat > /etc/systemd/journald.conf.d/kiosk.conf << 'JOURNAL'
[Journal]
SystemMaxUse=50M
RuntimeMaxUse=20M
Compress=yes
ForwardToSyslog=no
MaxRetentionSec=7day
JOURNAL
ok "Permessi configurati."

# ═══════════════════════════════════════════════════════════════════════════════
#  FASE 10 — Abilitazione servizi
# ═══════════════════════════════════════════════════════════════════════════════
step "Abilitazione servizi"

systemctl daemon-reload
systemctl enable kiosk.service
systemctl enable kiosk-logclean.timer
systemctl enable kiosk-nightly-restart.timer
systemctl enable kiosk-network-watchdog.service
[[ -f /etc/systemd/system/llamacpp.service ]] && \
    systemctl enable llamacpp.service 2>/dev/null || true

# Crea XDG runtime dir per kiosk
mkdir -p "/run/user/${KIOSK_UID}"
chmod 700 "/run/user/${KIOSK_UID}"
chown "${KIOSK_USER}:${KIOSK_USER}" "/run/user/${KIOSK_UID}"

ok "Servizi abilitati."

# ═══════════════════════════════════════════════════════════════════════════════
#  RIEPILOGO
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                  SETUP COMPLETATO                                ║"
echo "╠═══════════════════════════════════════════════════════════════════╣"
echo "║                                                                   ║"
echo "║  Installazione one-line:                                          ║"
echo "║    curl -fsSL https://raw.githubusercontent.com/${GITHUB_USER}/${GITHUB_REPO}/main/setup-kiosk.sh | sudo bash"
echo "║                                                                   ║"
echo "║  Reset completo:                                                  ║"
echo "║    curl -fsSL ...setup-kiosk.sh | sudo bash -s reset             ║"
echo "║                                                                   ║"
echo "║  Manutenzione:                                                    ║"
echo "║    kiosk-control status|restart|stop|log|update                  ║"
echo "║    journalctl -u kiosk -f                                        ║"
echo "║                                                                   ║"
echo "║  Log: ${APP_DIR}/logs/                                            ║"
echo "║                                                                   ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""
echo "  Riavvia con:  reboot"
echo ""