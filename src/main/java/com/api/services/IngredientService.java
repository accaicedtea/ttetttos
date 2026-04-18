package com.api.services;

import com.api.Api;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class IngredientService {

    private IngredientService() {
    }

    public static JsonArray getIngredients() throws Exception {
        String endpoint = "prodotti/ingredienti";

        JsonObject resp = Api.apiGet(endpoint);
        
        // --- INIZIO DEBUG/LOG RICHIESTO DAL CLIENTE ---
        System.out.println("[DEBUG IngredientService] Risposta grezza dal server: " + resp.toString());
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of(System.getProperty("user.dir"), "debug_ingredienti_loaded.json"), 
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(resp)
            );
            System.out.println("[DEBUG IngredientService] File JSON debug salvato in: debug_ingredienti_loaded.json");
        } catch (Exception e) {
            System.err.println("[DEBUG IngredientService] Errore salvataggio JSON locale: " + e.getMessage());
        }
        // --- FINE DEBUG ---

        if (resp.has("data")) {
            if (resp.get("data").isJsonObject()) {
                JsonObject dataObj = resp.getAsJsonObject("data");
                if (dataObj.has("ingredienti") && dataObj.get("ingredienti").isJsonArray()) {
                    return dataObj.getAsJsonArray("ingredienti");
                }
            } else if (resp.get("data").isJsonArray()) {
                return resp.getAsJsonArray("data");
            }
        }
        if (resp.has("ingredienti") && resp.get("ingredienti").isJsonArray()) {
            return resp.getAsJsonArray("ingredienti");
        }
        // fallback: if response is direct JsonArray object cant be handled here.
        return new JsonArray();
    }

    public static JsonArray searchIngredients(String term) throws Exception {
        String q = "prodotti/ingredienti?disponibile=1";
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
