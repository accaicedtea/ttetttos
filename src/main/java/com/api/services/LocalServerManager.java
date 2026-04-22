package com.api.services;

import com.app.model.OrderQueue;
import com.util.SystemManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.websocket.WsContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalServerManager {

    private static Javalin app;
    private static int port = 7070;
    private static int devicesConnected = 0;


    // Connessioni WebSocket attive (per i tablet)
    public static final Map<WsContext, String> authenticatedTablets = new ConcurrentHashMap<>();
    private static final Map<WsContext, String> tabletWebSockets = new ConcurrentHashMap<>();

    // Helper per inviare messaggi push a tutti i websocket attivi
        public static void broadcastTabletOrders() {
        // Riassegna gli ordini ai tablet attualmente connessi
        java.util.HashSet<String> activeIds = new java.util.HashSet<>(authenticatedTablets.values());
        com.app.model.OrderQueue.autoAssignOrders(activeIds);
        
        setDevicesConnected(activeIds.size());

        authenticatedTablets.keySet().stream().filter(ctx -> ctx.session.isOpen()).forEach(ctx -> {
            String sessionId = authenticatedTablets.get(ctx);
            String jsonQueue = com.app.model.OrderQueue.getTabletOrdersJson(sessionId);
            ctx.send(jsonQueue);
        });
    }

    private static void setDevicesConnected(int count) {
        LocalServerManager.devicesConnected = count;
        System.out.println("[LocalServerManager] Connected tablets: " + count);
    }
    
    public static int getDevicesConnected() {
        return devicesConnected;
    }

    public static void startLocalServer() {
        if (app != null) {
            return;
        }
        
        System.out.println("[LocalServerManager] Starting local Wi-Fi API Server for Android on port " + port + "...");

        // Initializza il server - ascolta su tutti gli indirizzi IPv4
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start("0.0.0.0", port);

        // --- AUTH ENDPOINT ---
        app.post("/auth", ctx -> {
            JsonObject body;
            try {
                body = com.google.gson.JsonParser.parseString(ctx.body()).getAsJsonObject();
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
                return;
            }

            if (!body.has("pin")) {
                ctx.status(HttpStatus.BAD_REQUEST).result("PIN required");
                return;
            }

            String pin = body.get("pin").getAsString();
            String token = JwtService.authenticateAndGenerateToken(pin);

            if (token == null) {
                ctx.status(HttpStatus.UNAUTHORIZED).result("Invalid PIN / Dispositivo rimosso dal DB");
            } else {
                JsonObject res = new JsonObject();
                res.addProperty("token", token);
                ctx.json(res.toString());
            }
        });

        // --- MIDDLEWARE DI PROTEZIONE ---
        app.before("/api/*", ctx -> {
            // Controllo 1: Il totem è lucchettato dal server remoto?
            if (com.util.SystemManager.isAppLocked()) {
                System.out.println("[LocalServerManager] Access denied. Totem is locked/disabled.");
                ctx.status(HttpStatus.FORBIDDEN).result("Totem attualmente disabilitato o bloccato dal server.");
                return; // Ferma il proseguimento
            }

            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.status(HttpStatus.UNAUTHORIZED).result("Missing or invalid token format");
                return;
            }

            String token = authHeader.substring(7);
            String role = JwtService.verifyAndGetRole(token);

            if (role == null) {
                ctx.status(HttpStatus.UNAUTHORIZED).result("Invalid or expired token");
                return;
            }

            // Memorizza il ruolo nel context se volessimo dividerli
            ctx.attribute("role", role);
        });

        // --- API ENDPOINT PROTETTI ---

        // Recupera ordini in coda (solo di esempio di integrazione con OrderQueue)
        app.get("/api/orders", ctx -> {
            String role = ctx.attribute("role");
            System.out.println("[LocalServerManager] Serving orders to Android device with role: " + role);

            // Carichiamo la coda degli ordini da order queue array in memoria per i tablet
            ctx.json(com.app.model.OrderQueue.getTabletOrdersJson());
        });

        // ESPONE IL CATALOGO: Menu completo e Ingredienti per l'Android App
        app.get("/api/catalog", ctx -> {
            String role = ctx.attribute("role");
            System.out.println("[LocalServerManager] Serving catalog (menu & ingredients) to Android device with role: " + role);
            try {
                com.app.pojo.MenuData menuData = com.api.repository.DataRepository.getMenu();
                java.util.List<com.app.pojo.Ingredient> ingredients = com.api.repository.DataRepository.getIngredients();

                JsonObject res = new JsonObject();
                Gson gson = new Gson();
                res.add("menu", JsonParser.parseString(gson.toJson(menuData)).getAsJsonObject());
                res.add("ingredienti", JsonParser.parseString(gson.toJson(ingredients)).getAsJsonArray());

                ctx.json(res.toString());
            } catch (Exception e) {
                System.err.println("[LocalServerManager] Errore recupero catalog: " + e.getMessage());
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Errore recupero catalogo");
            }
        });

        // TEST ENDPOINT - Aggiungi un ordine di test alla coda del tablet
        app.post("/api/orders/test", ctx -> {
            try {
                JsonObject testOrder = new JsonObject();
                testOrder.addProperty("tablet_order_id", "TEST-" + System.currentTimeMillis());
                testOrder.addProperty("totale", 15.50);
                
                JsonArray prodotti = new JsonArray();
                JsonObject prodotto = new JsonObject();
                prodotto.addProperty("nome", "Hamburger");
                prodotto.addProperty("quantita", 1);
                JsonArray ingredienti = new JsonArray();
                JsonObject ing = new JsonObject();
                ing.addProperty("nome", "Lattuga");
                ingredienti.add(ing);
                ing = new JsonObject();
                ing.addProperty("nome", "Pomodoro");
                ingredienti.add(ing);
                prodotto.add("ingredienti", ingredienti);
                prodotti.add(prodotto);
                
                testOrder.add("prodotti", prodotti);
                
                System.out.println("[LocalServerManager TEST] Aggiungendo ordine di test: " + testOrder);
                com.app.model.OrderQueue.enqueueForTablets(testOrder, testOrder.get("tablet_order_id").getAsString());
                
                ctx.status(HttpStatus.OK).result("Ordine di test aggiunto alla coda");
            } catch (Exception e) {
                System.err.println("[LocalServerManager TEST] Errore: " + e.getMessage());
                e.printStackTrace();
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Errore: " + e.getMessage());
            }
        });

        // --- WEBSOCKET ENDPOINT PUSH NOTIFICATIONS ---
        app.ws("/api/ws/orders", ws -> {
            ws.onConnect(ctx -> {
                // Imposta un timeout elevato affinché non cada la connessione ad ogni respiro
                ctx.session.setIdleTimeout(Duration.ofDays(30));

                System.out.println("[LocalServerManager] Tablet connected: " + ctx.sessionId());
                tabletWebSockets.put(ctx, ctx.sessionId());
                
                // Invia la coda attuale appena il tablet si connette
                // Only send after login now.
            });
            
            ws.onClose(ctx -> {
                System.out.println("[LocalServerManager] Tablet disconnected: " + ctx.sessionId());
                tabletWebSockets.remove(ctx);
                if (authenticatedTablets.remove(ctx) != null) {
                    broadcastTabletOrders(); // Reassign remaining orders
                }
            });
            
            ws.onError(ctx -> tabletWebSockets.remove(ctx));
            
            ws.onMessage(ctx -> {
                try {
                    JsonObject msg = JsonParser.parseString(ctx.message()).getAsJsonObject();
                    if (msg.has("action") && "ping".equals(msg.get("action").getAsString())) {
                        // Rispondo al ping per tenere viva la connessione se client manda heartbeat
                        ctx.send("{\"action\":\"pong\"}");
                        return;
                    }
                    
                    if (msg.has("action") && "kds_login".equals(msg.get("action").getAsString())) {
                        String pin = msg.has("pin") ? msg.get("pin").getAsString() : "";
                        try {
                            com.google.gson.JsonObject authResponse = OrdersService.kdsLogin(pin);
                            if (authResponse != null) {
                                System.out.println("[LocalServerManager] KDS Logged in successfully, session: " + ctx.sessionId());
                                authenticatedTablets.put(ctx, ctx.sessionId());
                                ctx.send("{\"action\":\"login_response\", \"success\":true}");
                                broadcastTabletOrders(); // Trigger assign and sync
                            } else {
                                ctx.send("{\"action\":\"login_response\", \"success\":false, \"message\":\"PIN errato o totem non associato\"}");
                            }
                        } catch (Exception e) {
                            ctx.send("{\"action\":\"login_response\", \"success\":false, \"message\":\"Autorizzazione fallita\"}");
                            System.err.println("[LocalServerManager] Login error: " + e.getMessage());
                        }
                    }

                    if (msg.has("action") && "take_order".equals(msg.get("action").getAsString())) {
                        String rawOrderId = msg.get("orderId").getAsString();
                        boolean isDone = rawOrderId.endsWith("-done");
                        String orderId = isDone ? rawOrderId.substring(0, rawOrderId.length() - 5) : rawOrderId;
                        
                        // Prova a PRENDERE l'ordine (mutual exclusion / the first one wins)
                        boolean success = com.app.model.OrderQueue.takeOrderFromTablets(orderId);
                        if (success) {
                            System.out.println("[LocalServerManager] Tablet " + ctx.sessionId() + " took/completed order " + orderId);
                            // Se rimosso con successo, aggiorna in broadcast tutti per farglielo togliere dallo schermo
                            broadcastTabletOrders();
                        } else {
                            System.out.println("[LocalServerManager] Tablet " + ctx.sessionId() + " tried to take order " + rawOrderId + " but it was already taken/removed.");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[LocalServerManager] WS Message Error: " + e.getMessage());
                }
            });
        });

    }

    public static void stopServer() {
        if (app != null) {
            app.stop();
            app = null;
            System.out.println("[LocalServerManager] Server stopped safely.");
        }
    }
}

