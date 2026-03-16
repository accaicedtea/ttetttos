#!/usr/bin/env bash
# =============================================================================
# setup-kiosk.sh - Kiosk JavaFX su Linux (X11 + startx, NO cage/Wayland)
#
# Funziona su: Debian 11/12, Ubuntu 20.04/22.04/24.04, qualsiasi distro con X11
# Approccio: TTY1 auto-login -> .bash_profile -> startx -> .xinitrc -> JavaFX
#
# MODIFICHE:
#   - Le operazioni che possono disturbare la sessione GUI (disabilitazione
#     display manager, mascheramento TTY, cambio runlevel) vengono eseguite
#     solo al termine dell'installazione, subito prima del riavvio.
#   - In questo modo lo script può essere lanciato da una finestra di terminale
#     all'interno di un ambiente grafico senza interrompere prematuramente
#     l'esecuzione.
#   - Dopo la configurazione, il sistema viene riavviato automaticamente
#     (countdown di 10 secondi) per avviare la modalità kiosk.
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
mkdir -p "$(dirname $LOG)" && touch "$LOG" 2>/dev/null || true
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
# Funzione pulizia sistema completa (solo per reset/reset-soft)
# =============================================================================
clean_system() {
    sep "Pulizia sistema"

    # Ferma display manager e server X (necessario per reset)
    log "Fermo display manager e X server..."
    for DM in gdm3 gdm lightdm sddm xdm; do
        systemctl stop    "$DM" 2>/dev/null || true
        systemctl disable "$DM" 2>/dev/null || true
        systemctl mask    "$DM" 2>/dev/null || true
    done
    pkill -9 Xorg   2>/dev/null || true
    pkill -9 X      2>/dev/null || true
    pkill -9 java   2>/dev/null || true
    pkill -9 openbox 2>/dev/null || true
    sleep 1
    ok "Display manager fermati."

    # Rimuovi servizi systemd kiosk
    for SVC in kiosk kiosk-logclean kiosk-nightly-restart; do
        systemctl stop    "${SVC}.service" 2>/dev/null || true
        systemctl disable "${SVC}.service" 2>/dev/null || true
        rm -f "/etc/systemd/system/${SVC}.service"
        rm -f "/etc/systemd/system/${SVC}.timer"
    done
    systemctl daemon-reload 2>/dev/null || true

    # Rimuovi TUTTI gli utenti non di sistema (UID >= 1000)
    log "Rimuovo utenti non di sistema..."
    while IFS=: read -r UNAME _ UID _ _ UHOME _; do
        [ "$UID" -lt 1000 ] 2>/dev/null && continue
        [ "$UNAME" = "nobody" ] && continue
        log "  Rimuovo: $UNAME (UID=$UID)"
        pkill -u "$UNAME" 2>/dev/null || true
        sleep 0.3
        userdel -r -f "$UNAME" 2>/dev/null || \
        userdel -r    "$UNAME" 2>/dev/null || \
        userdel       "$UNAME" 2>/dev/null || true
        [ -n "$UHOME" ] && [ "$UHOME" != "/" ] && rm -rf "$UHOME" 2>/dev/null || true
    done < /etc/passwd
    ok "Utenti rimossi."

    # Rimuovi configurazioni
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -f /usr/local/bin/kiosk-control
    rm -rf /usr/share/icons/blank-cursor

    # Riabilita TTY (non necessario ora, ma per pulizia)
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done
    systemctl daemon-reload 2>/dev/null || true
    ok "Pulizia completata."
}

# =============================================================================
# Funzione per applicare le modifiche di sistema che richiedono il riavvio
# (disabilita DM, maschera TTY, imposta target) e riavvia
# =============================================================================
apply_system_changes_and_reboot() {
    sep "APPLICAZIONE MODIFICHE DI SISTEMA E RIAVVIO"

    log "Disabilito display manager (gdm, lightdm, sddm, xdm)..."
    for DM in gdm3 gdm lightdm sddm xdm; do
        if systemctl is-active "$DM" >/dev/null 2>&1 || systemctl is-enabled "$DM" >/dev/null 2>&1; then
            systemctl stop "$DM" 2>/dev/null || true
            systemctl disable "$DM" 2>/dev/null || true
            systemctl mask "$DM" 2>/dev/null || true
            log "  $DM disabilitato e mascherato."
        fi
    done

    log "Maschero getty sulle TTY 2-6..."
    for i in 2 3 4 5 6; do
        systemctl mask "getty@tty${i}.service" 2>/dev/null || true
    done

    log "Imposto default target a multi-user (senza DM)..."
    systemctl set-default multi-user.target 2>/dev/null || true

    log "Tutte le modifiche sono state applicate."

    echo ""
    echo "===================================================="
    echo "  SETUP COMPLETATO - RIAVVIO IN 10 SECONDI"
    echo "  Premi Ctrl+C per annullare il riavvio e fermarti"
    echo "  (poi potrai riavviare manualmente con 'reboot')"
    echo "===================================================="
    sleep 10
    log "Riavvio in corso..."
    reboot
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
    pkill -9 java 2>/dev/null || true
    pkill -9 Xorg 2>/dev/null || true
    sleep 1
    MODE="install"
fi

if [ "$MODE" = "update" ]; then
    sep "UPDATE JAR"
    TAG="${2:-$RELEASE_TAG}"
    URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${TAG}/demo-1.jar"
    pkill java 2>/dev/null || true
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
echo "|  KIOSK SETUP - $(date '+%Y-%m-%d %H:%M')               |"
echo "|  Distro: $DISTRO ($ARCH)                        |"
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

# Avviso importante: display manager attivi (ma non li fermiamo ora)
DM_ACTIVE=""
for DM in gdm3 gdm lightdm sddm xdm; do
    systemctl is-active "$DM" >/dev/null 2>&1 && DM_ACTIVE="$DM_ACTIVE $DM"
done
if [ -n "$DM_ACTIVE" ]; then
    warn "Display manager attivi:$DM_ACTIVE"
    warn "Verranno disabilitati SOLO DOPO il completamento dell'installazione."
fi

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

# Strumenti base
inst curl wget ca-certificates unzip python3

# X11 - APPROCCIO SEMPLICE CHE FUNZIONA OVUNQUE
inst xorg openbox xinit x11-xserver-utils

# Font
inst fonts-dejavu-core 2>/dev/null || true
inst fonts-noto-color-emoji 2>/dev/null || true

# Strumenti sistema
inst pciutils util-linux procps iproute2 lm-sensors

# GTK3 (JavaFX glass platform)
inst libgtk-3-0 2>/dev/null || inst gtk3 2>/dev/null || true

# Mesa OpenGL - software rendering
case "$PKG" in
    apt)
        inst libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa \
             libgles2-mesa libgl1-mesa-swrast libgbm1 libdrm2
        ;;
    dnf)
        inst mesa-dri-drivers mesa-libGL mesa-libEGL mesa-libgbm libdrm ;;
    pacman)
        inst mesa libdrm ;;
esac

# Java
log "Installo Java..."
JAVA_OK=false
case "$PKG" in
    apt)
        DEBIAN_FRONTEND=noninteractive apt-get install -y \
            --no-install-recommends default-jre 2>/dev/null && JAVA_OK=true || \
        DEBIAN_FRONTEND=noninteractive apt-get install -y \
            --no-install-recommends openjdk-21-jre-headless 2>/dev/null && JAVA_OK=true || \
        DEBIAN_FRONTEND=noninteractive apt-get install -y \
            --no-install-recommends openjdk-17-jre-headless 2>/dev/null && JAVA_OK=true || true
        ;;
    dnf)
        dnf install -y java-21-openjdk-headless 2>/dev/null && JAVA_OK=true || \
        dnf install -y java-17-openjdk-headless 2>/dev/null && JAVA_OK=true || true
        ;;
    pacman)
        pacman -S --noconfirm jre21-openjdk-headless 2>/dev/null && JAVA_OK=true || \
        pacman -S --noconfirm jre17-openjdk-headless 2>/dev/null && JAVA_OK=true || true
        ;;
esac

# JavaFX sistema
inst openjfx 2>/dev/null || true

# unclutter: nasconde il cursore del mouse
inst unclutter 2>/dev/null || inst unclutter-xfixes 2>/dev/null || true

command -v java >/dev/null && ok "Java: $(java -version 2>&1 | head -1)" || warn "Java non trovato"
command -v startx >/dev/null && ok "startx: OK" || warn "startx non trovato"
command -v openbox >/dev/null && ok "openbox: OK" || warn "openbox non trovato"
ldconfig 2>/dev/null || true

# =============================================================================
# FASE 2 - Utente kiosk (sempre ricreato da zero)
# =============================================================================
sep "FASE 2 - Utente kiosk"

if id "$KIOSK_USER" >/dev/null 2>&1; then
    log "Utente $KIOSK_USER esiste - lo rimuovo per ricrearlo pulito..."
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    userdel -r -f "$KIOSK_USER" 2>/dev/null || \
    userdel -r    "$KIOSK_USER" 2>/dev/null || true
    ok "Rimosso."
fi

log "Creo utente $KIOSK_USER..."
useradd -m -s /bin/bash -G video,input,render,audio "$KIOSK_USER" 2>/dev/null || \
useradd -m -s /bin/bash "$KIOSK_USER" || fail "Impossibile creare $KIOSK_USER"
passwd -d "$KIOSK_USER"

KIOSK_HOME=$(eval echo ~"$KIOSK_USER")
KIOSK_UID=$(id -u "$KIOSK_USER")
ok "Utente $KIOSK_USER: UID=$KIOSK_UID home=$KIOSK_HOME"
log "Gruppi: $(id $KIOSK_USER)"

# =============================================================================
# FASE 3 - Download app
# =============================================================================
sep "FASE 3 - Download app"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/config"

# demo-1.jar
if [ -f "${APP_DIR}/demo-1.jar" ]; then
    SZ=$(stat -c%s "${APP_DIR}/demo-1.jar" 2>/dev/null || echo 0)
    if [ "$SZ" -gt 10000 ] 2>/dev/null; then
        ok "demo-1.jar: gia presente ($(du -h "${APP_DIR}/demo-1.jar" | cut -f1))"
    else
        warn "demo-1.jar presente ma corrotto - riscarico..."
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
            [ "$SZ" -gt 10000 ] 2>/dev/null && {
                ok "demo-1.jar: $(du -h "${APP_DIR}/demo-1.jar" | cut -f1)"; DONE=true; break; }
            warn "File troppo piccolo ($SZ bytes)"
        fi
        warn "Tentativo $TRY/5 fallito. Attendo $((TRY*3))s..."
        sleep $((TRY*3))
    done
    $DONE || fail "Impossibile scaricare demo-1.jar."
fi

# lib.tar.gz
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$LIB_N" -gt 0 ]; then
    ok "Librerie: gia presenti ($LIB_N JAR)"
else
    log "Download librerie..."
    for TRY in 1 2 3; do
        rm -f /tmp/kiosk-lib.tar.gz
        if curl -fsSL --retry 2 --max-time 180 \
                "$LIB_URL" -o /tmp/kiosk-lib.tar.gz; then
            tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" 2>/dev/null || \
            tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" \
                --strip-components=1 2>/dev/null || true
            rm -f /tmp/kiosk-lib.tar.gz
            LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
            ok "Librerie: $LIB_N JAR"
            break
        fi
        warn "Tentativo $TRY/3 fallito"
        sleep $((TRY*3))
        [ $TRY -eq 3 ] && warn "lib.tar.gz non scaricato - continuo"
    done
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
    FX_JAR=$(find /usr/share/java /usr/lib/jvm \
                  -name "javafx.controls.jar" 2>/dev/null | head -1 || true)
    if [ -n "$FX_JAR" ]; then
        FX_SYS=$(dirname "$FX_JAR")
        log "JavaFX di sistema: $FX_SYS"
        mkdir -p "$JAVAFX_DIR"
        cp "${FX_SYS}/"*.jar "$JAVAFX_DIR/" 2>/dev/null || true
        find "$FX_SYS" -name "*.so" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "JavaFX di sistema copiato: $FX_N JAR"
        FX_PATH="$JAVAFX_DIR"
    fi
fi

if [ -z "$FX_PATH" ]; then
    TMP_MB=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 999)
    [ "$TMP_MB" -lt 200 ] 2>/dev/null && {
        warn "Poco spazio /tmp - pulizia..."
        apt-get clean 2>/dev/null || true
        rm -rf /tmp/javafx-* /tmp/fxext /tmp/*.zip 2>/dev/null || true
    }
    log "Download JavaFX SDK $FX_VER da Gluon (~80MB)..."
    for TRY in 1 2 3; do
        rm -f /tmp/javafx.zip
        if curl -fsSL --max-time 360 "$FX_URL" -o /tmp/javafx.zip; then
            log "Estraggo JavaFX (escludo webkit 60MB inutile)..."
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
            [ "$FX_N" -gt 0 ] && {
                ok "JavaFX SDK: $FX_N JAR"; FX_PATH="$JAVAFX_DIR"; break; } || \
                warn "Nessun JAR estratto"
        else
            warn "Download tentativo $TRY/3 fallito"
            sleep $((TRY*5))
        fi
    done
fi

FX_PATH="${FX_PATH:-${APP_DIR}/lib}"
log "FX_PATH: $FX_PATH"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 5 - Test sistema (senza disabilitare DM)
# =============================================================================
sep "FASE 5 - Test e riparazione"

# Java
command -v java >/dev/null && ok "Java: $(java -version 2>&1 | head -1)" || {
    warn "Java non trovato - reinstallo..."
    inst default-jre 2>/dev/null || inst openjdk-17-jre-headless 2>/dev/null || true
}

# JavaFX moduli
if command -v java >/dev/null && [ -d "$FX_PATH" ]; then
    java --module-path "$FX_PATH" --list-modules 2>/dev/null | \
        grep -q "javafx.controls" && ok "JavaFX moduli: OK" || \
        warn "JavaFX moduli non trovati in $FX_PATH"
fi

# GTK3
ldconfig -p 2>/dev/null | grep -q "libgtk-3.so" && ok "GTK3: OK" || {
    warn "GTK3 mancante - installo..."
    inst libgtk-3-0 2>/dev/null || true; ldconfig 2>/dev/null || true; }

# libGL
ldconfig -p 2>/dev/null | grep -q "libGL.so" && ok "libGL: OK" || {
    warn "libGL mancante - installo..."
    inst libgl1-mesa-dri libgl1-mesa-glx libgl1-mesa-swrast 2>/dev/null || true
    ldconfig 2>/dev/null || true; }

# startx / openbox
command -v startx  >/dev/null && ok "startx: OK"  || { warn "startx mancante"; inst xinit; }
command -v openbox >/dev/null && ok "openbox: OK" || { warn "openbox mancante"; inst openbox; }

# NOTA: non disabilitiamo i display manager ora, lo faremo alla fine.
ok "Fase 5 completata."

# =============================================================================
# FASE 6 - Cursore invisibile
# =============================================================================
sep "FASE 6 - Cursore invisibile"
mkdir -p /usr/share/icons/blank-cursor/cursors
command -v python3 >/dev/null && python3 << 'PYEOF'
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
        try: os.symlink('left_ptr', dst)
        except: pass
PYEOF
printf '[Icon Theme]\nName=blank-cursor\n' \
    > /usr/share/icons/blank-cursor/index.theme
ok "Cursore invisibile creato."

# =============================================================================
# FASE 7 - run-kiosk.sh (self-healing con X11/DISPLAY=:0)
# =============================================================================
sep "FASE 7 - Script di avvio (self-healing)"
RUN="${APP_DIR}/run-kiosk.sh"

printf '#!/usr/bin/env bash\n'                   > "$RUN"
printf 'APP_DIR="%s"\n'        "$APP_DIR"       >> "$RUN"
printf 'API_KEY="%s"\n'        "$API_KEY"       >> "$RUN"
printf 'FX_PATH_DEFAULT="%s"\n' "$FX_PATH"      >> "$RUN"

cat >> "$RUN" << 'RUNEOF'
# run-kiosk.sh - lanciato da .xinitrc dopo startx
# DISPLAY=:0 e gia settato da X11

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

detect_error() {
    O="$1"
    echo "$O" | grep -qi "javafx.*not found\|FindException\|boot layer" \
        && echo "no_javafx"  && return
    echo "$O" | grep -qi "Cannot connect to X\|no display\|DISPLAY" \
        && echo "no_display" && return
    echo "$O" | grep -qi "OutOfMemoryError\|heap space" \
        && echo "oom"        && return
    echo "$O" | grep -qi "UnsatisfiedLinkError\|library.*not found" \
        && echo "missing_lib" && return
    echo "$O" | grep -qi "libGL\|MESA\|prism.*fail" \
        && echo "opengl"     && return
    echo "unknown"
}

# Con X11 usiamo sempre DISPLAY=:0 (impostato da startx)
# Profili: variano solo le opzioni di rendering JavaFX
PROFILE="x11_sw"
[ -f "$PROFILE_FILE" ] && PROFILE=$(cat "$PROFILE_FILE" | tr -d '[:space:]')

ATTEMPT=0
MEM="-Xms64m -Xmx256m"
LAST_WORKING=""

log "======================================="
log "Kiosk avvio su X11"
log "DISPLAY: ${DISPLAY:-non impostato}"
log "Profilo: $PROFILE"
log "FX: $(find_fx)"
log "======================================="

while true; do
    ATTEMPT=$((ATTEMPT+1))
    FX=$(find_fx)
    CP=$(build_cp)

    # Con X11, DISPLAY=:0 e sempre disponibile (impostato da startx)
    # Non serve Wayland, non serve cage, non serve seatd
    export TOTEM_API_KEY="$API_KEY"
    export LIBGL_ALWAYS_SOFTWARE=1
    export MESA_GL_VERSION_OVERRIDE=3.3
    export GALLIUM_DRIVER=softpipe

    case "$PROFILE" in
        x11_sw)
            # Standard: X11 + software rendering + GTK3
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
        x11_gtk2)
            # Fallback GTK2
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2 -Djava.awt.headless=false"
            ;;
        x11_llvmpipe)
            # llvmpipe renderer
            export GALLIUM_DRIVER=llvmpipe
            export MESA_GL_VERSION_OVERRIDE=4.5
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
        x11_verbose)
            # Verbose per debug
            MEM="-Xms128m -Xmx512m"
            JFXOPTS="-Dprism.order=sw -Dprism.forceGPU=false -Dprism.verbose=true -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
        *)
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3 -Djava.awt.headless=false"
            ;;
    esac

    log "--- Tentativo $ATTEMPT | profilo=$PROFILE | DISPLAY=${DISPLAY:-:0} ---"
    TMPF=$(mktemp /tmp/kiosk.XXXXXX 2>/dev/null || echo "/tmp/kiosk-$$")

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

    log "Exit: $RC"
    [ $RC -eq 0 ] && { log "Uscita normale."; exit 0; }

    # App era partita - riavvia con stesso profilo
    if echo "$OUT" | grep -qi "SplashController\|Login OK\|Nav.*->"; then
        log "App partita con $PROFILE - riavvio in 3s..."
        echo "$PROFILE" > "$PROFILE_FILE"
        LAST_WORKING="$PROFILE"
        sleep 3
        continue
    fi

    ERRTYPE=$(detect_error "$OUT")
    LASTERR=$(echo "$OUT" | grep -i "error\|exception\|failed" | tail -3)
    log "Errore: $ERRTYPE | $LASTERR"

    case "$ERRTYPE" in
        no_javafx)
            log "JavaFX mancante - installo openjfx..."
            apt-get install -y openjfx 2>/dev/null || true
            NEXT="x11_sw"
            ;;
        missing_lib)
            log "Libreria nativa mancante - reinstallo Mesa..."
            apt-get install -y libgl1-mesa-dri libgl1-mesa-glx \
                libegl1-mesa libgbm1 2>/dev/null || true
            ldconfig 2>/dev/null || true
            NEXT="x11_sw"
            ;;
        oom)
            MEM="-Xms128m -Xmx512m"
            log "OOM - aumento heap a 512m"
            NEXT="$PROFILE"
            ;;
        *)
            case "$PROFILE" in
                x11_sw)       NEXT="x11_gtk2" ;;
                x11_gtk2)     NEXT="x11_llvmpipe" ;;
                x11_llvmpipe) NEXT="x11_verbose" ;;
                x11_verbose)  NEXT="x11_sw" ;;
                *)             NEXT="x11_sw" ;;
            esac
            ;;
    esac

    if [ $ATTEMPT -ge 8 ]; then
        log "8 tentativi. Pausa 30s..."
        NEXT="${LAST_WORKING:-x11_sw}"
        ATTEMPT=0
        sleep 30
    fi

    log "Cambio: $PROFILE -> $NEXT"
    PROFILE="$NEXT"
    echo "$PROFILE" > "$PROFILE_FILE"
    WAIT=$((ATTEMPT * 2))
    [ $WAIT -gt 15 ] && WAIT=15
    log "Attendo ${WAIT}s..."
    sleep $WAIT
done
RUNEOF

chmod +x "$RUN"
bash -n "$RUN" && ok "run-kiosk.sh: sintassi OK" || warn "run-kiosk.sh: errore sintassi"

echo "x11_sw" > "${APP_DIR}/config/profile.conf"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 8 - .xinitrc (avviato da startx, configura X11 e lancia l'app)
# =============================================================================
sep "FASE 8 - .xinitrc (sessione X11)"

cat > "${KIOSK_HOME}/.xinitrc" << XIEOF
#!/bin/bash
# .xinitrc - configurazione sessione X11 per kiosk JavaFX

# Disabilita screensaver e risparmio energetico
xset -dpms
xset s off
xset s noblank
xset r rate 500 50

# Nasconde il cursore dopo 0.1 secondi di inattivita
unclutter -idle 0.1 -root &

# Avvia openbox come window manager (gestisce fullscreen)
openbox-session &

# Aspetta che openbox sia pronto
sleep 1

# Loop di avvio - riavvia l'app se crasha
while true; do
    /opt/kiosk/run-kiosk.sh
    EC=\$?
    echo "[$(date '+%H:%M:%S')] run-kiosk.sh uscito con codice \$EC" \
        >> /opt/kiosk/logs/kiosk.log
    # Se uscita normale (0) - esci senza riavviare
    [ \$EC -eq 0 ] && break
    echo "[$(date '+%H:%M:%S')] Riavvio in 3s..." >> /opt/kiosk/logs/kiosk.log
    sleep 3
done

# Se il loop finisce, chiudi X
openbox --exit 2>/dev/null || true
XIEOF

chmod +x "${KIOSK_HOME}/.xinitrc"
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.xinitrc"
ok ".xinitrc creato."

# =============================================================================
# FASE 9 - .bash_profile (avviato da auto-login su TTY1)
# =============================================================================
sep "FASE 9 - .bash_profile (auto-login TTY1)"

cat > "${KIOSK_HOME}/.bash_profile" << 'BPEOF'
# .bash_profile - eseguito al login su TTY1
# Lancia startx che avvia X11 + .xinitrc + JavaFX

if [ "$(tty)" = "/dev/tty1" ]; then
    # Controlla flag di stop manuale
    if [ -f /opt/kiosk/.stop ]; then
        rm -f /opt/kiosk/.stop
        echo ""
        echo "Kiosk fermato manualmente."
        echo "Per riavviare: startx"
        echo ""
    else
        echo ""
        echo "+----------------------------------------+"
        echo "|  Kiosk in avvio tra 3 secondi...       |"
        echo "|  Ctrl+C per accedere al terminale       |"
        echo "+----------------------------------------+"
        echo ""

        # 3 secondi per interrompere con Ctrl+C
        sleep 3

        # Avvia X server + sessione (loop: riavvia se X crasha)
        while true; do
            startx -- -nocursor 2>> /opt/kiosk/logs/kiosk.log
            EC=$?
            echo "[$(date '+%H:%M:%S')] startx uscito (cod $EC)" \
                >> /opt/kiosk/logs/kiosk.log
            # Fermato manualmente?
            [ -f /opt/kiosk/.stop ] && {
                rm -f /opt/kiosk/.stop
                echo "Kiosk fermato."
                break
            }
            echo "X server uscito - riavvio in 5s..."
            sleep 5
        done
    fi
fi
BPEOF
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bash_profile"
ok ".bash_profile creato."

# =============================================================================
# FASE 10 - Auto-login TTY1
# =============================================================================
sep "FASE 10 - Auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF
ok "Auto-login TTY1 configurato."

# Permessi X server (kiosk puo usare X senza sudo)
printf 'allowed_users=anybody\n' > /etc/X11/Xwrapper.config 2>/dev/null || true
# Alternativa:
dpkg-reconfigure -f noninteractive x11-common 2>/dev/null || true

printf 'kiosk ALL=(ALL) NOPASSWD: /usr/local/bin/kiosk-control\n' \
    > /etc/sudoers.d/kiosk
chmod 440 /etc/sudoers.d/kiosk

# =============================================================================
# FASE 11 - kiosk-control
# =============================================================================
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
APP_DIR="/opt/kiosk"
BASE_URL="https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh"

case "${1:-help}" in
    start)
        rm -f "$APP_DIR/.stop"
        su - kiosk -c "DISPLAY=:0 startx -- -nocursor &"
        echo "Avviato."
        ;;
    stop)
        touch "$APP_DIR/.stop"
        pkill java  2>/dev/null || true
        pkill Xorg  2>/dev/null || true
        pkill X     2>/dev/null || true
        echo "Fermato."
        ;;
    restart)    "$0" stop; sleep 3; "$0" start ;;
    status)
        pgrep Xorg >/dev/null && echo "X11:     IN ESECUZIONE" || echo "X11:     FERMO"
        pgrep java >/dev/null && echo "java:    IN ESECUZIONE" || echo "java:    FERMO"
        echo "Profilo: $(cat $APP_DIR/config/profile.conf 2>/dev/null || echo x11_sw)"
        echo ""
        echo "--- Log ---"
        tail -25 "$APP_DIR/logs/kiosk.log" 2>/dev/null || echo "(nessun log)"
        ;;
    log)            tail -f "$APP_DIR/logs/kiosk.log" ;;
    errlog)         tail -f "$APP_DIR/logs/kiosk-err.log" ;;
    profile)        cat "$APP_DIR/config/profile.conf" 2>/dev/null || echo "x11_sw" ;;
    reset-profile)  echo "x11_sw" > "$APP_DIR/config/profile.conf"; echo "Profilo resettato." ;;
    reset-soft)     curl -fsSL "$BASE_URL" | bash -s reset-soft ;;
    reset)          curl -fsSL "$BASE_URL" | bash -s reset ;;
    fix)            curl -fsSL "$BASE_URL" | bash -s fix ;;
    update)         curl -fsSL "$BASE_URL" | bash -s update "${2:-}" ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|errlog|profile|reset-profile|reset-soft|reset|fix|update [tag]}"
        ;;
esac
CTRLEOF
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control
ok "kiosk-control installato."

# =============================================================================
# FASE 12 - Ottimizzazioni sistema (non distruttive per la sessione corrente)
# =============================================================================
sep "FASE 12 - Ottimizzazioni sistema"

# TTY extra: non mascheriamo ora, lo faremo in fase finale
# per i in 2 3 4 5 6; do systemctl mask ... ; done  <-- SPOSTATO

# Default target: impostiamo dopo
# systemctl set-default multi-user.target  <-- SPOSTATO

# GRUB
if [ -f /etc/default/grub ]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=3/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        echo 'GRUB_TIMEOUT_STYLE=menu' >> /etc/default/grub
    update-grub 2>/dev/null || \
        grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB: 3s timeout."
fi

# sysctl
printf 'kernel.panic=30\nvm.swappiness=5\n' \
    > /etc/sysctl.d/99-kiosk.conf
sysctl -p /etc/sysctl.d/99-kiosk.conf >/dev/null 2>&1 || true

# udev
printf 'SUBSYSTEM=="drm",   TAG+="uaccess", GROUP="video"\n' \
    > /etc/udev/rules.d/99-kiosk.rules
printf 'SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"\n' \
    >> /etc/udev/rules.d/99-kiosk.rules
udevadm control --reload-rules 2>/dev/null || true

# Servizi inutili
for SVC in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer; do
    systemctl disable "$SVC" 2>/dev/null || true
    systemctl mask    "$SVC" 2>/dev/null || true
done

# Journal
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nCompress=yes\n' \
    > /etc/systemd/journald.conf.d/kiosk.conf

systemctl daemon-reload
ok "Ottimizzazioni completate."

# =============================================================================
# FASE 13 - Verifica finale
# =============================================================================
sep "FASE 13 - Verifica finale"

ALL_OK=true
chk() {
    LABEL="$1"; CMD="$2"
    eval "$CMD" >/dev/null 2>&1 && ok "$LABEL" || {
        warn "$LABEL: PROBLEMA"; ALL_OK=false; }
}

chk "demo-1.jar"          "[ -f '${APP_DIR}/demo-1.jar' ]"
chk "run-kiosk.sh"        "[ -x '${APP_DIR}/run-kiosk.sh' ]"
chk "run-kiosk.sh sintassi" "bash -n '${APP_DIR}/run-kiosk.sh'"
chk ".xinitrc"            "[ -f '${KIOSK_HOME}/.xinitrc' ]"
chk ".bash_profile"       "[ -f '${KIOSK_HOME}/.bash_profile' ]"
chk "Auto-login TTY1"     "[ -f '/etc/systemd/system/getty@tty1.service.d/autologin.conf' ]"
chk "kiosk-control"       "[ -x '/usr/local/bin/kiosk-control' ]"
chk "Java"                "command -v java"
chk "startx"              "command -v startx"
chk "openbox"             "command -v openbox"
chk "libGL"               "ldconfig -p | grep -q 'libGL.so'"
chk "libGTK3"             "ldconfig -p | grep -q 'libgtk-3.so'"

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
echo "  Come funziona (approccio X11, semplice e affidabile):"
echo "   1. reboot"
echo "   2. getty auto-login su TTY1 come utente kiosk"
echo "   3. .bash_profile chiama startx dopo 3s"
echo "   4. startx avvia X11 + .xinitrc"
echo "   5. .xinitrc avvia openbox + run-kiosk.sh"
echo "   6. run-kiosk.sh avvia JavaFX con DISPLAY=:0"
echo "   7. Se crash: riavvio automatico con profilo alternativo"
echo ""
echo "  Comandi:"
echo "   kiosk-control status       stato + ultimi log"
echo "   kiosk-control log          log live"
echo "   kiosk-control errlog       errori Java live"
echo "   kiosk-control reset-soft   risconfigura veloce"
echo "   kiosk-control reset        reinstalla tutto"
echo "   kiosk-control reset-profile torna a profilo default"
echo ""
echo "  Per il terminale durante il kiosk:"
echo "   - SSH: ssh root@<ip>"
echo "   - Locale: touch /opt/kiosk/.stop && pkill Xorg"
echo ""

# =============================================================================
# FASE 14 - Applicazione modifiche di sistema e riavvio
# =============================================================================
apply_system_changes_and_reboot