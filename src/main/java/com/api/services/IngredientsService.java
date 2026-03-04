package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

public class IngredientsService {

    private static final String ENDPOINT = "ingredients";

    public static JsonObject getAll() throws Exception {
        return Api.apiGet(ENDPOINT);
    }

    public static JsonObject getById(int id) throws Exception {
        return Api.apiGet(ENDPOINT + "/" + id);
    }
}
