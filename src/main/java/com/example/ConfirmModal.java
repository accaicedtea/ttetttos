package com.example;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class ConfirmModal {

    public static void show(StackPane parent, String title, String message, Runnable onConfirm) {
        try {
            FXMLLoader loader = new FXMLLoader(ConfirmModal.class.getResource("/com/example/ConfirmModal.fxml"));
            Node node = loader.load();
            ConfirmModalController controller = loader.getController();
            controller.show(title, message, onConfirm);

            // Remove any existing confirm modal to avoid duplicates
            parent.getChildren().remove(node);
            parent.getChildren().add(node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
