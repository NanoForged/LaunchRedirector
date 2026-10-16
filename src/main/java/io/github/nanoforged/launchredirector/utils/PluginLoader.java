package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.LaunchRedirector;
import io.github.nanoforged.launchredirector.api.ILaunchConfigPlugin;
import io.github.nanoforged.launchredirector.api.LaunchContext;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

/**
 * Loads the {@link ILaunchConfigPlugin} implementations shipped by enabled mods and runs them as a
 * responsibility chain over the launch context. No plugin failure may abort the relaunch: only a
 * missing {@link Instrumentation} does, because without it no mod jar can be made loadable at all.
 */
public final class PluginLoader {

    /** Appended mod jars are remembered so a repeated chain run does not re-append them. */
    private static final List<Path> APPENDED_JARS = new ArrayList<>();

    private PluginLoader() {
    }

    public static LaunchContext applyPlugins(LaunchContext ctx) {
        Instrumentation inst = LaunchRedirector.getInstrumentation();
        if (inst == null) {
            throw new IllegalStateException("Instrumentation unavailable");
        }

        // the mods directory comes from a system property a plugin may rewrite, so it is resolved once
        // here and reused for the rest of the chain
        Path modsDir = ModJarHelper.resolveModsDir(ctx.getWorkingDirectory());
        List<ModJarHelper.ModSpec> modSpecs = ModJarHelper.getModSpecs(modsDir);
        if (modSpecs.isEmpty()) {
            Log.info("[LaunchRedirector] No enabled mod implements ILaunchConfigPlugin, skipping plugin chain.");
            return ctx;
        }

        appendModJars(inst, modSpecs);

        List<ILaunchConfigPlugin> plugins = new ArrayList<>();
        ServiceLoader.load(ILaunchConfigPlugin.class, ClassLoader.getSystemClassLoader())
                .forEach(plugins::add);

        if (plugins.isEmpty()) {
            Log.info("[LaunchRedirector] No ILaunchConfigPlugin found after classpath append.");
            return ctx;
        }

        Map<String, Integer> registry = resolvePriorities(plugins);
        List<ILaunchConfigPlugin> ordered = orderPlugins(plugins, registry);

        for (ILaunchConfigPlugin plugin : ordered) {
            safeRun("preProcess", plugin, () -> plugin.preProcess(Map.copyOf(registry)));
        }

        LaunchContext last = ctx;
        for (ILaunchConfigPlugin plugin : ordered) {
            LaunchContext next = safeProcess(plugin, last);
            if (next != null && next != last) {
                last = next;
            }
        }

        for (ILaunchConfigPlugin plugin : ordered) {
            LaunchContext finalContext = last;
            safeRun("postProcess", plugin, () -> plugin.postProcess(finalContext));
        }

        return last;
    }

    private static void appendModJars(Instrumentation inst, List<ModJarHelper.ModSpec> modSpecs) {
        for (ModJarHelper.ModSpec modSpec : modSpecs) {
            for (Path jar : modSpec.jars()) {
                if (APPENDED_JARS.contains(jar)) {
                    continue;
                }
                try (JarFile jf = new JarFile(jar.toFile())) {
                    inst.appendToSystemClassLoaderSearch(jf);
                    APPENDED_JARS.add(jar);
                    Log.info("[LaunchRedirector] Appended SPI mod jar to system classpath: " + jar);
                } catch (IOException e) {
                    Log.error("[LaunchRedirector] Failed to append SPI mod jar " + jar, e);
                }
            }
        }
    }

    /**
     * Resolves every plugin's priority up front into a {@code class name -> priority} lookup table. A
     * plugin whose {@code getPriority()} throws is still kept, at the default priority, because
     * dropping it silently would be worse than running it late.
     */
    private static Map<String, Integer> resolvePriorities(List<ILaunchConfigPlugin> plugins) {
        Map<String, Integer> registry = new HashMap<>();
        for (ILaunchConfigPlugin plugin : plugins) {
            String name = plugin.getClass().getName();
            int priority;
            try {
                priority = plugin.getPriority();
            } catch (Throwable t) {
                priority = ILaunchConfigPlugin.DEFAULT_PRIORITY;
                Log.warn("[LaunchRedirector] Plugin " + name + " threw from getPriority(), using "
                        + priority + ": " + describe(t));
            }
            if (registry.putIfAbsent(name, priority) != null) {
                Log.warn("[LaunchRedirector] Duplicate plugin class in chain, keeping the first: " + name);
            }
        }
        return registry;
    }

    /**
     * Sorts deterministically in ascending priority (higher runs later): duplicate service entries
     * collapse to one instance, and class name breaks priority ties because {@link ServiceLoader}
     * iteration order is not guaranteed, so the same mod set could otherwise order differently on
     * every run.
     */
    private static List<ILaunchConfigPlugin> orderPlugins(List<ILaunchConfigPlugin> plugins,
                                                         Map<String, Integer> registry) {
        Map<String, ILaunchConfigPlugin> byName = new HashMap<>();
        for (ILaunchConfigPlugin plugin : plugins) {
            byName.putIfAbsent(plugin.getClass().getName(), plugin);
        }

        List<ILaunchConfigPlugin> ordered = new ArrayList<>(byName.values());
        ordered.sort(Comparator
                .comparingInt((ILaunchConfigPlugin p) -> registry.getOrDefault(
                        p.getClass().getName(), ILaunchConfigPlugin.DEFAULT_PRIORITY))
                .thenComparing(p -> p.getClass().getName()));
        return ordered;
    }

    /**
     * Runs one {@code process} step. The mutable context handed to the plugin is discarded on failure,
     * so a plugin that mutates and then throws cannot leave a partially applied configuration; the
     * previous context is kept instead of aborting the relaunch.
     */
    private static LaunchContext safeProcess(ILaunchConfigPlugin plugin, LaunchContext previous) {
        String name = plugin.getClass().getName();
        try {
            Log.info("[LaunchRedirector] Applying launch config: " + name);
            LaunchContext next = plugin.process(previous.toMutable());
            if (next == null) {
                Log.warn("[LaunchRedirector] Plugin " + name + " returned null, keeping previous context.");
            }
            return next;
        } catch (Throwable t) {
            Log.warn("[LaunchRedirector] Error on " + name + " processing, keeping previous context: "
                    + describe(t));
            Log.debug("[LaunchRedirector] Stack trace for " + name, t);
            return null;
        }
    }

    private static void safeRun(String phase, ILaunchConfigPlugin plugin, Runnable action) {
        try {
            Log.info("[LaunchRedirector] " + phase + " for " + plugin.getClass().getName());
            action.run();
        } catch (Throwable t) {
            Log.warn("[LaunchRedirector] Error on " + plugin.getClass().getName() + " " + phase + ": "
                    + describe(t));
            Log.debug("[LaunchRedirector] Stack trace for " + plugin.getClass().getName(), t);
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank()
                ? t.getClass().getName()
                : t.getClass().getName() + ": " + message;
    }
}
