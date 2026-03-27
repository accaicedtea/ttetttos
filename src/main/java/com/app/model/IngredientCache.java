package com.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IngredientCache {

    private static List<Ingredient> cached = new ArrayList<>();

    public static synchronized List<Ingredient> get() {
        return Collections.unmodifiableList(new ArrayList<>(cached));
    }

    public static synchronized void set(List<Ingredient> ingredients) {
        if (ingredients == null) {
            cached = new ArrayList<>();
        } else {
            cached = new ArrayList<>(ingredients);
        }
    }

    public static synchronized boolean has() {
        return !cached.isEmpty();
    }

    public static synchronized void clear() {
        cached = new ArrayList<>();
    }
}
