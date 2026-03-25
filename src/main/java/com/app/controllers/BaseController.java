package com.app.controllers;

import com.app.components.ToastOverlay;
import com.app.model.I18n;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Controller base con funzionalità condivise tra tutti i controller dell'app.
 *
 * Problema originale: ogni controller re-implementava:
 *  - Binding testi I18n
 *  - Toast (showToast) — duplicato in ShopPageController e CartController
 *  - Pattern di show/hide elementi
 *
 * I controller concreti estendono questa classe e chiamano i metodi
 * helper anziché duplicare il codice.
 */
public abstract class BaseController {

    /** StackPane radice della schermata — impostato dal controller concreto. */
    protected StackPane rootStack;

    /** Toast overlay — inizializzato lazy la prima volta che si usa showToast(). */
    private ToastOverlay toast;

    // ── I18n ─────────────────────────────────────────────────────────

    /**
     * Imposta il testo di una Label con la traduzione della chiave I18n.
     * Null-safe: ignora label null.
     */
    protected void t(Label label, String key) {
        if (label != null) label.setText(I18n.t(key));
    }

    /** Restituisce la traduzione della chiave corrente. */
    protected String t(String key) { return I18n.t(key); }

    // ── Toast ─────────────────────────────────────────────────────────

    /**
     * Mostra un toast breve sovrapposto allo schermo.
     * Richiede che {@link #rootStack} sia impostato.
     *
     * @param message messaggio da mostrare (già tradotto)
     */
    protected void showToast(String message) {
        if (rootStack == null) return;
        if (toast == null) {
            toast = new ToastOverlay();
            if (!rootStack.getChildren().contains(toast)) {
                rootStack.getChildren().add(toast);
            }
        }
        toast.show(message);
    }

    // ── Show/hide helper ─────────────────────────────────────────────

    /** Imposta visibilità e managed insieme (entrambi devono cambiare). */
    protected static void setVisible(javafx.scene.Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
