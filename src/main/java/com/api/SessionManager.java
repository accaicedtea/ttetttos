package com.api;

public class SessionManager {
    private static String token = null;
    public static void setToken(String newToken) { token = newToken; }
    public static String getToken()              { return token;     }
    public static void clearToken()              { token = null;     }
    public static boolean isLoggedIn()           { return token != null && !token.isBlank(); }
}
