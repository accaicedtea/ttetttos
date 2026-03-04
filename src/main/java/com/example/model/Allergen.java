package com.example.model;

/**
 * Allergene associato a un prodotto.
 * Formato raw: "id:nome:icona"
 */
public class Allergen {

    public final int    id;
    public final String name;
    public final String icon;

    public Allergen(String raw) {
        String[] parts = raw.split(":", 3);
        id   = parts.length > 0 ? parseId(parts[0]) : 0;
        name = parts.length > 1 ? parts[1] : "";
        icon = parts.length > 2 ? parts[2] : "";
    }

    private static int parseId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override
    public String toString() {
        return icon + " " + name;
    }
}
