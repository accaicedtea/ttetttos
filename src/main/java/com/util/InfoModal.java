package com.util;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.geometry.Pos;

public class InfoModal {
    public static void show(StackPane parent, String title, String message, Runnable onConfirm) {
        VBox modalBox = new VBox(18);
        modalBox.setAlignment(Pos.CENTER);
        modalBox.setStyle(
                "-fx-background-color: #fff; -fx-padding: 32; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, #00000055, 18, 0, 0, 4);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #222;");
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #444;");
        msgLabel.setWrapText(true);

        Button confirmBtn = new Button("CONFERMA");
        confirmBtn.setStyle(
                "-fx-font-size: 16px; -fx-background-color: #5b9cf5; -fx-text-fill: white; -fx-background-radius: 8;");
        confirmBtn.setOnAction(e -> {
            parent.getChildren().remove(modalBox);
            if (onConfirm != null)
                onConfirm.run();
        });

        Button closeBtn = new Button("ANNULLA");
        closeBtn.setStyle(
                "-fx-font-size: 16px; -fx-background-color: #e05555; -fx-text-fill: white; -fx-background-radius: 8;");
        closeBtn.setOnAction(e -> parent.getChildren().remove(modalBox));

        modalBox.getChildren().addAll(titleLabel, msgLabel, confirmBtn, closeBtn);

        parent.getChildren().remove(modalBox);
        parent.getChildren().add(modalBox);
    }
}
