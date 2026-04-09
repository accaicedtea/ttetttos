package com.app.components;

/**
 * Raccolta di tutti i pattern d'uso di ModalDialog.
 *
 * Non è una classe da deployare — serve come documentazione viva
 * e come test che tutti i factory method compilino correttamente.
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 1 — Info
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.info(rootPane, "Informazione", "Operazione completata.");
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 2 — Warning con bottoni custom
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.warning(rootPane, "Attenzione", "Menu non aggiornato.",
 * ModalButton.ghost("Ignora", null),
 * ModalButton.primary("Riprova", this::reloadMenu));
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 3 — Errore
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.error(rootPane, "Errore di rete", exception.getMessage());
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 4 — Conferma (es. eliminazione)
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.confirm(rootPane,
 * "Svuota carrello",
 * "Vuoi rimuovere tutti i prodotti?",
 * () -> CartManager.get().clear());
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 5 — Conferma pericolosa (rosso)
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.confirmDanger(rootPane,
 * "Elimina prodotto",
 * "Questa azione è irreversibile.",
 * "Elimina definitivamente",
 * () -> deleteProduct(id));
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 6 — Input testuale
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.input(rootPane,
 * "Nome promozione",
 * "Es. Sconto estate 20%",
 * "",
 * nomeInserito -> {
 * promozione.setNome(nomeInserito);
 * save(promozione);
 * });
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 7 — Loading (no interazione utente)
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog loading = ModalDialog.loading(rootPane, "Invio ordine...");
 * new Thread(() -> {
 * try { sendOrder(); }
 * finally { Platform.runLater(loading::dismiss); }
 * }).start();
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 8 — Successo con auto-dismiss
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.success(rootPane, "Salvato!", "Le modifiche sono state
 * applicate.", 2500);
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 9 — Custom con Node arbitrario
 * ─────────────────────────────────────────────────────────────────
 *
 * VBox detailContent = buildProductDetailView(product);
 * ModalDialog.custom(rootPane, product.nome, detailContent,
 * ModalButton.ghost("Chiudi", null),
 * ModalButton.primary("Aggiungi al carrello",
 * () -> CartManager.get().addItem(CartItem.fromProduct(product))));
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 10 — Builder completo
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog dialog = ModalDialog.builder(rootPane)
 * .type(ModalDialog.Type.WARNING)
 * .title("Totem offline")
 * .subtitle("Ultimo ping: 10 minuti fa")
 * .message("Il totem 'Cassa 2' non risponde. Verificare la connessione.")
 * .icon("📡")
 * .width(520)
 * .closeOnBackdrop(false)
 * .closeOnEscape(true)
 * .animation(ModalDialog.Animation.SCALE)
 * .position(ModalDialog.Position.CENTER)
 * .onClose(() -> System.out.println("modal chiuso"))
 * .button(ModalButton.ghost("Ignora", null))
 * .button(ModalButton.warning("Riavvia totem", () -> restartTotem(id)))
 * .button(ModalButton.danger("Disattiva", () -> disableTotem(id)))
 * .show();
 *
 * // In seguito, se necessario:
 * dialog.dismiss();
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 11 — Modal annidati (uno dentro l'altro)
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog.confirm(rootPane, "Elimina?", "Sicuro?", () ->
 * ModalDialog.success(rootPane, "Eliminato!", null, 1800));
 *
 * ─────────────────────────────────────────────────────────────────
 * PATTERN 12 — Dismissal programmatico da operazione async
 * ─────────────────────────────────────────────────────────────────
 *
 * ModalDialog saving = ModalDialog.loading(rootPane, "Salvataggio...");
 * orderQueue.submitOrder(cart, method, orderNumber -> {
 * saving.dismiss();
 * ModalDialog.success(rootPane, "Ordine #" + orderNumber, "Buon appetito!",
 * 3000);
 * Navigator.goTo(Navigator.Screen.CONFIRM, orderNumber);
 * });
 */
public final class ModalDialogExamples {
    private ModalDialogExamples() {
    } // solo documentazione
}
