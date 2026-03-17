#!/bin/bash
# ==============================================================================
#   Totem Kiosk — Setup Sistema Linux
#   Configura Ubuntu/Debian in modalità kiosk completa:
#   auto-login → auto-start → watchdog → recovery automatico
# ==============================================================================

set -euo pipefail

# ── Configurazione ─────────────────────────────────────────────────────────────
KIOSK_USER="kiosk"
KIOSK_HOME="/home/${KIOSK_USER}"
APP_NAME="totem-kiosk"
DEPLOY_SCRIPT="${KIOSK_HOME}/deploy.sh"
START_SCRIPT="${KIOSK_HOME}/.totem-kiosk/start.sh"
LOG_DIR="/var/log/${APP_NAME}"
WATCHDOG_INTERVAL=5      # secondi tra un check e l'altro del watchdog
CRASH_MAX_RETRIES=5      # tentativi prima di reboot
CRASH_RESET_AFTER=300    # secondi senza crash → reset contatore

# ── Colori ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

log()   { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
title() { echo -e "\n${BOLD}${CYAN}══ $1 ══${NC}\n"; }
die()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Controllo root ─────────────────────────────────────────────────────────────
[ "$EUID" -eq 0 ] || die "Esegui come root: sudo ./setup-kiosk.sh"

# ==============================================================================
# STEP 1 — Pacchetti di sistema
# ==============================================================================
install_packages() {
    title "Installazione pacchetti"

    apt-get update -qq

    # Display server minimale (Xorg senza desktop environment)
    apt-get install -y --no-install-recommends \
        xorg \
        openbox \
        xdotool \
        unclutter \
        x11-xserver-utils \
        xinit \
        curl \
        unzip \
        tar \
        ca-certificates \
        wget \
        lsb-release \
        procps \
        psmisc \
        inotify-tools \
        at \
        logrotate \
        systemd-timesyncd

    # Disabilita servizi non necessari per kiosk
    local services_to_disable=(
        bluetooth
        cups
        ModemManager
        NetworkManager-wait-online
        snapd
        apt-daily
        apt-daily-upgrade
        man-db
        motd-news
    )
    for svc in "${services_to_disable[@]}"; do
        systemctl disable --now "$svc" 2>/dev/null || true
    done

    ok "Pacchetti installati."
}

# ==============================================================================
# STEP 2 — Utente kiosk
# ==============================================================================
setup_kiosk_user() {
    title "Configurazione utente kiosk"

    if id "$KIOSK_USER" &>/dev/null; then
        warn "Utente '${KIOSK_USER}' già esistente."
    else
        useradd -m -s /bin/bash "$KIOSK_USER"
        # Password disabilitata — login solo automatico
        passwd -d "$KIOSK_USER"
        ok "Utente '${KIOSK_USER}' creato."
    fi

    # Aggiungi ai gruppi necessari per X11 e audio
    usermod -aG video,audio,input,render "$KIOSK_USER" 2>/dev/null || true

    # Copia deploy.sh nella home kiosk se non già presente
    if [ ! -f "$DEPLOY_SCRIPT" ]; then
        if [ -f "$(dirname "$0")/deploy.sh" ]; then
            cp "$(dirname "$0")/deploy.sh" "$DEPLOY_SCRIPT"
        elif [ -f "./deploy.sh" ]; then
            cp ./deploy.sh "$DEPLOY_SCRIPT"
        else
            warn "deploy.sh non trovato nella stessa directory. Copialo manualmente in ${DEPLOY_SCRIPT}"
        fi
    fi
    [ -f "$DEPLOY_SCRIPT" ] && chmod +x "$DEPLOY_SCRIPT" && chown "${KIOSK_USER}:${KIOSK_USER}" "$DEPLOY_SCRIPT"

    ok "Utente kiosk configurato."
}

# ==============================================================================
# STEP 3 — Auto-login TTY1 senza display manager
# ==============================================================================
setup_autologin() {
    title "Configurazione auto-login"

    # Override getty per TTY1 → auto-login utente kiosk
    mkdir -p /etc/systemd/system/getty@tty1.service.d
    cat > /etc/systemd/system/getty@tty1.service.d/autologin.conf << EOF
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin ${KIOSK_USER} --noclear %I \$TERM
Type=idle
EOF

    # Disabilita display manager se presente (gdm, sddm, lightdm)
    for dm in gdm gdm3 sddm lightdm; do
        systemctl disable "$dm" 2>/dev/null || true
    done

    systemctl daemon-reload
    ok "Auto-login su TTY1 configurato per utente '${KIOSK_USER}'."
}

# ==============================================================================
# STEP 4 — .bashrc: avvia X11 automaticamente al login
# ==============================================================================
setup_xinit() {
    title "Configurazione avvio automatico X11"

    # .bashrc del kiosk: se siamo su TTY1 e non c'è già un display, avvia X
    cat > "${KIOSK_HOME}/.bashrc" << 'BASHRC'
# Kiosk auto-start X11 su TTY1
if [ -z "$DISPLAY" ] && [ "$(tty)" = "/dev/tty1" ]; then
    exec startx -- -nocursor 2>/var/log/totem-kiosk/xorg.log
fi
BASHRC

    # .xinitrc: avvia openbox + app kiosk
    cat > "${KIOSK_HOME}/.xinitrc" << XINITRC
#!/bin/bash
# Kiosk X11 session

# Schermo sempre acceso, nessuno screensaver
xset s off
xset s noblank
xset -dpms

# Nasconde il cursore dopo 1 secondo di inattività
unclutter -idle 1 -root &

# Openbox come WM minimale (nessuna taskbar, nessun desktop)
openbox &

# Aspetta che Openbox sia pronto
sleep 1

# Avvia il watchdog che gestisce l'app (crash recovery, reboot, ecc.)
exec /usr/local/bin/totem-watchdog.sh
XINITRC

    # Configurazione Openbox: nessuna decorazione finestre, fullscreen forzato
    mkdir -p "${KIOSK_HOME}/.config/openbox"
    cat > "${KIOSK_HOME}/.config/openbox/rc.xml" << 'OBCONF'
<?xml version="1.0" encoding="UTF-8"?>
<openbox_config>
  <resistance><strength>10</strength><screen_edge_strength>20</screen_edge_strength></resistance>
  <focus><focusNew>yes</focusNew><followMouse>no</followMouse></focus>
  <placement><policy>Smart</policy></placement>
  <theme><name>Clearlooks</name><titleLayout>NLIMC</titleLayout></theme>
  <desktops><number>1</number><firstdesk>1</firstdesk><names><name>Kiosk</name></names></desktops>
  <keyboard>
    <!-- Blocca combinazioni di tasto comuni per kiosk pubblico -->
    <keybind key="A-F4"><action name="Close"/></keybind>
  </keyboard>
  <applications>
    <!-- Forza tutte le finestre fullscreen senza decorazioni -->
    <application class="*">
      <fullscreen>yes</fullscreen>
      <decor>no</decor>
      <maximized>yes</maximized>
    </application>
  </applications>
</openbox_config>
OBCONF

    chown -R "${KIOSK_USER}:${KIOSK_USER}" "${KIOSK_HOME}/.bashrc" \
        "${KIOSK_HOME}/.xinitrc" "${KIOSK_HOME}/.config"
    chmod +x "${KIOSK_HOME}/.xinitrc"

    ok "X11 e Openbox configurati."
}

# ==============================================================================
# STEP 5 — Watchdog: avvia app, gestisce crash e reboot automatico
# ==============================================================================
setup_watchdog() {
    title "Configurazione watchdog"

    mkdir -p "$LOG_DIR"
    chown "${KIOSK_USER}:${KIOSK_USER}" "$LOG_DIR"

    cat > /usr/local/bin/totem-watchdog.sh << WATCHDOG
#!/bin/bash
# ==============================================================================
#   Totem Kiosk — Watchdog
#   Avvia l'app, rileva crash, gestisce recovery automatico
# ==============================================================================

LOG="${LOG_DIR}/watchdog.log"
APP_LOG="${LOG_DIR}/app.log"
CRASH_COUNT=0
LAST_START=0

log()  { echo "\$(date '+%Y-%m-%d %H:%M:%S') [WATCHDOG] \$1" | tee -a "\$LOG"; }
crash(){ echo "\$(date '+%Y-%m-%d %H:%M:%S') [CRASH]    \$1" | tee -a "\$LOG"; }

log "Watchdog avviato. PID=\$\$"

# Prima esecuzione: scarica/aggiorna l'app via deploy.sh
if [ -f "${DEPLOY_SCRIPT}" ]; then
    log "Eseguo deploy.sh (download + setup)..."
    bash "${DEPLOY_SCRIPT}" >> "\$APP_LOG" 2>&1 || true
fi

# Loop principale
while true; do
    NOW=\$(date +%s)

    # Reset contatore crash se l'ultima sessione è durata abbastanza
    if [ \$LAST_START -gt 0 ]; then
        UPTIME=\$(( NOW - LAST_START ))
        if [ \$UPTIME -ge ${CRASH_RESET_AFTER} ]; then
            if [ \$CRASH_COUNT -gt 0 ]; then
                log "Sessione stabile (\${UPTIME}s). Reset contatore crash."
                CRASH_COUNT=0
            fi
        fi
    fi

    # Troppi crash consecutivi → reboot
    if [ \$CRASH_COUNT -ge ${CRASH_MAX_RETRIES} ]; then
        crash "Raggiunto limite crash (\${CRASH_COUNT}). Reboot tra 10s..."
        sleep 10
        sudo /sbin/reboot
        exit 1
    fi

    log "Avvio app (tentativo \$(( CRASH_COUNT + 1 )))..."
    LAST_START=\$(date +%s)

    # Avvia l'app — usa start.sh se disponibile, altrimenti deploy.sh
    if [ -f "${START_SCRIPT}" ]; then
        bash "${START_SCRIPT}" >> "\$APP_LOG" 2>&1
    elif [ -f "${DEPLOY_SCRIPT}" ]; then
        bash "${DEPLOY_SCRIPT}" --run >> "\$APP_LOG" 2>&1
    else
        crash "Nessuno script di avvio trovato. Attendo 30s..."
        sleep 30
        continue
    fi

    EXIT_CODE=\$?
    END_TIME=\$(date +%s)
    DURATION=\$(( END_TIME - LAST_START ))

    if [ \$EXIT_CODE -eq 0 ]; then
        log "App terminata normalmente (durata: \${DURATION}s). Riavvio..."
    else
        CRASH_COUNT=\$(( CRASH_COUNT + 1 ))
        crash "App crashata (exit=\${EXIT_CODE}, durata=\${DURATION}s). Crash #\${CRASH_COUNT}/${CRASH_MAX_RETRIES}."

        # Pulizia processi Java rimasti appesi
        pkill -f "com.example.App" 2>/dev/null || true
        pkill -f "demo-1.jar"       2>/dev/null || true

        # Pausa prima del retry (esponenziale: 3s, 6s, 12s…)
        WAIT=\$(( 3 * CRASH_COUNT ))
        [ \$WAIT -gt 30 ] && WAIT=30
        log "Attendo \${WAIT}s prima del retry..."
        sleep "\$WAIT"
    fi
done
WATCHDOG

    chmod +x /usr/local/bin/totem-watchdog.sh
    ok "Watchdog installato in /usr/local/bin/totem-watchdog.sh"
}

# ==============================================================================
# STEP 6 — sudoers: permette reboot senza password al watchdog
# ==============================================================================
setup_sudoers() {
    title "Configurazione sudoers"

    cat > /etc/sudoers.d/kiosk << EOF
# Kiosk: consente reboot e spegnimento senza password
${KIOSK_USER} ALL=(ALL) NOPASSWD: /sbin/reboot, /sbin/shutdown, /sbin/poweroff
EOF
    chmod 440 /etc/sudoers.d/kiosk
    ok "Sudoers configurato."
}

# ==============================================================================
# STEP 7 — Logrotate: gestione automatica log
# ==============================================================================
setup_logrotate() {
    title "Configurazione logrotate"

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
    ok "Logrotate configurato (7 giorni, compressione)."
}

# ==============================================================================
# STEP 8 — Watchdog di sistema (systemd): riavvia X se muore
# ==============================================================================
setup_systemd_watchdog() {
    title "Configurazione watchdog systemd"

    # Abilita watchdog hardware del kernel
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
# Monitora il processo X11 — se muore, rilancia il login su TTY1
while true; do
    sleep 15
    if ! pgrep -x Xorg > /dev/null && ! pgrep -x X > /dev/null; then
        echo "$(date) [MONITOR] X11 non trovato. Forzo login su TTY1..." \
            >> /var/log/totem-kiosk/monitor.log
        # Uccide eventuale getty bloccato e ne forza uno nuovo
        pkill -f "agetty.*tty1" 2>/dev/null || true
        sleep 2
        systemctl restart "getty@tty1"
    fi
done
MONITOR

    chmod +x /usr/local/bin/totem-x11-monitor.sh
    systemctl daemon-reload
    systemctl enable totem-x11-monitor
    ok "Monitor X11 installato come servizio systemd."
}

# ==============================================================================
# STEP 9 — Ottimizzazioni sistema per kiosk
# ==============================================================================
setup_optimizations() {
    title "Ottimizzazioni sistema"

    # Kernel: riduce swappiness, ottimizza per app interattiva
    cat >> /etc/sysctl.d/99-kiosk.conf << 'SYSCTL'
vm.swappiness=10
vm.dirty_ratio=15
kernel.printk=3 4 1 3
SYSCTL

    # Boot più veloce: riduci timeout GRUB a 1 secondo
    if [ -f /etc/default/grub ]; then
        sed -i 's/GRUB_TIMEOUT=.*/GRUB_TIMEOUT=1/' /etc/default/grub
        sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT=.*/GRUB_CMDLINE_LINUX_DEFAULT="quiet splash"/' /etc/default/grub
        update-grub 2>/dev/null || true
    fi

    # Disabilita sleep/suspend (kiosk deve sempre essere attivo)
    systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
    cat > /etc/systemd/sleep.conf << 'SLEEP'
[Sleep]
AllowSuspend=no
AllowHibernation=no
AllowSuspendThenHibernate=no
AllowHybridSleep=no
SLEEP

    # Watchdog hardware del kernel (reboot automatico se il sistema si blocca)
    if [ -f /etc/systemd/system.conf ]; then
        sed -i 's/#RuntimeWatchdogSec=.*/RuntimeWatchdogSec=30/' /etc/systemd/system.conf
        sed -i 's/#RebootWatchdogSec=.*/RebootWatchdogSec=10min/' /etc/systemd/system.conf
    fi

    ok "Ottimizzazioni applicate."
}

# ==============================================================================
# STEP 10 — Aggiornamento automatico app (opzionale, via cron notturno)
# ==============================================================================
setup_auto_update() {
    title "Aggiornamento automatico (03:00 ogni notte)"

    cat > /etc/cron.d/totem-kiosk-update << EOF
# Aggiorna l'app kiosk ogni notte alle 3:00
0 3 * * * ${KIOSK_USER} bash ${DEPLOY_SCRIPT} --update >> ${LOG_DIR}/update.log 2>&1
EOF

    ok "Cron aggiornamento notturno configurato."
}

# ==============================================================================
# REPORT FINALE
# ==============================================================================
print_summary() {
    echo ""
    echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${GREEN}║   Setup Kiosk completato con successo!           ║${NC}"
    echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  ${CYAN}Utente kiosk:${NC}      ${KIOSK_USER}"
    echo -e "  ${CYAN}Deploy script:${NC}     ${DEPLOY_SCRIPT}"
    echo -e "  ${CYAN}Watchdog:${NC}          /usr/local/bin/totem-watchdog.sh"
    echo -e "  ${CYAN}Monitor X11:${NC}       systemctl status totem-x11-monitor"
    echo -e "  ${CYAN}Log applicazione:${NC}  ${LOG_DIR}/"
    echo ""
    echo -e "  ${YELLOW}Flusso al boot:${NC}"
    echo -e "  systemd → getty@tty1 (auto-login '${KIOSK_USER}')"
    echo -e "  → .bashrc → startx"
    echo -e "  → .xinitrc → openbox + totem-watchdog.sh"
    echo -e "  → watchdog → deploy.sh (primo avvio) → app"
    echo -e "  → crash? → retry (max ${CRASH_MAX_RETRIES}x) → reboot"
    echo ""
    echo -e "  ${YELLOW}Comandi utili:${NC}"
    echo -e "  journalctl -f -u totem-x11-monitor   # log monitor"
    echo -e "  tail -f ${LOG_DIR}/watchdog.log       # log watchdog"
    echo -e "  tail -f ${LOG_DIR}/app.log            # log app"
    echo ""
    echo -e "  ${RED}Riavvia il sistema per attivare il kiosk:${NC}"
    echo -e "  ${BOLD}sudo reboot${NC}"
    echo ""
}

# ==============================================================================
# MAIN
# ==============================================================================
echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║   Totem Kiosk — Setup Sistema Linux              ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════╝${NC}"

install_packages
setup_kiosk_user
setup_autologin
setup_xinit
setup_watchdog
setup_sudoers
setup_logrotate
setup_systemd_watchdog
setup_optimizations
setup_auto_update
print_summary
