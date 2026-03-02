#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  kiosk-start.sh  —  Lancia l'app in modalità kiosk con Cage
#                     Cage è un compositor Wayland dedicato al kiosk:
#                     nessun DE, nessun pannello, nessuna gesture.
#  Per uscire:  Alt+F4  oppure  Ctrl+C dal terminale
# ─────────────────────────────────────────────────────────────────────
set -e
APP_DIR="$(dirname "$0")"

echo "[kiosk] Avvio in modalità kiosk con Cage..."
cd "$APP_DIR"

# Cage lancia l'app come unico client Wayland — sempre fullscreen per design.
# Senza -s: Ctrl+Alt+Fn (cambio TTY) è già BLOCCATO per default.
#   -d  non disegna decorazioni client-side (titolebar ecc.)
# Quando l'app si chiude, Cage termina e si torna al DE normale.
cage -d -- mvn javafx:run
