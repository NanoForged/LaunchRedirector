package io.github.nanoforged.launchredirector.api;

import io.github.nanoforged.launchredirector.api.model.AgentSpec;
import io.github.nanoforged.launchredirector.api.model.ModuleSpec;
import io.github.nanoforged.launchredirector.api.model.Resolution;
import lombok.*;

import java.nio.file.Path;
import java.util.*;

@Data
@Builder
public final class LaunchContext {

    private final Path javaHome;  //Java Runtime java.home

    @Singular("jvmArg")
    private final List<String> jvmArgs;         // -X, -XX

    @Singular("classpathEntry")
    private final Set<Path> classpath;         // classpath

    @Singular("javaAgent")
    private final Set<AgentSpec> javaAgents;

    @Singular("moduleOption")
    private final Set<ModuleSpec> moduleOptions; //JMPS options


    @Singular("programArg")
    private final List<String> programArgs;


    private final String mainClass;             // com.fs.starfarer.StarfarerLauncher

    private final Path workingDirectory;        // user.dir

    @Singular("env")
    private final Map<String, String> environment; // idk

    @Singular("systemProperty")
    private final Map<String, String> systemProperties; // -Dkey=value

    private final String gameVersion;
    private final Resolution resolution;
    private final boolean fullScreen;
    private final boolean soundEnabled;
    private final boolean launchDirect;


    public MutableLaunchContext toMutable() {
        return new MutableLaunchContext(this);
    }


}