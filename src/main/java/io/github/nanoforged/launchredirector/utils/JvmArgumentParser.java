package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class JvmArgumentParser {



    public LaunchContext buildDefaultContextFromJVMArgs(List<String> inputArgs) {
        final LinkedHashSet<String> jvmArgs = new LinkedHashSet<>();
        final LinkedHashSet<AgentSpec> javaAgents = new LinkedHashSet<>();
        final LinkedHashSet<ModuleSpec> moduleSpecs = new LinkedHashSet<>();
        final Map<String, String> systemProperties = new HashMap<>();

        for (String token : inputArgs) {
            if (token.toLowerCase().startsWith("-d")) {
                handleProp(systemProperties, token);
                continue;
            }

            if (AgentSpec.isParseable(token)) {
                if (!AgentSpec.isSelf(token)) {
                    javaAgents.add(AgentSpec.parse(token));
                }
                continue;
            }

            if (token.equals("-cp") || token.equals("-classpath")) {
                continue;
            }

            if (ModuleSpec.isParseable(token)) {
                moduleSpecs.add(ModuleSpec.parse(token));
                continue;
            }


            jvmArgs.add(token);
        }

        return LaunchContext.builder()
                .jvmArgs(jvmArgs.stream().toList())
                .javaAgents(javaAgents.stream().toList())
                .moduleOptions(moduleSpecs.stream().toList())
                .systemProperties(systemProperties)
                .build();
    }

    static void handleProp(Map<String, String> systemProperties, String token) {
        String kv = token.substring(2);
        int eq = kv.indexOf('=');
        if (eq >= 0) {
            systemProperties.put(kv.substring(0, eq), kv.substring(eq + 1));
        } else {
            systemProperties.put(kv, "");
        }
    }


}
