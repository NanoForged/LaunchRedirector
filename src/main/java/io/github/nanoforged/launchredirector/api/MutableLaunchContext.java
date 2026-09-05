package io.github.nanoforged.launchredirector.api;


import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@Data
@Accessors(chain = true)
public final class MutableLaunchContext {
    private Path javaHome;
    private Set<String> jvmArgs;
    private Set<Path> classpath;
    private Set<AgentSpec> javaAgents;
    private Set<ModuleSpec> moduleOptions;
    private List<String> programArgs;
    private String mainClass;
    private Path workingDirectory;
    private Map<String, String> environment;
    private Map<String, String> systemProperties;
    private String gameVersion;
    private Resolution resolution;
    private boolean fullScreen;
    private boolean soundEnabled;
    private boolean launchDirect;

    public MutableLaunchContext(LaunchContext ctx) {
        this.javaHome = ctx.getJavaHome();
        this.jvmArgs = new LinkedHashSet<>(ctx.getJvmArgs());
        this.classpath = new LinkedHashSet<>(ctx.getClasspath());
        this.javaAgents = new LinkedHashSet<>(ctx.getJavaAgents());
        this.moduleOptions = new LinkedHashSet<>(ctx.getModuleOptions());
        this.programArgs = new ArrayList<>(ctx.getProgramArgs());
        this.mainClass = ctx.getMainClass();
        this.workingDirectory = ctx.getWorkingDirectory();
        this.environment = new HashMap<>(ctx.getEnvironment());
        this.systemProperties = new HashMap<>(ctx.getSystemProperties());
        this.gameVersion = ctx.getGameVersion();
        this.resolution = ctx.getResolution();
        this.fullScreen = ctx.isFullScreen();
        this.soundEnabled = ctx.isSoundEnabled();
        this.launchDirect = ctx.isLaunchDirect();
    }

    public LaunchContext build() {
        return LaunchContext.builder()
                .javaHome(this.javaHome)
                .jvmArgs(new LinkedHashSet<>(this.jvmArgs))
                .classpath(new LinkedHashSet<>(this.classpath))
                .javaAgents(new LinkedHashSet<>(this.javaAgents))
                .moduleOptions(new LinkedHashSet<>(this.moduleOptions))
                .programArgs(new ArrayList<>(this.programArgs))
                .mainClass(this.mainClass)
                .workingDirectory(this.workingDirectory)
                .environment(new HashMap<>(this.environment))
                .systemProperties(new HashMap<>(this.systemProperties))
                .gameVersion(this.gameVersion)
                .resolution(this.resolution)
                .fullScreen(this.fullScreen)
                .soundEnabled(this.soundEnabled)
                .launchDirect(this.launchDirect)
                .build();
    }

    public MutableLaunchContext withJvmArgs(Consumer<Set<String>> operator) {
        operator.accept(this.jvmArgs);
        return this;
    }

    public MutableLaunchContext withClasspath(Consumer<Set<Path>> operator) {
        operator.accept(this.classpath);
        return this;
    }

    public MutableLaunchContext withJavaAgents(Consumer<Set<AgentSpec>> operator) {
        operator.accept(this.javaAgents);
        return this;
    }

    public MutableLaunchContext withModuleOptions(Consumer<Set<ModuleSpec>> operator) {
        operator.accept(this.moduleOptions);
        return this;
    }

    public MutableLaunchContext withProgramArgs(Consumer<List<String>> operator) {
        operator.accept(this.programArgs);
        return this;
    }

    public MutableLaunchContext withEnvironment(Consumer<Map<String, String>> operator) {
        operator.accept(this.environment);
        return this;
    }

    public MutableLaunchContext withSystemProperties(Consumer<Map<String, String>> operator) {
        operator.accept(this.systemProperties);
        return this;
    }

    public MutableLaunchContext addEnvironment(String key, String value) {
        this.environment.put(key, value);
        return this;
    }

    public MutableLaunchContext addSystemProperty(String key, String value) {
        this.systemProperties.put(key, value);
        return this;
    }

    public MutableLaunchContext addProgramArgs(String... value) {
        this.programArgs.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addJvmArgs(String... value) {
        this.jvmArgs.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addClasspath(Path... value) {
        this.classpath.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addJavaAgents(AgentSpec... value) {
        this.javaAgents.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addModuleOptions(ModuleSpec... value) {
        this.moduleOptions.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext removeJavaAgents(AgentSpec... value) {
        List.of(value).forEach(this.javaAgents::remove);
        return this;
    }

    public MutableLaunchContext removeModuleOptions(ModuleSpec... value) {
        List.of(value).forEach(this.moduleOptions::remove);
        return this;
    }

    public MutableLaunchContext removeJvmArgs(String... value) {
        List.of(value).forEach(this.jvmArgs::remove);
        return this;
    }

    public MutableLaunchContext removeClasspath(Path... value) {
        List.of(value).forEach(this.classpath::remove);
        return this;
    }

    public MutableLaunchContext removeProgramArgs(String... value) {
        this.programArgs.removeAll(List.of(value));
        return this;
    }

    public MutableLaunchContext removeEnvironment(String... value) {
        List.of(value).forEach(this.environment::remove);
        return this;
    }

    public MutableLaunchContext removeSystemProperties(String... value) {
        List.of(value).forEach(this.systemProperties::remove);
        return this;
    }
}
