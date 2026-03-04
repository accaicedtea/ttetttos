package com.example.model;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modello prodotto — mappa la risposta di v_products_full.
 */
public class Product {

    private static final String IMAGE_BASE = "https://thisisnotmysite.altervista.org/mymenu/";

    public final int    id;
    public final String name;
    public final String slug;
    public final String description;
    public final String price;
    public final String imageRaw;
    public final String imageUrl;
    public final boolean isAvailable;
    public final boolean isFeatured;

    public final int    categoryId;
    public final String categoryName;
    public final String categorySlug;
    public final String categoryIcon;
    public final String categoryColor;

    public final String allergensRaw;
    public final String ingredientsRaw;

    public final List<Allergen>   allergens;
    public final List<Ingredient> ingredients;

    public Product(JsonObject o) {
        id          = getInt(o, "product_id");
        name        = getString(o, "product_name");
        slug        = getString(o, "product_slug");
        description = getString(o, "product_description");
        price       = getString(o, "price");
        isAvailable = "1".equals(getString(o, "is_available"));
        isFeatured  = "1".equals(getString(o, "is_featured"));

        imageRaw = getString(o, "image");
        imageUrl = buildImageUrl(imageRaw);

        categoryId    = getInt(o, "category_id");
        categoryName  = getString(o, "category_name");
        categorySlug  = getString(o, "category_slug");
        categoryIcon  = getString(o, "category_icon");
        categoryColor = getString(o, "category_color");

        allergensRaw   = getString(o, "allergens");
        ingredientsRaw = getString(o, "ingredients");

        allergens   = parseAllergens(allergensRaw);
        ingredients = parseIngredients(ingredientsRaw);
    }

    // ── Parsing liste ─────────────────────────────────────────────

    private static List<Allergen> parseAllergens(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<Allergen> list = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) list.add(new Allergen(trimmed));
        }
        return Collections.unmodifiableList(list);
    }

    private static List<Ingredient> parseIngredients(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<Ingredient> list = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) list.add(new Ingredient(trimmed));
        }
        return Collections.unmodifiableList(list);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static String getString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        return o.get(key).getAsString();
    }

    private static int getInt(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return 0;
        try { return Integer.parseInt(o.get(key).getAsString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String buildImageUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        if (raw.startsWith("/")) return IMAGE_BASE + raw.substring(1);
        return IMAGE_BASE + "images/products/" + raw;
    }

    public String getPriceFormatted() {
        if (price == null) return "";
        try {
            return String.format("€ %.2f", Double.parseDouble(price));
        } catch (NumberFormatException e) {
            return "€ " + price;
        }
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', category='" + categoryName
                + "', allergens=" + allergens.size()
                + ", ingredients=" + ingredients.size() + "}";
    }
}
