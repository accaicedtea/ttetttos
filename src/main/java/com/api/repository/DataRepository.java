package com.api.repository;

import com.api.Api;
import com.api.SessionManager;
import com.app.pojo.MenuData;
import com.app.pojo.Category;
import com.app.pojo.Ingredient;
import com.app.pojo.Promotion;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DataRepository — Centralized, modular data access layer
 * 
 * Replaces:
 * - ViewsService
 * - IngredientService
 * - PromotionsService
 * - MenuCache (now internal)
 * - CacheSyncService (now internal)
 * 
 * Features:
 * - Cache-first strategy (check cache before API)
 * - Automatic background sync every 5 minutes
 * - Hash-based change detection
 * - Transparent to controllers
 * 
 * Usage:
 *   MenuData menu = DataRepository.getMenu();
 *   List<Ingredient> ing = DataRepository.getIngredients();
 *   List<Promotion> promos = DataRepository.getPromotions();
 *   DataRepository.startBackgroundSync();
 */
public final class DataRepository {

    private DataRepository() {
    }

    private static final int SYNC_INTERVAL_MINUTES = 5;
    private static ScheduledExecutorService syncExecutor;
    private static boolean isRunning = false;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Get Menu (cache-first)
     * Returns cached menu if valid, otherwise calls API and updates cache
     */
    public static MenuData getMenu() throws Exception {
        // STEP 1: Try cache
        MenuData cachedMenu = DataCache.loadMenuFromCache();
        if (cachedMenu != null && !cachedMenu.isEmpty()) {
            System.out.println("[DataRepo] ✓ Menu da cache");
            return cachedMenu;
        }

        // STEP 2: If cache empty, call API
        System.out.println("[DataRepo] → GET /menu");
        JsonObject resp = Api.apiGet("menu");

        MenuData menu = MenuData.from(resp);
        if (menu == null || menu.isEmpty()) {
            throw new Exception("Impossibile parsare menu dal server");
        }

        // STEP 3: Save to cache
        DataCache.saveMenu(menu, resp);

        return menu;
    }

    /**
     * Get Categories from menu
     */
    public static List<Category> getCategories() throws Exception {
        MenuData menu = getMenu();
        return menu != null ? menu.categorie : List.of();
    }

    /**
     * Get Ingredients (cache-first)
     * Returns cached ingredients if valid, otherwise calls API and updates cache
     */
    public static List<Ingredient> getIngredients() throws Exception {
        // STEP 1: Try cache
        List<Ingredient> cachedIng = DataCache.loadIngredientsFromCache();
        if (cachedIng != null && !cachedIng.isEmpty()) {
            System.out.println("[DataRepo] ✓ Ingredienti da cache (" + cachedIng.size() + ")");
            return cachedIng;
        }

        // STEP 2: If cache empty, call API
        System.out.println("[DataRepo] → GET /prodotti/ingredienti");
        JsonObject resp = Api.apiGet("prodotti/ingredienti");

        JsonArray ingredientiJson = new JsonArray();

        if (resp.has("data")) {
            if (resp.get("data").isJsonObject()) {
                JsonObject dataObj = resp.getAsJsonObject("data");
                if (dataObj.has("ingredienti") && dataObj.get("ingredienti").isJsonArray()) {
                    ingredientiJson = dataObj.getAsJsonArray("ingredienti");
                }
            } else if (resp.get("data").isJsonArray()) {
                ingredientiJson = resp.getAsJsonArray("data");
            }
        }

        if (ingredientiJson.isEmpty() && resp.has("ingredienti") && resp.get("ingredienti").isJsonArray()) {
            ingredientiJson = resp.getAsJsonArray("ingredienti");
        }

        List<Ingredient> ingredients = Ingredient.listFromJsonArray(ingredientiJson);

        if (ingredients == null || ingredients.isEmpty()) {
            throw new Exception("Nessun ingrediente ricevuto dal server");
        }

        System.out.println("[DataRepo]   ✓ Ingredienti ricevuti (" + ingredients.size() + ")");

        // STEP 3: Save to cache
        DataCache.saveIngredients(ingredients, ingredientiJson);

        return ingredients;
    }

    /**
     * Get Promotions (cache-first)
     * Returns cached promotions if valid, otherwise calls API and updates cache
     */
    public static List<Promotion> getPromotions() throws Exception {
        // STEP 1: Try cache
        List<Promotion> cachedPromos = DataCache.loadPromotionsFromCache();
        if (cachedPromos != null && !cachedPromos.isEmpty()) {
            System.out.println("[DataRepo] ✓ Promozioni da cache (" + cachedPromos.size() + ")");
            return cachedPromos;
        }

        // STEP 2: If cache empty, call API
        System.out.println("[DataRepo] → GET /promozioni/attive");
        JsonObject resp = Api.apiGet("promozioni/attive");

        JsonArray promozionJson = new JsonArray();

        if (resp.has("data")) {
            if (resp.get("data").isJsonObject()) {
                JsonObject dataObj = resp.getAsJsonObject("data");
                if (dataObj.has("promozioni") && dataObj.get("promozioni").isJsonArray()) {
                    promozionJson = dataObj.getAsJsonArray("promozioni");
                }
            } else if (resp.get("data").isJsonArray()) {
                promozionJson = resp.getAsJsonArray("data");
            }
        }

        if (promozionJson.isEmpty() && resp.has("promozioni") && resp.get("promozioni").isJsonArray()) {
            promozionJson = resp.getAsJsonArray("promozioni");
        }

        List<Promotion> promos = Promotion.listFromJsonArray(promozionJson);

        if (promos == null || promos.isEmpty()) {
            System.out.println("[DataRepo] ⚠ Nessuna promozione dal server");
            return List.of();
        }

        System.out.println("[DataRepo]   ✓ Promozioni ricevute (" + promos.size() + ")");

        // STEP 3: Save to cache
        DataCache.savePromotions(promos, promozionJson);

        return promos;
    }

    /**
     * Force refresh all data from server (ignores cache)
     */
    public static void forceRefresh() throws Exception {
        System.out.println("[DataRepo] ▶ Aggiornamento forzato da server...");

        // Menu
        System.out.println("[DataRepo]   → Menu");
        JsonObject menuResp = Api.apiGet("menu");
        MenuData menu = MenuData.from(menuResp);
        if (menu != null && !menu.isEmpty()) {
            DataCache.saveMenu(menu, menuResp);
            System.out.println("[DataRepo]     ✓ Menu aggiornato");
        }

        // Ingredients
        System.out.println("[DataRepo]   → Ingredienti");
        JsonObject ingResp = Api.apiGet("prodotti/ingredienti");
        JsonArray ingJson = extractJsonArray(ingResp);
        List<Ingredient> ing = Ingredient.listFromJsonArray(ingJson);
        if (ing != null && !ing.isEmpty()) {
            DataCache.saveIngredients(ing, ingJson);
            System.out.println("[DataRepo]     ✓ Ingredienti aggiornati");
        }

        // Promotions
        System.out.println("[DataRepo]   → Promozioni");
        JsonObject promoResp = Api.apiGet("promozioni/attive");
        JsonArray promoJson = extractJsonArray(promoResp);
        List<Promotion> promos = Promotion.listFromJsonArray(promoJson);
        if (promos != null && !promos.isEmpty()) {
            DataCache.savePromotions(promos, promoJson);
            System.out.println("[DataRepo]     ✓ Promozioni aggiornate");
        }

        System.out.println("[DataRepo] ▼ Aggiornamento completato");
    }

    // ── Background Sync ────────────────────────────────────────────────────────

    /**
     * Start background sync (checks for updates every 5 minutes)
     */
    public static void startBackgroundSync() {
        if (isRunning) {
            System.out.println("[DataRepo] ⚠ Background sync già in esecuzione");
            return;
        }

        if (!SessionManager.isLoggedIn()) {
            System.out.println("[DataRepo] ⚠ Non loggato — background sync non avviato");
            return;
        }

        isRunning = true;
        syncExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "DataRepo-SyncThread");
            t.setDaemon(true);
            return t;
        });

        System.out.println("[DataRepo] ▶ Background sync avviato (intervallo: " + SYNC_INTERVAL_MINUTES + " min)");

        // First check IMMEDIATELY, then every SYNC_INTERVAL_MINUTES
        syncExecutor.scheduleAtFixedRate(
                DataRepository::performSync,
                0, SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Stop background sync
     */
    public static void stopBackgroundSync() {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdown();
            isRunning = false;
            System.out.println("[DataRepo] ■ Background sync fermato");
        }
    }

    // ── Internal sync logic ────────────────────────────────────────────────────

    /**
     * Perform background sync (check for changes on server)
     */
    private static void performSync() {
        try {
            System.out.println("\n[DataRepo] ▲ Sync periodico avviato...");

            syncMenuIfChanged();
            syncIngredientsIfChanged();
            syncPromotionsIfChanged();

            System.out.println("[DataRepo] ▼ Sync periodico completato\n");
        } catch (Exception e) {
            System.err.println("[DataRepo] ✗ Errore durante sync: " + e.getMessage());
        }
    }

    /**
     * Sync Menu: check if server has changes
     */
    private static void syncMenuIfChanged() {
        try {
            String localHash = DataCache.getFileHash(java.nio.file.Paths.get("./menu-cache.json"));
            JsonObject remoteResp = Api.apiGet("menu");
            MenuData remoteMenu = MenuData.from(remoteResp);

            String remoteHash = DataCache.calculateHash(remoteResp.toString());

            if (!remoteHash.equals(localHash)) {
                System.out.println("[DataRepo] 📥 Menu modificato sul server, aggiornamento locale...");
                DataCache.saveMenu(remoteMenu, remoteResp);
                System.out.println("[DataRepo]   ✓ Menu cache aggiornato");
            } else {
                System.out.println("[DataRepo] ✓ Menu unchanged");
            }
        } catch (Exception e) {
            System.err.println("[DataRepo] ✗ Errore sync menu: " + e.getMessage());
        }
    }

    /**
     * Sync Ingredients: check if server has changes
     */
    private static void syncIngredientsIfChanged() {
        try {
            String localHash = DataCache.getFileHash(java.nio.file.Paths.get("./ingredients-cache.json"));
            JsonObject remoteResp = Api.apiGet("prodotti/ingredienti");
            JsonArray ingJson = extractJsonArray(remoteResp);
            List<Ingredient> remoteIng = Ingredient.listFromJsonArray(ingJson);

            String remoteHash = DataCache.calculateHash(remoteResp.toString());

            if (!remoteHash.equals(localHash)) {
                System.out.println("[DataRepo] 📥 Ingredienti modificati sul server, aggiornamento locale...");
                DataCache.saveIngredients(remoteIng, ingJson);
                System.out.println("[DataRepo]   ✓ Ingredienti cache aggiornato");
            } else {
                System.out.println("[DataRepo] ✓ Ingredienti unchanged");
            }
        } catch (Exception e) {
            System.err.println("[DataRepo] ✗ Errore sync ingredienti: " + e.getMessage());
        }
    }

    /**
     * Sync Promotions: check if server has changes
     */
    private static void syncPromotionsIfChanged() {
        try {
            String localHash = DataCache.getFileHash(java.nio.file.Paths.get("./promotions-cache.json"));
            JsonObject remoteResp = Api.apiGet("promozioni/attive");
            JsonArray promoJson = extractJsonArray(remoteResp);
            List<Promotion> remotePromos = Promotion.listFromJsonArray(promoJson);

            String remoteHash = DataCache.calculateHash(remoteResp.toString());

            if (!remoteHash.equals(localHash)) {
                System.out.println("[DataRepo] 📥 Promozioni modificate sul server, aggiornamento locale...");
                DataCache.savePromotions(remotePromos, promoJson);
                System.out.println("[DataRepo]   ✓ Promozioni cache aggiornato");
            } else {
                System.out.println("[DataRepo] ✓ Promozioni unchanged");
            }
        } catch (Exception e) {
            System.err.println("[DataRepo] ✗ Errore sync promozioni: " + e.getMessage());
        }
    }

    // ── Helper methods ─────────────────────────────────────────────────────────

    /**
     * Extract JsonArray from API response (handles multiple formats)
     */
    private static JsonArray extractJsonArray(JsonObject resp) {
        JsonArray result = new JsonArray();

        if (resp.has("data")) {
            if (resp.get("data").isJsonArray()) {
                result = resp.getAsJsonArray("data");
            } else if (resp.get("data").isJsonObject()) {
                JsonObject dataObj = resp.getAsJsonObject("data");
                if (dataObj.has("items") && dataObj.get("items").isJsonArray()) {
                    result = dataObj.getAsJsonArray("items");
                }
            }
        }

        return result;
    }
}
