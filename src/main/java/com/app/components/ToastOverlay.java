package com.app.components;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Toast "notifica breve" che appare in basso e svanisce.
 *
 * Estratto da ShopPageController.showToast() dove era duplicato con logica
 * inline non riutilizzabile.
 *
 * Uso:
 *   ToastOverlay toast = new ToastOverlay();
 *   rootStack.getChildren().add(toast);         // aggiunto allo StackPane principale
 *   StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
 *   toast.show("Aggiunto!");
 */
public class ToastOverlay extends HBox {

    private static final int SHOW_MS   = 1400;  // durata di visibilità piena
    private static final int FADE_MS   = 250;   // fade in / fade out

    private final Label label;
    private Timeline  timeline;

    public ToastOverlay() {
        getStyleClass().add("toast-box");
        setAlignment(Pos.CENTER);
        setSpacing(10);

        label = new Label();
        label.getStyleClass().add("toast-label");
        getChildren().add(label);

        setVisible(false);
        setManaged(false);

        // Posizionamento nel StackPane padre
        StackPane.setAlignment(this, Pos.BOTTOM_CENTER);
        StackPane.setMargin(this, new javafx.geometry.Insets(0, 0, 40, 0));
    }

    /** Mostra il toast con il messaggio dato per la durata predefinita. */
    public void show(String message) {
        if (timeline != null) timeline.stop();

        label.setText(message);
        setOpacity(0);
        setTranslateY(20);
        setVisible(true);
        setManaged(true);

        timeline = new Timeline(
            new KeyFrame(Duration.millis(0),
                new KeyValue(opacityProperty(),    0),
                new KeyValue(translateYProperty(), 20)),
            new KeyFrame(Duration.millis(FADE_MS),
                new KeyValue(opacityProperty(),    1),
                new KeyValue(translateYProperty(), 0)),
            new KeyFrame(Duration.millis(FADE_MS + SHOW_MS),
                new KeyValue(opacityProperty(),    1)),
            new KeyFrame(Duration.millis(FADE_MS + SHOW_MS + FADE_MS),
                new KeyValue(opacityProperty(),    0))
        );
        timeline.setOnFinished(e -> { setVisible(false); setManaged(false); });
        timeline.play();
    }
}
