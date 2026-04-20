#!/bin/bash
# Modify CartController to use createOrderAsync

sed -i 's/Navigator.goTo(Navigator.Screen.PAYMENT);/OrderQueue.createOrderAsync(CartManager.get(), () -> Navigator.goTo(Navigator.Screen.PAYMENT), err -> { System.err.println("Errore crezione ordine " + err); Navigator.goTo(Navigator.Screen.PAYMENT); });/g' /home/acca/websites/TOTTEM/demo/src/main/java/com/app/controllers/CartController.java

