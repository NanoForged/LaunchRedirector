package io.github.nanoforged.launchredirector.api.model;

import io.github.nanoforged.launchredirector.LaunchRedirector;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;

public record AgentSpec(Type type, Path path,@Nullable String args) {
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
        String base = type.getFlag() + path.toAbsolutePath();
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
            return new AgentSpec(type, Path.of(stripped.substring(0, eq)), stripped.substring(eq + 1));
        } else {
            return new AgentSpec(type, Path.of(stripped), null);
        }
    }

    public static boolean isSelf(String javaagentArg) {
        return javaagentArg.toLowerCase().contains(LaunchRedirector.class.getSimpleName().toLowerCase());
    }


    public static AgentSpec of(Type type, Path path, @Nullable String args) {
        return new AgentSpec(type, path, args);
    }

    public static AgentSpec of(Type type, Path path) {
        return new AgentSpec(type, path, null);
    }

}
