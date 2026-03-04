package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

/**
 * Servizio per tutte le viste (sola lettura).
 *
 * Viste con token (auth):
 *   - v_products_full
 *   - v_categories_with_count
 *   - v_ingredients_with_allergens
 *   - v_products_with_promotions
 *
 * Viste pubbliche (no token):
 *   - v_allergens_usage
 *   - v_azienda_stats
 *   - v_active_promotions
 *
 * Ogni vista è raggiungibile sia come _view_{nome} che come views/{nome}.
 */
public class ViewsService {

    // ── Viste con token ──────────────────────────────────────────

    public static JsonObject getProductsFull() throws Exception {
        return Api.apiGet("views/v_products_full");
    }

    public static JsonObject getCategoriesWithCount() throws Exception {
        return Api.apiGet("views/v_categories_with_count");
    }

    public static JsonObject getIngredientsWithAllergens() throws Exception {
        return Api.apiGet("views/v_ingredients_with_allergens");
    }

    public static JsonObject getProductsWithPromotions() throws Exception {
        return Api.apiGet("views/v_products_with_promotions");
    }

}
