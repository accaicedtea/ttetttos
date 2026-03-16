#!/usr/bin/env bash
# setup-kiosk.sh - Kiosk JavaFX 24/7
# Uso:
#   curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash
#   sudo bash setup-kiosk.sh reset
#   sudo bash setup-kiosk.sh update

set -euo pipefail

GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
RELEASE_TAG="v1.0.0"
JAR_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/demo-1.jar"
LIB_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/lib.tar.gz"
FX_URL="https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_linux-x64_bin-sdk.zip"
FX_VER="21.0.2"
KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
LOG="/var/log/kiosk-setup.log"
API_KEY="${TOTEM_API_KEY:-api_key_totem_1}"

mkdir -p "$(dirname "$LOG")"
exec > >(tee -a "$LOG") 2>&1

info() { echo "[INFO] $*"; }
ok()   { echo "[ OK ] $*"; }
warn() { echo "[WARN] $*"; }
fail() { echo "[FAIL] $*"; exit 1; }
step() { echo ""; echo "=== $* ==="; }

[[ $EUID -eq 0 ]] || fail "Eseguire come root: sudo bash $0"

# Rileva distro
if [[ -f /etc/os-release ]]; then
    source /etc/os-release
    DISTRO="${ID:-unknown}"
elif [[ -f /etc/debian_version ]]; then
    DISTRO="debian"
else
    DISTRO="unknown"
fi
info "Distro: $DISTRO"

# Package manager
pkg() {
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@" 2>/dev/null || true ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf install -y "$@" 2>/dev/null || true ;;
        arch|manjaro|endeavouros)
            pacman -S --noconfirm --needed "$@" 2>/dev/null || true ;;
        opensuse*|suse*)
            zypper install -y "$@" 2>/dev/null || true ;;
        *)
            apt-get install -y "$@" 2>/dev/null || dnf install -y "$@" 2>/dev/null || true ;;
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

# Rileva se VM
is_vm() {
    lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio-vga\|virgl" && return 0
    systemd-detect-virt 2>/dev/null | grep -qv "none" && return 0
    return 1
}

# ===============================================================================
#  RESET
# ===============================================================================
if [[ "${1:-}" == "reset" ]]; then
    step "RESET COMPLETO"
    for svc in kiosk kiosk-logclean kiosk-nightly-restart kiosk-network-watchdog; do
        systemctl stop    "${svc}.service" 2>/dev/null || true
        systemctl stop    "${svc}.timer"   2>/dev/null || true
        systemctl disable "${svc}.service" 2>/dev/null || true
        systemctl disable "${svc}.timer"   2>/dev/null || true
        rm -f "/etc/systemd/system/${svc}.service"
        rm -f "/etc/systemd/system/${svc}.timer"
    done
    systemctl daemon-reload
    if id "${KIOSK_USER}" &>/dev/null; then
        pkill -u "${KIOSK_USER}" 2>/dev/null || true
        sleep 1
        userdel -r "${KIOSK_USER}" 2>/dev/null || true
    fi
    rm -rf "${APP_DIR}"
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -rf /etc/systemd/system.conf.d/kiosk.conf
    rm -rf /usr/share/icons/blank-cursor
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done
    systemctl set-default multi-user.target 2>/dev/null || true
    systemctl daemon-reload
    ok "Reset completato. Reinstallazione..."
    echo ""
fi

# ===============================================================================
#  UPDATE
# ===============================================================================
if [[ "${1:-}" == "update" ]]; then
    step "Aggiornamento JAR"
    systemctl stop kiosk.service 2>/dev/null || true
    curl -fsSL "${JAR_URL}" -o "${APP_DIR}/demo-1.jar" \
        && chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar" \
        && rm -f "${APP_DIR}/.stop" \
        && systemctl start kiosk.service \
        && ok "Aggiornato." \
        || fail "Download fallito."
    exit 0
fi

echo ""
echo "+-----------------------------------------------+"
echo "|   KIOSK SETUP - $(date '+%Y-%m-%d')              |"
echo "+-----------------------------------------------+"
echo ""

# ===============================================================================
#  PREFLIGHT
# ===============================================================================
step "Preflight - verifica ambiente"

ERRORS=0

# RAM
RAM_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo 0)
if [[ $RAM_MB -lt 512 ]]; then
    warn "RAM: ${RAM_MB}MB - insufficiente (minimo 512MB)"
    ERRORS=$((ERRORS+1))
else
    ok "RAM: ${RAM_MB}MB"
fi

# Disco
DISK_MB=$(df "${APP_DIR%/*}" --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
[[ $DISK_MB -lt 100 ]] && { info "Creazione /opt..."; mkdir -p /opt; DISK_MB=$(df /opt --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0); }
if [[ $DISK_MB -lt 1000 ]]; then
    warn "Disco libero: ${DISK_MB}MB - consigliati almeno 1GB"
else
    ok "Disco libero: ${DISK_MB}MB"
fi

# Internet
if curl -sf --max-time 8 https://github.com -o /dev/null 2>/dev/null; then
    ok "GitHub raggiungibile"
else
    warn "GitHub non raggiungibile - verifica la connessione"
    ERRORS=$((ERRORS+1))
fi

if curl -sf --max-time 8 "https://hasanabdelaziz.altervista.org" -o /dev/null 2>/dev/null; then
    ok "Server API raggiungibile"
else
    warn "Server API non raggiungibile - funziona in modalita offline"
fi

# Java
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')
    JAVA_MAJ=$(echo "$JAVA_VER" | cut -d. -f1)
    if [[ "${JAVA_MAJ:-0}" -ge 17 ]]; then
        ok "Java: $JAVA_VER"
    else
        warn "Java $JAVA_VER trovato, necessario >= 17 - verra aggiornato"
    fi
else
    warn "Java non installato - verra installato"
fi

# Cage e dbus
command -v cage           &>/dev/null && ok "cage trovato"         || warn "cage non trovato - verra installato"
command -v dbus-run-session &>/dev/null && ok "dbus-run-session trovato" || warn "dbus-run-session non trovato - verra installato"
command -v systemctl      &>/dev/null && ok "systemd trovato"      || { warn "systemd non trovato"; ERRORS=$((ERRORS+1)); }

# VM check
if is_vm; then
    ok "Macchina Virtuale rilevata - software rendering abilitato"
    VM=true
else
    ok "Hardware fisico - rendering hardware disponibile"
    VM=false
fi

if [[ $ERRORS -gt 0 ]]; then
    warn "${ERRORS} problema/i critico/i rilevato/i - il setup potrebbe fallire"
    info "Attendi 5 secondi o premi Ctrl+C per interrompere..."
    sleep 5
fi

ok "Preflight completato."

# ===============================================================================
#  FASE 1 - Kernel
# ===============================================================================
step "Configurazione kernel"

cat > /etc/sysctl.d/99-kiosk.conf << 'EOF'
kernel.panic = 30
kernel.panic_on_oops = 1
vm.swappiness = 5
vm.overcommit_memory = 1
vm.oom_kill_allocating_task = 1
net.ipv4.tcp_keepalive_time = 60
net.ipv4.tcp_tw_reuse = 1
fs.inotify.max_user_watches = 524288
EOF
sysctl -p /etc/sysctl.d/99-kiosk.conf > /dev/null 2>&1 || true

if ! grep -q "tmpfs.*\/tmp" /etc/fstab 2>/dev/null; then
    echo "tmpfs /tmp tmpfs defaults,noatime,nosuid,nodev,size=256M 0 0" >> /etc/fstab
    mount -t tmpfs -o size=256M tmpfs /tmp 2>/dev/null || true
fi

mkdir -p /etc/systemd/system.conf.d/
cat > /etc/systemd/system.conf.d/kiosk.conf << 'EOF'
[Manager]
DefaultOOMPolicy=continue
DefaultTimeoutStartSec=20s
DefaultTimeoutStopSec=10s
EOF

ok "Kernel configurato."

# ===============================================================================
#  FASE 2 - Pacchetti
# ===============================================================================
step "Installazione pacchetti"
pkg_update

case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) JAVA_PKG="java-21-openjdk-headless" ;;
    arch|manjaro|endeavouros)           JAVA_PKG="jre21-openjdk-headless" ;;
    opensuse*|suse*)                    JAVA_PKG="java-21-openjdk-headless" ;;
    *)                                  JAVA_PKG="default-jre" ;;
esac

case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky) MESA="mesa-dri-drivers mesa-libGL mesa-libEGL" ;;
    arch|manjaro|endeavouros)           MESA="mesa" ;;
    opensuse*|suse*)                    MESA="Mesa-dri Mesa-libGL1" ;;
    *)                                  MESA="libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa libgles2-mesa" ;;
esac

pkg cage xwayland dbus dbus-user-session \
    fonts-dejavu-core \
    ca-certificates curl wget unzip \
    pciutils util-linux procps iproute2 lm-sensors \
    "${JAVA_PKG}" ${MESA}

pkg fonts-noto-color-emoji 2>/dev/null || true
pkg openjfx              2>/dev/null || true
pkg unclutter-xfixes     2>/dev/null || true

ok "Pacchetti installati."

# ===============================================================================
#  FASE 3 - Utente kiosk
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
ok "UID kiosk: ${KIOSK_UID}"

# ===============================================================================
#  FASE 4 - Download app
# ===============================================================================
step "Download app da GitHub"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/config"

info "Scarico demo-1.jar..."
if curl -fsSL --retry 3 "${JAR_URL}" -o "${APP_DIR}/demo-1.jar"; then
    ok "demo-1.jar scaricato: $(du -h "${APP_DIR}/demo-1.jar" | cut -f1)"
else
    fail "Impossibile scaricare demo-1.jar da ${JAR_URL}"
fi

info "Scarico librerie..."
if curl -fsSL --retry 3 "${LIB_URL}" | tar -xz -C "${APP_DIR}/lib/"; then
    LIB_COUNT=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l)
    ok "Librerie scaricate: ${LIB_COUNT} JAR in ${APP_DIR}/lib/"
else
    warn "Download lib.tar.gz fallito - l'app potrebbe non avviarsi"
fi

# ===============================================================================
#  FASE 5 - JavaFX SDK
# ===============================================================================
step "JavaFX SDK ${FX_VER}"

JAVAFX_DIR="${APP_DIR}/javafx-sdk"

if [[ -d "${JAVAFX_DIR}" ]] && ls "${JAVAFX_DIR}/"*.jar &>/dev/null 2>&1; then
    FX_COUNT=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l)
    ok "JavaFX SDK gia presente: ${FX_COUNT} JAR"
else
    # Prova prima openjfx di sistema
    FX_SYSTEM=""
    FX_SYSTEM=$(find /usr/share/java /usr/lib/jvm \
                     -name "javafx.controls.jar" 2>/dev/null | head -1 \
                | xargs -I{} dirname {} 2>/dev/null || echo "")

    if [[ -n "${FX_SYSTEM}" ]]; then
        info "Uso JavaFX di sistema: ${FX_SYSTEM}"
        mkdir -p "${JAVAFX_DIR}"
        cp "${FX_SYSTEM}/"*.jar "${JAVAFX_DIR}/" 2>/dev/null || true
        find "${FX_SYSTEM}" -name "*.so" 2>/dev/null \
            -exec cp {} "${JAVAFX_DIR}/" \; 2>/dev/null || true
        ok "JavaFX di sistema copiato."
    else
        # Scarica da Gluon
        info "Download JavaFX SDK da Gluon..."

        # Libera spazio /tmp se necessario
        FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 999)
        if [[ "${FREE_TMP}" -lt 250 ]]; then
            info "Poco spazio in /tmp (${FREE_TMP}MB) - pulizia..."
            apt-get clean 2>/dev/null || true
            rm -rf /tmp/javafx-* /tmp/*.zip 2>/dev/null || true
            FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
            info "Spazio /tmp dopo pulizia: ${FREE_TMP}MB"
        fi

        if curl -fsSL --retry 2 "${FX_URL}" -o /tmp/javafx.zip; then
            info "Download completato. Estrazione..."
            mkdir -p /tmp/fxext

            # Estrai escludendo webkit (~60MB inutile) e media
            unzip -q /tmp/javafx.zip \
                -d /tmp/fxext/ \
                -x "*/libjfxwebkit.so" \
                -x "*/libgstreamer-lite.so" \
                -x "*/src.zip" 2>/dev/null || \
            unzip -q /tmp/javafx.zip \
                -d /tmp/fxext/ 2>/dev/null || true

            mkdir -p "${JAVAFX_DIR}"

            # Copia JAR
            find /tmp/fxext -name "*.jar" \
                -exec cp {} "${JAVAFX_DIR}/" \; 2>/dev/null || true

            # Copia .so nativi (tranne webkit)
            find /tmp/fxext -name "*.so" \
                ! -name "libjfxwebkit.so" \
                ! -name "libgstreamer-lite.so" \
                -exec cp {} "${JAVAFX_DIR}/" \; 2>/dev/null || true

            # Rimuovi webkit se copiato
            rm -f "${JAVAFX_DIR}/libjfxwebkit.so" \
                  "${JAVAFX_DIR}/libgstreamer-lite.so" 2>/dev/null || true

            # Pulizia
            rm -rf /tmp/javafx.zip /tmp/fxext/

            FX_COUNT=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l)
            if [[ "${FX_COUNT}" -gt 0 ]]; then
                ok "JavaFX SDK installato: ${FX_COUNT} JAR in ${JAVAFX_DIR}"
            else
                warn "JavaFX SDK: nessun JAR estratto - controlla lo spazio su disco"
                warn "Installa manualmente: apt install openjfx"
            fi
        else
            warn "Download JavaFX fallito - usa 'apt install openjfx' manualmente"
        fi
    fi
fi

chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}"
chmod 755 "${APP_DIR}"

# ===============================================================================
#  FASE 6 - Verifica Java + JavaFX
# ===============================================================================
step "Verifica Java e JavaFX"

# Determina modulo path
if [[ -d "${JAVAFX_DIR}" ]] && ls "${JAVAFX_DIR}/"*.jar &>/dev/null 2>&1; then
    FX_PATH="${JAVAFX_DIR}"
else
    FX_SYSTEM=$(find /usr/share/java /usr/lib/jvm \
                     -name "javafx.controls.jar" 2>/dev/null | head -1 \
                | xargs -I{} dirname {} 2>/dev/null || echo "")
    FX_PATH="${FX_SYSTEM:-${APP_DIR}/lib}"
fi
info "FX_PATH: ${FX_PATH}"

# Costruisce classpath
CP="${APP_DIR}/demo-1.jar"
for jar in "${APP_DIR}/lib/"*.jar; do
    [[ -f "$jar" ]] && CP="${CP}:${jar}"
done

# Test 1: Java versione
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    ok "Java: ${JAVA_VER}"
else
    warn "Java non trovato dopo installazione - controllare il PATH"
fi

# Test 2: JavaFX moduli accessibili
if [[ -n "${FX_PATH}" ]] && [[ -d "${FX_PATH}" ]]; then
    if java --module-path "${FX_PATH}" \
            --list-modules 2>/dev/null | grep -q "javafx.controls"; then
        ok "JavaFX moduli accessibili in ${FX_PATH}"
    else
        warn "JavaFX moduli non trovati in ${FX_PATH}"
    fi
fi

# Test 3: JAR principale leggibile
if command -v java &>/dev/null && [[ -f "${APP_DIR}/demo-1.jar" ]]; then
    if java -cp "${APP_DIR}/demo-1.jar" --list-modules 2>/dev/null 1>/dev/null \
       || jar tf "${APP_DIR}/demo-1.jar" 2>/dev/null | grep -q "com/example/App"; then
        ok "demo-1.jar valido"
    else
        warn "demo-1.jar: impossibile verificare il contenuto"
    fi
fi

# Test 4: librerie presenti
LIB_COUNT=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l)
if [[ "${LIB_COUNT}" -gt 0 ]]; then
    ok "Librerie: ${LIB_COUNT} JAR in ${APP_DIR}/lib/"
else
    warn "Nessuna libreria in ${APP_DIR}/lib/ - l'app potrebbe non avviarsi"
fi

# ===============================================================================
#  FASE 7 - run-kiosk.sh (self-healing)
# ===============================================================================
step "Creazione script di avvio"

# Salva variabili che servono nello script generato
KIOSK_UID_VAL="${KIOSK_UID}"
API_KEY_VAL="${API_KEY}"
APP_DIR_VAL="${APP_DIR}"
FX_PATH_VAL="${FX_PATH}"

# Scrivi run-kiosk.sh riga per riga - NESSUN heredoc complesso
RUN="${APP_DIR}/run-kiosk.sh"
rm -f "${RUN}"

cat > "${RUN}" << RUNEOF
#!/usr/bin/env bash
APP_DIR="${APP_DIR_VAL}"
LOG="\${APP_DIR}/logs/kiosk.log"
ERR="\${APP_DIR}/logs/kiosk-err.log"
PROFILE_FILE="\${APP_DIR}/config/profile.conf"
API_KEY="${API_KEY_VAL}"
KIOSK_UID="${KIOSK_UID_VAL}"

mkdir -p "\${APP_DIR}/logs" "\${APP_DIR}/config"
log() { echo "[\$(date '+%H:%M:%S')] \$*" | tee -a "\${LOG}"; }

# Carica profilo salvato
PROFILE="default"
[[ -f "\${PROFILE_FILE}" ]] && PROFILE=\$(cat "\${PROFILE_FILE}" | tr -d '[:space:]')

# Determina FX_PATH
find_fx() {
    if [[ -d "\${APP_DIR}/javafx-sdk" ]] && ls "\${APP_DIR}/javafx-sdk/"*.jar &>/dev/null 2>&1; then
        echo "\${APP_DIR}/javafx-sdk"; return
    fi
    FX_SYS=\$(find /usr/share/java /usr/lib/jvm \
                   -name "javafx.controls.jar" 2>/dev/null | head -1 \
              | xargs -I{} dirname {} 2>/dev/null || echo "")
    if [[ -n "\${FX_SYS}" ]]; then
        echo "\${FX_SYS}"; return
    fi
    echo "\${APP_DIR}/lib"
}

# Costruisce classpath
build_cp() {
    local cp="\${APP_DIR}/demo-1.jar"
    for j in "\${APP_DIR}/lib/"*.jar; do
        [[ -f "\$j" ]] && cp="\${cp}:\${j}"
    done
    echo "\${cp}"
}

# Analizza errore
detect_error() {
    local out="\$1"
    echo "\${out}" | grep -qi "Module javafx.*not found\|boot layer\|FindException" && echo "no_javafx" && return
    echo "\${out}" | grep -qi "Unable to open DISPLAY\|no display\|GtkApplication" && echo "no_display" && return
    echo "\${out}" | grep -qi "dbus\|bus.*made\|connection to the bus" && echo "no_dbus" && return
    echo "\${out}" | grep -qi "libGL\|MESA\|prism\|es2pipe\|OpenGL" && echo "opengl" && return
    echo "\${out}" | grep -qi "OutOfMemoryError\|heap space" && echo "oom" && return
    echo "\${out}" | grep -qi "cage.*exit\|compositor" && echo "cage" && return
    echo "unknown"
}

# Applica profilo - setta variabili ambiente
apply() {
    export LIBGL_ALWAYS_SOFTWARE=1
    export WLR_NO_HARDWARE_CURSORS=1
    export WLR_RENDERER_ALLOW_SOFTWARE=1
    export TOTEM_API_KEY="\${API_KEY}"
    case "\${PROFILE}" in
        default)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            JAVA_EXTRA="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        pixman)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=4.5
            export GALLIUM_DRIVER=softpipe
            JAVA_EXTRA="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        llvmpipe)
            export WLR_RENDERER=pixman
            export GALLIUM_DRIVER=llvmpipe
            export MESA_GL_VERSION_OVERRIDE=3.3
            JAVA_EXTRA="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        gtk2)
            export WLR_RENDERER=pixman
            JAVA_EXTRA="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2"
            ;;
        xwayland)
            export DISPLAY="\${DISPLAY:-:0}"
            unset WAYLAND_DISPLAY 2>/dev/null || true
            JAVA_EXTRA="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        *)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            JAVA_EXTRA="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
    esac
}

ATTEMPT=0
CURRENT="\${PROFILE}"
FX_PATH=\$(find_fx)
CP=\$(build_cp)

log "========================================="
log "Avvio kiosk - profilo: \${CURRENT}"
log "FX_PATH: \${FX_PATH}"
log "CP jars: \$(echo \${CP} | tr ':' '\n' | wc -l)"
log "========================================="

while true; do
    ATTEMPT=\$((ATTEMPT + 1))
    apply

    FX_PATH=\$(find_fx)
    CP=\$(build_cp)
    log "Tentativo \${ATTEMPT} - profilo '\${CURRENT}'"

    TMPF=\$(mktemp /tmp/kiosk.XXXXXX)
    set +e
    java \
        -Xms64m -Xmx256m \
        --module-path "\${FX_PATH}" \
        --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
        --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
        --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
        -Djava.awt.headless=false \
        -Djavafx.animation.fullspeed=true \
        -Dfile.encoding=UTF-8 \
        -XX:+UseG1GC \
        \${JAVA_EXTRA} \
        -cp "\${CP}" \
        com.example.App 2>&1 | tee -a "\${ERR}" "\${TMPF}"
    RC=\${PIPESTATUS[0]}
    set -e

    OUT=\$(cat "\${TMPF}" 2>/dev/null || echo "")
    rm -f "\${TMPF}"

    if [[ \${RC} -eq 0 ]]; then
        log "Uscita normale."
        exit 0
    fi

    ERR_TYPE=\$(detect_error "\${OUT}")
    log "Errore: \${ERR_TYPE} (exit \${RC}) - profilo '\${CURRENT}'"

    if [[ \${ATTEMPT} -ge 8 ]]; then
        log "Troppi tentativi. Reset profilo e attesa 30s..."
        echo "default" > "\${PROFILE_FILE}"
        CURRENT="default"
        ATTEMPT=0
        sleep 30
        continue
    fi

    # Scegli prossimo profilo
    case "\${ERR_TYPE}" in
        no_javafx)
            log "JavaFX mancante - tentativo reinstall openjfx..."
            apt-get install -y openjfx 2>/dev/null || true
            NEXT="default"
            ;;
        no_display|cage)
            case "\${CURRENT}" in
                default)  NEXT="pixman" ;;
                pixman)   NEXT="xwayland" ;;
                *)        NEXT="default" ;;
            esac ;;
        opengl)
            case "\${CURRENT}" in
                default)  NEXT="pixman" ;;
                pixman)   NEXT="llvmpipe" ;;
                llvmpipe) NEXT="gtk2" ;;
                *)        NEXT="default" ;;
            esac ;;
        oom)
            log "OOM - aumento heap..."
            export JAVA_OPTS="-Xms128m -Xmx512m"
            NEXT="\${CURRENT}"
            ;;
        *)
            case "\${CURRENT}" in
                default)  NEXT="pixman" ;;
                pixman)   NEXT="llvmpipe" ;;
                llvmpipe) NEXT="gtk2" ;;
                gtk2)     NEXT="xwayland" ;;
                *)        NEXT="default" ;;
            esac ;;
    esac

    if [[ "\${NEXT}" != "\${CURRENT}" ]]; then
        log "Cambio profilo: \${CURRENT} -> \${NEXT}"
        echo "\${NEXT}" > "\${PROFILE_FILE}"
        CURRENT="\${NEXT}"
    fi

    WAIT=\$((ATTEMPT * 3))
    [[ \${WAIT} -gt 15 ]] && WAIT=15
    log "Attendo \${WAIT}s..."
    sleep "\${WAIT}"
done
RUNEOF

chmod +x "${RUN}"
ok "run-kiosk.sh creato."

# Verifica che run-kiosk.sh sia sintatticamente valido
if bash -n "${RUN}"; then
    ok "run-kiosk.sh: sintassi OK"
else
    fail "run-kiosk.sh ha errori di sintassi - controllare il log"
fi

# ===============================================================================
#  FASE 8 - kiosk-control
# ===============================================================================
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
case "${1:-help}" in
    start)   systemctl start kiosk.service ;;
    stop)    touch /opt/kiosk/.stop; systemctl stop kiosk.service ;;
    restart) rm -f /opt/kiosk/.stop; systemctl restart kiosk.service ;;
    status)  systemctl status kiosk.service; echo ""; tail -30 /opt/kiosk/logs/kiosk.log ;;
    log)     journalctl -u kiosk.service -f ;;
    errlog)  tail -f /opt/kiosk/logs/kiosk-err.log ;;
    profile) cat /opt/kiosk/config/profile.conf 2>/dev/null || echo "default" ;;
    reset-profile) rm -f /opt/kiosk/config/profile.conf; echo "Profilo resettato." ;;
    update)
        V="${2:-v1.0.0}"
        URL="https://github.com/accaicedtea/ttetttos/releases/download/${V}/demo-1.jar"
        curl -fsSL "${URL}" -o /tmp/kiosk-update.jar && {
            systemctl stop kiosk.service 2>/dev/null || true
            cp /tmp/kiosk-update.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /opt/kiosk/.stop /tmp/kiosk-update.jar
            systemctl start kiosk.service
            echo "Aggiornato a ${V}."
        } || echo "Download fallito."
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|errlog|profile|reset-profile|update [tag]}"
        ;;
esac
CTRLEOF
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control

# ===============================================================================
#  FASE 9 - Cursore trasparente
# ===============================================================================
step "Cursore trasparente"
mkdir -p /usr/share/icons/blank-cursor/cursors
python3 - << 'PYEOF'
import struct, os
data = (b'Xcur'
        + struct.pack('<III', 16, 0x10000, 1)
        + struct.pack('<III', 0xFFFD0002, 24, 28)
        + struct.pack('<IIIIIIIII', 36, 0xFFFD0002, 24, 1, 1, 1, 0, 0, 50)
        + b'\x00\x00\x00\x00')
with open('/usr/share/icons/blank-cursor/cursors/left_ptr', 'wb') as f:
    f.write(data)
for n in ['default','arrow','pointer','hand','hand1','hand2','text',
          'xterm','wait','watch','grabbing','grab','move','progress']:
    dst = f'/usr/share/icons/blank-cursor/cursors/{n}'
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Cursore OK.')
PYEOF
printf '[Icon Theme]\nName=blank-cursor\n' > /usr/share/icons/blank-cursor/index.theme
ok "Cursore trasparente creato."

# ===============================================================================
#  FASE 10 - Servizio systemd
# ===============================================================================
step "Servizio systemd"

mkdir -p "/run/user/${KIOSK_UID}"
chmod 700 "/run/user/${KIOSK_UID}"
chown "${KIOSK_USER}:${KIOSK_USER}" "/run/user/${KIOSK_UID}"
loginctl enable-linger "${KIOSK_USER}" 2>/dev/null || true

cat > /etc/systemd/system/kiosk.service << SVCEOF
[Unit]
Description=Kiosk JavaFX 24/7
After=network-online.target systemd-user-sessions.service local-fs.target
Wants=network-online.target
StartLimitIntervalSec=120
StartLimitBurst=5

[Service]
Type=simple
User=${KIOSK_USER}
Group=${KIOSK_USER}
WorkingDirectory=${APP_DIR}

Environment="HOME=${KIOSK_HOME}"
Environment="XDG_RUNTIME_DIR=/run/user/${KIOSK_UID}"
Environment="XCURSOR_THEME=blank-cursor"
Environment="XCURSOR_SIZE=24"
Environment="TOTEM_API_KEY=${API_KEY}"
Environment="LIBGL_ALWAYS_SOFTWARE=1"
Environment="WLR_RENDERER=pixman"
Environment="WLR_RENDERER_ALLOW_SOFTWARE=1"
Environment="WLR_NO_HARDWARE_CURSORS=1"

ExecStartPre=/bin/bash -c 'test ! -f ${APP_DIR}/.stop || { rm -f ${APP_DIR}/.stop; exit 1; }'
ExecStartPre=/bin/bash -c 'mkdir -p /run/user/${KIOSK_UID} && chmod 700 /run/user/${KIOSK_UID} && chown ${KIOSK_USER}:${KIOSK_USER} /run/user/${KIOSK_UID}'
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

# Timer log
cat > /etc/systemd/system/kiosk-logclean.service << 'EOF'
[Unit]
Description=Pulizia log kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c 'f=/opt/kiosk/logs/kiosk.log; [[ -f "$f" ]] && [[ $(wc -c < "$f") -gt 5242880 ]] && tail -500 "$f" > "$f.tmp" && mv "$f.tmp" "$f"; journalctl --vacuum-size=50M 2>/dev/null||true'
EOF
cat > /etc/systemd/system/kiosk-logclean.timer << 'EOF'
[Unit]
Description=Pulizia log giornaliera
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
EOF

# Riavvio notturno
cat > /etc/systemd/system/kiosk-nightly-restart.service << 'EOF'
[Unit]
Description=Riavvio notturno kiosk
[Service]
Type=oneshot
ExecStart=/bin/bash -c 'rm -f /opt/kiosk/.stop; systemctl restart kiosk.service'
EOF
cat > /etc/systemd/system/kiosk-nightly-restart.timer << 'EOF'
[Unit]
Description=Riavvio kiosk 04:00
[Timer]
OnCalendar=04:00
RandomizedDelaySec=300
[Install]
WantedBy=timers.target
EOF

ok "Servizi creati."

# ===============================================================================
#  FASE 11 - Auto-login TTY1
# ===============================================================================
step "Auto-login TTY1"
mkdir -p /etc/systemd/system/getty@tty1.service.d
printf '[Service]\nExecStart=\nExecStart=-/sbin/agetty --autologin %s --noclear %%I $TERM\nType=idle\n' \
    "${KIOSK_USER}" > /etc/systemd/system/getty@tty1.service.d/autologin.conf

cat > "${KIOSK_HOME}/.bash_profile" << 'BPEOF'
if [ "$(tty)" = "/dev/tty1" ]; then
    if systemctl is-active --quiet kiosk.service 2>/dev/null; then
        echo "Kiosk attivo. Invio per terminale."
        read -r _
    else
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
        export LIBGL_ALWAYS_SOFTWARE=1
        export WLR_RENDERER=pixman
        export WLR_NO_HARDWARE_CURSORS=1
        sudo systemctl restart kiosk.service 2>/dev/null || \
            dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh
    fi
fi
BPEOF
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bash_profile"

printf 'kiosk ALL=(ALL) NOPASSWD: /bin/systemctl restart kiosk.service\nkiosk ALL=(ALL) NOPASSWD: /bin/systemctl start kiosk.service\nkiosk ALL=(ALL) NOPASSWD: /bin/systemctl stop kiosk.service\n' \
    > /etc/sudoers.d/kiosk
chmod 440 /etc/sudoers.d/kiosk

# ===============================================================================
#  FASE 12 - Boot veloce
# ===============================================================================
step "Ottimizzazione boot"
for svc in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer dnf-makecache.timer; do
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

# Permessi udev
cat > /etc/udev/rules.d/99-kiosk.rules << 'EOF'
SUBSYSTEM=="drm",   TAG+="uaccess", GROUP="video"
SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"
SUBSYSTEM=="usb",   TAG+="uaccess"
EOF
udevadm control --reload-rules 2>/dev/null || true

# Journal
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nRuntimeMaxUse=20M\nCompress=yes\nForwardToSyslog=no\n' \
    > /etc/systemd/journald.conf.d/kiosk.conf

# ===============================================================================
#  FASE 13 - Abilitazione e avvio
# ===============================================================================
step "Abilitazione servizi"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}"

systemctl daemon-reload
systemctl enable kiosk.service
systemctl enable kiosk-logclean.timer
systemctl enable kiosk-nightly-restart.timer

# ===============================================================================
#  TEST FINALE
# ===============================================================================
step "Test finale pre-avvio"

FINAL_OK=true

# JAR
[[ -f "${APP_DIR}/demo-1.jar" ]] \
    && ok "demo-1.jar: OK ($(du -h "${APP_DIR}/demo-1.jar" | cut -f1))" \
    || { warn "demo-1.jar: MANCANTE"; FINAL_OK=false; }

# Librerie
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l)
[[ $LIB_N -gt 0 ]] \
    && ok "Librerie: ${LIB_N} JAR" \
    || warn "Librerie: nessuna trovata"

# JavaFX
FX_N=$(ls "${APP_DIR}/javafx-sdk/"*.jar 2>/dev/null | wc -l)
[[ $FX_N -gt 0 ]] \
    && ok "JavaFX SDK: ${FX_N} JAR" \
    || warn "JavaFX SDK: non trovato"

# run-kiosk.sh
[[ -x "${APP_DIR}/run-kiosk.sh" ]] \
    && ok "run-kiosk.sh: eseguibile" \
    || { warn "run-kiosk.sh: non eseguibile"; FINAL_OK=false; }

# Sintassi run-kiosk.sh
if bash -n "${APP_DIR}/run-kiosk.sh" 2>/dev/null; then
    ok "run-kiosk.sh: sintassi bash OK"
else
    warn "run-kiosk.sh: errori di sintassi rilevati"
    FINAL_OK=false
fi

# Servizio
systemctl is-enabled kiosk.service &>/dev/null \
    && ok "kiosk.service: abilitato" \
    || warn "kiosk.service: non abilitato"

# cage
command -v cage &>/dev/null \
    && ok "cage: $(cage --version 2>/dev/null || echo 'trovato')" \
    || warn "cage: non trovato - avvio potrebbe fallire"

# Java
if command -v java &>/dev/null; then
    JVER=$(java -version 2>&1 | head -1)
    ok "Java: ${JVER}"
else
    warn "Java: non trovato nel PATH corrente"
    FINAL_OK=false
fi

echo ""
if $FINAL_OK; then
    echo "+-------------------------------------------+"
    echo "| SETUP COMPLETATO - TUTTO OK               |"
    echo "+-------------------------------------------+"
else
    echo "+-------------------------------------------+"
    echo "| SETUP COMPLETATO CON AVVISI               |"
    echo "| Controlla i [WARN] sopra prima di avviare |"
    echo "+-------------------------------------------+"
fi

echo ""
echo "  Avvia subito:   systemctl start kiosk.service"
echo "  Oppure:         reboot"
echo ""
echo "  Diagnostica:"
echo "    kiosk-control status"
echo "    kiosk-control log"
echo "    kiosk-control errlog"
echo "    journalctl -u kiosk -f"
echo ""