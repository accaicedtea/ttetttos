package com.app.pojo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.util.JsonHelper;
import com.util.RemoteLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ingredient {
    public final int id;
    public final String nome;
    public final boolean disponibile;
    public final List<String> allergeni;
    public final double prezzo;

    public Ingredient(int id, String nome, boolean disponibile, List<String> allergeni, double prezzo) {
        this.id = id;
        this.nome = nome != null ? nome : "";
        this.disponibile = disponibile;
        this.allergeni = Collections.unmodifiableList(allergeni != null ? allergeni : List.of());
        this.prezzo = prezzo;
    }

    public static Ingredient fromJson(JsonObject j) {
        System.out.println("[Ingredient] fromJson: loading ingredient from JSON: ");
        if (j == null)
            return null;
        int id = JsonHelper.intVal(j, "id", 0);
        String nome = JsonHelper.str(j, "nome", "");
        
        // Verifica se disponibile è boolean o int dal server CodeIgniter
        boolean disponibile;
        if (j.has("disponibile") && j.get("disponibile").isJsonPrimitive() && j.get("disponibile").getAsJsonPrimitive().isBoolean()) {
            disponibile = j.get("disponibile").getAsBoolean();
        } else {
            disponibile = JsonHelper.intVal(j, "disponibile", 0) == 1;
        }

        List<String> allergeni = JsonHelper.toStringList(JsonHelper.arr(j, "allergeni"));
        double prezzo = JsonHelper.doubleVal(j, "prezzo", 0.0);
        return new Ingredient(id, nome, disponibile, allergeni, prezzo);
    }

    public static List<Ingredient> listFromJsonArray(JsonArray arr) {
        if (arr == null)
            return List.of();
        List<Ingredient> ret = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                Ingredient ing = fromJson(e.getAsJsonObject());
                if (ing != null)
                    ret.add(ing);
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return nome;
    }
}
