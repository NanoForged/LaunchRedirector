package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.LaunchRedirector;
import io.github.nanoforged.launchredirector.api.ILaunchConfigPlugin;
import io.github.nanoforged.launchredirector.api.LaunchContext;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import java.util.*;
import java.util.jar.JarFile;



public final class PluginLoader {

    private PluginLoader() {}



    public static LaunchContext applyPlugins(LaunchContext ctx) {
        Instrumentation inst = LaunchRedirector.getInstrumentation();
        if (inst == null) {
            throw new IllegalStateException("Instrumentation unavailable");
        }

        List<ModJarHelper.ModSpec> modSpecs = ModJarHelper.getModSpecs(ctx.getWorkingDirectory());
        if (modSpecs.isEmpty()) {
            Log.info("[LaunchRedirector] No enabled mod implements ILaunchConfigPlugin, skipping plugin chain.");
            return ctx;
        }

        modSpecs.forEach(modSpec -> modSpec.jars().forEach(jar -> {
            try (JarFile jf = new JarFile(jar.toFile())) {
                inst.appendToSystemClassLoaderSearch(jf);
                Log.info("[LaunchRedirector] Appended SPI mod jar to system classpath: " + jar);
            } catch (IOException e) {
                Log.error("[LaunchRedirector] Failed to append SPI mod jar " + jar, e);
            }
        }));

        List<ILaunchConfigPlugin> plugins = new ArrayList<>();
        ServiceLoader.load(ILaunchConfigPlugin.class, ClassLoader.getSystemClassLoader())
                .forEach(plugins::add);

        if (plugins.isEmpty()) {
            Log.info("[LaunchRedirector] No ILaunchConfigPlugin found after classpath append.");
            return ctx;
        }

        final var pluginsReg = new HashMap<String, Integer>();
        plugins.forEach(plugin -> pluginsReg.put(plugin.getClass().getName(), plugin.getPriority()));

        // Pre Process
        for (ILaunchConfigPlugin plugin : plugins) {
            try {
                Log.info("[LaunchRedirector] Notice plugin: " + plugin.getClass().getName());
                plugin.preProcess(pluginsReg);
            } catch (Exception e) {
                Log.warn("[LaunchRedirector] Error on "+ plugin.getClass().getName() + " Pre Processing " + e);
            }
        }

        // sorting
        plugins.sort(Comparator.comparingInt(ILaunchConfigPlugin::getPriority));

        // handle ctx chain
        LaunchContext last = ctx;
        for (ILaunchConfigPlugin plugin : plugins) {
            try {
                Log.info("[LaunchRedirector] Applying launch config: " + plugin.getClass().getName());
                LaunchContext next = plugin.process(last.toMutable());
                if (next != null) {
                    last = next;
                } else {
                    Log.warn("[LaunchRedirector] Plugin " + plugin.getClass().getName() + " returned null, keeping previous context.");
                }
            }catch (Exception e) {
                Log.warn("[LaunchRedirector] Error on "+ plugin.getClass().getName() + " Processing " + e);
            }

        }

        // Post Process
        for (ILaunchConfigPlugin plugin : plugins) {
            try {
                Log.info("[LaunchRedirector] Notice plugin: " + plugin.getClass().getName());
                plugin.postProcess(last);
            } catch (Exception e) {
                Log.warn("[LaunchRedirector] Error on "+ plugin.getClass().getName() + " Post Processing " + e);
            }
        }

        return last;
    }

}
