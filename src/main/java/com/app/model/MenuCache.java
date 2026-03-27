package com.app.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.util.RemoteLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache locale del menu su file JSON.
 * File: /opt/kiosk/menu-cache.json (prod) | ./menu-cache.json (dev)
 */

public final class MenuCache {
    private MenuCache() {
    }

    private static final Path PROD_CACHE = Path.of("/opt/kiosk/menu-cache.json");
    private static final Path DEV_CACHE = Path.of("./menu-cache.json");
    private static final Path CACHE_PATH = Files.isWritable(PROD_CACHE.getParent()) ? PROD_CACHE : DEV_CACHE;
    private static final long CACHE_TTL_SECONDS = Long.parseLong(System.getProperty("menu.cache.ttl.seconds", "300")); // default 5 minuti

    private static ScheduledExecutorService syncExecutor;

    public static boolean isCached() {
        try {
            if (!Files.exists(CACHE_PATH))
                return false;
            String txt = Files.readString(CACHE_PATH, StandardCharsets.UTF_8);
            if (txt.isBlank())
                return false;
            JsonObject obj = JsonParser.parseString(txt).getAsJsonObject();
            if (!obj.has("menu") || !obj.has("version") || !obj.has("ts"))
                return false;
            long ts = obj.get("ts").getAsLong();
            return System.currentTimeMillis() - ts <= TimeUnit.SECONDS.toMillis(CACHE_TTL_SECONDS);
        } catch (Exception e) {
            RemoteLogger.warn("MenuCache", "isCached error: " + e.getMessage());
            return false;
        }
    }

    public static JsonObject loadFromCache() {
        try {
            if (!Files.exists(CACHE_PATH))
                return null;
            String txt = Files.readString(CACHE_PATH, StandardCharsets.UTF_8);
            if (txt.isBlank())
                return null;
            JsonObject obj = JsonParser.parseString(txt).getAsJsonObject();
            if (obj.has("menu") && obj.get("menu").isJsonObject())
                return obj.get("menu").getAsJsonObject();
            return null;
        } catch (Exception e) {
            RemoteLogger.error("MenuCache", "loadFromCache error", e);
            return null;
        }
    }

    public static void save(JsonObject menu, String version) {
        if (menu == null)
            return;
        JsonObject out = new JsonObject();
        out.add("menu", menu);
        out.add("version", new JsonPrimitive(version != null ? version : ""));
        out.add("ts", new JsonPrimitive(System.currentTimeMillis()));
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Files.writeString(CACHE_PATH, out.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            RemoteLogger.error("MenuCache", "save error", e);
        }
    }

    public static void startBackgroundSync(Consumer<JsonObject> onUpdate) {
        if (syncExecutor != null && !syncExecutor.isShutdown())
            return;
        syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MenuCache-sync");
            t.setDaemon(true);
            return t;
        });
        syncExecutor.scheduleWithFixedDelay(() -> {
            try {
                JsonObject fresh = com.api.services.ViewsService.getMenu();
                if (fresh != null) {
                    save(fresh, "");
                    if (onUpdate != null)
                        onUpdate.accept(fresh);
                }
            } catch (Exception e) {
                RemoteLogger.error("MenuCache", "startBackgroundSync tick error", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public static void stopBackgroundSync() {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdownNow();
            syncExecutor = null;
            RemoteLogger.info("MenuCache", "Background sync stopped");
        }
    }
}
