package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entrypoint dell'app.
 *
 * All'avvio mostra SPLASH (che gestisce login, menu, traduzioni),
 * poi naviga automaticamente a WELCOME.
 * Login, cache menu e traduzioni sono tutti gestiti da SplashController.
 */
public class App extends Application {

    private static final Path STOP_FLAG = Path.of("/opt/kiosk/.stop");

    public static StackPane rootPane;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) throws Exception {

        rootPane = new StackPane();

        Scene scene = new Scene(rootPane, Color.BLACK);
        ThemeManager.init(scene);
        Navigator.init(rootPane);

        // Prima schermata: SPLASH (gestisce tutto il setup)
        Navigator.goTo(Navigator.Screen.SPLASH);

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);

        Rectangle2D screen = Screen.getPrimary().getBounds();
        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setWidth(screen.getWidth());
        stage.setHeight(screen.getHeight());
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        scene.setCursor(Cursor.NONE);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.H
                    && event.isControlDown() && event.isAltDown()) {
                try { Files.createFile(STOP_FLAG); } catch (Exception ignored) {}
                Platform.exit();
                return;
            }
            if (event.getCode() == KeyCode.T
                    && event.isControlDown() && event.isAltDown()) {
                ThemeManager.toggle();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) event.consume();
        });

        Platform.setImplicitExit(false);
        stage.show();
    }
}
