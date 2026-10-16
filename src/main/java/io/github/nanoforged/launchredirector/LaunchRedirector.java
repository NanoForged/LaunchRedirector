package io.github.nanoforged.launchredirector;

import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.MutableLaunchContext;
import io.github.nanoforged.launchredirector.api.State;
import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import io.github.nanoforged.launchredirector.mixin.MixinStarfarerLauncher;
import io.github.nanoforged.launchredirector.utils.LaunchContextHelper;
import io.github.nanoforged.launchredirector.utils.Log;
import io.github.nanoforged.launchredirector.utils.PluginLoader;
import lombok.Getter;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.github.nanoforged.launchredirector.api.State.STATE_PROP;
import static io.github.nanoforged.launchredirector.utils.LaunchContextHelper.*;

public class LaunchRedirector {

    public static String gameVersion;

    private static final String REGISTERED_PROP = "launch.redirector.registered";

    private static final AtomicBoolean RELAUNCHED = new AtomicBoolean(false);

    /** Exit code used when the child process could not be started at all. */
    private static final int RELAUNCH_FAILED_EXIT_CODE = 1337;

    /** Resolution used when a plugin nulled it out; the game's own launcher default. */
    private static final Resolution DEFAULT_RESOLUTION = Resolution.of(1920, 1080);

    /** Headless launcher switch, read by {@code StarfarerSettings.isLaunchDirect()}. */
    private static final String LAUNCH_DIRECT_PROP = "launchDirect";

    public static final IClassProvider CLASS_PROVIDER = new BasicClassProvider();
    public static final TransformerManager TRANSFORMER_MANAGER = new TransformerManager(CLASS_PROVIDER);


    @Getter
    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst){
        if (System.getProperty(REGISTERED_PROP) != null) {
            Log.info("[LaunchRedirector] Already registered, skipping duplicate agent.");
            return;
        }
        System.setProperty(REGISTERED_PROP, "true");

        if (State.GAME.name().equals(System.getProperty(STATE_PROP))) {
            Log.info("[LaunchRedirector] Phase=GAME, skipping injection.");
            return;
        }
        System.setProperty(STATE_PROP, State.INIT.name());

        instrumentation = inst;
        TRANSFORMER_MANAGER.addTransformer(MixinStarfarerLauncher.class.getName());
        TRANSFORMER_MANAGER.hookInstrumentation(instrumentation);
    }




    private static void handleRelaunch(LaunchContext context) {
        if (!RELAUNCHED.compareAndSet(false, true)) {
            Log.warn("[LaunchRedirector] Relaunch already triggered, ignoring duplicate.");
            return;
        }

        LaunchContext finalCtx = withChildLaunchProperties(PluginLoader.applyPlugins(context).toMutable()).build();
        List<String> command = buildCommandLine(finalCtx);
        logCommand(finalCtx, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(finalCtx.getWorkingDirectory().toFile());
            pb.environment().putAll(finalCtx.getEnvironment());
            pb.inheritIO();
            var p = pb.start();
            Log.info("[LaunchRedirector] Child process created: " + p.pid());
            System.exit(0);
        } catch (Exception e) {
            Log.error("[LaunchRedirector] Failed to start the relaunched child process", e);
            System.exit(RELAUNCH_FAILED_EXIT_CODE);
        }
    }

    /**
     * Writes the launch switches back into the system properties of the child: these three properties
     * are the only channel a plugin's change crosses the process boundary on, since
     * {@link LaunchContextHelper#buildFromCurrentJvm()} re-reads them. The stage marker is flipped to
     * {@link State#GAME} so the agent injected into the child stays inert.
     */
    private static MutableLaunchContext withChildLaunchProperties(MutableLaunchContext ctx) {
        ctx.modifySystemProperties(map -> {
            map.put(STATE_PROP, State.GAME.name());

            // -DlaunchDirect is the headless-launch switch: StarfarerSettings.isLaunchDirect() is
            // exactly "System.getProperty("launchDirect") != null", and the launcher only calls
            // launchGame instead of showing its UI while it is set. The null value is deliberate, it
            // is what emits the bare "-DlaunchDirect" token, the same form Utils.restartGame writes.
            if (ctx.isLaunchDirect()) {
                map.put(LAUNCH_DIRECT_PROP, null);
            }

            map.put(FS_PROP, String.valueOf(ctx.isFullScreen()));
            map.put(SOUND_PROP, String.valueOf(ctx.isSoundEnabled()));
            map.put(RES_PROP, ctx.getResolution() == null 
                    ? DEFAULT_RESOLUTION.toString()
                    : ctx.getResolution().toString());
        });

        return ctx;
    }

    private static void logCommand(LaunchContext finalCtx, List<String> command) {
        var agent = finalCtx.getJavaAgents().stream().map(Record::toString).collect(Collectors.joining("\n"));
        var home = finalCtx.getJavaHome().toString();
        var work = finalCtx.getWorkingDirectory().toString();
        var main = finalCtx.getMainClass();
        Log.info(String.format(
                """
                [LaunchRedirector]
                =======================
                Starting Relaunch...
                Target:  %s
                Dir: %s
                Home: %s
                Agent: %s
                =======================
                """
                , main, work, home, agent
        ));

        Log.info(String.format(
                """
                [LaunchRedirector] Relaunching with command:
                %s
                """, String.join(System.lineSeparator(), command)));
    }

    public static void relaunch() {
        handleRelaunch(LaunchContextHelper.buildFromCurrentJvm());
    }

    public static void relaunch(boolean fs, boolean sound, String w, String h) {
        handleRelaunch(LaunchContextHelper.buildFromCurrentJvm(fs, sound, w, h));
    }

    /**
     * Expands one declared classpath entry into the tokens the child should receive.
     *
     * <p>A directory expands into two tokens, because neither form covers the other: the directory
     * itself keeps working as a class root, so classes and resources below it resolve by package path,
     * while {@code <dir>/*} adds the jars sitting directly in it. Handing over a directory and
     * expecting its jars to come along is the natural thing for a caller to do, and a bare directory
     * entry silently ignores them.
     *
     * <p>{@code <dir>/*} is emitted only when the directory actually holds a jar. The VM tolerates an
     * unmatched wildcard, but the star survives into the child's {@code java.class.path}, and a star
     * cannot be read back into a {@link Path}; keeping it out means every value this process writes is
     * readable by {@link LaunchContextHelper#buildFromCurrentJvm()}.
     *
     * <p>Entries are made absolute here on purpose: the child resolves a relative {@code -cp} entry
     * against its own working directory, which need not be the directory the entry was written in, and
     * directory entries are the ones normally written relative to a project.
     */
    private static List<String> expandClasspathEntry(Path entry) {
        Path absolute = entry.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute) || !containsJar(absolute)) {
            return List.of(absolute.toString());
        }
        return List.of(absolute.toString(), absolute + "/*");
    }

    /** True when the directory holds at least one jar directly inside it. */
    private static boolean containsJar(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.anyMatch(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".jar"));
        } catch (IOException e) {
            Log.warn("[LaunchRedirector] Cannot list classpath directory " + directory + ": " + e);
            return false;
        }
    }

    public static List<String> buildCommandLine(LaunchContext ctx) {
        List<String> cmd = new ArrayList<>();
        Log.info("[LaunchRedirector] Building command line");

        // taken from the context, not this JVM's java.home: the context is what plugins adjust
        Path javaHome = ctx.getJavaHome() != null
                ? ctx.getJavaHome()
                : Path.of(System.getProperty("java.home"));
        cmd.add(Path.of(javaHome.toString(), "bin", "java").toString());

        cmd.addAll(ctx.getJvmArgs());

        // system properties. A null value is not a mistake: it is the bare "-Dkey" switch form the
        // game itself uses for -DlaunchDirect, so nulls must survive the context all the way here.
        ctx.getSystemProperties().forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                cmd.add("-D" + key);
            } else {
                cmd.add("-D" + key + "=" + value);
            }
        });



        // only -javaagent / -jar / -cp impose an order on the remaining tokens: a -jar start ignores
        // -cp, so the classpath form and the jar form are mutually exclusive and both must precede
        // the program args.
        ctx.getModuleOptions().stream()
                .map(ModuleSpec::toArgument)
                .forEach(cmd::add);

        ctx.getJavaAgents().stream()
                .map(AgentSpec::toArgument)
                .forEach(cmd::add);

        if (ctx.isJarLaunch()) {
            // a "-jar" start carries its own classpath in the manifest, "-cp" would be ignored
            cmd.add("-jar");
            cmd.add(ctx.getMainClass());
        } else {
            if (!ctx.getClasspath().isEmpty()) {
                cmd.add("-cp");
                // one declared directory becomes two tokens, and two declarations can normalize onto
                // the same directory, so the expanded result is de-duplicated keeping the given order
                Set<String> entries = new LinkedHashSet<>();
                ctx.getClasspath().forEach(entry -> entries.addAll(expandClasspathEntry(entry)));
                cmd.add(String.join(File.pathSeparator, entries));
            }

            cmd.add(ctx.getMainClass());
        }

        cmd.addAll(ctx.getProgramArgs());

        return cmd;
    }




    public static String getGameVersion() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        if(gameVersion == null){
            var clazz = Class.forName("com.fs.starfarer.Version");
            Field verf = clazz.getDeclaredField("versionOnly");
            verf.setAccessible(true);
            gameVersion =(String) verf.get(null);
        }
        return gameVersion;
    }
}
