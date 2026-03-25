package com.api.services;

import com.api.Api;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class IngredientService {

    private IngredientService() {}

    public static JsonArray getIngredients(boolean onlyAvailable) throws Exception {
        String endpoint = "ingredienti";
        if (onlyAvailable) endpoint += "?disponibile=1";
        JsonObject resp = Api.apiGet(endpoint);
        if (resp.has("data") && resp.get("data").isJsonArray()) {
            return resp.getAsJsonArray("data");
        }
        if (resp.has("ingredienti") && resp.get("ingredienti").isJsonArray()) {
            return resp.getAsJsonArray("ingredienti");
        }
        // fallback: if response is direct JsonArray object cant be handled here.
        return new JsonArray();
    }

    public static JsonArray searchIngredients(String term) throws Exception {
        String q = "ingredienti?disponibile=1";
        if (term != null && !term.isBlank()) {
            q += "&cerca=" + java.net.URLEncoder.encode(term.trim(), java.nio.charset.StandardCharsets.UTF_8);
        }
        JsonObject resp = Api.apiGet(q);
        if (resp.has("data") && resp.get("data").isJsonArray()) {
            return resp.getAsJsonArray("data");
        }
        if (resp.has("ingredienti") && resp.get("ingredienti").isJsonArray()) {
            return resp.getAsJsonArray("ingredienti");
        }
        return new JsonArray();
    }
}
