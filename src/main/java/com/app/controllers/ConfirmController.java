package com.app.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.app.model.CartManager;
import com.util.Navigator;

import java.util.Random;

/**
 * ConfirmController — schermata di conferma ordine.
 * Rispetto all'originale: estende BaseController per t() e setVisible().
 * La logica animazioni rimane identica.
 */
public class ConfirmController extends BaseController implements Navigator.DataReceiver {

    @FXML private StackPane confirmRoot;
    @FXML private StackPane bgCircle1, bgCircle2, bgCircle3;
    @FXML private StackPane checkCircle;
    @FXML private FontIcon  checkIconNode;
    @FXML private VBox      contentBox;
    @FXML private Label     titleLabel;
    @FXML private Label     numberSubLabel;
    @FXML private Label     orderNumber;
    @FXML private Label     msgLabel;
    @FXML private Label     countdownLabel;

    private static final int AUTO_RETURN = 10;
    private Timeline countdown;

    @FXML
    private void initialize() {
        this.rootStack = confirmRoot;

        t(titleLabel,      "confirm_title");
        t(numberSubLabel,  "confirm_sub");
        t(msgLabel,        "confirm_msg");
        if (orderNumber != null) orderNumber.setText(String.valueOf(new Random().nextInt(99) + 1));

        CartManager.get().clear();

        countdownLabel.setText(AUTO_RETURN + "s");
        if (countdown != null) { countdown.stop(); countdown = null; }

        contentBox.setOpacity(0);
        checkCircle.setScaleX(0); checkCircle.setScaleY(0);
        bgCircle1.setScaleX(0); bgCircle1.setScaleY(0);
        bgCircle2.setScaleX(0); bgCircle2.setScaleY(0);
        bgCircle3.setScaleX(0); bgCircle3.setScaleY(0);

        Platform.runLater(this::playAnimations);
    }

    @Override
    public void receiveData(Object data) {
        if (data != null && orderNumber != null) {
            String numStr = data.toString();
            if (numStr.contains("-")) {
                String[] parts = numStr.split("-");
                numStr = parts[parts.length - 1].replaceFirst("^0+", "");
                if (numStr.isEmpty()) numStr = "0";
            }
            orderNumber.setText(numStr);
        }
    }

    // ── Animazioni (invariate dall'originale) ─────────────────────────

    private void playAnimations() {
        playRipple(bgCircle1, 0,   600);
        playRipple(bgCircle2, 150, 700);
        playRipple(bgCircle3, 300, 800);

        new SequentialTransition(
            pause(200), parallel(scale(checkCircle,0,1.25,400,Interpolator.EASE_OUT), fade(checkCircle,0,1,300)),
            scale(checkCircle,1.25,0.92,150,Interpolator.EASE_IN),
            scale(checkCircle,0.92,1.06,120,Interpolator.EASE_OUT),
            scale(checkCircle,1.06,1.0,100,Interpolator.EASE_IN)
        ).play();

        checkIconNode.setOpacity(0); checkIconNode.setScaleX(0.3); checkIconNode.setScaleY(0.3);
        new SequentialTransition(pause(480),
            parallel(scale(checkIconNode,0.3,1.2,200,Interpolator.EASE_OUT), fade(checkIconNode,0,1,200)),
            scale(checkIconNode,1.2,1.0,120,Interpolator.EASE_IN)
        ).play();

        contentBox.setTranslateY(40);
        new SequentialTransition(pause(600), parallel(fade(contentBox,0,1,500), translate(contentBox,40,0,500))).play();

        orderNumber.setScaleX(0.2); orderNumber.setScaleY(0.2); orderNumber.setOpacity(0);
        new SequentialTransition(pause(900),
            parallel(scale(orderNumber,0.2,1.1,400,Interpolator.EASE_OUT), fade(orderNumber,0,1,300)),
            scale(orderNumber,1.1,1.0,150,Interpolator.EASE_IN)
        ).play();

        SequentialTransition pulse = new SequentialTransition(pause(1500),
            new SequentialTransition(scale(checkCircle,1.0,1.06,300,Interpolator.EASE_OUT), scale(checkCircle,1.06,1.0,300,Interpolator.EASE_IN)));
        pulse.setCycleCount(Animation.INDEFINITE); pulse.play();

        RotateTransition rt1 = new RotateTransition(Duration.seconds(20), bgCircle1);
        rt1.setByAngle(360);
        rt1.setCycleCount(Animation.INDEFINITE);
        rt1.play();

        RotateTransition rt2 = new RotateTransition(Duration.seconds(28), bgCircle2);
        rt2.setByAngle(-360);
        rt2.setCycleCount(Animation.INDEFINITE);
        rt2.play();

        startCountdown();
    }

    private void playRipple(StackPane circle, int delayMs, int durationMs) {
        new SequentialTransition(
            pause(delayMs),
            parallel(scale(circle,0,1.0,durationMs,Interpolator.EASE_OUT), fade(circle,0,0.6,durationMs/2))
        ).play();
    }

    private void startCountdown() {
        final int[] remaining = {AUTO_RETURN};
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remaining[0]--;
            if (countdownLabel != null) countdownLabel.setText(remaining[0] + "s");
            if (remaining[0] <= 0) { countdown.stop(); Navigator.goTo(Navigator.Screen.WELCOME); }
        }));
        countdown.setCycleCount(AUTO_RETURN);
        countdown.play();
    }

    // ── Animation helpers ─────────────────────────────────────────────

    private static PauseTransition       pause(int ms)                                { PauseTransition p=new PauseTransition(Duration.millis(ms)); return p; }
    private static FadeTransition        fade(javafx.scene.Node n, double f, double t, int ms) { FadeTransition ft=new FadeTransition(Duration.millis(ms),n); ft.setFromValue(f); ft.setToValue(t); return ft; }
    private static ScaleTransition       scale(javafx.scene.Node n, double f, double t, int ms, Interpolator ip) { ScaleTransition st=new ScaleTransition(Duration.millis(ms),n); st.setFromX(f); st.setFromY(f); st.setToX(t); st.setToY(t); st.setInterpolator(ip); return st; }
    private static TranslateTransition   translate(javafx.scene.Node n, double f, double t, int ms) { TranslateTransition tt=new TranslateTransition(Duration.millis(ms),n); tt.setFromY(f); tt.setToY(t); return tt; }
    private static ParallelTransition    parallel(Animation... a) { return new ParallelTransition(a); }
}
