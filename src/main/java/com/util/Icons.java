package com.util;

import org.kordamp.ikonli.javafx.FontIcon;
/**
 * Factory centralizzata per le icone Ikonli (Material Design 2).
 *
 * Uso:
 *   Node icon = Icons.get(Icons.CART, 28);
 *   label.setGraphic(Icons.get(Icons.CHECK, 40));
 *
 * Tutti i nomi MDI2 sono nell'enum MaterialDesign2 del pack scaricato da Maven.
 * Riferimento icone: https://pictogrammers.com/library/mdi/
 */
public class Icons {

    // ── Identificatori icone usate nell'app ───────────────────────────

    // Navigazione
    public static final String BACK         = "mdi2a-arrow-left";
    public static final String CLOSE        = "mdi2c-close";
    public static final String FORWARD      = "mdi2a-arrow-right";

    // Menu / Prodotti
    public static final String MENU         = "mdi2f-food-fork-drink";
    public static final String FOOD         = "mdi2f-food";
    public static final String CART         = "mdi2c-cart";
    public static final String CART_PLUS    = "mdi2c-cart-plus";
    public static final String CART_CHECK   = "mdi2c-cart-check";

    // Pagamento
    public static final String CASH         = "mdi2c-cash";
    public static final String CARD         = "mdi2c-credit-card";
    public static final String RECEIPT      = "mdi2r-receipt";

    // Status / Feedback
    public static final String CHECK        = "mdi2c-check-circle";
    public static final String CHECK_BOLD   = "mdi2c-check-bold";
    public static final String ALERT        = "mdi2a-alert";
    public static final String ALERT_CIRCLE = "mdi2a-alert-circle";
    public static final String INFO         = "mdi2i-information";
    public static final String SUCCESS      = "mdi2c-check-decagram";

    // Connettività
    public static final String WIFI         = "mdi2w-wifi";
    public static final String WIFI_OFF     = "mdi2w-wifi";
    public static final String SIGNAL       = "mdi2s-signal";

    // Sistema
    public static final String SETTINGS     = "mdi2c-cog";
    public static final String LOADING      = "mdi2l-loading";
    public static final String REFRESH      = "mdi2r-refresh";
    public static final String BATTERY      = "mdi2b-battery-high";
    public static final String CLOCK        = "mdi2c-clock-outline";
    public static final String CALENDAR     = "mdi2c-calendar";

    // Tema
    public static final String MOON         = "mdi2w-weather-night";
    public static final String SUN          = "mdi2w-weather-sunny";

    // Splash steps
    public static final String LOCK         = "mdi2l-lock";
    public static final String LOCK_OPEN    = "mdi2l-lock-open";
    public static final String CLIPBOARD    = "mdi2c-clipboard-list";
    public static final String TRANSLATE    = "mdi2t-translate";
    public static final String ROBOT        = "mdi2r-robot";
    public static final String TIMER        = "mdi2t-timer-sand";

    // Allergen
    public static final String ALLERGEN     = "mdi2a-alert-rhombus";

    // Lingue (bandiere non disponibili in MDI — usiamo icone generiche)
    public static final String LANGUAGE     = "mdi2t-translate";
    public static final String GLOBE        = "mdi2e-earth";

    // ── Factory ────────────────────────────────────────────────────────

    /**
     * Crea un FontIcon con l'icona richiesta.
     *
     * @param iconCode codice MDI2 (es. "mdi2c-cart")
     * @param size     dimensione in pixel
     * @return FontIcon pronto all'uso come graphic di qualsiasi Node
     */
    public static FontIcon get(String iconCode, int size) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * FontIcon con colore CSS esplicito.
     *
     * @param iconCode codice MDI2
     * @param size     dimensione
     * @param color    colore CSS (es. "#5b9cf5", "white", "rgba(255,100,0,0.8)")
     */
    public static FontIcon get(String iconCode, int size, String color) {
        FontIcon icon = get(iconCode, size);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        return icon;
    }

    /**
     * FontIcon con style class CSS (per tematizzazione automatica).
     * Il colore viene ereditato dal CSS dell'app.
     */
    public static FontIcon styled(String iconCode, int size, String... styleClasses) {
        FontIcon icon = get(iconCode, size);
        icon.getStyleClass().addAll(styleClasses);
        return icon;
    }
}
