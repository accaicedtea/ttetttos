package com.api.services;

import com.api.Api;
import com.api.SessionManager;
import com.google.gson.JsonObject;

// ─────────────────────────────────────────────────────────────────────────────
// AUTHSERVICE — Gestione autenticazione
//
// UTILIZZO TIPICO (in un thread separato, MAI sul JavaFX thread):
//
//   try {
//       AuthService.login("utente@esempio.it", "password");
//       // da qui SessionManager.isLoggedIn() == true
//       // tutti i servizi che richiedono token funzioneranno
//   } catch (Exception e) {
//       // credenziali errate → e.getMessage() contiene il messaggio del server
//   }
//
// Per il logout:
//   AuthService.logout();  // cancella il token, SessionManager.isLoggedIn() → false
// ─────────────────────────────────────────────────────────────────────────────
public class AuthService {

    private static final String ENDPOINT = "auth/login";

    /**
     * Effettua il login con email e password.
     *
     * Internamente fa POST /api/auth/login con body { email, password }.
     * Il server risponde con:
     *   { status: "success", message: "...", data: { token: "jwt...", user: {...} } }
     *
     * Il token viene estratto e salvato automaticamente in SessionManager.
     * Da questo momento tutte le chiamate API autenticate funzioneranno.
     *
     * @param email    indirizzo email registrato
     * @param password password in chiaro (HTTPS in produzione)
     * @return risposta completa del server (contiene anche i dati utente)
     * @throws Exception se le credenziali sono errate o il server non risponde
     */
    public static JsonObject login(String email, String password) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        JsonObject result = Api.apiPostPublic(ENDPOINT, body);

        // Estrae il token JWT dalla risposta e lo salva in SessionManager
        // Struttura: { status, message, data: { token, user } }
        if (result.has("data") && result.getAsJsonObject("data").has("token")) {
            SessionManager.setToken(result.getAsJsonObject("data").get("token").getAsString());
        }

        return result;
    }

    /**
     * Effettua il logout cancellando il token da SessionManager.
     * Dopo questa chiamata tutte le API protette lanceranno Exception
     * ("Token mancante, devi fare login").
     */
    public static void logout() {
        SessionManager.clearToken();
    }
}
