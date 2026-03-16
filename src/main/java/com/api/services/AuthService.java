package com.api.services;

import com.api.Api;
import com.api.SessionManager;
import com.google.gson.JsonObject;

import java.util.Map;

public class AuthService {

    private static final String ENDPOINT = "auth/login";

    public static JsonObject loginTotem(String apiKey) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("api_key", apiKey);
        JsonObject result = Api.apiPostPublic(ENDPOINT, body, Map.of("X-Api-Key", apiKey));
        if (result.has("data") && result.getAsJsonObject("data").has("token")) {
            SessionManager.setToken(result.getAsJsonObject("data").get("token").getAsString());
        }
        return result;
    }

    public static JsonObject login(String email, String password) throws Exception {
        return loginTotem(email);
    }

    public static JsonObject refreshToken() throws Exception { return Api.apiPost("auth/refresh", new JsonObject()); }
    public static JsonObject ping()         throws Exception { return Api.apiPost("auth/ping",    new JsonObject()); }
    public static void       logout()                        { SessionManager.clearToken(); }
}
