package io.github.nanoforged.launchredirector.api.model;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ModuleSpec(String option, String path, String target) {

    public enum Type {
        ADD_EXPORTS("--add-exports"),
        ADD_OPENS("--add-opens"),
        ADD_READS("--add-reads"),
        PATCH_MODULE("--patch-module");
        @Getter
        private final String flag;

        private static final Map<String, Type> FLAG_MAP =
                Arrays.stream(values()).collect(Collectors.toMap(
                        Type::getFlag,
                        Function.identity()
                ));

        Type(String flag) { this.flag = flag; }

        public static Type fromFlag(String flag) {
            return flag == null ? null : FLAG_MAP.get(flag);
        }

        public static boolean isValidOption(String argument) {
            return fromFlag(argument) != null;
        }

        @Override
        public String toString() {
            return flag;
        }
    }

    public Type getType() {
        return Type.fromFlag(option);
    }

    public String toArgument() {
        return String.format("%s=%s=%s", option, path, target);
    }

    /** True when the token is exactly a module directive flag, e.g. {@code --add-opens}. */
    public static boolean isFlag(String argument) {
        return Type.fromFlag(argument) != null;
    }

    /**
     * True when the token carries a module directive, either exactly ({@code --add-opens}) or joined
     * to its value with '=' ({@code --add-opens=java.base/java.lang=ALL-UNNAMED}). A plain prefix
     * match would also accept {@code --add-opensomething}.
     */
    public static boolean isParseable(String argument) {
        if (argument == null) {
            return false;
        }
        for (Type type : Type.values()) {
            String flag = type.getFlag();
            if (argument.startsWith(flag)
                    && (argument.length() == flag.length() || argument.charAt(flag.length()) == '=')) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a module directive, returning {@code null} instead of throwing when the token is not
     * one: the JVM argument parser must pass unrecognized arguments through to the child rather than
     * abort the relaunch, so it cannot use the strict {@link #parse(String)}.
     */
    public static ModuleSpec parseOrNull(String argument) {
        if (!isParseable(argument) || isFlag(argument)) {
            return null;
        }
        int firstEq = argument.indexOf('=');
        if (firstEq < 0) {
            return null;
        }
        int secondEq = argument.indexOf('=', firstEq + 1);
        if (secondEq < 0) {
            return null;
        }
        return new ModuleSpec(
                argument.substring(0, firstEq),
                argument.substring(firstEq + 1, secondEq),
                argument.substring(secondEq + 1));
    }

    public static ModuleSpec parse(String fullArg) {
        int firstEq = fullArg.indexOf('=');
        if (firstEq < 0) throw new IllegalArgumentException("Missing '=' in module directive: " + fullArg);

        String modifier = fullArg.substring(0, firstEq);
        String rest = fullArg.substring(firstEq + 1);

        int secondEq = rest.indexOf('=');
        if (secondEq < 0) throw new IllegalArgumentException("Missing second '=' in module directive: " + fullArg);

        String path = rest.substring(0, secondEq);
        String target = rest.substring(secondEq + 1);
        return new ModuleSpec(modifier, path, target);
    }


    public static ModuleSpec of(String option, String path, String target) {
        return new ModuleSpec(option, path, target);
    }

    public static ModuleSpec of(AgentSpec.Type option, String path, String target) {
        return new ModuleSpec(option.getFlag(), path, target);
    }
}