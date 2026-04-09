package com.app.pojo;

import com.google.gson.*;
import com.util.JsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * POJO immutabile che rappresenta una categoria del menu con i relativi
 * prodotti.
 */
public final class Category {

    public final int id;
    public final String nome;
    public final String descrizione;
    public final List<Product> prodotti;

    private Category(JsonObject j) {
        this.id = JsonHelper.intVal(j, "id", 0);
        this.nome = JsonHelper.str(j, "nome", "Categoria");
        this.descrizione = JsonHelper.str(j, "descrizione");

        List<Product> list = new ArrayList<>();
        JsonArray arr = JsonHelper.arr(j, "prodotti");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    try {
                        list.add(Product.from(el.getAsJsonObject()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        this.prodotti = Collections.unmodifiableList(list);
    }

    public static Category from(JsonObject json) {
        if (json == null)
            throw new IllegalArgumentException("json null");
        return new Category(json);
    }

    public boolean isEmpty() {
        return prodotti.isEmpty();
    }

    @Override
    public String toString() {
        return "Category{nome='" + nome + "', prodotti=" + prodotti.size() + "}";
    }
}
