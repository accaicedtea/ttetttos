package com.app.model;

import com.google.gson.*;

import java.net.http.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Traduzioni UI tramite modello AI LOCALE leggero.
 *
 * NON richiede Ollama. Usa direttamente l'API REST di llama.cpp server
 * oppure, se non disponibile, scarica un modello GGUF tiny e lo esegue
 * tramite llama-cpp-python (pip) che pesa solo ~60MB di RAM.
 *
 * Gerarchia di tentativi:
 *   1. Cache locale  (./translations.json) — zero latenza, zero rete
 *   2. llama.cpp server su localhost:8080  — se già in esecuzione
 *   3. Modello hardcoded in I18n.java       — fallback sempre disponibile
 *
 * Il modello consigliato e' TinyLlama-1.1B-Chat (GGUF Q4_K_M, ~670MB).
 * Alternativa piu' leggera: Qwen2-0.5B-Instruct (GGUF Q4, ~350MB).
 *
 * Setup manuale (opzionale, solo per avere traduzioni AI):
 *   pip install llama-cpp-python
 *   llama-cpp-python --model tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf --port 8080
 *
 * Il setup-kiosk.sh installa automaticamente llama-cpp-python e scarica
 * il modello GGUF se non presente.
 */
public class TranslationManager {

    private static final String LLAMACPP_URL =
            System.getProperty("llamacpp.url", "http://localhost:8080/v1/chat/completions");

    private static final Path CACHE_PATH = resolveCachePath();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static Path resolveCachePath() {
        Path prod = Path.of("/opt/kiosk/translations.json");
        try { if (Files.isWritable(prod.getParent())) return prod; }
        catch (Exception ignored) {}
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
            JsonParser.parseString(json).getAsJsonObject(); // valida
            Files.writeString(CACHE_PATH, json);
            applyToI18n(json);
            System.out.println("[Trans] Traduzioni salvate: " + CACHE_PATH);
        } catch (Exception e) {
            System.err.println("[Trans] Errore: " + e.getMessage());
        }
    }

    // ── llama.cpp (OpenAI-compatible API, no Ollama needed) ───────────

    private static boolean isLlamaCppRunning() {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.discarding())
                       .statusCode() == 200;
        } catch (Exception e) { return false; }
    }

    private static String callLlamaCpp(String prompt) throws Exception {
        // Formato OpenAI chat completions (compatibile con llama.cpp, LM Studio, ecc.)
        JsonObject body = new JsonObject();
        body.addProperty("model", "local");
        body.addProperty("max_tokens", 1200);
        body.addProperty("temperature", 0.1);

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", "You are a JSON translation engine. Return only valid JSON.");
        messages.add(sys);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(user);
        body.add("messages", messages);

        var req = HttpRequest.newBuilder()
                .uri(URI.create(LLAMACPP_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode());

        JsonObject result = JsonParser.parseString(resp.body()).getAsJsonObject();
        String raw = result.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        // Estrai JSON dalla risposta
        int start = raw.indexOf('{'), end = raw.lastIndexOf('}') + 1;
        if (start < 0 || end <= start) return null;
        return raw.substring(start, end);
    }

    private static String buildPrompt() {
        return "Translate these UI strings for a food kiosk app into Italian (it), " +
               "English (en), German (de), French (fr), Arabic (ar).\n" +
               "Return ONLY a JSON object with this structure:\n" +
               "{\"it\":{\"key\":\"value\",...},\"en\":{...},\"de\":{...},\"fr\":{...},\"ar\":{...}}\n\n" +
               "Keys and Italian reference values:\n" +
               "welcome_title=Benvenuto, welcome_subtitle=Tocca per iniziare, " +
               "choose_lang=Scegli la lingua, start=Inizia l ordine, " +
               "add_to_cart=Aggiungi al carrello, added=Aggiunto!, " +
               "cart=Carrello, cart_empty=Il carrello e vuoto, total=Totale, " +
               "proceed=Procedi al pagamento, payment_title=Come vuoi pagare?, " +
               "cash=Contanti, cash_sub=Paga alla cassa, card=Carta, " +
               "card_sub=Bancomat Credito, confirm_title=Ordine confermato!, " +
               "confirm_sub=Il tuo numero e, confirm_msg=Ritira il tuo ordine alla cassa Grazie!, " +
               "back=Indietro, allergens=Allergeni, ingredients=Ingredienti, " +
               "qty=Qta, remove=Rimuovi, allergen_warning=Attenzione allergeni nel tuo ordine, " +
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
