package com.app.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Dialog di conferma modale riutilizzabile.
 *
 * Originale: due file separati (ConfirmModal.java + ConfirmModalController.java)
 * con responsabilità sovrapposte. Ora è una singola classe che gestisce sia il
 * caricamento dell'FXML sia la logica del controller.
 *
 * Uso static (più comune):
 *   ConfirmModal.show(parentPane, "Titolo", "Messaggio", () -> doAction());
 *
 * Uso come controller FXML:
 *   Annotare i field @FXML e usare normalmente.
 */
public class ConfirmModal {

    // ── Uso come controller FXML ─────────────────────────────────────

    @FXML private StackPane confirmRoot;
    @FXML private Label     titleLabel;
    @FXML private Label     msgLabel;
    @FXML private Button    confirmBtn;
    @FXML private Button    cancelBtn;

    private Runnable onConfirm;

    @FXML
    private void initialize() {
        if (cancelBtn  != null) cancelBtn.setOnAction(e -> close());
        if (confirmBtn != null) confirmBtn.setOnAction(e -> { close(); if (onConfirm != null) onConfirm.run(); });
    }

    public void configure(String title, String message, Runnable onConfirm) {
        this.onConfirm = onConfirm;
        if (titleLabel != null) titleLabel.setText(title);
        if (msgLabel   != null) msgLabel.setText(message);
    }

    public void open() {
        BaseController.setVisible(confirmRoot, true);
    }

    public void close() {
        BaseController.setVisible(confirmRoot, false);
    }

    // ── Uso statico (factory) ────────────────────────────────────────

    /**
     * Mostra un dialog di conferma aggiungendolo a {@code parent}.
     * Il dialog si rimuove automaticamente alla chiusura.
     *
     * @param parent    StackPane radice (es. App.rootPane)
     * @param title     titolo del dialog
     * @param message   corpo del messaggio
     * @param onConfirm callback eseguita se l'utente conferma
     */
    public static void show(StackPane parent, String title, String message, Runnable onConfirm) {
        if (parent == null) { if (onConfirm != null) onConfirm.run(); return; }
        try {
            FXMLLoader loader = new FXMLLoader(
                ConfirmModal.class.getResource("/com/app/ConfirmModal.fxml"));
            Node node = loader.load();
            ConfirmModal ctrl = loader.getController();

            // Override chiusura: rimuove anche il nodo dal parent
            ctrl.onConfirm = () -> { parent.getChildren().remove(node); if (onConfirm != null) onConfirm.run(); };
            if (ctrl.cancelBtn  != null) ctrl.cancelBtn.setOnAction(e  -> parent.getChildren().remove(node));
            if (ctrl.confirmBtn != null) ctrl.confirmBtn.setOnAction(e -> { parent.getChildren().remove(node); if (onConfirm != null) onConfirm.run(); });

            if (ctrl.titleLabel != null) ctrl.titleLabel.setText(title);
            if (ctrl.msgLabel   != null) ctrl.msgLabel.setText(message);

            parent.getChildren().add(node);
        } catch (Exception e) {
            // Fallback: esegui l'azione direttamente se l'FXML non è disponibile
            System.err.println("[ConfirmModal] FXML non caricabile: " + e.getMessage());
            if (onConfirm != null) onConfirm.run();
        }
    }
}
