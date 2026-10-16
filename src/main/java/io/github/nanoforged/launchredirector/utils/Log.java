package io.github.nanoforged.launchredirector.utils;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal file logger for the pre-main agent.
 * <p>Configured by system properties, which are read once when this class initializes:
 * <ul>
 *   <li>{@code -Dlaunch.redirector.log=ture} to enable logging entirely, including the file itself.
 *   </li>
 *   <li>{@code -Dlaunch.redirector.log.file=<path>} writes somewhere other than the working
 *   directory.</li>
 *   <li>{@code -Dlaunch.redirector.verbose=true} adds DEBUG lines and stack traces for contained
 *   plugin failures. Stack traces are deliberately out of the default output so a broken mod cannot
 *   flood the log.</li>
 * </ul>
 */
public final class Log {

    private static final String LOG_PROP = "launch.redirector.log";
    private static final String LOG_FILE_PROP = "launch.redirector.log.file";
    private static final String VERBOSE_PROP = "launch.redirector.verbose";
    private static final String DEFAULT_LOG_FILE = "launchredirector.log";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(LOG_PROP, "false"));
    private static final boolean VERBOSE = Boolean.parseBoolean(System.getProperty(VERBOSE_PROP, "false"));
    private static final String LOG_FILE = System.getProperty(LOG_FILE_PROP, DEFAULT_LOG_FILE);

    private Log() {
    }

    public static void info(String msg) {
        write("INFO", msg);
    }

    public static void warn(String msg) {
        write("WARN", msg);
    }

    public static void error(String msg) {
        write("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        write("ERROR", msg + System.lineSeparator() + stackTrace(t));
    }

    public static void debug(String msg) {
        if (VERBOSE) {
            write("DEBUG", msg);
        }
    }

    public static void debug(String msg, Throwable t) {
        if (VERBOSE) {
            write("DEBUG", msg + System.lineSeparator() + stackTrace(t));
        }
    }

    private static String stackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter buffer = new StringWriter();
        t.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    private static synchronized void write(String level, String msg) {
        if (!ENABLED) {
            return;
        }
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = String.format("[%s] [%s] %s%n", timestamp, level, msg);
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.print(line);
        } catch (Exception e) {
            System.err.println("Failed to write log: " + line);
            e.printStackTrace();
        }
    }
}
