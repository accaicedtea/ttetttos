package com.util;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class ThemeManager {

    public enum Theme { DARK, LIGHT }

    private static Theme   current = Theme.DARK;
    private static Scene   scene;

    private static final String DARK_CSS  = "/com/example/styles/dark-theme.css";
    private static final String LIGHT_CSS = "/com/example/styles/light-theme.css";

    public static void init(Scene s) {
        scene = s;
        apply(current);
    }

    public static void toggle() {
        set(current == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    public static void set(Theme theme) {
        current = theme;
        if (Platform.isFxApplicationThread()) {
            apply(theme);
            Navigator.refreshTheme();
        } else {
            Platform.runLater(() -> {
                apply(theme);
                Navigator.refreshTheme();
            });
        }
    }

    public static Theme   getCurrent()    { return current; }
    public static boolean isDark()        { return current == Theme.DARK; }
    public static String  getToggleIcon() { return current == Theme.DARK ? "☀" : "🌙"; }

    /** Applica il tema a un nodo Parent (es. nuovi screen caricati da FXML). */
    public static void applyTo(Parent p) {
        p.getStylesheets().clear();
        var url = ThemeManager.class.getResource(
                current == Theme.DARK ? DARK_CSS : LIGHT_CSS);
        if (url != null) p.getStylesheets().add(url.toExternalForm());
    }

    private static void apply(Theme theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        var url = ThemeManager.class.getResource(
                theme == Theme.DARK ? DARK_CSS : LIGHT_CSS);
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }
}
