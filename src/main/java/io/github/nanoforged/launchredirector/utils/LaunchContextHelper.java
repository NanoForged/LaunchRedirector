package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.LaunchRedirector;
import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@UtilityClass
public class LaunchContextHelper {

    public static final String FALLBACK_MAIN = "com.fs.starfarer.StarfarerLauncher";
    public static final String FS_PROP = "startFS";
    public static final String SOUND_PROP = "startSound";
    public static final String RES_PROP = "startRes";

    public static LaunchContext buildFromCurrentJvm(boolean fs, boolean sound, String w, String h) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        List<String> inputArgs = runtime.getInputArguments();
        LaunchContext defaultContext = JvmArgumentParser.buildDefaultContextFromJVMArgs(inputArgs);

        Set<Path> classpath = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                classpath.add(Path.of(entry).toAbsolutePath().normalize());
            }
        }

        String mainClass = FALLBACK_MAIN;
        List<String> programArgs = new ArrayList<>();
        boolean jarLaunch = false;
        String javaCommand = System.getProperty("sun.java.command");
        if (javaCommand != null && !javaCommand.isBlank()) {
            // "sun.java.command" holds the main class (or the jar path for a "-jar" start) followed by
            // the program arguments, joined with single spaces and unquoted, so arguments containing
            // whitespace are not recoverable. Forwarding the rest beats dropping them, which is what
            // used to happen here; the game's own relauncher (Utils.restartGame) does the same.
            String[] parts = javaCommand.trim().split("\\s+");
            String head = parts[0];

            if (head.endsWith(".jar")) {
                Path jar = toPathOrNull(head);
                if (jar != null && Files.isRegularFile(jar)) {
                    // the jar path sits where the main class normally sits
                    jarLaunch = true;
                    mainClass = jar.toAbsolutePath().normalize().toString();
                } else {
                    mainClass = head;
                }
            } else {
                mainClass = head;
            }

            programArgs.addAll(Arrays.asList(parts).subList(1, parts.length));
        }

        // an unreadable game version must not stop the relaunch
        String version;
        try {
            version = LaunchRedirector.getGameVersion();
        } catch (Exception e) {
            version = "unknown";
            Log.warn("[LaunchRedirector] Failed to read game version: " + e);
        }

        return defaultContext.toMutable()
                .setJavaHome(Path.of(System.getProperty("java.home")))
                .setClasspath(classpath)
                .setMainClass(mainClass)
                .setWorkingDirectory(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize())
                .setEnvironment(new HashMap<>(System.getenv()))
                .setProgramArgs(programArgs)
                .setJarLaunch(jarLaunch)
                .setGameVersion(version)
                .setResolution(Resolution.of(w, h))
                .setFullScreen(fs)
                .setSoundEnabled(sound)
                .setLaunchDirect(true)
                .build();
    }

    private static Path toPathOrNull(String candidate) {
        try {
            return Path.of(candidate);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static LaunchContext buildFromCurrentJvm() {
        boolean fs = Boolean.parseBoolean(System.getProperty(FS_PROP, "false"));
        boolean sound = Boolean.parseBoolean(System.getProperty(SOUND_PROP, "true"));
        String res = System.getProperty(RES_PROP, "1920x1080");
        String[] parts = res.split("x");
        String w = parts.length > 0 ? parts[0] : "1920";
        String h = parts.length > 1 ? parts[1] : "1080";
        return buildFromCurrentJvm(fs, sound, w, h);
    }
}
