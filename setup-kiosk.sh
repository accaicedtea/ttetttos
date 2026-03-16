#!/usr/bin/env bash
# setup-kiosk.sh - Kiosk JavaFX
# Uso: curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash
#      bash setup-kiosk.sh reset
#      bash setup-kiosk.sh update

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

mkdir -p "$(dirname $LOG)" && touch "$LOG"
log()  { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG"; }
ok()   { echo "[ OK ] $*" | tee -a "$LOG"; }
warn() { echo "[WARN] $*" | tee -a "$LOG"; }
fail() { echo "[FAIL] $*" | tee -a "$LOG"; exit 1; }
sep()  { echo "" | tee -a "$LOG"; echo "=== $* ===" | tee -a "$LOG"; }

[ "$(id -u)" = "0" ] || fail "Eseguire come root: sudo bash $0"

log "Setup avviato - mode: ${1:-install} - distro: $(. /etc/os-release 2>/dev/null && echo $ID || echo unknown)"

# =============================================================================
# RESET
# =============================================================================
if [ "${1:-}" = "reset" ]; then
    sep "RESET COMPLETO"

    # Ferma cage se in esecuzione
    pkill cage 2>/dev/null || true
    pkill java 2>/dev/null || true
    sleep 1

    # Rimuovi servizi systemd kiosk se esistono
    for SVC in kiosk kiosk-logclean kiosk-nightly-restart; do
        systemctl stop    "${SVC}.service" 2>/dev/null || true
        systemctl disable "${SVC}.service" 2>/dev/null || true
        rm -f "/etc/systemd/system/${SVC}.service"
        rm -f "/etc/systemd/system/${SVC}.timer"
    done
    systemctl daemon-reload 2>/dev/null || true

    # Rimuovi utente kiosk completamente
    if id "$KIOSK_USER" >/dev/null 2>&1; then
        log "Rimuovo utente $KIOSK_USER..."
        pkill -u "$KIOSK_USER" 2>/dev/null || true
        sleep 1
        userdel -r -f "$KIOSK_USER" 2>/dev/null || userdel -r "$KIOSK_USER" 2>/dev/null || true
        ok "Utente $KIOSK_USER rimosso."
    fi

    # Rimuovi tutto il resto
    rm -rf "$APP_DIR"
    rm -f /etc/sudoers.d/kiosk
    rm -f /etc/udev/rules.d/99-kiosk.rules
    rm -f /etc/sysctl.d/99-kiosk.conf
    rm -rf /etc/systemd/system/getty@tty1.service.d/
    rm -rf /usr/share/icons/blank-cursor
    rm -f /usr/local/bin/kiosk-control

    # Riabilita TTY
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done
    systemctl daemon-reload 2>/dev/null || true

    ok "Reset completato. Reinstallazione..."
    echo ""
fi

# =============================================================================
# UPDATE
# =============================================================================
if [ "${1:-}" = "update" ]; then
    sep "Update JAR"
    pkill java 2>/dev/null || true
    sleep 1
    curl -fsSL --retry 3 "$JAR_URL" -o "${APP_DIR}/demo-1.jar" \
        && chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar" \
        && ok "Aggiornato." \
        || fail "Download fallito."
    exit 0
fi

echo ""
echo "+--------------------------------------------------+"
echo "|  KIOSK SETUP - $(date '+%Y-%m-%d')                 |"
echo "+--------------------------------------------------+"
echo ""

# =============================================================================
# FASE 1 - Pacchetti
# =============================================================================
sep "FASE 1 - Installazione pacchetti"
DEBIAN_FRONTEND=noninteractive apt-get update -qq
ok "apt-get update fatto."

log "Installo dipendenze base..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    cage xwayland \
    dbus dbus-user-session \
    libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa libgles2-mesa libgl1-mesa-swrast \
    libgtk-3-0 \
    libinput-tools \
    fonts-dejavu-core fonts-noto-color-emoji \
    ca-certificates curl wget unzip \
    pciutils util-linux procps lm-sensors \
    2>/dev/null || warn "Alcuni pacchetti non installati - continuo."

log "Installo Java..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    default-jre 2>/dev/null || \
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-21-jre-headless 2>/dev/null || \
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless 2>/dev/null || \
warn "Java non installato automaticamente."

log "Installo openjfx (JavaFX di sistema, fallback)..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjfx 2>/dev/null || warn "openjfx non disponibile - usero JavaFX SDK."

command -v java >/dev/null && ok "Java: $(java -version 2>&1 | head -1)" || warn "Java non trovato nel PATH."
command -v cage >/dev/null && ok "cage: trovato." || warn "cage non trovato."

# =============================================================================
# FASE 2 - Utente kiosk
# =============================================================================
sep "FASE 2 - Utente kiosk"

# IMPORTANTE: elimina sempre l'utente se esiste, per partire pulito
if id "$KIOSK_USER" >/dev/null 2>&1; then
    log "Utente $KIOSK_USER esiste. Lo elimino per ricrearlo pulito..."
    pkill -u "$KIOSK_USER" 2>/dev/null || true
    sleep 1
    userdel -r -f "$KIOSK_USER" 2>/dev/null || userdel -r "$KIOSK_USER" 2>/dev/null || true
    ok "Utente $KIOSK_USER rimosso."
fi

log "Creo utente $KIOSK_USER..."
useradd -m -s /bin/bash -G video,input,render,audio "$KIOSK_USER" 2>/dev/null || \
useradd -m -s /bin/bash "$KIOSK_USER"
passwd -d "$KIOSK_USER"
ok "Utente $KIOSK_USER creato."

# Aggiungi a seat/tty se i gruppi esistono
groupadd seat 2>/dev/null || true
usermod -aG seat "$KIOSK_USER" 2>/dev/null || true
usermod -aG tty  "$KIOSK_USER" 2>/dev/null || true

KIOSK_HOME=$(eval echo ~"$KIOSK_USER")
KIOSK_UID=$(id -u "$KIOSK_USER")
log "Home: $KIOSK_HOME | UID: $KIOSK_UID"

# =============================================================================
# FASE 3 - Download app
# =============================================================================
sep "FASE 3 - Download app da GitHub"
mkdir -p "${APP_DIR}/lib" "${APP_DIR}/logs" "${APP_DIR}/config"

log "Scarico demo-1.jar da GitHub..."
for TRY in 1 2 3; do
    if curl -fsSL --retry 2 --max-time 120 "$JAR_URL" -o "${APP_DIR}/demo-1.jar"; then
        ok "demo-1.jar scaricato: $(du -h "${APP_DIR}/demo-1.jar" | cut -f1)"
        break
    fi
    warn "Tentativo $TRY fallito, aspetto 5s..."
    sleep 5
    [ $TRY -eq 3 ] && fail "Impossibile scaricare demo-1.jar."
done

log "Scarico librerie da GitHub..."
for TRY in 1 2 3; do
    rm -f /tmp/kiosk-lib.tar.gz
    if curl -fsSL --retry 2 --max-time 120 "$LIB_URL" -o /tmp/kiosk-lib.tar.gz; then
        tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" 2>/dev/null || \
        tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" --strip-components=1 2>/dev/null || true
        rm -f /tmp/kiosk-lib.tar.gz
        LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "Librerie estratte: $LIB_N JAR."
        break
    fi
    warn "Tentativo $TRY fallito, aspetto 5s..."
    sleep 5
    [ $TRY -eq 3 ] && warn "lib.tar.gz non scaricato - continuo senza."
done

# =============================================================================
# FASE 4 - JavaFX SDK
# =============================================================================
sep "FASE 4 - JavaFX SDK"
JAVAFX_DIR="${APP_DIR}/javafx-sdk"

# Usa SDK gia presente
if [ -d "$JAVAFX_DIR" ]; then
    FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
    if [ "$FX_N" -gt 0 ]; then
        ok "JavaFX SDK gia presente: $FX_N JAR."
        FX_PATH="$JAVAFX_DIR"
        FX_INSTALLED=true
    fi
fi

# Prova JavaFX di sistema
if [ "${FX_INSTALLED:-false}" = "false" ]; then
    FX_JAR=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" 2>/dev/null | head -1 || true)
    if [ -n "$FX_JAR" ]; then
        FX_SYS=$(dirname "$FX_JAR")
        log "JavaFX di sistema trovato: $FX_SYS"
        mkdir -p "$JAVAFX_DIR"
        cp "${FX_SYS}/"*.jar "$JAVAFX_DIR/" 2>/dev/null || true
        find "$FX_SYS" -name "*.so" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "JavaFX di sistema copiato: $FX_N JAR."
        FX_PATH="$JAVAFX_DIR"
        FX_INSTALLED=true
    fi
fi

# Scarica da Gluon
if [ "${FX_INSTALLED:-false}" = "false" ]; then
    log "Scarico JavaFX SDK $FX_VER da Gluon..."
    FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 999)
    if [ "$FREE_TMP" -lt 200 ] 2>/dev/null; then
        log "Poco spazio /tmp (${FREE_TMP}MB) - pulizia..."
        apt-get clean 2>/dev/null || true
        rm -rf /tmp/javafx-* /tmp/fxext 2>/dev/null || true
    fi

    if curl -fsSL --max-time 300 "$FX_URL" -o /tmp/javafx.zip; then
        log "Estraggo JavaFX (escludo webkit ~60MB)..."
        mkdir -p /tmp/fxext
        unzip -q /tmp/javafx.zip -d /tmp/fxext/ \
            -x "*/libjfxwebkit.so" -x "*/libgstreamer-lite.so" -x "*/src.zip" 2>/dev/null
        true
        mkdir -p "$JAVAFX_DIR"
        find /tmp/fxext -name "*.jar" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        find /tmp/fxext -name "*.so"  \
            ! -name "libjfxwebkit.so" ! -name "libgstreamer-lite.so" \
            -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        rm -rf /tmp/javafx.zip /tmp/fxext
        FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        if [ "$FX_N" -gt 0 ]; then
            ok "JavaFX SDK installato: $FX_N JAR."
            FX_PATH="$JAVAFX_DIR"
            FX_INSTALLED=true
        else
            warn "JavaFX SDK: nessun JAR estratto."
        fi
    else
        warn "Download JavaFX fallito."
    fi
fi

FX_PATH="${FX_PATH:-${APP_DIR}/lib}"
log "FX_PATH finale: $FX_PATH"

chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 5 - Cursore trasparente
# =============================================================================
sep "FASE 5 - Cursore trasparente"
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
PYEOF
printf '[Icon Theme]\nName=blank-cursor\n' > /usr/share/icons/blank-cursor/index.theme
ok "Cursore trasparente creato."

# =============================================================================
# FASE 6 - run-kiosk.sh (self-healing)
# =============================================================================
sep "FASE 6 - Script di avvio"
RUN="${APP_DIR}/run-kiosk.sh"

printf '#!/usr/bin/env bash\n'                    > "$RUN"
printf 'APP_DIR="%s"\n'        "$APP_DIR"        >> "$RUN"
printf 'API_KEY="%s"\n'        "$API_KEY"        >> "$RUN"
printf 'FX_PATH_DEFAULT="%s"\n' "$FX_PATH"       >> "$RUN"

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
    echo "$O" | grep -qi "javafx.*not found\|FindException\|boot layer" \
        && echo "no_javafx"   && return
    echo "$O" | grep -qi "Unable to open DISPLAY\|GtkApplication\|no display" \
        && echo "no_display"  && return
    echo "$O" | grep -qi "dbus\|connection to the bus" \
        && echo "no_dbus"     && return
    echo "$O" | grep -qi "OutOfMemoryError\|heap space" \
        && echo "oom"         && return
    echo "$O" | grep -qi "libGL\|MESA\|prism.*fail" \
        && echo "opengl"      && return
    echo "unknown"
}

# Profili: xwayland primo (piu affidabile in cage -d)
PROFILE="xwayland_sw"
[ -f "$PROFILE_FILE" ] && PROFILE=$(cat "$PROFILE_FILE" | tr -d '[:space:]')

ATTEMPT=0
MEM="-Xms64m -Xmx256m"

log "======================================="
log "Avvio - profilo: $PROFILE"
log "FX_PATH: $(find_fx)"
log "======================================="

while true; do
    ATTEMPT=$((ATTEMPT+1))
    FX=$(find_fx)
    CP=$(build_cp)

    # Setup ambiente in base al profilo
    export LIBGL_ALWAYS_SOFTWARE=1
    export WLR_NO_HARDWARE_CURSORS=1
    export WLR_RENDERER_ALLOW_SOFTWARE=1
    export TOTEM_API_KEY="$API_KEY"
    unset GDK_BACKEND 2>/dev/null || true

    case "$PROFILE" in
        xwayland_sw)
            # Cage -d avvia Xwayland su :1 - il piu affidabile
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

    # Uscita normale
    [ $RC -eq 0 ] && { log "Uscita normale."; exit 0; }

    # App era partita - stesso profilo, e un crash dell'app non del renderer
    if echo "$OUT" | grep -qi "SplashController\|Login OK\|Nav.*->"; then
        log "App era partita (profilo $PROFILE funziona). Riavvio in 3s..."
        echo "$PROFILE" > "$PROFILE_FILE"
        sleep 3
        continue
    fi

    ERRTYPE=$(detect_error "$OUT")
    LASTERR=$(echo "$OUT" | grep -i "error\|exception\|failed" | tail -2)
    log "Errore: $ERRTYPE | $LASTERR"

    # Azioni speciali
    case "$ERRTYPE" in
        no_javafx)
            log "JavaFX mancante - installo openjfx..."
            apt-get install -y openjfx 2>/dev/null || true
            NEXT="xwayland_sw"
            ;;
        oom)
            MEM="-Xms128m -Xmx512m"
            log "OOM - aumento heap a 512m"
            NEXT="$PROFILE"
            ;;
        *)
            # Cicla tra profili
            case "$PROFILE" in
                xwayland_sw)  NEXT="xwayland_gtk2"  ;;
                xwayland_gtk2) NEXT="wayland_native" ;;
                wayland_native) NEXT="fallback"      ;;
                fallback)      NEXT="xwayland_sw"    ;;
                *)             NEXT="xwayland_sw"    ;;
            esac
            ;;
    esac

    # Dopo 8 tentativi totali - pausa lunga
    if [ $ATTEMPT -ge 8 ]; then
        log "8 tentativi falliti. Pausa 30s e reset a xwayland_sw..."
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
bash -n "$RUN" && ok "run-kiosk.sh: sintassi OK." || warn "run-kiosk.sh: errore sintassi."

# Imposta profilo iniziale
mkdir -p "${APP_DIR}/config"
echo "xwayland_sw" > "${APP_DIR}/config/profile.conf"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 7 - kiosk-control
# =============================================================================
cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
case "${1:-help}" in
    start)
        rm -f /opt/kiosk/.stop
        # Avvia come farebbe l'auto-login: cage diretto
        su - kiosk -c "
            export XDG_RUNTIME_DIR=/run/user/\$(id -u)
            mkdir -p \$XDG_RUNTIME_DIR && chmod 700 \$XDG_RUNTIME_DIR
            export LIBGL_ALWAYS_SOFTWARE=1
            export WLR_RENDERER=pixman
            export WLR_NO_HARDWARE_CURSORS=1
            cage -d -- /opt/kiosk/run-kiosk.sh &
        "
        ;;
    stop)
        touch /opt/kiosk/.stop
        pkill cage 2>/dev/null || true
        pkill java 2>/dev/null || true
        ;;
    restart)
        $0 stop; sleep 2; $0 start
        ;;
    status)
        pgrep cage  >/dev/null && echo "cage:  IN ESECUZIONE" || echo "cage:  FERMO"
        pgrep java  >/dev/null && echo "java:  IN ESECUZIONE" || echo "java:  FERMO"
        echo "Profilo: $(cat /opt/kiosk/config/profile.conf 2>/dev/null || echo 'default')"
        echo "--- Ultimi log ---"
        tail -20 /opt/kiosk/logs/kiosk.log 2>/dev/null || echo "(nessun log)"
        ;;
    log)     tail -f /opt/kiosk/logs/kiosk.log ;;
    errlog)  tail -f /opt/kiosk/logs/kiosk-err.log ;;
    profile) cat /opt/kiosk/config/profile.conf 2>/dev/null || echo "xwayland_sw" ;;
    reset-profile)
        echo "xwayland_sw" > /opt/kiosk/config/profile.conf
        echo "Profilo resettato a xwayland_sw."
        ;;
    update)
        VER="${2:-v1.0.0}"
        URL="https://github.com/accaicedtea/ttetttos/releases/download/${VER}/demo-1.jar"
        echo "Download $URL..."
        if curl -fsSL "$URL" -o /tmp/kiosk-update.jar; then
            pkill java 2>/dev/null || true
            sleep 1
            cp /tmp/kiosk-update.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /tmp/kiosk-update.jar /opt/kiosk/.stop
            echo "Aggiornato a $VER. Riavvia con: kiosk-control restart"
        else
            echo "Download fallito."
        fi
        ;;
    help|*)
        echo "Uso: kiosk-control {start|stop|restart|status|log|errlog|profile|reset-profile|update [tag]}"
        ;;
esac
CTRLEOF
chmod +x "${APP_DIR}/kiosk-control.sh"
ln -sf "${APP_DIR}/kiosk-control.sh" /usr/local/bin/kiosk-control
ok "kiosk-control installato."

# =============================================================================
# FASE 8 - Auto-login TTY1 + .bash_profile (metodo che funziona!)
# =============================================================================
sep "FASE 8 - Auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF
ok "Auto-login TTY1 configurato."

# .bash_profile - cage lanciato DIRETTAMENTE qui (come nell'esempio che funzionava)
# L'utente ha gia il seat dal TTY, non serve dbus-run-session o seatd
cat > "${KIOSK_HOME}/.bash_profile" << 'BPEOF'
if [ "$(tty)" = "/dev/tty1" ]; then
    # Controlla flag di stop
    if [ -f /opt/kiosk/.stop ]; then
        rm -f /opt/kiosk/.stop
        echo "Kiosk fermato manualmente. Shell disponibile."
    else
        echo ""
        echo "+-------------------------------------------+"
        echo "|  Kiosk in avvio... Ctrl+C per il terminale |"
        echo "+-------------------------------------------+"
        echo ""

        # Setup ambiente
        export XDG_RUNTIME_DIR="/run/user/$(id -u)"
        mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
        export XCURSOR_THEME=blank-cursor
        export XCURSOR_SIZE=24

        # Avvia cage direttamente (utente TTY ha gia il seat)
        # cage -d avvia Xwayland su :1, run-kiosk.sh usa DISPLAY=:1
        exec cage -d -- /opt/kiosk/run-kiosk.sh
    fi
fi
BPEOF
chown "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bash_profile"
ok ".bash_profile configurato."

# Sudoers per kiosk-control da root
cat > /etc/sudoers.d/kiosk << 'SUDOEOF'
kiosk ALL=(ALL) NOPASSWD: /usr/local/bin/kiosk-control
SUDOEOF
chmod 440 /etc/sudoers.d/kiosk

# =============================================================================
# FASE 9 - Ottimizzazioni
# =============================================================================
sep "FASE 9 - Ottimizzazioni"

# Disabilita display manager (occupano il TTY)
for DM in gdm3 gdm lightdm sddm xdm; do
    if systemctl is-active "$DM" >/dev/null 2>&1 || \
       systemctl is-enabled "$DM" >/dev/null 2>&1; then
        log "Disabilito display manager: $DM"
        systemctl stop    "$DM" 2>/dev/null || true
        systemctl disable "$DM" 2>/dev/null || true
        ok "$DM disabilitato."
    fi
done

# Disabilita servizi inutili
for SVC in ModemManager bluetooth cups avahi-daemon \
           apt-daily.timer apt-daily-upgrade.timer; do
    systemctl disable "$SVC" 2>/dev/null || true
    systemctl mask    "$SVC" 2>/dev/null || true
done

# TTY extra
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done

# Default target
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

# Journal
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nCompress=yes\n' \
    > /etc/systemd/journald.conf.d/kiosk.conf

systemctl daemon-reload
ok "Ottimizzazioni completate."

# =============================================================================
# RIEPILOGO
# =============================================================================
sep "RIEPILOGO"

echo ""
JAR_OK=false
FX_OK=false
JAVA_OK=false
CAGE_OK=false

[ -f "${APP_DIR}/demo-1.jar" ]   && { ok "demo-1.jar: $(du -h "${APP_DIR}/demo-1.jar" | cut -f1)"; JAR_OK=true; }  || warn "demo-1.jar: MANCANTE"
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$LIB_N" -gt 0 ]               && ok "Librerie: $LIB_N JAR"         || warn "Librerie: nessuna"
FX_N=$(ls "${APP_DIR}/javafx-sdk/"*.jar 2>/dev/null | wc -l || echo 0)
[ "$FX_N" -gt 0 ]                && { ok "JavaFX SDK: $FX_N JAR"; FX_OK=true; }  || warn "JavaFX SDK: non trovato"
command -v java >/dev/null        && { ok "Java: $(java -version 2>&1 | head -1)"; JAVA_OK=true; } || warn "Java: non trovato"
command -v cage >/dev/null        && { ok "cage: trovato"; CAGE_OK=true; }         || warn "cage: NON TROVATO"
[ -x "${APP_DIR}/run-kiosk.sh" ] && ok "run-kiosk.sh: OK"             || warn "run-kiosk.sh: problema"
[ -f "${KIOSK_HOME}/.bash_profile" ] && ok ".bash_profile: OK"        || warn ".bash_profile: mancante"
ok "Profilo avvio: xwayland_sw (Xwayland + software rendering)"

echo ""
echo "+--------------------------------------------------+"
echo "|  SETUP COMPLETATO                                |"
echo "+--------------------------------------------------+"
echo "|                                                  |"
echo "|  Come funziona:                                  |"
echo "|   1. reboot                                      |"
echo "|   2. Auto-login su TTY1 come utente kiosk        |"
echo "|   3. .bash_profile avvia cage direttamente       |"
echo "|   4. cage avvia Xwayland su :1                   |"
echo "|   5. run-kiosk.sh avvia JavaFX con DISPLAY=:1    |"
echo "|                                                  |"
echo "|  Ctrl+C nei primi secondi per il terminale       |"
echo "|                                                  |"
echo "|  Diagnostica:                                    |"
echo "|    tail -f /opt/kiosk/logs/kiosk.log             |"
echo "|    tail -f /opt/kiosk/logs/kiosk-err.log         |"
echo "|    kiosk-control status                          |"
echo "+--------------------------------------------------+"
echo ""
echo "Riavvia con: reboot"
echo ""