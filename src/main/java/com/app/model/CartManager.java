package com.app.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * CartManager — singleton per il carrello dell'ordine.
 *
 * Refactoring rispetto all'originale:
 * - addItem(CartItem) invece del metodo con 6 parametri primitivi
 * - Mantiene addItem(int, String, String, double, int, String, List<String>)
 * per retrocompatibilità, ma ora delega a CartItem.builder()
 * - OrderQueue.saveCurrentOrder() rimane invariato
 */
public class CartManager {

    private static final CartManager INSTANCE = new CartManager();
    private final ObservableList<CartItem> items = FXCollections.observableArrayList();

    private CartManager() {
    }

    public static CartManager get() {
        return INSTANCE;
    }

    // ── API pubblica ──────────────────────────────────────────────────

    /** Aggiunge un CartItem già costruito (via Builder o fromProduct). */
    public void addItem(CartItem item) {
        // Prova a raggruppare con elemento esistente con stesso nome
        for (CartItem existing : items) {
            if (existing.getName().equals(item.getName())) {
                existing.setQty(existing.getQty() + item.getQty());
                OrderQueue.saveCurrentOrder(this);
                return;
            }
        }
        items.add(item);
        OrderQueue.saveCurrentOrder(this);
    }

    /**
     * @deprecated Usa {@link #addItem(CartItem)} con CartItem.builder() o
     *             CartItem.fromProduct().
     */
    @Deprecated
    public void addItem(int productId, String name, String price, double priceVal,
            int iva, String sku, List<String> allergens) {
        addItem(CartItem.builder(productId, name, price, priceVal)
                .iva(iva).sku(sku).allergens(allergens).build());
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        OrderQueue.saveCurrentOrder(this);
    }

    public ObservableList<CartItem> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int totalItems() {
        return items.stream().mapToInt(CartItem::getQty).sum();
    }

    public double totalPrice() {
        return Math.round(items.stream().mapToDouble(CartItem::totalPrice).sum() * 100.0) / 100.0;
    }

    public String totalPriceFormatted() {
        return String.format("€ %.2f", totalPrice()).replace('.', ',');
    }

    public void clear() {
        items.clear();
        OrderQueue.clearCurrentOrder();
    }

    // ── Funzionalità extra Suggerisci prodotti se non ci sono nel cattello
    // ─────────────────────────────────
    // Se nel carrello non ci sono prodotti della categoria "Bevande", suggerisce di
    // aggiungere una bevanda.
    // Se nel carrello non ci sono prodotti della categoria "kumpir", suggerisce di
    // aggiungere un kumpir.
    // Se nel carrello non ci sono prodotti della categoria "Dessert", suggerisce di
    // aggiungere un dessert.
    public String suggestProducts() {
        if (items.stream()
                .noneMatch(item -> item.getCategory() != null && item.getCategory().equalsIgnoreCase("Bevande"))) {
            // Suggerisci bevande
            System.out.println("Suggerimento: Aggiungi una bevanda al tuo ordine!");
            return "Bevande";
        } else if (items.stream()
                .noneMatch(item -> item.getCategory() != null && item.getCategory().equalsIgnoreCase("kumpir"))) {
            // Suggerisci kumpir
            System.out.println("Suggerimento: Aggiungi un kumpir al tuo ordine!");
            return "kumpir";
        } else if (items.stream()
                .noneMatch(item -> item.getCategory() != null && item.getCategory().equalsIgnoreCase("Dessert"))) {
            // Suggerisci dessert
            System.out.println("Suggerimento: Aggiungi un dessert al tuo ordine!");
            return "Dessert";
        }
        return null;
    }
}
