#!/bin/bash
if [ "$EUID" -ne 0 ]; then echo "Esegui con sudo"; exit 1; fi
echo "Disabilitando autologin Kiosk..."
sed -i '/AutomaticLoginEnable=True/d' /etc/gdm3/custom.conf
sed -i '/AutomaticLogin=totem-test/d' /etc/gdm3/custom.conf
echo "Fatto! Riavvia il pc (sudo reboot) per tornare alla normale schermata di login."
