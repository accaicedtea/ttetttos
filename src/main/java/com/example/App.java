package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Files;
import java.nio.file.Path;

public class App extends Application {

    /** File la cui presenza segnala uscita intenzionale al loop di restart. */
    private static final Path STOP_FLAG = Path.of("/opt/kiosk/.stop");

    public static void main(String[] args) {
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
            // Ctrl+Alt+H — uscita intenzionale (non causa restart)
            if (event.getCode() == KeyCode.H
                    && event.isControlDown()
                    && event.isAltDown()) {
                try { Files.createFile(STOP_FLAG); } catch (Exception ignored) {}
                Platform.exit();
                return;
            }
            // Blocca ESC
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
        });

        stage.show();
    }
}
