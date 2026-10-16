package io.github.nanoforged.launchredirector.api;

import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import io.github.nanoforged.launchredirector.utils.Log;
import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The mutable view of a {@link LaunchContext} that the plugin chain operates on: every collection
 * getter returns an unmodifiable copy, and every setter is null guarded so a bad value is rejected
 * at the call site instead of surfacing inside a later plugin or the command line builder.
 *
 * <p>The one deliberate exception is a {@code systemProperties} value: null is meaningful there, it
 * is the bare {@code -Dkey} switch form used by {@code -DlaunchDirect}.
 *
 * <p>Isolation does not depend on plugins behaving: {@link LaunchContext#toMutable()} always re-wraps
 * its input in a fresh mutable context, so a plugin that returns a live context instead of calling
 * {@link #build()} cannot make the chain alias its internal state.
 *
 * <p>{@code jvmArgs} and {@code programArgs} are ordered lists because duplicate tokens are legal and
 * their position decides which wins; the classpath is de-duplicated, hence a set.
 */
@Data
@Accessors(chain = true)
public final class MutableLaunchContext {

    private Path javaHome;
    private List<String> jvmArgs;
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
    private boolean jarLaunch;

    public MutableLaunchContext(LaunchContext ctx) {
        setJavaHome(ctx.getJavaHome());
        setJvmArgs(ctx.getJvmArgs());
        setClasspath(ctx.getClasspath());
        setJavaAgents(ctx.getJavaAgents());
        setModuleOptions(ctx.getModuleOptions());
        setProgramArgs(ctx.getProgramArgs());
        setMainClass(ctx.getMainClass());
        setWorkingDirectory(ctx.getWorkingDirectory());
        setEnvironment(ctx.getEnvironment());
        setSystemProperties(ctx.getSystemProperties());
        setGameVersion(ctx.getGameVersion());
        setResolution(ctx.getResolution());
        setFullScreen(ctx.isFullScreen());
        setSoundEnabled(ctx.isSoundEnabled());
        setLaunchDirect(ctx.isLaunchDirect());
        setJarLaunch(ctx.isJarLaunch());
    }


    /** Unmodifiable snapshot of the JVM arguments, in order. */
    public List<String> getJvmArgs() {
        return List.copyOf(jvmArgs);
    }

    /** Unmodifiable snapshot of the classpath. */
    public Set<Path> getClasspath() {
        return Set.copyOf(classpath);
    }

    public Set<AgentSpec> getJavaAgents() {
        return Set.copyOf(javaAgents);
    }

    public Set<ModuleSpec> getModuleOptions() {
        return Set.copyOf(moduleOptions);
    }

    /** Unmodifiable snapshot of the program arguments, in order. */
    public List<String> getProgramArgs() {
        return List.copyOf(programArgs);
    }

    public Map<String, String> getEnvironment() {
        return Map.copyOf(environment);
    }

    /**
     * Unmodifiable snapshot of the system properties.
     *
     * <p>Deliberately not {@link Map#copyOf}, which rejects null values: a null value is how the bare
     * {@code -Dkey} switch form is encoded.
     */
    public Map<String, String> getSystemProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(systemProperties));
    }

    public MutableLaunchContext setJvmArgs(Collection<String> jvmArgs) {
        this.jvmArgs = copyList("jvmArgs", jvmArgs);
        return this;
    }

    public MutableLaunchContext setClasspath(Collection<Path> classpath) {
        this.classpath = copySet("classpath", classpath);
        return this;
    }

    public MutableLaunchContext setJavaAgents(Collection<AgentSpec> javaAgents) {
        this.javaAgents = copySet("javaAgents", javaAgents);
        return this;
    }

    public MutableLaunchContext setModuleOptions(Collection<ModuleSpec> moduleOptions) {
        this.moduleOptions = copySet("moduleOptions", moduleOptions);
        return this;
    }

    public MutableLaunchContext setProgramArgs(Collection<String> programArgs) {
        this.programArgs = copyList("programArgs", programArgs);
        return this;
    }

    public MutableLaunchContext setEnvironment(Map<String, String> environment) {
        this.environment = copyMap("environment", environment);
        return this;
    }

    public MutableLaunchContext setSystemProperties(Map<String, String> systemProperties) {
        this.systemProperties = copySystemProperties(systemProperties);
        return this;
    }

    /**
     * Hands the live, mutable JVM argument list to {@code operator} and returns this context for
     * chaining. Unlike {@link #getJvmArgs()}, which is an unmodifiable snapshot, mutations here stick.
     */
    public MutableLaunchContext modifyJvmArgs(Consumer<? super List<String>> operator) {
        return mutate(operator, jvmArgs);
    }

    /** Live classpath, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifyClasspath(Consumer<? super Set<Path>> operator) {
        return mutate(operator, classpath);
    }

    /** Live java agent set, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifyJavaAgents(Consumer<? super Set<AgentSpec>> operator) {
        return mutate(operator, javaAgents);
    }

    /** Live module option set, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifyModuleOptions(Consumer<? super Set<ModuleSpec>> operator) {
        return mutate(operator, moduleOptions);
    }

    /** Live program argument list, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifyProgramArgs(Consumer<? super List<String>> operator) {
        return mutate(operator, programArgs);
    }

    /** Live environment map, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifyEnvironment(Consumer<? super Map<String, String>> operator) {
        return mutate(operator, environment);
    }

    /** Live system property map, mutable in place. See {@link #modifyJvmArgs}. */
    public MutableLaunchContext modifySystemProperties(Consumer<? super Map<String, String>> operator) {
        return mutate(operator, systemProperties);
    }

    /**
     * Applies {@code operator} to a live collection and returns this context for chaining. It
     * deliberately does not go through the corresponding setter: the setters normalize by dropping
     * nulls, which is right for values coming in from outside but would silently delete entries that
     * are already part of this context.
     */
    private <T> MutableLaunchContext mutate(Consumer<? super T> operator, T target) {
        Objects.requireNonNull(operator, "operator").accept(target);
        return this;
    }

    public MutableLaunchContext addEnvironment(String key, String value) {
        environment.put(requireKey("environment key", key), requireNonNullString("environment value", value));
        return this;
    }

    /**
     * Adds a system property. A null value is allowed and produces the bare {@code -Dkey} form; use
     * {@link #removeSystemProperties(String...)} to drop the property instead.
     */
    public MutableLaunchContext addSystemProperty(String key, String value) {
        systemProperties.put(requireKey("system property key", key), value);
        return this;
    }

    public MutableLaunchContext addProgramArgs(String... value) {
        requireElements("programArgs", value);
        programArgs.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addJvmArgs(String... value) {
        requireElements("jvmArgs", value);
        jvmArgs.addAll(List.of(value));
        return this;
    }

    /** A directory adds its own classes and the jars directly inside it; see {@link LaunchContext#getClasspath()}. */
    public MutableLaunchContext addClasspath(Path... value) {
        requireElements("classpath", value);
        classpath.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addJavaAgents(AgentSpec... value) {
        requireElements("javaAgents", value);
        javaAgents.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext addModuleOptions(ModuleSpec... value) {
        requireElements("moduleOptions", value);
        moduleOptions.addAll(List.of(value));
        return this;
    }

    public MutableLaunchContext removeJavaAgents(AgentSpec... value) {
        requireElements("javaAgents", value);
        List.of(value).forEach(javaAgents::remove);
        return this;
    }

    public MutableLaunchContext removeModuleOptions(ModuleSpec... value) {
        requireElements("moduleOptions", value);
        List.of(value).forEach(moduleOptions::remove);
        return this;
    }

    public MutableLaunchContext removeJvmArgs(String... value) {
        requireElements("jvmArgs", value);
        removeAllOccurrences(jvmArgs, value);
        return this;
    }

    public MutableLaunchContext removeClasspath(Path... value) {
        requireElements("classpath", value);
        List.of(value).forEach(classpath::remove);
        return this;
    }

    /**
     * Removes every occurrence rather than the first, because {@code programArgs} keeps duplicates.
     */
    public MutableLaunchContext removeProgramArgs(String... value) {
        requireElements("programArgs", value);
        removeAllOccurrences(programArgs, value);
        return this;
    }

    public MutableLaunchContext removeEnvironment(String... value) {
        requireElements("environment", value);
        List.of(value).forEach(environment::remove);
        return this;
    }

    public MutableLaunchContext removeSystemProperties(String... value) {
        requireElements("systemProperties", value);
        List.of(value).forEach(systemProperties::remove);
        return this;
    }

    /**
     * Freezes the current state into an immutable context. Identity fields are validated here so a
     * plugin that wiped one fails with a message naming it, rather than producing a child JVM that
     * dies on a nonsensical command line.
     *
     * @throws IllegalStateException when a required field was set to null by a plugin
     */
    public LaunchContext build() {
        requireField("javaHome", javaHome);
        requireField("mainClass", mainClass);
        requireField("workingDirectory", workingDirectory);

        return LaunchContext.builder()
                .javaHome(javaHome)
                .jvmArgs(jvmArgs)
                .classpath(classpath)
                .javaAgents(javaAgents)
                .moduleOptions(moduleOptions)
                .programArgs(programArgs)
                .mainClass(mainClass)
                .workingDirectory(workingDirectory)
                .environment(environment)
                .systemProperties(systemProperties)
                .gameVersion(gameVersion)
                .resolution(resolution)
                .fullScreen(fullScreen)
                .soundEnabled(soundEnabled)
                .launchDirect(launchDirect)
                .jarLaunch(jarLaunch)
                .build();
    }

    private static void requireField(String name, Object value) {
        if (value == null) {
            throw new IllegalStateException(
                    "Launch context field '" + name + "' is required but was set to null by a plugin");
        }
    }

    private static String requireKey(String name, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        return value;
    }

    private static String requireNonNullString(String name, String value) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    private static <T> void requireElements(String name, T[] values) {
        Objects.requireNonNull(values, name + " must not be null");
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException(name + "[" + i + "] must not be null");
            }
        }
    }

    @SafeVarargs
    private static <T> void removeAllOccurrences(List<T> target, T... values) {
        for (T value : values) {
            target.removeIf(value::equals);
        }
    }

    private static <T> List<T> copyList(String name, Collection<T> values) {
        Objects.requireNonNull(values, name + " must not be null");
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                Log.warn("[LaunchRedirector] Dropped null element from " + name);
                continue;
            }
            copy.add(value);
        }
        return copy;
    }

    private static <T> Set<T> copySet(String name, Collection<T> values) {
        Objects.requireNonNull(values, name + " must not be null");
        Set<T> copy = new LinkedHashSet<>(values.size());
        for (T value : values) {
            if (value == null) {
                Log.warn("[LaunchRedirector] Dropped null element from " + name);
                continue;
            }
            copy.add(value);
        }
        return copy;
    }

    /**
     * System properties keep null values, because they encode the bare {@code -Dkey} switch form. The
     * environment does not: {@link ProcessBuilder#environment()} rejects null values, so {@link
     * #copyMap} drops them there instead.
     */
    private static Map<String, String> copySystemProperties(Map<String, String> values) {
        Objects.requireNonNull(values, "systemProperties must not be null");
        Map<String, String> copy = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> {
            if (key == null || key.isEmpty()) {
                Log.warn("[LaunchRedirector] Dropped system property with a null or empty key");
                return;
            }
            copy.put(key, value);
        });
        return copy;
    }

    private static Map<String, String> copyMap(String name, Map<String, String> values) {
        Objects.requireNonNull(values, name + " must not be null");
        Map<String, String> copy = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> {
            if (key == null || value == null) {
                Log.warn("[LaunchRedirector] Dropped null entry from " + name);
                return;
            }
            copy.put(key, value);
        });
        return copy;
    }
}
