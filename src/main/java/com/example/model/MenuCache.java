package com.example.model;

import com.google.gson.*;

import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Cache locale del menu su file JSON.
 *
 * File: /opt/kiosk/menu-cache.json  (produzione)  |  ./menu-cache.json  (sviluppo)
 *
 * Struttura del file:
 * {
 *   "cached_at": "2026-03-16T10:00:00",
 *   "version":   "abc123",          ← hash/versione dal server (se disponibile)
 *   "menu":      { ...risposta completa del server... }
 * }
 *
 * Flusso:
 *   1. All'avvio: leggi cache → mostra subito il menu
 *   2. In background: chiama l'API → confronta con la cache
 *   3. Se diverso: aggiorna file + UI
 *   4. Poi ogni CHECK_INTERVAL minuti: ripeti il punto 2
 */
public class MenuCache {

    private static final Path CACHE_PATH     = resolveCachePath();
    private static final int  CHECK_INTERVAL = 5; // minuti tra i controlli in background

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static Path resolveCachePath() {
        Path prod = Path.of("/opt/kiosk/menu-cache.json");
        try {
            if (Files.isWritable(prod.getParent())) return prod;
        } catch (Exception ignored) {}
        return Path.of("menu-cache.json");
    }

    // ── API pubblica ──────────────────────────────────────────────────

    /** true se esiste una cache valida. */
    public static boolean isCached() {
        return Files.exists(CACHE_PATH);
    }

    /**
     * Legge il menu dalla cache locale.
     * Ritorna null se la cache non esiste o è corrotta.
     */
    public static JsonObject loadFromCache() {
        if (!Files.exists(CACHE_PATH)) return null;
        try {
            JsonObject wrapper = JsonParser.parseString(
                    Files.readString(CACHE_PATH)).getAsJsonObject();
            System.out.println("[MenuCache] Caricato da cache ("
                    + wrapper.get("cached_at").getAsString() + ")");
            return wrapper.getAsJsonObject("menu");
        } catch (Exception e) {
            System.err.println("[MenuCache] Cache corrotta: " + e.getMessage());
            return null;
        }
    }

    /**
     * Salva il menu in cache.
     * @param menuResponse risposta completa del server (con "ok", "data", ecc.)
     * @param version      stringa di versione/hash (può essere null)
     */
    public static void save(JsonObject menuResponse, String version) {
        try {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("cached_at", LocalDateTime.now().format(ISO));
            wrapper.addProperty("version",   version != null ? version : "");
            wrapper.add("menu", menuResponse);
            Files.writeString(CACHE_PATH, new GsonBuilder()
                    .setPrettyPrinting().create().toJson(wrapper));
            System.out.println("[MenuCache] Cache salvata in: " + CACHE_PATH);
        } catch (Exception e) {
            System.err.println("[MenuCache] Errore salvataggio cache: " + e.getMessage());
        }
    }

    /** Versione salvata nella cache (stringa vuota se non disponibile). */
    public static String getCachedVersion() {
        if (!Files.exists(CACHE_PATH)) return "";
        try {
            JsonObject w = JsonParser.parseString(
                    Files.readString(CACHE_PATH)).getAsJsonObject();
            return w.has("version") ? w.get("version").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Avvia un thread di background che:
     *  - Controlla subito se il menu è cambiato rispetto alla cache
     *  - Poi ripete ogni CHECK_INTERVAL minuti
     *
     * @param onUpdate callback chiamato sul thread di background quando il menu
     *                  è cambiato (il chiamante deve usare Platform.runLater se
     *                  aggiorna la UI)
     */
    public static void startBackgroundSync(java.util.function.Consumer<JsonObject> onUpdate) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    checkForUpdate(onUpdate);
                    Thread.sleep(CHECK_INTERVAL * 60 * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[MenuCache] Sync error: " + e.getMessage());
                    try { Thread.sleep(60_000); } catch (InterruptedException ie) { break; }
                }
            }
        }, "menu-cache-sync");
        t.setDaemon(true); // non blocca la chiusura dell'app
        t.start();
        System.out.println("[MenuCache] Background sync avviato (ogni " + CHECK_INTERVAL + " min).");
    }

    // ── Controllo aggiornamento ───────────────────────────────────────

    private static void checkForUpdate(java.util.function.Consumer<JsonObject> onUpdate)
            throws Exception {

        // Prima controlla la versione (endpoint leggero)
        String serverVersion = fetchVersion();
        String cachedVersion = getCachedVersion();

        System.out.println("[MenuCache] Versione server: " + serverVersion
                + "  cache: " + cachedVersion);

        if (!serverVersion.isEmpty()
                && serverVersion.equals(cachedVersion)
                && isCached()) {
            System.out.println("[MenuCache] Nessuna modifica rilevata.");
            return;
        }

        // Versione diversa o non disponibile → scarica il menu completo
        System.out.println("[MenuCache] Aggiornamento menu in corso...");
        JsonObject freshMenu = com.api.services.ViewsService.getMenu();
        save(freshMenu, serverVersion);
        System.out.println("[MenuCache] Menu aggiornato.");

        // Notifica l'UI
        if (onUpdate != null) onUpdate.accept(freshMenu);
    }

    /** Chiama l'endpoint di versione (leggero, pochi byte). */
    private static String fetchVersion() {
        try {
            JsonObject resp = com.api.services.ViewsService.getMenuVersion();
            // Prova diversi campi comuni per la versione
            for (String key : new String[]{"version", "versione", "hash", "updated_at"}) {
                if (resp.has(key) && !resp.get(key).isJsonNull())
                    return resp.get(key).getAsString();
            }
            // Se non c'è un campo versione usa il timestamp della risposta
            return resp.toString().hashCode() + "";
        } catch (Exception e) {
            System.err.println("[MenuCache] Fetch versione fallito: " + e.getMessage());
            return "";
        }
    }
}
