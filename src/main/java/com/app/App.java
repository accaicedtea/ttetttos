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

    private javafx.scene.control.Label memoryStatsLabel;
    private javafx.animation.Timeline memoryStatsTicker;
    private boolean memoryStatsVisible = false;

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

        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            () -> toggleMemoryStatsOverlay(rootPane)
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

    private void toggleMemoryStatsOverlay(StackPane root) {
        if (memoryStatsVisible) {
            hideMemoryStatsOverlay(root);
        } else {
            showMemoryStatsOverlay(root);
        }
    }

    private void showMemoryStatsOverlay(StackPane root) {
        if (memoryStatsLabel == null) {
            memoryStatsLabel = new javafx.scene.control.Label();
            memoryStatsLabel.setStyle("-fx-background-color: rgba(0,0,0,0.84); -fx-text-fill: white; -fx-padding: 10; -fx-font-size: 12px; -fx-font-family: 'Segoe UI', 'Arial'; -fx-border-radius: 6; -fx-background-radius: 6;");
            memoryStatsLabel.setPrefWidth(310);
            memoryStatsLabel.setWrapText(true);
            StackPane.setAlignment(memoryStatsLabel, javafx.geometry.Pos.TOP_RIGHT);
            StackPane.setMargin(memoryStatsLabel, new javafx.geometry.Insets(10, 10, 0, 0));
            memoryStatsLabel.setMouseTransparent(true);
        }

        if (!root.getChildren().contains(memoryStatsLabel)) {
            root.getChildren().add(memoryStatsLabel);
        }

        memoryStatsVisible = true;

        if (memoryStatsTicker != null) {
            memoryStatsTicker.stop();
        }
        memoryStatsTicker = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), e -> updateMemoryStats(root)));
        memoryStatsTicker.setCycleCount(javafx.animation.Animation.INDEFINITE);
        memoryStatsTicker.play();

        updateMemoryStats(root);
    }

    private void hideMemoryStatsOverlay(StackPane root) {
        memoryStatsVisible = false;
        if (memoryStatsTicker != null) {
            memoryStatsTicker.stop();
            memoryStatsTicker = null;
        }
        if (memoryStatsLabel != null) {
            root.getChildren().remove(memoryStatsLabel);
        }
    }

    private void updateMemoryStats(StackPane root) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long jvmUsed = runtime.totalMemory() - runtime.freeMemory();
            long jvmTotal = runtime.totalMemory();
            long jvmMax = runtime.maxMemory();

            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long sysTotal = -1;
            long sysFree = -1;
            try {
                java.lang.reflect.Method getTotal = osBean.getClass().getMethod("getTotalPhysicalMemorySize");
                java.lang.reflect.Method getFree = osBean.getClass().getMethod("getFreePhysicalMemorySize");
                sysTotal = ((Number) getTotal.invoke(osBean)).longValue();
                sysFree = ((Number) getFree.invoke(osBean)).longValue();
            } catch (Exception e) {
                // fallback
            }

            int threadCount = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
            int daemonCount = java.lang.management.ManagementFactory.getThreadMXBean().getDaemonThreadCount();

            String text = String.format(
                    "APP JVM: usata %s / %s (max %s)\n" +
                            "SISTEMA: %s / %s liberi\n" +
                            "THREAD: tot %d, daemon %d",
                    humanReadableBytes(jvmUsed), humanReadableBytes(jvmTotal), humanReadableBytes(jvmMax),
                    sysFree >= 0 ? humanReadableBytes(sysFree) : "n/a",
                    sysTotal >= 0 ? humanReadableBytes(sysTotal) : "n/a",
                    threadCount, daemonCount
            );

            memoryStatsLabel.setText(text);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String humanReadableBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
