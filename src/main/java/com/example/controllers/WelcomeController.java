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

    private volatile com.google.gson.JsonObject cachedMenu = null;
    private volatile boolean loadError = false;

    /** Riceve il menu pre-caricato dalla SplashScreen. */
    @Override
    public void receiveData(Object data) {
        if (data instanceof com.google.gson.JsonObject menu) {
            cachedMenu = menu;
            System.out.println("[Welcome] Menu ricevuto dalla Splash.");
        }
    }

    @FXML
    private void initialize() {
        for (Button btn : new Button[]{btnIt, btnEn, btnDe, btnFr, btnAr, startBtn})
            if (btn != null) Animations.touchFeedback(btn);

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

        // Mostra il pulsante start
        startBtn.setVisible(true);
        startBtn.setManaged(true);
        startBtn.setScaleX(0.7); startBtn.setScaleY(0.7); startBtn.setOpacity(0);

        ParallelTransition show = new ParallelTransition(
            scale(startBtn, 0.7, 1.0, 250),
            fade(startBtn, 0, 1.0, 250)
        );
        show.setInterpolator(Interpolator.EASE_OUT);
        show.play();
    }

    // ── Start ─────────────────────────────────────────────────────────

    @FXML
    private void onStart() {
        CartManager.get().clear();

        if (cachedMenu != null) {
            // Menu già pronto — naviga subito
            Navigator.goTo(Navigator.Screen.MENU, cachedMenu);
        } else if (loadError) {
            // Errore di rete — riprova e naviga comunque (il menu si ricaricherà nel ShopPage)
            Navigator.goTo(Navigator.Screen.MENU, null);
        } else {
            // In attesa — usa un Timeline che controlla ogni 200ms sul FX thread
            startBtn.setText("⏳  " + I18n.t("start"));
            startBtn.setDisable(true);

            final Timeline[] pollerRef = {null};
            pollerRef[0] = new Timeline(new KeyFrame(Duration.millis(200), e -> {
                if (cachedMenu != null) {
                    pollerRef[0].stop();  // ferma PRIMA di navigare
                    Navigator.goTo(Navigator.Screen.MENU, cachedMenu);
                } else if (loadError) {
                    pollerRef[0].stop();  // ferma PRIMA di navigare
                    Navigator.goTo(Navigator.Screen.MENU, null);
                }
                // else: continua a pollare
            }));
            pollerRef[0].setCycleCount(Timeline.INDEFINITE);
            pollerRef[0].play();
        }
    }

    // ── Precaricamento ────────────────────────────────────────────────

    private void preloadMenu() {
        // La Splash ha già fatto il carico pesante.
        // Se per qualche motivo il menu non è arrivato via receiveData,
        // proviamo la cache e poi la rete.
        if (cachedMenu != null) return;

        com.google.gson.JsonObject fromCache = MenuCache.loadFromCache();
        if (fromCache != null) {
            cachedMenu = fromCache;
            return;
        }

        // Ultimo fallback: rete
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
