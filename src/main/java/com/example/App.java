package com.example;

import com.util.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.*;

/**
 * TouchUI — Touch-optimized JavaFX component showcase.
 */
public class App extends Application {

    private static final String VERSION = "1.0.0";
    private static final String APP_TITLE = "TouchUI — Component Showcase";
    private static final String BANNER =
        "\n" +
        "    ╔═══════════════════════════════════════════╗\n" +
        "    ║         👆 TouchUI v" + VERSION + "                 ║\n" +
        "    ║   Touch-Optimized Component Showcase      ║\n" +
        "    ╚═══════════════════════════════════════════╝\n";

    public static void main(String[] args) {
        Set<String> flags = new HashSet<>(Arrays.asList(args));
        if (flags.contains("--help") || flags.contains("-h")) {
            System.out.println(BANNER);
            System.out.println("  Usage: java App [options]");
            System.out.println("    (no args)    Launch the GUI showcase");
            System.out.println("    --version    Show version");
            System.out.println("    --help       Show this help");
            return;
        }
        if (flags.contains("--version")) {
            System.out.println("TouchUI v" + VERSION);
            return;
        }
        System.out.println(BANNER);
        Log.info("Avvio TouchUI...");
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Platform.setImplicitExit(false);
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/com/example/ShopPage.fxml")
        );
        stage.initStyle(StageStyle.UNDECORATED);
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(
            getClass().getResource("/com/example/styles/dark-theme.css").toExternalForm()
        );
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);

        // Imposta dimensioni esplicite dalla schermata fisica
        // (necessario con Cage che non le passa automaticamente a JavaFX)
        Rectangle2D screen = Screen.getPrimary().getBounds();
        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setWidth(screen.getWidth());
        stage.setHeight(screen.getHeight());

        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // Blocca solo ESC; Alt+F4 e Ctrl+C rimangono funzionanti
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                event.consume();
            }
        });

        stage.show();
        Log.info("TouchUI avviato in modalità kiosk.");
    }
}