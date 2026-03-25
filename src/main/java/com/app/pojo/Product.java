package com.app.pojo;

import com.google.gson.JsonObject;
import com.util.JsonHelper;

import java.util.List;

/**
 * POJO immutabile che rappresenta un prodotto del menu.
 *
 * Centralizza il parsing del JSON del server in un unico punto,
 * così i controller non devono più conoscere i nomi dei campi JSON.
 *
 * Costruzione: {@code Product.from(jsonObject)}
 */
public final class Product {

    public final int          id;
    public final String       nome;
    public final String       descrizione;
    public final String       category;
    public final double       prezzo;
    public final String       prezzoFmt;    // "€ 12,50"
    public final int          iva;
    public final String       sku;
    public final String       imageUrl;
    public final List<String> allergeni;
    public final List<String> ingredienti;  // nomi, non oggetti

    private Product(JsonObject j) {
        this.id          = JsonHelper.intVal(j, "id", 0);
        this.nome        = JsonHelper.str(j, "nome", "Prodotto");
        this.descrizione = JsonHelper.str(j, "descrizione");
        this.category    = JsonHelper.str(j, "categoria", "");
        this.prezzo      = JsonHelper.parsePrice(j, "prezzo");
        this.prezzoFmt   = JsonHelper.formatPrice(this.prezzo);
        this.iva         = JsonHelper.intVal(j, "iva", 10);
        this.sku         = JsonHelper.str(j, "sku");
        this.imageUrl    = JsonHelper.str(j, "immagine_url");
        this.allergeni   = JsonHelper.toStringList(JsonHelper.arr(j, "allergeni"));
        this.ingredienti = JsonHelper.toIngredientNames(JsonHelper.arr(j, "ingredienti"));
    }

    /**
     * Parsea un JsonObject del server in un Product.
     * @throws IllegalArgumentException se {@code json} è null
     */
    public static Product from(JsonObject json) {
        if (json == null) throw new IllegalArgumentException("json null");
        return new Product(json);
    }

    /** true se il prodotto ha un'immagine valida. */
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", nome='" + nome + "', prezzo=" + prezzo + "}";
    }
}
