package com.example.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.example.model.CartManager;
import com.example.model.I18n;
import com.util.Navigator;

import java.util.Random;

public class ConfirmController implements Navigator.DataReceiver {

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
        titleLabel.setText(I18n.t("confirm_title"));
        numberSubLabel.setText(I18n.t("confirm_sub"));
        msgLabel.setText(I18n.t("confirm_msg"));
        orderNumber.setText(String.valueOf(new Random().nextInt(99) + 1));

        CartManager.get().clear();

        // Prepariamo il timer in modo che parta sempre da un valore corretto
        // (evitiamo decrementi doppi/veloci in caso di più istanze o timer ancora attivi).
        countdownLabel.setText(AUTO_RETURN + "s");
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }

        // Nascondi tutto prima delle animazioni
        contentBox.setOpacity(0);
        checkCircle.setScaleX(0); checkCircle.setScaleY(0);
        bgCircle1.setScaleX(0);   bgCircle1.setScaleY(0);
        bgCircle2.setScaleX(0);   bgCircle2.setScaleY(0);
        bgCircle3.setScaleX(0);   bgCircle3.setScaleY(0);

        javafx.application.Platform.runLater(this::playAnimations);
    }

    @Override
    public void receiveData(Object data) {
        // data = numero ordine dal server (es. "2026-000042") o locale (es. "LOC-0001")
        if (data != null && orderNumber != null) {
            String numStr = data.toString();
            // Mostra solo la parte numerica finale per leggibilita
            if (numStr.contains("-")) {
                String[] parts = numStr.split("-");
                numStr = parts[parts.length - 1].replaceFirst("^0+", "");
                if (numStr.isEmpty()) numStr = "0";
            }
            orderNumber.setText(numStr);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ANIMAZIONI
    // ─────────────────────────────────────────────────────────────────

    private void playAnimations() {

        // 1. Sfondo: cerchi che si espandono (ripple effect)
        playRipple(bgCircle1, 0,    1.0,  600);
        playRipple(bgCircle2, 150,  1.0,  700);
        playRipple(bgCircle3, 300,  1.0,  800);

        // 2. Cerchio check: spring bounce
        SequentialTransition checkAnim = new SequentialTransition(
            pause(200),
            parallel(
                scale(checkCircle, 0, 1.25, 400, Interpolator.EASE_OUT),
                fade(checkCircle, 0, 1, 300)
            ),
            scale(checkCircle, 1.25, 0.92, 150, Interpolator.EASE_IN),
            scale(checkCircle, 0.92, 1.06, 120, Interpolator.EASE_OUT),
            scale(checkCircle, 1.06, 1.0,  100, Interpolator.EASE_IN)
        );
        checkAnim.play();

        // 3. Check icon pop-in dopo che il cerchio è comparso
        checkIconNode.setOpacity(0); checkIconNode.setScaleX(0.3); checkIconNode.setScaleY(0.3);
        SequentialTransition iconAnim = new SequentialTransition(
            pause(480),
            parallel(
                scale(checkIconNode, 0.3, 1.2, 200, Interpolator.EASE_OUT),
                fade(checkIconNode, 0, 1, 200)
            ),
            scale(checkIconNode, 1.2, 1.0, 120, Interpolator.EASE_IN)
        );
        iconAnim.play();

        // 4. Contenuto testo: fade + slide su
        contentBox.setTranslateY(40);
        SequentialTransition textAnim = new SequentialTransition(
            pause(600),
            parallel(
                fade(contentBox, 0, 1, 500),
                translate(contentBox, 40, 0, 500)
            )
        );
        textAnim.play();

        // 5. Numero ordine: scala drammatica
        orderNumber.setScaleX(0.2); orderNumber.setScaleY(0.2); orderNumber.setOpacity(0);
        SequentialTransition numAnim = new SequentialTransition(
            pause(900),
            parallel(
                scale(orderNumber, 0.2, 1.1, 400, Interpolator.EASE_OUT),
                fade(orderNumber, 0, 1, 300)
            ),
            scale(orderNumber, 1.1, 1.0, 150, Interpolator.EASE_IN)
        );
        numAnim.play();

        // 6. Pulse continuo sul cerchio (batte come un cuore)
        SequentialTransition pulse = new SequentialTransition(
            pause(1500),
            new SequentialTransition(
                scale(checkCircle, 1.0, 1.06, 300, Interpolator.EASE_OUT),
                scale(checkCircle, 1.06, 1.0,  300, Interpolator.EASE_IN)
            )
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();

        // 7. Cerchi di sfondo: rotazione lenta
        RotateTransition rot1 = new RotateTransition(Duration.seconds(20), bgCircle1);
        rot1.setByAngle(360); rot1.setCycleCount(Animation.INDEFINITE); rot1.play();
        RotateTransition rot2 = new RotateTransition(Duration.seconds(28), bgCircle2);
        rot2.setByAngle(-360); rot2.setCycleCount(Animation.INDEFINITE); rot2.play();

        // 8. Countdown auto-ritorno
        startCountdown();
    }

    private void playRipple(StackPane circle, int delayMs, double toScale, int durationMs) {
        SequentialTransition st = new SequentialTransition(
            pause(delayMs),
            parallel(
                scale(circle, 0, toScale, durationMs, Interpolator.EASE_OUT),
                fade(circle, 0, 0.15, durationMs)
            )
        );
        st.play();
    }

    private void startCountdown() {
        // Sicurezza: ferma eventuale timer precedente
        if (countdown != null) {
            countdown.stop();
        }

        final int[] secs = {AUTO_RETURN};
        countdownLabel.setText(secs[0] + "s");

        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secs[0]--;
            if (secs[0] > 0) {
                countdownLabel.setText(secs[0] + "s");
                // Anima il countdown
                ScaleTransition st = new ScaleTransition(Duration.millis(200), countdownLabel);
                st.setFromX(1.3); st.setFromY(1.3); st.setToX(1.0); st.setToY(1.0);
                st.play();
            } else {
                countdown.stop();
                Navigator.goTo(Navigator.Screen.WELCOME);
            }
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    // ── Helper animazione ──────────────────────────────────────────────

    private PauseTransition pause(int ms) {
        return new PauseTransition(Duration.millis(ms));
    }

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

    private TranslateTransition translate(javafx.scene.Node n, double fromY, double toY, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setFromY(fromY); tt.setToY(toY);
        tt.setInterpolator(Interpolator.EASE_OUT);
        return tt;
    }
}
