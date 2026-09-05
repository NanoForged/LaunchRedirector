package io.github.nanoforged.launchredirector.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log {

    private static final String LOG_FILE = "launchredirector.log";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");


    public static void info(String msg) {
        log("INFO", msg);
    }

    public static void warn(String msg) {
        log("WARN", msg);
    }

    public static void error(String msg) {
        log("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        log("ERROR", msg + " - " + t.toString());
    }

    private static synchronized void log(String level, String msg) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = String.format("[%s] [%s] %s%n", timestamp, level, msg);
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.print(line);
        } catch (IOException e) {
            System.err.println("Failed to write log: " + line);
            e.printStackTrace();
        }
    }

}
