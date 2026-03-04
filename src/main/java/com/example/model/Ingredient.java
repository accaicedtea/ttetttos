package com.example.model;

/**
 * Ingrediente associato a un prodotto.
 * Formato raw: "id:nome:quantità:isAllergenico"
 */
public class Ingredient {

    public final int     id;
    public final String  name;
    public final double  quantity;
    public final boolean isOptional;

    public Ingredient(String raw) {
        String[] parts = raw.split(":", 4);
        id         = parts.length > 0 ? parseId(parts[0])    : 0;
        name       = parts.length > 1 ? parts[1]             : "";
        quantity   = parts.length > 2 ? parseDouble(parts[2]): 0.0;
        isOptional = parts.length > 3 && "1".equals(parts[3].trim());
    }

    private static int parseId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    @Override
    public String toString() {
        return name + (isOptional ? " (opt.)" : "");
    }
}
