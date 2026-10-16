package io.github.nanoforged.launchredirector.api;

import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import lombok.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Immutable snapshot of the configuration used to launch the game process.
 *
 * <p>{@link #toMutable()} copies the containers but shares the element references, as does
 * {@link MutableLaunchContext#build()} in the other direction. That is safe only because every
 * element type is immutable ({@code String}, {@link Path}, {@link AgentSpec}, {@link ModuleSpec},
 * {@link Resolution}); adding a mutable component to any of them would silently degrade the plugin
 * chain's isolation to a shallow copy.
 */
@Data
@Builder
public final class LaunchContext {

    private final Path javaHome;

    @Singular("jvmArg")
    private final List<String> jvmArgs;

    @Singular("classpathEntry")
    private final Set<Path> classpath;

    @Singular("javaAgent")
    private final Set<AgentSpec> javaAgents;

    @Singular("moduleOption")
    private final Set<ModuleSpec> moduleOptions;

    @Singular("programArg")
    private final List<String> programArgs;

    private final String mainClass;

    private final Path workingDirectory;

    @Singular("env")
    private final Map<String, String> environment;

    @Singular("systemProperty")
    private final Map<String, String> systemProperties;

    private final String gameVersion;
    private final Resolution resolution;
    private final boolean fullScreen;
    private final boolean soundEnabled;
    private final boolean launchDirect;


    private final boolean jarLaunch;


    public MutableLaunchContext toMutable() {
        return new MutableLaunchContext(this);
    }


}