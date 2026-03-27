package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.api.services.AuthService;
import com.app.model.MenuCache;
import com.app.model.OrderQueue;
import com.app.model.TranslationManager;
import com.util.Navigator;
import com.util.NetworkWatchdog;

/**
 * SplashController — schermata di avvio con sequenza di loading.
 *
 * Rispetto all'originale:
 * - Estende BaseController
 * - sleep() estratto come metodo privato statico
 * - setStep() estratto per eliminare ripetizione
 * - Usa RemoteLogger dal package corretto (non duplicato)
 */
public class SplashController extends BaseController {

    @FXML
    private StackPane splashCircle1, splashCircle2;
    @FXML
    private FontIcon logoLabel;
    @FXML
    private FontIcon stepIconNode;
    @FXML
    private Label stepLabel, detailLabel;
    @FXML
    private ProgressBar progressBar;

    private static final String API_KEY = System.getProperty("totem.api.key",
            System.getenv().getOrDefault("TOTEM_API_KEY", "api_key_totem_1"));

    @FXML
    private void initialize() {
        logoLabel.setOpacity(0);
        logoLabel.setScaleX(0.3);
        logoLabel.setScaleY(0.3);
        progressBar.setProgress(0);
        stepLabel.setText("");
        detailLabel.setText("");
        Platform.runLater(this::startSequence);
    }

    // ─────────────────────────────────────────────────────────────────

    private void startSequence() {
        animateBackground();

        SequentialTransition logoAnim = new SequentialTransition(
                new PauseTransition(Duration.millis(200)),
                parallel(scale(logoLabel, 0.3, 1.15, 500, Interpolator.EASE_OUT),
                        fade(logoLabel, 0, 1, 400)),
                scale(logoLabel, 1.15, 1.0, 150, Interpolator.EASE_IN));
        logoAnim.setOnFinished(e -> runSetupSequence());
        logoAnim.play();
    }

    private void runSetupSequence() {
        new Thread(() -> {

            // ── Step 1: Login ─────────────────────────────────────────
            setStep("mdi2l-lock", "Connessione al server...", "", 0.05);
            String loginError = null;
            try {
                AuthService.loginTotem(API_KEY);
                setStep("mdi2l-lock", "Connesso", "Login completato", 0.35);
                NetworkWatchdog.start(online -> System.out.println("[Net] " + (online ? "Online" : "Offline")));
                OrderQueue.startQueueSync();
                sleep(300);
            } catch (Exception e) {
                loginError = e.getMessage();
                setStep("mdi2a-alert", "Connessione fallita", "Uso modalità offline", 0.35);
                System.err.println("[Splash] Login fallito: " + e.getMessage());
                sleep(800);
            }

            // ── Step 2: Menu ──────────────────────────────────────────
            setStep("mdi2c-clipboard-list", "Caricamento menu...", "", 0.40);
            com.google.gson.JsonObject menuData = MenuCache.loadFromCache();
            if (menuData != null) {
                setStep("mdi2c-clipboard-list", "Menu caricato", "Da cache locale", 0.55);
                sleep(200);
            }

            if (com.api.SessionManager.isLoggedIn()) {
                setStep("mdi2c-clipboard-list", "Aggiornamento menu...", "Sincronizzazione server", 0.55);
                try {
                    com.google.gson.JsonObject fresh = com.api.services.ViewsService.getMenu();
                    MenuCache.save(fresh, "");
                    menuData = fresh;
                    setStep("mdi2c-clipboard-list", "Menu aggiornato", "Dati sincronizzati", 0.65);
                    sleep(300);
                } catch (Exception e) {
                    setStep(menuData != null ? "mdi2c-clipboard-list" : "mdi2a-alert-circle",
                            menuData != null ? "Menu da cache" : "Menu non disponibile",
                            menuData != null ? "Server non raggiungibile" : e.getMessage(), 0.65);
                    sleep(600);
                }
            } else {
                setStep("mdi2c-clipboard-list",
                        menuData != null ? "Menu da cache" : "Menu non disponibile",
                        loginError != null ? "Modalità offline" : "", 0.65);
                sleep(400);
            }

            // ── Step 3: Traduzioni ────────────────────────────────────
            setStep("mdi2e-earth", "Caricamento traduzioni...", "", 0.68);
            if (TranslationManager.isCached()) {
                TranslationManager.loadFromCache();
                setStep("mdi2e-earth", "Traduzioni caricate", "Da cache locale", 0.85);
                sleep(200);
                new Thread(TranslationManager::fetchAndSave, "trans-refresh").start();
            } else {
                TranslationManager.fetchAndSave();
                setStep("mdi2e-earth", "Traduzioni pronte", "", 0.85);
                sleep(300);
            }

            // ── Step 4: UI pronta ─────────────────────────────────────
            setStep("mdi2c-check-circle", "Pronto!", "", 1.0);
            sleep(400);

            // ── Step 5: BUILD Menù compose ─────────────────────────────────────
            build();
            sleep(300);

            // Naviga al WelcomeScreen passando il menu precaricato
            final com.google.gson.JsonObject finalMenu = menuData;
            Platform.runLater(() -> Navigator.goTo(Navigator.Screen.WELCOME, finalMenu));

        }, "splash-setup").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Aggiorna l'UI di stato della splash screen (thread-safe).
     * Estratto per eliminare la ripetizione di Platform.runLater() 12+ volte.
     */
    private void setStep(String icon, String step, String detail, double progress) {
        Platform.runLater(() -> {
            if (stepIconNode != null)
                stepIconNode.setIconLiteral(icon);
            if (stepLabel != null)
                stepLabel.setText(step);
            if (detailLabel != null)
                detailLabel.setText(detail);
            if (progressBar != null)
                progressBar.setProgress(progress);
        });
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void animateBackground() {
        for (StackPane circle : new StackPane[] { splashCircle1, splashCircle2 }) {
            if (circle == null)
                continue;
            RotateTransition rt = new RotateTransition(Duration.seconds(20 + Math.random() * 10), circle);
            rt.setByAngle(360);
            rt.setCycleCount(Animation.INDEFINITE);
            rt.play();
        }
    }

    private void build() {

    }
    // ── Animation helpers ─────────────────────────────────────────────

    private static PauseTransition pause(int ms) {
        return new PauseTransition(Duration.millis(ms));
    }

    private static FadeTransition fade(javafx.scene.Node n, double f, double t, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(f);
        ft.setToValue(t);
        return ft;
    }

    private static ScaleTransition scale(javafx.scene.Node n, double f, double t, int ms, Interpolator ip) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setFromX(f);
        st.setFromY(f);
        st.setToX(t);
        st.setToY(t);
        st.setInterpolator(ip);
        return st;
    }

    private static ParallelTransition parallel(Animation... a) {
        return new ParallelTransition(a);
    }
}
