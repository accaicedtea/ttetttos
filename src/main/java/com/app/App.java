package com.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.util.Navigator;
import com.util.ThemeManager;

/**
 * Punto di ingresso dell'applicazione kiosk.
 * INVARIATO rispetto all'originale — nessuna modifica.
 */
public class App extends Application {

    /** Pane radice condiviso — usato da ConfirmModal e BaseController. */
    public static StackPane rootPane;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/app/screens/SplashScreen.fxml"));
        rootPane = loader.load();

        Scene scene = new Scene(rootPane);
        ThemeManager.init(scene);
        ThemeManager.set(ThemeManager.Theme.DARK);

        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                Platform.exit();
                System.exit(0);
            }
        );

        stage.setScene(scene);
        stage.setTitle("TotemOrder");
        stage.initStyle(StageStyle.UNDECORATED); // rimuove chiudi/riduci/espandi
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        stage.show();

        Navigator.init(rootPane);
    }

    @Override
    public void stop() {
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
