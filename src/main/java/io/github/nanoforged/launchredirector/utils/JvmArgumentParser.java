package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class JvmArgumentParser {


    private static final Set<String> VALUED_OPTIONS = Set.of(
            "-cp", "-classpath", "--class-path",
            "-p", "--module-path", "--upgrade-module-path",
            "-jar");

    public LaunchContext buildDefaultContextFromJVMArgs(List<String> inputArgs) {
        final List<String> jvmArgs = new ArrayList<>();
        final Set<AgentSpec> javaAgents = new LinkedHashSet<>();
        final Set<ModuleSpec> moduleOptions = new LinkedHashSet<>();
        final Map<String, String> systemProperties = new LinkedHashMap<>();

        for (int i = 0; i < inputArgs.size(); i++) {
            final String token = inputArgs.get(i);
            if (token == null || token.isEmpty()) {
                continue;
            }

            // "-cp a.jar;b.jar": the option's value is the next token
            if (VALUED_OPTIONS.contains(token)) {
                i++;
                continue;
            }

            // "--class-path=a.jar"
            if (isAttachedValuedOption(token)) {
                continue;
            }

            // system property: "-D" or "-d", always re-emitted as the documented uppercase form.
            // Matching only one case silently loses every property written in the other, and HotSpot
            // emits the uppercase prefix, so a lowercase-only test parses nothing but stray flags.
            if (isSystemProperty(token)) {
                handleProp(systemProperties, token);
                continue;
            }

            if (AgentSpec.isParseable(token)) {
                if (!AgentSpec.isSelf(token)) {
                    javaAgents.add(AgentSpec.parse(token));
                }
                continue;
            }

            // module directive, "--add-opens=java.base/java.lang=ALL-UNNAMED"
            final ModuleSpec directive = ModuleSpec.parseOrNull(token);
            if (directive != null) {
                moduleOptions.add(directive);
                continue;
            }

            // "--add-opens java.base/java.lang=ALL-UNNAMED": HotSpot normalizes this into the joined
            // form, but a native launcher need not.
            if (ModuleSpec.isFlag(token) && i + 1 < inputArgs.size()) {
                final ModuleSpec joined = ModuleSpec.parseOrNull(token + "=" + inputArgs.get(i + 1));
                if (joined != null) {
                    moduleOptions.add(joined);
                    i++;
                    continue;
                }
            }

            // anything else is a plain JVM option, kept verbatim in order
            jvmArgs.add(token);
        }

        return LaunchContext.builder()
                .jvmArgs(jvmArgs)
                .javaAgents(javaAgents)
                .moduleOptions(moduleOptions)
                .systemProperties(systemProperties)
                .build();
    }

    /** True for the {@code -D} / {@code -d} system property prefix, in either case. */
    private static boolean isSystemProperty(String token) {
        return token.length() >= 2
                && token.charAt(0) == '-'
                && Character.toLowerCase(token.charAt(1)) == 'd';
    }

    private static boolean isAttachedValuedOption(String token) {
        for (final String option : VALUED_OPTIONS) {
            if (token.startsWith(option + "=")) {
                return true;
            }
        }
        return false;
    }

    static void handleProp(Map<String, String> systemProperties, String token) {
        String kv = token.substring(2);
        if (kv.isEmpty()) {
            return;
        }
        int eq = kv.indexOf('=');
        if (eq >= 0) {
            systemProperties.put(kv.substring(0, eq), kv.substring(eq + 1));
        } else {
            systemProperties.put(kv, "");
        }
    }
}
