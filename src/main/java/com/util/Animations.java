package com.util;

import javafx.animation.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Animazioni e feedback touch per JavaFX.
 *
 * FIX touch doppio-click:
 * - touchFeedback NON usa MOUSE_CLICKED (che può arrivare tardi su touch).
 * Usa MOUSE_PRESSED per il feedback visivo e lascia il click propagarsi
 * SEMPRE al nodo figlio tramite il suo handler registrato.
 * - Il pulse è puramente estetico e non consuma mai l'evento.
 *
 * FIX scroll che consuma click:
 * - inertiaScroll consuma MOUSE_CLICKED solo quando c'è stato un vero drag
 * (spostamento > TAP_THRESH pixel). Click brevi non vengono mai consumati.
 */
public class Animations {

    // ── Touch feedback ─────────────────────────────────────────────────────────

    /**
     * Aggiunge feedback visivo (piccolo pulse di scala) su press.
     *
     * IMPORTANTE: non usa addEventHandler(MOUSE_CLICKED) perché su touch
     * screen il click sintetico arriva con ritardo e interferisce con i
     * gestori dell'app. Usa MOUSE_PRESSED per il feedback visivo (immediato)
     * e non consuma MAI l'evento — la propagazione rimane intatta.
     */
    public static void touchFeedback(javafx.scene.Node node) {
        ScaleTransition down = new ScaleTransition(Duration.millis(70), node);
        down.setToX(0.93);
        down.setToY(0.93);
        down.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition up = new ScaleTransition(Duration.millis(120), node);
        up.setToX(1.0);
        up.setToY(1.0);
        up.setInterpolator(Interpolator.EASE_OUT);

        SequentialTransition pulse = new SequentialTransition(down, up);

        // PRESS → avvia animazione (non consuma)
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.isSynthesized()) {
                pulse.stop();
                node.setScaleX(1.0);
                node.setScaleY(1.0);
                pulse.play();
            }
        });

        // RELEASED → assicura che la scala torni a 1 se il press è stato breve
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (node.getScaleX() != 1.0 || node.getScaleY() != 1.0) {
                ScaleTransition reset = new ScaleTransition(Duration.millis(80), node);
                reset.setToX(1.0);
                reset.setToY(1.0);
                reset.play();
            }
        });
    }

    // ── Scale utilities ────────────────────────────────────────────────────────

    public static void scaleUp(javafx.scene.Node node, double durationMs, double scaleUp) {
        double dur = durationMs > 0 ? durationMs : 120;
        double su = scaleUp > 0 ? scaleUp : 1.04;
        ScaleTransition up = new ScaleTransition(Duration.millis(dur), node);
        up.setToX(su);
        up.setToY(su);
        ScaleTransition down = new ScaleTransition(Duration.millis(dur), node);
        down.setToX(1.0);
        down.setToY(1.0);
        new SequentialTransition(up, down).play();
    }

    public static void scaleDown(javafx.scene.Node node, double durationMs, double scaleDown) {
        double dur = durationMs > 0 ? durationMs : 120;
        double sd = scaleDown > 0 ? scaleDown : 0.96;
        ScaleTransition down = new ScaleTransition(Duration.millis(dur), node);
        down.setToX(sd);
        down.setToY(sd);
        ScaleTransition up = new ScaleTransition(Duration.millis(dur), node);
        up.setToX(1.0);
        up.setToY(1.0);
        new SequentialTransition(down, up).play();
    }

    // ── Inertia scroll ─────────────────────────────────────────────────────────

    /**
     * Scroll inerziale touch-friendly.
     *
     * FIX click: MOUSE_CLICKED viene consumato SOLO se c'è stato un drag
     * effettivo (> TAP_THRESH px). Un tap breve non viene mai consumato,
     * quindi i click sulle card funzionano sempre al primo tocco.
     */
    public static void inertiaScroll(ScrollPane pane) {
        pane.setPannable(false);

        final double FRICTION = Math.log(2) / 400.0;
        final double MAX_VEL = 10.0;
        final double TAP_THRESH = 12.0; // pixel — soglia più alta = meno falsi drag
        final int BUF = 10;

        final double[] bufY = new double[BUF];
        final long[] bufT = new long[BUF];
        final int[] head = { 0 };
        final int[] size = { 0 };
        final double[] vel = { 0 };
        final double[] pressY = { 0 };
        final double[] pressX = { 0 };
        final double[] sh = { 0 };
        final boolean[] dragging = { false };
        final boolean[] moved = { false }; // true se lo spostamento supera soglia

        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            private long prevNs = 0;

            @Override
            public void handle(long now) {
                if (prevNs == 0) {
                    prevNs = now;
                    return;
                }
                double dt = Math.min((now - prevNs) / 1_000_000.0, 32.0);
                prevNs = now;
                if (sh[0] <= 0) {
                    stop();
                    return;
                }
                pane.setVvalue(clamp01(pane.getVvalue() + vel[0] * dt / sh[0]));
                vel[0] *= Math.max(0.0, 1.0 - FRICTION * dt);
                if (Math.abs(vel[0]) < 0.001) {
                    vel[0] = 0;
                    prevNs = 0;
                    stop();
                }
            }
        };

        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            timer.stop();
            vel[0] = 0;
            head[0] = 0;
            size[0] = 0;
            pressY[0] = e.getSceneY();
            pressX[0] = e.getSceneX();
            dragging[0] = false;
            moved[0] = false;
            sh[0] = scrollableHeight(pane);
            bufY[0] = e.getSceneY();
            bufT[0] = System.nanoTime();
            head[0] = 1;
            size[0] = 1;
        });

        // Mouse wheel / trackpad
        pane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (Math.abs(e.getDeltaY()) < 0.01)
                return;
            timer.stop();
            vel[0] = 0;
            double shLocal = scrollableHeight(pane);
            if (shLocal <= 0)
                return;
            pane.setVvalue(clamp01(pane.getVvalue() - e.getDeltaY() / shLocal));
            e.consume();
        });

        pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            long now = System.nanoTime();

            double dx = Math.abs(e.getSceneX() - pressX[0]);
            double dy = Math.abs(e.getSceneY() - pressY[0]);

            // Drag solo se principalmente verticale e oltre la soglia
            if (!moved[0] && (dx > TAP_THRESH || dy > TAP_THRESH)) {
                moved[0] = true;
                // Considera drag solo se il gesto è prevalentemente verticale
                dragging[0] = dy > dx;
            }

            int idx = head[0] % BUF;
            bufY[idx] = e.getSceneY();
            bufT[idx] = now;
            head[0]++;
            if (size[0] < BUF)
                size[0]++;

            if (!dragging[0] || sh[0] <= 0)
                return;
            int prev = ((head[0] - 2) % BUF + BUF) % BUF;
            double dtMs = (now - bufT[prev]) / 1_000_000.0;
            if (dtMs <= 0)
                return;
            pane.setVvalue(clamp01(pane.getVvalue()
                    + (bufY[prev] - e.getSceneY()) / sh[0]));
        });

        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!dragging[0] || size[0] < 2)
                return;
            long now = System.nanoTime();
            int newest = ((head[0] - 1) % BUF + BUF) % BUF;
            double ny = bufY[newest];
            long nt = bufT[newest];
            double oy = ny;
            long ot = nt;
            for (int i = 1; i < size[0]; i++) {
                int idx = ((head[0] - 1 - i) % BUF + BUF) % BUF;
                if ((now - bufT[idx]) / 1_000_000.0 > 80.0)
                    break;
                oy = bufY[idx];
                ot = bufT[idx];
            }
            double dtMs = (nt - ot) / 1_000_000.0;
            if (dtMs > 2.0) {
                vel[0] = Math.max(-MAX_VEL, Math.min(MAX_VEL, (oy - ny) / dtMs));
                if (Math.abs(vel[0]) > 0.05) {
                    sh[0] = scrollableHeight(pane);
                    timer.start();
                }
            }
        });

        // Consuma MOUSE_CLICKED solo se c'è stato un drag verticale reale
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (dragging[0]) {
                e.consume();
                dragging[0] = false;
                moved[0] = false;
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static double scrollableHeight(ScrollPane p) {
        if (p.getContent() == null)
            return 0;
        return Math.max(0,
                p.getContent().getBoundsInLocal().getHeight()
                        - p.getViewportBounds().getHeight());
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
