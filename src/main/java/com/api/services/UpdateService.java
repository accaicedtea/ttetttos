package com.api.services;

import com.api.Api;
import com.google.gson.JsonObject;
import com.util.SystemManager;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class UpdateService {

    private static final String APP_DIR = System.getProperty("user.home") + "/.totem-kiosk/app";
    private static final Path CURRENT_JAR = Paths.get(APP_DIR, "demo-1.jar");
    private static final Path BACKUP_JAR = Paths.get(APP_DIR, "demo-1.jar.bak");
    private static final Path PENDING_JSON = Paths.get(APP_DIR, "update_pending.json");
    private static final Path ROLLBACK_JSON = Paths.get(APP_DIR, "rollback_pending.json");

    private static volatile boolean updating = false;
    private static volatile boolean hasCheckedUpdates = false;

    /**
     * Esegue il controllo di un aggiornamento disponibile una sola volta.
     */
    public static void checkForUpdates() {
        if (hasCheckedUpdates) return;
        hasCheckedUpdates = true;

        new Thread(() -> {
            try {
                JsonObject updateResponse = Api.apiPost("aggiornamenti/check", new JsonObject());
                
                if (updateResponse != null) {
                    JsonObject data = updateResponse.has("data") && !updateResponse.get("data").isJsonNull() ? 
                                        updateResponse.getAsJsonObject("data") : updateResponse;
                    
                    boolean hasUpdate = (data.has("aggiornamento_disponibile") && data.get("aggiornamento_disponibile").getAsBoolean()) 
                                     || (updateResponse.has("aggiornamento_disponibile") && updateResponse.get("aggiornamento_disponibile").getAsBoolean())
                                     || (data.has("update_available") && data.get("update_available").getAsBoolean());
                    
                    if (hasUpdate) {
                        String versioneA = "Nuova";
                        if (data.has("versione_stabile") && !data.get("versione_stabile").isJsonNull()) {
                            versioneA = data.get("versione_stabile").getAsString();
                        } else if (data.has("versione_a") && !data.get("versione_a").isJsonNull()) {
                            versioneA = data.get("versione_a").getAsString();
                        } else if (updateResponse.has("versione_stabile") && !updateResponse.get("versione_stabile").isJsonNull()) {
                            versioneA = updateResponse.get("versione_stabile").getAsString();
                        } else if (updateResponse.has("versione_a") && !updateResponse.get("versione_a").isJsonNull()) {
                            versioneA = updateResponse.get("versione_a").getAsString();
                        } else if (data.has("update_version") && !data.get("update_version").isJsonNull()) {
                            versioneA = data.get("update_version").getAsString();
                        }

                        Long aggiornamentoId = null;
                        if (data.has("aggiornamento_id") && !data.get("aggiornamento_id").isJsonNull()) {
                            aggiornamentoId = data.get("aggiornamento_id").getAsLong();
                        } else if (updateResponse.has("aggiornamento_id") && !updateResponse.get("aggiornamento_id").isJsonNull()) {
                            aggiornamentoId = updateResponse.get("aggiornamento_id").getAsLong();
                        }
                        
                        System.out.println("[UpdateService] Trovato aggiornamento! Versione: " + versioneA + " (ID: " + aggiornamentoId + ")");
                        
                        if (!"Nuova".equals(versioneA)) {
                            SystemManager.showUpdatePrompt(versioneA, aggiornamentoId);
                        } else {
                            System.err.println("[UpdateService] Errore: versione target mancante. Update annullato. " + updateResponse.toString());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UpdateService] Errore controllando gli aggiornamenti all'avvio: " + e.getMessage());
            }
        }, "update-check").start();
    }

    /**
     * Avvia il processo di aggiornamento asincrono se non e' gia' in corso.
     */
    public static void startUpdate(String targetVersion, Long updateId) {
        if (updating) return;
        updating = true;

        new Thread(() -> {
            try {
                System.out.println("[UpdateService] Inizio aggiornamento alla versione " + targetVersion);
                
                // Segnala l'inizio
                sendStatus(updateId, "in_corso", null);
                
                // Mostra la UI di aggionamento (che blocca l'utente)
                SystemManager.showUpdateScreen(targetVersion);

                Path tempJar = Paths.get(APP_DIR, "demo-1-update.jar");
                
                // Assicurati che la directory esista
                Files.createDirectories(CURRENT_JAR.getParent());

                // Download da GitHub (URL pubblico delle release)
                String downloadUrl = "https://github.com/user/repository/releases/download/v" + targetVersion + "/demo-1.jar";
                System.out.println("[UpdateService] Download da: " + downloadUrl);
                
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                if (response.statusCode() >= 400) {
                    throw new Exception("HTTP " + response.statusCode() + " scaricando il JAR.");
                }

                long size = Files.copy(response.body(), tempJar, StandardCopyOption.REPLACE_EXISTING);
                if (size < 1000) { // Se troppo piccolo e' probabilmenta una pagina 404
                    throw new Exception("JAR scaricato troppo piccolo, o pagina di errore.");
                }

                // Salviamo le info pendenti *prima* di sostituire
                JsonObject pending = new JsonObject();
                if (updateId != null) {
                    pending.addProperty("id", updateId);
                }
                pending.addProperty("target", targetVersion);
                Files.writeString(PENDING_JSON, pending.toString());

                // Backup e Sostituzione
                if (Files.exists(CURRENT_JAR)) {
                    Files.move(CURRENT_JAR, BACKUP_JAR, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tempJar, CURRENT_JAR, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("[UpdateService] JAR sostituito con successo. Termino applicazione in 3 secondi...");
                
                // Diamo un paio di secondi per mostrare l'UI poi si riavvia
                Thread.sleep(3000);
                System.exit(0);

            } catch (Exception e) {
                System.err.println("[UpdateService] Errore in aggiornamento: " + e.getMessage());
                e.printStackTrace();
                sendStatus(updateId, "fallito", e.getMessage());
                try { SystemManager.unlockApp(); } catch(Exception ignored){}
            } finally {
                updating = false;
            }
        }, "update-thread").start();
    }

    /**
     * Invia lo stato dell'aggiornamento al backend.
     */
    public static void sendStatus(Long updateId, String stato, String errore) {
        if (updateId == null || updateId <= 0) {
            // Aggiornamento "globale" non legato ad una coda specifica.
            return;
        }
        try {
            JsonObject body = new JsonObject();
            body.addProperty("stato", stato);
            if (errore != null) {
                body.addProperty("errore", errore);
            }
            Api.apiPatch("aggiornamenti/" + updateId + "/stato", body);
            System.out.println("[UpdateService] Stato inviato correttamente: " + stato);
        } catch (Exception e) {
            System.err.println("[UpdateService] Errore comunicando lo stato: " + e.getMessage());
        }
    }

    /**
     * Da controllare all'avvio dell'applicazione. (Es. dentro SplashController)
     * Verifica e segnala i completamenti o i rollback.
     */
    public static void checkPendingStates() {
        // Controllo se questo avvio e' il rollback
        if (Files.exists(ROLLBACK_JSON)) {
            try {
                // Leggiamo il json del pending se presente (per estrarre l'ID)
                Long updateId = null;
                if (Files.exists(PENDING_JSON)) {
                    String jsonStr = Files.readString(PENDING_JSON);
                    JsonObject obj = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                        updateId = obj.get("id").getAsLong();
                    }
                }
                System.out.println("[UpdateService] Riscontrato ROLLBACK pendente! Inoltro stato...");
                sendStatus(updateId, "rollback", "Il nuovo JAR ha causato crash ripetuti post-aggiornamento. Ripristinata la versione vecchia.");
                Files.deleteIfExists(ROLLBACK_JSON);
                Files.deleteIfExists(PENDING_JSON);
                // Lasciamo il file in .bak in caso serva analizzarlo
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        // Controllo se questo e' un avvio post-update riuscito
        else if (Files.exists(PENDING_JSON)) {
            try {
                String jsonStr = Files.readString(PENDING_JSON);
                JsonObject obj = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                Long updateId = null;
                if (obj.has("id") && !obj.get("id").isJsonNull()) {
                    updateId = obj.get("id").getAsLong();
                }
                System.out.println("[UpdateService] Segnalazione COMPLETATO pendente. Inoltro stato...");
                sendStatus(updateId, "completato", null);
                Files.deleteIfExists(PENDING_JSON);
                Files.deleteIfExists(BACKUP_JAR); // Rimuove il backup per risparmiare spazio (o lascialo per futuri imprevisti)
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}