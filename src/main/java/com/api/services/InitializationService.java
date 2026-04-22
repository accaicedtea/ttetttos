package com.api.services;

import com.api.Api;
import com.api.SessionManager;
import com.api.repository.DataRepository;
import com.app.pojo.MenuData;
import com.google.gson.JsonObject;

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
        public Object menu;                // Menu: può essere MenuData o JsonObject
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

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           INIZIO INIZIALIZZAZIONE APP                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // ── STEP 1: Login + Token ─────────────────────────────
        System.out.println("[Init] STEP 1: Autenticazione...");
        try {
            AuthService.loginTotem(apiKey);
            System.out.println("[Init] ✓ Login completato");
            System.out.println("[Init] ✓ Token salvato in SessionManager\n");
            
            // ── Avvia background sync dopo login ──────────────────
            DataRepository.startBackgroundSync();
            
        } catch (Exception e) {
            error = e.getMessage();
            System.err.println("[Init] ✗ Login fallito: " + error);
            System.err.println("[Init] → Fallback a modalità OFFLINE\n");
            
            // Tenta di usare dati in cache
            try {
                menuData = DataRepository.getMenu();
            } catch (Exception ignored) {
            }
            return new InitData(menuData, false, error);
        }

        // ── STEP 2: Check online ──────────────────────────────
        System.out.println("[Init] STEP 2: Verifica connessione...");
        try {
            AuthService.ping();
            isOnline = true;
            System.out.println("[Init] ✓ Server raggiungibile (ONLINE)\n");
        } catch (Exception e) {
            isOnline = false;
            System.out.println("[Init] ✗ Server non raggiungibile (OFFLINE)");
            System.out.println("[Init] → Reason: " + e.getMessage() + "\n");
        }

        // ── STEP 3: Carica TUTTI i dati via DataRepository ────
        System.out.println("[Init] STEP 3: Caricamento dati dal server/cache...");
        if (SessionManager.isLoggedIn()) {
            try {
                System.out.println("[Init]   → Pre-caricamento Menu...");
                MenuData menu = DataRepository.getMenu();
                
                System.out.println("[Init]   → Pre-caricamento Promozioni...");
                DataRepository.getPromotions();
                
                if (menu != null && !menu.isEmpty()) {
                    System.out.println("[Init]   ✓ Menu & Promozioni ricevuti");
                    System.out.println("[Init] ✓ Dati salvati in cache locale (DataRepository)\n");
                    
                    return new InitData(menu, isOnline, null);
                } else {
                    System.err.println("[Init] ✗ Menu vuoto dal server\n");
                    return new InitData(null, isOnline, "Menu vuoto dal server");
                }
            } catch (Exception e) {
                error = e.getMessage();
                System.err.println("[Init] ✗ Errore caricamento dati: " + error);
                System.err.println("[Init] → Fallback a cache locale\n");
                
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
        System.out.println("[Init] ⚠ Non loggato — usando cache\n");
        try {
            MenuData cachedMenu = DataRepository.getMenu();
            return new InitData(cachedMenu, isOnline, "Non autenticato");
        } catch (Exception e) {
            return new InitData(null, isOnline, "Non autenticato e cache non disponibile");
        }
    }
}
