package com.api.services;

import java.io.Console;

import com.api.Api;
import com.api.SessionManager;
import com.api.repository.DataRepository;
import com.app.pojo.MenuData;
import com.app.pojo.Promotion;
import com.google.gson.JsonObject;
import com.util.ConsoleColors;

/**
 * InitializationService — Consolida TUTTO l'init in una sola sequenza.
 * 
 * FLUSSO:
 * 1) Login + salva token
 * 2) Check online
 * 3) Carica TUTTI i dati (menu+categorie+ingredienti) in UNA sola chiamata API
 * 4) Salva localmente
 * 5) Torna i dati per la navigazione
 */
public class InitializationService {

    public static class InitData {
        public Object menu; // Menu: può essere MenuData o JsonObject
        public Object promotions; // Promozioni: può essere Promotion o JsonObject
        public boolean isOnline;
        public String error;

        public InitData(Object menu, boolean isOnline, String error) {
            this.menu = menu;
            this.isOnline = isOnline;
            this.error = error;
        }
    }

    /**
     * Esegue TUTTO l'init in una sola sequenza.
     * Ritorna InitData con menu, stato online, e errore se present.
     */
    public static InitData initializeApp(String apiKey) {
        boolean isOnline = true;
        String error = null;
        MenuData menuData = null;

        ConsoleColors.sectionStart("INIZIO INIZIALIZZAZIONE APP");
        // ── STEP 1: Login + Token ─────────────────────────────
        ConsoleColors.section();
        ConsoleColors.printInfo("[Init] STEP 1: Autenticazione...");
        try {
            AuthService.loginTotem(apiKey);
            ConsoleColors.printSuccess("[Init] Login completato");
            ConsoleColors.printSuccess("[Init] Token salvato in SessionManager\n");

            // ── Avvia background sync dopo login ──────────────────
            DataRepository.startBackgroundSync();

        } catch (Exception e) {
            error = e.getMessage();
            ConsoleColors.printErr("[Init] Login fallito: " + error);
            ConsoleColors.printErr("[Init] Fallback a modalità OFFLINE\n");

            // Tenta di usare dati in cache
            try {
                menuData = DataRepository.getMenu();
            } catch (Exception ignored) {
            }
            return new InitData(menuData, false, error);
        }

        // ── STEP 2: Check online ──────────────────────────────
        ConsoleColors.section();
        ConsoleColors.printInfo("[Init] STEP 2: Verifica connessione...");
        try {
            AuthService.ping();
            isOnline = true;
            ConsoleColors.printSuccess("[Init] Server raggiungibile (ONLINE)\n");
        } catch (Exception e) {
            isOnline = false;
            ConsoleColors.printErr("[Init] Server 'rraggiungibile (OFFLINE)");
            ConsoleColors.printErr("[Init] Reason: " + e.getMessage() + "\n");
        }

        // ── STEP 3: Carica TUTTI i dati via DataRepository ────
        ConsoleColors.section();
        ConsoleColors.printInfo("[Init] STEP 3: Caricamento dati dal server/cache...");
        if (SessionManager.isLoggedIn()) {
            try {
                ConsoleColors.printInfo("[Init] Pre-caricamento Menu...");
                MenuData menu = DataRepository.getMenu();

                ConsoleColors.printInfo("[Init] Pre-caricamento Promozioni...");
                DataRepository.getPromotions();

                if (menu != null && !menu.isEmpty()) {
                    ConsoleColors.printSuccess("[Init] Menu & Promozioni ricevuti");
                    ConsoleColors.printSuccess("[Init] Dati salvati in cache locale (DataRepository)\n");

                    return new InitData(menu, isOnline, null);
                } else {
                    ConsoleColors.printErr("[Init] Menu vuoto dal server\n");
                    return new InitData(null, isOnline, "Menu vuoto dal server");
                }

            } catch (Exception e) {
                error = e.getMessage();
                ConsoleColors.printErr("[Init] Errore caricamento dati: " + error);
                ConsoleColors.printErr("[Init] Fallback a cache locale\n");

                // Fallback a cache
                try {
                    MenuData cachedMenu = DataRepository.getMenu();
                    return new InitData(cachedMenu, isOnline, error);
                } catch (Exception cacheErr) {
                    return new InitData(null, isOnline, error);
                }
            }
        }

        // Se non loggato, usa cache
        ConsoleColors.printWarn("[Init] Non loggato — usando cache\n");
        try {
            MenuData cachedMenu = DataRepository.getMenu();
            return new InitData(cachedMenu, isOnline, "Non autenticato");
        } catch (Exception e) {
            return new InitData(null, isOnline, "Non autenticato e cache non disponibile");
        }
    }
}
