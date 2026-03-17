#!/bin/bash
# ==============================================================================
#   Test Email — verifica msmtp + Gmail SMTP
#   Uso: ./test-email.sh
# ==============================================================================

# ── Configura qui ──────────────────────────────────────────────────────────────
GMAIL_USER="giaccabugata@gmail.com"            # ← account Gmail mittente
GMAIL_APP_PASSWORD="zytg gsty ifvn wlrn" # ← App Password SENZA spazi
NOTIFY_EMAIL="giaccabugata@gmail.com" # ← dove ricevi la mail
# ──────────────────────────────────────────────────────────────────────────────

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[OK]${NC}    $1"; }
fail() { echo -e "${RED}[FAIL]${NC}  $1"; }
log()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }

echo ""
echo -e "${BOLD}══ Test Email msmtp ══${NC}"
echo ""

# ==============================================================================
# 1. Verifica msmtp installato
# ==============================================================================
log "Controllo msmtp..."
if ! command -v msmtp &>/dev/null; then
    fail "msmtp non installato. Installa con:"
    echo "       sudo apt-get install -y msmtp msmtp-mta"
    exit 1
fi
ok "msmtp trovato: $(msmtp --version | head -1)"

# ==============================================================================
# 2. Scrivi /etc/msmtprc temporaneo (pulisce eventuali configurazioni rotte)
# ==============================================================================
log "Scrivo configurazione msmtp..."

MSMTP_CONF=$(mktemp)
cat > "$MSMTP_CONF" << MSMTPCONF
defaults
auth           on
tls            on
tls_trust_file /etc/ssl/certs/ca-certificates.crt
logfile        /tmp/msmtp-test.log

account        gmail
host           smtp.gmail.com
port           587
from           ${GMAIL_USER}
user           ${GMAIL_USER}
password       ${GMAIL_APP_PASSWORD}

account default : gmail
MSMTPCONF

chmod 600 "$MSMTP_CONF"
ok "Config temporanea creata: $MSMTP_CONF"

# ==============================================================================
# 3. Invia email di test
# ==============================================================================
HOSTNAME=$(hostname)
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
LOCAL_IP=$(ip route get 1 2>/dev/null | grep -oP 'src \K[^ ]+' | head -1 || echo "sconosciuto")

log "Invio email a ${NOTIFY_EMAIL}..."

ERROR_LOG=$(mktemp)

printf "To: %s\nFrom: %s\nSubject: [Totem Kiosk] Test email — %s\n\nTest email dal totem kiosk.\n\nHostname: %s\nIP:       %s\nData:     %s\n\nSe ricevi questa email, la configurazione funziona!\n" \
    "${NOTIFY_EMAIL}" \
    "${GMAIL_USER}" \
    "${HOSTNAME}" \
    "${HOSTNAME}" \
    "${LOCAL_IP}" \
    "${TIMESTAMP}" \
    | msmtp --file="$MSMTP_CONF" -a gmail "${NOTIFY_EMAIL}" 2>"$ERROR_LOG"

EXIT=$?

if [ $EXIT -eq 0 ]; then
    ok "Email inviata con successo a ${NOTIFY_EMAIL}!"
    echo ""
    echo -e "${GREEN}La configurazione funziona.${NC}"
    echo -e "Ora copia /etc/msmtprc con questi parametri:"
    echo ""
    cat "$MSMTP_CONF"
    echo ""

    # Installa la config come definitiva
    read -p "Vuoi installare questa config in /etc/msmtprc? (s/n): " ans
    if [ "$ans" = "s" ]; then
        sudo cp "$MSMTP_CONF" /etc/msmtprc
        sudo chmod 600 /etc/msmtprc
        ok "Config installata in /etc/msmtprc"
    fi
else
    fail "Invio fallito (exit=$EXIT)"
    echo ""
    echo -e "${RED}Errore msmtp:${NC}"
    cat "$ERROR_LOG"
    echo ""
    echo -e "${YELLOW}Soluzioni comuni:${NC}"
    echo "  1. App Password sbagliata → vai su myaccount.google.com → App Password"
    echo "  2. App Password con spazi → rimuovili: 'abcd efgh' → 'abcdefgh'"
    echo "  3. Verifica 2 passaggi non attiva → attivala su Google Account"
    echo "  4. Account bloccato → controlla gmail.com per avvisi di sicurezza"
    echo ""
    echo -e "${YELLOW}Log completo:${NC} cat /tmp/msmtp-test.log"
fi

# Pulizia
rm -f "$MSMTP_CONF" "$ERROR_LOG"
