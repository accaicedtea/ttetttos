package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.util.Duration;

import com.api.services.AuthService;
import com.app.model.MenuCache;
import com.app.model.OrderQueue;
import com.app.model.TranslationManager;
import com.util.Navigator;
import com.util.NetworkWatchdog;

/**
 * Controller della splash screen di avvio.
 *
 * Step sequenziali con progresso visivo:
 *   1. Animazione entrata           (0.0 → 0.05)
 *   2. Login API                    (0.05 → 0.35)
 *   3. Caricamento menu             (0.35 → 0.65)
 *   4. Traduzioni (cache o Ollama)  (0.65 → 0.90)
 *   5. Preparazione UI              (0.90 → 1.00)
 *   → Naviga a WelcomeScreen
 */
public class SplashController {

    @FXML private StackPane   splashCircle1, splashCircle2;
    @FXML private FontIcon    logoLabel;
    @FXML private FontIcon    stepIconNode;
    @FXML private Label       stepLabel, detailLabel;
    @FXML private ProgressBar progressBar;

    // API key — stessa logica di App.java
    private static final String API_KEY =
            System.getProperty("totem.api.key",
                    System.getenv().getOrDefault("TOTEM_API_KEY", "api_key_totem_1"));

    @FXML
    private void initialize() {
        // Stato iniziale nascosto
        logoLabel.setOpacity(0);
        logoLabel.setScaleX(0.3);
        logoLabel.setScaleY(0.3);
        progressBar.setProgress(0);
        stepLabel.setText("");
        detailLabel.setText("");

        Platform.runLater(this::startSequence);
    }

    // ─────────────────────────────────────────────────────────────────
    // SEQUENZA PRINCIPALE
    // ─────────────────────────────────────────────────────────────────

    private void startSequence() {
        // Animazione cerchi di sfondo
        animateBackground();

        // 1. Logo pop-in
        SequentialTransition logoAnim = new SequentialTransition(
            new PauseTransition(Duration.millis(200)),
            parallel(
                scale(logoLabel, 0.3, 1.15, 500, Interpolator.EASE_OUT),
                fade(logoLabel, 0, 1, 400)
            ),
            scale(logoLabel, 1.15, 1.0, 150, Interpolator.EASE_IN)
        );

        logoAnim.setOnFinished(e -> {
            // Dopo il logo parte la sequenza asincrona
            runSetupSequence();
        });
        logoAnim.play();
    }

    private void runSetupSequence() {
        // Tutto gira in un thread di background;
        // ogni step aggiorna la UI con setStep() poi Platform.runLater
        new Thread(() -> {

            // ── STEP 1: Login ─────────────────────────────────────────
            setStep("mdi2l-lock", "Connessione al server...", "", 0.05);

            String loginError = null;
            try {
                AuthService.loginTotem(API_KEY);
                setStep("mdi2l-lock", "Connesso", "Login completato", 0.35);
                // Avvia watchdog e sync coda ordini offline
                NetworkWatchdog.start(isOnline ->
                        System.out.println("[Net] " + (isOnline ? "Online" : "Offline")));
                OrderQueue.startQueueSync();
                sleep(300);
            } catch (Exception e) {
                loginError = e.getMessage();
                setStep("mdi2a-alert", "Connessione fallita", "Uso modalità offline", 0.35);
                System.err.println("[Splash] Login fallito: " + e.getMessage());
                // Non logghiamo remotamente qui — non siamo autenticati
                sleep(800);
            }

            // ── STEP 2: Menu ──────────────────────────────────────────
            setStep("mdi2c-clipboard-list", "Caricamento menu...", "", 0.40);

            com.google.gson.JsonObject menuData = null;

            // Prima prova la cache locale (istantaneo)
            menuData = MenuCache.loadFromCache();
            if (menuData != null) {
                setStep("mdi2c-clipboard-list", "Menu caricato", "Da cache locale", 0.55);
                sleep(200);
            }

            // Se loggato, aggiorna dalla rete
            if (com.api.SessionManager.isLoggedIn()) {
                setStep("mdi2c-clipboard-list", "Aggiornamento menu...", "Sincronizzazione con il server", 0.55);
                try {
                    com.google.gson.JsonObject fresh =
                            com.api.services.ViewsService.getMenu();
                    MenuCache.save(fresh, "");
                    menuData = fresh;
                    setStep("mdi2c-clipboard-list", "Menu aggiornato", "Dati sincronizzati", 0.65);
                    sleep(300);
                } catch (Exception e) {
                    if (menuData != null) {
                        setStep("mdi2c-clipboard-list", "Menu da cache", "Server non raggiungibile", 0.65);
                    } else {
                        setStep("mdi2a-alert-circle", "Menu non disponibile", e.getMessage(), 0.65);
                    }
                    sleep(600);
                }
            } else {
                setStep("mdi2c-clipboard-list", menuData != null ? "Menu da cache" : "Menu non disponibile",
                        loginError != null ? "Modalità offline" : "", 0.65);
                sleep(400);
            }

            // ── STEP 3: Traduzioni ────────────────────────────────────
            setStep("mdi2e-earth", "Caricamento traduzioni...", "", 0.68);

            if (TranslationManager.isCached()) {
                TranslationManager.loadFromCache();
                setStep("mdi2e-earth", "Traduzioni caricate", "Da cache locale", 0.85);
                sleep(200);

                // Aggiorna in background con Ollama se disponibile
                new Thread(() -> {
                    TranslationManager.fetchAndSave();
                }, "trans-bg-update").start();

            } else {
                // Prima volta: genera con Ollama (può richiedere tempo)
                setStep("mdi2r-robot", "Generazione traduzioni AI...",
                        "Ollama in esecuzione — attendere", 0.68);
                TranslationManager.fetchAndSave();

                if (TranslationManager.isCached()) {
                    setStep("mdi2e-earth", "Traduzioni generate", "Salvate per i prossimi avvii", 0.85);
                } else {
                    setStep("mdi2e-earth", "Traduzioni predefinite", "Ollama non disponibile", 0.85);
                }
                sleep(400);
            }

            // ── STEP 4: Finalizzazione ────────────────────────────────
            setStep("mdi2c-check-circle", "Tutto pronto!", "", 0.95);
            sleep(300);
            setProgress(1.0);
            sleep(400);

            // ── Navigazione a Welcome ─────────────────────────────────
            final com.google.gson.JsonObject finalMenu = menuData;
            Platform.runLater(() -> {
                // Passa il menu precachato al WelcomeController
                Navigator.goTo(Navigator.Screen.WELCOME, finalMenu);
            });

        }, "splash-setup").start();
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS UI (thread-safe)
    // ─────────────────────────────────────────────────────────────────

    private void setStep(String iconLiteral, String label, String detail, double progress) {
        Platform.runLater(() -> {
            if (stepIconNode != null) stepIconNode.setIconLiteral(iconLiteral);

            // Animazione testo: fade out → cambia → fade in
            FadeTransition out = new FadeTransition(Duration.millis(120), stepLabel);
            out.setToValue(0);
            out.setOnFinished(e -> {
                stepLabel.setText(label);
                detailLabel.setText(detail);
                FadeTransition in = new FadeTransition(Duration.millis(180), stepLabel);
                in.setToValue(1);
                in.play();
            });
            out.play();

            // Progresso con animazione smooth
            animateProgress(progress);

            System.out.println("[Splash] " + iconLiteral + " " + label
                    + (detail.isBlank() ? "" : " — " + detail));
        });
    }

    private void setProgress(double value) {
        Platform.runLater(() -> animateProgress(value));
    }

    private void animateProgress(double target) {
        double current = progressBar.getProgress();
        if (target <= current) { progressBar.setProgress(target); return; }
        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(progressBar.progressProperty(), current)),
            new KeyFrame(Duration.millis(400),
                new KeyValue(progressBar.progressProperty(), target,
                        Interpolator.EASE_BOTH))
        );
        tl.play();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    // ANIMAZIONI SFONDO
    // ─────────────────────────────────────────────────────────────────

    private void animateBackground() {
        // Cerchio 1: pulse lento
        ScaleTransition p1 = new ScaleTransition(Duration.seconds(4), splashCircle1);
        p1.setFromX(0.8); p1.setFromY(0.8); p1.setToX(1.1); p1.setToY(1.1);
        p1.setAutoReverse(true); p1.setCycleCount(Animation.INDEFINITE);
        p1.setInterpolator(Interpolator.EASE_BOTH);
        p1.play();

        
        ScaleTransition p2s = new ScaleTransition(Duration.seconds(5), splashCircle2);
        p2s.setFromX(1.1); p2s.setFromY(1.1); p2s.setToX(0.85); p2s.setToY(0.85);
        p2s.setAutoReverse(true); p2s.setCycleCount(Animation.INDEFINITE);
        p2s.setInterpolator(Interpolator.EASE_BOTH);
        PauseTransition p2delay = new PauseTransition(Duration.seconds(1));
        p2delay.setOnFinished(e -> p2s.play());
        p2delay.play();

        // Rotazione lenta cerchio 1
        RotateTransition r1 = new RotateTransition(Duration.seconds(30), splashCircle1);
        r1.setByAngle(360); r1.setCycleCount(Animation.INDEFINITE);
        r1.play();
    }

    // ─────────────────────────────────────────────────────────────────
    // ANIMATION HELPERS
    // ─────────────────────────────────────────────────────────────────

    private ParallelTransition parallel(Animation... anims) {
        return new ParallelTransition(anims);
    }

    private ScaleTransition scale(javafx.scene.Node n, double from, double to,
                                  int ms, Interpolator interp) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(from); st.setFromY(from); st.setToX(to); st.setToY(to);
        st.setInterpolator(interp);
        return st;
    }

    private FadeTransition fade(javafx.scene.Node n, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }
}
