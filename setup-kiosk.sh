#!/usr/bin/env bash
# ===============================================================================
#  setup-kiosk.sh  Sistema kiosk JavaFX 24/7
#
#  Uso:
#    curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash
#    sudo bash setup-kiosk.sh          # installazione
#    sudo bash setup-kiosk.sh reset    # reset completo e reinstalla
#    sudo bash setup-kiosk.sh update   # aggiorna solo il JAR
# ===============================================================================
set -euo pipefail

# --- Config -------------------------------------------------------------------
GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
RELEASE_TAG="v1.0.0"
APP_JAR_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/demo-1.jar"
APP_LIB_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/lib.tar.gz"
JAVAFX_VERSION="21.0.2"
JAVAFX_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-sdk.zip"

KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
LOG_FILE="/var/log/kiosk-setup.log"
JAVA_OPTS="-Xms64m -Xmx256m"
API_KEY="${TOTEM_API_KEY:-api_key_totem_1}"

# --- Colori -------------------------------------------------------------------
RED='\033[0;31m'; GRN='\033[0;32m'; CYN='\033[0;36m'; YLW='\033[0;33m'; NC='\033[0m'
info() { echo -e "${CYN}[INFO]${NC} $*" | tee -a "${LOG_FILE}"; }
ok()   { echo -e "${GRN}[ OK ]${NC} $*" | tee -a "${LOG_FILE}"; }
warn() { echo -e "${YLW}[WARN]${NC} $*" | tee -a "${LOG_FILE}"; }
fail() { echo -e "${RED}[FAIL]${NC} $*" | tee -a "${LOG_FILE}"; exit 1; }
step() { echo -e "\n${CYN}--- $* ---${NC}" | tee -a "${LOG_FILE}"; }

mkdir -p "$(dirname "${LOG_FILE}")"
echo "=== Setup avviato $(date) MODE=${1:-install} ===" >> "${LOG_FILE}"
[[ $EUID -eq 0 ]] || fail "Eseguire come root: sudo bash $0"

# --- Rilevamento distro -------------------------------------------------------
detect_distro() {
    if [[ -f /etc/os-release ]]; then
        source /etc/os-release
        echo "${ID:-unknown}"
    elif [[ -f /etc/debian_version ]]; then echo "debian"
    elif [[ -f /etc/fedora-release ]];  then echo "fedora"
    elif [[ -f /etc/arch-release ]];    then echo "arch"
    else echo "unknown"; fi
}
DISTRO=$(detect_distro)

pkg_install() {
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@" 2>/dev/null || true ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf install -y "$@" 2>/dev/null || true ;;
        arch|manjaro|endeavouros)
            pacman -S --noconfirm --needed "$@" 2>/dev/null || true ;;
        opensuse*|suse*)
            zypper install -y "$@" 2>/dev/null || true ;;
        *)  apt-get install -y "$@" 2>/dev/null || dnf install -y "$@" 2>/dev/null || true ;;
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

# --- Rilevamento GPU / VM -----------------------------------------------------
detect_gpu() {
    if lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio-vga\|virgl"; then
        echo "vm"
    elif lspci 2>/dev/null | grep -qi "nvidia"; then
        echo "nvidia"
    elif lspci 2>/dev/null | grep -qi "intel.*graphics\|intel.*uhd\|intel.*iris"; then
        echo "intel"
    elif lspci 2>/dev/null | grep -qi "amd\|ati\|radeon"; then
        echo "amd"
    else
        echo "unknown"
    fi
}

# ===============================================================================
#  MODALITA RESET
# ===============================================================================
if [[ "${1:-}" == "reset" ]]; then
    step "RESET COMPLETO"
    for svc in kiosk kiosk-logclean kiosk-nightly-restart kiosk-network-watchdog; do
        systemctl stop    "${svc}.service" 2>/dev/null || true
        systemctl stop    "${svc}.timer"   2>/dev/null || true
        systemctl disable "${svc}.service" 2>/dev/null || true
        systemctl disable "${svc}.timer"   2>/dev/null || true
        rm -f "/etc/systemd/system/${svc}.service" "/etc/systemd/system/${svc}.timer"
    done
    systemctl daemon-reload
    if id "${KIOSK_USER}" &>/dev/null; then
        pkill -u "${KIOSK_USER}" 2>/dev/null || true
        sleep 1
        userdel -r "${KIOSK_USER}" 2>/dev/null || true
    fi
    rm -rf "${APP_DIR}"
    rm -f /etc/sudoers.d/kiosk /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -rf /etc/systemd/journald.conf.d/kiosk.conf
    rm -rf /etc/systemd/system.conf.d/kiosk-oom.conf
    rm -rf /usr/share/icons/blank-cursor
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done
    systemctl set-default multi-user.target 2>/dev/null || true
    systemctl daemon-reload
    ok "Reset completato. Reinstallazione in corso..."
    echo ""
fi

# ===============================================================================
#  MODALITA UPDATE
# ===============================================================================
if [[ "${1:-}" == "update" ]]; then
    step "Aggiornamento JAR"
    systemctl stop kiosk.service 2>/dev/null || true
    curl -fsSL "${APP_JAR_URL}" -o "${APP_DIR}/demo-1.jar" \
        && chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar" \
        && rm -f "${APP_DIR}/.stop" \
        && systemctl start kiosk.service \
        && ok "Aggiornato." \
        || fail "Download fallito."
    exit 0
fi

echo ""
echo "+===============================================================+"
echo "|     KIOSK SETUP  $(echo $DISTRO | tr '[:lower:]' '[:upper:]')  $(date '+%Y-%m-%d')                |"
echo "+===============================================================+"
echo ""

GPU=$(detect_gpu)
info "Distro: $DISTRO | GPU: $GPU"

# ===============================================================================
#  FASE 1  Kernel
# ===============================================================================
step "Ottimizzazione kernel"
cat > /etc/sysctl.d/99-kiosk.conf << 'SYSCTL'
kernel.panic                    = 30
kernel.panic_on_oops            = 1
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
    sed -i 's/\(ext[234]\|btrfs\|f2fs\)\(.*defaults\)/\1\2,noatime/' /etc/fstab 2>/dev/null || true
fi
if ! grep -q "tmpfs.*\/tmp" /etc/fstab 2>/dev/null; then
    echo "tmpfs /tmp tmpfs defaults,noatime,nosuid,nodev,size=256M 0 0" >> /etc/fstab
    mount -t tmpfs -o size=256M tmpfs /tmp 2>/dev/null || true
fi
mkdir -p /etc/systemd/system.conf.d/
cat > /etc/systemd/system.conf.d/kiosk.conf << 'SYSD'
[Manager]
DefaultOOMPolicy=continue
DefaultTimeoutStartSec=20s
DefaultTimeoutStopSec=10s
SYSD
modprobe softdog 2>/dev/null && {
    echo "softdog" >> /etc/modules-load.d/kiosk.conf 2>/dev/null || true
    cat >> /etc/systemd/system.conf.d/kiosk.conf << 'WDOG'
RuntimeWatchdogSec=60
RebootWatchdogSec=10m
WDOG
}
ok "Kernel configurato."

# ===============================================================================
#  FASE 2  Pacchetti
# ===============================================================================
step "Installazione pacchetti"
pkg_update

# Java
JAVA_PKG="default-jre"
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) JAVA_PKG="java-21-openjdk-headless" ;;
    arch|manjaro) JAVA_PKG="jre21-openjdk-headless" ;;
    opensuse*|suse*) JAVA_PKG="java-21-openjdk-headless" ;;
esac

# Mesa / OpenGL (necessario per JavaFX prism)
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky)
        MESA_PKGS="mesa-dri-drivers mesa-libGL mesa-libEGL" ;;
    arch|manjaro)
        MESA_PKGS="mesa" ;;
    opensuse*|suse*)
        MESA_PKGS="Mesa-dri Mesa-libGL1 Mesa-libEGL1" ;;
    *)  MESA_PKGS="libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa libgles2-mesa" ;;
esac

pkg_install \
    cage xwayland \
    dbus dbus-user-session \
    fonts-dejavu-core fonts-noto-color-emoji \
    ca-certificates curl wget unzip \
    pciutils util-linux procps iproute2 \
    lm-sensors \
    "${JAVA_PKG}" \
    ${MESA_PKGS}

pkg_install openjfx 2>/dev/null || true
pkg_install unclutter-xfixes 2>/dev/null || true

# Driver GPU specifici
case "$GPU" in
    nvidia)
        case "$DISTRO" in
            debian|ubuntu)
                pkg_install nvidia-driver 2>/dev/null || \
                pkg_install xserver-xorg-video-nouveau 2>/dev/null || true ;;
            arch*) pkg_install mesa 2>/dev/null || true ;;
        esac ;;
    intel)
        case "$DISTRO" in
            debian|ubuntu)
                pkg_install xserver-xorg-video-intel intel-media-va-driver 2>/dev/null || true ;;
            arch*) pkg_install mesa vulkan-intel 2>/dev/null || true ;;
        esac ;;
    amd)
        case "$DISTRO" in
            debian|ubuntu)
                pkg_install xserver-xorg-video-amdgpu firmware-amd-graphics 2>/dev/null || true ;;
            arch*) pkg_install mesa vulkan-radeon 2>/dev/null || true ;;
        esac ;;
    vm|unknown)
        info "VM / GPU sconosciuta: software rendering."
        case "$DISTRO" in
            debian|ubuntu)
                pkg_install libgl1-mesa-swrast libgl1-mesa-dri 2>/dev/null || true ;;
            *) pkg_install mesa 2>/dev/null || true ;;
        esac ;;
esac
ok "Pacchetti installati."

# ===============================================================================
#  FASE 3  Utente kiosk
# ===============================================================================
step "Utente kiosk"
if ! id "${KIOSK_USER}" &>/dev/null; then
    useradd -m -s /bin/bash -G video,input,render,audio "${KIOSK_USER}" 2>/dev/null || \
    useradd -m -s /bin/bash "${KIOSK_USER}"
    passwd -d "${KIOSK_USER}"
    ok "Utente '${KIOSK_USER}' creato."
else
    usermod -aG video,input,render,audio "${KIOSK_USER}" 2>/dev/null || true
    info "Utente '${KIOSK_USER}' esistente."
fi
KIOSK_HOME=$(eval echo ~"${KIOSK_USER}")
KIOSK_UID=$(id -u "${KIOSK_USER}")

# ===============================================================================
#  FASE 4  Download app e JavaFX
# ===============================================================================
step "Download applicazione"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/models"

# JAR principale
info "Download demo-1.jar..."
if curl -fsSL "${APP_JAR_URL}" -o "${APP_DIR}/demo-1.jar" 2>/dev/null; then
    ok "JAR scaricato."
else
    warn "Download JAR da GitHub fallito."
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo ".")"
    [[ -f "${SCRIPT_DIR}/dist/demo-1.jar" ]] \
        && cp "${SCRIPT_DIR}/dist/demo-1.jar" "${APP_DIR}/" \
        && ok "JAR copiato da locale." \
        || fail "JAR non trovato. Impossibile continuare."
fi

# Librerie (Ikonli, Gson)
info "Download librerie..."
if curl -fsSL "${APP_LIB_URL}" 2>/dev/null | tar -xz -C "${APP_DIR}/lib/"; then
    ok "Librerie scaricate."
else
    warn "Download lib.tar.gz fallito."
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo ".")"
    ls "${SCRIPT_DIR}/dist/lib/"*.jar &>/dev/null 2>&1 \
        && cp "${SCRIPT_DIR}/dist/lib/"*.jar "${APP_DIR}/lib/" \
        && ok "Librerie copiate da locale." \
        || warn "Nessuna libreria trovata."
fi

# --- JavaFX SDK (nativi Linux  necessari per JavaFX su sistemi senza desktop) -
step "Download JavaFX SDK ${JAVAFX_VERSION}"
JAVAFX_DIR="${APP_DIR}/javafx-sdk"

if [[ ! -d "${JAVAFX_DIR}" ]]; then
    # Prima prova: usa openjfx di sistema se disponibile
    FX_SYSTEM=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" \
                     2>/dev/null | head -1 | xargs dirname 2>/dev/null || echo "")
    if [[ -n "${FX_SYSTEM}" ]]; then
        info "JavaFX di sistema trovato: ${FX_SYSTEM}"
        mkdir -p "${JAVAFX_DIR}"
        cp "${FX_SYSTEM}"/*.jar "${JAVAFX_DIR}/" 2>/dev/null || true
        # Copia anche i .so nativi se presenti
        find "${FX_SYSTEM}" -name "*.so" 2>/dev/null | \
            xargs -I{} cp {} "${JAVAFX_DIR}/" 2>/dev/null || true
        ok "JavaFX di sistema usato."
    else
        # Seconda prova: scarica da Gluon
        info "Download JavaFX SDK da Gluon (~80MB, solo JAR+nativi)..."
        FREE_MB=$(df /tmp --output=avail -m 2>/dev/null | tail -1 || echo 999)
        if [[ "${FREE_MB}" -lt 300 ]]; then
            warn "Poco spazio in /tmp (${FREE_MB}MB). Pulizia..."
            apt-get clean 2>/dev/null || true
            rm -rf /tmp/javafx-* 2>/dev/null || true
        fi

        if curl -fsSL "${JAVAFX_URL}" -o /tmp/javafx-sdk.zip 2>/dev/null; then
            mkdir -p /tmp/javafx-extract/
            # Estrai SOLO i JAR e i .so, escludi libjfxwebkit.so (~60MB inutile)
            unzip -q /tmp/javafx-sdk.zip \
                "javafx-sdk-${JAVAFX_VERSION}/lib/*.jar" \
                "javafx-sdk-${JAVAFX_VERSION}/lib/*.so" \
                -d /tmp/javafx-extract/ 2>/dev/null || \
            unzip -q /tmp/javafx-sdk.zip \
                -d /tmp/javafx-extract/ \
                -x "*/libjfxwebkit.so" \
                -x "*/libgstreamer-lite.so" \
                -x "*/src.zip" 2>/dev/null || true

            mkdir -p "${JAVAFX_DIR}"
            cp /tmp/javafx-extract/javafx-sdk-${JAVAFX_VERSION}/lib/* \
               "${JAVAFX_DIR}/" 2>/dev/null || true
            # Rimuovi webkit anche se estratto
            rm -f "${JAVAFX_DIR}/libjfxwebkit.so" \
                  "${JAVAFX_DIR}/libgstreamer-lite.so" 2>/dev/null || true
            rm -rf /tmp/javafx-sdk.zip /tmp/javafx-extract/
            ok "JavaFX SDK installato ($(ls ${JAVAFX_DIR}/*.jar 2>/dev/null | wc -l) JAR)."
        else
            warn "Download JavaFX SDK fallito."
            warn "JavaFX potrebbe non funzionare. Installa manualmente: apt install openjfx"
        fi
    fi
else
    info "JavaFX SDK gia presente."
fi

chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}"
chmod 755 "${APP_DIR}"

# ===============================================================================
#  FASE 5  Script di lancio
# ===============================================================================
step "Script di lancio"

cat > "${APP_DIR}/run-kiosk.sh" << 'LAUNCHER_EOF'
#!/usr/bin/env bash
# ===============================================================================
#  run-kiosk.sh  Auto-healing launcher for JavaFX Kiosk
#
#  On each start:
#    1. Reads saved profile from config/profile.conf
#    2. Tries to launch JavaFX with that profile
#    3. On failure: detects error type and switches to next profile
#    4. Saves the working profile for next boot
# ===============================================================================

APP_DIR="/opt/kiosk"
LOG="${APP_DIR}/logs/kiosk.log"
ERR="${APP_DIR}/logs/kiosk-err.log"
PROFILE_FILE="${APP_DIR}/config/profile.conf"
FAIL_LOG="${APP_DIR}/logs/failures.log"

mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"

log()  { echo "[$(date '+%H:%M:%S')] $*" | tee -a "${LOG}"; }
fail() { echo "[$(date '+%H:%M:%S')] FAIL: $*" | tee -a "${FAIL_LOG}"; }

# -- Find JavaFX module path ---------------------------------------------------
find_fx_path() {
    if [ -d "${APP_DIR}/javafx-sdk" ] && \
       ls "${APP_DIR}/javafx-sdk/"*.jar >/dev/null 2>&1; then
        echo "${APP_DIR}/javafx-sdk"
        return
    fi
    FX_SYS=$(find /usr/share/java /usr/lib/jvm \
                  -name "javafx.controls.jar" 2>/dev/null | \
             head -1 | xargs -r dirname 2>/dev/null || echo "")
    if [ -n "${FX_SYS}" ]; then
        echo "${FX_SYS}"
        return
    fi
    echo "${APP_DIR}/lib"
}

# -- Build classpath -----------------------------------------------------------
build_cp() {
    CP="${APP_DIR}/demo-1.jar"
    for jar in "${APP_DIR}/lib/"*.jar; do
        [ -f "$jar" ] && CP="${CP}:${jar}"
    done
    echo "${CP}"
}

# -- Load / save profile -------------------------------------------------------
load_profile() {
    PROFILE="default"
    [ -f "${PROFILE_FILE}" ] && . "${PROFILE_FILE}" || true
}

save_profile() {
    echo "PROFILE=${1}" > "${PROFILE_FILE}"
    log "Profile saved: ${1}"
}

# -- Detect error type from output --------------------------------------------
detect_error() {
    OUTPUT="${1}"
    if   echo "${OUTPUT}" | grep -qi "Module javafx.*not found\|boot layer"; then
        echo "missing_javafx"
    elif echo "${OUTPUT}" | grep -qi "Unable to open DISPLAY\|no display"; then
        echo "no_display"
    elif echo "${OUTPUT}" | grep -qi "GtkApplication\|UnsupportedOperation.*DISPLAY"; then
        echo "gtk_display"
    elif echo "${OUTPUT}" | grep -qi "connection to the bus\|dbus"; then
        echo "no_dbus"
    elif echo "${OUTPUT}" | grep -qi "libGL\|MESA\|OpenGL\|prism\|es2pipe\|failed to create"; then
        echo "opengl"
    elif echo "${OUTPUT}" | grep -qi "OutOfMemoryError\|heap space\|GC overhead"; then
        echo "oom"
    elif echo "${OUTPUT}" | grep -qi "Could not find or load main class\|ClassNotFoundException"; then
        echo "class_not_found"
    elif echo "${OUTPUT}" | grep -qi "cage.*exit\|compositor\|wayland"; then
        echo "cage_failed"
    else
        echo "unknown"
    fi
}

# -- Build java command for a given profile -----------------------------------
# Uses a plain string instead of arrays for maximum shell compatibility
build_java_cmd() {
    PROFILE="${1}"
    FX_PATH=$(find_fx_path)
    CP=$(build_cp)

    # Common flags
    BASE_FLAGS="-Xms64m -Xmx256m"
    BASE_FLAGS="${BASE_FLAGS} --module-path ${FX_PATH}"
    BASE_FLAGS="${BASE_FLAGS} --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base"
    BASE_FLAGS="${BASE_FLAGS} --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"
    BASE_FLAGS="${BASE_FLAGS} --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    BASE_FLAGS="${BASE_FLAGS} -Djava.awt.headless=false"
    BASE_FLAGS="${BASE_FLAGS} -Djavafx.animation.fullspeed=true"
    BASE_FLAGS="${BASE_FLAGS} -Dfile.encoding=UTF-8"
    BASE_FLAGS="${BASE_FLAGS} -XX:+UseG1GC -XX:MaxGCPauseMillis=50"
    BASE_FLAGS="${BASE_FLAGS} -cp ${CP}"

    case "${PROFILE}" in
        default)
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_RENDERER_ALLOW_SOFTWARE=1
            export WLR_NO_HARDWARE_CURSORS=1
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            PROFILE_FLAGS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        sw_pixman)
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_RENDERER_ALLOW_SOFTWARE=1
            export WLR_NO_HARDWARE_CURSORS=1
            export MESA_GL_VERSION_OVERRIDE=4.5
            export GALLIUM_DRIVER=softpipe
            PROFILE_FLAGS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        sw_mesa)
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_NO_HARDWARE_CURSORS=1
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=llvmpipe
            PROFILE_FLAGS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        gtk2)
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_NO_HARDWARE_CURSORS=1
            PROFILE_FLAGS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2"
            ;;
        xwayland)
            export DISPLAY="${DISPLAY:-:0}"
            export LIBGL_ALWAYS_SOFTWARE=1
            unset WAYLAND_DISPLAY 2>/dev/null || true
            PROFILE_FLAGS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        es2_hw)
            unset LIBGL_ALWAYS_SOFTWARE 2>/dev/null || true
            export WLR_RENDERER=gles2
            PROFILE_FLAGS="-Dprism.order=es2,sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        *)
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_RENDERER_ALLOW_SOFTWARE=1
            export WLR_NO_HARDWARE_CURSORS=1
            export GALLIUM_DRIVER=softpipe
            PROFILE_FLAGS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
    esac

    echo "java ${BASE_FLAGS} ${PROFILE_FLAGS} com.example.App"
}

# -- Next profile after failure -----------------------------------------------
next_profile() {
    CURRENT="${1}"
    ERROR="${2}"

    case "${ERROR}" in
        missing_javafx)
            log "Attempting JavaFX SDK download..."
            curl -fsSL \
                "https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_linux-x64_bin-sdk.zip" \
                -o /tmp/javafx.zip 2>/dev/null && {
                mkdir -p /tmp/fxext/
                unzip -q /tmp/javafx.zip -d /tmp/fxext/ \
                    -x "*/libjfxwebkit.so" 2>/dev/null || true
                mkdir -p "${APP_DIR}/javafx-sdk"
                cp /tmp/fxext/javafx-sdk-21.0.2/lib/*.jar \
                   "${APP_DIR}/javafx-sdk/" 2>/dev/null || true
                rm -rf /tmp/javafx.zip /tmp/fxext/
                log "JavaFX SDK downloaded."
            } || {
                apt-get install -y openjfx 2>/dev/null || true
                log "Tried apt install openjfx."
            }
            echo "default"
            ;;
        opengl)
            case "${CURRENT}" in
                default)    echo "sw_pixman" ;;
                sw_pixman)  echo "sw_mesa"   ;;
                sw_mesa)    echo "gtk2"      ;;
                *)          echo "fallback"  ;;
            esac
            ;;
        no_display|gtk_display)
            case "${CURRENT}" in
                default)    echo "xwayland"  ;;
                xwayland)   echo "sw_pixman" ;;
                *)          echo "fallback"  ;;
            esac
            ;;
        cage_failed)
            case "${CURRENT}" in
                default)    echo "sw_pixman" ;;
                sw_pixman)  echo "xwayland"  ;;
                *)          echo "fallback"  ;;
            esac
            ;;
        oom)
            log "OOM: increasing heap to 512m"
            sed -i 's/-Xmx256m/-Xmx512m/g' "${APP_DIR}/run-kiosk.sh" 2>/dev/null || true
            echo "${CURRENT}"
            ;;
        class_not_found)
            log "JAR missing? Re-downloading..."
            curl -fsSL \
                "https://github.com/accaicedtea/ttetttos/releases/download/v1.0.0/demo-1.jar" \
                -o "${APP_DIR}/demo-1.jar" 2>/dev/null && \
                log "JAR re-downloaded." || log "Re-download failed."
            echo "default"
            ;;
        *)
            case "${CURRENT}" in
                default)    echo "sw_pixman" ;;
                sw_pixman)  echo "sw_mesa"   ;;
                sw_mesa)    echo "gtk2"      ;;
                gtk2)       echo "xwayland"  ;;
                xwayland)   echo "es2_hw"    ;;
                *)          echo "fallback"  ;;
            esac
            ;;
    esac
}

# -- Main loop ----------------------------------------------------------------
load_profile
CURRENT_PROFILE="${PROFILE}"
ATTEMPT=0

log "==============================="
log "Kiosk start  profile=${CURRENT_PROFILE}"
log "FX path: $(find_fx_path)"
log "==============================="

while true; do
    ATTEMPT=$(( ATTEMPT + 1 ))
    export TOTEM_API_KEY="${TOTEM_API_KEY:-api_key_totem_1}"

    JAVA_CMD=$(build_java_cmd "${CURRENT_PROFILE}")
    log "Attempt ${ATTEMPT}  profile=${CURRENT_PROFILE}"

    TMPOUT=$(mktemp /tmp/kiosk-out.XXXXXX)

    set +e
    eval "${JAVA_CMD}" 2>&1 | tee -a "${ERR}" > "${TMPOUT}"
    EXIT_CODE=$?
    set -e

    OUTPUT=$(cat "${TMPOUT}" 2>/dev/null || echo "")
    rm -f "${TMPOUT}"

    # Clean exit (Ctrl+Alt+H pressed)
    if [ "${EXIT_CODE}" -eq 0 ]; then
        log "Clean exit."
        exit 0
    fi

    ERROR_TYPE=$(detect_error "${OUTPUT}")
    fail "exit=${EXIT_CODE} type=${ERROR_TYPE} profile=${CURRENT_PROFILE} attempt=${ATTEMPT}"
    log "Error: ${ERROR_TYPE}"

    if [ "${ATTEMPT}" -gt 6 ]; then
        log "Too many failures. Waiting 30s then resetting to default..."
        sleep 30
        ATTEMPT=0
        CURRENT_PROFILE="default"
        save_profile "default"
        continue
    fi

    NEXT=$(next_profile "${CURRENT_PROFILE}" "${ERROR_TYPE}")
    log "Next profile: ${NEXT}"

    if [ "${NEXT}" != "${CURRENT_PROFILE}" ]; then
        save_profile "${NEXT}"
        CURRENT_PROFILE="${NEXT}"
    fi

    WAIT=$(( ATTEMPT * 3 ))
    [ "${WAIT}" -gt 15 ] && WAIT=15
    log "Waiting ${WAIT}s..."
    sleep "${WAIT}"
done
LAUNCHER_EOF
chmod +x "${APP_DIR}/run-kiosk.sh"
chmod +x "${APP_DIR}/run-kiosk.sh"

# --- Script controllo ---------------------------------------------------------
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRL'
#!/usr/bin/env bash
case "${1:-help}" in
    start)   systemctl start  kiosk.service ;;
    stop)    touch /opt/kiosk/.stop; systemctl stop kiosk.service ;;
    restart) rm -f /opt/kiosk/.stop; systemctl restart kiosk.service ;;
    status)  systemctl status kiosk.service; echo ""; tail -30 /opt/kiosk/logs/kiosk.log ;;
    log)     journalctl -u kiosk.service -f ;;
    update)
        VER="${2:-v1.0.0}"
        URL="https://github.com/accaicedtea/ttetttos/releases/download/${VER}/demo-1.jar"
        curl -fsSL "${URL}" -o /tmp/kiosk-update.jar && {
            systemctl stop kiosk.service 2>/dev/null || true
            cp /tmp/kiosk-update.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /opt/kiosk/.stop /tmp/kiosk-update.jar
            systemctl start kiosk.service
            echo "Aggiornato a ${VER}."
        } || echo "Download fallito."
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|update [tag]}"
        ;;
esac
CTRL
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control
chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}"
ok "Script configurati."

# ===============================================================================
#  FASE 6  Cursore trasparente
# ===============================================================================
step "Cursore trasparente"
mkdir -p /usr/share/icons/blank-cursor/cursors
python3 - << 'PYEOF'
import struct, os
data = (b'Xcur' + struct.pack('<III', 16, 0x10000, 1) +
        struct.pack('<III', 0xFFFD0002, 24, 28) +
        struct.pack('<IIIIIIIII', 36, 0xFFFD0002, 24, 1, 1, 1, 0, 0, 50) +
        b'\x00\x00\x00\x00')
base = '/usr/share/icons/blank-cursor/cursors/left_ptr'
open(base, 'wb').write(data)
for n in ['default','arrow','pointer','hand','hand1','hand2','crosshair',
          'text','xterm','wait','watch','grabbing','grab','move',
          'X_cursor','right_ptr','top_left_arrow','progress','not-allowed']:
    dst = f'/usr/share/icons/blank-cursor/cursors/{n}'
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
PYEOF
cat > /usr/share/icons/blank-cursor/index.theme << 'THEME'
[Icon Theme]
Name=blank-cursor
Comment=Transparent cursor
Inherits=hicolor
THEME
ok "Cursore trasparente."

# ===============================================================================
#  FASE 7  Servizio systemd
# ===============================================================================
step "Servizio systemd"

# Crea XDG runtime dir persistente
mkdir -p "/run/user/${KIOSK_UID}"
chmod 700 "/run/user/${KIOSK_UID}"
chown "${KIOSK_USER}:${KIOSK_USER}" "/run/user/${KIOSK_UID}"

# Abilita linger (mantiene la sessione attiva al boot)
loginctl enable-linger "${KIOSK_USER}" 2>/dev/null || true

cat > /etc/systemd/system/kiosk.service << SVCEOF
[Unit]
Description=Kiosk JavaFX Totem 24/7
After=network-online.target systemd-user-sessions.service local-fs.target
Wants=network-online.target
StartLimitIntervalSec=120
StartLimitBurst=5

[Service]
Type=simple
User=${KIOSK_USER}
Group=${KIOSK_USER}
WorkingDirectory=${APP_DIR}

# Ambiente
Environment="HOME=${KIOSK_HOME}"
Environment="XDG_RUNTIME_DIR=/run/user/${KIOSK_UID}"
Environment="XCURSOR_THEME=blank-cursor"
Environment="XCURSOR_SIZE=24"
Environment="TOTEM_API_KEY=${API_KEY}"

# Software rendering  compatibile con VM e hardware senza GPU
Environment="LIBGL_ALWAYS_SOFTWARE=1"
Environment="MESA_GL_VERSION_OVERRIDE=3.3"
Environment="GALLIUM_DRIVER=softpipe"
Environment="WLR_RENDERER=pixman"
Environment="WLR_RENDERER_ALLOW_SOFTWARE=1"
Environment="WLR_NO_HARDWARE_CURSORS=1"

# Prepara runtime dir
ExecStartPre=/bin/bash -c 'if [ -f ${APP_DIR}/.stop ]; then rm -f ${APP_DIR}/.stop; exit 1; fi'
ExecStartPre=/bin/bash -c 'mkdir -p /run/user/${KIOSK_UID} && chmod 700 /run/user/${KIOSK_UID} && chown ${KIOSK_USER}:${KIOSK_USER} /run/user/${KIOSK_UID}'

# Avvia Cage (Wayland compositor minimale) + app
ExecStart=/usr/bin/dbus-run-session /usr/bin/cage -d -- ${APP_DIR}/run-kiosk.sh

Restart=always
RestartSec=5s
MemoryMax=600M
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

# Timer pulizia log
cat > /etc/systemd/system/kiosk-logclean.service << 'L'
[Unit]
Description=Pulizia log kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c 'f=/opt/kiosk/logs/kiosk.log; [[ -f "$f" ]] && [[ $(wc -c < "$f") -gt 5242880 ]] && tail -500 "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"; journalctl --vacuum-size=50M 2>/dev/null||true'
L
cat > /etc/systemd/system/kiosk-logclean.timer << 'L'
[Unit]
Description=Pulizia log giornaliera
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
L

# Riavvio notturno
cat > /etc/systemd/system/kiosk-nightly-restart.service << 'N'
[Unit]
Description=Riavvio notturno kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c 'rm -f /opt/kiosk/.stop; systemctl restart kiosk.service'
N
cat > /etc/systemd/system/kiosk-nightly-restart.timer << 'N'
[Unit]
Description=Riavvio kiosk 04:00
[Timer]
OnCalendar=04:00
RandomizedDelaySec=300
[Install]
WantedBy=timers.target
N

ok "Servizi systemd creati."

# ===============================================================================
#  FASE 8  Auto-login TTY1
# ===============================================================================
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
        echo "Kiosk attivo. Premi Invio per il terminale."
        read -r _
    else
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
        export XCURSOR_THEME=blank-cursor
        export LIBGL_ALWAYS_SOFTWARE=1
        export WLR_RENDERER=pixman
        export WLR_NO_HARDWARE_CURSORS=1
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

# ===============================================================================
#  FASE 9  Boot veloce
# ===============================================================================
step "Ottimizzazione boot"
for svc in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer \
           dnf-makecache.timer man-db.timer; do
    systemctl disable "${svc}" 2>/dev/null || true
    systemctl mask    "${svc}" 2>/dev/null || true
done
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done
systemctl set-default multi-user.target
if [[ -f /etc/default/grub ]]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
fi
ok "Boot ottimizzato."

# ===============================================================================
#  FASE 10  Permessi e abilitazione
# ===============================================================================
step "Permessi hardware e abilitazione servizi"
cat > /etc/udev/rules.d/99-kiosk.rules << 'UDEV'
SUBSYSTEM=="drm",   TAG+="uaccess", GROUP="video"
SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"
SUBSYSTEM=="usb",   TAG+="uaccess"
UDEV
udevadm control --reload-rules 2>/dev/null || true

mkdir -p /etc/systemd/journald.conf.d/
cat > /etc/systemd/journald.conf.d/kiosk.conf << 'J'
[Journal]
SystemMaxUse=50M
RuntimeMaxUse=20M
Compress=yes
ForwardToSyslog=no
J

systemctl daemon-reload
systemctl enable kiosk.service
systemctl enable kiosk-logclean.timer
systemctl enable kiosk-nightly-restart.timer
ok "Servizi abilitati."

# ===============================================================================
#  RIEPILOGO
# ===============================================================================
echo ""
echo "+===================================================================+"
echo "|                  SETUP COMPLETATO                                |"
echo "+===================================================================+"
echo "|  Distro: ${DISTRO} | GPU: ${GPU}                                 |"
echo "|                                                                   |"
echo "|  Avvia subito:   systemctl start kiosk.service                   |"
echo "|  Oppure:         reboot                                           |"
echo "|                                                                   |"
echo "|  Diagnostica:    kiosk-control status                            |"
echo "|                  journalctl -u kiosk -f                          |"
echo "|                  tail -f /opt/kiosk/logs/kiosk.log               |"
echo "|                  tail -f /opt/kiosk/logs/kiosk-err.log           |"
echo "+===================================================================+"
echo ""