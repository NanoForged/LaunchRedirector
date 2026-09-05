package io.github.nanoforged.launchredirector;

import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.MutableLaunchContext;
import io.github.nanoforged.launchredirector.api.State;
import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.mixin.MixinStarfarerLauncher;
import io.github.nanoforged.launchredirector.utils.LaunchContextHelper;
import io.github.nanoforged.launchredirector.utils.Log;
import io.github.nanoforged.launchredirector.utils.PluginLoader;
import lombok.Getter;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.github.nanoforged.launchredirector.api.State.STATE_PROP;
import static io.github.nanoforged.launchredirector.utils.LaunchContextHelper.*;

public class LaunchRedirector {

    public static String gameVersion;

    private static final String REGISTERED_PROP = "launch.redirector.registered";

    private static final AtomicBoolean RELAUNCHED = new AtomicBoolean(false);

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


        // ctx chain
        MutableLaunchContext returnedCtx = PluginLoader.applyPlugins(context).toMutable();


        // Force Prop Overwrite

        LaunchContext finalCtx = returnedCtx.
                withSystemProperties( map -> {
                            map.put(STATE_PROP, State.GAME.name());

                            if (returnedCtx.isLaunchDirect()) {
                                map.put("launchDirect",null);
                            }

                            map.put(FS_PROP, String.valueOf(returnedCtx.isFullScreen()));
                            map.put(SOUND_PROP, String.valueOf(returnedCtx.isSoundEnabled()));
                            map.put(RES_PROP,returnedCtx.getResolution().toString());

                        }
                ).build();


        // 构建命令行
        List<String> command = buildCommandLine(finalCtx);

        logging(finalCtx, command);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(finalCtx.getWorkingDirectory().toFile());
            pb.environment().putAll(finalCtx.getEnvironment());
            pb.inheritIO();
            var p = pb.start();
            Log.info("[LaunchRedirector] Child process Created: " + p.pid());
            System.exit(0);
        } catch (Exception e) {
            System.exit(1337);
        }
    }

    private static void logging(LaunchContext finalCtx,List<String> commandLine) {
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
                ,main,work,home,agent
        ));

        var sb = new StringBuilder();
        commandLine.forEach(string ->  sb.append(string).append("\n"));
        Log.info(String.format(
                """
                [LaunchRedirector] Relaunching with command:
                %s
                """,sb));
    }

    public static void relaunch(){
        handleRelaunch(LaunchContextHelper.buildFromCurrentJvm());
    }

    public  static void relaunch(boolean fs, boolean sound, String w, String h){
        handleRelaunch(LaunchContextHelper.buildFromCurrentJvm(fs,sound,w,h));
    }

    public static List<String> buildCommandLine(LaunchContext ctx) {
        LinkedHashSet<String> cmd = new LinkedHashSet<>();
        Log.info("[LaunchRedirector] Building command line");

        // 1. Java Executable
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        cmd.add(java);

        // 2. JVM Args
        cmd.addAll(ctx.getJvmArgs());

        // 3. Sys Prop（-D）
        ctx.getSystemProperties().forEach((k, v) -> {
            if (v == null || v.isEmpty()) cmd.add("-D" + k);
            else cmd.add("-D" + k + "=" + v);
        });

        // 4. Module（--x-x=x=x）
        ctx.getModuleOptions().stream()
                .map(ModuleSpec::toArgument)
                .forEach(cmd::add);

        // 5. Java Agent
        ctx.getJavaAgents().stream()
                .map(AgentSpec::toArgument)
                .forEach(cmd::add);

        // 6. classpath
        if (!ctx.getClasspath().isEmpty()) {
            cmd.add("-cp");
            cmd.add(ctx.getClasspath().stream()
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator)));
        }

        // 7. main
        cmd.add(ctx.getMainClass());

        // 8. args
        cmd.addAll(ctx.getProgramArgs());

        return cmd.stream().toList();
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
