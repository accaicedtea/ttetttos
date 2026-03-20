package com.app.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * Singleton che gestisce il carrello globale.
 * Accessibile da qualsiasi controller con CartManager.get()
 */
public class CartManager {

    private static final CartManager INSTANCE = new CartManager();
    private CartManager() {}
    public static CartManager get() { return INSTANCE; }

    private final ObservableList<CartItem> items =
            FXCollections.observableArrayList();

    // Lingua selezionata nella welcome screen ("it" / "en" / "de" / "fr" / "ar")
    private String language = "it";

    // ── Carrello ──────────────────────────────────────────────────────

    public ObservableList<CartItem> getItems() { return items; }

    /** Aggiunge un prodotto. Se già presente incrementa la quantità. */
    /** Compatibilita: senza productId/iva. */
    public void addItem(String name, String priceFormatted, double priceVal) {
        addItem(0, name, priceFormatted, priceVal, 10, null, null);
    }

    /** Compatibilita: con solo allergeni. */
    public void addItem(String name, String priceFormatted, double priceVal,
                        java.util.List<String> allergens) {
        addItem(0, name, priceFormatted, priceVal, 10, null, allergens);
    }

    /**
     * Versione completa: con productId, iva e sku dal menu.
     * Usare questa versione per inviare ordini corretti al server.
     */
    public void addItem(int productId, String name, String priceFormatted,
                        double priceVal, int iva, String sku,
                        java.util.List<String> allergens) {
        Optional<CartItem> existing = items.stream()
                .filter(i -> i.getName().equals(name))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQty(existing.get().getQty() + 1);
        } else {
            items.add(new CartItem(productId, name, priceFormatted,
                                   priceVal, iva, sku, allergens));
        }
        // Salva ordine corrente su disco (crash recovery)
        OrderQueue.saveCurrentOrder(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        OrderQueue.saveCurrentOrder(this);
    }

    public void clear() { items.clear(); }

    public boolean isEmpty() { return items.isEmpty(); }

    public int totalItems() {
        return items.stream().mapToInt(CartItem::getQty).sum();
    }

    public double totalPrice() {
        return items.stream().mapToDouble(CartItem::total).sum();
    }

    public String totalPriceFormatted() {
        return String.format("€ %.2f", totalPrice()).replace('.', ',');
    }

    // ── Lingua ────────────────────────────────────────────────────────

    public String getLanguage() { return language; }
    public void   setLanguage(String lang) { language = lang; }
}
