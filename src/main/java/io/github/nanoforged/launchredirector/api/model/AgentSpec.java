package io.github.nanoforged.launchredirector.api.model;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarFile;

public record AgentSpec(Type type, Path path,@Nullable String args) {

    private static final String PREMAIN_CLASS =
            "io.github.nanoforged.launchredirector.LaunchRedirector";

    private static final String PREMAIN_CLASS_ATTRIBUTE = "Premain-Class";

    public enum Type {
        JAVAAGENT("-javaagent:"),
        AGENTLIB("-agentlib:"),
        AGENTPATH("-agentpath:");

        @Getter

        private final String flag;

        Type(String flag) {
            this.flag = flag;
        }
    }


    public String toArgument() {
        // Only -javaagent: takes a file path. -agentlib: takes a library name the VM resolves through
        // java.library.path and -agentpath: is used as given, so absolutizing either rewrites
        // "-agentlib:jdwp" into "-agentlib:C:\...\jdwp" and the child fails with "agent library failed
        // to init".
        String target = type == Type.JAVAAGENT ? path.toAbsolutePath().toString() : path.toString();
        String base = type.getFlag() + target;
        return (args != null && !args.isEmpty()) ? base + "=" + args : base;
    }

    public static boolean isParseable(String argument) {
        return detectType(argument) != null;
    }

    private static Type detectType(String argument) {
        return Arrays.stream(Type.values()).filter(t -> argument.startsWith(t.getFlag())).findAny().orElse(null);
    }


    public static AgentSpec parse(String argument) {
        Type type = detectType(argument);
        if (type == null) {
            throw new IllegalArgumentException("Unrecognized agent argument: " + argument);
        }

        String stripped = argument.substring(type.getFlag().length());
        int eq = stripped.indexOf('=');
        if (eq >= 0) {
            return new AgentSpec(type, Path.of(unquote(stripped.substring(0, eq))), stripped.substring(eq + 1));
        } else {
            return new AgentSpec(type, Path.of(unquote(stripped)), null);
        }
    }

    /**
     * True when the argument names this agent itself, so that P1 does not forward its own
     * {@code -javaagent} into the child JVM.
     *
     * <p>Identity comes from the jar manifest, not from its path or file name: the argument is a jar
     * the JVM already opened as an agent, so its {@code Premain-Class} is authoritative. Renamed,
     * relocated or duplicated copies of this jar are all recognized, while another project's agent
     * whose name merely looks similar is not.
     */
    public static boolean isSelf(String javaagentArg) {
        if (javaagentArg == null || !isParseable(javaagentArg)) {
            return false;
        }

        File jar = new File(targetOf(javaagentArg));
        if (!jar.isFile()) {
            return false;
        }

        try (JarFile jarFile = new JarFile(jar)) {
            var manifest = jarFile.getManifest();
            String premain = manifest == null
                    ? null
                    : manifest.getMainAttributes().getValue(PREMAIN_CLASS_ATTRIBUTE);
            return PREMAIN_CLASS.equals(premain);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The argument comes from {@code RuntimeMXBean.getInputArguments()}, which stores an
     * {@code -X} option as the single string the VM was given. A launcher that hands over
     * {@code -javaagent:"C:\Program Files\agent.jar"} therefore keeps the quotes, so they have to
     * come off before the value is used as a path.
     */
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /** Extracts the agent target from an argument, dropping any {@code =args} suffix. */
    private static String targetOf(String javaagentArg) {
        Type type = detectType(javaagentArg);
        String stripped = javaagentArg.substring(type.getFlag().length());
        int eq = stripped.indexOf('=');
        return unquote(eq >= 0 ? stripped.substring(0, eq) : stripped);
    }



    public static AgentSpec of(Type type, Path path, @Nullable String args) {
        return new AgentSpec(type, path, args);
    }

    public static AgentSpec of(Type type, Path path) {
        return new AgentSpec(type, path, null);
    }

}
