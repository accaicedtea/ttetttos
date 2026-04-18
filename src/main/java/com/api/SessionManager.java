package com.api;

public class SessionManager {
    private static volatile String token = null;

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
}
