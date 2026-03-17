package com.example.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.example.model.CartManager;
import com.example.model.I18n;
import com.example.model.MenuCache;
import com.util.Animations;
import com.util.Navigator;

public class WelcomeController implements Navigator.DataReceiver {

    @FXML private VBox   welcomeContainer;
    @FXML private Label  titleLabel;
    @FXML private Label  subtitleLabel;
    @FXML private Label  chooseLangLabel;
    @FXML private Button btnIt, btnEn, btnDe, btnFr, btnAr;
    @FXML private Button startBtn;

    // Dimensioni bandiera — grandi e leggibili da lontano
    private static final double FLAG_W = 190;
    private static final double FLAG_H = 130;

    private volatile com.google.gson.JsonObject cachedMenu = null;
    private volatile boolean loadError = false;

    @Override
    public void receiveData(Object data) {
        if (data instanceof com.google.gson.JsonObject menu) {
            cachedMenu = menu;
        }
    }

    @FXML
    private void initialize() {
        for (Button btn : new Button[]{btnIt, btnEn, btnDe, btnFr, btnAr, startBtn})
            if (btn != null) Animations.touchFeedback(btn);

        decorateLangButton(btnIt, "it", "Italiano");
        decorateLangButton(btnEn, "en", "English");
        decorateLangButton(btnDe, "de", "Deutsch");
        decorateLangButton(btnFr, "fr", "Français");
        decorateLangButton(btnAr, "ar", "العربية");

        fadeIn();
        preloadMenu();
    }

    // ── Lingua ────────────────────────────────────────────────────────

    @FXML private void onLangIt() { selectLang("it", btnIt); }
    @FXML private void onLangEn() { selectLang("en", btnEn); }
    @FXML private void onLangDe() { selectLang("de", btnDe); }
    @FXML private void onLangFr() { selectLang("fr", btnFr); }
    @FXML private void onLangAr() { selectLang("ar", btnAr); }

    private void selectLang(String lang, Button active) {
        I18n.setLang(lang);
        CartManager.get().setLanguage(lang);

        titleLabel.setText(I18n.t("welcome_title"));
        subtitleLabel.setText(I18n.t("welcome_subtitle"));
        chooseLangLabel.setText(I18n.t("choose_lang"));
        startBtn.setText(I18n.t("start") + "  →");

        // Deseleziona tutti
        for (Button b : new Button[]{btnIt, btnEn, btnDe, btnFr, btnAr})
            b.getStyleClass().remove("lang-btn-active");

        // Seleziona il corrente con animazione
        active.getStyleClass().add("lang-btn-active");
        ScaleTransition pulse = new ScaleTransition(Duration.millis(120), active);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.06);  pulse.setToY(1.06);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();

        // Mostra il pulsante start
        startBtn.setVisible(true);
        startBtn.setManaged(true);
        startBtn.setScaleX(0.8); startBtn.setScaleY(0.8); startBtn.setOpacity(0);

        ParallelTransition show = new ParallelTransition(
            scale(startBtn, 0.8, 1.0, 280),
            fade(startBtn, 0, 1.0, 280)
        );
        show.setInterpolator(Interpolator.EASE_OUT);
        show.play();
    }

    // ── Start ─────────────────────────────────────────────────────────

    @FXML
    private void onStart() {
        CartManager.get().clear();

        if (cachedMenu != null) {
            Navigator.goTo(Navigator.Screen.MENU, cachedMenu);
        } else if (loadError) {
            Navigator.goTo(Navigator.Screen.MENU, null);
        } else {
            startBtn.setText("⏳  " + I18n.t("start"));
            startBtn.setDisable(true);

            final Timeline[] pollerRef = {null};
            pollerRef[0] = new Timeline(new KeyFrame(Duration.millis(200), e -> {
                if (cachedMenu != null) {
                    pollerRef[0].stop();
                    Navigator.goTo(Navigator.Screen.MENU, cachedMenu);
                } else if (loadError) {
                    pollerRef[0].stop();
                    Navigator.goTo(Navigator.Screen.MENU, null);
                }
            }));
            pollerRef[0].setCycleCount(Timeline.INDEFINITE);
            pollerRef[0].play();
        }
    }

    // ── Precaricamento ────────────────────────────────────────────────

    private void preloadMenu() {
        if (cachedMenu != null) return;

        com.google.gson.JsonObject fromCache = MenuCache.loadFromCache();
        if (fromCache != null) { cachedMenu = fromCache; return; }

        new Thread(() -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    cachedMenu = com.api.services.ViewsService.getMenu();
                    MenuCache.save(cachedMenu, "");
                    return;
                } catch (Exception e) {
                    if (attempt == 3) loadError = true;
                    else { try { Thread.sleep(1000L * attempt); }
                           catch (InterruptedException ignored) {} }
                }
            }
        }, "welcome-fallback").start();
    }

    // ── Decorazione bottone lingua ────────────────────────────────────

    private void decorateLangButton(Button btn, String lang, String label) {
        btn.setTooltip(new javafx.scene.control.Tooltip(label));

        // Nome lingua (grande, leggibile da lontano)
        javafx.scene.control.Label text = new javafx.scene.control.Label(label);
        text.getStyleClass().add("lang-btn-text");
        text.setAlignment(javafx.geometry.Pos.CENTER);
        text.setMaxWidth(220);
        text.setWrapText(false);

        // Bandiera grande
        javafx.scene.Node flag = com.util.FlagIcon.load(lang, FLAG_W, FLAG_H);

        // Container verticale: bandiera sopra, nome sotto
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(14);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setPadding(new javafx.geometry.Insets(18, 16, 18, 16));
        vbox.getChildren().addAll(flag, text);

        btn.setText("");
        btn.setGraphic(vbox);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
    }

    // ── Animazioni ────────────────────────────────────────────────────

    private void fadeIn() {
        if (welcomeContainer == null) return;
        welcomeContainer.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), welcomeContainer);
        ft.setFromValue(0); ft.setToValue(1);
        ft.play();
    }

    private ScaleTransition scale(javafx.scene.Node n, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from); st.setFromY(from); st.setToX(to); st.setToY(to);
        return st;
    }

    private FadeTransition fade(javafx.scene.Node n, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }
}