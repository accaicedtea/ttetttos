#!/bin/bash
# create-kiosk-user.sh
# Crea un utente Linux dedicato e una SESSIONE DI SISTEMA dedicata 
# per ignorare totalmente GNOME fin dalla schermata di login.

if [ "$EUID" -ne 0 ]; then
  echo -e "\033[1;31mErrore: Esegui questo script come amministratore (sudo)\033[0m"
  echo "Usa: sudo ./create-kiosk-user.sh"
  exit 1
fi

TEST_USER="totem-test"

# 1. Creazione utente
if id "$TEST_USER" &>/dev/null; then
    echo -e "\033[1;33mL'utente $TEST_USER esiste già. Procedo con l'AGGIORNAMENTO del codice e delle impostazioni...\033[0m"
else
    echo "Creazione del nuovo utente Linux '$TEST_USER'..."
    useradd -m -s /bin/bash "$TEST_USER"
    echo "$TEST_USER:kiosk" | chpasswd
    echo "Password impostata su: kiosk"
fi

# 2. Clona il progetto nella home del nuovo utente
echo "Copia del progetto pre-compilato..."
rm -rf /home/$TEST_USER/demo
cp -r /home/acca/websites/TOTTEM/demo /home/$TEST_USER/
chown -R $TEST_USER:$TEST_USER /home/$TEST_USER/

# 3. Creiamo una "Sessione Grafica" ufficiale a livello di Linux (come GNOME, ma per il Totem)
echo "Creazione Sessione Custom X11..."

# Questo file dice a Ubuntu "Esiste un nuovo Desktop Enviroment chiamato Totem Kiosk"
cat << 'SESSION_EOF' > /usr/share/xsessions/totem-kiosk.desktop
[Desktop Entry]
Name=Totem Kiosk
Comment=Esegue solo l'app Java in isolamento senza GNOME
Exec=dbus-launch --exit-with-session /home/totem-test/start-kiosk.sh
Type=Application
SESSION_EOF

# 4. Creiamo lo script che GDM eseguirà quando fai login
cat << 'SCRIPT_EOF' > /home/$TEST_USER/start-kiosk.sh
#!/bin/bash
# Niente blocchi schermo
xset s off
xset s noblank
xset -dpms

# ========================================================
# 1. NASCONDI IL CURSORE DEL MOUSE
# Usa 'unclutter' (già installato da setup-kiosk.sh)
# Mette un timeout di 0.1s: non si vedrà mai.
unclutter -idle 0.1 -root &

# ========================================================
# 2. ROTAZIONE DELLO SCHERMO
# In Openbox non c'è GNOME a ruotare in automatico lo schermo.
# Impostiamo lo schermo del tuo portatile/totem (eDP-1) in verticale!
# Cambia 'left' con 'right', 'inverted' o 'normal' se serve ruotare al contrario.
xrandr --output eDP-1 --rotate left
# ========================================================

# Avvia Openbox in background (fornisce solo la cornice di base, ma nessuna taskbar)
openbox &

# Entra nella cartella e avvia il programma
cd /home/totem-test/demo
java --module-path target/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
     -cp "target/demo-1.jar:target/lib/*" \
     com.app.App

# Qundo chiudi l'app Java, abbatte Openbox e fa chiudere la sessione (tornando al Login)
killall openbox
SCRIPT_EOF

chmod +x /home/$TEST_USER/start-kiosk.sh
chown $TEST_USER:$TEST_USER /home/$TEST_USER/start-kiosk.sh

# 5. Incolliamo brutalmente ad Ubuntu che per QUESTO ACCOUNT, di default, deve partire QUELLA sessione.
mkdir -p /var/lib/AccountsService/users
cat << 'ACC_EOF' > /var/lib/AccountsService/users/$TEST_USER
[User]
Session=totem-kiosk
XSession=totem-kiosk
SystemAccount=false
ACC_EOF
chmod 644 /var/lib/AccountsService/users/$TEST_USER

echo -e "\n\033[1;32m=========================================================================\033[0m"
echo -e "\033[1;32m   SESSIONE TOTALMENTE ISOLATA CONFIGURATA!\033[0m"
echo -e "\033[1;32m=========================================================================\033[0m"
echo -e "\n1. Adesso fai Termina Sessione (Logout)."
echo -e "2. Clicca su \033[1;36mtotem-test\033[0m."
echo -e "3. \033[1;33mNON C'È BISOGNO DI CLICCARE L'INGRANAGGIO\033[0m, il sistema sa già che deve caricare 'Totem Kiosk' al posto di GNOME."
echo -e "4. Inserisci 'kiosk' come password e premi Invio."
