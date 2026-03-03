package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {

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

        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                event.consume();
            }
        });

        stage.show();
    }
}
