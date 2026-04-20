package com.app.pojo;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.List;

/**
 * Promotion POJO — Special menu category for promotional items
 * 
 * Fields:
 * - id: unique identifier
 * - nome: display name
 * - descrizione: description
 * - type: promo type (sconto, bundle, limited-time, etc)
 * - sconto: discount percentage or amount
 * - imageUrl: display image
 * - active: whether the promotion is active
 * - categoria: associated category
 */
public final class Promotion {

    public final int id;
    public final String nome;
    public final String descrizione;
    public final String type;
    public final String sconto;
    public final String imageUrl;
    public final boolean active;
    public final String categoria;

    private Promotion(int id, String nome, String descrizione, String type, String sconto, String imageUrl, boolean active, String categoria) {
        this.id = id;
        this.nome = nome != null ? nome : "";
        this.descrizione = descrizione != null ? descrizione : "";
        this.type = type != null ? type : "";
        this.sconto = sconto != null ? sconto : "";
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.active = active;
        this.categoria = categoria != null ? categoria : "";
    }

    /**
     * Parse Promotion from JsonObject
     */
    public static Promotion from(JsonObject obj) {
        if (obj == null)
            return null;
            
        try {
            int id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
            String nome = obj.has("nome") && !obj.get("nome").isJsonNull() ? obj.get("nome").getAsString() : "";
            String descrizione = obj.has("descrizione") && !obj.get("descrizione").isJsonNull() ? obj.get("descrizione").getAsString() : "";
            String type = obj.has("tipo") && !obj.get("tipo").isJsonNull() ? obj.get("tipo").getAsString() : (obj.has("type") && !obj.get("type").isJsonNull() ? obj.get("type").getAsString() : "");
            String sconto = obj.has("valore") && !obj.get("valore").isJsonNull() ? obj.get("valore").getAsString() : (obj.has("sconto") && !obj.get("sconto").isJsonNull() ? obj.get("sconto").getAsString() : "");
            String imageUrl = obj.has("imageUrl") && !obj.get("imageUrl").isJsonNull() ? obj.get("imageUrl").getAsString() : "";
            boolean active = obj.has("attiva") && !obj.get("attiva").isJsonNull() ? (obj.get("attiva").getAsInt() == 1 || obj.get("attiva").getAsBoolean()) : (obj.has("active") && !obj.get("active").isJsonNull() ? obj.get("active").getAsBoolean() : true);
            String categoria = obj.has("categoria") && !obj.get("categoria").isJsonNull() ? obj.get("categoria").getAsString() : "";

            return new Promotion(id, nome, descrizione, type, sconto, imageUrl, active, categoria);
        } catch (Exception e) {
            System.err.println("[Promotion] Errore parse: " + e.getMessage() + " / obj: " + obj.toString());
            return null;
        }
    }

    /**
     * Parse list of Promotions from JsonArray
     */
    public static List<Promotion> listFromJsonArray(JsonArray arr) {
        List<Promotion> promos = new ArrayList<>();
        if (arr == null)
            return promos;

        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).isJsonObject()) {
                Promotion promo = from(arr.get(i).getAsJsonObject());
                if (promo != null)
                    promos.add(promo);
            }
        }

        return promos;
    }

    /**
     * Convert Promotion to JsonObject
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("nome", nome);
        obj.addProperty("descrizione", descrizione);
        obj.addProperty("type", type);
        obj.addProperty("sconto", sconto);
        obj.addProperty("imageUrl", imageUrl);
        obj.addProperty("active", active);
        obj.addProperty("categoria", categoria);
        return obj;
    }

    @Override
    public String toString() {
        return "Promotion{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", type='" + type + '\'' +
                ", sconto='" + sconto + '\'' +
                ", active=" + active +
                '}';
    }
}
