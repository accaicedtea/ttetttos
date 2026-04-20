package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.util.Navigator;

/**
 * PresentationController — Schermata di presentazione iniziale.
 * 
 * Animazioni elaborate con effetto sipario teatrale:
 * - Animazioni di apertura con transizioni smooth
 * - Click per interrompere e andare a WELCOME
 * - Supporto per video (opzionale in futuro)
 */
public class PresentationController extends BaseController {

    @FXML
    private StackPane rootPane;
    @FXML
    private VBox presentationContent;
    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private FontIcon presentationIcon;
    @FXML
    private Label clickToContinueLabel;

    private Animation presentationTimeline;
    private boolean isPlaying = true;

    @FXML
    private void initialize() {
        this.rootStack = rootPane;

        // Setup styling
        if (presentationContent != null) {
            presentationContent.setStyle("-fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);");
        }

        // Setup click listener per continuare
        if (rootPane != null) {
            rootPane.setOnMouseClicked(e -> skipPresentation());
        }

        // Avvia animazioni direttamente (non da runLater)
        playPresentationAnimation();
    }

    /**
     * Animazione principale della presentazione con effetto sipario.
     * Combina: fade, scale, slide, rotate per un effetto coinvolgente
     */
    private void playPresentationAnimation() {
        if (presentationContent == null) return;

        // Crea animazione dello sfondo
        createBackgroundAnimation();
        
        // Crea stelle animate
        createAnimatedStars();

        presentationContent.setOpacity(0);
        presentationContent.setScaleX(0.85);
        presentationContent.setScaleY(0.85);
        presentationContent.setTranslateY(-20);

        // ═════════════════════════════════════════════════════════════
        // FASE 1: APERTURA CON EFFETTO SIPARIO (0-2.5 secondi)
        // ═════════════════════════════════════════════════════════════
        
        SequentialTransition phase1 = new SequentialTransition(
            new ParallelTransition(
                createFadeTransition(presentationContent, 0, 1, 1200),
                createTranslateTransition(presentationContent, -20, 0, 1200),
                createScaleTransition(presentationContent, 0.85, 1.0, 1200, javafx.animation.Interpolator.EASE_OUT)
            )
        );

        // ═════════════════════════════════════════════════════════════
        // FASE 2: ICONA CON EFFETTO 3D COMPLESSO (2.5-6 secondi)
        // ═════════════════════════════════════════════════════════════

        SequentialTransition phase2 = null;
        if (presentationIcon != null) {
            presentationIcon.setOpacity(0);
            presentationIcon.setScaleX(0);
            presentationIcon.setScaleY(0);
            presentationIcon.setRotate(45);

            phase2 = new SequentialTransition(
                // Apparizione con bounce e rotazione
                new ParallelTransition(
                    createFadeTransition(presentationIcon, 0, 1, 700),
                    createScaleTransition(presentationIcon, 0, 1.3, 700, javafx.animation.Interpolator.EASE_OUT),
                    createRotateTransition(presentationIcon, 45, 0, 700)
                ),
                // Rimpicciolimento rapido (bounce effect)
                createScaleTransition(presentationIcon, 1.3, 1.05, 300, javafx.animation.Interpolator.EASE_IN),
                // Ingrandimento leggero
                createScaleTransition(presentationIcon, 1.05, 1.0, 200, javafx.animation.Interpolator.EASE_OUT),
                // Rotazione continua veloce con pulsing
                new ParallelTransition(
                    createRotationAnimation(3, 360),
                    createPulsingScaleAnimation(presentationIcon, 1.0, 1.15, 4000)
                )
            );
        }

        // ═════════════════════════════════════════════════════════════
        // FASE 3: TITOLO CON GLOW EFFECT (4.5-6.5 secondi)
        // ═════════════════════════════════════════════════════════════

        SequentialTransition phase3 = null;
        if (titleLabel != null) {
            titleLabel.setOpacity(0);
            titleLabel.setTranslateX(-100);
            
            phase3 = new SequentialTransition(
                new PauseTransition(Duration.millis(400)),
                // Slide in da sinistra con fade
                new ParallelTransition(
                    createFadeTransition(titleLabel, 0, 1, 800),
                    createTranslateTransition(titleLabel, -100, 0, 800)
                ),
                // Glow pulsing (cambio di colore)
                createGlowAnimation(titleLabel)
            );
        }

        // ═════════════════════════════════════════════════════════════
        // FASE 4: SOTTOTITOLO CON SLIDE (5.5-7 secondi)
        // ═════════════════════════════════════════════════════════════

        if (subtitleLabel != null) {
            subtitleLabel.setOpacity(0);
            subtitleLabel.setTranslateX(100);
            
            SequentialTransition subtitleAnim = new SequentialTransition(
                new PauseTransition(Duration.millis(1000)),
                // Slide in da destra con fade
                new ParallelTransition(
                    createFadeTransition(subtitleLabel, 0, 1, 800),
                    createTranslateTransition(subtitleLabel, 100, 0, 800)
                )
            );
            subtitleAnim.play();
        }

        // ═════════════════════════════════════════════════════════════
        // FASE 5: INDICAZIONE "CLICCA PER CONTINUARE" (7-10+ secondi)
        // ═════════════════════════════════════════════════════════════

        SequentialTransition phase5 = null;
        if (clickToContinueLabel != null) {
            clickToContinueLabel.setOpacity(0);
            clickToContinueLabel.setScaleX(0.8);
            clickToContinueLabel.setScaleY(0.8);
            
            phase5 = new SequentialTransition(
                new PauseTransition(Duration.millis(2500)),
                // Apparizione con scale
                new ParallelTransition(
                    createFadeTransition(clickToContinueLabel, 0, 1, 500),
                    createScaleTransition(clickToContinueLabel, 0.8, 1.0, 500, javafx.animation.Interpolator.EASE_OUT)
                ),
                // Pulsing infinito
                createInfinitePulsingAnimation(clickToContinueLabel)
            );
        }

        // ═════════════════════════════════════════════════════════════
        // COMBINA TUTTE LE FASI
        // ═════════════════════════════════════════════════════════════

        SequentialTransition allPhases = new SequentialTransition();
        allPhases.getChildren().add(phase1);
        
        if (phase2 != null) {
            allPhases.getChildren().add(phase2);
        }
        if (phase3 != null) {
            allPhases.getChildren().add(phase3);
        }
        if (phase5 != null) {
            allPhases.getChildren().add(phase5);
        }

        presentationTimeline = allPhases;

        // Auto-advance dopo 12 secondi se non clickato
        presentationTimeline.setOnFinished(e -> {
            if (isPlaying) {
                Platform.runLater(this::autoAdvanceToWelcome);
            }
        });

        presentationTimeline.play();
    }

    /**
     * Animazione dello sfondo con gradient che si muove
     */
    private void createBackgroundAnimation() {
        if (rootPane == null) return;
        
        // Crea un rettangolo di sfondo animato
        Rectangle bgAnimation = new Rectangle();
        bgAnimation.setWidth(rootPane.getWidth() > 0 ? rootPane.getWidth() : 800);
        bgAnimation.setHeight(rootPane.getHeight() > 0 ? rootPane.getHeight() : 600);
        bgAnimation.setOpacity(0.3);
        bgAnimation.setStyle("-fx-fill: linear-gradient(45deg, #ff6b6b, #4ecdc4, #45b7d1);");
        bgAnimation.setVisible(false); // Nascondi, userà l'effetto di sfondo principale
    }

    /**
     * Crea stelle animate che compaiono e scompaiono nello sfondo
     */
    private void createAnimatedStars() {
        if (rootPane == null) return;
        
        for (int i = 0; i < 8; i++) {
            Circle star = new Circle(3 + Math.random() * 4);
            star.setFill(Color.web("#ffffff", 0.6));
            
            double startX = Math.random() * 700 - 100;
            double startY = Math.random() * 500 - 100;
            
            star.setLayoutX(startX);
            star.setLayoutY(startY);
            
            rootPane.getChildren().add(0, star); // Aggiungi dietro il contenuto
            
            // Animazione individuale per ogni stella
            SequentialTransition starAnim = new SequentialTransition(
                new PauseTransition(Duration.millis(i * 300)),
                new ParallelTransition(
                    // Fade in
                    createFadeTransition(star, 0, 0.8, 800),
                    // Movimento verso l'alto e verso il lato
                    createTranslateTransitionByValue(star, (Math.random() - 0.5) * 200, -150 - Math.random() * 100, 3000),
                    // Rotazione
                    createRotateTransitionByValue(star, 360 * (Math.random() > 0.5 ? 1 : -1), 3000),
                    // Scale variabile
                    createScaleTransition(star, 1.0, 0.3, 3000, javafx.animation.Interpolator.EASE_IN)
                ),
                // Fade out
                createFadeTransition(star, 0.8, 0, 500)
            );
            starAnim.setCycleCount(Animation.INDEFINITE);
            starAnim.play();
        }
    }

    /**
     * Animazione di rotazione continua per l'icona
     */
    private RotateTransition createRotationAnimation(double seconds, double byAngle) {
        if (presentationIcon == null) return null;
        
        RotateTransition rt = new RotateTransition(Duration.seconds(seconds), presentationIcon);
        rt.setByAngle(byAngle);
        rt.setCycleCount(Animation.INDEFINITE);
        return rt;
    }

    /**
     * Pulsing scale per icona durante rotazione
     */
    private ScaleTransition createPulsingScaleAnimation(javafx.scene.Node node, double minScale, double maxScale, double millis) {
        ScaleTransition st = new ScaleTransition(Duration.millis(millis / 2), node);
        st.setFromX(minScale);
        st.setFromY(minScale);
        st.setToX(maxScale);
        st.setToY(maxScale);
        st.setAutoReverse(true);
        st.setCycleCount(Animation.INDEFINITE);
        st.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        return st;
    }

    /**
     * Helper per creare TranslateTransition
     */
    private TranslateTransition createTranslateTransition(javafx.scene.Node node, double fromX, double toX, double millis) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(millis), node);
        tt.setFromX(fromX);
        tt.setToX(toX);
        tt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        return tt;
    }

    /**
     * Helper per creare TranslateTransition con valori BY
     */
    private TranslateTransition createTranslateTransitionByValue(javafx.scene.Node node, double byX, double byY, double millis) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(millis), node);
        tt.setByX(byX);
        tt.setByY(byY);
        tt.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        return tt;
    }

    /**
     * Helper per creare RotateTransition
     */
    private RotateTransition createRotateTransition(javafx.scene.Node node, double fromAngle, double toAngle, double millis) {
        RotateTransition rt = new RotateTransition(Duration.millis(millis), node);
        rt.setFromAngle(fromAngle);
        rt.setToAngle(toAngle);
        rt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        return rt;
    }

    /**
     * Helper per creare RotateTransition con valore BY
     */
    private RotateTransition createRotateTransitionByValue(javafx.scene.Node node, double byAngle, double millis) {
        RotateTransition rt = new RotateTransition(Duration.millis(millis), node);
        rt.setByAngle(byAngle);
        rt.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        return rt;
    }

    /**
     * Helper per creare ScaleTransition
     */
    private ScaleTransition createScaleTransition(javafx.scene.Node node, double fromScale, double toScale, 
                                                   double millis, javafx.animation.Interpolator interpolator) {
        ScaleTransition st = new ScaleTransition(Duration.millis(millis), node);
        st.setFromX(fromScale);
        st.setFromY(fromScale);
        st.setToX(toScale);
        st.setToY(toScale);
        st.setInterpolator(interpolator);
        return st;
    }

    /**
     * Helper per creare FadeTransition
     */
    private FadeTransition createFadeTransition(javafx.scene.Node node, double from, double to, double millis) {
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    /**
     * Effetto glow per il titolo
     */
    private SequentialTransition createGlowAnimation(Label label) {
        SequentialTransition glow = new SequentialTransition();
        
        // Ripeti il glow 3 volte con fade del shadow
        for (int i = 0; i < 3; i++) {
            glow.getChildren().add(new PauseTransition(Duration.millis(500)));
            glow.getChildren().add(createFadeTransition(label, 1.0, 0.8, 300));
            glow.getChildren().add(createFadeTransition(label, 0.8, 1.0, 300));
        }
        
        return glow;
    }

    /**
     * Pulsing infinito per il "clicca per continuare"
     */
    private SequentialTransition createInfinitePulsingAnimation(javafx.scene.Node node) {
        SequentialTransition pulsing = new SequentialTransition(
            createScaleTransition(node, 1.0, 1.1, 600, javafx.animation.Interpolator.EASE_OUT),
            createScaleTransition(node, 1.1, 1.0, 600, javafx.animation.Interpolator.EASE_IN)
        );
        pulsing.setCycleCount(Animation.INDEFINITE);
        return pulsing;
    }

    /**
     * Effetto sipario teatrale quando si skip
     */
    private void skipPresentation() {
        if (!isPlaying) return;
        
        isPlaying = false;
        
        if (presentationTimeline != null) {
            presentationTimeline.stop();
        }

        // Effetto sipario di chiusura
        SequentialTransition curtainEffect = new SequentialTransition(
            // Veloce fade out con scale down
            new ParallelTransition(
                createFadeTransition(presentationContent, 1, 0, 600),
                createScaleTransition(presentationContent, 1.0, 0.95, 600, javafx.animation.Interpolator.EASE_IN)
            )
        );

        curtainEffect.setOnFinished(e -> goToWelcome());
        curtainEffect.play();
    }

    /**
     * Advance automatico a WELCOME dopo il timeout
     */
    private void autoAdvanceToWelcome() {
        skipPresentation();
    }

    /**
     * Naviga a WELCOME
     */
    private void goToWelcome() {
        Platform.runLater(() -> Navigator.goTo(Navigator.Screen.WELCOME));
    }
}
