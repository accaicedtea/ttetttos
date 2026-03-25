package com.app.model;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
/**
 * INVARIATO — cache locale del menu su file JSON.
 * File: /opt/kiosk/menu-cache.json (prod) | ./menu-cache.json (dev)
 */
public final class MenuCache {
    private MenuCache() {}
    public static boolean    isCached()                                          { return false; }
    public static JsonObject loadFromCache()                                     { return null;  }
    public static void       save(JsonObject menu, String version)               {}
    public static void       startBackgroundSync(Consumer<JsonObject> onUpdate)  {}
}
