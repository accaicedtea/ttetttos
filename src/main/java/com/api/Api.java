package com.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// ─────────────────────────────────────────────────────────────────────────────
// API — Client HTTP base, equivalente Java di api.js
//
// Questa classe non viene usata direttamente dall'applicazione: i servizi
// (AuthService, ProductsService, ecc.) la usano internamente.
//
// METODI DISPONIBILI per i servizi:
//
//   apiGetPublic(endpoint)          → GET senza token  (endpoint pubblici)
//   apiPostPublic(endpoint, body)   → POST senza token (login, registrazione)
//   apiGet(endpoint)                → GET con token    (richiede login)
//   apiPost(endpoint, body)         → POST con token
//   apiPut(endpoint, body)          → PUT con token
//   apiDelete(endpoint)             → DELETE con token
//
// Tutti i metodi lanciano Exception in caso di:
//   - rete non raggiungibile
//   - risposta non-2xx dal server (usa il campo "message" come messaggio)
//   - risposta non JSON valida
// ─────────────────────────────────────────────────────────────────────────────
public class Api {

    // URL base del backend — cambia solo questo se cambia il server
    private static final String BASE_URL = "https://thisisnotmysite.altervista.org/mytotem/api/";

    // HttpClient è thread-safe e costoso da creare: una sola istanza per tutta l'app
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // -------------------------------------------------------------------------
    // Helpers privati
    // -------------------------------------------------------------------------

    private static String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Aggiunge Content-Type: application/json e, se presente il token JWT,
     * l'header Authorization: Bearer <token>.
     * Chiamato da tutti i metodi che richiedono autenticazione.
     */
    private static HttpRequest.Builder applyAuthHeaders(HttpRequest.Builder builder) {
        builder.header("Content-Type", "application/json");

        String token = SessionManager.getToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    /**
     * Interpreta la risposta HTTP:
     *   - tenta di parsare il body come JsonObject
     *   - se lo status è 2xx restituisce il JsonObject
     *   - altrimenti lancia un'eccezione con il messaggio dal campo "message"
     *
     * Tutti i metodi pubblici passano per qui, quindi gli errori del server
     * vengono propagati come Exception con testo leggibile.
     */
    private static JsonObject handleResponse(HttpResponse<String> response) throws Exception {
        String responseText = response.body();
        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response text: " + responseText);

        JsonObject result;
        try {
            result = JsonParser.parseString(responseText).getAsJsonObject();
        } catch (Exception parseError) {
            System.err.println("Errore parsing JSON: " + parseError.getMessage());
            System.err.println("Response non è JSON valido: " +
                    responseText.substring(0, Math.min(responseText.length(), 500)));
            throw new Exception("Risposta server non valida (non è JSON)");
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return result;
        } else {
            String message = result.has("message")
                    ? result.get("message").getAsString()
                    : "Errore nella richiesta";
            throw new Exception(message);
        }
    }

    /**
     * Verifica che ci sia un token in SessionManager.
     * Chiamato dai metodi che richiedono autenticazione (apiGet, apiPost, ecc.).
     * Se l'utente non ha fatto login lancia Exception invece di mandare una
     * richiesta destinata a fallire con 401.
     */
    private static void requireToken() throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new Exception("Token mancante, devi fare login");
        }
    }

    // -------------------------------------------------------------------------
    // Metodi HTTP pubblici
    // -------------------------------------------------------------------------

    // ─────────────────────────────────────────────────────────────────────────
    // METODI HTTP — usati dai servizi, non chiamare direttamente dall'app
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST senza token — usato da AuthService.login().
     * endpoint: path relativo, es. "auth/login"
     * data:     JsonObject con i campi del body (es. { email, password })
     */
    public static JsonObject apiPostPublic(String endpoint, JsonObject data) throws Exception {
        String body = data != null ? data.toString() : "{}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    /**
     * GET senza token — per endpoint pubblici che non richiedono login.
     * Usato da ViewsService per le viste pubbliche (es. v_active_promotions).
     */
    public static JsonObject apiGetPublic(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + endpoint))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    /**
     * GET autenticato — il metodo più usato dai servizi.
     * Lancia Exception se l'utente non è loggato (token mancante).
     * Esempio d'uso in un servizio:
     *   return Api.apiGet("products");           // GET /api/products
     *   return Api.apiGet("products/" + id);     // GET /api/products/5
     */
    public static JsonObject apiGet(String endpoint) throws Exception {
        requireToken();

        HttpRequest request = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + endpoint))
                        .GET()
        ).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    /**
     * POST autenticato — per creare nuove risorse.
     * Esempio:
     *   JsonObject body = new JsonObject();
     *   body.addProperty("name", "Nuovo prodotto");
     *   Api.apiPost("products", body);  // POST /api/products
     */
    public static JsonObject apiPost(String endpoint, JsonObject data) throws Exception {
        requireToken();

        String body = data != null ? data.toString() : "{}";

        HttpRequest request = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + endpoint))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
        ).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    /**
     * PUT autenticato — per aggiornare una risorsa esistente.
     * Esempio:
     *   Api.apiPut("products/5", body);  // PUT /api/products/5
     */
    public static JsonObject apiPut(String endpoint, JsonObject data) throws Exception {
        requireToken();

        String body = data != null ? data.toString() : "{}";

        HttpRequest request = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + endpoint))
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
        ).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    /**
     * DELETE autenticato — per eliminare una risorsa.
     * Esempio:
     *   Api.apiDelete("products/5");  // DELETE /api/products/5
     */
    public static JsonObject apiDelete(String endpoint) throws Exception {
        requireToken();

        HttpRequest request = applyAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + endpoint))
                        .DELETE()
        ).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }
}
