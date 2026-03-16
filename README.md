# Totem Kiosk — Documentazione completa

Sistema di ordinazione digitale JavaFX per totem touch screen.  
Progettato per girare 24/7 su hardware dedicato con Wayland (Cage).

---

## Indice

1. [Requisiti](#requisiti)
2. [Sviluppo locale](#sviluppo-locale)
3. [Compilazione e build](#compilazione-e-build)
4. [Deploy su macchina reale](#deploy-su-macchina-reale)
5. [Deploy su macchina virtuale (VM)](#deploy-su-macchina-virtuale-vm)
6. [GitHub Actions — Release automatica](#github-actions--release-automatica)
7. [Manutenzione](#manutenzione)
8. [Struttura del progetto](#struttura-del-progetto)
9. [Variabili di configurazione](#variabili-di-configurazione)
10. [Troubleshooting](#troubleshooting)

---

## Requisiti

### Macchina di sviluppo

| Strumento | Versione minima | Note |
|-----------|----------------|------|
| Java JDK  | 17+            | Consigliato OpenJDK 21 |
| Maven     | 3.8+           | `mvn --version` |
| Git       | qualsiasi      | Per clonare e taggare |

```bash
# Verifica installazione
java --version
mvn --version
```

### Macchina target (kiosk / VM)

- Linux qualsiasi (Debian 11+, Ubuntu 20.04+, Fedora 38+, Arch, openSUSE)
- Java 17+ (installato automaticamente da setup-kiosk.sh)
- Connessione internet per il primo setup
- **NON** serve un desktop environment

---

## Sviluppo locale

### 1. Clona il repo

```bash
git clone https://github.com/accaicedtea/ttetttos.git
cd ttetttos
```

### 2. Avvia in modalità sviluppo

```bash
mvn javafx:run
```

L'app si avvia in modalità finestra normale (non fullscreen).  
Il cursore è visibile, ESC non è bloccato.

### 3. Variabili d'ambiente utili in sviluppo

```bash
# API key del totem (default: api_key_totem_1)
export TOTEM_API_KEY="la_tua_api_key"

# Porta llama.cpp per traduzioni AI (default: localhost:8080)
export LLAMACPP_URL="http://localhost:8080/v1/chat/completions"

# Avvia
mvn javafx:run
```

### 4. Hotkey in sviluppo

| Combinazione | Azione |
|---|---|
| `Ctrl+Alt+T` | Toggle tema chiaro/scuro |
| `Ctrl+Alt+H` | Uscita sicura (crea `.stop`) |

---

## Compilazione e build

### Build standard (JAR + lib separati)

```bash
mvn clean package
```

Produce in `target/`:
- `demo-1.jar` — app principale (senza dipendenze)
- `lib/` — tutte le dipendenze (JavaFX, Ikonli, Gson, ecc.)
- `lib.tar.gz` — archivio delle lib (usato da setup-kiosk.sh)
- `totem-fat.jar` — fat JAR con tutto incluso

### Fat JAR (tutto in un file)

```bash
mvn clean package
java -jar target/totem-fat.jar
```

> **Nota:** il fat JAR non funziona con i moduli JavaFX nativi su alcune configurazioni.  
> Usare il JAR + lib separati per il deploy su kiosk.

### Struttura artifacts dopo il build

```
target/
├── demo-1.jar          ← copia nel server come GitHub Release asset
├── lib.tar.gz          ← copia nel server come GitHub Release asset
├── totem-fat.jar       ← opzionale
└── lib/
    ├── javafx-controls-21.jar
    ├── javafx-fxml-21.jar
    ├── ikonli-core-12.3.1.jar
    ├── ikonli-javafx-12.3.1.jar
    ├── ikonli-materialdesign2-pack-12.3.1.jar
    └── gson-2.11.0.jar
```

---

## Deploy su macchina reale

### Metodo 1 — One-line (consigliato)

Su una macchina Linux con accesso root:

```bash
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | sudo bash
```

Lo script:
1. Scarica `demo-1.jar` e `lib.tar.gz` da GitHub Releases
2. Installa Java, Cage, driver GPU, lm-sensors
3. Crea l'utente `kiosk`
4. Configura il servizio systemd con riavvio automatico
5. Configura auto-login su TTY1
6. Ottimizza il boot

### Metodo 2 — Con file locali

```bash
# Sul PC di sviluppo
mvn clean package
scp target/demo-1.jar target/lib.tar.gz root@<ip-kiosk>:/tmp/

# Sul kiosk
ssh root@<ip-kiosk>
mkdir -p /tmp/kiosk-dist/lib
cp /tmp/demo-1.jar /tmp/kiosk-dist/
tar -xzf /tmp/lib.tar.gz -C /tmp/kiosk-dist/lib/

# Scarica e avvia lo script con i file locali
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh \
    -o /tmp/setup-kiosk.sh
bash /tmp/setup-kiosk.sh
```

### Metodo 3 — Reset e reinstallazione pulita

```bash
# Cancella TUTTO (utenti, servizi, app, config) e reinstalla
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh \
    | sudo bash -s reset
```

Il reset elimina:
- Utente `kiosk` e la sua home
- `/opt/kiosk/` (app, log, cache, ordini)
- Tutti i servizi systemd kiosk
- File udev, sysctl, sudoers, GRUB kiosk
- llama.cpp e modelli AI

### Aggiornamento JAR (senza reinstallare)

```bash
# Metodo rapido via kiosk-control
ssh root@<ip-kiosk>
kiosk-control update

# Oppure manuale
curl -fsSL https://github.com/accaicedtea/ttetttos/releases/download/v1.0.0/demo-1.jar \
    -o /tmp/demo-1.jar
kiosk-control stop
cp /tmp/demo-1.jar /opt/kiosk/demo-1.jar
chown kiosk:kiosk /opt/kiosk/demo-1.jar
kiosk-control start
```

---

## Deploy su macchina virtuale (VM)

Le VM non hanno GPU hardware — servono configurazioni specifiche per Wayland e JavaFX.

### Prerequisiti VM

- VMware Workstation / VirtualBox / QEMU-KVM / Proxmox
- Guest: Debian 12 o Ubuntu 22.04 **minimale** (no desktop)
- RAM: minimo 1GB (consigliato 2GB)
- Disco: minimo 4GB (+ 1GB per modello AI opzionale)
- Rete: NAT o Bridge (per raggiungere le API)

### Passo 1 — Installa il sistema guest minimale

Installa Debian/Ubuntu senza desktop environment.  
Seleziona solo: `SSH server` + `standard system utilities`.

### Passo 2 — Connettiti via SSH

```bash
ssh root@<ip-vm>
```

### Passo 3 — Installazione con setup automatico

```bash
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh \
    | bash
```

Lo script rileva automaticamente la VM e attiva il software rendering:
- `LIBGL_ALWAYS_SOFTWARE=1`
- `WLR_RENDERER=pixman`
- `WLR_NO_HARDWARE_CURSORS=1`

### Passo 4 — Configurazione display VM

#### VMware Workstation / Player

1. Imposta risoluzione: **Impostazioni VM → Display → Risoluzione personalizzata**
2. Consigliato per totem verticale: `1080x1920` o `768x1280`
3. Abilita "Accelerate 3D graphics" → **NO** (usa software rendering)

#### VirtualBox

```bash
# Nel terminale guest (come root)
VBoxManage setextradata "NomeVM" "CustomVideoMode1" "1080x1920x32"
```

Oppure dalla GUI: **Impostazioni → Display → Schermo → Memoria video** (almeno 16MB).

#### QEMU / Proxmox

```bash
# Aggiungi al comando QEMU:
-vga virtio -display gtk,gl=off
```

In Proxmox: **Hardware → Display → VirtIO-GPU** con memoria 16MB.

### Passo 5 — Test manuale in VM (senza riavviare)

```bash
# Come utente kiosk
su - kiosk

# Imposta ambiente
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

# Software rendering obbligatorio in VM
export LIBGL_ALWAYS_SOFTWARE=1
export WLR_RENDERER=pixman
export WLR_RENDERER_ALLOW_SOFTWARE=1
export WLR_NO_HARDWARE_CURSORS=1

# Avvia
dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh
```

### Passo 6 — Riavvia per attivare il servizio automatico

```bash
reboot
```

Dopo il riavvio il kiosk si avvia automaticamente.

### Accesso al terminale durante il kiosk (VM)

```bash
# Via SSH (sempre disponibile)
ssh root@<ip-vm>

# Via console VM: premi Ctrl+C nei primi 2 secondi dopo il login su TTY1
# Oppure: TTY2 con Alt+F2 (se non mascherato)
```

---

## GitHub Actions — Release automatica

### Come funziona

Ad ogni `git push` di un tag `v*`, GitHub Actions:
1. Compila il progetto con `mvn package`
2. Pubblica automaticamente su GitHub Releases:
   - `demo-1.jar`
   - `lib.tar.gz`
   - `totem-fat.jar`

### Creare una release

```bash
# 1. Committa e pusha le modifiche
git add .
git commit -m "feat: descrizione delle modifiche"
git push

# 2. Crea il tag di versione
git tag v1.0.1
git push origin v1.0.1

# GitHub Actions si avvia automaticamente e pubblica la release
```

### Verificare la release

1. Vai su `https://github.com/accaicedtea/ttetttos/releases`
2. Verifica che i file `demo-1.jar` e `lib.tar.gz` siano presenti
3. Il setup-kiosk.sh li scaricherà automaticamente

### Aggiornare la versione nel setup-kiosk.sh

```bash
# Modifica la riga RELEASE_TAG in setup-kiosk.sh
RELEASE_TAG="v1.0.1"
```

---

## Manutenzione

### Comandi principali

```bash
kiosk-control status     # Stato servizio + ultimi log
kiosk-control restart    # Riavvia l'app
kiosk-control stop       # Ferma (non si riavvia)
kiosk-control start      # Avvia
kiosk-control log        # Log in tempo reale
kiosk-control update     # Scarica e installa ultima versione
```

### Log

```bash
# Log app JavaFX
tail -f /opt/kiosk/logs/kiosk.log

# Log errori Java
tail -f /opt/kiosk/logs/kiosk-err.log

# Log systemd
journalctl -u kiosk -f

# Log rete (watchdog)
tail -f /opt/kiosk/logs/network.log
```

### File importanti

```
/opt/kiosk/
├── demo-1.jar              ← app principale
├── lib/                    ← dipendenze
├── run-kiosk.sh            ← script di avvio
├── logs/
│   ├── kiosk.log           ← output app
│   ├── kiosk-err.log       ← errori Java
│   └── network.log         ← watchdog rete
├── menu-cache.json         ← cache menu locale
├── translations.json       ← traduzioni AI generate
├── current-order.json      ← ordine corrente (crash recovery)
├── order-queue.json        ← ordini offline in attesa
└── models/
    └── tinyllama.gguf      ← modello AI per traduzioni
```

### Uscita di emergenza

Da tastiera fisica sul kiosk:
```
Ctrl+Alt+H   → uscita sicura (crea .stop, non si riavvia)
```

Via SSH:
```bash
kiosk-control stop
# oppure
touch /opt/kiosk/.stop && systemctl stop kiosk.service
```

---

## Struttura del progetto

```
ttetttos/
├── src/main/java/
│   ├── com/api/
│   │   ├── Api.java                  ← client HTTP base (retry, HTTP/1.1)
│   │   ├── SessionManager.java       ← gestione token JWT
│   │   └── services/
│   │       ├── AuthService.java      ← login/logout/ping
│   │       ├── ViewsService.java     ← menu, versione
│   │       └── OrdersService.java    ← CRUD ordini
│   ├── com/example/
│   │   ├── App.java                  ← entrypoint, avvia Splash
│   │   ├── Navigator.java            ← navigazione tra schermate (con cache)
│   │   ├── ThemeManager.java         ← tema chiaro/scuro
│   │   ├── Icons.java                ← factory icone MDI2 (Ikonli)
│   │   ├── NetworkWatchdog.java      ← ping periodico + heartbeat sistema
│   │   ├── RemoteLogger.java         ← log errori al server
│   │   ├── SplashController.java     ← schermata caricamento iniziale
│   │   ├── WelcomeController.java    ← selezione lingua
│   │   ├── ShopPageController.java   ← menu prodotti + carrello
│   │   ├── CartController.java       ← riepilogo ordine
│   │   ├── PaymentController.java    ← scelta pagamento
│   │   ├── ConfirmController.java    ← conferma ordine + animazioni
│   │   ├── components/
│   │   │   └── ProductCard.java      ← card prodotto riutilizzabile
│   │   └── model/
│   │       ├── CartItem.java         ← voce carrello (con id, iva, sku)
│   │       ├── CartManager.java      ← singleton carrello
│   │       ├── OrderQueue.java       ← coda ordini offline + crash recovery
│   │       ├── MenuCache.java        ← cache menu locale con sync background
│   │       ├── TranslationManager.java ← traduzioni AI via llama.cpp
│   │       └── I18n.java             ← stringhe multilingua (it/en/de/fr/ar)
│   └── com/util/
│       └── Animations.java           ← scroll inerziale, touch feedback
├── src/main/resources/com/example/
│   ├── ShopPage.fxml
│   ├── screens/
│   │   ├── SplashScreen.fxml
│   │   ├── WelcomeScreen.fxml
│   │   ├── CartScreen.fxml
│   │   ├── PaymentScreen.fxml
│   │   └── ConfirmScreen.fxml
│   └── styles/
│       ├── dark-theme.css
│       └── light-theme.css
├── .github/workflows/
│   └── release.yml                   ← build e release automatica
├── module-info.java
├── pom.xml
├── setup-kiosk.sh                    ← setup completo sistema kiosk
└── README.md                         ← questo file
```

---

## Variabili di configurazione

### Variabili d'ambiente

| Variabile | Default | Descrizione |
|---|---|---|
| `TOTEM_API_KEY` | `api_key_totem_1` | Chiave API per il login totem |
| `LLAMACPP_URL` | `http://localhost:8080/v1/chat/completions` | URL llama.cpp per traduzioni |
| `LIBGL_ALWAYS_SOFTWARE` | — | Forza software rendering (VM) |

### Proprietà Java (-D)

| Proprietà | Default | Descrizione |
|---|---|---|
| `totem.api.key` | `api_key_totem_1` | Alternativa a env var |
| `llamacpp.url` | `http://localhost:8080/...` | URL llama.cpp |
| `ollama.model` | `llama3.2:1b` | Nome modello (compatibilità) |

### Esempio avvio con configurazione custom

```bash
java \
  -Dtotem.api.key=mykey \
  -Dllamacpp.url=http://192.168.1.10:8080/v1/chat/completions \
  --module-path /opt/kiosk/lib \
  --add-modules javafx.controls,javafx.fxml \
  -cp /opt/kiosk/demo-1.jar \
  com.example.App
```

---

## Troubleshooting

### `Unable to open DISPLAY`

```bash
# Verifica che XDG_RUNTIME_DIR esista
ls -la /run/user/$(id -u kiosk)/

# Crealo se manca
mkdir -p /run/user/$(id -u kiosk)
chmod 700 /run/user/$(id -u kiosk)
chown kiosk:kiosk /run/user/$(id -u kiosk)

# Abilita linger per l'utente kiosk
loginctl enable-linger kiosk

# Riavvia il servizio
systemctl restart kiosk.service
```

### `A connection to the bus can't be made`

```bash
# Usa dbus-run-session esplicitamente
su - kiosk -c "XDG_RUNTIME_DIR=/run/user/$(id -u kiosk) dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh"
```

### Schermo nero / Cage si avvia ma JavaFX non appare

```bash
# Forza software rendering
export LIBGL_ALWAYS_SOFTWARE=1
export WLR_RENDERER=pixman
export PRISM_ORDER=sw

# Testa in foreground
su - kiosk
dbus-run-session cage -d -- /opt/kiosk/run-kiosk.sh 2>&1 | head -50
```

### `Connection reset` sulle API

Il server non supporta HTTP/2. L'app usa già HTTP/1.1 forzato — se persiste:

```bash
# Verifica connettività
curl -v https://hasanabdelaziz.altervista.org/api/v1/totem/auth/ping

# Controlla log
journalctl -u kiosk --since "5 min ago"
```

### Ordini non inviati (modalità offline)

```bash
# Controlla la coda
cat /opt/kiosk/order-queue.json | python3 -m json.tool

# La coda viene svuotata automaticamente ogni 30s quando la rete torna
# Forza sync manuale riavviando l'app
kiosk-control restart
```

### Il modello AI (llama.cpp) non genera traduzioni

```bash
# Verifica che il servizio sia attivo
systemctl status llamacpp.service

# Test manuale
curl http://localhost:8080/health

# Avvia manualmente
systemctl start llamacpp.service
journalctl -u llamacpp -f
```

Se il modello non è disponibile, l'app usa automaticamente le traduzioni hardcoded in `I18n.java` — funziona comunque.

### Temperatura CPU sempre 0 in dashboard

```bash
# Installa sensori
apt install lm-sensors -y
sensors-detect --auto

# Verifica
sensors
cat /sys/class/thermal/thermal_zone0/temp
```

### Reset completo e reinstallazione

```bash
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh \
    | sudo bash -s reset
```

---

*Documentazione aggiornata: Marzo 2026*
