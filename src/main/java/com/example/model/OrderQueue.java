package com.example.model;

import com.api.services.OrdersService;
import com.google.gson.*;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestione ordini locale con persistenza e sincronizzazione offline.
 *
 * Il JSON inviato al server segue esattamente la struttura attesa da
 * OrdiniController::create():
 *
 * {
 *   "prodotti": [
 *     {
 *       "id":              123,          // prodotto_id (int, obbligatorio)
 *       "nome":            "Kumpir",     // prodotto_nome
 *       "sku":             null,
 *       "quantita":        2,
 *       "prezzo_unitario": 7.50,         // prezzo singolo, non totale riga
 *       "iva":             10,           // aliquota IVA (4 / 10 / 22)
 *       "note":            null,
 *       "ingredienti":     []            // array composizione (vuoto se nessuno)
 *     }
 *   ],
 *   "subtotale":     15.00,
 *   "sconto":        0,
 *   "totale":        15.00,
 *   "pagamento_tipo": "contanti",       // carta | contanti | pos_esterno | test
 *   "lingua":        "it",
 *   "sessione_id":   "uuid",            // UUID sessione totem
 *   "note":          null
 * }
 *
 * File locali:
 *   current-order.json  — ordine corrente (aggiornato ad ogni modifica carrello)
 *   order-queue.json    — coda ordini offline da inviare al ritorno della rete
 */
public class OrderQueue {

    private static final Path CURRENT_ORDER_FILE = resolvePath("current-order.json");
    private static final Path QUEUE_FILE          = resolvePath("order-queue.json");
    private static final DateTimeFormatter ISO    = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // IVA di default — il server la usa per calcolare iva_4/iva_10/iva_22
    // Cambia in base alla categoria prodotto se il server lo supporta
    private static final int DEFAULT_IVA = 10;

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "order-sync");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicInteger localSeq = new AtomicInteger(
            (int)(System.currentTimeMillis() % 100));

    // Sessione UUID: identifica questa sessione del totem (cambia ad ogni avvio)
    private static final String SESSION_ID = UUID.randomUUID().toString();

    private static Runnable onSyncSuccess = null;

    private static Path resolvePath(String name) {
        Path p = Path.of("/opt/kiosk/" + name);
        try { if (Files.isWritable(p.getParent())) return p; }
        catch (Exception ignored) {}
        return Path.of(name);
    }

    // ── API pubblica ──────────────────────────────────────────────────

    public static void setOnSyncSuccess(Runnable cb) { onSyncSuccess = cb; }

    /**
     * Salva l'ordine corrente su disco.
     * Chiamato automaticamente da CartManager ad ogni addItem/removeItem.
     */
    public static void saveCurrentOrder(CartManager cart) {
        try {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("stato",      "locale");
            wrapper.addProperty("salvato_il", LocalDateTime.now().format(ISO));
            wrapper.add("payload", buildServerPayload(cart, "contanti")); // metodo placeholder
            Files.writeString(CURRENT_ORDER_FILE,
                    new GsonBuilder().setPrettyPrinting().create().toJson(wrapper));
        } catch (Exception e) {
            System.err.println("[OrderQueue] Salvataggio: " + e.getMessage());
        }
    }

    /**
     * Carica l'ordine corrente dal file (crash recovery all'avvio).
     * Ritorna null se non esiste o e gia stato inviato.
     */
    public static JsonObject loadCurrentOrder() {
        if (!Files.exists(CURRENT_ORDER_FILE)) return null;
        try {
            JsonObject w = JsonParser.parseString(
                    Files.readString(CURRENT_ORDER_FILE)).getAsJsonObject();
            String stato = w.has("stato") ? w.get("stato").getAsString() : "";
            if (stato.equals("inviato") || stato.equals("annullato")) return null;
            return w.has("payload") ? w.getAsJsonObject("payload") : null;
        } catch (Exception e) {
            System.err.println("[OrderQueue] Caricamento: " + e.getMessage());
            return null;
        }
    }

    /** Marca l'ordine corrente come inviato (dopo conferma). */
    public static void clearCurrentOrder() {
        try {
            if (!Files.exists(CURRENT_ORDER_FILE)) return;
            JsonObject w = JsonParser.parseString(
                    Files.readString(CURRENT_ORDER_FILE)).getAsJsonObject();
            w.addProperty("stato", "inviato");
            Files.writeString(CURRENT_ORDER_FILE,
                    new GsonBuilder().create().toJson(w));
        } catch (Exception ignored) {}
    }

    /**
     * Invia l'ordine al server in modo asincrono.
     * Se offline aggiunge alla coda persistente su disco.
     *
     * @param cart     carrello corrente
     * @param method   "contanti" | "carta" (tradotto da "cash"/"card")
     * @param onDone   callback con numero ordine reale (stringa tipo "2026-000042")
     *                 oppure numero locale se offline
     */
    public static void submitOrder(CartManager cart, String method,
                                   java.util.function.Consumer<String> onDone) {
        // Normalizza il metodo pagamento per il server
        String pagamento = switch (method) {
            case "cash"  -> "contanti";
            case "card"  -> "carta";
            default      -> method;
        };

        JsonObject payload = buildServerPayload(cart, pagamento);

        EXECUTOR.submit(() -> {
            try {
                JsonObject resp = OrdersService.createOrder(payload);
                String numero = extractNumeroOrdine(resp);
                clearCurrentOrder();
                System.out.println("[OrderQueue] Ordine inviato: " + numero);
                if (onDone != null)
                    javafx.application.Platform.runLater(() -> onDone.accept(numero));
            } catch (Exception e) {
                System.err.println("[OrderQueue] Offline, salvo in coda: " + e.getMessage());
                String localNum = addToQueue(payload);
                if (onDone != null)
                    javafx.application.Platform.runLater(() -> onDone.accept(localNum));
            }
        });
    }

    /** Avvia sync coda offline ogni 30 secondi. */
    public static void startQueueSync() {
        EXECUTOR.scheduleWithFixedDelay(() -> {
            try { flushQueue(); }
            catch (Exception e) {
                System.err.println("[OrderQueue] Sync: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
        System.out.println("[OrderQueue] Sync avviato (ogni 30s).");
    }

    public static int queueSize() {
        try { return loadQueue().size(); }
        catch (Exception e) { return 0; }
    }

    // ── Costruzione payload server ────────────────────────────────────

    /**
     * Costruisce il JSON esatto atteso da OrdiniController::create().
     *
     * Struttura prodotti:
     *   id, nome, sku, quantita, prezzo_unitario, iva, note, ingredienti
     *
     * Nota: CartItem non ha l'id del prodotto dal DB — viene passato 0
     * se non disponibile. Il server usa 'nome' come fallback.
     * Per avere l'id corretto, estendere CartItem con il campo productId
     * quando si costruisce la card dal JSON del menu.
     */
    private static JsonObject buildServerPayload(CartManager cart, String pagamento) {
        JsonObject body = new JsonObject();

        // Prodotti
        JsonArray prodotti = new JsonArray();
        for (CartItem item : cart.getItems()) {
            JsonObject p = new JsonObject();
            p.addProperty("id",               item.getProductId()); // 0 se non disponibile
            p.addProperty("nome",             item.getName());
            p.addProperty("sku",              (String) null);
            p.addProperty("quantita",         item.getQty());
            p.addProperty("prezzo_unitario",  item.getPriceVal());
            p.addProperty("iva",              item.getIva());       // default 10
            p.addProperty("totale_riga",      roundTo2(item.getPriceVal() * item.getQty()));
            p.addProperty("note",             (String) null);
            p.add("ingredienti", new JsonArray());
            prodotti.add(p);
        }
        body.add("prodotti", prodotti);

        // Totali
        double subtotale = cart.totalPrice();
        body.addProperty("subtotale",     roundTo2(subtotale));
        body.addProperty("sconto",        0);
        body.addProperty("totale",        roundTo2(subtotale));

        // Metadati ordine
        body.addProperty("pagamento_tipo", pagamento);
        body.addProperty("lingua",         com.example.model.I18n.getLang());
        body.addProperty("sessione_id",    SESSION_ID);
        body.addProperty("note",           (String) null);

        return body;
    }

    private static double roundTo2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ── Coda offline ──────────────────────────────────────────────────

    private static String addToQueue(JsonObject payload) {
        try {
            JsonArray queue = loadQueue();
            int localNum = localSeq.incrementAndGet();
            String localId = "LOC-" + String.format("%04d", localNum);
            payload.addProperty("_numero_locale", localId);
            payload.addProperty("_stato_coda",    "pending");
            payload.addProperty("_salvato_il",    LocalDateTime.now().format(ISO));
            queue.add(payload);
            Files.writeString(QUEUE_FILE,
                    new GsonBuilder().create().toJson(queue));
            System.out.println("[OrderQueue] Coda: " + localId
                    + " (totale: " + queue.size() + ")");
            return localId;
        } catch (Exception e) {
            System.err.println("[OrderQueue] Errore coda: " + e.getMessage());
            return "LOC-" + localSeq.incrementAndGet();
        }
    }

    private static void flushQueue() throws Exception {
        JsonArray queue = loadQueue();
        if (queue.isEmpty()) return;

        System.out.println("[OrderQueue] Flush: " + queue.size() + " ordini...");
        JsonArray remaining = new JsonArray();
        boolean anySent = false;

        for (JsonElement el : queue) {
            JsonObject order = el.getAsJsonObject().deepCopy();
            // Rimuovi campi interni prima di inviare
            order.remove("_numero_locale");
            order.remove("_stato_coda");
            order.remove("_salvato_il");
            try {
                JsonObject resp = OrdersService.createOrder(order);
                System.out.println("[OrderQueue] Inviato: " + extractNumeroOrdine(resp));
                anySent = true;
            } catch (Exception e) {
                remaining.add(el); // rimane in coda
            }
        }

        Files.writeString(QUEUE_FILE,
                new GsonBuilder().create().toJson(remaining));

        if (anySent && onSyncSuccess != null)
            javafx.application.Platform.runLater(onSyncSuccess);
    }

    private static JsonArray loadQueue() {
        if (!Files.exists(QUEUE_FILE)) return new JsonArray();
        try {
            return JsonParser.parseString(
                    Files.readString(QUEUE_FILE)).getAsJsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String extractNumeroOrdine(JsonObject resp) {
        // Struttura risposta: { ok, data: { ordine_id, numero_ordine } }
        try {
            if (resp.has("data")) {
                JsonObject data = resp.getAsJsonObject("data");
                if (data.has("numero_ordine"))
                    return data.get("numero_ordine").getAsString();
                if (data.has("ordine_id"))
                    return "#" + data.get("ordine_id").getAsInt();
            }
        } catch (Exception ignored) {}
        return "#" + localSeq.incrementAndGet();
    }
}
