package com.util;

import com.api.Api;
import com.google.gson.JsonObject;
import javafx.application.Platform;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Watchdog di rete + heartbeat al server.
 *
 * Ogni INTERVAL_MS secondi:
 * 1. Raccoglie metriche di sistema (CPU, RAM, disco, OS, IP)
 * 2. POST /auth/ping con il corpo JSON (struttura tabella totem_heartbeat)
 * 3. Notifica il listener se lo stato online/offline cambia
 *
 * Campi inviati:
 * ip_pubblico, app_versione, java_versione, os_info,
 * disco_libero_mb, ram_libera_mb, cpu_percent,
 * internet_ok, ordini_in_coda
 */
public class NetworkWatchdog {

    public static final String APP_VERSION = "1.0";

    // ===== CONFIGURAZIONE PARAMETRICA E DEBUG =====
    public static boolean DEBUG_SIMULATE_UPDATE = false;
    public static boolean DEBUG_SIMULATE_OFFLINE = false;
    public static int MAX_OFFLINE_DAYS = 30;
    // ==============================================

    private static final int INTERVAL_ONLINE_MS = 15_000;
    private static final int INTERVAL_OFFLINE_MS = 5_000;
    private static final int PING_TIMEOUT_MS = 6_000;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(PING_TIMEOUT_MS))
            .build();

    private static Thread thread = null;
    private static volatile boolean running = false;
    private static volatile boolean online = false;
    
    // Sostituito Consumer con BooleanProperty per permettere binding multipli
    public static final javafx.beans.property.BooleanProperty onlineProperty = new javafx.beans.property.SimpleBooleanProperty(true);

    // -- API pubblica --

    public static synchronized void start(Consumer<Boolean> onStatusChange) {
        if (running)
            return;
        running = true;

        if (onStatusChange != null) {
            onlineProperty.addListener((obs, oldV, newV) -> onStatusChange.accept(newV));
        }

        // Esegui un primo ping subito, così lo stato è aggiornato all'avvio.
        online = sendPing();
        Platform.runLater(() -> onlineProperty.set(online));
        
        System.out.println("[Watchdog] Avviato. Stato iniziale: " + (online ? "ONLINE" : "OFFLINE"));

        thread = new Thread(() -> {
            while (running) {
                boolean wasOnline = online;
                boolean isOnline = sendPing();
                online = isOnline;

                if (isOnline != wasOnline) {
                    System.out.println("[Watchdog] -> " + (isOnline ? "ONLINE" : "OFFLINE"));
                    final boolean onlineFinal = isOnline;
                    Platform.runLater(() -> onlineProperty.set(onlineFinal));
                }

                try {
                    Thread.sleep(isOnline ? INTERVAL_ONLINE_MS : INTERVAL_OFFLINE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[Watchdog] Fermato.");
        }, "network-watchdog");

        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public static boolean isOnline() {
        return onlineProperty.get();
    }

    // -- Ping + heartbeat --

    /**
     * Invia il ping al server con le metriche di sistema.
     * Ritorna true se il server risponde, false altrimenti.
     */
    private static boolean sendPing() {
        if (DEBUG_SIMULATE_OFFLINE) {
            checkOfflineLock(false, null);
            return false;
        }

        try {
            JsonObject payload = buildHeartbeat();
            
            // Simula un update se il parametro di debug è attivo
            if (DEBUG_SIMULATE_UPDATE) {
                JsonObject debugData = new JsonObject();
                debugData.addProperty("versione_a", "1.1-DEBUG");
                debugData.addProperty("aggiornamento_id", 9999L);
                
                JsonObject debugResponse = new JsonObject();
                debugResponse.add("data", debugData);
                
                System.out.println("[Watchdog] Trovato aggiornamento (simulato)! Versione target: 1.1-DEBUG");
                SystemManager.showUpdatePrompt("1.1-DEBUG", 9999L);
                return true;
            }
            
            // Invia dati al server con Authorization: Bearer (non public)
            JsonObject response = com.api.Api.apiPost("auth/ping", payload);
            
            // Aggiorna e resetta il timer del blocco offline
            checkOfflineLock(true, response);


            return true;
        } catch (Exception e) {
            System.err.println("[Watchdog] Errore API: " + e.getMessage());
            checkOfflineLock(false, null);
            return false;
        }
    }

    /**
     * Usa le java.util.prefs persistenti su disco per resistere ai riavvii
     * e valutare se il Kiosk è rimasto disconnesso senza permessi per un tot di giorni.
     */
    private static void checkOfflineLock(boolean isOnlineNow, JsonObject serverResponse) {
        Preferences prefs = Preferences.userNodeForPackage(NetworkWatchdog.class);
        long nowMs = System.currentTimeMillis();
        
        if (isOnlineNow) {
            // Aggiorna e salva permanentemente il timestamp
            prefs.putLong("last_online_timestamp", nowMs);
            
            // Reagisce attivamente in base al database remoto
            boolean lockedByDb = serverResponse != null && serverResponse.has("is_locked") && serverResponse.get("is_locked").getAsBoolean();
            if (lockedByDb) {
                String lockMessage = serverResponse.has("lock_message") ? serverResponse.get("lock_message").getAsString() : "Totem sospeso dal server.";
                Platform.runLater(() -> SystemManager.lockApp(lockMessage));
            } else {
                // Se il db lo dà come sbloccato e non è offline, rimuoviamo un eventuale blocco pregresso
                Platform.runLater(() -> SystemManager.unlockApp());
            }

        } else {
            // Se offline, calcola da quanto tempo
            long lastOnlineMs = prefs.getLong("last_online_timestamp", nowMs);
            
            // Caso in cui non fosse ma stato online da quando installato
            if (lastOnlineMs == nowMs && !prefs.getBoolean("has_initialized", false)) {
                prefs.putLong("last_online_timestamp", nowMs);
                prefs.putBoolean("has_initialized", true);
            }
            
            long daysOffline = TimeUnit.MILLISECONDS.toDays(nowMs - lastOnlineMs);
            if (daysOffline > MAX_OFFLINE_DAYS) {
                System.out.println("[Watchdog] Kiosk offline da " + daysOffline + " gg. Innesco blocco di sicurezza.");
                Platform.runLater(() -> SystemManager.lockApp("Dispositivo bloccato per sicurezza.\nRimasto offline per più di " + MAX_OFFLINE_DAYS + " giorni consecutivi."));
            }
        }
    }

    private static boolean isNetworkReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), PING_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ping HTTP grezzo senza autenticazione (fallback se non loggati). */
    private static boolean rawPing() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://hasanabdelaziz.altervista.org/api/v1/totem/auth/ping"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofMillis(PING_TIMEOUT_MS))
                    .build();
            HttpResponse<Void> resp = CLIENT.send(req,
                    HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // -- Raccolta metriche di sistema --
    private static JsonObject buildHeartbeat() {
        JsonObject j = new JsonObject();

        // app_versione, java_versione, os_info
        j.addProperty("app_versione", APP_VERSION);
        j.addProperty("java_versione", System.getProperty("java.version", "?"));
        j.addProperty("os_info", buildOsInfo());

        // ip_pubblico (meglio lasciare che il server lo ricavi dall'header)
        // ma proviamo a rilevarlo localmente come fallback
        j.addProperty("ip_pubblico", getLocalIp());

        // RAM libera in MB
        j.addProperty("ram_libera_mb", getFreeRamMb());

        // Disco libero in MB (partizione root o drive principale)
        j.addProperty("disco_libero_mb", getFreeDiscMb());

        // CPU % (media ultimo minuto via OperatingSystemMXBean)
        j.addProperty("cpu_percent", getCpuPercent());

        // internet_ok = 1 (se siamo qui, la rete funziona)
        j.addProperty("internet_ok", 1);

        // Temperatura CPU in ?C
        j.addProperty("temperatura_cpu", getCpuTemperature());

        // ordini_in_coda: per ora 0 (non abbiamo coda locale implementata)
        j.addProperty("ordini_in_coda", 0);

        return j;
    }

    private static String buildOsInfo() {
        String name = System.getProperty("os.name", "?");
        String version = System.getProperty("os.version", "?");
        String arch = System.getProperty("os.arch", "?");
        return name + " " + version + " (" + arch + ")";
    }

    private static String getLocalIp() {
        try {
            // Prova a ricavare l'IP locale aprendo un socket verso un DNS pubblico
            var socket = new java.net.DatagramSocket();
            socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            String ip = socket.getLocalAddress().getHostAddress();
            socket.close();
            return ip != null ? ip : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static int getFreeRamMb() {
        try {
            // Prima scelta: OperatingSystemMXBean (RAM di sistema reale)
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                long free = sunOs.getFreeMemorySize();
                if (free > 0)
                    return (int) (free / (1024 * 1024));
            }
            // Seconda scelta: leggi /proc/meminfo (Linux)
            int memAvail = readProcMeminfo();
            if (memAvail > 0)
                return memAvail;
            // Fallback: Runtime JVM heap (meno accurato)
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory() + (rt.maxMemory() - rt.totalMemory());
            return (int) (free / (1024 * 1024));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Legge MemAvailable da /proc/meminfo (Linux).
     * MemAvailable e la stima piu accurata della RAM realmente disponibile.
     */
    private static int readProcMeminfo() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("/proc/meminfo");
            if (!java.nio.file.Files.exists(p))
                return 0;
            for (String line : java.nio.file.Files.readAllLines(p)) {
                if (line.startsWith("MemAvailable:")) {
                    // Formato: "MemAvailable: 1234567 kB"
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long kb = Long.parseLong(parts[1].trim());
                        return (int) (kb / 1024); // converti kB -> MB
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int getFreeDiscMb() {
        try {
            long maxUsable = 0;
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                // Salta filesystem virtuali: tmpfs, devtmpfs, proc, sys, cgroup, ecc.
                String type = store.type();
                if (type == null)
                    continue;
                switch (type.toLowerCase()) {
                    case "tmpfs", "devtmpfs", "proc", "sysfs", "cgroup",
                            "cgroup2", "devpts", "securityfs", "pstore",
                            "bpf", "tracefs", "hugetlbfs", "mqueue",
                            "debugfs", "configfs", "fusectl", "overlay",
                            "nsfs", "ramfs", "autofs" -> {
                        continue;
                    }
                }
                // Prendi il filesystem con piu spazio libero (il disco principale)
                long usable = store.getUsableSpace();
                if (usable > maxUsable)
                    maxUsable = usable;
            }
            return (int) (maxUsable / (1024 * 1024));
        } catch (Exception e) {
            // Fallback: spazio libero su path corrente
            try {
                return (int) (new java.io.File("/").getUsableSpace() / (1024 * 1024));
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    private static int getCpuPercent() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                double load = sunOs.getCpuLoad();
                if (load >= 0)
                    return (int) Math.round(load * 100);
            }
            // Fallback: system load average / core count
            double load = os.getSystemLoadAverage();
            int cores = os.getAvailableProcessors();
            if (load >= 0 && cores > 0)
                return (int) Math.min(100, Math.round(load / cores * 100));
        } catch (Exception e) {
            // ignora
        }
        return 0;
    }

    /**
     * Legge la temperatura CPU in gradi Celsius.
     *
     * Strategia (in ordine di priorit?):
     * 1. /sys/class/thermal/thermal_zonetemp - Linux standard (Raspberry Pi, SBC,
     * laptop)
     * 2. /sys/class/hwmon/hwmon temp*_input - hwmon (Intel, AMD desktop)
     * 3. Comando sensors command (lm-sensors) - fallback universale
     * 4. 0.0 se nulla funziona
     */
    private static double getCpuTemperature() {
        // Strategia 1: thermal_zone (comune su ARM, laptop, Pi)
        double t = readThermalZone();
        if (t > 0)
            return t;

        // Strategia 2: hwmon (PC desktop/server)
        t = readHwmon();
        if (t > 0)
            return t;

        // Strategia 3: lm-sensors (richiede package installato)
        t = readSensors();
        if (t > 0)
            return t;

        return 0.0;
    }

    /** Legge da /sys/class/thermal/thermal_zone temp (valore in milligradi). */
    private static double readThermalZone() {
        try {
            java.nio.file.Path base = java.nio.file.Path.of("/sys/class/thermal");
            if (!java.nio.file.Files.exists(base))
                return 0;

            double maxTemp = 0;
            try (var stream = java.nio.file.Files.list(base)) {
                for (java.nio.file.Path zone : stream.toList()) {
                    // Leggi solo zone di tipo CPU (x86_pkg_temp, cpu-thermal, ecc.)
                    java.nio.file.Path typePath = zone.resolve("type");
                    java.nio.file.Path tempPath = zone.resolve("temp");
                    if (!java.nio.file.Files.exists(tempPath))
                        continue;

                    String type = "";
                    if (java.nio.file.Files.exists(typePath)) {
                        type = java.nio.file.Files.readString(typePath).strip().toLowerCase();
                    }

                    // Includi zone CPU/package, escludi GPU e batteria
                    boolean isCpu = type.isEmpty()
                            || type.contains("cpu")
                            || type.contains("x86_pkg")
                            || type.contains("pkg")
                            || type.contains("core")
                            || type.contains("soc")
                            || type.contains("thermal");

                    if (!isCpu)
                        continue;

                    String raw = java.nio.file.Files.readString(tempPath).strip();
                    double milliC = Double.parseDouble(raw);
                    // I valori > 1000 sono in milligradi
                    double celsius = milliC > 1000 ? milliC / 1000.0 : milliC;
                    if (celsius > maxTemp && celsius < 120)
                        maxTemp = celsius;
                }
            }
            return maxTemp;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Legge da /sys/class/hwmon/hwmon temp*_input (Intel/AMD). */
    private static double readHwmon() {
        try {
            java.nio.file.Path base = java.nio.file.Path.of("/sys/class/hwmon");
            if (!java.nio.file.Files.exists(base))
                return 0;

            double maxTemp = 0;
            try (var hwmonStream = java.nio.file.Files.list(base)) {
                for (java.nio.file.Path hwmon : hwmonStream.toList()) {
                    // Controlla il nome del dispositivo (coretemp, k10temp, ecc.)
                    java.nio.file.Path namePath = hwmon.resolve("name");
                    if (!java.nio.file.Files.exists(namePath))
                        continue;
                    String name = java.nio.file.Files.readString(namePath).strip().toLowerCase();

                    // Solo sensori CPU
                    if (!name.contains("coretemp") && !name.contains("k10temp")
                            && !name.contains("zenpower") && !name.contains("cpu")
                            && !name.contains("acpi"))
                        continue;

                    // Leggi tutti i file temp*_input in questa directory
                    try (var tempStream = java.nio.file.Files.list(hwmon)) {
                        for (java.nio.file.Path f : tempStream.toList()) {
                            String fname = f.getFileName().toString();
                            if (!fname.startsWith("temp") || !fname.endsWith("_input"))
                                continue;

                            String raw = java.nio.file.Files.readString(f).strip();
                            double milliC = Double.parseDouble(raw);
                            double celsius = milliC > 1000 ? milliC / 1000.0 : milliC;
                            if (celsius > maxTemp && celsius < 120)
                                maxTemp = celsius;
                        }
                    }
                }
            }
            return maxTemp;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Legge la temperatura tramite il comando `sensors` (lm-sensors). */
    private static double readSensors() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sensors", "-u");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                output = reader.lines()
                        .collect(java.util.stream.Collectors.joining("\n"));
            }

            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

            // Cerca righe come " temp1_input: 45.000"
            double maxTemp = 0;
            for (String line : output.split("\n")) {
                line = line.trim();
                if (!line.contains("temp") || !line.contains("_input"))
                    continue;
                String[] parts = line.split(":");
                if (parts.length < 2)
                    continue;
                try {
                    double t = Double.parseDouble(parts[1].trim());
                    if (t > maxTemp && t < 120)
                        maxTemp = t;
                } catch (NumberFormatException ignored) {
                }
            }
            return maxTemp;
        } catch (Exception e) {
            return 0; // sensors non installato o non disponibile
        }
    }

}
