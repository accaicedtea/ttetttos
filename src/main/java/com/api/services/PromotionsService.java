package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

public class PromotionsService {

    private static final String ENDPOINT = "promotions";

    public static JsonObject getAll() throws Exception {
        return Api.apiGet(ENDPOINT);
    }

    public static JsonObject getById(int id) throws Exception {
        return Api.apiGet(ENDPOINT + "/" + id);
    }
}
