package com.api.services;

import com.api.Api;
import com.api.SessionManager;
import com.google.gson.JsonObject;
import java.util.Map;
import com.util.ConsoleColors;
public class AuthService {

    private static final String ENDPOINT = "auth/login";

    public static JsonObject loginTotem(String apiKey) throws Exception {
        ConsoleColors.printInfo("[AuthService] --> LOGIN");
        JsonObject body = new JsonObject();
        body.addProperty("api_key", apiKey);
        JsonObject result = Api.apiPostPublic(ENDPOINT, body, Map.of("X-Api-Key", apiKey));
        
        if (result.has("data")) {
            JsonObject data = result.getAsJsonObject("data");
            if (data.has("token")) {
                String token = data.get("token").getAsString();
                SessionManager.setToken(token);
                ConsoleColors.printSuccess("[AuthService] TOKEN SALVATO IN SessionManager");
            }
        }
        return result;
    }

    public static JsonObject refreshToken() throws Exception {
        ConsoleColors.printInfo("[AuthService] --> REFRESH");
        return Api.apiPost("auth/refresh", new JsonObject());
    }

    public static JsonObject ping() throws Exception {
        ConsoleColors.printInfo("[AuthService] --> PING");
        JsonObject body = buildPingPayload();
        JsonObject result = Api.apiPost("auth/ping", body);
        ConsoleColors.printSuccess("[AuthService]   ✓ Ping risposto (server online)");
        return result;
    }

    private static JsonObject buildPingPayload() {
        JsonObject payload = new JsonObject();
        try {
            // Versione app
            payload.addProperty("app_versione", "1.0.12");
            
            // Versione Java
            payload.addProperty("java_versione", System.getProperty("java.version"));
            
            // Info OS
            payload.addProperty("os_info", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            
            // RAM libera
            Runtime runtime = Runtime.getRuntime();
            long freeRam = runtime.freeMemory() / (1024 * 1024);
            payload.addProperty("ram_libera_mb", freeRam);
            
            // Disco libero
            java.io.File root = new java.io.File("/");
            long freeSpace = root.getFreeSpace() / (1024 * 1024);
            payload.addProperty("disco_libero_mb", freeSpace);
            
        } catch (Exception e) {
            ConsoleColors.printErr("[AuthService] Errore building ping payload: " + e.getMessage());
        }
        return payload;
    }

    public static void logout() {
        SessionManager.clearToken();
    }
}
