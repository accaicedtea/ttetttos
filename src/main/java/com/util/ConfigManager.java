package com.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Function;

public final class ConfigManager {
    private static final Properties props = new Properties();

    static {
        // Carica il file di default dal classpath
        try (InputStream in = ConfigManager.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Impossibile caricare config.properties");
        }
    }

    /**
     * Recupera il valore come stringa con priorità:
     * 1. System property (-Dkey=value)
     * 2. Variabile d'ambiente (trasforma punti in underscore, maiuscolo)
     * 3. File properties interno
     * 4. default
     */
    public static String get(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null)
            return value;

        String envKey = key.toUpperCase().replace('.', '_');
        value = System.getenv(envKey);
        if (value != null)
            return value;

        value = props.getProperty(key);
        if (value != null)
            return value;

        return defaultValue;
    }

    public static String get(String key) {
        return get(key, null);
    }

    // ---------- Tipi primitivi comuni ----------
    public static int getInt(String key) {
        String str = requireNonNull(key);
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Valore non intero per chiave " + key + ": " + str, e);
        }
    }

    public static long getLong(String key) {
        String str = requireNonNull(key);
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Valore non long per chiave " + key + ": " + str, e);
        }
    }

    public static double getDouble(String key) {
        String str = requireNonNull(key);
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Valore non double per chiave " + key + ": " + str, e);
        }
    }

    public static boolean getBoolean(String key) {
        String str = requireNonNull(key);
        str = str.trim().toLowerCase();
        if ("true".equals(str) || "1".equals(str) || "yes".equals(str))
            return true;
        if ("false".equals(str) || "0".equals(str) || "no".equals(str))
            return false;
        throw new IllegalStateException("Valore non booleano per chiave " + key + ": " + str);
    }

    // Metodo di supporto che controlla la presenza
    private static String requireNonNull(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalStateException("Configurazione mancante per chiave: " + key);
        }
        return value;
    }

    public static int getInt(String key, int defaultValue) {
        String str = get(key, null);
        if (str == null)
            return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            // Logga eventuale warning se vuoi
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String str = get(key, null);
        if (str == null)
            return defaultValue;
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String str = get(key, null);
        if (str == null)
            return defaultValue;
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String str = get(key, null);
        if (str == null)
            return defaultValue;
        str = str.trim().toLowerCase();
        if ("true".equals(str) || "1".equals(str) || "yes".equals(str))
            return true;
        if ("false".equals(str) || "0".equals(str) || "no".equals(str))
            return false;
        return defaultValue;
    }

    // ---------- Metodo generico per tipi arbitrari ----------
    /**
     * Converte il valore usando un parser personalizzato.
     * Esempio: ConfigManager.get("timeout", 30, Integer::parseInt)
     */
    public static <T> T get(String key, T defaultValue, Function<String, T> parser) {
        String str = get(key, null);
        if (str == null)
            return defaultValue;
        try {
            return parser.apply(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}