package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.api.services.AuthService;
import com.util.Navigator;
import com.util.NetworkWatchdog;
import com.util.SystemManager;

/**
 * SplashController — schermata di avvio con sequenza ottimizzata.
 *
 * FLUSSO SEMPLIFICATO (4 step):
 * 1) Login + salva token
 * 2) Check online
 * 3) Carica TUTTI i dati (menu+categorie+ingredienti) in UNA sola passata
 * 4) Naviga a Welcome
 *
 * (Eliminati i passi frammentati di traduzioni, aggiornamenti, build, ecc.)
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

            // ──────────────────────────────────────────────────────────────
            // SEMPLIFICATO: 4 STEP SOLI (era 10+)
            // ──────────────────────────────────────────────────────────────

            // ── STEP 1: Login + salva token ───────────────────────────────
            setStep("mdi2l-lock", "Connessione al server...", "", 0.25);
            com.api.services.InitializationService.InitData init = 
                com.api.services.InitializationService.initializeApp(API_KEY);
            
            if (init.error != null && init.menu == null) {
                // Errore critico — app bloccata
                setStep("mdi2a-alert-circle", "Errore critico", init.error, 1.0);
                System.err.println("[Splash] ERRORE: " + init.error);
                sleep(1500);
                if (com.util.SystemManager.isAppLocked()) {
                    return;
                }
            }

            // ── STEP 2: Check online ──────────────────────────────────────
            setStep("mdi2w-wifi" + (init.isOnline ? "" : "-off"), 
                    init.isOnline ? "Online" : "Offline", 
                    init.isOnline ? "" : "Modalità cache", 
                    0.50);
            sleep(300);

            // ── STEP 2.5: Avvia NetworkWatchdog per monitorare continuamente ─
            NetworkWatchdog.start(online -> {
                System.out.println("[Net] Stato: " + (online ? "Online" : "Offline"));
                // Show global toast
                if (com.app.App.globalToast != null) {
                    com.app.App.globalToast.show(online ? "Connessione internet ripristinata" : "Connessione internet persa: modalità offline");
                }
            });

            // ── STEP 3: Dati carificati ───────────────────────────────────
            setStep("mdi2c-check-circle", "Dati caricati", "Pronto!", 0.75);
            sleep(200);

            // ── STEP 4: Naviga a WELCOME ──────────────────────────────────
            setStep("mdi2c-check-circle", "Pronto!", "", 1.0);
            sleep(300);

            final Object finalMenu = init.menu;
            Platform.runLater(() -> Navigator.goTo(Navigator.Screen.PRESENTATION, finalMenu));

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
