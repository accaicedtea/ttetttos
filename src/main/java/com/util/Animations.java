package com.util;

import javafx.animation.AnimationTimer;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public class Animations {
    // Scale up (on press) and down (on release) for a simple touch feedback effect.
    // It can be used for any Node, but is especially effective on buttons and
    // interactive controls.
    // Default scale up is 1.04 (4% larger), and scale down is 0.96 (4% smaller),
    // with a duration of 120ms.
    @FXML
    public static void scaleUp(
            javafx.scene.Node animatedItem,
            double durationMillis,
            double scaleUpValue) {
        double duration = durationMillis > 0 ? durationMillis : 120;
        double scaleUp = scaleUpValue > 0 ? scaleUpValue : 1.04;

        ScaleTransition scaleUpTransition = new ScaleTransition(Duration.millis(duration), animatedItem);
        scaleUpTransition.setToX(scaleUp);
        scaleUpTransition.setToY(scaleUp);

        ScaleTransition scaleDownTransition = new ScaleTransition(Duration.millis(duration), animatedItem);
        scaleDownTransition.setToX(1.0);
        scaleDownTransition.setToY(1.0);

        SequentialTransition seq = new SequentialTransition(scaleUpTransition, scaleDownTransition);
        seq.play();
    }

    @FXML
    public static void scaleDown(
            javafx.scene.Node animatedItem,
            double durationMillis,
            double scaleDownValue) {
        double duration = durationMillis > 0 ? durationMillis : 120;
        double scaleDown = scaleDownValue > 0 ? scaleDownValue : 0.96;
        ScaleTransition scaleDownTransition = new ScaleTransition(Duration.millis(duration), animatedItem);
        scaleDownTransition.setToX(scaleDown);
        scaleDownTransition.setToY(scaleDown);

        ScaleTransition scaleUpTransition = new ScaleTransition(Duration.millis(duration), animatedItem);
        scaleUpTransition.setToX(1.0);
        scaleUpTransition.setToY(1.0);

        SequentialTransition seq = new SequentialTransition(scaleDownTransition, scaleUpTransition);
        seq.play();
    }

    @FXML
    public static void flip(
            javafx.scene.Node flipFront,
            javafx.scene.Node flipBack) {
        Duration half = Duration.millis(200);

        boolean showBack = !flipBack.isVisible();

        javafx.animation.RotateTransition p1 = new javafx.animation.RotateTransition(half,
                showBack ? flipFront : flipBack);
        p1.setAxis(javafx.geometry.Point3D.ZERO.add(0, 1, 0)); // Rotate.Y_AXIS
        p1.setFromAngle(0);
        p1.setToAngle(90);
        p1.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        p1.setOnFinished(e -> {
            flipFront.setVisible(!showBack);
            flipBack.setVisible(showBack);

            javafx.animation.RotateTransition p2 = new javafx.animation.RotateTransition(half,
                    showBack ? flipBack : flipFront);
            p2.setAxis(javafx.geometry.Point3D.ZERO.add(0, 1, 0)); // Rotate.Y_AXIS
            p2.setFromAngle(-90);
            p2.setToAngle(0);
            p2.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            p2.play();
        });
        p1.play();
    }

    /**
     * Attaches inertia (momentum) scrolling to any ScrollPane.
     * Works with mouse drag and touch (JavaFX synthesizes MouseEvents from touch).
     *
     * Optimizations vs naive implementation:
     *  - scrollableHeight computed ONCE per gesture (not every frame/event)
     *  - friction via cheap linear approx instead of Math.pow every frame
     *  - dtMs capped at 32ms to prevent post-freeze jumps
     *  - velocity capped at ±10 px/ms to prevent flying
     *  - MOUSE_CLICKED consumed in capture phase when drag occurred
     */
    public static void inertiaScroll(ScrollPane pane) {
        pane.setPannable(false);

        final double   FRICTION   = Math.log(2) / 400.0; // half-life 400 ms
        final double   MAX_VEL    = 10.0;                 // px/ms
        final double   TAP_THRESH = 8.0;                  // px
        final int      BUF        = 10;
        final double[] bufY       = new double[BUF];
        final long[]   bufT       = new long[BUF];
        final int[]    head       = {0};
        final int[]    size       = {0};
        final double[] vel        = {0};
        final double[] pressY     = {0};
        final double[] sh         = {0};     // scrollable height cached per gesture
        final boolean[] dragging  = {false};

        AnimationTimer timer = new AnimationTimer() {
            private long prevNs = 0;
            @Override public void handle(long now) {
                if (prevNs == 0) { prevNs = now; return; }
                double dt = Math.min((now - prevNs) / 1_000_000.0, 32.0); // cap 1 frame
                prevNs = now;
                if (sh[0] <= 0) { stop(); return; }
                pane.setVvalue(clamp01(pane.getVvalue() + vel[0] * dt / sh[0]));
                vel[0] *= Math.max(0.0, 1.0 - FRICTION * dt);  // cheap approximation
                if (Math.abs(vel[0]) < 0.001) { vel[0] = 0; prevNs = 0; stop(); }
            }
        };

        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            timer.stop(); vel[0] = 0;
            head[0] = 0; size[0] = 0;
            pressY[0] = e.getSceneY();
            dragging[0] = false;
            sh[0] = scrollableHeight(pane);   // compute once per gesture
            bufY[0] = e.getSceneY(); bufT[0] = System.nanoTime();
            head[0] = 1; size[0] = 1;
        });

        pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            long now = System.nanoTime();
            if (!dragging[0] && Math.abs(e.getSceneY() - pressY[0]) > TAP_THRESH)
                dragging[0] = true;

            // registra sempre nel buffer (serve per la velocità al rilascio)
            int idx = head[0] % BUF;
            bufY[idx] = e.getSceneY(); bufT[idx] = now;
            head[0]++;
            if (size[0] < BUF) size[0]++;

            if (!dragging[0] || sh[0] <= 0) return;
            int prev = ((head[0] - 2) % BUF + BUF) % BUF;
            double dtMs = (now - bufT[prev]) / 1_000_000.0;
            if (dtMs <= 0) return;
            pane.setVvalue(clamp01(pane.getVvalue() + (bufY[prev] - e.getSceneY()) / sh[0]));
        });

        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!dragging[0] || size[0] < 2) return;
            long now = System.nanoTime();
            int newest = ((head[0] - 1) % BUF + BUF) % BUF;
            double ny = bufY[newest]; long nt = bufT[newest];
            double oy = ny; long ot = nt;
            for (int i = 1; i < size[0]; i++) {
                int idx = ((head[0] - 1 - i) % BUF + BUF) % BUF;
                if ((now - bufT[idx]) / 1_000_000.0 > 80.0) break;
                oy = bufY[idx]; ot = bufT[idx];
            }
            double dtMs = (nt - ot) / 1_000_000.0;
            if (dtMs > 2.0) {
                vel[0] = Math.max(-MAX_VEL, Math.min(MAX_VEL, (oy - ny) / dtMs));
                if (Math.abs(vel[0]) > 0.05) {
                    sh[0] = scrollableHeight(pane); // aggiorna prima dell'inerzia
                    timer.start();
                }
            }
        });

        // Fase capture: consuma CLICK se era uno scroll → i figli non ricevono nulla
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (dragging[0]) { e.consume(); dragging[0] = false; }
        });
    }

    /**
     * Attaches touch tap feedback (quick scale pulse) to any Node.
     * Triggers on MOUSE_CLICKED only — so it never fires during scrolls,
     * since inertiaScroll consumes MOUSE_CLICKED when a drag occurred.
     * Hover highlight is managed via explicit class (not CSS :hover)
     * so touch never leaves the card "stuck" in highlighted state.
     */
    public static void touchFeedback(javafx.scene.Node node) {
        ScaleTransition down = new ScaleTransition(Duration.millis(80), node);
        down.setToX(0.94); down.setToY(0.94);
        ScaleTransition up = new ScaleTransition(Duration.millis(80), node);
        up.setToX(1.0); up.setToY(1.0);
        SequentialTransition pulse = new SequentialTransition(down, up);

        // Tap feedback: solo su click vero (consumato dall'inertiaScroll durante drag)
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            pulse.stop();
            node.setScaleX(1.0); node.setScaleY(1.0);
            pulse.play();
        });
    }

    private static double scrollableHeight(ScrollPane p) {
        if (p.getContent() == null) return 0;
        double ch = p.getContent().getBoundsInLocal().getHeight();
        double vh = p.getViewportBounds().getHeight();
        return Math.max(0, ch - vh);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
