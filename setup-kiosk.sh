#!/bin/bash
# ==============================================================================
#   Totem Kiosk — Setup Sistema Linux
#   Auto-login → Kiosk → Watchdog → SSH → Email notifica accesso
# ==============================================================================

set -euo pipefail

# ── Configurazione ─────────────────────────────────────────────────────────────
KIOSK_USER="kiosk"
KIOSK_HOME="/home/${KIOSK_USER}"
APP_NAME="totem-kiosk"
DEPLOY_SCRIPT="${KIOSK_HOME}/deploy.sh"
START_SCRIPT="${KIOSK_HOME}/.totem-kiosk/start.sh"
LOG_DIR="/var/log/${APP_NAME}"
CRASH_MAX_RETRIES=5
CRASH_RESET_AFTER=300

# ── Email (modifica questi valori prima di eseguire) ───────────────────────────
NOTIFY_EMAIL="giaccabugata@gmail.com"        # ← dove ricevi la notifica
GMAIL_USER="giaccabugata@gmail.com"        # ← account Gmail mittente
GMAIL_APP_PASSWORD="zytg gsty ifvn wlrn"  # ← App Password Gmail (vedi istruzioni)

# ── Colori ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
log()   { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
title() { echo -e "\n${BOLD}${CYAN}══ $1 ══${NC}\n"; }
die()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }


# ==============================================================================
# STEP 1 — Pacchetti
# ==============================================================================
install_packages() {
    title "Installazione pacchetti"
    apt-get update -qq
    apt-get install -y --no-install-recommends \
        xorg openbox xdotool unclutter x11-xserver-utils xinit \
        curl unzip tar ca-certificates wget lsb-release \
        procps psmisc logrotate systemd-timesyncd \
        openssh-server \
        msmtp msmtp-mta mailutils \
        net-tools iproute2

    for svc in bluetooth cups ModemManager snapd apt-daily apt-daily-upgrade; do
        systemctl disable --now "$svc" 2>/dev/null || true
    done
    ok "Pacchetti installati."
}

# ==============================================================================
# STEP 2 — Utente kiosk
# ==============================================================================
setup_kiosk_user() {
    title "Configurazione utente kiosk"
    if ! id "$KIOSK_USER" &>/dev/null; then
        useradd -m -s /bin/bash "$KIOSK_USER"
        passwd -d "$KIOSK_USER"
        ok "Utente '${KIOSK_USER}' creato."
    else
        warn "Utente '${KIOSK_USER}' già esistente."
    fi
    usermod -aG video,audio,input,render "$KIOSK_USER" 2>/dev/null || true

    if [ ! -f "$DEPLOY_SCRIPT" ]; then
        if   [ -f "$(dirname "$0")/deploy.sh" ]; then cp "$(dirname "$0")/deploy.sh" "$DEPLOY_SCRIPT"
        elif [ -f "./deploy.sh" ];               then cp ./deploy.sh "$DEPLOY_SCRIPT"
        else warn "deploy.sh non trovato — copialo manualmente in ${DEPLOY_SCRIPT}"; fi
    fi
    [ -f "$DEPLOY_SCRIPT" ] && chmod +x "$DEPLOY_SCRIPT" \
        && chown "${KIOSK_USER}:${KIOSK_USER}" "$DEPLOY_SCRIPT"
    ok "Utente kiosk configurato."
}

# ==============================================================================
# STEP 3 — SSH
# ==============================================================================
setup_ssh() {
    title "Configurazione SSH"

    # Abilita e avvia SSH
    systemctl enable ssh
    systemctl start ssh

    # Configurazione sicura per kiosk:
    # - porta standard 22
    # - accesso root disabilitato
    # - autenticazione con password abilitata (per semplicità su LAN)
    cat > /etc/ssh/sshd_config.d/kiosk.conf << 'SSHCONF'
PermitRootLogin no
PasswordAuthentication yes
X11Forwarding no
MaxAuthTries 3
LoginGraceTime 30
AllowUsers kiosk vboxuser ubuntu admin
SSHCONF

    # Assicura che l'utente kiosk abbia una password per SSH
    # Imposta password "kiosk123" — cambiala dopo il primo accesso!
    echo "${KIOSK_USER}:kiosk123" | chpasswd
    warn "Password SSH utente kiosk impostata a 'kiosk123' — CAMBIALA dopo il primo accesso!"

    systemctl restart ssh
    ok "SSH abilitato sulla porta 22."
}

# ==============================================================================
# STEP 4 — msmtp (email via Gmail SMTP)
# ==============================================================================
setup_email() {
    title "Configurazione email (Gmail SMTP)"

    # Configurazione msmtp globale
    cat > /etc/msmtprc << MSMTP
defaults
auth           on
tls            on
tls_trust_file /etc/ssl/certs/ca-certificates.crt
logfile        /var/log/totem-kiosk/msmtp.log

account        gmail
host           smtp.gmail.com
port           587
from           ${GMAIL_USER}
user           ${GMAIL_USER}
password       ${GMAIL_APP_PASSWORD}

account default : gmail
MSMTP

    chmod 600 /etc/msmtprc
    ok "msmtp configurato con Gmail SMTP."
}

# ==============================================================================
# STEP 5 — Script notifica email all'avvio
# ==============================================================================
setup_email_notifier() {
    title "Notifica email all'avvio"

    cat > /usr/local/bin/totem-notify.sh << NOTIFY
#!/bin/bash
# Invia email con IP e info SSH all'avvio del sistema
LOG="/var/log/totem-kiosk/notify.log"

sleep 15  # aspetta che la rete sia pronta

HOSTNAME=\$(hostname)
LOCAL_IP=\$(ip route get 1 2>/dev/null | grep -oP 'src \K[^ ]+' | head -1)
PUBLIC_IP=\$(curl -sf --max-time 5 https://api.ipify.org 2>/dev/null || echo "non disponibile")
TIMESTAMP=\$(date '+%Y-%m-%d %H:%M:%S')
OS=\$(lsb_release -ds 2>/dev/null || cat /etc/os-release | grep PRETTY_NAME | cut -d'"' -f2)
UPTIME=\$(uptime -p)

SUBJECT="[Totem Kiosk] Avviato: \${HOSTNAME} — \${TIMESTAMP}"

BODY="Totem Kiosk avviato con successo.

=== INFORMAZIONI ACCESSO SSH ===

  Host:        \${HOSTNAME}
  IP locale:   \${LOCAL_IP}
  IP pubblico: \${PUBLIC_IP}
  Porta SSH:   22

  Comando SSH:
  ssh kiosk@\${LOCAL_IP}

  (se sei sulla stessa rete)

=== SISTEMA ===

  OS:      \${OS}
  Uptime:  \${UPTIME}
  Data:    \${TIMESTAMP}

=== LOG UTILI ===

  tail -f /var/log/totem-kiosk/watchdog.log
  tail -f /var/log/totem-kiosk/app.log

=== COMANDI RAPIDI ===

  Aggiorna app:   sudo -u kiosk bash ~/deploy.sh --update
  Riavvia kiosk:  sudo reboot
  Stop kiosk:     sudo systemctl stop totem-x11-monitor

---
Questo messaggio è stato inviato automaticamente da setup-kiosk.sh
"

echo "\${TIMESTAMP} Invio notifica a ${NOTIFY_EMAIL}..." >> "\$LOG"

echo "\$BODY" | msmtp \
    --from="${GMAIL_USER}" \
    -a gmail \
    "${NOTIFY_EMAIL}" \
    -S "subject:\${SUBJECT}" 2>> "\$LOG" \
    || echo "\${TIMESTAMP} Invio email fallito." >> "\$LOG"

echo "\${TIMESTAMP} Notifica inviata." >> "\$LOG"
NOTIFY

    chmod +x /usr/local/bin/totem-notify.sh

    # Servizio systemd che manda l'email ad ogni boot
    cat > /etc/systemd/system/totem-notify.service << 'SERVICE'
[Unit]
Description=Totem Kiosk — Notifica email avvio
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/totem-notify.sh
RemainAfterExit=no
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

    systemctl daemon-reload
    systemctl enable totem-notify
    ok "Notifica email configurata (invia ad ogni boot)."
}

# ==============================================================================
# STEP 6 — Auto-login TTY1
# ==============================================================================
setup_autologin() {
    title "Configurazione auto-login"
    mkdir -p /etc/systemd/system/getty@tty1.service.d
    cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF
    for dm in gdm gdm3 sddm lightdm; do systemctl disable "$dm" 2>/dev/null || true; done
    systemctl daemon-reload
    ok "Auto-login TTY1 configurato."
}

# ==============================================================================
# STEP 7 — X11 + Openbox
# ==============================================================================
setup_xinit() {
    title "Configurazione X11 + Openbox"

    cat > "${KIOSK_HOME}/.bashrc" << 'BASHRC'
if [ -z "$DISPLAY" ] && [ "$(tty)" = "/dev/tty1" ]; then
    exec startx -- -nocursor 2>/var/log/totem-kiosk/xorg.log
fi
BASHRC

    cat > "${KIOSK_HOME}/.xinitrc" << 'XINITRC'
#!/bin/bash
xset s off
xset s noblank
xset -dpms
unclutter -idle 1 -root &
openbox &
sleep 1
exec /usr/local/bin/totem-watchdog.sh
XINITRC

    mkdir -p "${KIOSK_HOME}/.config/openbox"
    cat > "${KIOSK_HOME}/.config/openbox/rc.xml" << 'OBCONF'
<?xml version="1.0" encoding="UTF-8"?>
<openbox_config>
  <focus><focusNew>yes</focusNew><followMouse>no</followMouse></focus>
  <desktops><number>1</number><firstdesk>1</firstdesk><names><name>Kiosk</name></names></desktops>
  <applications>
    <application class="*">
      <fullscreen>yes</fullscreen>
      <decor>no</decor>
      <maximized>yes</maximized>
    </application>
  </applications>
</openbox_config>
OBCONF

    chown -R "${KIOSK_USER}:${KIOSK_USER}" \
        "${KIOSK_HOME}/.bashrc" \
        "${KIOSK_HOME}/.xinitrc" \
        "${KIOSK_HOME}/.config"
    chmod +x "${KIOSK_HOME}/.xinitrc"
    ok "X11 e Openbox configurati."
}

# ==============================================================================
# STEP 8 — Watchdog app
# ==============================================================================
setup_watchdog() {
    title "Configurazione watchdog"
    mkdir -p "$LOG_DIR"
    chown "${KIOSK_USER}:${KIOSK_USER}" "$LOG_DIR"

    cat > /usr/local/bin/totem-watchdog.sh << WATCHDOG
#!/bin/bash
LOG="${LOG_DIR}/watchdog.log"
APP_LOG="${LOG_DIR}/app.log"
CRASH_COUNT=0
LAST_START=0

log()  { echo "\$(date '+%Y-%m-%d %H:%M:%S') [WATCHDOG] \$1" | tee -a "\$LOG"; }
crash(){ echo "\$(date '+%Y-%m-%d %H:%M:%S') [CRASH]    \$1" | tee -a "\$LOG"; }

log "Watchdog avviato. PID=\$\$"

# Solo al primo avvio (start.sh non ancora creato)
if [ ! -f "${START_SCRIPT}" ]; then
    log "Prima installazione — eseguo deploy.sh..."
    bash "${DEPLOY_SCRIPT}" >> "\$APP_LOG" 2>&1 || true
else
    log "App già installata — avvio diretto."
fi

while true; do
    NOW=\$(date +%s)

    if [ \$LAST_START -gt 0 ]; then
        UPTIME=\$(( NOW - LAST_START ))
        if [ \$UPTIME -ge ${CRASH_RESET_AFTER} ] && [ \$CRASH_COUNT -gt 0 ]; then
            log "Sessione stabile (\${UPTIME}s). Reset crash counter."
            CRASH_COUNT=0
        fi
    fi

    if [ \$CRASH_COUNT -ge ${CRASH_MAX_RETRIES} ]; then
        crash "Limite crash raggiunto. Reboot tra 10s..."
        sleep 10
        sudo /sbin/reboot
        exit 1
    fi

    log "Avvio app (tentativo \$(( CRASH_COUNT + 1 )))..."
    LAST_START=\$(date +%s)

    if [ -f "${START_SCRIPT}" ]; then
        bash "${START_SCRIPT}" >> "\$APP_LOG" 2>&1
        EXIT_CODE=\$?
    else
        bash "${DEPLOY_SCRIPT}" --run >> "\$APP_LOG" 2>&1
        EXIT_CODE=\$?
    fi

    DURATION=\$(( \$(date +%s) - LAST_START ))

    if [ \$EXIT_CODE -eq 0 ]; then
        log "App terminata normalmente (durata: \${DURATION}s). Riavvio..."
    else
        CRASH_COUNT=\$(( CRASH_COUNT + 1 ))
        crash "Crash exit=\${EXIT_CODE} durata=\${DURATION}s — #\${CRASH_COUNT}/${CRASH_MAX_RETRIES}"
        pkill -f "com.example.App" 2>/dev/null || true
        pkill -f "demo-1.jar"      2>/dev/null || true
        WAIT=\$(( 3 * CRASH_COUNT ))
        [ \$WAIT -gt 30 ] && WAIT=30
        log "Retry tra \${WAIT}s..."
        sleep "\$WAIT"
    fi
done
WATCHDOG

    chmod +x /usr/local/bin/totem-watchdog.sh
    ok "Watchdog installato."
}

# ==============================================================================
# STEP 9 — Sudoers
# ==============================================================================
setup_sudoers() {
    title "Configurazione sudoers"
    cat > /etc/sudoers.d/kiosk << EOF
${KIOSK_USER} ALL=(ALL) NOPASSWD: /sbin/reboot, /sbin/shutdown, /sbin/poweroff
EOF
    chmod 440 /etc/sudoers.d/kiosk
    ok "Sudoers configurato."
}

# ==============================================================================
# STEP 10 — Logrotate
# ==============================================================================
setup_logrotate() {
    cat > /etc/logrotate.d/totem-kiosk << EOF
${LOG_DIR}/*.log {
    daily
    missingok
    rotate 7
    compress
    delaycompress
    notifempty
    copytruncate
}
EOF
    ok "Logrotate configurato."
}

# ==============================================================================
# STEP 11 — Monitor X11 systemd
# ==============================================================================
setup_systemd_watchdog() {
    title "Monitor X11 systemd"
    cat > /etc/systemd/system/totem-x11-monitor.service << EOF
[Unit]
Description=Totem Kiosk X11 Monitor
After=multi-user.target
[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/totem-x11-monitor.sh
Restart=always
RestartSec=10
[Install]
WantedBy=multi-user.target
EOF

    cat > /usr/local/bin/totem-x11-monitor.sh << 'MONITOR'
#!/bin/bash
while true; do
    sleep 15
    if ! pgrep -x Xorg > /dev/null && ! pgrep -x X > /dev/null; then
        echo "$(date) [MONITOR] X11 morto — riavvio getty@tty1" \
            >> /var/log/totem-kiosk/monitor.log
        pkill -f "agetty.*tty1" 2>/dev/null || true
        sleep 2
        systemctl restart "getty@tty1"
    fi
done
MONITOR

    chmod +x /usr/local/bin/totem-x11-monitor.sh
    systemctl daemon-reload
    systemctl enable totem-x11-monitor
    ok "Monitor X11 configurato."
}

# ==============================================================================
# STEP 12 — Ottimizzazioni
# ==============================================================================
setup_optimizations() {
    title "Ottimizzazioni sistema"
    cat >> /etc/sysctl.d/99-kiosk.conf << 'SYSCTL'
vm.swappiness=10
vm.dirty_ratio=15
kernel.printk=3 4 1 3
SYSCTL
    if [ -f /etc/default/grub ]; then
        sed -i 's/GRUB_TIMEOUT=.*/GRUB_TIMEOUT=1/' /etc/default/grub
        update-grub 2>/dev/null || true
    fi
    systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
    if [ -f /etc/systemd/system.conf ]; then
        sed -i 's/#RuntimeWatchdogSec=.*/RuntimeWatchdogSec=30/' /etc/systemd/system.conf
        sed -i 's/#RebootWatchdogSec=.*/RebootWatchdogSec=10min/' /etc/systemd/system.conf
    fi
    ok "Ottimizzazioni applicate."
}

# ==============================================================================
# STEP 13 — Test email
# ==============================================================================
send_test_email() {
    title "Test invio email"
    LOCAL_IP=$(ip route get 1 2>/dev/null | grep -oP 'src \K[^ ]+' | head -1 || echo "sconosciuto")

    echo "Setup Totem Kiosk completato su $(hostname).

Accesso SSH:
  ssh kiosk@${LOCAL_IP}
  Password: kiosk123

Cambia la password appena possibile:
  passwd

Log: /var/log/totem-kiosk/
" | msmtp \
        --from="${GMAIL_USER}" \
        -a gmail \
        "${NOTIFY_EMAIL}" \
        -S "subject:[Totem Kiosk] Setup completato — $(hostname)" \
    && ok "Email di test inviata a ${NOTIFY_EMAIL}." \
    || warn "Invio email fallito. Controlla GMAIL_USER e GMAIL_APP_PASSWORD nello script."
}

# ==============================================================================
# REPORT
# ==============================================================================
print_summary() {
    LOCAL_IP=$(ip route get 1 2>/dev/null | grep -oP 'src \K[^ ]+' | head -1 || echo "sconosciuto")
    echo ""
    echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${GREEN}║   Setup Kiosk completato!                        ║${NC}"
    echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  ${CYAN}SSH:${NC}  ssh kiosk@${LOCAL_IP}  (password: kiosk123)"
    echo -e "  ${CYAN}Log:${NC}  tail -f ${LOG_DIR}/watchdog.log"
    echo -e "  ${RED}⚠️   Cambia la password SSH dopo il primo accesso: passwd${NC}"
    echo ""
    echo -e "  ${YELLOW}Istruzioni Gmail App Password:${NC}"
    echo -e "  1. Vai su myaccount.google.com → Sicurezza"
    echo -e "  2. Abilita verifica in 2 passaggi"
    echo -e "  3. Cerca 'App Password' → crea una per 'Mail'"
    echo -e "  4. Incolla la password a 16 caratteri in GMAIL_APP_PASSWORD"
    echo ""
    echo -e "  ${RED}Riavvia per attivare il kiosk:${NC} ${BOLD}sudo reboot${NC}"
    echo ""
}

# ==============================================================================
# MAIN — wrappato in funzione per il case statement
# ==============================================================================
run_setup() {
    echo ""
    echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${CYAN}║   Totem Kiosk — Setup Sistema Linux              ║${NC}"
    echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════╝${NC}"

    install_packages
    setup_kiosk_user
    setup_ssh
    setup_email
    setup_email_notifier
    setup_autologin
    setup_xinit
    setup_watchdog
    setup_sudoers
    setup_logrotate
    setup_systemd_watchdog
    setup_optimizations
    send_test_email
    print_summary
}

# ==============================================================================
# DISABLE — disabilita tutto il kiosk (auto-login, watchdog, SSH notify, X11)
# ==============================================================================
disable_kiosk() {
    echo ""
    echo -e "${BOLD}${RED}══ Disabilitazione modalità kiosk ══${NC}"
    echo ""

    # 1. Auto-login TTY1
    if [ -f /etc/systemd/system/getty@tty1.service.d/autologin.conf ]; then
        rm -f /etc/systemd/system/getty@tty1.service.d/autologin.conf
        rmdir /etc/systemd/system/getty@tty1.service.d 2>/dev/null || true
        ok "Auto-login TTY1 disabilitato."
    fi

    # 2. Servizi systemd kiosk
    for svc in totem-x11-monitor totem-notify; do
        if systemctl is-enabled "$svc" &>/dev/null; then
            systemctl disable --now "$svc" 2>/dev/null || true
            ok "Servizio ${svc} disabilitato."
        fi
    done

    # 3. Watchdog e monitor (rimuove gli script)
    for f in /usr/local/bin/totem-watchdog.sh \
              /usr/local/bin/totem-x11-monitor.sh \
              /usr/local/bin/totem-notify.sh; do
        [ -f "$f" ] && rm -f "$f" && ok "Rimosso: $f"
    done

    # 4. File unit systemd
    for f in /etc/systemd/system/totem-x11-monitor.service \
              /etc/systemd/system/totem-notify.service; do
        [ -f "$f" ] && rm -f "$f" && ok "Rimosso: $f"
    done

    # 5. .bashrc del kiosk (blocco startx)
    if [ -f "${KIOSK_HOME}/.bashrc" ]; then
        # Rimuove solo il blocco kiosk, lascia intatto il resto
        sed -i '/# Kiosk auto-start X11/,/^fi$/d' "${KIOSK_HOME}/.bashrc"
        ok "Auto-start X11 rimosso da .bashrc."
    fi

    # 6. Cron aggiornamento notturno
    rm -f /etc/cron.d/totem-kiosk-update
    ok "Cron aggiornamento rimosso."

    # 7. Sudoers kiosk
    rm -f /etc/sudoers.d/kiosk
    ok "Sudoers kiosk rimosso."

    # 8. Ripristina sleep/suspend
    systemctl unmask sleep.target suspend.target hibernate.target hybrid-sleep.target \
        2>/dev/null || true
    ok "Sleep/suspend riabilitati."

    # 9. Ripristina GRUB timeout a 5 secondi
    if [ -f /etc/default/grub ]; then
        sed -i 's/GRUB_TIMEOUT=.*/GRUB_TIMEOUT=5/' /etc/default/grub
        update-grub 2>/dev/null || true
        ok "GRUB timeout ripristinato a 5s."
    fi

    # 10. Ricarica systemd
    systemctl daemon-reload
    systemctl reset-failed 2>/dev/null || true

    echo ""
    echo -e "${BOLD}${GREEN}Kiosk disabilitato.${NC}"
    echo -e "SSH e i file in ${LOG_DIR}/ sono stati lasciati intatti."
    echo -e "Riavvia per applicare: ${BOLD}sudo reboot${NC}"
    echo ""
}

# ==============================================================================
# ENTRY POINT — deve stare ALLA FINE, dopo tutte le definizioni
# ==============================================================================
case "${1:-}" in

    --help|-h)
        echo ""
        echo "Uso: sudo ./setup-kiosk.sh [opzione]"
        echo ""
        echo "  (nessuna)   Setup completo modalità kiosk"
        echo "  --disable   Disabilita tutto: auto-login, watchdog, X11, notify"
        echo "  --help      Mostra questo help"
        echo ""
        exit 0
        ;;

    --disable)
        [ "$EUID" -eq 0 ] || { echo "Esegui come root: sudo ./setup-kiosk.sh --disable"; exit 1; }
        disable_kiosk
        exit 0
        ;;

    "")
        [ "$EUID" -eq 0 ] || { echo "Esegui come root: sudo ./setup-kiosk.sh"; exit 1; }
        echo ""
        echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════╗${NC}"
        echo -e "${BOLD}${CYAN}║   Totem Kiosk — Setup Sistema Linux              ║${NC}"
        echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════╝${NC}"
        install_packages
        setup_kiosk_user
        setup_ssh
        setup_email
        setup_email_notifier
        setup_autologin
        setup_xinit
        setup_watchdog
        setup_sudoers
        setup_logrotate
        setup_systemd_watchdog
        setup_optimizations
        send_test_email
        print_summary
        exit 0
        ;;

    *)
        echo "Opzione sconosciuta: $1"
        echo "Usa: sudo ./setup-kiosk.sh --help"
        exit 1
        ;;
esac