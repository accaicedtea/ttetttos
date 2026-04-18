#!/bin/bash
# kiosk-test.sh
# Crea una sessione isolata usando Xephyr (server X11 virtuale) e Openbox
# Nessuna impostazione di GNOME verrà alterata, avrai un monitor "in finestra".

echo -e "\n=============================================="
echo -e "   Avvio TotemOrder in Sandbox (Xephyr)"
echo -e "==============================================\n"

TEST_DISPLAY=":99"

echo "1. Avvio Server grafico isolato (Xephyr)..."
# Crea una finestra che si comporta come un intero monitor indipendente
Xephyr $TEST_DISPLAY -ac -screen 1280x800 -resizeable -title "Totem Kiosk Sandbox" &
XEPHYR_PID=$!

sleep 2 # Attendiamo che il server video sia pronto

echo "2. Avvio Openbox (Window Manager usato in produzione)..."
# Avvia openbox *SOLO* all'interno di quella finestra
DISPLAY=$TEST_DISPLAY openbox &
OPENBOX_PID=$!

echo "3. Compilazione e avvio del progetto Java..."
echo -e "   \033[1;31m► L'app si aprirà in FULLSCREEN dentro la finestra 'Totem Kiosk Sandbox' ◄\033[0m"
echo -e "   \033[1;31m► Nessuna impostazione del tuo PC principale verrà toccata! ◄\033[0m\n"

# Esegue Java e JavaFX costringendoli a disegnare solo dentro la Sandbox
DISPLAY=$TEST_DISPLAY mvn compile javafx:run

echo -e "\nApp chiusa. Pulizia processi sandbox..."
kill $OPENBOX_PID 2>/dev/null
kill $XEPHYR_PID 2>/dev/null

echo -e "\n=============================================="
echo "   Test concluso. Sistema intatto."
echo -e "==============================================\n"
