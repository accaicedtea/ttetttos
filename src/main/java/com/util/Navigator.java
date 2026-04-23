
package com.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestisce la navigazione tra le schermate del totem.
 *
 * CACHE: MENU e WELCOME vengono tenuti in memoria dopo il primo caricamento.
 * Tornare al menu non ricrea l'FXML — è istantaneo.
 * CART, PAYMENT, CONFIRM vengono ricreati ogni volta (dati freschi).
 *
 * ANIMAZIONE: slide orizzontale con fade, direzione automatica
 * (avanti/indietro in base all'ordine delle schermate).
 */
public class Navigator {

    public enum Screen {
        SPLASH, PRESENTATION, WELCOME, MENU, CART, PAYMENT, CONFIRM
    }

    // Schermate che vengono tenute in cache (riutilizzate senza ricaricare)
    private static final java.util.Set<Screen> CACHED_SCREENS = java.util.Set.of(
            Screen.WELCOME, Screen.MENU);

    private static final Screen[] ORDER = {
            Screen.SPLASH, Screen.PRESENTATION, Screen.WELCOME, Screen.MENU,
            Screen.CART, Screen.PAYMENT, Screen.CONFIRM
    };

    private static StackPane root;
    private static Screen currentScreen = null;

    // Cache nodi già costruiti
    private static final Map<Screen, Node> nodeCache = new HashMap<>();
    private static final Map<Screen, Object> controllerCache = new HashMap<>();

    public static void init(StackPane rootPane) {
        root = rootPane;
        currentScreen = null;
        nodeCache.clear();
        controllerCache.clear();
    }

    /**
     * Riapplca il tema corrente a tutte le schermate in cache.
     * Utile quando si cambia tema senza ricaricare le schermate.
     */
    public static void refreshTheme() {
        nodeCache.values().forEach(n -> {
            if (n instanceof Parent p)
                ThemeManager.applyTo(p);
        });
    }

    public static void goTo(Screen screen) {
        currentScreen = screen;
        // ...existing code...
        goTo(screen, null);
    }

    public static void goTo(Screen screen, Object data) {
        if (SystemManager.isAppLocked()) {
            ConsoleColors.printWarn("[Nav] Navigazione bloccata: applicazione in lock");
            return;
        }

        currentScreen = screen;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> goTo(screen, data));
            return;
        }

        try {
            Node newNode;
            Object controller;
            boolean fromCache = false;

            if (CACHED_SCREENS.contains(screen) && nodeCache.containsKey(screen)) {
                // ── Schermata in cache: riuso senza ricaricare ────────────
                newNode = nodeCache.get(screen);
                controller = controllerCache.get(screen);
                fromCache = true;
                ConsoleColors.printInfo("[Nav] → " + screen + " (da cache)");
            } else {
                // ── Prima visita: carica l'FXML ───────────────────────────
                String fxml = switch (screen) {
                    case SPLASH -> "/com/app/screens/SplashScreen.fxml";
                    case PRESENTATION -> "/com/app/screens/PresentationScreen.fxml";
                    case WELCOME -> "/com/app/screens/WelcomeScreen.fxml";
                    case MENU -> "/com/app/ShopPage.fxml";
                    case CART -> "/com/app/screens/CartScreen.fxml";
                    case PAYMENT -> "/com/app/screens/PaymentScreen.fxml";
                    case CONFIRM -> "/com/app/screens/ConfirmScreen.fxml";
                };

                var url = Navigator.class.getResource(fxml);
                if (url == null) {
                    System.err.println("[Nav] FXML non trovato: " + fxml);
                    return;
                }

                FXMLLoader loader = new FXMLLoader(url);
                newNode = loader.load();
                controller = loader.getController();

                if (newNode instanceof Parent p)
                    ThemeManager.applyTo(p);

                // Metti in cache se è una schermata cacheable
                if (CACHED_SCREENS.contains(screen)) {
                    nodeCache.put(screen, newNode);
                    controllerCache.put(screen, controller);
                }
                ConsoleColors.printPurple("[Nav] → " + screen + " (nuovo)");
            }

            // Passa dati al controller
            if (controller instanceof DataReceiver dr && data != null) {
                dr.receiveData(data);
            }

            // Notifica il controller del ritorno (se era in cache)
            if (fromCache && controller instanceof ScreenReturnable sr) {
                sr.onReturn();
            }

            boolean forward = isForward(screen);
            currentScreen = screen;
            doTransition(newNode, forward);

        } catch (Exception e) {
            ConsoleColors.printErr("[Nav] Errore → " + screen + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Invalida la cache di una schermata (es. dopo logout). */
    public static void invalidateCache(Screen screen) {
        nodeCache.remove(screen);
        controllerCache.remove(screen);
    }

    /** Invalida tutta la cache. */
    public static void clearCache() {
        nodeCache.clear();
        controllerCache.clear();
    }

    // ── Interfacce per i controller ───────────────────────────────────────────

    /** Riceve dati alla navigazione. */
    public interface DataReceiver {
        void receiveData(Object data);
    }

    /**
     * Callback chiamato quando si TORNA a una schermata già in cache.
     * Utile per aggiornare il badge del carrello, ricaricare dati, ecc.
     */
    public interface ScreenReturnable {
        void onReturn();
    }

    // ── Animazione ────────────────────────────────────────────────────────────

    private static boolean isForward(Screen next) {
        if (currentScreen == null)
            return true;
        int cur = indexOf(currentScreen);
        int nxt = indexOf(next);
        return nxt >= cur;
    }

    private static int indexOf(Screen s) {
        for (int i = 0; i < ORDER.length; i++)
            if (ORDER[i] == s)
                return i;
        return 0;
    }

    private static void doTransition(Node incoming, boolean forward) {

        // Rimuovi schermate extra
        while (root.getChildren().size() > 1)
            root.getChildren().remove(0);

        Node outgoing = root.getChildren().isEmpty() ? null
                : root.getChildren().get(0);

        root.getChildren().remove(incoming); // Evita duplicati
        root.getChildren().add(incoming);

        double w = root.getScene() != null ? root.getScene().getWidth() : 1080;
        if (w <= 0)
            w = 1080;
        final double W = w;

        incoming.setTranslateX(forward ? W : -W);
        incoming.setOpacity(1.0);

        TranslateTransition inT = new TranslateTransition(Duration.millis(280), incoming);
        inT.setFromX(forward ? W : -W);
        inT.setToX(0);
        inT.setInterpolator(Interpolator.EASE_BOTH);

        inT.setOnFinished(e -> root.setMouseTransparent(false));

        root.setMouseTransparent(true);
        if (outgoing != null) {
            final Node old = outgoing;
            TranslateTransition outT = new TranslateTransition(Duration.millis(280), old);
            outT.setToX(forward ? -W * 0.25 : W * 0.25);
            outT.setInterpolator(Interpolator.EASE_IN);
            FadeTransition outF = new FadeTransition(Duration.millis(280), old);
            outF.setToValue(0);
            ParallelTransition outAnim = new ParallelTransition(outT, outF);

            // Se la schermata uscente è in cache, NON rimuoverla dal DOM
            // ma solo dalla scena visiva (verrà riaggiunta al prossimo goTo)
            outAnim.setOnFinished(e -> {
                root.getChildren().remove(old);
                // Ripristina la posizione per il prossimo utilizzo dalla cache
                old.setTranslateX(0);
                old.setOpacity(1.0);
            });
            outAnim.play();
        }

        inT.play();
    }

    public static Screen getCurrentScreen() {
        return currentScreen;
    }
}
