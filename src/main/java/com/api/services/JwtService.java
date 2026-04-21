package com.api.services;

import com.api.SessionManager;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Date;

public class JwtService {
    // In un sistema di produzione, usa un Secret memorizzato su file / configurazione
    private static final String SECRET_KEY = "TOTEM_LOCAL_SECURE_KEY";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);
    private static final String ISSUER = "TotemKiosk";

    /**
     * Valida un PIN richiesto confrontandolo con l'elenco KDS autorizzato
     * e, se OK, restituisce un token JWT con il ruolo; altrimenti null.
     */
    public static String authenticateAndGenerateToken(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            return null;
        }

        JsonArray devices = SessionManager.getKdsDevices();
        if (devices == null) {
            return null;
        }

        for (int i = 0; i < devices.size(); i++) {
            JsonObject device = devices.get(i).getAsJsonObject();
            if (device.has("pin") && pin.equals(device.get("pin").getAsString())) {
                String role = device.has("nome") ? device.get("nome").getAsString() : "Worker";
                return generateTokenForRole(role);
            }
        }
        return null; // Nessun KDS trovato con quel PIN
    }

    private static String generateTokenForRole(String role) {
        // Scade tra 24 ore
        Date expirationDate = new Date(System.currentTimeMillis() + 86400000L);

        return JWT.create()
                .withIssuer(ISSUER)
                .withClaim("role", role)
                .withExpiresAt(expirationDate)
                .sign(ALGORITHM);
    }

    /**
     * Verifica il token e ritorna il ruolo, oppure null se non valido.
     */
    public static String verifyAndGetRole(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaim("role").asString();
        } catch (Exception e) {
            return null;
        }
    }
}
