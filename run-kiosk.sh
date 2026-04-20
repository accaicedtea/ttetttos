#!/bin/bash
export APP_SCALE="1.8"
LOG_FILE="/home/acca/websites/TOTTEM/demo/totem_kiosk.log"
APP_DIR="/home/acca/websites/TOTTEM/demo"

{
    echo "=== AVVIO KIOSK [$(date)] ==="
    
    # Disabilita screensaver e power management
    xset s off 2>/dev/null || true
    xset s noblank 2>/dev/null || true
    xset -dpms 2>/dev/null || true
    
    # Nasconde il cursore
    unclutter -idle 0.1 -root &
    
    cd "$APP_DIR"
    java -Dprism.forceGPU=true \
         -Dglass.gtk.uiScale=${APP_SCALE} \
         --module-path target/lib \
         --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
         -cp "target/demo-1.jar:target/lib/*" \
         com.app.App
} >> "$LOG_FILE" 2>&1
