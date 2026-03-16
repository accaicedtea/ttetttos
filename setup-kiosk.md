# setup-kiosk.sh — Totem Kiosk Installer

## Cos'è
Script di installazione automatica per il sistema kiosk JavaFX Totem. Scarica l'applicazione e tutte le dipendenze dalla release GitHub, configura il sistema per funzionamento 24/7, crea utente, servizi systemd, watchdog, ottimizza il boot e avvia l'app in automatico.

## Requisiti
- Sistema Linux minimale (no desktop environment)
- Java 17+ installato o installabile via package manager
- Connessione internet

## Installazione automatica (consigliata)

```
ssh root@target 'bash -c "curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash"'
```

## Installazione manuale (copia locale)

```
scp -r <progetto> root@target:/tmp/kiosk-app
ssh root@target 'bash /tmp/kiosk-app/setup-kiosk.sh'
```

## Reset completo

Per cancellare completamente /opt/kiosk e reinstallare tutto da zero:

```
ssh root@target 'bash /tmp/kiosk-app/setup-kiosk.sh reset'
# oppure
curl -fsSL https://raw.githubusercontent.com/accaicedtea/ttetttos/main/setup-kiosk.sh | bash -s reset
```

## Cosa fa lo script
- Scarica sempre la versione stabile da GitHub (demo-1.jar + lib.tar.gz)
- Installa tutte le dipendenze di sistema (Java, driver GPU, cage, ecc.)
- Crea/aggiorna utente kiosk e directory /opt/kiosk
- Installa e abilita tutti i servizi systemd necessari
- Configura watchdog hardware/software, OOM killer, ottimizza kernel e boot
- Avvia automaticamente la tua app come servizio robusto
- Se già installato, aggiorna/sovrascrive tutto senza errori
- Se lanciato con `reset`, cancella /opt/kiosk e riparte da zero

## Avvio automatico
- L'app viene avviata da systemd (kiosk.service) e riavviata in caso di crash
- Se il sistema si blocca, il watchdog forza il reboot
- Auto-login su TTY1 come fallback: anche senza systemd, la bash rilancia l'app

## Manutenzione
- Per aggiornare solo il JAR: copia il nuovo demo-1.jar in /tmp e lancia `kiosk-control update`
- Per vedere lo stato/log: `kiosk-control status` oppure `journalctl -u kiosk -f`

## Personalizzazione
- Modifica la variabile KIOSK_APP_URL nello script per cambiare versione/release
- Puoi aggiungere logica per scegliere la versione via argomento o variabile

---

Per dettagli e troubleshooting, consulta il file di log: `/var/log/kiosk-setup.log`
