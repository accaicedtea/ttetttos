package com.api;

import com.util.SystemManager;
import com.api.repository.DataCache;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import com.util.ConsoleColors;
/**
 * Client HTTP base.
 * Retry automatico fino a 3 volte su IOException (connection reset, ecc.)
 */
public class Api {

    private static final String BASE_URL = "http://localhost:8080/api/v1/totem/";

    // HttpClient standard — identico alla versione originale che funzionava
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static HttpRequest.Builder applyAuthHeaders(HttpRequest.Builder b) {
        b.header("Content-Type", "application/json");
        String token = SessionManager.getToken();
        if (token != null && !token.isBlank()) {
            // Per le richieste autenticate usa Authorization: Bearer
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private static JsonObject handleResponse(HttpResponse<String> response) throws Exception {
        String body = response.body();
        JsonObject result;
        try {
            result = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Risposta non JSON: " + body.substring(0, Math.min(body.length(), 200)));
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300)
            return result;
        // Log corpo completo per debug (utile per 422 Unprocessable Entity)
        ConsoleColors.printErr("[Api] HTTP " + status + " body: " + body);
        ConsoleColors.printErr("[Api] Richiesta: " + response.request().toString());
        ConsoleColors.printErr("[Api] Header Authorization inviato: " + response.request().headers().firstValue("Authorization"));
        String msg = result.has("message") ? result.get("message").getAsString()
                : result.has("error") ? result.get("error").getAsString()
                : result.has("messaggio") ? result.get("messaggio").getAsString()
                        : "Errore HTTP " + status;

        // Se la API key e' invalida, blocca immediatamente il totem.
        if (status == 401 && "totem disattivato".equalsIgnoreCase(msg)) {
            SessionManager.clearToken();
            SystemManager.lockApp("Totem disattivato. Accesso disabilitato.\nContatta l'assistenza tecnica.");
            DataCache.clearAllCaches();
        }

        throw new Exception("HTTP " + status + ": " + msg);
    }

    private static void requireToken() throws Exception {
        if (!SessionManager.isLoggedIn())
            throw new Exception("Token mancante, devi fare login");
    }

    /**
     * Retry automatico su IOException (es. connection reset).
     * Non ricrea il client — usa quello originale come prima.
     */
    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        int maxTries = 3;
        Exception last = null;
        for (int attempt = 1; attempt <= maxTries; attempt++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException e) {
                last = e;
                ConsoleColors.printErr("[Api] Tentativo " + attempt + "/" + maxTries + " fallito: " + e.getMessage());
                if (attempt < maxTries)
                    Thread.sleep(600L * attempt);
            }
        }
        throw last;
    }

    // ── Pubblici ──────────────────────────────────────────────────────────────

    public static JsonObject apiPostPublic(String endpoint, JsonObject data) throws Exception {
        return apiPostPublic(endpoint, data, null);
    }

    public static JsonObject apiPostPublic(String endpoint, JsonObject data,
            Map<String, String> extra) throws Exception {
        ConsoleColors.printInfo("[Api] POST " + BASE_URL + endpoint);
        String body = data != null ? data.toString() : "{}";
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (extra != null)
            extra.forEach(b::header);
        JsonObject result = handleResponse(send(b.build()));
        ConsoleColors.printInfo("[Api] POST " + endpoint + " completato");
        return result;
    }

    public static JsonObject apiGetPublic(String endpoint) throws Exception {
        ConsoleColors.printInfo("[Api] GET " + BASE_URL + endpoint);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .GET().build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printInfo("[Api] GET " + endpoint + " completato");
        return result;
    }

    // ── Autenticati ───────────────────────────────────────────────────────────

    public static JsonObject apiGet(String endpoint) throws Exception {
        requireToken();
        ConsoleColors.printInfo("[Api] GET " + BASE_URL + endpoint);
        HttpRequest req = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .GET())
                .build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printInfo("[Api] GET " + endpoint + " completato");
        return result;
    }

    public static JsonObject apiPost(String endpoint, JsonObject data) throws Exception {
        requireToken();
        ConsoleColors.printInfo("[Api] POST " + BASE_URL + endpoint);
        String body = data != null ? data.toString() : "{}";
        HttpRequest req = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .POST(HttpRequest.BodyPublishers.ofString(body)))
                .build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printInfo("[Api] POST " + endpoint + " completato");
        return result;
    }

    public static JsonObject apiPut(String endpoint, JsonObject data) throws Exception {
        requireToken();
        ConsoleColors.printInfo("[Api] PUT " + BASE_URL + endpoint);
        String body = data != null ? data.toString() : "{}";
        HttpRequest req = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .PUT(HttpRequest.BodyPublishers.ofString(body)))
                .build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printInfo("[Api] PUT " + endpoint + " completato");
        return result;
    }

    public static JsonObject apiPatch(String endpoint, JsonObject data) throws Exception {
        requireToken();
        ConsoleColors.printInfo("[Api] PATCH " + BASE_URL + endpoint);
        String body = data != null ? data.toString() : "{}";
        HttpRequest req = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body)))
                .build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printInfo("[Api] PATCH " + endpoint + " completato");
        return result;
    }

    public static JsonObject apiDelete(String endpoint) throws Exception {
        requireToken();
        ConsoleColors.printWarn("[Api] DELETE " + BASE_URL + endpoint);
        HttpRequest req = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .DELETE())
                .build();
        JsonObject result = handleResponse(send(req));
        ConsoleColors.printWarn("[Api] DELETE " + endpoint + " completato");
        return result;
    }
}
