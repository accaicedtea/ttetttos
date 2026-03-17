#!/bin/bash

# ==============================================================================
#   Totem Kiosk — Deploy & Launcher
#   Scarica la release da GitHub, verifica Java + JavaFX, avvia l'app.
# ==============================================================================

set -euo pipefail

# ── Parametri release ──────────────────────────────────────────────────────────
GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
RELEASE_TAG="v1.0.12"                   # ← aggiorna ad ogni nuova versione
JAR_NAME="demo-1.jar"
LIB_ARCHIVE="lib.tar.gz"

BASE_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}"
JAR_URL="${BASE_URL}/${JAR_NAME}"
LIB_URL="${BASE_URL}/${LIB_ARCHIVE}"

# ── Parametri JavaFX ───────────────────────────────────────────────────────────
FX_VER="21.0.2"
FX_URL="https://download2.gluonhq.com/openjfx/${FX_VER}/openjfx-${FX_VER}_linux-x64_bin-sdk.zip"
FX_ZIP="openjfx-${FX_VER}.zip"
FX_DIR_NAME="javafx-sdk-${FX_VER}"

# ── Directory di installazione ─────────────────────────────────────────────────
INSTALL_DIR="${HOME}/.totem-kiosk"
APP_DIR="${INSTALL_DIR}/app"
LIB_DIR="${APP_DIR}/lib"
FX_BASE_DIR="${INSTALL_DIR}/javafx"
FX_DIR="${FX_BASE_DIR}/${FX_DIR_NAME}"

# ── Java minima richiesta ──────────────────────────────────────────────────────
JAVA_MIN=17

# ── Colori ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

log()     { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()      { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()     { echo -e "${RED}[ERROR]${NC} $1"; }
title()   { echo -e "\n${BOLD}${CYAN}══ $1 ══${NC}\n"; }
die()     { err "$1"; exit 1; }

# ==============================================================================
# STEP 1 — Verifica dipendenze di sistema
# ==============================================================================
check_system_deps() {
    title "Controllo dipendenze di sistema"

    local missing=()
    for cmd in curl unzip tar; do
        if ! command -v "$cmd" &>/dev/null; then
            missing+=("$cmd")
        else
            ok "$cmd trovato: $(command -v $cmd)"
        fi
    done

    if [ ${#missing[@]} -gt 0 ]; then
        warn "Installazione pacchetti mancanti: ${missing[*]}"
        if command -v apt-get &>/dev/null; then
            sudo apt-get update -qq
            sudo apt-get install -y "${missing[@]}"
        elif command -v dnf &>/dev/null; then
            sudo dnf install -y "${missing[@]}"
        elif command -v pacman &>/dev/null; then
            sudo pacman -Sy --noconfirm "${missing[@]}"
        else
            die "Package manager non riconosciuto. Installa manualmente: ${missing[*]}"
        fi
        ok "Dipendenze installate."
    fi
}

# ==============================================================================
# STEP 2 — Verifica Java
# ==============================================================================
check_java() {
    title "Controllo Java"

    # Cerca java nel PATH o nelle posizioni standard
    JAVA_BIN=""

    if command -v java &>/dev/null; then
        JAVA_BIN="java"
    elif [ -x "/usr/lib/jvm/temurin-17/bin/java" ]; then
        JAVA_BIN="/usr/lib/jvm/temurin-17/bin/java"
    elif [ -x "${HOME}/.sdkman/candidates/java/current/bin/java" ]; then
        JAVA_BIN="${HOME}/.sdkman/candidates/java/current/bin/java"
    fi

    if [ -n "$JAVA_BIN" ]; then
        JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oP '(?<=version ")[\d]+' | head -1)
        if [ "${JAVA_VER:-0}" -ge "$JAVA_MIN" ] 2>/dev/null; then
            ok "Java ${JAVA_VER} trovato: $($JAVA_BIN -version 2>&1 | head -1)"
            return
        else
            warn "Java ${JAVA_VER} trovato ma serve >= ${JAVA_MIN}."
        fi
    fi

    # Java mancante o troppo vecchio — installa Temurin 17
    warn "Java ${JAVA_MIN}+ non trovato. Installo Eclipse Temurin 17..."
    install_java
}

install_java() {
    log "Aggiunta repository Adoptium..."

    if command -v apt-get &>/dev/null; then
        # Ubuntu/Debian
        sudo apt-get install -y wget apt-transport-https gnupg
        wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
            | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
        echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
            | sudo tee /etc/apt/sources.list.d/adoptium.list
        sudo apt-get update -qq
        sudo apt-get install -y temurin-17-jdk
        JAVA_BIN="java"

    elif command -v dnf &>/dev/null; then
        sudo dnf install -y java-17-openjdk
        JAVA_BIN="java"

    elif command -v pacman &>/dev/null; then
        sudo pacman -Sy --noconfirm jdk17-openjdk
        JAVA_BIN="java"

    else
        # Fallback: scarica JDK binario da Adoptium
        log "Scarico JDK 17 binario da Adoptium..."
        local JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
        local JDK_TAR="${INSTALL_DIR}/jdk17.tar.gz"
        local JDK_DIR="${INSTALL_DIR}/jdk17"
        mkdir -p "$JDK_DIR"
        curl -L --progress-bar "$JDK_URL" -o "$JDK_TAR"
        tar -xzf "$JDK_TAR" -C "$JDK_DIR" --strip-components=1
        rm -f "$JDK_TAR"
        JAVA_BIN="${JDK_DIR}/bin/java"
    fi

    ok "Java installato: $($JAVA_BIN -version 2>&1 | head -1)"
}

# ==============================================================================
# STEP 3 — Verifica / Scarica JavaFX SDK
# ==============================================================================
check_javafx() {
    title "Controllo JavaFX SDK"

    # Cerca JavaFX nelle posizioni comuni
    local fx_search_dirs=(
        "${FX_DIR}"
        "/opt/javafx-sdk-${FX_VER}"
        "/usr/local/javafx-sdk-${FX_VER}"
        "${HOME}/javafx-sdk-${FX_VER}"
    )

    for dir in "${fx_search_dirs[@]}"; do
        if [ -f "${dir}/lib/javafx.controls.jar" ]; then
            FX_DIR="$dir"
            ok "JavaFX SDK trovato: ${FX_DIR}"
            return
        fi
    done

    warn "JavaFX SDK ${FX_VER} non trovato. Scarico..."
    download_javafx
}

download_javafx() {
    mkdir -p "$FX_BASE_DIR"
    local zip_path="${FX_BASE_DIR}/${FX_ZIP}"

    log "Download JavaFX SDK ${FX_VER}..."
    curl -L --progress-bar "$FX_URL" -o "$zip_path"

    log "Estrazione..."
    unzip -q "$zip_path" -d "$FX_BASE_DIR"
    rm -f "$zip_path"

    # Il nome della cartella estratta può variare — trova quella corretta
    local extracted
    extracted=$(find "$FX_BASE_DIR" -maxdepth 1 -type d -name "javafx-sdk*" | head -1)
    if [ -z "$extracted" ]; then
        die "Estrazione JavaFX fallita. Controlla manualmente ${FX_BASE_DIR}."
    fi

    FX_DIR="$extracted"
    ok "JavaFX SDK pronto: ${FX_DIR}"
}

# ==============================================================================
# STEP 4 — Crea directory e scarica JAR + lib dalla release GitHub
# ==============================================================================
download_app() {
    title "Download release ${RELEASE_TAG}"

    mkdir -p "$APP_DIR" "$LIB_DIR"

    # ── JAR ──────────────────────────────────────────────────────────────────
    local jar_path="${APP_DIR}/${JAR_NAME}"
    local need_download=true

    if [ -f "$jar_path" ]; then
        warn "JAR già presente. Vuoi ri-scaricarlo? (s/n)"
        read -r ans
        [ "$ans" = "s" ] && need_download=true || need_download=false
    fi

    if $need_download; then
        log "Scarico ${JAR_NAME}..."
        curl -L --progress-bar "$JAR_URL" -o "$jar_path" \
            || die "Download JAR fallito. Verifica che la release ${RELEASE_TAG} esista su GitHub."
        ok "JAR scaricato: ${jar_path}"
    fi

    # ── Librerie ─────────────────────────────────────────────────────────────
    local lib_count
    lib_count=$(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)

    if [ "$lib_count" -gt 0 ] && ! $need_download; then
        ok "Librerie già presenti (${lib_count} JAR)."
    else
        log "Scarico librerie (${LIB_ARCHIVE})..."
        local lib_tar="${APP_DIR}/${LIB_ARCHIVE}"
        curl -L --progress-bar "$LIB_URL" -o "$lib_tar" \
            || die "Download lib.tar.gz fallito."

        log "Estrazione librerie..."
        tar -xzf "$lib_tar" -C "$LIB_DIR" --strip-components=0
        rm -f "$lib_tar"

        lib_count=$(find "$LIB_DIR" -name "*.jar" | wc -l)
        ok "Librerie estratte: ${lib_count} JAR in ${LIB_DIR}"
    fi
}

# ==============================================================================
# STEP 5 — Costruisce il classpath e avvia l'app
# ==============================================================================
launch_app() {
    title "Avvio Totem Kiosk ${RELEASE_TAG}"

    local jar_path="${APP_DIR}/${JAR_NAME}"
    [ -f "$jar_path" ] || die "JAR non trovato: ${jar_path}"
    [ -f "${FX_DIR}/lib/javafx.controls.jar" ] || die "JavaFX non trovato in ${FX_DIR}"

    # ── Classpath: JAR app + tutte le lib ─────────────────────────────────────
    local cp="${jar_path}"
    while IFS= read -r lib; do
        cp="${cp}:${lib}"
    done < <(find "$LIB_DIR" -name "*.jar" | sort)

    # ── Module path JavaFX ────────────────────────────────────────────────────
    local fx_modules="${FX_DIR}/lib"

    # ── Moduli JavaFX necessari ───────────────────────────────────────────────
    local fx_mods="javafx.controls,javafx.fxml,javafx.graphics,javafx.base"

    log "Classpath: ${cp:0:80}..."
    log "JavaFX:    ${fx_modules}"
    echo ""
    ok "Avvio applicazione..."
    echo "──────────────────────────────────────────────"

    "$JAVA_BIN" \
        --module-path "${fx_modules}" \
        --add-modules "${fx_mods}" \
        --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
        --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
        -cp "${cp}" \
        com.example.App
}

# ==============================================================================
# STEP 6 — Crea collegamento rapido per avvii successivi
# ==============================================================================
create_launcher_script() {
    local launcher="${INSTALL_DIR}/start.sh"

    cat > "$launcher" <<LAUNCHER
#!/bin/bash
# Avvio rapido Totem Kiosk — generato da deploy.sh
JAVA_BIN="${JAVA_BIN}"
FX_DIR="${FX_DIR}"
APP_DIR="${APP_DIR}"
LIB_DIR="${LIB_DIR}"
JAR_NAME="${JAR_NAME}"

cp="\${APP_DIR}/\${JAR_NAME}"
for lib in "\${LIB_DIR}"/*.jar; do cp="\${cp}:\${lib}"; done

"\${JAVA_BIN}" \\
    --module-path "\${FX_DIR}/lib" \\
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \\
    --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \\
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \\
    -cp "\${cp}" \\
    com.example.App
LAUNCHER

    chmod +x "$launcher"
    ok "Script avvio rapido creato: ${launcher}"
    ok "Prossime volte usa: ${launcher}"
}

# ==============================================================================
# MAIN
# ==============================================================================
main() {
    echo ""
    echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${CYAN}║   Totem Kiosk — Deploy & Launcher        ║${NC}"
    echo -e "${BOLD}${CYAN}║   Release: ${RELEASE_TAG}                          ║${NC}"
    echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════╝${NC}"

    mkdir -p "$INSTALL_DIR"

    check_system_deps
    check_java
    check_javafx
    download_app
    create_launcher_script
    launch_app
}

# ── Gestione argomenti CLI ──────────────────────────────────────────────────────
case "${1:-}" in
    --help|-h)
        echo "Uso: ./deploy.sh [opzione]"
        echo ""
        echo "Opzioni:"
        echo "  (nessuna)   Installazione completa + avvio"
        echo "  --run       Avvia senza ri-scaricare"
        echo "  --update    Forza re-download JAR + lib"
        echo "  --help      Mostra questo help"
        exit 0
        ;;
    --run)
        # Solo avvio (presuppone installazione già fatta)
        check_java
        check_javafx
        launch_app
        ;;
    --update)
        # Forza re-download anche se già presente
        check_system_deps
        check_java
        check_javafx
        mkdir -p "$APP_DIR" "$LIB_DIR"
        log "Forzando re-download..."
        curl -L --progress-bar "$JAR_URL" -o "${APP_DIR}/${JAR_NAME}"
        local lib_tar="${APP_DIR}/${LIB_ARCHIVE}"
        curl -L --progress-bar "$LIB_URL" -o "$lib_tar"
        tar -xzf "$lib_tar" -C "$LIB_DIR" --strip-components=0
        rm -f "$lib_tar"
        create_launcher_script
        launch_app
        ;;
    *)
        main
        ;;
esac
