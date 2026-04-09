package com.util;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility null-safe per la lettura di JsonObject.
 *
 * Estratto da ShopPageController dove viveva come metodi privati.
 * Ora disponibile a controller, POJO e servizi.
 */
public final class JsonHelper {

    private JsonHelper() {
    }

    // ── Stringhe ─────────────────────────────────────────────────────

    public static String str(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull())
            return fallback;
        return o.get(key).getAsString();
    }

    public static String str(JsonObject o, String key) {
        return str(o, key, "");
    }

    // ── Numeri ───────────────────────────────────────────────────────

    public static int intVal(JsonObject o, String key, int fallback) {
        try {
            return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static double doubleVal(JsonObject o, String key, double fallback) {
        try {
            return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ── Array / Oggetti ──────────────────────────────────────────────

    public static JsonArray arr(JsonObject o, String key) {
        return (o != null && o.has(key) && o.get(key).isJsonArray()) ? o.getAsJsonArray(key) : null;
    }

    public static JsonObject obj(JsonObject o, String key) {
        return (o != null && o.has(key) && o.get(key).isJsonObject()) ? o.getAsJsonObject(key) : null;
    }

    // ── Conversioni ──────────────────────────────────────────────────

    /** JsonArray di stringhe → List<String> (salta null). */
    public static List<String> toStringList(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null)
            return out;
        for (JsonElement el : arr)
            if (!el.isJsonNull())
                out.add(el.getAsString());
        return out;
    }

    /** JsonArray di oggetti-ingrediente (con campo "nome") → List<String>. */
    public static List<String> toIngredientNames(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null)
            return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject())
                continue;
            String name = str(el.getAsJsonObject(), "nome");
            if (!name.isBlank())
                out.add(name);
        }
        return out;
    }

    // ── Prezzi ───────────────────────────────────────────────────────

    /** Legge prezzo come double, gestisce sia numeri sia stringhe "12,50". */
    public static double parsePrice(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull())
            return 0.0;
        try {
            return Double.parseDouble(o.get(key).getAsString().replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Formatta double come "€ 12,50". Ritorna "" se prezzo ≤ 0. */
    public static String formatPrice(double price) {
        return price <= 0 ? "" : String.format("€ %.2f", price).replace('.', ',');
    }
}
