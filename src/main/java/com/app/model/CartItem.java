package com.app.model;

import com.app.pojo.Product;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Voce del carrello.
 *
 * Refactoring rispetto all'originale:
 *  - Aggiunto Builder per costruzione leggibile
 *  - Costruzione diretta da {@link Product} POJO
 *  - Campi tipizzati (int productId, int iva invece di Object)
 *  - qtyProperty() per binding reattivo (invariato)
 */
public class CartItem {

    private final int              productId;
    private final String           name;
    private final String           priceLabel;   // "€ 7,50" (già formattato)
    private final String           category;     // categoria del prodotto (es. "Pizze", "Bibite")
    private final double           priceVal;
    private final int              iva;
    private final String           sku;
    private final List<String>     allergens;
    private final IntegerProperty  qty = new SimpleIntegerProperty(1);
    private List<String> ingredienti = List.of();
    // ── Costruttore privato (via Builder) ────────────────────────────

    private CartItem(Builder b) {
        this.productId  = b.productId;
        this.name       = b.name;
        this.priceLabel = b.priceLabel;
        this.priceVal   = b.priceVal;
        this.iva        = b.iva;
        this.sku        = b.sku;
        this.allergens  = Collections.unmodifiableList(b.allergens);
        this.category   = b.category;
        this.ingredienti = Collections.unmodifiableList(b.ingredienti);
        this.qty.set(b.qty);
    }

    // ── Factory methods ──────────────────────────────────────────────

    /** Crea un CartItem direttamente da un Product POJO. */
    public static CartItem fromProduct(Product p) {
        return new Builder(p.id, p.nome, p.prezzoFmt, p.prezzo)
                .iva(p.iva)
                .sku(p.sku)
                .allergens(p.allergeni)
                .category(p.category != null ? p.category : "")
                .ingredienti(p.ingredienti)
                .build();
    }

    /** Builder fluente per costruzione esplicita (es. da JSON legacy). */
    public static Builder builder(int productId, String name, String priceLabel, double priceVal) {
        return new Builder(productId, name, priceLabel, priceVal);
    }

    // ── Getters ──────────────────────────────────────────────────────

    public int           getProductId()  { return productId; }
    public String        getName()       { return name; }
    public String        getPrice()      { return priceLabel; }
    public double        getPriceVal()   { return priceVal; }
    public int           getIva()        { return iva; }
    public String        getSku()        { return sku; }
    public List<String>  getAllergens()  { return allergens; }
    public String        getCategory()  { return category; }
    public int           getQty()        { return qty.get(); }
    public void          setQty(int q)   { qty.set(q); }
    public IntegerProperty qtyProperty() { return qty; }
    public List<String> getIngredienti() { return ingredienti; }    
    // ── Calcoli ──────────────────────────────────────────────────────

    public double totalPrice()          { return Math.round(priceVal * qty.get() * 100.0) / 100.0; }
    public String totalFormatted()      {
        return String.format("€ %.2f", totalPrice()).replace('.', ',');
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static final class Builder {
        private final int    productId;
        private final String name;
        private final String priceLabel;
        private final double priceVal;

        private int          iva       = 10;
        private String       sku       = null;
        private String       category  = "";
        private List<String> allergens = new ArrayList<>();
        private List<String> ingredienti = new ArrayList<>();
        private int          qty       = 1;

        public Builder(int productId, String name, String priceLabel, double priceVal) {
            this.productId  = productId;
            this.name       = name;
            this.priceLabel = priceLabel;
            this.priceVal   = priceVal;
        }

        public Builder iva(int iva)               { this.iva = iva; return this; }
        public Builder sku(String sku)             { this.sku = sku; return this; }
        public Builder category(String category)   { this.category = category != null ? category : ""; return this; }
        public Builder allergens(List<String> al)  { if (al != null) this.allergens = al; return this; }
        public Builder ingredienti(List<String> v) { this.ingredienti = v; return this; }
        public Builder qty(int qty)                { this.qty = Math.max(1, qty); return this; }

        public CartItem build() { return new CartItem(this); }
    }
}
