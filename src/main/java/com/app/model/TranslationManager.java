package com.app.model;

import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Traduzioni UI tramite modello AI locale leggero (llama.cpp).
 *
 * NOTA: questo file era letteralmente duplicato 3 volte nel progetto originale.
 * Ora esiste solo qui — il package corretto è com.app.model.
 *
 * Gerarchia di tentativi:
 *  1. Cache locale (./translations.json)  → zero latenza
 *  2. llama.cpp server su localhost:8080  → se in esecuzione
 *  3. Hardcoded in I18n.java              → sempre disponibile
 */
public final class TranslationManager {

    private static final String LLAMACPP_URL =
            System.getProperty("llamacpp.url", "http://localhost:8080/v1/chat/completions");

    private static final Path CACHE_PATH = resolveCachePath();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private TranslationManager() {}

    // ── Cache path ────────────────────────────────────────────────────

    private static Path resolveCachePath() {
        Path prod = Path.of("/opt/kiosk/translations.json");
        try { if (Files.isWritable(prod.getParent())) return prod; } catch (Exception ignored) {}
        return Path.of("translations.json");
    }

    // ── API pubblica ──────────────────────────────────────────────────

    public static boolean isCached() { return Files.exists(CACHE_PATH); }

    public static void loadFromCache() {
        if (!Files.exists(CACHE_PATH)) return;
        try {
            applyToI18n(Files.readString(CACHE_PATH));
            System.out.println("[Trans] Traduzioni da cache: " + CACHE_PATH);
        } catch (Exception e) {
            System.err.println("[Trans] Cache non leggibile: " + e.getMessage());
        }
    }

    public static void fetchAndSave() {
        if (!isLlamaCppRunning()) {
            System.out.println("[Trans] llama.cpp non disponibile — uso fallback I18n.");
            return;
        }
        System.out.println("[Trans] Generazione traduzioni con llama.cpp...");
        try {
            String json = callLlamaCpp(buildPrompt());
            if (json == null) { System.err.println("[Trans] Risposta vuota."); return; }
            JsonParser.parseString(json).getAsJsonObject(); // valida JSON
            Files.writeString(CACHE_PATH, json);
            applyToI18n(json);
            System.out.println("[Trans] Traduzioni salvate.");
        } catch (Exception e) {
            System.err.println("[Trans] Errore: " + e.getMessage());
        }
    }

    // ── llama.cpp (API OpenAI-compatible) ────────────────────────────

    private static boolean isLlamaCppRunning() {
        try {
            return HTTP.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.discarding()
            ).statusCode() == 200;
        } catch (Exception e) { return false; }
    }

    private static String callLlamaCpp(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", "local");
        body.addProperty("max_tokens", 1200);
        body.addProperty("temperature", 0.1);

        JsonArray messages = new JsonArray();
        JsonObject sys  = new JsonObject(); sys.addProperty("role","system"); sys.addProperty("content","You are a JSON translation engine. Return only valid JSON."); messages.add(sys);
        JsonObject user = new JsonObject(); user.addProperty("role","user");   user.addProperty("content", prompt); messages.add(user);
        body.add("messages", messages);

        var resp = HTTP.send(
            HttpRequest.newBuilder().uri(URI.create(LLAMACPP_URL))
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode());

        String raw = JsonParser.parseString(resp.body())
            .getAsJsonObject()
            .getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").get("content").getAsString();

        int start = raw.indexOf('{'), end = raw.lastIndexOf('}') + 1;
        return (start < 0 || end <= start) ? null : raw.substring(start, end);
    }

    private static String buildPrompt() {
        return "Translate these UI strings for a food kiosk app into Italian (it), " +
               "English (en), German (de), French (fr), Arabic (ar).\n" +
               "Return ONLY a JSON object: {\"it\":{\"key\":\"value\",...},\"en\":{...},...}\n\n" +
               "Keys (Italian reference): welcome_title=Benvenuto, " +
               "welcome_subtitle=Tocca per iniziare, choose_lang=Scegli la lingua, " +
               "start=Inizia l ordine, add_to_cart=Aggiungi al carrello, added=Aggiunto!, " +
               "cart=Carrello, cart_empty=Il carrello e vuoto, total=Totale, " +
               "proceed=Procedi al pagamento, payment_title=Come vuoi pagare?, " +
               "cash=Contanti, cash_sub=Paga alla cassa, card=Carta, card_sub=Bancomat Credito, " +
               "confirm_title=Ordine confermato!, confirm_sub=Il tuo numero e, " +
               "confirm_msg=Ritira il tuo ordine alla cassa Grazie!, back=Indietro, " +
               "allergens=Allergeni, ingredients=Ingredienti, qty=Qta, remove=Rimuovi, " +
               "allergen_warning=Attenzione allergeni nel tuo ordine, " +
               "order_number=Numero ordine\n\nReturn ONLY the JSON.";
    }

    private static void applyToI18n(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<String, Map<String, String>> loaded = new HashMap<>();
            for (String lang : new String[]{"it","en","de","fr","ar"}) {
                if (!root.has(lang)) continue;
                Map<String, String> m = new HashMap<>();
                root.getAsJsonObject(lang).entrySet()
                    .forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
                loaded.put(lang, m);
            }
            I18n.mergeTranslations(loaded);
        } catch (Exception e) {
            System.err.println("[Trans] Parsing fallito: " + e.getMessage());
        }
    }
}
