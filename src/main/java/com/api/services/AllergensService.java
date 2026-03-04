package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;

/**
 * Servizio per la gestione degli allergeni.
 * Equivalente Java di allergensService.js
 */
public class AllergensService {

    private static final String ENDPOINT = "allergens";

    /** Ottieni tutti gli allergeni. */
    public static JsonObject getAll() throws Exception {
        return Api.apiGet(ENDPOINT);
    }

    /** Ottieni un allergene specifico per ID. */
    public static JsonObject getById(int id) throws Exception {
        return Api.apiGet(ENDPOINT + "/" + id);
    }
}
