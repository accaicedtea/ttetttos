package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import com.app.model.I18n;
import com.api.repository.DataRepository;
import com.util.Animations;
import com.util.FlagIcon;
import com.util.Navigator;
import com.util.RemoteLogger;

import java.util.List;

/**
 * WelcomeController — schermata di benvenuto con selezione lingua.
 *
 * Rispetto all'originale:
 * - Estende BaseController (t(), setVisible())
 * - Resto invariato
 */
public class WelcomeController extends BaseController implements Navigator.DataReceiver, Navigator.ScreenReturnable {

    @FXML
    private StackPane rootPane;
    @FXML
    private Label titleLabel, subtitleLabel;
    @FXML
    private FlowPane languagePane;
    @FXML
    private Button startBtn;
    @FXML
    private Label startHint;

    // Menu precaricato dalla Splash
    private com.google.gson.JsonObject preloadedMenu;

    @FXML
    private void initialize() {
        this.rootStack = rootPane;

        t(titleLabel, "welcome_title");
        t(subtitleLabel, "welcome_subtitle");
        if (startBtn != null)
            startBtn.setText(I18n.t("start"));
        t(startHint, null);
        if (startHint != null)
            startHint.setText(I18n.t("proceed_hint"));

        buildLanguageButtons();
        Animations.touchFeedback(startBtn);
        animateEntrance();
        
        // Esegue il controllo aggiornamenti (scatterà una volta sola per l'intero ciclo di vita grazie alla flag interna)
        com.api.services.UpdateService.checkForUpdates();

        // Ferma il monitoraggio dell'inattività in welcome screen
        com.util.InactivityManager.stopMonitoring();
    }

    @Override
    public void onReturn() {
        // Ferma il monitoraggio quando si ritorna a welcome
        com.util.InactivityManager.stopMonitoring();
    }

    @Override
    public void receiveData(Object data) {
        if (data instanceof com.google.gson.JsonObject j) {
            preloadedMenu = j;
        } else if (data instanceof com.app.pojo.MenuData m) {
            // MenuData ricevuto da InitializationService - fallback a DataRepository
            try {
                com.app.pojo.MenuData cached = DataRepository.getMenu();
                if (cached != null) {
                    // Serialize MenuData - per ora non fare conversione
                    preloadedMenu = null; // Will use DataRepository
                }
            } catch (Exception e) {
                // Fallback
                preloadedMenu = null;
            }
        }
    }

    private void buildLanguageButtons() {
        if (languagePane == null)
            return;
        languagePane.getChildren().clear();

        for (String lang : List.of("it", "en", "de", "fr", "ar")) {
            Button btn = new Button();
            btn.getStyleClass().addAll("lang-btn", "lang-" + lang);

            // Bandiera + etichetta
            var flag = FlagIcon.load(lang, 200); // dimensione touch-friendly
            Label lbl = new Label(langLabel(lang));
            VBox box = new VBox(4, flag, lbl);
            box.setAlignment(javafx.geometry.Pos.CENTER);
            btn.setGraphic(box);

            Animations.touchFeedback(btn);
            btn.setOnAction(e -> selectLanguage(lang, btn));
            languagePane.getChildren().add(btn);
        }
    }

    private void selectLanguage(String lang, Button btn) {
        I18n.setLang(lang);
        // Evidenzia il bottone selezionato
        languagePane.getChildren().forEach(n -> n.getStyleClass().remove("lang-btn-active"));
        btn.getStyleClass().add("lang-btn-active");
        // Aggiorna UI con nuova lingua
        t(titleLabel, "welcome_title");
        t(subtitleLabel, "welcome_subtitle");
        if (startBtn != null) {
            startBtn.setText(I18n.t("start"));
            setVisible(startBtn, true); // mostra il pulsante di avvio dopo selezione lingua
        }
    }

    @FXML
    private void onStart() {
        // Carica menu dai dati precaricati o da DataRepository
        if (preloadedMenu != null) {
            // Usa menu precaricato da InitializationService
            Navigator.goTo(Navigator.Screen.MENU, preloadedMenu);
        } else {
            // Carica da DataRepository e naviga
            try {
                com.app.pojo.MenuData menuData = DataRepository.getMenu();
                if (menuData != null && !menuData.isEmpty()) {
                    // Naviga con MenuData
                    Navigator.goTo(Navigator.Screen.MENU, menuData);
                } else {
                    // Menu vuoto - nulla da fare
                    System.err.println("[Welcome] Menu vuoto dal repository");
                }
            } catch (Exception e) {
                RemoteLogger.error("Welcome", "onStart load menu", e);
            }
        }
    }

    private void animateEntrance() {
        Platform.runLater(() -> {
            if (rootPane != null) {
                rootPane.setOpacity(0);
                rootPane.setTranslateY(30);

                FadeTransition fade = new FadeTransition(Duration.millis(500), rootPane);
                fade.setToValue(1);

                TranslateTransition translate = new TranslateTransition(Duration.millis(500), rootPane);
                translate.setToY(0);
                translate.setInterpolator(Interpolator.EASE_OUT);

                new ParallelTransition(fade, translate).play();
            }
        });
    }

    private static String langLabel(String lang) {
        return switch (lang) {
            case "it" -> "Italiano";
            case "en" -> "English";
            case "de" -> "Deutsch";
            case "fr" -> "Français";
            case "ar" -> "العربية";
            default -> lang;
        };
    }
}
