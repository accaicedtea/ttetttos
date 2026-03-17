package com.example.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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

    // Dimensioni fisse bottone — tutto interno, nessun overflow
    private static final double BTN_W   = 240;
    private static final double BTN_H   = 210;
    private static final double FLAG_W  = 180;
    private static final double FLAG_H  = 118;

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

        for (Button b : new Button[]{btnIt, btnEn, btnDe, btnFr, btnAr})
            b.getStyleClass().remove("lang-btn-active");
        active.getStyleClass().add("lang-btn-active");

        // Pulse sul bottone selezionato
        ScaleTransition pulse = new ScaleTransition(Duration.millis(110), active);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.06);  pulse.setToY(1.06);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();

        // Mostra start
        startBtn.setVisible(true);
        startBtn.setManaged(true);
        startBtn.setScaleX(0.8); startBtn.setScaleY(0.8); startBtn.setOpacity(0);
        new ParallelTransition(
            scale(startBtn, 0.8, 1.0, 280),
            fade(startBtn,  0,   1.0, 280)
        ).play();
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
            final Timeline[] ref = {null};
            ref[0] = new Timeline(new KeyFrame(Duration.millis(200), e -> {
                if (cachedMenu != null) { ref[0].stop(); Navigator.goTo(Navigator.Screen.MENU, cachedMenu); }
                else if (loadError)     { ref[0].stop(); Navigator.goTo(Navigator.Screen.MENU, null); }
            }));
            ref[0].setCycleCount(Timeline.INDEFINITE);
            ref[0].play();
        }
    }

    // ── Precaricamento ────────────────────────────────────────────────

    private void preloadMenu() {
        if (cachedMenu != null) return;
        com.google.gson.JsonObject fromCache = MenuCache.loadFromCache();
        if (fromCache != null) { cachedMenu = fromCache; return; }
        new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                try {
                    cachedMenu = com.api.services.ViewsService.getMenu();
                    MenuCache.save(cachedMenu, "");
                    return;
                } catch (Exception e) {
                    if (i == 3) loadError = true;
                    else try { Thread.sleep(1000L * i); } catch (InterruptedException ignored) {}
                }
            }
        }, "welcome-fallback").start();
    }

    // ── Decorazione bottone lingua ────────────────────────────────────
    /**
     * Crea il contenuto del bottone con bandiera + nome lingua.
     * Tutto il contenuto è contenuto in uno StackPane delle stesse
     * dimensioni del bottone → nessun overflow di testo verso l'esterno.
     */
    private void decorateLangButton(Button btn, String lang, String langLabel) {
        // 1. Imposta dimensioni FISSE sul bottone — uguali al container grafico
        btn.setMinSize(BTN_W, BTN_H);
        btn.setPrefSize(BTN_W, BTN_H);
        btn.setMaxSize(BTN_W, BTN_H);
        btn.setText("");
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);

        // 2. Bandiera
        Node flag = com.util.FlagIcon.load(lang, FLAG_W, FLAG_H);

        // 3. Testo lingua (centrato, non wrappa)
        Label text = new Label(langLabel);
        text.getStyleClass().add("lang-btn-text");
        text.setAlignment(Pos.CENTER);
        text.setMaxWidth(BTN_W - 16);
        text.setWrapText(false);

        // 4. VBox con bandiera sopra e testo sotto, padding incluso
        VBox content = new VBox(12, flag, text);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16, 8, 16, 8));
        content.setPrefSize(BTN_W, BTN_H);
        content.setMaxSize(BTN_W, BTN_H);

        // 5. Clip: impedisce che qualsiasi elemento esca dal rettangolo del bottone
        Rectangle clip = new Rectangle(BTN_W, BTN_H);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        content.setClip(clip);

        btn.setGraphic(content);
    }

    // ── Animazioni ────────────────────────────────────────────────────

    private void fadeIn() {
        if (welcomeContainer == null) return;
        welcomeContainer.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), welcomeContainer);
        ft.setFromValue(0); ft.setToValue(1);
        ft.play();
    }

    private ScaleTransition scale(Node n, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from); st.setFromY(from); st.setToX(to); st.setToY(to);
        return st;
    }

    private FadeTransition fade(Node n, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }
}