package com.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SessionManager {
    private static volatile String token = null;
    private static volatile JsonArray kdsDevices = new JsonArray();

    public static synchronized void setToken(String newToken) {
        token = newToken;
    }

    public static synchronized String getToken() {
        return token;
    }

    public static synchronized void clearToken() {
        System.out.println("[SessionManager] clearToken");
        token = null;
    }

    public static synchronized boolean isLoggedIn() {
        return token != null && !token.isBlank();
    }

    public static synchronized void setKdsDevices(JsonArray devices) {
        if (devices != null) {
            kdsDevices = devices;
            System.out.println("[SessionManager] KDS devices updated. Total: " + kdsDevices.size());
        }
    }

    public static synchronized JsonArray getKdsDevices() {
        return kdsDevices;
    }
}
