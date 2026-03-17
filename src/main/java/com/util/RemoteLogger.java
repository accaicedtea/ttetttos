package com.util;

import com.api.Api;
import com.google.gson.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Invia log di errore al server tramite POST /log.
 *
 * Uso:
 *   RemoteLogger.error("Componente", "Messaggio", exception);
 *   RemoteLogger.warn("Componente", "Avviso");
 *
 * Non blocca mai l'app — tutto asincrono, errori di invio ignorati.
 */
public class RemoteLogger {

    public static void error(String component, String message, Throwable t) {
        send("ERROR", component, message, t);
    }

    public static void error(String component, String message) {
        send("ERROR", component, message, null);
    }

    public static void warn(String component, String message) {
        send("WARN", component, message, null);
    }

    public static void info(String component, String message) {
        send("INFO", component, message, null);
    }

    private static void send(String level, String component,
                             String message, Throwable t) {
        // Sempre in background, mai blocca
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("livello",     level);
                body.addProperty("componente",  component);
                body.addProperty("messaggio",   message);
                body.addProperty("app_version", NetworkWatchdog.APP_VERSION);
                body.addProperty("os",
                        System.getProperty("os.name", "?") + " "
                        + System.getProperty("os.version", ""));

                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    String stack = sw.toString();
                    // Tronca a 2000 caratteri per non sovraccaricare il server
                    body.addProperty("stacktrace",
                            stack.length() > 2000 ? stack.substring(0, 2000) + "..." : stack);
                }

                Api.apiPost("log", body);
            } catch (Exception ignored) {
                // Errori di invio log ignorati silenziosamente
            }
        }, "remote-logger").start();
    }
}
