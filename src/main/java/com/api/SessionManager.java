package com.api;

// ─────────────────────────────────────────────────────────────────────────────
// SESSIONMANAGER — Equivalente del localStorage per il token JWT
//
// Nel frontend JavaScript il token viene salvato con:
//   localStorage.setItem('token', data.token)
//
// In Java non esiste localStorage: usiamo una variabile statica che vive per
// tutta la durata del processo JVM.
//
// UTILIZZO TIPICO:
//   1. Dopo il login:  SessionManager.setToken(tokenDalServer);
//   2. Prima di usare API protette: SessionManager.isLoggedIn()
//   3. Al logout:      SessionManager.clearToken();
//
// Il token viene allegato automaticamente a ogni richiesta HTTP da Api.java,
// quindi non è necessario passarlo manualmente ai servizi.
// ─────────────────────────────────────────────────────────────────────────────
public class SessionManager {

    // Token JWT salvato in memoria (null = non autenticato)
    private static String token = null;

    /** Salva il token ricevuto dal server dopo il login. */
    public static void setToken(String newToken) {
        token = newToken;
    }

    /**
     * Restituisce il token corrente.
     * Usato da Api.java per costruire l'header:
     *   Authorization: Bearer <token>
     */
    public static String getToken() {
        return token;
    }

    /** Cancella il token (equivalente di localStorage.removeItem). */
    public static void clearToken() {
        token = null;
    }

    /** Ritorna true se c'è un token valido (utente loggato). */
    public static boolean isLoggedIn() {
        return token != null && !token.isBlank();
    }
}
