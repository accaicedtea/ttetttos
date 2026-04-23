package com.util;

import com.app.App;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Gestore dell'applicazione per aggiornamenti e blocchi di sicurezza.
 */
public class SystemManager {

    private static VBox activeLockScreen = null;
    private static volatile boolean appLocked = false;

    /**
     * Blocca il totem e mostra una schermata rossa (es. dopo 30 giorni offline o disattivo dal database).
     */
    public static void lockApp(String message) {
        appLocked = true;
        ConsoleColors.printWarn("[SystemManager] Applicazione bloccata: " + message);
        Platform.runLater(() -> {
            // Rimuoviamo eventuale blocco precedente prima di metterne uno nuovo aggiornato
            if (activeLockScreen != null && App.rootPane != null) {
                App.rootPane.getChildren().remove(activeLockScreen);
            }

            VBox lockScreen = new VBox(20);
            lockScreen.setAlignment(Pos.CENTER);
            lockScreen.setStyle("-fx-background-color: #b71c1c;"); // Rosso errore scuro
            
            // Blocca i click affinché non passino all'interfaccia sottostante
            lockScreen.setMouseTransparent(false);
            lockScreen.setOnMouseClicked(event -> event.consume());

            Label title = new Label("SISTEMA BLOCCATO");
            title.setStyle("-fx-text-fill: white; -fx-font-size: 48px; -fx-font-weight: bold;");

            Label msgLabel = new Label(message);
            msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");

            lockScreen.getChildren().addAll(title, msgLabel);
            activeLockScreen = lockScreen;

            if (App.rootPane != null && !App.rootPane.getChildren().contains(lockScreen)) {
                App.rootPane.getChildren().add(lockScreen);
                lockScreen.toFront();
            }
        });
    }

    /**
     * Sblocca l'applicazione rimuovendo il pannello di blocco, ripristinando il normale uso.
     */
    public static void unlockApp() {
        ConsoleColors.printSuccess("[SystemManager] Applicazione sbloccata");
        appLocked = false;
        Platform.runLater(() -> {
            if (activeLockScreen != null && App.rootPane != null) {
                App.rootPane.getChildren().remove(activeLockScreen);
                activeLockScreen = null;
            }
        });
    }

    public static boolean isAppLocked() {
        return appLocked;
    }

    private static boolean updatePromptShown = false;

    /**
     * Mostra un popup per decidere se aggiornare subito o annullare.
     * Viene mostrato una sola volta per ciclo di vita dell'applicazione.
     */
    public static void showUpdatePrompt(String version, Long updateId) {
        if (updatePromptShown) return;

        Platform.runLater(() -> {
            if (updatePromptShown) return; // double check on FX thread
            if (App.rootPane == null) return;
            
            updatePromptShown = true;
            
            com.app.components.ModalDialog.builder(App.rootPane)
                .type(com.app.components.ModalDialog.Type.INFO)
                .title("AGGIORNAMENTO DISPONIBILE")
                .message("È DISPONIBILE LA VERSIONE " + version + " DEL SISTEMA.\nVUOI AGGIORNARE IL TOTEM ADESSO?")
                .width(800) // Aumentato rispetto al default per renderlo bello grande
                .closeOnBackdrop(false)
                .closeOnEscape(false)
                .button(com.app.components.ModalButton.cancel("CHIUDI"))
                .button(com.app.components.ModalButton.primary("AGGIORNA ADESSO", () -> {
                    com.api.services.UpdateService.startUpdate(version, updateId);
                }))
                .show();
        });
    }

    /**
     * Mostra la schermata blu di aggiornamento e forza il riavvio del kiosk.
     */
    public static void showUpdateScreen(String version) {
        Platform.runLater(() -> {
            VBox updateScreen = new VBox(20);
            updateScreen.setAlignment(Pos.CENTER);
            updateScreen.setStyle("-fx-background-color: #0277bd;"); // Blu info
            
            updateScreen.setMouseTransparent(false);
            updateScreen.setOnMouseClicked(event -> event.consume());

            Label title = new Label("AGGIORNAMENTO IN CORSO");
            title.setStyle("-fx-text-fill: white; -fx-font-size: 48px; -fx-font-weight: bold;");

            Label msgLabel = new Label("SCARICAMENTO AGGIORNAMENTO V" + version + "...");
            msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");

            updateScreen.getChildren().addAll(title, msgLabel);

            if (App.rootPane != null && !App.rootPane.getChildren().contains(updateScreen)) {
                App.rootPane.getChildren().add(updateScreen);
                updateScreen.toFront();
            }

            // Simula il tempo di download, poi spegne l'app.
            new Thread(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
                Platform.runLater(() -> System.exit(0));
            }).start();
        });
    }
}
