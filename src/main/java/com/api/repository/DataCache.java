package com.api.repository;

import com.app.pojo.MenuData;
import com.app.pojo.Category;
import com.app.pojo.Ingredient;
import com.app.pojo.Promotion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.util.RemoteLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * DataCache — File-based persistence layer for DataRepository
 * 
 * Manages:
 * - menu-cache.json (Menu with categories & products)
 * - ingredients-cache.json (Ingredients list)
 * - promotions-cache.json (Promotions as special category)
 * 
 * Cache-first strategy: Check TTL before serving, only call API if stale
 */
public final class DataCache {

    private DataCache() {
    }

    // ── Cache paths ────────────────────────────────────────────────────────────
    private static final Path PROD_MENU_CACHE = Path.of("/opt/kiosk/menu-cache.json");
    private static final Path DEV_MENU_CACHE = Path.of("./menu-cache.json");
    private static final Path MENU_CACHE_PATH = Files.isWritable(PROD_MENU_CACHE.getParent()) ? PROD_MENU_CACHE : DEV_MENU_CACHE;

    private static final Path PROD_INGREDIENTS_CACHE = Path.of("/opt/kiosk/ingredients-cache.json");
    private static final Path DEV_INGREDIENTS_CACHE = Path.of("./ingredients-cache.json");
    private static final Path INGREDIENTS_CACHE_PATH = Files.isWritable(PROD_INGREDIENTS_CACHE.getParent()) ? PROD_INGREDIENTS_CACHE : DEV_INGREDIENTS_CACHE;

    private static final Path PROD_PROMOTIONS_CACHE = Path.of("/opt/kiosk/promotions-cache.json");
    private static final Path DEV_PROMOTIONS_CACHE = Path.of("./promotions-cache.json");
    private static final Path PROMOTIONS_CACHE_PATH = Files.isWritable(PROD_PROMOTIONS_CACHE.getParent()) ? PROD_PROMOTIONS_CACHE : DEV_PROMOTIONS_CACHE;

    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    // ── Menu Cache ─────────────────────────────────────────────────────────────

    /**
     * Load Menu from cache (if valid TTL)
     */
    public static MenuData loadMenuFromCache() {
        try {
            if (!Files.exists(MENU_CACHE_PATH)) {
                System.out.println("[DataCache] ⚠ Menu cache non trovato: " + MENU_CACHE_PATH);
                return null;
            }

            String txt = Files.readString(MENU_CACHE_PATH, StandardCharsets.UTF_8);
            if (txt.isBlank()) {
                System.out.println("[DataCache] ⚠ Menu cache vuoto");
                return null;
            }

            JsonObject obj = JsonParser.parseString(txt).getAsJsonObject();
            if (!obj.has("menu") || !obj.has("ts")) {
                System.out.println("[DataCache] ⚠ Menu cache malformato");
                return null;
            }

            // Check TTL
            long ts = obj.get("ts").getAsLong();
            long ageSeconds = (System.currentTimeMillis() - ts) / 1000;
            if (ageSeconds > CACHE_TTL_SECONDS) {
                System.out.println("[DataCache] ⚠ Menu cache scaduto (age: " + ageSeconds + "s, TTL: " + CACHE_TTL_SECONDS + "s)");
                return null;
            }

            JsonObject menuJson = obj.get("menu").getAsJsonObject();
            MenuData menu = MenuData.from(menuJson);
            System.out.println("[DataCache] ✓ Menu caricato da cache (age: " + ageSeconds + "s)");
            System.out.println("[DataCache]   └─ Percorso: " + MENU_CACHE_PATH);
            return menu;

        } catch (Exception e) {
            System.err.println("[DataCache] ✗ Errore caricamento menu cache: " + e.getMessage());
            RemoteLogger.error("DataCache", "loadMenuFromCache error", e);
            return null;
        }
    }

    /**
     * Save Menu to cache (from original API response JSON)
     */
    public static void saveMenu(MenuData menu, JsonObject originalResponse) {
        if (menu == null || originalResponse == null)
            return;

        try {
            JsonObject out = new JsonObject();
            out.add("menu", originalResponse);
            out.add("ts", new JsonPrimitive(System.currentTimeMillis()));

            Files.createDirectories(MENU_CACHE_PATH.getParent());
            Files.writeString(MENU_CACHE_PATH, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("[DataCache] ✓ Menu salvato in cache");
            System.out.println("[DataCache]   └─ Percorso: " + MENU_CACHE_PATH);
        } catch (IOException e) {
            System.err.println("[DataCache] ✗ Errore salvataggio menu: " + e.getMessage());
            RemoteLogger.error("DataCache", "saveMenu error", e);
        }
    }

    // ── Ingredients Cache ──────────────────────────────────────────────────────

    /**
     * Load Ingredients from cache (if valid TTL)
     */
    public static List<Ingredient> loadIngredientsFromCache() {
        try {
            if (!Files.exists(INGREDIENTS_CACHE_PATH)) {
                System.out.println("[DataCache] ⚠ Ingredienti cache non trovato");
                return null;
            }

            String txt = Files.readString(INGREDIENTS_CACHE_PATH, StandardCharsets.UTF_8);
            if (txt.isBlank()) {
                System.out.println("[DataCache] ⚠ Ingredienti cache vuoto");
                return null;
            }

            JsonObject obj = JsonParser.parseString(txt).getAsJsonObject();
            if (!obj.has("ingredienti") || !obj.has("ts")) {
                System.out.println("[DataCache] ⚠ Ingredienti cache malformato");
                return null;
            }

            // Check TTL
            long ts = obj.get("ts").getAsLong();
            long ageSeconds = (System.currentTimeMillis() - ts) / 1000;
            if (ageSeconds > CACHE_TTL_SECONDS) {
                System.out.println("[DataCache] ⚠ Ingredienti cache scaduto");
                return null;
            }

            JsonArray ingredientiJson = obj.get("ingredienti").getAsJsonArray();
            List<Ingredient> ingredients = Ingredient.listFromJsonArray(ingredientiJson);

            System.out.println("[DataCache] ✓ Ingredienti caricati da cache (" + ingredients.size() + ")");
            return ingredients;

        } catch (Exception e) {
            System.err.println("[DataCache] ✗ Errore caricamento ingredienti: " + e.getMessage());
            RemoteLogger.error("DataCache", "loadIngredientsFromCache error", e);
            return null;
        }
    }

    /**
     * Save Ingredients to cache (from original API response JSON array)
     */
    public static void saveIngredients(List<Ingredient> ingredients, JsonArray originalJsonArray) {
        if (originalJsonArray == null || originalJsonArray.isEmpty())
            return;

        try {
            JsonObject out = new JsonObject();
            out.add("ingredienti", originalJsonArray);
            out.add("ts", new JsonPrimitive(System.currentTimeMillis()));

            Files.createDirectories(INGREDIENTS_CACHE_PATH.getParent());
            Files.writeString(INGREDIENTS_CACHE_PATH, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("[DataCache] ✓ Ingredienti salvati in cache (" + ingredients.size() + ")");
        } catch (IOException e) {
            System.err.println("[DataCache] ✗ Errore salvataggio ingredienti: " + e.getMessage());
            RemoteLogger.error("DataCache", "saveIngredients error", e);
        }
    }

    // ── Promotions Cache ───────────────────────────────────────────────────────

    /**
     * Load Promotions from cache (if valid TTL)
     */
    public static List<Promotion> loadPromotionsFromCache() {
        try {
            if (!Files.exists(PROMOTIONS_CACHE_PATH)) {
                System.out.println("[DataCache] ⚠ Promozioni cache non trovato");
                return null;
            }

            String txt = Files.readString(PROMOTIONS_CACHE_PATH, StandardCharsets.UTF_8);
            if (txt.isBlank()) {
                System.out.println("[DataCache] ⚠ Promozioni cache vuoto");
                return null;
            }

            JsonObject obj = JsonParser.parseString(txt).getAsJsonObject();
            if (!obj.has("promozioni") || !obj.has("ts")) {
                System.out.println("[DataCache] ⚠ Promozioni cache malformato");
                return null;
            }

            // Check TTL
            long ts = obj.get("ts").getAsLong();
            long ageSeconds = (System.currentTimeMillis() - ts) / 1000;
            if (ageSeconds > CACHE_TTL_SECONDS) {
                System.out.println("[DataCache] ⚠ Promozioni cache scaduto");
                return null;
            }

            JsonArray promozionJson = obj.get("promozioni").getAsJsonArray();
            List<Promotion> promos = Promotion.listFromJsonArray(promozionJson);

            System.out.println("[DataCache] ✓ Promozioni caricate da cache (" + promos.size() + ")");
            return promos;

        } catch (Exception e) {
            System.err.println("[DataCache] ✗ Errore caricamento promozioni: " + e.getMessage());
            RemoteLogger.error("DataCache", "loadPromotionsFromCache error", e);
            return null;
        }
    }

    /**
     * Save Promotions to cache (from original API response JSON array)
     */
    public static void savePromotions(List<Promotion> promos, JsonArray originalJsonArray) {
        if (originalJsonArray == null || originalJsonArray.isEmpty())
            return;

        try {
            JsonObject out = new JsonObject();
            out.add("promozioni", originalJsonArray);
            out.add("ts", new JsonPrimitive(System.currentTimeMillis()));

            Files.createDirectories(PROMOTIONS_CACHE_PATH.getParent());
            Files.writeString(PROMOTIONS_CACHE_PATH, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("[DataCache] ✓ Promozioni salvate in cache (" + promos.size() + ")");
        } catch (IOException e) {
            System.err.println("[DataCache] ✗ Errore salvataggio promozioni: " + e.getMessage());
            RemoteLogger.error("DataCache", "savePromotions error", e);
        }
    }

    // ── Hash-based change detection ────────────────────────────────────────────

    /**
     * Calculate SHA-256 hash of string
     */
    public static String calculateHash(String data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Get cached file hash (or "" if not exists)
     */
    public static String getFileHash(Path path) {
        try {
            if (Files.exists(path)) {
                String content = new String(Files.readAllBytes(path));
                return calculateHash(content);
            }
        } catch (Exception e) {
            // File doesn't exist, return empty hash
        }
        return "";
    }

    public static void clearAllCaches() {
        try {
            Files.deleteIfExists(MENU_CACHE_PATH);
            Files.deleteIfExists(INGREDIENTS_CACHE_PATH);
            Files.deleteIfExists(PROMOTIONS_CACHE_PATH);
            System.out.println("[DataCache] ✓ Tutte le cache cancellate");
        } catch (IOException e) {
            System.err.println("[DataCache] ✗ Errore cancellazione cache: " + e.getMessage());
            RemoteLogger.error("DataCache", "clearAllCaches error", e);
        }
    }
}
