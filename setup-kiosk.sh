#!/usr/bin/env bash
# setup-kiosk.sh
# Uso:
#   curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash
#   bash setup-kiosk.sh              # installazione completa
#   bash setup-kiosk.sh reset        # reset COMPLETO (scarica tutto di nuovo)
#   bash setup-kiosk.sh reset-soft   # reset VELOCE (mantiene file, risconfigura)
#   bash setup-kiosk.sh update       # aggiorna solo il JAR

GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
RELEASE_TAG="v1.0.0"
JAR_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/demo-1.jar"
LIB_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}/lib.tar.gz"
FX_URL="https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_linux-x64_bin-sdk.zip"
FX_VER="21.0.2"
KIOSK_USER="kiosk"
APP_DIR="/opt/kiosk"
API_KEY="api_key_totem_1"
LOG="/var/log/kiosk-setup.log"

mkdir -p "$(dirname $LOG)" && touch "$LOG" 2>/dev/null || true
log()  { MSG="[$(date '+%H:%M:%S')] $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null || true; }
ok()   { MSG="[ OK ] $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null || true; }
warn() { MSG="[WARN] $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null || true; }
fail() { MSG="[FAIL] $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null || true; exit 1; }
sep()  { echo ""; echo "======================================================"; echo "  $*"; echo "======================================================"; }

[ "$(id -u)" = "0" ] || fail "Eseguire come root: sudo bash $0"

# Rileva distro
DISTRO="unknown"
[ -f /etc/os-release ] && . /etc/os-release && DISTRO="${ID:-unknown}"
log "=== Setup avviato: mode=${1:-install} distro=$DISTRO ==="

# =============================================================================
# FUNZIONE PULIZIA SISTEMA (usata da reset e reset-soft)
# =============================================================================
clean_system() {
    sep "Pulizia sistema"

    # 1. Ferma cage e java ovunque
    log "Fermo cage e java..."
    pkill -9 cage 2>/dev/null || true
    pkill -9 java 2>/dev/null || true
    sleep 1

    # 2. Rimuovi servizi systemd kiosk
    log "Rimuovo servizi systemd kiosk..."
    for SVC in kiosk kiosk-logclean kiosk-nightly-restart kiosk-network-watchdog; do
        systemctl stop    "${SVC}.service" 2>/dev/null || true
        systemctl stop    "${SVC}.timer"   2>/dev/null || true
        systemctl disable "${SVC}.service" 2>/dev/null || true
        systemctl disable "${SVC}.timer"   2>/dev/null || true
        rm -f "/etc/systemd/system/${SVC}.service"
        rm -f "/etc/systemd/system/${SVC}.timer"
    done
    systemctl daemon-reload 2>/dev/null || true
    ok "Servizi rimossi."

    # 3. Rimuovi TUTTI gli utenti non-sistema che potrebbero interferire
    # (mantieni system users, root, e utenti con UID < 1000)
    log "Cerco utenti da rimuovere..."
    while IFS=: read -r USERNAME _ UID _ _ HOME _; do
        # Salta utenti di sistema (UID < 1000), root, e utenti speciali
        [ "$UID" -lt 1000 ] 2>/dev/null && continue
        [ "$USERNAME" = "root" ] && continue
        [ "$USERNAME" = "nobody" ] && continue
        # Rimuovi l'utente
        log "  Rimuovo utente: $USERNAME (UID=$UID, home=$HOME)"
        pkill -u "$USERNAME" 2>/dev/null || true
        sleep 0.5
        userdel -r -f "$USERNAME" 2>/dev/null || \
        userdel -r    "$USERNAME" 2>/dev/null || \
        userdel       "$USERNAME" 2>/dev/null || true
        # Rimuovi home se rimasta
        [ -n "$HOME" ] && [ "$HOME" != "/" ] && rm -rf "$HOME" 2>/dev/null || true
        ok "  Utente $USERNAME rimosso."
    done < /etc/passwd
    ok "Pulizia utenti completata."

    # 4. Rimuovi configurazioni
    log "Rimuovo configurazioni kiosk..."
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -f /etc/systemd/system.conf.d/kiosk.conf
    rm -rf /usr/share/icons/blank-cursor
    rm -f /usr/local/bin/kiosk-control

    # 5. Riabilita TTY
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done
    systemctl daemon-reload 2>/dev/null || true
    ok "Configurazioni rimosse."
}

# =============================================================================
# RESET COMPLETO (scarica tutto)
# =============================================================================
if [ "${1:-}" = "reset" ]; then
    sep "RESET COMPLETO"
    clean_system
    rm -rf "$APP_DIR"
    ok "Reset completo. Reinstallazione da zero..."
    echo ""
fi

# =============================================================================
# RESET SOFT (mantiene file, risconfigura tutto)
# =============================================================================
if [ "${1:-}" = "reset-soft" ]; then
    sep "RESET VELOCE (mantiene file scaricati)"
    clean_system
    log "File mantenuti: demo-1.jar, lib/, javafx-sdk/"
    ok "Reset veloce completato. Riconfigurazione..."
    echo ""
fi

# =============================================================================
# UPDATE JAR
# =============================================================================
if [ "${1:-}" = "update" ]; then
    sep "Update JAR"
    pkill cage 2>/dev/null || true
    pkill java 2>/dev/null || true
    sleep 1
    curl -fsSL --retry 3 "$JAR_URL" -o "${APP_DIR}/demo-1.jar" \
        && chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar" \
        && ok "Aggiornato." \
        || fail "Download fallito."
    exit 0
fi

echo ""
echo "+------------------------------------------------------+"
echo "|  KIOSK SETUP - $(date '+%Y-%m-%d %H:%M')               |"
echo "|  Distro: $DISTRO                                 |"
echo "+------------------------------------------------------+"
echo ""

# =============================================================================
# FASE 0 - PREFLIGHT (test prima di fare qualsiasi cosa)
# =============================================================================
sep "FASE 0 - Preflight checks"

PREFLIGHT_FAIL=0

# Root
[ "$(id -u)" = "0" ] && ok "Root: OK" || { warn "Non root!"; PREFLIGHT_FAIL=$((PREFLIGHT_FAIL+1)); }

# Distro supportata
case "$DISTRO" in
    debian|ubuntu|raspbian|linuxmint|pop)
        ok "Distro: $DISTRO (supportata, uso apt)"
        PKG_MGR="apt"
        ;;
    fedora|rhel|centos|almalinux|rocky)
        ok "Distro: $DISTRO (supportata, uso dnf)"
        PKG_MGR="dnf"
        ;;
    arch|manjaro|endeavouros)
        ok "Distro: $DISTRO (supportata, uso pacman)"
        PKG_MGR="pacman"
        ;;
    *)
        warn "Distro '$DISTRO' non riconosciuta - provo con apt"
        PKG_MGR="apt"
        ;;
esac

# Architettura
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ok  "Arch: $ARCH (supportata)" ;;
    aarch64) ok  "Arch: $ARCH (ARM64 - Raspberry Pi 4/5 OK)" ;;
    armv7l)  warn "Arch: $ARCH (ARM32 - performance limitata)" ;;
    *)       warn "Arch: $ARCH (non testata)" ;;
esac

# RAM
RAM_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo 0)
FREE_MB=$(awk '/MemAvailable/{printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo 0)
if [ "$RAM_MB" -ge 1024 ] 2>/dev/null; then
    ok "RAM: ${RAM_MB}MB totale, ${FREE_MB}MB libera"
elif [ "$RAM_MB" -ge 512 ] 2>/dev/null; then
    warn "RAM: ${RAM_MB}MB - minimo, potrebbe essere lento"
else
    warn "RAM: ${RAM_MB}MB - insufficiente (minimo 512MB)"
    PREFLIGHT_FAIL=$((PREFLIGHT_FAIL+1))
fi

# Disco /opt
mkdir -p /opt 2>/dev/null || true
DISK_MB=$(df /opt --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
if [ "$DISK_MB" -ge 2000 ] 2>/dev/null; then
    ok "Disco /opt: ${DISK_MB}MB liberi"
elif [ "$DISK_MB" -ge 1000 ] 2>/dev/null; then
    warn "Disco /opt: ${DISK_MB}MB - spazio ridotto (consigliati 2GB)"
else
    warn "Disco /opt: ${DISK_MB}MB - spazio insufficiente"
    PREFLIGHT_FAIL=$((PREFLIGHT_FAIL+1))
fi

# Disco /tmp
TMP_MB=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
if [ "$TMP_MB" -ge 300 ] 2>/dev/null; then
    ok "Disco /tmp: ${TMP_MB}MB liberi"
else
    warn "Disco /tmp: ${TMP_MB}MB - pulizia automatica in corso..."
    apt-get clean 2>/dev/null || true
    rm -rf /tmp/javafx-* /tmp/fxext /tmp/kiosk-* 2>/dev/null || true
    TMP_MB=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
    log "Spazio /tmp dopo pulizia: ${TMP_MB}MB"
fi

# Internet
if curl -sf --max-time 8 https://github.com -o /dev/null 2>/dev/null; then
    ok "Internet: GitHub raggiungibile"
else
    warn "Internet: GitHub non raggiungibile"
    PREFLIGHT_FAIL=$((PREFLIGHT_FAIL+1))
fi

if curl -sf --max-time 8 https://hasanabdelaziz.altervista.org -o /dev/null 2>/dev/null; then
    ok "Internet: Server API raggiungibile"
else
    warn "Internet: Server API non raggiungibile (funziona offline)"
fi

# curl disponibile
command -v curl >/dev/null && ok "curl: trovato" || { warn "curl mancante"; PREFLIGHT_FAIL=$((PREFLIGHT_FAIL+1)); }

# systemd
command -v systemctl >/dev/null && ok "systemd: trovato" || warn "systemd non trovato"

# Java esistente
if command -v java >/dev/null 2>&1; then
    JVER=$(java -version 2>&1 | head -1)
    JMAJ=$(java -version 2>&1 | head -1 | grep -oP '(?<=version ")[\d]+' | head -1 || echo 0)
    if [ "$JMAJ" -ge 17 ] 2>/dev/null; then
        ok "Java pre-esistente: $JVER (>= 17)"
    else
        warn "Java pre-esistente: $JVER (< 17, verra aggiornato)"
    fi
else
    warn "Java non installato (verra installato)"
fi

# VM check
IS_VM=false
if lspci 2>/dev/null | grep -qi "vmware\|virtualbox\|qxl\|virtio-vga\|virgl"; then
    IS_VM=true
    ok "VM rilevata: software rendering abilitato"
elif systemd-detect-virt 2>/dev/null | grep -qv "none"; then
    IS_VM=true
    ok "VM rilevata ($(systemd-detect-virt 2>/dev/null)): software rendering abilitato"
else
    ok "Hardware fisico: rendering hardware disponibile"
fi

# Display manager attivi (interferiscono con TTY1)
DM_FOUND=""
for DM in gdm3 gdm lightdm sddm xdm; do
    if systemctl is-active "$DM" >/dev/null 2>&1; then
        warn "Display manager attivo: $DM (verra disabilitato)"
        DM_FOUND="$DM_FOUND $DM"
    fi
done
[ -z "$DM_FOUND" ] && ok "Nessun display manager attivo"

echo ""
if [ "$PREFLIGHT_FAIL" -gt 0 ]; then
    warn "Preflight: $PREFLIGHT_FAIL problema/i rilevato/i"
    warn "Premi Invio per continuare comunque o Ctrl+C per interrompere..."
    read _DUMMY
else
    ok "Preflight: tutti i controlli superati."
fi

# =============================================================================
# FASE 1 - Pacchetti
# =============================================================================
sep "FASE 1 - Pacchetti di sistema"

install_pkg() {
    log "Installo: $*"
    case "$PKG_MGR" in
        apt)
            DEBIAN_FRONTEND=noninteractive apt-get install -y \
                --no-install-recommends "$@" 2>/dev/null
            ;;
        dnf)  dnf install -y "$@" 2>/dev/null ;;
        pacman) pacman -S --noconfirm --needed "$@" 2>/dev/null ;;
        *)    DEBIAN_FRONTEND=noninteractive apt-get install -y "$@" 2>/dev/null || true ;;
    esac
    RET=$?
    [ $RET -eq 0 ] && ok "Installato: $*" || warn "Parzialmente installato: $* (codice $RET)"
}

# Aggiorna lista pacchetti
log "Aggiorno lista pacchetti..."
case "$PKG_MGR" in
    apt)    DEBIAN_FRONTEND=noninteractive apt-get update -qq && ok "apt update OK" ;;
    dnf)    dnf check-update -y 2>/dev/null || true ;;
    pacman) pacman -Sy --noconfirm 2>/dev/null || true ;;
esac

# Pacchetti base
install_pkg curl wget ca-certificates unzip python3

# Cage + Wayland
install_pkg cage xwayland
install_pkg dbus dbus-user-session

# Font
install_pkg fonts-dejavu-core 2>/dev/null || \
install_pkg dejavu-fonts       2>/dev/null || true
install_pkg fonts-noto-color-emoji 2>/dev/null || true

# Strumenti
install_pkg pciutils util-linux procps iproute2 lm-sensors

# GTK3 (necessario per JavaFX glass platform)
install_pkg libgtk-3-0 2>/dev/null || \
install_pkg gtk3       2>/dev/null || true

# Mesa / OpenGL
case "$PKG_MGR" in
    apt)
        install_pkg libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa \
                    libgles2-mesa libgl1-mesa-swrast \
                    libgbm1 libdrm2 ;;
    dnf)
        install_pkg mesa-dri-drivers mesa-libGL mesa-libEGL mesa-libgbm ;;
    pacman)
        install_pkg mesa ;;
esac

# Java
JAVA_INSTALLED=false
case "$PKG_MGR" in
    apt)
        install_pkg default-jre && JAVA_INSTALLED=true || \
        install_pkg openjdk-21-jre-headless && JAVA_INSTALLED=true || \
        install_pkg openjdk-17-jre-headless && JAVA_INSTALLED=true || true
        ;;
    dnf)
        install_pkg java-21-openjdk-headless && JAVA_INSTALLED=true || \
        install_pkg java-17-openjdk-headless && JAVA_INSTALLED=true || true
        ;;
    pacman)
        install_pkg jre21-openjdk-headless && JAVA_INSTALLED=true || \
        install_pkg jre17-openjdk-headless && JAVA_INSTALLED=true || true
        ;;
esac

# JavaFX di sistema
install_pkg openjfx 2>/dev/null || true

# Verifica post-installazione
if command -v java >/dev/null 2>&1; then
    ok "Java disponibile: $(java -version 2>&1 | head -1)"
else
    warn "Java non trovato nel PATH dopo installazione."
    warn "Potrebbe essere necessario impostare JAVA_HOME."
fi
command -v cage >/dev/null && ok "cage: OK" || warn "cage: non trovato dopo installazione"

# =============================================================================
# FASE 2 - Utente kiosk (ricreato sempre da zero)
# =============================================================================
sep "FASE 2 - Utente kiosk"

# Rimuovi se esiste
if id "$KIOSK_USER" >/dev/null 2>&1; then
    log "Utente $KIOSK_USER esiste - lo rimuovo per ricrearlo pulito..."
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    userdel -r -f "$KIOSK_USER" 2>/dev/null || \
    userdel -r    "$KIOSK_USER" 2>/dev/null || \
    userdel       "$KIOSK_USER" 2>/dev/null || true
    ok "Utente $KIOSK_USER rimosso."
fi

log "Creo utente $KIOSK_USER..."
useradd -m -s /bin/bash -G video,input,render,audio "$KIOSK_USER" 2>/dev/null || \
useradd -m -s /bin/bash "$KIOSK_USER" || fail "Impossibile creare utente $KIOSK_USER"
passwd -d "$KIOSK_USER"

# Aggiungi a seat/tty se disponibili
groupadd seat 2>/dev/null || true
usermod -aG seat "$KIOSK_USER" 2>/dev/null || true
usermod -aG tty  "$KIOSK_USER" 2>/dev/null || true

KIOSK_HOME=$(eval echo ~"$KIOSK_USER")
KIOSK_UID=$(id -u "$KIOSK_USER")
ok "Utente $KIOSK_USER creato (UID=$KIOSK_UID, home=$KIOSK_HOME)"

# =============================================================================
# FASE 3 - Download app (saltata in reset-soft se file esistono)
# =============================================================================
sep "FASE 3 - Download app"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/config"

# demo-1.jar
if [ -f "${APP_DIR}/demo-1.jar" ] && [ "${1:-}" = "reset-soft" ]; then
    ok "demo-1.jar: mantenuto dal reset-soft ($(du -h "${APP_DIR}/demo-1.jar" | cut -f1))"
else
    log "Scarico demo-1.jar..."
    DOWNLOADED=false
    for TRY in 1 2 3; do
        if curl -fsSL --retry 2 --max-time 120 \
                "$JAR_URL" -o "${APP_DIR}/demo-1.jar"; then
            ok "demo-1.jar scaricato: $(du -h "${APP_DIR}/demo-1.jar" | cut -f1)"
            DOWNLOADED=true
            break
        fi
        warn "Tentativo $TRY fallito, aspetto 5s..."
        sleep 5
    done
    $DOWNLOADED || fail "Impossibile scaricare demo-1.jar dopo 3 tentativi."
fi

# lib.tar.gz
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$LIB_N" -gt 0 ] && [ "${1:-}" = "reset-soft" ]; then
    ok "Librerie: mantenute dal reset-soft ($LIB_N JAR)"
else
    log "Scarico librerie..."
    for TRY in 1 2 3; do
        rm -f /tmp/kiosk-lib.tar.gz
        if curl -fsSL --retry 2 --max-time 120 \
                "$LIB_URL" -o /tmp/kiosk-lib.tar.gz; then
            tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" 2>/dev/null || \
            tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" \
                --strip-components=1 2>/dev/null || true
            rm -f /tmp/kiosk-lib.tar.gz
            LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
            ok "Librerie estratte: $LIB_N JAR"
            break
        fi
        warn "Tentativo $TRY fallito, aspetto 5s..."
        sleep 5
        [ $TRY -eq 3 ] && warn "lib.tar.gz non scaricato - continuo senza."
    done
fi

# =============================================================================
# FASE 4 - JavaFX SDK
# =============================================================================
sep "FASE 4 - JavaFX SDK"
JAVAFX_DIR="${APP_DIR}/javafx-sdk"
FX_INSTALLED=false

# Mantieni SDK in reset-soft
FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$FX_N" -gt 0 ] && [ "${1:-}" = "reset-soft" ]; then
    ok "JavaFX SDK: mantenuto dal reset-soft ($FX_N JAR)"
    FX_PATH="$JAVAFX_DIR"
    FX_INSTALLED=true
fi

# Prova JavaFX di sistema
if [ "$FX_INSTALLED" = "false" ]; then
    FX_JAR=$(find /usr/share/java /usr/lib/jvm \
                  -name "javafx.controls.jar" 2>/dev/null | head -1 || true)
    if [ -n "$FX_JAR" ]; then
        FX_SYS=$(dirname "$FX_JAR")
        log "JavaFX di sistema trovato: $FX_SYS"
        mkdir -p "$JAVAFX_DIR"
        cp "${FX_SYS}/"*.jar "$JAVAFX_DIR/" 2>/dev/null || true
        find "$FX_SYS" -name "*.so" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "JavaFX di sistema copiato: $FX_N JAR"
        FX_PATH="$JAVAFX_DIR"
        FX_INSTALLED=true
    fi
fi

# Scarica da Gluon
if [ "$FX_INSTALLED" = "false" ]; then
    # Controlla spazio
    FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 999)
    if [ "$FREE_TMP" -lt 200 ] 2>/dev/null; then
        log "Pulizia /tmp (spazio: ${FREE_TMP}MB)..."
        apt-get clean 2>/dev/null || true
        rm -rf /tmp/javafx-* /tmp/fxext /tmp/*.zip 2>/dev/null || true
    fi

    log "Scarico JavaFX SDK $FX_VER da Gluon..."
    if curl -fsSL --max-time 300 "$FX_URL" -o /tmp/javafx.zip; then
        log "Download OK. Estraggo (escludo webkit ~60MB)..."
        mkdir -p /tmp/fxext
        unzip -q /tmp/javafx.zip -d /tmp/fxext/ \
            -x "*/libjfxwebkit.so" -x "*/libgstreamer-lite.so" \
            -x "*/src.zip" 2>/dev/null
        true
        mkdir -p "$JAVAFX_DIR"
        find /tmp/fxext -name "*.jar" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        find /tmp/fxext -name "*.so" \
            ! -name "libjfxwebkit.so" ! -name "libgstreamer-lite.so" \
            -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        rm -rf /tmp/javafx.zip /tmp/fxext
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        if [ "$FX_N" -gt 0 ]; then
            ok "JavaFX SDK installato: $FX_N JAR"
            FX_PATH="$JAVAFX_DIR"
            FX_INSTALLED=true
        else
            warn "JavaFX SDK: nessun JAR estratto. Controllo spazio disco..."
            df -h /tmp/ /opt/ || true
        fi
    else
        warn "Download JavaFX fallito."
    fi
fi

FX_PATH="${FX_PATH:-${APP_DIR}/lib}"
log "FX_PATH: $FX_PATH"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 5 - Test Java + JavaFX (prima di configurare)
# =============================================================================
sep "FASE 5 - Test Java e JavaFX"

TEST_JAVA=false
TEST_FX=false
TEST_CAGE=false

# Test 1: Java funziona
if command -v java >/dev/null 2>&1; then
    if java -version >/dev/null 2>&1; then
        ok "Test Java: OK ($(java -version 2>&1 | head -1))"
        TEST_JAVA=true
    else
        warn "Test Java: java trovato ma non eseguibile"
    fi
else
    warn "Test Java: non trovato"
fi

# Test 2: JavaFX moduli accessibili
if [ "$TEST_JAVA" = "true" ] && [ -d "$FX_PATH" ]; then
    if java --module-path "$FX_PATH" --list-modules 2>/dev/null | \
            grep -q "javafx.controls"; then
        ok "Test JavaFX: moduli trovati in $FX_PATH"
        TEST_FX=true
    else
        warn "Test JavaFX: moduli non trovati in $FX_PATH"
        # Prova percorso alternativo
        ALT_FX=$(find /usr -name "javafx.controls.jar" 2>/dev/null | head -1 | \
                 xargs -I{} dirname {} 2>/dev/null || echo "")
        if [ -n "$ALT_FX" ]; then
            warn "Trovato JavaFX alternativo: $ALT_FX - uso quello"
            FX_PATH="$ALT_FX"
            TEST_FX=true
        fi
    fi
fi

# Test 3: GTK3 disponibile
if ldconfig -p 2>/dev/null | grep -q "libgtk-3.so"; then
    ok "Test GTK3: libgtk-3.so trovata"
else
    warn "Test GTK3: libgtk-3.so non trovata - installo..."
    install_pkg libgtk-3-0 2>/dev/null || \
    install_pkg gtk3       2>/dev/null || true
fi

# Test 4: libGL disponibile
if ldconfig -p 2>/dev/null | grep -q "libGL.so"; then
    ok "Test libGL: trovata"
else
    warn "Test libGL: non trovata - installo Mesa..."
    install_pkg libgl1-mesa-dri libgl1-mesa-glx libgl1-mesa-swrast 2>/dev/null || \
    install_pkg mesa-libGL 2>/dev/null || true
    ldconfig 2>/dev/null || true
fi

# Test 5: cage funziona
if command -v cage >/dev/null 2>&1; then
    ok "Test cage: trovato ($(cage --version 2>/dev/null || echo ok))"
    TEST_CAGE=true
else
    warn "Test cage: non trovato - tentativo reinstallo..."
    install_pkg cage
    command -v cage >/dev/null && TEST_CAGE=true && ok "Test cage: reinstallato OK"
fi

# Test 6: dbus-run-session disponibile
if command -v dbus-run-session >/dev/null 2>&1; then
    ok "Test dbus-run-session: trovato"
else
    warn "Test dbus-run-session: non trovato - installo..."
    install_pkg dbus dbus-user-session
fi

# Test 7: aggiorna ldconfig
log "Aggiorno cache librerie (ldconfig)..."
ldconfig 2>/dev/null && ok "ldconfig: aggiornato" || warn "ldconfig: errore (non critico)"

# Riepilogo test
echo ""
log "=== Risultati test pre-configurazione ==="
[ "$TEST_JAVA" = "true" ]  && ok "Java:    OK" || warn "Java:    PROBLEMA"
[ "$TEST_FX"   = "true" ]  && ok "JavaFX:  OK" || warn "JavaFX:  PROBLEMA (l'app potrebbe non partire)"
[ "$TEST_CAGE" = "true" ]  && ok "cage:    OK" || warn "cage:    PROBLEMA (avvio impossibile)"

if [ "$TEST_CAGE" = "false" ]; then
    fail "cage non disponibile - impossibile continuare."
fi

# =============================================================================
# FASE 6 - Cursore trasparente
# =============================================================================
sep "FASE 6 - Cursore trasparente"
mkdir -p /usr/share/icons/blank-cursor/cursors
python3 << 'PYEOF'
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
    dst = '/usr/share/icons/blank-cursor/cursors/' + n
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Cursore OK.')
PYEOF
printf '[Icon Theme]\nName=blank-cursor\n' \
    > /usr/share/icons/blank-cursor/index.theme
ok "Cursore trasparente creato."

# =============================================================================
# FASE 7 - run-kiosk.sh
# =============================================================================
sep "FASE 7 - Script di avvio"
RUN="${APP_DIR}/run-kiosk.sh"

printf '#!/usr/bin/env bash\n'                  > "$RUN"
printf 'APP_DIR="%s"\n'        "$APP_DIR"      >> "$RUN"
printf 'API_KEY="%s"\n'        "$API_KEY"      >> "$RUN"
printf 'FX_PATH_DEFAULT="%s"\n' "$FX_PATH"     >> "$RUN"

cat >> "$RUN" << 'RUNEOF'
LOG="${APP_DIR}/logs/kiosk.log"
ERRLOG="${APP_DIR}/logs/kiosk-err.log"
PROFILE_FILE="${APP_DIR}/config/profile.conf"
mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"
ts()  { date '+%H:%M:%S'; }
log() { echo "[$(ts)] $*" | tee -a "$LOG"; }

find_fx() {
    [ -d "${APP_DIR}/javafx-sdk" ] && \
        ls "${APP_DIR}/javafx-sdk/"*.jar >/dev/null 2>&1 && \
        echo "${APP_DIR}/javafx-sdk" && return
    [ -n "$FX_PATH_DEFAULT" ] && [ -d "$FX_PATH_DEFAULT" ] && \
        echo "$FX_PATH_DEFAULT" && return
    FX_J=$(find /usr/share/java /usr/lib/jvm \
                -name "javafx.controls.jar" 2>/dev/null | head -1)
    [ -n "$FX_J" ] && dirname "$FX_J" && return
    echo "${APP_DIR}/lib"
}

build_cp() {
    CP="${APP_DIR}/demo-1.jar"
    for J in "${APP_DIR}/lib/"*.jar; do
        [ -f "$J" ] && CP="${CP}:${J}"
    done
    echo "$CP"
}

detect_error() {
    O="$1"
    echo "$O" | grep -qi "javafx.*not found\|FindException\|boot layer" && echo "no_javafx"  && return
    echo "$O" | grep -qi "Unable to open DISPLAY\|GtkApplication\|no display" && echo "no_display" && return
    echo "$O" | grep -qi "dbus\|connection to the bus" && echo "no_dbus"   && return
    echo "$O" | grep -qi "OutOfMemoryError\|heap space" && echo "oom"      && return
    echo "$O" | grep -qi "libGL\|MESA\|prism.*fail"     && echo "opengl"   && return
    echo "unknown"
}

PROFILE="xwayland_sw"
[ -f "$PROFILE_FILE" ] && PROFILE=$(cat "$PROFILE_FILE" | tr -d '[:space:]')

ATTEMPT=0
MEM="-Xms64m -Xmx256m"

log "======================================="
log "Avvio kiosk - profilo: $PROFILE"
log "FX_PATH: $(find_fx)"
log "======================================="

while true; do
    ATTEMPT=$((ATTEMPT+1))
    FX=$(find_fx)
    CP=$(build_cp)

    export LIBGL_ALWAYS_SOFTWARE=1
    export WLR_NO_HARDWARE_CURSORS=1
    export WLR_RENDERER_ALLOW_SOFTWARE=1
    export TOTEM_API_KEY="$API_KEY"
    unset GDK_BACKEND 2>/dev/null || true

    case "$PROFILE" in
        xwayland_sw)
            export DISPLAY=":1"
            unset WAYLAND_DISPLAY 2>/dev/null || true
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
        xwayland_gtk2)
            export DISPLAY=":1"
            unset WAYLAND_DISPLAY 2>/dev/null || true
            export WLR_RENDERER=pixman
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2 -Djava.awt.headless=false"
            ;;
        wayland_native)
            unset DISPLAY 2>/dev/null || true
            export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
            export WLR_RENDERER=pixman
            export GALLIUM_DRIVER=softpipe
            export GDK_BACKEND=wayland
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
        fallback)
            export DISPLAY=":1"
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            export GDK_BACKEND=x11
            MEM="-Xms128m -Xmx512m"
            JFXOPTS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false -Dprism.verbose=true"
            ;;
        *)
            export DISPLAY=":1"
            export WLR_RENDERER=pixman
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
    esac

    log "--- Tentativo $ATTEMPT | $PROFILE | DISPLAY=${DISPLAY:-unset} ---"
    TMPF=$(mktemp /tmp/kiosk.XXXXXX 2>/dev/null || echo /tmp/kiosk-$$)

    java $MEM \
        --module-path "$FX" \
        --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
        --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
        --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
        -Djava.awt.headless=false \
        -Djavafx.animation.fullspeed=true \
        -Dfile.encoding=UTF-8 \
        -XX:+UseG1GC \
        $JFXOPTS \
        -cp "$CP" \
        com.example.App > "$TMPF" 2>&1
    RC=$?

    cat "$TMPF" >> "$ERRLOG"
    OUT=$(cat "$TMPF" 2>/dev/null || echo "")
    rm -f "$TMPF"

    [ $RC -eq 0 ] && { log "Uscita normale."; exit 0; }

    if echo "$OUT" | grep -qi "SplashController\|Login OK\|Nav.*->"; then
        log "App era partita con profilo $PROFILE. Riavvio in 3s..."
        echo "$PROFILE" > "$PROFILE_FILE"
        sleep 3
        continue
    fi

    ERRTYPE=$(detect_error "$OUT")
    LASTERR=$(echo "$OUT" | grep -i "error\|exception\|failed" | tail -2)
    log "Errore: $ERRTYPE | $LASTERR"

    case "$ERRTYPE" in
        no_javafx)
            log "JavaFX mancante - installo openjfx..."
            apt-get install -y openjfx 2>/dev/null || true
            NEXT="xwayland_sw"
            ;;
        oom)
            MEM="-Xms128m -Xmx512m"
            log "OOM - aumento heap"
            NEXT="$PROFILE"
            ;;
        *)
            case "$PROFILE" in
                xwayland_sw)   NEXT="xwayland_gtk2"  ;;
                xwayland_gtk2) NEXT="wayland_native" ;;
                wayland_native) NEXT="fallback"      ;;
                fallback)      NEXT="xwayland_sw"    ;;
                *)             NEXT="xwayland_sw"    ;;
            esac
            ;;
    esac

    if [ $ATTEMPT -ge 8 ]; then
        log "8 tentativi. Reset profilo e pausa 30s..."
        echo "xwayland_sw" > "$PROFILE_FILE"
        PROFILE="xwayland_sw"
        ATTEMPT=0
        sleep 30
        continue
    fi

    log "Cambio profilo: $PROFILE -> $NEXT"
    PROFILE="$NEXT"
    echo "$PROFILE" > "$PROFILE_FILE"
    WAIT=$((ATTEMPT * 2))
    [ $WAIT -gt 10 ] && WAIT=10
    log "Attendo ${WAIT}s..."
    sleep $WAIT
done
RUNEOF

chmod +x "$RUN"
bash -n "$RUN" && ok "run-kiosk.sh: sintassi OK" || warn "run-kiosk.sh: errore sintassi"

echo "xwayland_sw" > "${APP_DIR}/config/profile.conf"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 8 - kiosk-control
# =============================================================================
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
APP_DIR="/opt/kiosk"
case "${1:-help}" in
    start)
        rm -f "$APP_DIR/.stop"
        su - kiosk -c "
            export XDG_RUNTIME_DIR=/run/user/\$(id -u)
            mkdir -p \$XDG_RUNTIME_DIR && chmod 700 \$XDG_RUNTIME_DIR
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_NO_HARDWARE_CURSORS=1
            cage -d -- $APP_DIR/run-kiosk.sh &
        "
        echo "Kiosk avviato."
        ;;
    stop)
        touch "$APP_DIR/.stop"
        pkill cage 2>/dev/null || true
        pkill java 2>/dev/null || true
        echo "Kiosk fermato."
        ;;
    restart) "$0" stop; sleep 2; "$0" start ;;
    status)
        pgrep cage >/dev/null && echo "cage:  IN ESECUZIONE" || echo "cage:  FERMO"
        pgrep java >/dev/null && echo "java:  IN ESECUZIONE" || echo "java:  FERMO"
        echo "Profilo: $(cat $APP_DIR/config/profile.conf 2>/dev/null || echo xwayland_sw)"
        echo "--- Ultimi 20 log ---"
        tail -20 "$APP_DIR/logs/kiosk.log" 2>/dev/null || echo "(nessun log)"
        ;;
    log)            tail -f "$APP_DIR/logs/kiosk.log" ;;
    errlog)         tail -f "$APP_DIR/logs/kiosk-err.log" ;;
    profile)        cat "$APP_DIR/config/profile.conf" 2>/dev/null || echo "xwayland_sw" ;;
    reset-profile)  echo "xwayland_sw" > "$APP_DIR/config/profile.conf"; echo "Profilo resettato." ;;
    reset-soft)     curl -fsSL "https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh" | bash -s reset-soft ;;
    reset)          curl -fsSL "https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh" | bash -s reset ;;
    update)
        VER="${2:-v1.0.0}"
        URL="https://github.com/accaicedtea/ttetttos/releases/download/${VER}/demo-1.jar"
        curl -fsSL "$URL" -o /tmp/kiosk-update.jar && {
            pkill java 2>/dev/null || true; sleep 1
            cp /tmp/kiosk-update.jar "$APP_DIR/demo-1.jar"
            chown kiosk:kiosk "$APP_DIR/demo-1.jar"
            rm -f /tmp/kiosk-update.jar "$APP_DIR/.stop"
            echo "Aggiornato a $VER."
        } || echo "Download fallito."
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|errlog|profile|reset-profile|reset-soft|reset|update [tag]}"
        ;;
esac
CTRLEOF
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control
ok "kiosk-control installato."

# =============================================================================
# FASE 9 - Auto-login TTY1 + .bash_profile
# =============================================================================
sep "FASE 9 - Auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF
ok "Auto-login TTY1 configurato."

cat > "${KIOSK_HOME}/.bash_profile" << 'BPEOF'
if [ "$(tty)" = "/dev/tty1" ]; then
    if [ -f /opt/kiosk/.stop ]; then
        rm -f /opt/kiosk/.stop
        echo "Kiosk fermato. Shell disponibile."
    else
        echo ""
        echo "+------------------------------------------+"
        echo "|  Kiosk in avvio - Ctrl+C per terminale  |"
        echo "+------------------------------------------+"
        echo ""
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
        export XCURSOR_THEME=blank-cursor
        export XCURSOR_SIZE=24
        # cage -d avvia Xwayland su :1 automaticamente
        # run-kiosk.sh usera DISPLAY=:1 per JavaFX
        exec cage -d -- /opt/kiosk/run-kiosk.sh
    fi
fi
BPEOF
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bash_profile"
ok ".bash_profile configurato."

printf 'kiosk ALL=(ALL) NOPASSWD: /usr/local/bin/kiosk-control\n' \
    > /etc/sudoers.d/kiosk
chmod 440 /etc/sudoers.d/kiosk

# =============================================================================
# FASE 10 - Ottimizzazioni
# =============================================================================
sep "FASE 10 - Ottimizzazioni sistema"

# Disabilita display manager
for DM in gdm3 gdm lightdm sddm xdm; do
    if systemctl is-active "$DM" >/dev/null 2>&1 || \
       systemctl is-enabled "$DM" >/dev/null 2>&1; then
        log "Disabilito: $DM"
        systemctl stop    "$DM" 2>/dev/null || true
        systemctl disable "$DM" 2>/dev/null || true
        ok "$DM disabilitato."
    fi
done

# Servizi inutili
for SVC in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer; do
    systemctl disable "$SVC" 2>/dev/null || true
    systemctl mask    "$SVC" 2>/dev/null || true
done

# TTY extra
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done

systemctl set-default multi-user.target 2>/dev/null || true

# GRUB
if [ -f /etc/default/grub ]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB: 2s timeout."
fi

# sysctl
printf 'kernel.panic=30\nvm.swappiness=5\n' > /etc/sysctl.d/99-kiosk.conf
sysctl -p /etc/sysctl.d/99-kiosk.conf >/dev/null 2>&1 || true

# udev
printf 'SUBSYSTEM=="drm",   TAG+="uaccess", GROUP="video"\n' \
    > /etc/udev/rules.d/99-kiosk.rules
printf 'SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"\n' \
    >> /etc/udev/rules.d/99-kiosk.rules
udevadm control --reload-rules 2>/dev/null || true

# journal
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nCompress=yes\n' \
    > /etc/systemd/journald.conf.d/kiosk.conf

systemctl daemon-reload
ok "Ottimizzazioni completate."

# =============================================================================
# FASE 11 - Test finale
# =============================================================================
sep "FASE 11 - Test finale"

FINAL_OK=true
[ -f "${APP_DIR}/demo-1.jar" ] && \
    ok "demo-1.jar: OK ($(du -h "${APP_DIR}/demo-1.jar" | cut -f1))" || \
    { warn "demo-1.jar: MANCANTE"; FINAL_OK=false; }

LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$LIB_N" -gt 0 ] && ok "Librerie: $LIB_N JAR" || warn "Librerie: nessuna"

FX_N=$(ls "${APP_DIR}/javafx-sdk/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$FX_N" -gt 0 ] && ok "JavaFX SDK: $FX_N JAR" || \
    warn "JavaFX SDK: non presente (usa openjfx di sistema o lib/)"

command -v java >/dev/null && ok "Java: OK" || \
    { warn "Java: non trovato"; FINAL_OK=false; }

command -v cage >/dev/null && ok "cage: OK" || \
    { warn "cage: NON TROVATO"; FINAL_OK=false; }

bash -n "${APP_DIR}/run-kiosk.sh" 2>/dev/null && \
    ok "run-kiosk.sh: sintassi OK" || warn "run-kiosk.sh: errore sintassi"

[ -f "${KIOSK_HOME}/.bash_profile" ] && ok ".bash_profile: OK" || \
    warn ".bash_profile: mancante"

# Verifica che auto-login sia configurato
[ -f "/etc/systemd/system/getty@tty1.service.d/autologin.conf" ] && \
    ok "Auto-login TTY1: configurato" || warn "Auto-login TTY1: non configurato"

echo ""
if [ "$FINAL_OK" = "true" ]; then
    echo "+--------------------------------------------------+"
    echo "|  SETUP COMPLETATO - TUTTO OK                     |"
    echo "+--------------------------------------------------+"
else
    echo "+--------------------------------------------------+"
    echo "|  SETUP COMPLETATO CON AVVISI                     |"
    echo "+--------------------------------------------------+"
fi
echo "|                                                  |"
echo "|  Come funziona al boot:                          |"
echo "|   1. Auto-login su TTY1 come utente kiosk        |"
echo "|   2. .bash_profile avvia cage -d direttamente    |"
echo "|   3. cage avvia Xwayland su DISPLAY=:1           |"
echo "|   4. JavaFX si connette a Xwayland (X11)         |"
echo "|                                                  |"
echo "|  Comandi:                                        |"
echo "|   reboot                   avvia il kiosk        |"
echo "|   kiosk-control status     stato                 |"
echo "|   kiosk-control log        log in tempo reale    |"
echo "|   kiosk-control errlog     log errori Java       |"
echo "|   kiosk-control reset-soft risconfigura (veloce) |"
echo "|   kiosk-control reset      reinstalla tutto      |"
echo "+--------------------------------------------------+"
echo ""