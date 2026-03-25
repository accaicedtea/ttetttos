package com.app.pojo;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * POJO che rappresenta l'intera risposta /menu.
 *
 * Gestisce le varianti di struttura del server:
 *   { data: { categorie: [...] } }
 *   { data: { menu: [...] } }
 *   { data: [...] }
 *   { categorie: [...] }
 */
public final class MenuData {

    public final List<Category> categorie;

    private MenuData(List<Category> categorie) {
        this.categorie = Collections.unmodifiableList(categorie);
    }

    /**
     * Parsea la risposta completa dell'API /menu.
     * Ritorna un MenuData con lista vuota se il JSON non è riconoscibile.
     */
    public static MenuData from(JsonObject response) {
        JsonArray raw = extractCategories(response);
        List<Category> list = new ArrayList<>();
        if (raw != null) {
            for (JsonElement el : raw) {
                if (el.isJsonObject()) {
                    try { list.add(Category.from(el.getAsJsonObject())); }
                    catch (Exception ignored) {}
                }
            }
        }
        return new MenuData(list);
    }

    public boolean isEmpty() { return categorie.isEmpty(); }
    public int size()        { return categorie.size(); }

    // ── Parsing struttura risposta ────────────────────────────────────

    private static JsonArray extractCategories(JsonObject r) {
        if (r == null) return null;
        if (r.has("data")) {
            JsonElement data = r.get("data");
            if (data.isJsonObject()) {
                JsonObject d = data.getAsJsonObject();
                if (d.has("categorie") && d.get("categorie").isJsonArray()) return d.getAsJsonArray("categorie");
                if (d.has("menu")      && d.get("menu").isJsonArray())      return d.getAsJsonArray("menu");
            }
            if (data.isJsonArray()) return data.getAsJsonArray();
        }
        if (r.has("categorie") && r.get("categorie").isJsonArray()) return r.getAsJsonArray("categorie");
        return null;
    }
}
