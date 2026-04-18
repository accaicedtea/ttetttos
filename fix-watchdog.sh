#!/bin/bash
# ==============================================================================
#   Fix watchdog — esegui sulla macchina kiosk come root
#   Corregge il watchdog per non riscaricare ad ogni boot
# ==============================================================================

LOG_DIR="/var/log/totem-kiosk"
DEPLOY_SCRIPT="/home/kiosk/deploy.sh"
START_SCRIPT="/home/kiosk/.totem-kiosk/start.sh"
CRASH_MAX_RETRIES=5
CRASH_RESET_AFTER=300

cat > /usr/local/bin/totem-watchdog.sh << WATCHDOG
#!/bin/bash
LOG="${LOG_DIR}/watchdog.log"
APP_LOG="${LOG_DIR}/app.log"
CRASH_COUNT=0
LAST_START=0

log()  { echo "\$(date '+%Y-%m-%d %H:%M:%S') [WATCHDOG] \$1" | tee -a "\$LOG"; }
crash(){ echo "\$(date '+%Y-%m-%d %H:%M:%S') [CRASH]    \$1" | tee -a "\$LOG"; }

log "Watchdog avviato. PID=\$\$"

# ── Setup iniziale: solo se start.sh NON esiste ancora ────────────────────────
# Evita di riscaricare tutto ad ogni reboot
if [ ! -f "${START_SCRIPT}" ]; then
    log "Prima installazione — eseguo deploy.sh..."
    # Forza modalità non-interattiva: risponde automaticamente 's' a qualsiasi prompt
    echo "s" | bash "${DEPLOY_SCRIPT}" >> "\$APP_LOG" 2>&1 || true
    log "Deploy completato."
else
    log "App già installata — skip download. Uso: ${START_SCRIPT}"
fi

# ── Loop principale ────────────────────────────────────────────────────────────
while true; do
    NOW=\$(date +%s)

    # Reset contatore crash se la sessione è durata abbastanza
    if [ \$LAST_START -gt 0 ]; then
        UPTIME=\$(( NOW - LAST_START ))
        if [ \$UPTIME -ge ${CRASH_RESET_AFTER} ] && [ \$CRASH_COUNT -gt 0 ]; then
            log "Sessione stabile (\${UPTIME}s). Reset contatore crash."
            CRASH_COUNT=0
        fi
    fi

    # Troppi crash → reboot
    if [ \$CRASH_COUNT -ge ${CRASH_MAX_RETRIES} ]; then
        if [ -f "/home/kiosk/.totem-kiosk/app/demo-1.jar.bak" ]; then
            crash "Limite crash raggiunto con backup. Eseguo ROLLBACK..."
            mv "/home/kiosk/.totem-kiosk/app/demo-1.jar.bak" "/home/kiosk/.totem-kiosk/app/demo-1.jar"
            touch "/home/kiosk/.totem-kiosk/app/rollback_pending.json"
            CRASH_COUNT=0
            log "Rollback completato. Attesa 5s poi riavvio app..."
            sleep 5
            continue
        fi
        
        crash "Limite crash raggiunto e nessun backup. Reboot tra 10s..."
        sleep 10
        sudo /sbin/reboot
        exit 1
    fi

    log "Avvio app (tentativo \$(( CRASH_COUNT + 1 )))..."
    LAST_START=\$(date +%s)

    # Usa start.sh (veloce, nessun download) oppure deploy.sh --run come fallback
    if [ -f "${START_SCRIPT}" ]; then
        bash "${START_SCRIPT}" >> "\$APP_LOG" 2>&1
        EXIT_CODE=\$?
    elif [ -f "${DEPLOY_SCRIPT}" ]; then
        bash "${DEPLOY_SCRIPT}" --run >> "\$APP_LOG" 2>&1
        EXIT_CODE=\$?
    else
        crash "Nessuno script trovato. Attendo 30s..."
        sleep 30
        continue
    fi

    DURATION=\$(( \$(date +%s) - LAST_START ))

    if [ \$EXIT_CODE -eq 0 ]; then
        log "App terminata normalmente (durata: \${DURATION}s). Riavvio..."
    else
        CRASH_COUNT=\$(( CRASH_COUNT + 1 ))
        crash "Crash (exit=\${EXIT_CODE}, durata=\${DURATION}s). #\${CRASH_COUNT}/${CRASH_MAX_RETRIES}"
        pkill -f "com.app.App" 2>/dev/null || true
        pkill -f "demo-1.jar"      2>/dev/null || true
        WAIT=\$(( 3 * CRASH_COUNT ))
        [ \$WAIT -gt 30 ] && WAIT=30
        log "Retry tra \${WAIT}s..."
        sleep "\$WAIT"
    fi
done
WATCHDOG

chmod +x /usr/local/bin/totem-watchdog.sh
echo "[OK] Watchdog aggiornato."

# ── Fix deploy.sh: aggiungi flag --non-interactive ────────────────────────────
# Sostituisce il blocco 'read' con risposta automatica quando JAR esiste
sed -i 's/warn "JAR già presente. Vuoi ri-scaricarlo? (s\/n)"/warn "JAR già presente. Uso quello esistente (usa --update per forzare)."\n        need_download=false\n        return\n        warn "JAR già presente. Vuoi ri-scaricarlo? (s\/n) [DISABILITATO]"/' \
    "${DEPLOY_SCRIPT}" 2>/dev/null || true

echo "[OK] Fix completato."
echo ""
echo "Comandi utili:"
echo "  tail -f /var/log/totem-kiosk/watchdog.log   # vedi cosa fa"
echo "  tail -f /var/log/totem-kiosk/app.log         # log dell'app"
echo ""
echo "Per forzare aggiornamento app:"
echo "  sudo -u kiosk bash /home/kiosk/deploy.sh --update"
echo ""
echo "Riavvia per applicare:"
echo "  sudo reboot"
