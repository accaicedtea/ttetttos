package com.util;

import javafx.application.Platform;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import com.app.model.CartManager;
import com.app.components.ModalDialog;
import com.app.controllers.ShopPageController;

/**
 * InactivityManager — Gestisce i timeout di inattività per il totem.
 *
 * LOGICA:
 * - MENU (ShopPage): 15 secondi di inattività → mostra dialogo di conferma con 10 sec countdown
 * - CART / PAYMENT (CartScreen / PaymentScreen): 30 secondi di inattività → mostra dialogo di conferma con 10 sec countdown
 * 
 * Nel dialogo di conferma:
 * - "Continua a ordinare" (VERDE): chiude il dialogo, resetta il timer, continua normalmente
 * - "Chiudi" (ROSSO): CANCELLA ORDINE e torna a WELCOME
 * - Se il timeout di 10 secondi scade: CANCELLA ORDINE e torna a WELCOME
 *
 * Il timer si resetta ad ogni interazione dell'utente (click, tastiera).
 */
public class InactivityManager {

    private static Timeline inactivityTimer = null;
    private static Navigator.Screen currentScreen = null;
    private static StackPane currentRootStack = null;
    private static final int MENU_TIMEOUT_SEC = 15;
    private static final int CART_PAYMENT_TIMEOUT_SEC = 30;
    private static final int CONFIRMATION_TIMEOUT_SEC = 10;

    /**
     * Avvia il monitoraggio dell'inattività per la schermata corrente.
     */
    public static void startMonitoring(Navigator.Screen screen, StackPane rootStack) {
        ConsoleColors.printInfo("[InactivityManager] Avvio monitoraggio per schermata: " + screen);
        currentScreen = screen;
        currentRootStack = rootStack;
        resetTimer(screen.name());
    }

    /**
     * Avvia il monitoraggio senza StackPane (fallback per compatibilità).
     */
    public static void startMonitoring(Navigator.Screen screen) {
        ConsoleColors.printInfo("[InactivityManager] Avvio monitoraggio per schermata: " + screen + " (no StackPane)");
        startMonitoring(screen, null);
    }

    /**
     * Ferma il monitoraggio dell'inattività.
     */
    public static void stopMonitoring() {
        ConsoleColors.printWarn("[InactivityManager] Stop monitoraggio per schermata: " + currentScreen);
        if (inactivityTimer != null) {
            inactivityTimer.stop();
            inactivityTimer = null;
        }
        currentScreen = null;
        currentRootStack = null;
    }

    /**
     * Resetta il timer di inattività (chiamare ad ogni interazione dell'utente).
     */
    public static void resetTimer(String source) {
        // ConsoleColors.printInfo("[InactivityManager] Timer resettato da " + source);
        // Ferma il timer precedente
        if (inactivityTimer != null) {
            inactivityTimer.stop();
        }

        if (currentScreen == null) {
            return;
        }

        // Determina il timeout in secondi in base alla schermata
        int timeoutSec = getTimeoutForScreen(currentScreen);
        if (timeoutSec <= 0) {
            return; // Nessun timeout per questa schermata
        }

        // Crea un nuovo timer
        inactivityTimer = new Timeline(
                new KeyFrame(Duration.seconds(timeoutSec), event -> {
                    onInactivityTimeout();
                }));
        inactivityTimer.setCycleCount(1);
        inactivityTimer.play();
    }

    /**
     * Determina il timeout in secondi per la schermata specificata.
     */
    private static int getTimeoutForScreen(Navigator.Screen screen) {
        return switch (screen) {
            case MENU -> MENU_TIMEOUT_SEC;
            case CART, PAYMENT -> CART_PAYMENT_TIMEOUT_SEC;
            default -> -1; // Nessun timeout per altre schermate
        };
    }

    /**
     * Eseguito quando il timeout di inattività scade.
     * Mostra un dialogo di conferma con 10 secondi di countdown.
     */
    private static void onInactivityTimeout() {
        ConsoleColors.printInfo("[InactivityManager] Timeout inattività raggiunto per schermata: " + currentScreen);
        Platform.runLater(() -> {
            if (currentRootStack != null) {
                // Mostra dialogo di conferma con countdown
                ModalDialog.confirmWithTimeout(
                    currentRootStack,
                    "ATTENZIONE",
                    "Inattività rilevata. Vuoi continuare l'ordine oppure annullare?",
                    CONFIRMATION_TIMEOUT_SEC,
                    // onContinue: verde - continua l'ordine (nessuna azione)
                    () -> {
                        // Reset timer e continua normalmente
                        resetTimer("onInactivityTimeout");
                    },
                    // onClose: rosso - annulla ordine e torna a WELCOME
                    () -> returnToWelcome()
                );
            } else {
                // Fallback: torna direttamente a welcome
                returnToWelcome();
            }
        });
    }

    /**
     * Cancella l'ordine e ritorna a WELCOME.
     */
    private static void returnToWelcome() {
        ConsoleColors.printWarn("[InactivityManager] Utente inattivo. Annullamento ordine e ritorno a WELCOME.");
        CartManager.get().clear();
        
        //TODO: CHIDERE IL MODAL DELLA COMPOSIZIONE KUMPIR SE APERTO

        Navigator.goTo(Navigator.Screen.WELCOME);
        stopMonitoring();
    }
}
