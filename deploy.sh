#!/bin/bash
# ==============================================================================
#   Totem Kiosk — Deploy & Launcher
# ==============================================================================

set -euo pipefail

GITHUB_USER="accaicedtea"
GITHUB_REPO="ttetttos"
RELEASE_TAG="v1.0.12"
JAR_NAME="demo-1.jar"
LIB_ARCHIVE="lib.tar.gz"

BASE_URL="https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${RELEASE_TAG}"
JAR_URL="${BASE_URL}/${JAR_NAME}"
LIB_URL="${BASE_URL}/${LIB_ARCHIVE}"

FX_VER="21.0.2"
FX_URL="https://download2.gluonhq.com/openjfx/${FX_VER}/openjfx-${FX_VER}_linux-x64_bin-sdk.zip"
FX_ZIP="openjfx-${FX_VER}.zip"

INSTALL_DIR="${HOME}/.totem-kiosk"
APP_DIR="${INSTALL_DIR}/app"
LIB_DIR="${APP_DIR}/lib"
FX_BASE_DIR="${INSTALL_DIR}/javafx"
FX_DIR="${FX_BASE_DIR}/javafx-sdk-${FX_VER}"
JAVA_MIN=17
JAVA_BIN=""

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
log()   { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
title() { echo -e "\n${BOLD}${CYAN}══ $1 ══${NC}\n"; }
die()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ==============================================================================
check_system_deps() {
    title "Controllo dipendenze"
    local missing=()
    for cmd in curl unzip tar; do
        command -v "$cmd" &>/dev/null || missing+=("$cmd")
    done
    if [ ${#missing[@]} -gt 0 ]; then
        warn "Installo: ${missing[*]}"
        sudo apt-get update -qq && sudo apt-get install -y "${missing[@]}"
    fi
    ok "Dipendenze OK."
}

# ==============================================================================
check_java() {
    title "Controllo Java"
    for candidate in java \
        "/usr/lib/jvm/temurin-17/bin/java" \
        "${HOME}/.sdkman/candidates/java/current/bin/java" \
        "${INSTALL_DIR}/jdk17/bin/java"; do
        if [ "$candidate" = "java" ]; then
            command -v java &>/dev/null || continue
            JAVA_BIN="java"
        else
            [ -x "$candidate" ] || continue
            JAVA_BIN="$candidate"
        fi
        JAVA_VER=$("$JAVA_BIN" -version 2>&1 | grep -oP '(?<=version ")[\d]+' | head -1)
        if [ "${JAVA_VER:-0}" -ge "$JAVA_MIN" ] 2>/dev/null; then
            ok "Java ${JAVA_VER}: $JAVA_BIN"
            return
        fi
    done
    warn "Java ${JAVA_MIN}+ non trovato. Installo Temurin 17..."
    install_java
}

install_java() {
    sudo apt-get install -y wget apt-transport-https gnupg lsb-release
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
    echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
        | sudo tee /etc/apt/sources.list.d/adoptium.list
    sudo apt-get update -qq && sudo apt-get install -y temurin-17-jdk
    JAVA_BIN="java"
    ok "Java installato."
}

# ==============================================================================
check_javafx() {
    title "Controllo JavaFX"
    for dir in "$FX_DIR" \
        "/opt/javafx-sdk-${FX_VER}" \
        "/usr/local/javafx-sdk-${FX_VER}" \
        "${HOME}/javafx-sdk-${FX_VER}"; do
        if [ -f "${dir}/lib/javafx.controls.jar" ]; then
            FX_DIR="$dir"
            ok "JavaFX trovato: ${FX_DIR}"
            return
        fi
    done
    warn "JavaFX non trovato. Scarico..."
    mkdir -p "$FX_BASE_DIR"
    curl -L --progress-bar "$FX_URL" -o "${FX_BASE_DIR}/${FX_ZIP}"
    unzip -q "${FX_BASE_DIR}/${FX_ZIP}" -d "$FX_BASE_DIR"
    rm -f "${FX_BASE_DIR}/${FX_ZIP}"
    FX_DIR=$(find "$FX_BASE_DIR" -maxdepth 1 -type d -name "javafx-sdk*" | head -1)
    [ -n "$FX_DIR" ] || die "Estrazione JavaFX fallita."
    ok "JavaFX pronto: ${FX_DIR}"
}

# ==============================================================================
download_app() {
    title "Download release ${RELEASE_TAG}"
    mkdir -p "$APP_DIR" "$LIB_DIR"

    local jar_path="${APP_DIR}/${JAR_NAME}"

    # ── JAR: scarica solo se non esiste o se --update ─────────────────────────
    if [ ! -f "$jar_path" ] || [ "${FORCE_UPDATE:-false}" = "true" ]; then
        log "Scarico ${JAR_NAME}..."
        curl -L --progress-bar "$JAR_URL" -o "$jar_path" \
            || die "Download JAR fallito. Release ${RELEASE_TAG} esiste?"
        ok "JAR scaricato."
    else
        ok "JAR già presente — skip download."
    fi

    # ── Lib: scarica solo se vuota o se --update ──────────────────────────────
    local lib_count
    lib_count=$(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)

    if [ "$lib_count" -eq 0 ] || [ "${FORCE_UPDATE:-false}" = "true" ]; then
        log "Scarico librerie..."
        local lib_tar="${APP_DIR}/${LIB_ARCHIVE}"
        curl -L --progress-bar "$LIB_URL" -o "$lib_tar" || die "Download lib.tar.gz fallito."
        # Pulisce lib esistenti prima di estrarre
        rm -rf "${LIB_DIR:?}"/*
        tar -xzf "$lib_tar" -C "$LIB_DIR" --strip-components=0
        rm -f "$lib_tar"
        lib_count=$(find "$LIB_DIR" -name "*.jar" | wc -l)
        ok "Librerie estratte: ${lib_count} JAR."
    else
        ok "Librerie già presenti (${lib_count} JAR) — skip download."
    fi
}

# ==============================================================================
create_launcher_script() {
    local launcher="${INSTALL_DIR}/start.sh"
    cat > "$launcher" << LAUNCHER
#!/bin/bash
JAVA_BIN="${JAVA_BIN}"
FX_DIR="${FX_DIR}"
APP_DIR="${APP_DIR}"
LIB_DIR="${LIB_DIR}"
JAR_NAME="${JAR_NAME}"

cp="\${APP_DIR}/\${JAR_NAME}"
while IFS= read -r lib; do cp="\${cp}:\${lib}"; done \
    < <(find "\${LIB_DIR}" -name "*.jar" | sort)

"\${JAVA_BIN}" \\
    --module-path "\${FX_DIR}/lib" \\
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \\
    --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \\
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \\
    -cp "\${cp}" \\
    com.app.App
LAUNCHER
    chmod +x "$launcher"
    ok "Launcher creato: ${launcher}"
}

# ==============================================================================
launch_app() {
    title "Avvio Totem Kiosk"
    local jar_path="${APP_DIR}/${JAR_NAME}"
    [ -f "$jar_path" ]                          || die "JAR non trovato: ${jar_path}"
    [ -f "${FX_DIR}/lib/javafx.controls.jar" ]  || die "JavaFX non trovato: ${FX_DIR}"

    local cp="${jar_path}"
    while IFS= read -r lib; do cp="${cp}:${lib}"; done \
        < <(find "$LIB_DIR" -name "*.jar" | sort)

    ok "Avvio app..."
    "$JAVA_BIN" \
        --module-path "${FX_DIR}/lib" \
        --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
        --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
        --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
        -cp "${cp}" \
        com.app.App
}

# ==============================================================================
# MAIN — nessun prompt interattivo, tutto automatico
# ==============================================================================
case "${1:-}" in
    --help|-h)
        echo "Uso: ./deploy.sh [--update | --run | --help]"
        echo "  (nessuno)  Prima installazione o avvio normale"
        echo "  --update   Forza re-download JAR + lib"
        echo "  --run      Solo avvio (skip download)"
        exit 0 ;;

    --update)
        export FORCE_UPDATE=true
        mkdir -p "$INSTALL_DIR"
        check_system_deps; check_java; check_javafx
        download_app; create_launcher_script; launch_app ;;

    --run)
        check_java; check_javafx; launch_app ;;

    *)
        mkdir -p "$INSTALL_DIR"
        check_system_deps; check_java; check_javafx
        download_app; create_launcher_script; launch_app ;;
esac