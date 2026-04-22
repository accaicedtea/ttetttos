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

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import com.util.Navigator;
import com.util.ThemeManager;

/**
 * Punto di ingresso dell'applicazione kiosk.
 * INVARIATO rispetto all'originale — nessuna modifica.
 */
public class App extends Application {

    /** Pane radice condiviso — usato da ConfirmModal e BaseController. */
    public static StackPane rootPane;
    public static com.app.components.ToastOverlay globalToast;

    private javafx.scene.control.Label memoryStatsLabel;
    private javafx.animation.Timeline memoryStatsTicker;
    private boolean memoryStatsVisible = false;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/app/screens/SplashScreen.fxml"));
        rootPane = loader.load();

        StackPane mainRoot = new StackPane(rootPane);

        globalToast = new com.app.components.ToastOverlay();
        mainRoot.getChildren().add(globalToast);

        Scene scene = new Scene(mainRoot);
        ThemeManager.init(scene);

        // Select theme
        ThemeManager.set(ThemeManager.Theme.LIGHT);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                () -> {
                    Platform.exit();
                    System.exit(0);
                });

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                () -> toggleMemoryStatsOverlay(mainRoot));

        stage.setScene(scene);
        stage.setTitle("TotemOrder");

        // --- INIZIO OTTIMIZZAZIONI KIOSK LINUX ---
        stage.initStyle(StageStyle.UNDECORATED);
        // stage.setAlwaysOnTop(true);
        stage.setMaximized(true);
        stage.setResizable(false);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");
        // stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        // scene.setCursor(javafx.scene.Cursor.NONE); // Rimuovi commento per nascondere
        // mouse su schermi touch se serve
        // --- FINE OTTIMIZZAZIONI KIOSK LINUX ---

        stage.show();

        Navigator.init(rootPane);

        // Avvia il server API locale per Android
        new Thread(() -> {
            try {
                com.api.services.LocalServerManager.startLocalServer();
            } catch (Exception e) {
                System.err.println("Errore avvio server Javalin: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void stop() {
        com.api.services.LocalServerManager.stopServer();
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
            memoryStatsLabel.setStyle(
                    "-fx-background-color: rgba(0,0,0,0.84); -fx-text-fill: white; -fx-padding: 10; -fx-font-size: 12px; -fx-font-family: 'Segoe UI', 'Arial'; -fx-border-radius: 6; -fx-background-radius: 6;");
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
        memoryStatsTicker = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(500), e -> updateMemoryStats(root)));
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

            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory
                    .getOperatingSystemMXBean();
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

            java.io.File diskRoot = new java.io.File("/");
            long diskTotal = diskRoot.getTotalSpace();
            long diskFree = diskRoot.getFreeSpace();
            String osName = System.getProperty("os.name") + " " + System.getProperty("os.arch");
            String javaVer = System.getProperty("java.version");
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

            String text = String.format(
                    "OS: %s | Java: %s | Uptime: %d min\n" +
                            "APP JVM: usata %s / %s (max %s)\n" +
                            "SISTEMA: %s / %s liberi\n" +
                            "DISCO (/): %s / %s liberi\n" +
                            "CPU LOAD: %s%% | TEMP: %s\n" +
                            "IP: %s\n" +
                            "THREAD: tot %d, daemon %d\n" +
                            "KDS CONNECTED: %d",
                    osName, javaVer, (uptimeMs / 60000),
                    humanReadableBytes(jvmUsed), humanReadableBytes(jvmTotal), humanReadableBytes(jvmMax),
                    sysFree >= 0 ? humanReadableBytes(sysFree) : "n/a",
                    sysTotal >= 0 ? humanReadableBytes(sysTotal) : "n/a",
                    humanReadableBytes(diskFree), humanReadableBytes(diskTotal),
                    getCpuLoad() != null ? String.format("%.1f", getCpuLoad() * 100) : "n/a",
                    getCpuTemperature(),
                    getLocalIpAddress().replace("\n", ", "),
                    threadCount, daemonCount, com.api.services.LocalServerManager.getDevicesConnected());

            memoryStatsLabel.setText(text);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Double getCpuLoad() {
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        return operatingSystemMXBean.getSystemLoadAverage() / operatingSystemMXBean.getAvailableProcessors();

    }

    private String getCpuTemperature() {
        try {
            java.nio.file.Path tempPath = java.nio.file.Path.of("/sys/class/thermal/thermal_zone0/temp");
            if (java.nio.file.Files.exists(tempPath)) {
                String textTemp = java.nio.file.Files.readString(tempPath).trim();
                double tempC = Double.parseDouble(textTemp) / 1000.0;
                return String.format("%.1f °C", tempC);
            }
        } catch (Exception e) {
            // ignore
        }
        return "n/a";
    }

    private String getLocalIpAddress() {
        String ret = "";
        Enumeration e = null;
        try {
            e = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
        while (e.hasMoreElements()) {
            NetworkInterface n = (NetworkInterface) e.nextElement();
            Enumeration ee = n.getInetAddresses();
            while (ee.hasMoreElements()) {
                InetAddress i = (InetAddress) ee.nextElement();
                if (!i.isLoopbackAddress() && i instanceof java.net.Inet4Address) {
                    ret += i.getHostAddress() + "\n";
                }
            }
        }
        return ret.trim();
    }

    private String humanReadableBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
