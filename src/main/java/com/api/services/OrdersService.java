package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

public class OrdersService {
    private static final String BASE = "ordini";

    public static JsonObject createOrder(JsonObject body) throws Exception {
        return Api.apiPost(BASE, body);
    }

    public static JsonObject getOrder(int id) throws Exception {
        return Api.apiGet(BASE + "/" + id);
    }

    public static JsonObject confirmPayment(int id) throws Exception {
        return Api.apiPatch(BASE + "/" + id + "/pagato", new JsonObject());
    }

    public static JsonObject cancelOrder(int id) throws Exception {
        return Api.apiPatch(BASE + "/" + id + "/annulla", new JsonObject());
    }

    public static JsonObject confirmPrint(int id) throws Exception {
        return Api.apiPatch(BASE + "/" + id + "/stampato", new JsonObject());
    }
}
