package com.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal logger utility.
 */
public class Log {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static Level currentLevel = Level.INFO;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public static void debug(String msg) {
        if (currentLevel.ordinal() <= Level.DEBUG.ordinal())
            print("DEBUG", msg);
    }

    public static void info(String msg) {
        if (currentLevel.ordinal() <= Level.INFO.ordinal())
            print("INFO", msg);
    }

    public static void warn(String msg) {
        if (currentLevel.ordinal() <= Level.WARN.ordinal())
            print("WARN", msg);
    }

    public static void error(String msg) {
        print("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        print("ERROR", msg + " — " + t.getMessage());
    }

    private static void print(String level, String msg) {
        String ts = LocalDateTime.now().format(FMT);
        System.out.printf("  [%s] %s: %s%n", ts, level, msg);
    }
}
