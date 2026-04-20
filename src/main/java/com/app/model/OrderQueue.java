package com.app.model;

import com.api.services.OrdersService;
import com.google.gson.*;
import com.app.model.OrderStateManager;

import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestione ordini con persistenza locale e sincronizzazione offline.
 *
 * Refactoring rispetto all'originale:
 * - buildServerPayload() usa CartItem.getPriceVal() / CartItem.getIva()
 * (invariato funzionalmente, ora usa getter tipizzati)
 * - Tutto il resto invariato
 *
 * File locali:
 * /opt/kiosk/current-order.json → ordine corrente (crash recovery)
 * /opt/kiosk/order-queue.json → coda ordini offline
 */
public final class OrderQueue {

    private static final Path CURRENT_ORDER_FILE = resolvePath("current-order.json");
    private static final Path QUEUE_FILE = resolvePath("order-queue.json");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Duration MAX_QUEUE_AGE = Duration.ofMinutes(30);

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "order-sync");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger localSeq = new AtomicInteger((int) (System.currentTimeMillis() % 100));

    private static final int MAX_QUEUE_SIZE = 100;
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static Runnable onSyncSuccess = null;

    private OrderQueue() {
    }

    // ── Path resolution ──────────────────────────────────────────────

    private static Path resolvePath(String name) {
        Path p = Path.of("/opt/kiosk/" + name);
        try {
            if (Files.isWritable(p.getParent()))
                return p;
        } catch (Exception ignored) {
        }
        return Path.of(name);
    }

    // ── API pubblica ──────────────────────────────────────────────────

    public static void setOnSyncSuccess(Runnable cb) {
        onSyncSuccess = cb;
    }

    public static void saveCurrentOrder(CartManager cart) {
        try {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("stato", "locale");
            wrapper.addProperty("salvato_il", LocalDateTime.now().format(ISO));
            wrapper.add("payload", buildServerPayload(cart, "contanti"));
            Files.writeString(CURRENT_ORDER_FILE,
                    new GsonBuilder().setPrettyPrinting().create().toJson(wrapper));
        } catch (Exception e) {
            System.err.println("[OrderQueue] Salvataggio: " + e.getMessage());
        }
    }

    public static JsonObject loadCurrentOrder() {
        if (!Files.exists(CURRENT_ORDER_FILE))
            return null;
        try {
            JsonObject w = JsonParser.parseString(Files.readString(CURRENT_ORDER_FILE)).getAsJsonObject();
            String stato = w.has("stato") ? w.get("stato").getAsString() : "";
            if (stato.equals("inviato") || stato.equals("annullato"))
                return null;
            return w.has("payload") ? w.getAsJsonObject("payload") : null;
        } catch (Exception e) {
            System.err.println("[OrderQueue] Caricamento: " + e.getMessage());
            return null;
        }
    }

    public static void clearCurrentOrder() {
        try {
            if (!Files.exists(CURRENT_ORDER_FILE))
                return;
            JsonObject w = JsonParser.parseString(Files.readString(CURRENT_ORDER_FILE)).getAsJsonObject();
            w.addProperty("stato", "inviato");
            Files.writeString(CURRENT_ORDER_FILE, new GsonBuilder().create().toJson(w));
        } catch (Exception ignored) {
        }
    }

    public static void createOrderAsync(CartManager cart, Runnable onSuccess, java.util.function.Consumer<String> onError) {
        String pagamento = "N/A"; // Il pagamento effettivo verrà gestito dopo
        JsonObject payload = buildServerPayload(cart, pagamento);
        System.err.println("[OrderQueue] createOrderAsync payload: " + payload.toString());
        
        EXECUTOR.submit(() -> {
            try {
                JsonObject resp = OrdersService.createOrder(payload);
                String numero = extractNumeroOrdine(resp);
                
                try {
                    int orderId = Integer.parseInt(numero);
                    OrderStateManager.get().createOrder(orderId);
                } catch (NumberFormatException e) {
                    System.err.println("[OrderQueue] Invalid order ID format: " + numero);
                }
                
                clearCurrentOrder();
                if (onSuccess != null)
                    javafx.application.Platform.runLater(onSuccess);
            } catch (Exception e) {
                System.err.println("[OrderQueue] Errore creazione ordine: " + e.getMessage());
                if (onError != null)
                    javafx.application.Platform.runLater(() -> onError.accept(e.getMessage()));
            }
        });
    }

    public static void startQueueSync() {
        EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                flushQueue();
            } catch (Exception e) {
                System.err.println("[OrderQueue] Sync: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public static int queueSize() {
        try {
            return loadQueue().size();
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Payload server ────────────────────────────────────────────────

    private static JsonObject buildServerPayload(CartManager cart, String pagamento) {
        JsonObject body = new JsonObject();
        JsonArray prodotti = new JsonArray();
        for (CartItem item : cart.getItems()) {
            JsonObject p = new JsonObject();
            int productId = item.getProductId() > 0 ? item.getProductId() : 1; // fallback per custom Kumpir e data non
                                                                               // valida
            p.addProperty("id", productId);
            p.addProperty("nome", item.getName());
            p.addProperty("sku", item.getSku());
            p.addProperty("quantita", item.getQty());
            p.addProperty("prezzo_unitario", item.getPriceVal());
            p.addProperty("iva", item.getIva());
            p.addProperty("totale_riga", round2(item.getPriceVal() * item.getQty()));
            p.addProperty("note", (String) null);
            JsonArray ingArray = new JsonArray();
            if (item.getIngredienti() != null) {
                for (String ing : item.getIngredienti()) {
                    JsonObject ingObj = new JsonObject();
                    ingObj.addProperty("nome", ing);
                    ingArray.add(ingObj);
                }
            }
            p.add("ingredienti", ingArray);
            prodotti.add(p);
        }
        body.add("prodotti", prodotti);
        double sub = cart.totalPrice();
        body.addProperty("subtotale", round2(sub));
        body.addProperty("sconto", 0);
        body.addProperty("totale", round2(sub));
        body.addProperty("pagamento_tipo", pagamento);
        body.addProperty("lingua", I18n.getLang());
        body.addProperty("sessione_id", SESSION_ID);
        body.addProperty("note", (String) null);
        return body;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ── Coda offline ──────────────────────────────────────────────────

    private static String addToQueue(JsonObject payload) {
        try {
            JsonArray queue = loadQueue();
            if (queue.size() >= MAX_QUEUE_SIZE) {
                JsonElement removed = queue.remove(0); // solleva vecchi ordini per fare spazio
                System.err.println("[OrderQueue] coda piena, elimino ordine vecchio: " + removed.toString());
            }
            int n = localSeq.incrementAndGet();
            String localId = "LOC-" + String.format("%04d", n);
            payload.addProperty("_numero_locale", localId);
            payload.addProperty("_stato_coda", "pending");
            payload.addProperty("_salvato_il", LocalDateTime.now().format(ISO));
            queue.add(payload);
            Files.writeString(QUEUE_FILE, new GsonBuilder().create().toJson(queue));
            return localId;
        } catch (Exception e) {
            System.err.println("[OrderQueue] addToQueue fallito: " + e.getMessage());
            return "LOC-" + localSeq.incrementAndGet();
        }
    }

    private static void flushQueue() throws Exception {
        JsonArray queue = loadQueue();
        if (queue.isEmpty())
            return;
        JsonArray remaining = new JsonArray();
        boolean anySent = false;
        for (JsonElement el : queue) {
            JsonObject order = el.getAsJsonObject().deepCopy();
            order.remove("_numero_locale");
            order.remove("_stato_coda");
            order.remove("_salvato_il");
            System.err.println("[OrderQueue] flushQueue payload: " + order.toString());
            try {
                OrdersService.createOrder(order);
                anySent = true;
            } catch (Exception e) {
                remaining.add(el);
            }
        }
        Files.writeString(QUEUE_FILE, new GsonBuilder().create().toJson(remaining));
        if (anySent && onSyncSuccess != null)
            javafx.application.Platform.runLater(onSyncSuccess);
    }

    private static JsonArray loadQueue() {
        if (!Files.exists(QUEUE_FILE))
            return new JsonArray();
        try {
            JsonArray queue = JsonParser.parseString(Files.readString(QUEUE_FILE)).getAsJsonArray();
            JsonArray pruned = pruneStaleOrders(queue);
            if (pruned.size() != queue.size()) {
                Files.writeString(QUEUE_FILE, new GsonBuilder().create().toJson(pruned));
                System.err.println("[OrderQueue] coda scaduta, rimosse " + (queue.size() - pruned.size()) + " ordinI");
            }
            return pruned;
        } catch (Exception e) {
            System.err.println("[OrderQueue] loadQueue fallito: " + e.getMessage());
            return new JsonArray();
        }
    }

    private static JsonArray pruneStaleOrders(JsonArray queue) {
        JsonArray valid = new JsonArray();
        LocalDateTime now = LocalDateTime.now();
        for (JsonElement el : queue) {
            if (!el.isJsonObject())
                continue;
            JsonObject order = el.getAsJsonObject();
            if (!order.has("_salvato_il")) {
                valid.add(order);
                continue;
            }
            String saved = order.get("_salvato_il").getAsString();
            try {
                LocalDateTime date = LocalDateTime.parse(saved, ISO);
                if (date.plus(MAX_QUEUE_AGE).isAfter(now)) {
                    valid.add(order);
                } else {
                    System.err.println("[OrderQueue] scarto ordine scaduto: " + order.toString());
                }
            } catch (Exception e) {
                valid.add(order);
            }
        }
        return valid;
    }

    private static String extractNumeroOrdine(JsonObject resp) {
        try {
            if (resp.has("data")) {
                JsonObject data = resp.getAsJsonObject("data");
                if (data.has("numero_ordine"))
                    return data.get("numero_ordine").getAsString();
                if (data.has("ordine_id"))
                    return "#" + data.get("ordine_id").getAsInt();
            }
        } catch (Exception ignored) {
        }
        return "#" + localSeq.incrementAndGet();
    }
}
