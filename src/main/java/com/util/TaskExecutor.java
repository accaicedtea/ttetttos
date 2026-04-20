package com.util;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 * TaskExecutor
 * ============================================================================
 * Gestore centralizzato per i Thread in background dell'applicazione.
 * EVITA di creare 'new Thread()' o 'Executors.newFixedThreadPool()' 
 * sparsi nei controller locali.
 * ============================================================================
 */
public class TaskExecutor {

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * Esegue un task asincrono nel pool di thread in background.
     * @param task Il Runnable da eseguire.
     */
    public static void execute(Runnable task) {
        executor.submit(task);
    }

    /**
     * Helper per eseguire del codice obbligatoriamente sul JavaFX Application Thread.
     * @param task Il Runnable da eseguire nella UI.
     */
    public static void runOnUI(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }
    
    /** Chiusura sicura (da chiamare nell'App.stop()) */
    public static void shutdown() {
        executor.shutdownNow();
    }
}
