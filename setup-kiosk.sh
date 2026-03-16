#!/usr/bin/env bash
# =============================================================================
# setup-kiosk.sh - Kiosk JavaFX su Linux (Debian/Ubuntu) con GNOME Kiosk
#
# Approccio moderno:
#   - GNOME Kiosk (sessioni Wayland o X11) per l'ambiente chiosco
#   - OverlayFS per proteggere il filesystem di root (read-only)
#   - Autologin per l'utente kiosk
#   - Script di avvio personalizzato per JavaFX
#
# Basato su:
#   https://johanneskinzig.de/blog/2025/07/13/how-to-set-up-ubuntu-linux-as-a-kiosk-or-self-service-terminal-part-1/
#   https://gist.github.com/smahi/a80302fa0ac031697a4e2985826837cd
#
# Uso:
#   curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash
#   bash setup-kiosk.sh              # installazione
#   bash setup-kiosk.sh reset        # reset completo (riscarica tutto)
#   bash setup-kiosk.sh reset-soft   # reset veloce (mantiene i file scaricati)
#   bash setup-kiosk.sh fix          # ripara configurazione esistente
#   bash setup-kiosk.sh update [tag] # aggiorna solo il JAR
# =============================================================================

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
MODE="${1:-install}"

# =============================================================================
# Logging
# =============================================================================
mkdir -p "$(dirname "$LOG")" && touch "$LOG" 2>/dev/null || true
log()  { MSG="[$(date '+%H:%M:%S')] INFO  $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null; }
ok()   { MSG="[$(date '+%H:%M:%S')]  OK   $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null; }
warn() { MSG="[$(date '+%H:%M:%S')] WARN  $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null; }
fail() { MSG="[$(date '+%H:%M:%S')] FAIL  $*"; echo "$MSG"; echo "$MSG" >> "$LOG" 2>/dev/null; exit 1; }
sep()  { echo ""; echo "===================================================="; echo "  $*"; echo "===================================================="; }

[ "$(id -u)" = "0" ] || fail "Eseguire come root: sudo bash $0 $MODE"

# Rileva distro
DISTRO="unknown"
[ -f /etc/os-release ] && . /etc/os-release && DISTRO="${ID:-unknown}"
ARCH=$(uname -m)
log "=== Setup avviato: mode=$MODE distro=$DISTRO arch=$ARCH ==="

# Package manager
case "$DISTRO" in
    debian|ubuntu|raspbian|linuxmint|pop) PKG="apt" ;;
    fedora|rhel|centos|almalinux|rocky)   PKG="dnf" ;;
    arch|manjaro|endeavouros)             PKG="pacman" ;;
    *)                                    PKG="apt" ;;
esac

inst() {
    log "Installo: $*"
    case "$PKG" in
        apt)    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@" 2>/dev/null ;;
        dnf)    dnf install -y "$@" 2>/dev/null ;;
        pacman) pacman -S --noconfirm --needed "$@" 2>/dev/null ;;
    esac
    RET=$?
    [ $RET -eq 0 ] && ok "OK: $*" || warn "Parziale: $* (cod $RET)"
    return 0
}

# =============================================================================
# Funzioni per la gestione di JavaFX (riutilizzate)
# =============================================================================
find_fx() {
    [ -d "${APP_DIR}/javafx-sdk" ] && \
        ls "${APP_DIR}/javafx-sdk/"*.jar >/dev/null 2>&1 && \
        echo "${APP_DIR}/javafx-sdk" && return
    [ -n "$FX_PATH_DEFAULT" ] && [ -d "$FX_PATH_DEFAULT" ] && \
        echo "$FX_PATH_DEFAULT" && return
    FXJ=$(find /usr/share/java /usr/lib/jvm /usr/share/openjfx \
               -name "javafx.controls.jar" 2>/dev/null | head -1)
    [ -n "$FXJ" ] && dirname "$FXJ" && return
    echo "${APP_DIR}/lib"
}

build_cp() {
    CP="${APP_DIR}/demo-1.jar"
    for J in "${APP_DIR}/lib/"*.jar; do
        [ -f "$J" ] && CP="${CP}:${J}"
    done
    echo "$CP"
}

# =============================================================================
# Funzione pulizia sistema (reset/reset-soft)
# =============================================================================
clean_system() {
    sep "Pulizia sistema"

    # Disabilita overlayroot se attivo
    if [ -f /etc/overlayroot.conf ] && grep -q "^overlayroot=" /etc/overlayroot.conf; then
        overlayroot-chroot <<EOF
sed -i 's/^overlayroot=.*/#overlayroot="tmpfs:swap=1,recurse=0"/' /etc/overlayroot.conf
EOF
    fi

    # Ferma sessioni kiosk
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1

    # Rimuovi utente kiosk
    if id "$KIOSK_USER" >/dev/null 2>&1; then
        userdel -r -f "$KIOSK_USER" 2>/dev/null || true
    fi

    # Rimuovi configurazioni
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -f /usr/local/bin/kiosk-control
    rm -rf /usr/share/icons/blank-cursor

    # Rimuovi overlayroot.conf (se completamente pulito)
    # rm -f /etc/overlayroot.conf   # forse no, lo lasciamo ma disabilitato

    # Reimposta target grafico se necessario
    systemctl set-default graphical.target 2>/dev/null || true

    ok "Pulizia completata."
}

# =============================================================================
# MODALITA
# =============================================================================
if [ "$MODE" = "reset" ]; then
    sep "RESET COMPLETO"
    clean_system
    rm -rf "$APP_DIR"
    ok "Reset completo. Reinstallazione da zero..."
    MODE="install"
fi

if [ "$MODE" = "reset-soft" ]; then
    sep "RESET SOFT (mantiene file scaricati)"
    clean_system
    ok "Reset soft completato. Riconfigurazione..."
    MODE="install"
fi

if [ "$MODE" = "fix" ]; then
    sep "MODALITA FIX"
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    MODE="install"
fi

if [ "$MODE" = "update" ]; then
    sep "UPDATE JAR"
    TAG="${2:-$RELEASE_TAG}"
    URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${TAG}/demo-1.jar"
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    for TRY in 1 2 3; do
        curl -fsSL --max-time 120 "$URL" -o "${APP_DIR}/demo-1.jar" && {
            chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar"
            ok "Aggiornato a $TAG"
            exit 0
        }
        sleep 5
    done
    fail "Download fallito."
fi

# =============================================================================
echo ""
echo "+------------------------------------------------------+"
echo "|  KIOSK SETUP (GNOME Kiosk) - $(date '+%Y-%m-%d %H:%M') |"
echo "|  Distro: $DISTRO ($ARCH)                              |"
echo "+------------------------------------------------------+"
echo ""

# =============================================================================
# FASE 0 - PREFLIGHT
# =============================================================================
sep "FASE 0 - Preflight"

ERRORS=0

RAM_MB=$(awk '/MemTotal/{printf "%d",$2/1024}' /proc/meminfo 2>/dev/null || echo 0)
FREE_MB=$(awk '/MemAvailable/{printf "%d",$2/1024}' /proc/meminfo 2>/dev/null || echo 0)
[ "$RAM_MB" -ge 512 ] 2>/dev/null && ok "RAM: ${RAM_MB}MB (libera: ${FREE_MB}MB)" || {
    warn "RAM: ${RAM_MB}MB - insufficiente"; ERRORS=$((ERRORS+1)); }

mkdir -p /opt 2>/dev/null || true
DISK_MB=$(df /opt --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
[ "$DISK_MB" -ge 1000 ] 2>/dev/null && ok "Disco /opt: ${DISK_MB}MB" || {
    warn "Disco /opt: ${DISK_MB}MB - spazio ridotto"; }

TMP_MB=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
[ "$TMP_MB" -lt 200 ] 2>/dev/null && {
    warn "Poco spazio /tmp (${TMP_MB}MB) - pulizia..."
    apt-get clean 2>/dev/null || true
    rm -rf /tmp/javafx-* /tmp/fxext /tmp/kiosk-* /tmp/*.zip 2>/dev/null || true
}

curl -sf --max-time 10 https://github.com -o /dev/null && ok "Internet: OK" || {
    warn "Internet: non raggiungibile"; ERRORS=$((ERRORS+1)); }

command -v curl >/dev/null && ok "curl: OK" || { warn "curl mancante"; ERRORS=$((ERRORS+1)); }

[ "$ERRORS" -gt 0 ] && {
    warn "$ERRORS errore/i critico/i. Premi Invio per continuare o Ctrl+C..."
    read _X 2>/dev/null || true
} || ok "Preflight OK"

# =============================================================================
# FASE 1 - Pacchetti
# =============================================================================
sep "FASE 1 - Installazione pacchetti"

case "$PKG" in
    apt)    DEBIAN_FRONTEND=noninteractive apt-get update -qq && ok "apt update OK" ;;
    dnf)    dnf check-update -y 2>/dev/null || true ;;
    pacman) pacman -Sy --noconfirm 2>/dev/null || true ;;
esac

# Pacchetti essenziali
inst curl wget ca-certificates unzip python3

# GNOME Kiosk e overlayroot
inst gnome-kiosk gnome-kiosk-script-session overlayroot

# X11 (opzionale, per sessione X11) e utilità
inst xorg x11-xserver-utils xinit openbox unclutter

# Java
inst openjdk-17-jre-headless || inst default-jre

# JavaFX (pacchetto di sistema, se disponibile)
inst openjfx 2>/dev/null || true

# GTK, Mesa, librerie grafiche
inst libgtk-3-0 libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa libgbm1 libdrm2

# Font
inst fonts-dejavu-core fonts-noto-color-emoji

# SSH (per gestione remota)
inst openssh-server

command -v java >/dev/null && ok "Java: $(java -version 2>&1 | head -1)" || warn "Java non trovato"
ldconfig 2>/dev/null || true

# =============================================================================
# FASE 2 - Utente kiosk
# =============================================================================
sep "FASE 2 - Utente kiosk"

if id "$KIOSK_USER" >/dev/null 2>&1; then
    log "Utente $KIOSK_USER esiste - lo rimuovo per ricrearlo pulito..."
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    userdel -r -f "$KIOSK_USER" 2>/dev/null || true
fi

log "Creo utente $KIOSK_USER..."
useradd -m -s /bin/bash -G video,input,render,audio,tty "$KIOSK_USER" || \
useradd -m -s /bin/bash "$KIOSK_USER" || fail "Impossibile creare $KIOSK_USER"
passwd -d "$KIOSK_USER"

KIOSK_HOME=$(eval echo ~"$KIOSK_USER")
KIOSK_UID=$(id -u "$KIOSK_USER")
ok "Utente $KIOSK_USER: UID=$KIOSK_UID home=$KIOSK_HOME"

# Abilita autologin per l'utente (impostazione di GNOME)
# Configura il file /var/lib/AccountsService/users/kiosk
mkdir -p /var/lib/AccountsService/users
cat > "/var/lib/AccountsService/users/${KIOSK_USER}" <<EOF
[User]
Session=gnome-kiosk-script-wayland
SystemAccount=false
EOF
chmod 644 "/var/lib/AccountsService/users/${KIOSK_USER}"
ok "Autologin configurato per GNOME Kiosk (Wayland)."

# =============================================================================
# FASE 3 - Download app e librerie
# =============================================================================
sep "FASE 3 - Download app"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/config"

# demo-1.jar
if [ -f "${APP_DIR}/demo-1.jar" ]; then
    SZ=$(stat -c%s "${APP_DIR}/demo-1.jar" 2>/dev/null || echo 0)
    if [ "$SZ" -gt 10000 ] 2>/dev/null; then
        ok "demo-1.jar: gia presente ($(du -h "${APP_DIR}/demo-1.jar" | cut -f1))"
    else
        rm -f "${APP_DIR}/demo-1.jar"
    fi
fi

if [ ! -f "${APP_DIR}/demo-1.jar" ]; then
    log "Download demo-1.jar..."
    DONE=false
    for TRY in 1 2 3 4 5; do
        if curl -fsSL --retry 2 --connect-timeout 30 --max-time 180 \
                "$JAR_URL" -o "${APP_DIR}/demo-1.jar"; then
            SZ=$(stat -c%s "${APP_DIR}/demo-1.jar" 2>/dev/null || echo 0)
            [ "$SZ" -gt 10000 ] 2>/dev/null && { ok "demo-1.jar: OK"; DONE=true; break; }
        fi
        warn "Tentativo $TRY/5 fallito. Attendo $((TRY*3))s..."
        sleep $((TRY*3))
    done
    $DONE || fail "Impossibile scaricare demo-1.jar."
fi

# lib.tar.gz - Download ed estrazione robusta
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$LIB_N" -gt 0 ]; then
    ok "Librerie: gia presenti ($LIB_N JAR)"
else
    log "Download librerie (lib.tar.gz)..."
    DOWNLOAD_OK=false
    for TRY in 1 2 3; do
        rm -f /tmp/kiosk-lib.tar.gz
        if curl -fsSL --retry 2 --max-time 180 "$LIB_URL" -o /tmp/kiosk-lib.tar.gz; then
            SIZE=$(stat -c%s /tmp/kiosk-lib.tar.gz 2>/dev/null || echo 0)
            [ "$SIZE" -lt 1024 ] && { warn "File troppo piccolo"; continue; }
            # Analizza contenuto per decidere strip
            FIRST_ENTRY=$(tar -tzf /tmp/kiosk-lib.tar.gz 2>/dev/null | head -1)
            STRIP_OPT=""
            [[ "$FIRST_ENTRY" == lib/* || "$FIRST_ENTRY" == ./lib/* ]] && STRIP_OPT="--strip-components=1"
            mkdir -p "${APP_DIR}/lib"
            if tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" $STRIP_OPT 2>/dev/null; then
                LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
                if [ "$LIB_N" -gt 0 ]; then
                    ok "Librerie: $LIB_N JAR estratti"
                    DOWNLOAD_OK=true
                    break
                fi
            fi
        fi
        rm -f /tmp/kiosk-lib.tar.gz
        sleep $((TRY*3))
    done
    [ "$DOWNLOAD_OK" = "true" ] || fail "Impossibile scaricare lib.tar.gz"
fi

# =============================================================================
# FASE 4 - JavaFX SDK
# =============================================================================
sep "FASE 4 - JavaFX SDK"
JAVAFX_DIR="${APP_DIR}/javafx-sdk"
FX_PATH=""

FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$FX_N" -gt 0 ]; then
    ok "JavaFX SDK: gia presente ($FX_N JAR)"
    FX_PATH="$JAVAFX_DIR"
fi

if [ -z "$FX_PATH" ]; then
    # Cerca JavaFX di sistema
    FX_JAR=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" 2>/dev/null | head -1)
    if [ -n "$FX_JAR" ]; then
        FX_SYS=$(dirname "$FX_JAR")
        mkdir -p "$JAVAFX_DIR"
        cp "${FX_SYS}/"*.jar "$JAVAFX_DIR/" 2>/dev/null || true
        cp "${FX_SYS}/"*.so "$JAVAFX_DIR/" 2>/dev/null || true
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        [ "$FX_N" -gt 0 ] && { ok "JavaFX di sistema copiato"; FX_PATH="$JAVAFX_DIR"; }
    fi
fi

if [ -z "$FX_PATH" ]; then
    # Download da Gluon
    log "Download JavaFX SDK $FX_VER da Gluon (~80MB)..."
    for TRY in 1 2 3; do
        rm -f /tmp/javafx.zip
        if curl -fsSL --max-time 360 "$FX_URL" -o /tmp/javafx.zip; then
            mkdir -p /tmp/fxext
            unzip -q /tmp/javafx.zip -d /tmp/fxext/ -x "*/libjfxwebkit.so" "*/src.zip" 2>/dev/null
            mkdir -p "$JAVAFX_DIR"
            find /tmp/fxext -name "*.jar" -exec cp {} "$JAVAFX_DIR/" \;
            find /tmp/fxext -name "*.so" ! -name "libjfxwebkit.so" -exec cp {} "$JAVAFX_DIR/" \;
            rm -rf /tmp/javafx.zip /tmp/fxext
            FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
            [ "$FX_N" -gt 0 ] && { ok "JavaFX SDK scaricato"; FX_PATH="$JAVAFX_DIR"; break; }
        fi
        sleep $((TRY*5))
    done
fi

FX_PATH="${FX_PATH:-${APP_DIR}/lib}"
log "FX_PATH: $FX_PATH"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 5 - Script di avvio per GNOME Kiosk
# =============================================================================
sep "FASE 5 - Script di avvio (gnome-kiosk-script)"

# Crea la directory per lo script (se non esiste)
mkdir -p "${KIOSK_HOME}/.local/bin"

# Crea lo script che verrà eseguito da GNOME Kiosk
cat > "${KIOSK_HOME}/.local/bin/gnome-kiosk-script" << 'KIOSKSCRIPT'
#!/bin/bash
# Script per GNOME Kiosk - avvia l'applicazione JavaFX

APP_DIR="/opt/kiosk"
LOG="${APP_DIR}/logs/kiosk.log"
ERRLOG="${APP_DIR}/logs/kiosk-err.log"
PROFILE_FILE="${APP_DIR}/config/profile.conf"
mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"

export DISPLAY=:0
export XAUTHORITY=/run/user/$(id -u)/gdm/Xauthority  # per Wayland? Forse non serve

# Funzioni helper (copiate da run-kiosk.sh)
find_fx() {
    [ -d "${APP_DIR}/javafx-sdk" ] && ls "${APP_DIR}/javafx-sdk/"*.jar >/dev/null 2>&1 && echo "${APP_DIR}/javafx-sdk" && return
    [ -n "$FX_PATH_DEFAULT" ] && [ -d "$FX_PATH_DEFAULT" ] && echo "$FX_PATH_DEFAULT" && return
    FXJ=$(find /usr/share/java /usr/lib/jvm /usr/share/openjfx -name "javafx.controls.jar" 2>/dev/null | head -1)
    [ -n "$FXJ" ] && dirname "$FXJ" && return
    echo "${APP_DIR}/lib"
}

build_cp() {
    CP="${APP_DIR}/demo-1.jar"
    for J in "${APP_DIR}/lib/"*.jar; do
        [ -f "$J" ] && CP="${CP}:${J}"
    done
    echo "$CP"
}

# Leggi profilo
PROFILE="x11_sw"
[ -f "$PROFILE_FILE" ] && PROFILE=$(cat "$PROFILE_FILE")

export TOTEM_API_KEY="api_key_totem_1"
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=3.3
export GALLIUM_DRIVER=softpipe

case "$PROFILE" in
    x11_sw)      JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false" ;;
    x11_gtk2)    JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2 -Djava.awt.headless=false" ;;
    x11_llvmpipe) export GALLIUM_DRIVER=llvmpipe; export MESA_GL_VERSION_OVERRIDE=4.5; JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false" ;;
    x11_verbose) MEM="-Xms128m -Xmx512m"; JFXOPTS="-Dprism.order=sw -Dprism.verbose=true -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false" ;;
    *)           JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false" ;;
esac

# Esegui JavaFX
FX=$(find_fx)
CP=$(build_cp)
MEM="-Xms64m -Xmx256m"

echo "=== Avvio JavaFX (profilo $PROFILE) ===" >> "$LOG"
java $MEM \
    --module-path "$FX" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
    --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    $JFXOPTS \
    -cp "$CP" \
    com.example.App >> "$LOG" 2>> "$ERRLOG"

# Se termina, GNOME Kiosk riavvierà lo script automaticamente (grazie al loop in fondo)
# ma dobbiamo mettere un loop per mantenerlo vivo? No, GNOME Kiosk riavvia lo script se termina? Sì, se lo script termina, la sessione termina.
# Per far sì che l'applicazione venga riavviata in caso di crash, mettiamo un while true qui dentro.
# In realtà, il meccanismo di GNOME Kiosc prevede che lo script venga eseguito una volta; se termina, la sessione finisce.
# Quindi dobbiamo mettere un loop di riavvio manuale.

while true; do
    java $MEM \
        --module-path "$FX" \
        --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
        --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
        --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
        $JFXOPTS \
        -cp "$CP" \
        com.example.App >> "$LOG" 2>> "$ERRLOG"
    EC=$?
    echo "$(date): Java uscito con codice $EC - riavvio tra 3s" >> "$LOG"
    sleep 3
done
KIOSKSCRIPT

chmod +x "${KIOSK_HOME}/.local/bin/gnome-kiosk-script"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.local"

# Imposta profilo iniziale
echo "x11_sw" > "${APP_DIR}/config/profile.conf"
chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/config/profile.conf"

ok "Script di avvio creato in ~/.local/bin/gnome-kiosk-script"

# =============================================================================
# FASE 6 - Cursore invisibile (opzionale, già gestito da GNOME Kiosk? Meglio unclutter)
# =============================================================================
sep "FASE 6 - Configurazione accessorie"

# Unclutter per nascondere il cursore
if command -v unclutter >/dev/null; then
    mkdir -p "${KIOSK_HOME}/.config/autostart"
    cat > "${KIOSK_HOME}/.config/autostart/unclutter.desktop" << EOF
[Desktop Entry]
Type=Application
Name=Unclutter
Exec=unclutter -idle 0.1 -root
X-GNOME-Autostart-enabled=true
EOF
    chown -R "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.config"
    ok "Unclutter configurato per nascondere il cursore"
fi

# =============================================================================
# FASE 7 - OverlayFS (protezione root in lettura)
# =============================================================================
sep "FASE 7 - Configurazione OverlayFS"

# Assicuriamoci che /opt sia una partizione separata (come da linee guida) - qui assumiamo che lo sia.
# Configuriamo overlayroot
if [ -f /etc/overlayroot.conf ]; then
    cp /etc/overlayroot.conf /etc/overlayroot.conf.bak
fi

cat > /etc/overlayroot.conf << EOF
overlayroot_cfgdisk="disabled"
overlayroot="tmpfs:swap=1,recurse=0"
EOF
ok "OverlayFS configurato (protezione root)."

# =============================================================================
# FASE 8 - kiosk-control (comandi utente)
# =============================================================================
sep "FASE 8 - Script di controllo (kiosk-control)"

cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
APP_DIR="/opt/kiosk"
BASE_URL="https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh"

case "${1:-help}" in
    start)
        # Avvia la sessione kiosk per l'utente
        loginctl terminate-user kiosk 2>/dev/null || true
        sleep 1
        systemctl start user@$(id -u kiosk) 2>/dev/null || true
        echo "Kiosk avviato."
        ;;
    stop)
        loginctl terminate-user kiosk 2>/dev/null || true
        pkill -u kiosk 2>/dev/null || true
        echo "Kiosk fermato."
        ;;
    restart)    "$0" stop; sleep 3; "$0" start ;;
    status)
        pgrep -u kiosk java >/dev/null && echo "Kiosk: IN ESECUZIONE" || echo "Kiosk: FERMO"
        echo "Profilo: $(cat $APP_DIR/config/profile.conf 2>/dev/null || echo x11_sw)"
        tail -20 "$APP_DIR/logs/kiosk.log" 2>/dev/null || echo "(nessun log)"
        ;;
    log)        tail -f "$APP_DIR/logs/kiosk.log" ;;
    errlog)     tail -f "$APP_DIR/logs/kiosk-err.log" ;;
    profile)    cat "$APP_DIR/config/profile.conf" 2>/dev/null || echo "x11_sw" ;;
    reset-profile) echo "x11_sw" > "$APP_DIR/config/profile.conf"; echo "Profilo resettato." ;;
    reset-soft) curl -fsSL "$BASE_URL" | bash -s reset-soft ;;
    reset)      curl -fsSL "$BASE_URL" | bash -s reset ;;
    fix)        curl -fsSL "$BASE_URL" | bash -s fix ;;
    update)     curl -fsSL "$BASE_URL" | bash -s update "${2:-}" ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|errlog|profile|reset-profile|reset-soft|reset|fix|update [tag]}"
        ;;
esac
CTRLEOF
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control
ok "kiosk-control installato."

# =============================================================================
# FASE 9 - Ottimizzazioni varie
# =============================================================================
sep "FASE 9 - Ottimizzazioni sistema"

# Disabilita sleep, lock screen, ecc. per l'utente kiosk (via gsettings)
# Questo richiede che l'utente abbia un dbus attivo, ma possiamo preconfigurare
sudo -u "$KIOSK_USER" dbus-launch gsettings set org.gnome.desktop.screensaver idle-activation-enabled false 2>/dev/null || true
sudo -u "$KIOSK_USER" dbus-launch gsettings set org.gnome.desktop.screensaver lock-enabled false 2>/dev/null || true
sudo -u "$KIOSK_USER" dbus-launch gsettings set org.gnome.desktop.session idle-delay 0 2>/dev/null || true

# GRUB timeout ridotto
if [ -f /etc/default/grub ]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB timeout ridotto a 2s"
fi

# sysctl
printf 'kernel.panic=30\nvm.swappiness=5\n' > /etc/sysctl.d/99-kiosk.conf
sysctl -p /etc/sysctl.d/99-kiosk.conf >/dev/null 2>&1 || true

# udev
printf 'SUBSYSTEM=="drm", TAG+="uaccess", GROUP="video"\n' > /etc/udev/rules.d/99-kiosk.rules
printf 'SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"\n' >> /etc/udev/rules.d/99-kiosk.rules
udevadm control --reload-rules 2>/dev/null || true

# Disabilita servizi non necessari
for SVC in bluetooth cups avahi-daemon apt-daily.timer apt-daily-upgrade.timer; do
    systemctl disable "$SVC" 2>/dev/null || true
    systemctl mask "$SVC" 2>/dev/null || true
done

# Journal ridotto
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nCompress=yes\n' > /etc/systemd/journald.conf.d/kiosk.conf
systemctl daemon-reload

ok "Ottimizzazioni completate."

# =============================================================================
# FASE 10 - Verifica finale
# =============================================================================
sep "FASE 10 - Verifica finale"

ALL_OK=true
chk() {
    LABEL="$1"; CMD="$2"
    eval "$CMD" >/dev/null 2>&1 && ok "$LABEL" || { warn "$LABEL: PROBLEMA"; ALL_OK=false; }
}

chk "demo-1.jar"          "[ -f '${APP_DIR}/demo-1.jar' ]"
chk "gnome-kiosk-script"  "[ -x '${KIOSK_HOME}/.local/bin/gnome-kiosk-script' ]"
chk "Autologin config"    "[ -f '/var/lib/AccountsService/users/${KIOSK_USER}' ]"
chk "kiosk-control"       "[ -x '/usr/local/bin/kiosk-control' ]"
chk "Java"                "command -v java"
chk "overlayroot.conf"    "[ -f /etc/overlayroot.conf ]"

FX_N=$(ls "${APP_DIR}/javafx-sdk/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$FX_N" -gt 0 ] && ok "JavaFX SDK: $FX_N JAR" || warn "JavaFX SDK: non trovato"

LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$LIB_N" -gt 0 ] && ok "lib/: $LIB_N JAR" || warn "lib/: nessun JAR"

echo ""
if [ "$ALL_OK" = "true" ]; then
    echo "+------------------------------------------------------+"
    echo "|  SETUP COMPLETATO - TUTTO OK                         |"
    echo "+------------------------------------------------------+"
else
    echo "+------------------------------------------------------+"
    echo "|  SETUP COMPLETATO CON AVVISI                         |"
    echo "|  Usa: kiosk-control fix  per riparare                |"
    echo "+------------------------------------------------------+"
fi

echo ""
echo "  Riepilogo:"
echo "    - GNOME Kiosk attivo con utente $KIOSK_USER"
echo "    - OverlayFS proteggerà il root al prossimo riavvio"
echo "    - L'applicazione JavaFX verrà avviata automaticamente"
echo ""
echo "  Comandi:"
echo "    kiosk-control status"
echo "    kiosk-control log"
echo "    kiosk-control stop/start"
echo ""
echo "  Per disabilitare OverlayFS temporaneamente (es. per aggiornamenti):"
echo "    sudo overlayroot-chroot"
echo "    # modifica /etc/overlayroot.conf commentando la riga overlayroot="
echo "    exit; reboot"
echo ""
echo "  Riavvia ora con: reboot"