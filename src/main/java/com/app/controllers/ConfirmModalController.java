package com.app.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class ConfirmModalController {

    @FXML private StackPane confirmRoot;
    @FXML private Label titleLabel;
    @FXML private Label msgLabel;
    @FXML private Button confirmBtn;
    @FXML private Button cancelBtn;

    private Runnable onConfirm;

    @FXML
    private void initialize() {
        if (cancelBtn != null) cancelBtn.setOnAction(e -> close());
        if (confirmBtn != null) confirmBtn.setOnAction(e -> {
            close();
            if (onConfirm != null) onConfirm.run();
        });
    }

    public void show(String title, String message, Runnable onConfirm) {
        this.onConfirm = onConfirm;
        if (titleLabel != null) titleLabel.setText(title);
        if (msgLabel != null) msgLabel.setText(message);
        if (confirmRoot != null) {
            confirmRoot.setVisible(true);
            confirmRoot.setManaged(true);
        }
    }

    public void close() {
        if (confirmRoot != null) {
            confirmRoot.setVisible(false);
            confirmRoot.setManaged(false);
        }
    }
}
