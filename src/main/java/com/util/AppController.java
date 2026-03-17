package com.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;

public class AppController {
    private static AppController instance;
    private static final int INACTIVITY_TIMEOUT_MS = 30_000;
    private long lastActivity = System.currentTimeMillis();
    private Runnable onTimeout;
    private Thread timerThread;

    private AppController() {}

    public static AppController getInstance() {
        if (instance == null) instance = new AppController();
        return instance;
    }

    public void attachToScene(Scene scene, Runnable onTimeoutAction) {
        this.onTimeout = () -> {
            
        };
        EventHandler<MouseEvent> mouseHandler = e -> resetTimer();
        EventHandler<TouchEvent> touchHandler = e -> resetTimer();
        scene.addEventFilter(MouseEvent.ANY, mouseHandler);
        scene.addEventFilter(TouchEvent.ANY, touchHandler);
        startTimer();
    }

    private void resetTimer() {
        lastActivity = System.currentTimeMillis();
    }

    private void startTimer() {
        if (timerThread != null && timerThread.isAlive()) return;
        timerThread = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                if (System.currentTimeMillis() - lastActivity > INACTIVITY_TIMEOUT_MS) {
                    Platform.runLater(() -> {
                        if (onTimeout != null) onTimeout.run();
                    });
                    resetTimer();
                }
            }
        }, "AppInactivityTimer");
        timerThread.setDaemon(true);
        timerThread.start();
    }
}
