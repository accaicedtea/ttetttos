#!/usr/bin/env bash
# =============================================================================
# setup-kiosk.sh - Installa il kiosk JavaFX
# Uso: curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash
#      sudo bash setup-kiosk.sh reset
#      sudo bash setup-kiosk.sh update
# =============================================================================

# NON usare set -e: vogliamo andare avanti anche se qualcosa fallisce
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
API_KEY="api_key_totem_1"

mkdir -p "$(dirname $LOG)" || true
touch "$LOG" || true

log()  { MSG="[$(date '+%H:%M:%S')] $*"; echo "$MSG"; echo "$MSG" >> "$LOG"; }
ok()   { MSG="[ OK ] $*"; echo "$MSG"; echo "$MSG" >> "$LOG"; }
warn() { MSG="[WARN] $*"; echo "$MSG"; echo "$MSG" >> "$LOG"; }
fail() { MSG="[FAIL] $*"; echo "$MSG"; echo "$MSG" >> "$LOG"; exit 1; }
sep()  { echo ""; echo "======================================================"; echo "  $*"; echo "======================================================"; }

log "Setup avviato $(date) - modalita: ${1:-install}"

# Root check
if [ "$(id -u)" != "0" ]; then
    fail "Eseguire come root: sudo bash $0"
fi

# Rileva distro
DISTRO="unknown"
if [ -f /etc/os-release ]; then
    . /etc/os-release
    DISTRO="${ID:-unknown}"
fi
log "Distro rilevata: $DISTRO"

# Funzione install pacchetti
install_pkg() {
    log "Installo pacchetti: $*"
    case "$DISTRO" in
        debian|ubuntu|raspbian|linuxmint|pop)
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@"
            ;;
        fedora|rhel|centos|almalinux|rocky)
            dnf install -y "$@"
            ;;
        arch|manjaro|endeavouros)
            pacman -S --noconfirm --needed "$@"
            ;;
        opensuse*|suse*)
            zypper install -y "$@"
            ;;
        *)
            log "Distro sconosciuta, provo con apt-get..."
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@" || \
            dnf install -y "$@" || \
            warn "Impossibile installare: $*"
            ;;
    esac
    RET=$?
    if [ $RET -eq 0 ]; then
        ok "Pacchetti installati: $*"
    else
        warn "Alcuni pacchetti non installati (codice: $RET) - continuo comunque"
    fi
}

# =============================================================================
# RESET
# =============================================================================
if [ "${1:-}" = "reset" ]; then
    sep "RESET COMPLETO"
    log "Fermo e rimuovo tutti i servizi kiosk..."

    for SVC in kiosk kiosk-logclean kiosk-nightly-restart kiosk-network-watchdog; do
        log "Fermo $SVC..."
        systemctl stop    "${SVC}.service" 2>/dev/null || true
        systemctl stop    "${SVC}.timer"   2>/dev/null || true
        systemctl disable "${SVC}.service" 2>/dev/null || true
        systemctl disable "${SVC}.timer"   2>/dev/null || true
        rm -f "/etc/systemd/system/${SVC}.service"
        rm -f "/etc/systemd/system/${SVC}.timer"
    done

    systemctl daemon-reload

    if id "$KIOSK_USER" >/dev/null 2>&1; then
        log "Termino processi utente $KIOSK_USER..."
        pkill -u "$KIOSK_USER" 2>/dev/null || true
        sleep 2
        log "Rimuovo utente $KIOSK_USER..."
        userdel -r "$KIOSK_USER" 2>/dev/null || true
        ok "Utente rimosso."
    fi

    log "Rimuovo /opt/kiosk..."
    rm -rf "$APP_DIR" || true

    log "Rimuovo configurazioni..."
    rm -f /etc/sudoers.d/kiosk || true
    rm -f /etc/udev/rules.d/99-kiosk.rules || true
    rm -f /etc/sysctl.d/99-kiosk.conf || true
    rm -rf /etc/systemd/system/getty@tty1.service.d/ || true
    rm -f /etc/systemd/system.conf.d/kiosk.conf || true
    rm -rf /usr/share/icons/blank-cursor || true

    log "Riabilito TTY..."
    for i in 2 3 4 5 6; do
        systemctl unmask "getty@tty${i}.service" 2>/dev/null || true
    done

    systemctl set-default multi-user.target 2>/dev/null || true
    systemctl daemon-reload
    ok "Reset completato."
    echo ""
    log "Riavvio installazione da zero..."
    echo ""
fi

# =============================================================================
# UPDATE JAR
# =============================================================================
if [ "${1:-}" = "update" ]; then
    sep "Aggiornamento JAR"
    log "Download nuovo JAR..."
    systemctl stop kiosk.service 2>/dev/null || true

    if curl -fsSL --retry 3 "$JAR_URL" -o /tmp/kiosk-new.jar; then
        cp /tmp/kiosk-new.jar "${APP_DIR}/demo-1.jar"
        chown "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/demo-1.jar"
        rm -f "${APP_DIR}/.stop"
        rm -f /tmp/kiosk-new.jar
        systemctl start kiosk.service
        ok "Aggiornamento completato."
    else
        fail "Download JAR fallito."
    fi
    exit 0
fi

# =============================================================================
# INIZIO INSTALLAZIONE
# =============================================================================
echo ""
echo "+-----------------------------------------------------+"
echo "|  KIOSK SETUP - $(date '+%Y-%m-%d %H:%M')              |"
echo "|  Distro: $DISTRO                                |"
echo "+-----------------------------------------------------+"
echo ""

# =============================================================================
# FASE 1 - Aggiornamento pacchetti
# =============================================================================
sep "FASE 1 - Aggiornamento lista pacchetti"
log "Aggiorno lista pacchetti..."
case "$DISTRO" in
    debian|ubuntu|raspbian|linuxmint|pop)
        DEBIAN_FRONTEND=noninteractive apt-get update -qq
        ok "apt-get update completato."
        ;;
    fedora|rhel|centos|almalinux|rocky)
        dnf check-update -y 2>/dev/null || true
        ok "dnf check-update completato."
        ;;
    arch|manjaro|endeavouros)
        pacman -Sy --noconfirm 2>/dev/null || true
        ok "pacman -Sy completato."
        ;;
    *)
        DEBIAN_FRONTEND=noninteractive apt-get update -qq 2>/dev/null || true
        warn "Update pacchetti: distro non riconosciuta, tentato con apt-get."
        ;;
esac

# =============================================================================
# FASE 2 - Installazione dipendenze base
# =============================================================================
sep "FASE 2 - Installazione dipendenze"

log "Installo strumenti base (curl, wget, unzip, ca-certificates)..."
install_pkg curl wget unzip ca-certificates

log "Installo Cage (compositor Wayland minimale)..."
install_pkg cage

log "Installo seatd (gestore seat - necessario per cage)..."
install_pkg seatd
# Abilita e avvia seatd subito
systemctl enable seatd 2>/dev/null || true
systemctl start  seatd 2>/dev/null || true

log "Installo Xwayland..."
install_pkg xwayland

log "Installo dbus..."
install_pkg dbus dbus-user-session

log "Installo font..."
install_pkg fonts-dejavu-core || install_pkg dejavu-fonts || warn "Font non installati."
install_pkg fonts-noto-color-emoji 2>/dev/null || true

log "Installo strumenti sistema..."
install_pkg pciutils util-linux procps iproute2 lm-sensors

log "Installo Java..."
JAVA_INSTALLED=false
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky)
        install_pkg java-21-openjdk-headless && JAVA_INSTALLED=true || \
        install_pkg java-17-openjdk-headless && JAVA_INSTALLED=true || true
        ;;
    arch|manjaro|endeavouros)
        install_pkg jre21-openjdk-headless && JAVA_INSTALLED=true || \
        install_pkg jre17-openjdk-headless && JAVA_INSTALLED=true || true
        ;;
    opensuse*|suse*)
        install_pkg java-21-openjdk-headless && JAVA_INSTALLED=true || \
        install_pkg java-17-openjdk-headless && JAVA_INSTALLED=true || true
        ;;
    *)
        # Debian/Ubuntu: prima prova default-jre, poi versioni specifiche
        install_pkg default-jre && JAVA_INSTALLED=true || \
        install_pkg openjdk-21-jre-headless && JAVA_INSTALLED=true || \
        install_pkg openjdk-17-jre-headless && JAVA_INSTALLED=true || true
        ;;
esac

if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    ok "Java disponibile: $JAVA_VER"
else
    warn "Java non trovato nel PATH dopo installazione."
    warn "Potrebbe essere necessario impostare JAVA_HOME manualmente."
fi

log "Installo Mesa/OpenGL (software rendering)..."
case "$DISTRO" in
    fedora|rhel|centos|almalinux|rocky)
        install_pkg mesa-dri-drivers mesa-libGL mesa-libEGL || true
        ;;
    arch|manjaro|endeavouros)
        install_pkg mesa || true
        ;;
    opensuse*|suse*)
        install_pkg Mesa-dri Mesa-libGL1 || true
        ;;
    *)
        install_pkg libgl1-mesa-dri libgl1-mesa-glx libegl1-mesa libgles2-mesa \
                    libgl1-mesa-swrast || true
        ;;
esac

log "Installo openjfx di sistema (fallback JavaFX)..."
install_pkg openjfx 2>/dev/null || warn "openjfx non disponibile nei repo - usero JavaFX SDK."

log "Installo unclutter (nasconde il cursore)..."
install_pkg unclutter-xfixes 2>/dev/null || true

ok "Tutte le dipendenze installate."

# =============================================================================
# FASE 3 - Creazione utente kiosk
# =============================================================================
sep "FASE 3 - Utente kiosk"

if id "$KIOSK_USER" >/dev/null 2>&1; then
    log "Utente '$KIOSK_USER' gia esiste."
    usermod -aG video,input,render,audio "$KIOSK_USER" 2>/dev/null || true
    ok "Gruppi aggiornati."
else
    log "Creo utente '$KIOSK_USER'..."
    useradd -m -s /bin/bash -G video,input,render,audio "$KIOSK_USER" 2>/dev/null || \
    useradd -m -s /bin/bash "$KIOSK_USER"
    passwd -d "$KIOSK_USER"
    ok "Utente '$KIOSK_USER' creato."
fi

KIOSK_HOME=$(eval echo ~"$KIOSK_USER")
KIOSK_UID=$(id -u "$KIOSK_USER")
log "Home: $KIOSK_HOME | UID: $KIOSK_UID"

# =============================================================================
# FASE 4 - Download applicazione da GitHub
# =============================================================================
sep "FASE 4 - Download app da GitHub Releases"

mkdir -p "${APP_DIR}/lib"
mkdir -p "${APP_DIR}/logs"
mkdir -p "${APP_DIR}/config"

log "Scarico demo-1.jar da: $JAR_URL"
DOWNLOAD_OK=false
for ATTEMPT in 1 2 3; do
    log "Tentativo $ATTEMPT/3..."
    if curl -fsSL --retry 2 --connect-timeout 30 --max-time 120 \
            "$JAR_URL" -o "${APP_DIR}/demo-1.jar"; then
        JAR_SIZE=$(du -h "${APP_DIR}/demo-1.jar" 2>/dev/null | cut -f1)
        ok "demo-1.jar scaricato: $JAR_SIZE"
        DOWNLOAD_OK=true
        break
    else
        warn "Tentativo $ATTEMPT fallito. Attendo 5s..."
        sleep 5
    fi
done
if [ "$DOWNLOAD_OK" = "false" ]; then
    fail "Impossibile scaricare demo-1.jar dopo 3 tentativi. Verifica la connessione internet."
fi

log "Scarico librerie da: $LIB_URL"
LIBS_OK=false
for ATTEMPT in 1 2 3; do
    log "Tentativo $ATTEMPT/3..."
    rm -f /tmp/kiosk-lib.tar.gz
    if curl -fsSL --retry 2 --connect-timeout 30 --max-time 120 \
            "$LIB_URL" -o /tmp/kiosk-lib.tar.gz; then
        log "Estraggo librerie..."
        tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" 2>/dev/null || \
        tar -xzf /tmp/kiosk-lib.tar.gz -C "${APP_DIR}/lib/" --strip-components=1 2>/dev/null || true
        rm -f /tmp/kiosk-lib.tar.gz
        LIB_COUNT=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "Librerie estratte: $LIB_COUNT JAR in ${APP_DIR}/lib/"
        LIBS_OK=true
        break
    else
        warn "Tentativo $ATTEMPT fallito. Attendo 5s..."
        sleep 5
    fi
done
if [ "$LIBS_OK" = "false" ]; then
    warn "Download lib.tar.gz fallito - continuo senza (l'app potrebbe non avviarsi)."
fi

# =============================================================================
# FASE 5 - Installazione JavaFX SDK
# =============================================================================
sep "FASE 5 - JavaFX SDK"

JAVAFX_DIR="${APP_DIR}/javafx-sdk"
FX_INSTALLED=false

# Controlla se gia installato
if [ -d "$JAVAFX_DIR" ]; then
    FX_COUNT=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
    if [ "$FX_COUNT" -gt 0 ]; then
        ok "JavaFX SDK gia presente: $FX_COUNT JAR. Salto il download."
        FX_INSTALLED=true
    fi
fi

if [ "$FX_INSTALLED" = "false" ]; then
    # Prova 1: usa openjfx di sistema
    log "Cerco JavaFX nel sistema..."
    FX_JAR=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" 2>/dev/null | head -1 || echo "")
    if [ -n "$FX_JAR" ]; then
        FX_SYS_DIR=$(dirname "$FX_JAR")
        log "Trovato JavaFX di sistema in: $FX_SYS_DIR"
        mkdir -p "$JAVAFX_DIR"
        cp "${FX_SYS_DIR}/"*.jar "$JAVAFX_DIR/" 2>/dev/null || true
        find "$FX_SYS_DIR" -name "*.so" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
        FX_COUNT=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
        ok "JavaFX di sistema copiato: $FX_COUNT JAR"
        FX_INSTALLED=true
    fi
fi

if [ "$FX_INSTALLED" = "false" ]; then
    # Prova 2: scarica JavaFX SDK da Gluon
    log "JavaFX non trovato nel sistema. Scarico JavaFX SDK ${FX_VER} da Gluon..."
    log "URL: $FX_URL"

    # Controlla spazio disponibile in /tmp
    FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 999)
    log "Spazio libero in /tmp: ${FREE_TMP}MB"

    if [ "$FREE_TMP" -lt 200 ] 2>/dev/null; then
        log "Poco spazio in /tmp. Pulizia..."
        apt-get clean 2>/dev/null || true
        rm -rf /tmp/javafx-* /tmp/*.zip /tmp/fxext 2>/dev/null || true
        FREE_TMP=$(df /tmp --output=avail -m 2>/dev/null | tail -1 | tr -d ' ' || echo 0)
        log "Spazio /tmp dopo pulizia: ${FREE_TMP}MB"
    fi

    # Download con progress
    log "Download in corso (circa 80MB)..."
    DOWNLOAD_FX=false
    for ATTEMPT in 1 2; do
        rm -f /tmp/javafx.zip
        if curl -fSL --retry 1 --connect-timeout 30 --max-time 300 \
                "$FX_URL" -o /tmp/javafx.zip 2>&1 | tail -5; then
            FX_ZIP_SIZE=$(du -h /tmp/javafx.zip 2>/dev/null | cut -f1 || echo "?")
            ok "Download JavaFX completato: $FX_ZIP_SIZE"
            DOWNLOAD_FX=true
            break
        else
            warn "Download tentativo $ATTEMPT fallito."
            rm -f /tmp/javafx.zip
            sleep 3
        fi
    done

    if [ "$DOWNLOAD_FX" = "true" ]; then
        log "Estraggo JavaFX SDK (escludo libjfxwebkit.so ~60MB)..."
        mkdir -p /tmp/fxext
        rm -rf /tmp/fxext/*

        # Estrazione - unzip ritorna 1 su warning, non e un errore
        unzip -q /tmp/javafx.zip \
            -d /tmp/fxext/ \
            -x "*/libjfxwebkit.so" \
            -x "*/libgstreamer-lite.so" \
            -x "*/src.zip" 2>/dev/null
        true  # ignora exit code unzip

        log "Cerco JAR estratti..."
        FX_JARS=$(find /tmp/fxext -name "*.jar" 2>/dev/null | wc -l || echo 0)
        log "Trovati $FX_JARS JAR in /tmp/fxext"

        if [ "$FX_JARS" -gt 0 ]; then
            mkdir -p "$JAVAFX_DIR"
            find /tmp/fxext -name "*.jar" -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
            find /tmp/fxext -name "*.so" \
                ! -name "libjfxwebkit.so" \
                ! -name "libgstreamer-lite.so" \
                -exec cp {} "$JAVAFX_DIR/" \; 2>/dev/null || true
            rm -f "${JAVAFX_DIR}/libjfxwebkit.so" 2>/dev/null || true
            rm -f "${JAVAFX_DIR}/libgstreamer-lite.so" 2>/dev/null || true

            FX_FINAL=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
            ok "JavaFX SDK installato: $FX_FINAL JAR in $JAVAFX_DIR"
            FX_INSTALLED=true
        else
            warn "Nessun JAR trovato dopo estrazione. Controllo spazio disco..."
            df -h /tmp/ || true
        fi

        # Pulizia sempre
        log "Pulizia file temporanei..."
        rm -f /tmp/javafx.zip
        rm -rf /tmp/fxext
    else
        warn "Download JavaFX SDK fallito dopo 2 tentativi."
        warn "L'app potrebbe non avviarsi se non c'e JavaFX nel sistema."
        warn "Soluzione manuale: apt install openjfx"
    fi
fi

# Determina FX_PATH finale
if [ -d "$JAVAFX_DIR" ]; then
    FX_COUNT=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
    if [ "$FX_COUNT" -gt 0 ]; then
        FX_PATH="$JAVAFX_DIR"
        ok "FX_PATH impostato a: $FX_PATH ($FX_COUNT JAR)"
    else
        FX_PATH="${APP_DIR}/lib"
        warn "JavaFX SDK vuoto, uso ${APP_DIR}/lib come fallback"
    fi
else
    FX_PATH="${APP_DIR}/lib"
    warn "JavaFX SDK non presente, uso ${APP_DIR}/lib come fallback"
fi

chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"

# =============================================================================
# FASE 5b - Test rendering JavaFX (trova il profilo che funziona)
# =============================================================================
sep "FASE 5b - Test rendering JavaFX"

log "Cerco il profilo di rendering che funziona su questo sistema..."
log "Questo evita lo schermo nero al primo avvio."

WORKING_PROFILE="default"
JAVA_BIN=$(command -v java 2>/dev/null || echo "")

if [ -z "$JAVA_BIN" ]; then
    warn "Java non trovato - salto il test rendering."
else
    # Crea un mini programma di test JavaFX
    TEST_DIR=$(mktemp -d /tmp/fx-test.XXXXXX)
    mkdir -p "${TEST_DIR}"

    # Determina FX_PATH per il test
    if [ -d "${JAVAFX_DIR}" ] && ls "${JAVAFX_DIR}/"*.jar >/dev/null 2>&1; then
        TEST_FX="${JAVAFX_DIR}"
    else
        FX_JAR=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" 2>/dev/null | head -1 || echo "")
        if [ -n "$FX_JAR" ]; then
            TEST_FX=$(dirname "$FX_JAR")
        else
            TEST_FX="${APP_DIR}/lib"
        fi
    fi

    # Classpath test: includi tutte le lib
    TEST_CP="${APP_DIR}/demo-1.jar"
    for J in "${APP_DIR}/lib/"*.jar; do
        [ -f "$J" ] && TEST_CP="${TEST_CP}:${J}"
    done

    log "FX_PATH per test: $TEST_FX"
    log "Test rendering in corso..."

    # Funzione che testa un profilo e ritorna 0 se JavaFX parte
    test_profile() {
        PNAME="$1"
        log "  -> Provo profilo: $PNAME"

        # Setup environment per questo profilo
        export LIBGL_ALWAYS_SOFTWARE=1
        export WLR_NO_HARDWARE_CURSORS=1
        export WLR_RENDERER_ALLOW_SOFTWARE=1

        case "$PNAME" in
            default)
                export WLR_RENDERER=pixman
                export MESA_GL_VERSION_OVERRIDE=3.3
                export GALLIUM_DRIVER=softpipe
                JFXTEST="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
                ;;
            pixman)
                export WLR_RENDERER=pixman
                export MESA_GL_VERSION_OVERRIDE=4.5
                export GALLIUM_DRIVER=softpipe
                JFXTEST="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
                ;;
            llvmpipe)
                export WLR_RENDERER=pixman
                export GALLIUM_DRIVER=llvmpipe
                JFXTEST="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
                ;;
            gtk2)
                export WLR_RENDERER=pixman
                JFXTEST="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2"
                ;;
        esac

        # Test: avvia Java con JavaFX per 3 secondi e controlla se parte
        # Usa timeout per non bloccarsi
        TESTOUT=$(timeout 8 java             -Xms32m -Xmx128m             --module-path "$TEST_FX"             --add-modules javafx.graphics,javafx.controls             $JFXTEST             -Djavafx.headless=false             -Djava.awt.headless=false             -Dfile.encoding=UTF-8             -cp "$TEST_CP"             com.example.App 2>&1 || true)

        # Controlla se JavaFX si e avviato (cerca segnali positivi nei log)
        if echo "$TESTOUT" | grep -qi "Nav.*->|SplashController|Login|avvio|started|toolkit|stage"; then
            log "  -> Profilo $PNAME: FUNZIONA (JavaFX si e avviato)"
            echo "$TESTOUT" | head -5 >> "$LOG"
            return 0
        fi

        # Controlla se almeno JavaFX si e inizializzato senza errori critici
        if echo "$TESTOUT" | grep -qi "prism\|quantum\|glass" &&            ! echo "$TESTOUT" | grep -qi "Exception\|Error\|failed\|not found"; then
            log "  -> Profilo $PNAME: PARZIALMENTE OK (JavaFX avviato ma uscito subito)"
            return 0
        fi

        # Errori critici = profilo non funziona
        ERR_LINE=$(echo "$TESTOUT" | grep -i "Exception\|Error\|not found\|failed" | head -2)
        log "  -> Profilo $PNAME: FALLITO - $ERR_LINE"
        return 1
    }

    # Prova i profili in ordine
    for P in default pixman llvmpipe gtk2; do
        if test_profile "$P"; then
            WORKING_PROFILE="$P"
            ok "Profilo funzionante trovato: $WORKING_PROFILE"
            break
        fi
        sleep 1
    done

    rm -rf "${TEST_DIR}" 2>/dev/null || true

    if [ "$WORKING_PROFILE" = "default" ]; then
        warn "Nessun profilo testato con successo - uso 'default' come base."
        warn "Lo script di avvio provera automaticamente tutti i profili."
    fi
fi

# Salva il profilo trovato
mkdir -p "${APP_DIR}/config"
echo "$WORKING_PROFILE" > "${APP_DIR}/config/profile.conf"
chown -R "${KIOSK_USER}:${KIOSK_USER}" "${APP_DIR}/config"
ok "Profilo salvato: $WORKING_PROFILE"

# =============================================================================
# FASE 5c - Test Cage + seatd
# =============================================================================
sep "FASE 5c - Test Cage e seatd"

# Verifica seatd attivo
if systemctl is-active seatd >/dev/null 2>&1; then
    ok "seatd: attivo"
else
    warn "seatd non attivo - avvio..."
    systemctl start seatd 2>/dev/null || true
    sleep 2
    if systemctl is-active seatd >/dev/null 2>&1; then
        ok "seatd: avviato con successo"
    else
        warn "seatd non parte - installo libseat..."
        install_pkg libseat-dev 2>/dev/null || install_pkg libseat1 2>/dev/null || true
        systemctl start seatd 2>/dev/null || true
    fi
fi

# Verifica gruppo seat
if id "$KIOSK_USER" 2>/dev/null | grep -q "seat"; then
    ok "Utente $KIOSK_USER nel gruppo seat: OK"
else
    warn "Aggiungo $KIOSK_USER al gruppo seat..."
    groupadd seat 2>/dev/null || true
    usermod -aG seat "$KIOSK_USER"
    ok "Aggiunto al gruppo seat."
fi

# Verifica cage funziona
if command -v cage >/dev/null 2>&1; then
    ok "cage trovato: $(cage --version 2>/dev/null || echo 'ok')"

    # Test cage con un comando dummy (ls) per vedere se parte
    log "Test cage con comando dummy..."
    CAGE_TEST=$(timeout 5 su - "$KIOSK_USER" -c "
        export XDG_RUNTIME_DIR=/run/user/$(id -u $KIOSK_USER)
        export LIBGL_ALWAYS_SOFTWARE=1
        export WLR_RENDERER=pixman
        export WLR_NO_HARDWARE_CURSORS=1
        dbus-run-session cage -- /bin/true 2>&1
    " 2>&1 || true)

    if echo "$CAGE_TEST" | grep -qi "DRM\|seat\|permission\|tty"; then
        warn "Cage ha problemi di permessi TTY/DRM:"
        echo "$CAGE_TEST" | head -3
        warn "Assicurati che il sistema non abbia un display manager attivo."
        # Ferma display manager se presente
        for DM in gdm3 gdm lightdm sddm xdm; do
            if systemctl is-active "$DM" >/dev/null 2>&1; then
                warn "Display manager attivo: $DM - fermo..."
                systemctl stop    "$DM" 2>/dev/null || true
                systemctl disable "$DM" 2>/dev/null || true
                ok "$DM fermato."
            fi
        done
    else
        ok "Cage test: OK"
    fi
else
    warn "cage non trovato - tentativo installazione..."
    install_pkg cage
fi

# Controlla se c'e un display manager che occupa il TTY
for DM in gdm3 gdm lightdm sddm xdm; do
    if systemctl is-active "$DM" >/dev/null 2>&1; then
        warn "Display manager '$DM' attivo - lo fermo (occupa il TTY)..."
        systemctl stop    "$DM" 2>/dev/null || true
        systemctl disable "$DM" 2>/dev/null || true
        ok "$DM disabilitato."
    fi
done

# =============================================================================
# FASE 5d - Verifica librerie native JavaFX
# =============================================================================
sep "FASE 5d - Verifica librerie native"

log "Controllo librerie native JavaFX..."

# Controlla libGL
if ldconfig -p 2>/dev/null | grep -q "libGL.so"; then
    ok "libGL.so: trovata"
else
    warn "libGL.so non trovata - installo Mesa..."
    install_pkg libgl1-mesa-dri libgl1-mesa-glx 2>/dev/null ||     install_pkg mesa-libGL 2>/dev/null || true
fi

# Controlla libEGL
if ldconfig -p 2>/dev/null | grep -q "libEGL.so"; then
    ok "libEGL.so: trovata"
else
    warn "libEGL.so non trovata - installo..."
    install_pkg libegl1-mesa 2>/dev/null || install_pkg mesa-libEGL 2>/dev/null || true
fi

# Controlla libgbm (necessario per Wayland/DRM)
if ldconfig -p 2>/dev/null | grep -q "libgbm.so"; then
    ok "libgbm.so: trovata"
else
    warn "libgbm.so non trovata - installo..."
    install_pkg libgbm1 2>/dev/null || install_pkg mesa-libgbm 2>/dev/null || true
fi

# Controlla libdrm
if ldconfig -p 2>/dev/null | grep -q "libdrm.so"; then
    ok "libdrm.so: trovata"
else
    warn "libdrm.so non trovata - installo..."
    install_pkg libdrm2 2>/dev/null || install_pkg libdrm 2>/dev/null || true
fi

# Controlla GTK3
if ldconfig -p 2>/dev/null | grep -q "libgtk-3.so"; then
    ok "libgtk-3.so (GTK3): trovata"
else
    warn "GTK3 non trovato - installo..."
    install_pkg libgtk-3-0 2>/dev/null || install_pkg gtk3 2>/dev/null || true
fi

# Aggiorna cache librerie
log "Aggiorno cache librerie (ldconfig)..."
ldconfig 2>/dev/null || true
ok "Cache librerie aggiornata."

# =============================================================================
# FASE 5e - Imposta variabili ambiente nel servizio in base al test
# =============================================================================
sep "FASE 5e - Configurazione ambiente basata sui test"

log "Profilo di rendering selezionato: $WORKING_PROFILE"

# Imposta JFXOPTS in base al profilo trovato
case "$WORKING_PROFILE" in
    pixman)
        FINAL_JFXOPTS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
        FINAL_GALLIUM="softpipe"
        FINAL_MESA="4.5"
        ;;
    llvmpipe)
        FINAL_JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
        FINAL_GALLIUM="llvmpipe"
        FINAL_MESA="3.3"
        ;;
    gtk2)
        FINAL_JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2"
        FINAL_GALLIUM="softpipe"
        FINAL_MESA="3.3"
        ;;
    *)
        FINAL_JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
        FINAL_GALLIUM="softpipe"
        FINAL_MESA="3.3"
        ;;
esac

log "JFXOPTS: $FINAL_JFXOPTS"
log "GALLIUM: $FINAL_GALLIUM"
log "MESA:    $FINAL_MESA"

# Aggiorna variabili ambiente nel service file (verra scritto nella FASE 9)
# Salva per uso successivo
SAVED_JFXOPTS="$FINAL_JFXOPTS"
SAVED_GALLIUM="$FINAL_GALLIUM"
SAVED_MESA="$FINAL_MESA"
sep "FASE 6 - Script di avvio (self-healing)"

RUN_SCRIPT="${APP_DIR}/run-kiosk.sh"
log "Creo $RUN_SCRIPT..."

# Scrivi header
printf '#!/usr/bin/env bash\n' > "$RUN_SCRIPT"
printf '# run-kiosk.sh - Launcher auto-healing JavaFX\n' >> "$RUN_SCRIPT"
printf 'APP_DIR="%s"\n' "$APP_DIR" >> "$RUN_SCRIPT"
printf 'API_KEY="%s"\n' "$API_KEY" >> "$RUN_SCRIPT"
printf 'KIOSK_UID="%s"\n' "$KIOSK_UID" >> "$RUN_SCRIPT"
printf 'FX_PATH_DEFAULT="%s"\n' "$FX_PATH" >> "$RUN_SCRIPT"

# Scrivi il resto dello script
cat >> "$RUN_SCRIPT" << 'RUNEOF'

LOG="${APP_DIR}/logs/kiosk.log"
ERR_LOG="${APP_DIR}/logs/kiosk-err.log"
PROFILE_FILE="${APP_DIR}/config/profile.conf"

mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"

ts() { date '+%H:%M:%S'; }
log() { echo "[$(ts)] $*" | tee -a "$LOG"; }

# Carica profilo salvato
PROFILE="default"
if [ -f "$PROFILE_FILE" ]; then
    SAVED=$(cat "$PROFILE_FILE" 2>/dev/null | tr -d '[:space:]')
    [ -n "$SAVED" ] && PROFILE="$SAVED"
fi

# Trova FX_PATH
find_fx() {
    # 1. JavaFX SDK installato
    if [ -d "${APP_DIR}/javafx-sdk" ]; then
        CNT=$(ls "${APP_DIR}/javafx-sdk/"*.jar 2>/dev/null | wc -l)
        [ "$CNT" -gt 0 ] && echo "${APP_DIR}/javafx-sdk" && return
    fi
    # 2. Variabile default dal setup
    if [ -n "$FX_PATH_DEFAULT" ] && [ -d "$FX_PATH_DEFAULT" ]; then
        echo "$FX_PATH_DEFAULT" && return
    fi
    # 3. openjfx di sistema
    FX_JAR=$(find /usr/share/java /usr/lib/jvm -name "javafx.controls.jar" 2>/dev/null | head -1)
    if [ -n "$FX_JAR" ]; then
        dirname "$FX_JAR" && return
    fi
    # 4. lib/ come ultimo fallback
    echo "${APP_DIR}/lib"
}

# Costruisce classpath
build_cp() {
    CP="${APP_DIR}/demo-1.jar"
    for JAR in "${APP_DIR}/lib/"*.jar; do
        [ -f "$JAR" ] && CP="${CP}:${JAR}"
    done
    echo "$CP"
}

# Analizza tipo di errore dall'output
detect_error() {
    OUT="$1"
    echo "$OUT" | grep -qi "Module javafx.*not found\|FindException\|boot layer" && echo "no_javafx" && return
    echo "$OUT" | grep -qi "Unable to open DISPLAY\|no display\|GtkApplication" && echo "no_display" && return
    echo "$OUT" | grep -qi "dbus\|connection to the bus" && echo "no_dbus" && return
    echo "$OUT" | grep -qi "libGL\|MESA\|prism.*error\|es2pipe" && echo "opengl" && return
    echo "$OUT" | grep -qi "OutOfMemoryError\|heap space" && echo "oom" && return
    echo "$OUT" | grep -qi "cage\|compositor\|wayland" && echo "cage" && return
    echo "unknown"
}

# Profilo -> variabili ambiente + opzioni Java
setup_profile() {
    P="$1"
    export LIBGL_ALWAYS_SOFTWARE=1
    export WLR_NO_HARDWARE_CURSORS=1
    export WLR_RENDERER_ALLOW_SOFTWARE=1
    export TOTEM_API_KEY="$API_KEY"
    case "$P" in
        default)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        pixman)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=4.5
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        llvmpipe)
            export WLR_RENDERER=pixman
            export GALLIUM_DRIVER=llvmpipe
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        gtk2)
            export WLR_RENDERER=pixman
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=2"
            ;;
        xwayland)
            export DISPLAY="${DISPLAY:-:0}"
            unset WAYLAND_DISPLAY 2>/dev/null || true
            JFXOPTS="-Dprism.order=sw -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
        *)
            export WLR_RENDERER=pixman
            export MESA_GL_VERSION_OVERRIDE=3.3
            export GALLIUM_DRIVER=softpipe
            JFXOPTS="-Dprism.order=sw -Dprism.forceGPU=false -Dglass.platform=gtk -Djdk.gtk.version=3"
            ;;
    esac
}

# Prossimo profilo da provare
next_profile() {
    CURR="$1"
    ERR="$2"
    case "$ERR" in
        no_javafx)
            log "Provo a installare openjfx..."
            apt-get install -y openjfx 2>/dev/null || true
            echo "default"
            ;;
        no_display|cage)
            case "$CURR" in
                default) echo "pixman" ;;
                pixman)  echo "xwayland" ;;
                *)       echo "default" ;;
            esac
            ;;
        opengl)
            case "$CURR" in
                default) echo "pixman" ;;
                pixman)  echo "llvmpipe" ;;
                llvmpipe) echo "gtk2" ;;
                *)       echo "default" ;;
            esac
            ;;
        oom)
            export JAVA_MEM="-Xms128m -Xmx512m"
            echo "$CURR"
            ;;
        *)
            case "$CURR" in
                default)  echo "pixman" ;;
                pixman)   echo "llvmpipe" ;;
                llvmpipe) echo "gtk2" ;;
                gtk2)     echo "xwayland" ;;
                *)        echo "default" ;;
            esac
            ;;
    esac
}

JAVA_MEM="-Xms64m -Xmx256m"
ATTEMPT=0
CURRENT="$PROFILE"

log "======================================="
log "Avvio kiosk"
log "Profilo: $CURRENT"
FX=$(find_fx)
log "FX_PATH: $FX"
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
log "Lib JAR: $LIB_N"
log "======================================="

while true; do
    ATTEMPT=$((ATTEMPT + 1))
    setup_profile "$CURRENT"

    FX=$(find_fx)
    CP=$(build_cp)

    log "--- Tentativo $ATTEMPT | Profilo: $CURRENT | FX: $FX ---"
    log "Avvio Java..."

    TMPOUT=$(mktemp /tmp/kiosk-out.XXXXXX 2>/dev/null || echo /tmp/kiosk-out-$$)

    java \
        $JAVA_MEM \
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
        com.example.App > "$TMPOUT" 2>&1
    RC=$?

    cat "$TMPOUT" >> "$ERR_LOG"
    OUT=$(cat "$TMPOUT" 2>/dev/null || echo "")
    rm -f "$TMPOUT"

    log "Java terminato con codice: $RC"

    # Uscita normale
    if [ $RC -eq 0 ]; then
        log "Uscita normale (codice 0)."
        exit 0
    fi

    # Analisi errore
    ERRTYPE=$(detect_error "$OUT")
    log "Tipo errore: $ERRTYPE"

    # Salva il profilo funzionante se era partito bene
    if echo "$OUT" | grep -q "Nav.*->"; then
        log "L'app era partita (navigazione rilevata). Profilo '$CURRENT' funziona."
        echo "$CURRENT" > "$PROFILE_FILE"
    fi

    # Reset dopo troppi fallimenti
    if [ $ATTEMPT -ge 8 ]; then
        log "8 tentativi falliti. Reset a default e pausa 30s..."
        echo "default" > "$PROFILE_FILE"
        CURRENT="default"
        ATTEMPT=0
        sleep 30
        continue
    fi

    # Calcola prossimo profilo
    NEXT=$(next_profile "$CURRENT" "$ERRTYPE")
    log "Prossimo profilo: $NEXT"

    if [ "$NEXT" != "$CURRENT" ]; then
        echo "$NEXT" > "$PROFILE_FILE"
        CURRENT="$NEXT"
    fi

    WAIT=$((ATTEMPT * 3))
    [ $WAIT -gt 15 ] && WAIT=15
    log "Attendo ${WAIT}s prima del prossimo tentativo..."
    sleep $WAIT
done
RUNEOF

chmod +x "$RUN_SCRIPT"

# Verifica sintassi
log "Verifico sintassi di run-kiosk.sh..."
if bash -n "$RUN_SCRIPT"; then
    ok "run-kiosk.sh: sintassi OK"
else
    warn "run-kiosk.sh ha errori di sintassi - verifico il contenuto..."
    bash -n "$RUN_SCRIPT" 2>&1 | head -20
fi

# =============================================================================
# FASE 7 - kiosk-control
# =============================================================================
sep "FASE 7 - Script kiosk-control"

cat > "${APP_DIR}/kiosk-control.sh" << 'CTRLEOF'
#!/usr/bin/env bash
case "${1:-help}" in
    start)          systemctl start kiosk.service ;;
    stop)           touch /opt/kiosk/.stop; systemctl stop kiosk.service ;;
    restart)        rm -f /opt/kiosk/.stop; systemctl restart kiosk.service ;;
    status)         systemctl status kiosk.service; echo ""; tail -30 /opt/kiosk/logs/kiosk.log ;;
    log)            journalctl -u kiosk.service -f ;;
    errlog)         tail -f /opt/kiosk/logs/kiosk-err.log ;;
    profile)        cat /opt/kiosk/config/profile.conf 2>/dev/null || echo "default" ;;
    reset-profile)  rm -f /opt/kiosk/config/profile.conf; echo "Profilo resettato a default." ;;
    update)
        VER="${2:-v1.0.0}"
        URL="https://github.com/accaicedtea/ttetttos/releases/download/${VER}/demo-1.jar"
        echo "Download $URL..."
        if curl -fsSL "$URL" -o /tmp/kiosk-update.jar; then
            systemctl stop kiosk.service 2>/dev/null || true
            cp /tmp/kiosk-update.jar /opt/kiosk/demo-1.jar
            chown kiosk:kiosk /opt/kiosk/demo-1.jar
            rm -f /opt/kiosk/.stop /tmp/kiosk-update.jar
            systemctl start kiosk.service
            echo "Aggiornato a $VER."
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
ok "kiosk-control installato in /usr/local/bin/kiosk-control"

# =============================================================================
# FASE 8 - Cursore trasparente
# =============================================================================
sep "FASE 8 - Cursore trasparente"

mkdir -p /usr/share/icons/blank-cursor/cursors

python3 << 'PYEOF'
import struct, os, sys
data = (b'Xcur'
    + struct.pack('<III', 16, 0x10000, 1)
    + struct.pack('<III', 0xFFFD0002, 24, 28)
    + struct.pack('<IIIIIIIII', 36, 0xFFFD0002, 24, 1, 1, 1, 0, 0, 50)
    + b'\x00\x00\x00\x00')
base = '/usr/share/icons/blank-cursor/cursors/left_ptr'
with open(base, 'wb') as f:
    f.write(data)
names = ['default','arrow','pointer','hand','hand1','hand2',
         'text','xterm','wait','watch','grabbing','grab',
         'move','progress','not-allowed']
for n in names:
    dst = '/usr/share/icons/blank-cursor/cursors/' + n
    if not os.path.exists(dst):
        os.symlink('left_ptr', dst)
print('Cursore trasparente: OK')
PYEOF

printf '[Icon Theme]\nName=blank-cursor\n' \
    > /usr/share/icons/blank-cursor/index.theme

ok "Cursore trasparente creato."

# =============================================================================
# FASE 9 - Servizio systemd
# =============================================================================
sep "FASE 9 - Servizio systemd kiosk"

# Prepara XDG runtime dir
mkdir -p "/run/user/${KIOSK_UID}"
chmod 700 "/run/user/${KIOSK_UID}"
chown "${KIOSK_USER}:${KIOSK_USER}" "/run/user/${KIOSK_UID}"
loginctl enable-linger "$KIOSK_USER" 2>/dev/null || true
log "XDG_RUNTIME_DIR: /run/user/${KIOSK_UID}"

# Scrivi il service file
log "Creo /etc/systemd/system/kiosk.service..."
printf '[Unit]\n' > /etc/systemd/system/kiosk.service
printf 'Description=Kiosk JavaFX 24/7\n' >> /etc/systemd/system/kiosk.service
printf 'After=network-online.target systemd-user-sessions.service local-fs.target\n' >> /etc/systemd/system/kiosk.service
printf 'Wants=network-online.target\n' >> /etc/systemd/system/kiosk.service
printf 'StartLimitIntervalSec=120\n' >> /etc/systemd/system/kiosk.service
printf 'StartLimitBurst=5\n' >> /etc/systemd/system/kiosk.service
printf '\n[Service]\n' >> /etc/systemd/system/kiosk.service
printf 'Type=simple\n' >> /etc/systemd/system/kiosk.service
printf 'User=%s\n' "$KIOSK_USER" >> /etc/systemd/system/kiosk.service
printf 'Group=%s\n' "$KIOSK_USER" >> /etc/systemd/system/kiosk.service
printf 'WorkingDirectory=%s\n' "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'Environment="HOME=%s"\n' "$KIOSK_HOME" >> /etc/systemd/system/kiosk.service
printf 'Environment="XDG_RUNTIME_DIR=/run/user/%s"\n' "$KIOSK_UID" >> /etc/systemd/system/kiosk.service
printf 'Environment="XCURSOR_THEME=blank-cursor"\n' >> /etc/systemd/system/kiosk.service
printf 'Environment="XCURSOR_SIZE=24"\n' >> /etc/systemd/system/kiosk.service
printf 'Environment="TOTEM_API_KEY=%s"\n' "$API_KEY" >> /etc/systemd/system/kiosk.service
printf 'Environment="LIBGL_ALWAYS_SOFTWARE=1"\n' >> /etc/systemd/system/kiosk.service
printf 'Environment="WLR_RENDERER=pixman"\n' >> /etc/systemd/system/kiosk.service
printf 'Environment="WLR_RENDERER_ALLOW_SOFTWARE=1"\n' >> /etc/systemd/system/kiosk.service
printf 'Environment="WLR_NO_HARDWARE_CURSORS=1"\n' >> /etc/systemd/system/kiosk.service
printf 'ExecStartPre=/bin/bash -c "test ! -f %s/.stop || { rm -f %s/.stop; exit 1; }"\n' \
    "$APP_DIR" "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'ExecStartPre=/bin/bash -c "mkdir -p /run/user/%s && chmod 700 /run/user/%s && chown %s:%s /run/user/%s"\n' \
    "$KIOSK_UID" "$KIOSK_UID" "$KIOSK_USER" "$KIOSK_USER" "$KIOSK_UID" \
    >> /etc/systemd/system/kiosk.service
printf 'ExecStart=/usr/bin/dbus-run-session /usr/bin/cage -d -- %s/run-kiosk.sh\n' \
    "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'Restart=always\n' >> /etc/systemd/system/kiosk.service
printf 'RestartSec=5s\n' >> /etc/systemd/system/kiosk.service
printf 'MemoryMax=600M\n' >> /etc/systemd/system/kiosk.service
printf 'OOMScoreAdjust=200\n' >> /etc/systemd/system/kiosk.service
printf 'StandardOutput=append:%s/logs/kiosk.log\n' "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'StandardError=append:%s/logs/kiosk-err.log\n' "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'ProtectSystem=strict\n' >> /etc/systemd/system/kiosk.service
printf 'ReadWritePaths=%s /tmp /run/user\n' "$APP_DIR" >> /etc/systemd/system/kiosk.service
printf 'PrivateTmp=true\n' >> /etc/systemd/system/kiosk.service
printf 'NoNewPrivileges=true\n' >> /etc/systemd/system/kiosk.service
printf '\n[Install]\n' >> /etc/systemd/system/kiosk.service
printf 'WantedBy=multi-user.target\n' >> /etc/systemd/system/kiosk.service

ok "kiosk.service creato."

# Timer pulizia log
printf '[Unit]\nDescription=Pulizia log kiosk\n[Service]\nType=oneshot\n' \
    > /etc/systemd/system/kiosk-logclean.service
printf 'ExecStart=/bin/bash -c "f=%s/logs/kiosk.log; [ -f $f ] && [ $(wc -c < $f) -gt 5242880 ] && tail -500 $f > $f.tmp && mv $f.tmp $f; journalctl --vacuum-size=50M 2>/dev/null||true"\n' \
    "$APP_DIR" >> /etc/systemd/system/kiosk-logclean.service
printf '[Unit]\nDescription=Pulizia log giornaliera\n[Timer]\nOnCalendar=daily\nPersistent=true\n[Install]\nWantedBy=timers.target\n' \
    > /etc/systemd/system/kiosk-logclean.timer

# Riavvio notturno
printf '[Unit]\nDescription=Riavvio notturno kiosk\n[Service]\nType=oneshot\n' \
    > /etc/systemd/system/kiosk-nightly-restart.service
printf 'ExecStart=/bin/bash -c "rm -f %s/.stop; systemctl restart kiosk.service"\n' \
    "$APP_DIR" >> /etc/systemd/system/kiosk-nightly-restart.service
printf '[Unit]\nDescription=Riavvio kiosk 04:00\n[Timer]\nOnCalendar=04:00\nRandomizedDelaySec=300\n[Install]\nWantedBy=timers.target\n' \
    > /etc/systemd/system/kiosk-nightly-restart.timer

ok "Tutti i servizi systemd creati."

# =============================================================================
# FASE 10 - Auto-login TTY1
# =============================================================================
sep "FASE 10 - Auto-login TTY1"

mkdir -p /etc/systemd/system/getty@tty1.service.d
printf '[Service]\nExecStart=\nExecStart=-/sbin/agetty --autologin %s --noclear %%I $TERM\nType=idle\n' \
    "$KIOSK_USER" > /etc/systemd/system/getty@tty1.service.d/autologin.conf
ok "Auto-login TTY1 configurato."

cat > "${KIOSK_HOME}/.bash_profile" << 'BPEOF'
if [ "$(tty)" = "/dev/tty1" ]; then
    if systemctl is-active --quiet kiosk.service 2>/dev/null; then
        echo "Kiosk attivo. Premi Invio per il terminale."
        read _
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

printf 'kiosk ALL=(ALL) NOPASSWD: /bin/systemctl restart kiosk.service\n' \
    > /etc/sudoers.d/kiosk
printf 'kiosk ALL=(ALL) NOPASSWD: /bin/systemctl start kiosk.service\n' \
    >> /etc/sudoers.d/kiosk
printf 'kiosk ALL=(ALL) NOPASSWD: /bin/systemctl stop kiosk.service\n' \
    >> /etc/sudoers.d/kiosk
chmod 440 /etc/sudoers.d/kiosk
ok "sudoers configurato."

# =============================================================================
# FASE 11 - Ottimizzazioni
# =============================================================================
sep "FASE 11 - Ottimizzazioni sistema"

# sysctl
printf 'kernel.panic = 30\nvm.swappiness = 5\nnet.ipv4.tcp_tw_reuse = 1\n' \
    > /etc/sysctl.d/99-kiosk.conf
sysctl -p /etc/sysctl.d/99-kiosk.conf > /dev/null 2>&1 || true
ok "sysctl configurato."

# Disabilita servizi inutili
for SVC in ModemManager bluetooth cups avahi-daemon apt-daily.timer apt-daily-upgrade.timer; do
    systemctl disable "$SVC" 2>/dev/null || true
    systemctl mask    "$SVC" 2>/dev/null || true
done
ok "Servizi inutili disabilitati."

# TTY extra
for i in 2 3 4 5 6; do
    systemctl mask "getty@tty${i}.service" 2>/dev/null || true
done

# Default target
systemctl set-default multi-user.target

# GRUB
if [ -f /etc/default/grub ]; then
    sed -i 's/^GRUB_TIMEOUT=.*/GRUB_TIMEOUT=2/' /etc/default/grub
    grep -q '^GRUB_TIMEOUT_STYLE' /etc/default/grub || \
        printf 'GRUB_TIMEOUT_STYLE=menu\n' >> /etc/default/grub
    update-grub 2>/dev/null || grub-mkconfig -o /boot/grub/grub.cfg 2>/dev/null || true
    ok "GRUB: timeout 2s."
fi

# udev
printf 'SUBSYSTEM=="drm",   TAG+="uaccess", GROUP="video"\n' \
    > /etc/udev/rules.d/99-kiosk.rules
printf 'SUBSYSTEM=="input", TAG+="uaccess", GROUP="input"\n' \
    >> /etc/udev/rules.d/99-kiosk.rules
udevadm control --reload-rules 2>/dev/null || true

# Journal
mkdir -p /etc/systemd/journald.conf.d/
printf '[Journal]\nSystemMaxUse=50M\nCompress=yes\nForwardToSyslog=no\n' \
    > /etc/systemd/journald.conf.d/kiosk.conf

ok "Ottimizzazioni completate."

# =============================================================================
# FASE 12 - Abilitazione servizi e permessi finali
# =============================================================================
sep "FASE 12 - Abilitazione servizi"

chown -R "${KIOSK_USER}:${KIOSK_USER}" "$APP_DIR"
chmod -R u+rw "$APP_DIR"

systemctl daemon-reload
systemctl enable kiosk.service        && ok "kiosk.service abilitato."
systemctl enable kiosk-logclean.timer && ok "kiosk-logclean.timer abilitato."
systemctl enable kiosk-nightly-restart.timer && ok "kiosk-nightly-restart.timer abilitato."

# =============================================================================
# RIEPILOGO FINALE
# =============================================================================
sep "RIEPILOGO"

echo ""
log "=== STATO FINALE ==="

# JAR
if [ -f "${APP_DIR}/demo-1.jar" ]; then
    SZ=$(du -h "${APP_DIR}/demo-1.jar" | cut -f1)
    ok "demo-1.jar: presente ($SZ)"
else
    warn "demo-1.jar: MANCANTE"
fi

# Lib
LIB_N=$(ls "${APP_DIR}/lib/"*.jar 2>/dev/null | wc -l || echo 0)
ok "Librerie: $LIB_N JAR in ${APP_DIR}/lib/"

# JavaFX
FX_N=$(ls "${JAVAFX_DIR}/"*.jar 2>/dev/null | wc -l || echo 0)
if [ "$FX_N" -gt 0 ]; then
    ok "JavaFX SDK: $FX_N JAR in $JAVAFX_DIR"
else
    warn "JavaFX SDK: non presente. L'app potrebbe non avviarsi."
    warn "Soluzione: apt install openjfx"
fi

# run-kiosk.sh
if [ -x "${APP_DIR}/run-kiosk.sh" ]; then
    ok "run-kiosk.sh: eseguibile"
else
    warn "run-kiosk.sh: problemi di permessi"
    chmod +x "${APP_DIR}/run-kiosk.sh"
fi

# Java
if command -v java >/dev/null 2>&1; then
    ok "Java: $(java -version 2>&1 | head -1)"
else
    warn "Java: non trovato nel PATH"
fi

# cage
if command -v cage >/dev/null 2>&1; then
    ok "cage: $(cage --version 2>/dev/null || echo 'trovato')"
else
    warn "cage: non trovato - avvio non funzionera"
    warn "Installa con: apt install cage"
fi

# seatd
if systemctl is-active seatd >/dev/null 2>&1; then
    ok "seatd: attivo (gestione seat OK)"
elif command -v seatd >/dev/null 2>&1; then
    warn "seatd installato ma non attivo - avvio..."
    systemctl start seatd 2>/dev/null || true
else
    warn "seatd non installato - cage potrebbe non avviarsi"
    warn "Installa con: apt install seatd"
fi

# Verifica gruppo seat
if id "$KIOSK_USER" 2>/dev/null | grep -q "seat"; then
    ok "Utente $KIOSK_USER nel gruppo seat: OK"
else
    warn "Utente $KIOSK_USER NON nel gruppo seat"
    warn "Fix: usermod -aG seat $KIOSK_USER && reboot"
fi

# servizio
systemctl is-enabled kiosk.service >/dev/null 2>&1     && ok "kiosk.service: abilitato"     || warn "kiosk.service: non abilitato"

echo ""
echo "+------------------------------------------------------+"
echo "|  SETUP COMPLETATO                                    |"
echo "+------------------------------------------------------+"
echo "|                                                      |"
echo "|  Avvia ora:  systemctl start kiosk.service           |"
echo "|  Oppure:     reboot                                  |"
echo "|                                                      |"
echo "|  Diagnostica:                                        |"
echo "|    kiosk-control status                              |"
echo "|    kiosk-control log                                 |"
echo "|    kiosk-control errlog                              |"
echo "|    journalctl -u kiosk -f                            |"
echo "|                                                      |"
echo "|  Profilo attuale:  kiosk-control profile             |"
echo "|  Reset profilo:    kiosk-control reset-profile       |"
echo "+------------------------------------------------------+"
echo ""