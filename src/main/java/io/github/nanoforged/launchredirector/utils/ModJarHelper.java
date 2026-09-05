package io.github.nanoforged.launchredirector.utils;

import io.github.nanoforged.launchredirector.api.ILaunchConfigPlugin;
import lombok.experimental.UtilityClass;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


@UtilityClass
public final class ModJarHelper {
    private static final String SPI_SERVICE_PATH = "META-INF/services/" + ILaunchConfigPlugin.class.getName();
    private static final String MODS_PATH_PROP = "com.fs.starfarer.settings.paths.mods";


    public static List<ModSpec> getModSpecs(Path workDir)  {
        final Path modsDir = resolveModsDir(workDir);

        final List<Path> modSubDirs = getModSubDirs(modsDir);

        final Set<String> enabledIds = getEnabledModIds(modsDir);

        return getModSpecs(modSubDirs,enabledIds);
    }


    private static List<Path> getModSubDirs(Path modsDir) {
       final List<Path> found = new ArrayList<>();

        if (modsDir == null || !Files.isDirectory(modsDir)) {
            Log.warn("[LaunchRedirector] Mods directory not found: " + modsDir);
            return found;
        }

        try (var stream = Files.list(modsDir)) {
            stream.filter(Files::isDirectory).forEach(found::add);
        } catch (IOException e) {
            Log.error("[LaunchRedirector] Failed to list mods directory " + modsDir, e);
            return found;
        }
        return found;
    }


    private static List<ModSpec> getModSpecs(List<Path> modSubDirs, Set<String> enabledIds) {
        return modSubDirs.stream()
                .map(dir -> processModDir(dir, enabledIds))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ModSpec> processModDir(Path dir, Set<String> enabledIds) {
        Path info = dir.resolve("mod_info.json");
        if (!Files.isRegularFile(info)) {
            return Optional.empty();
        }

        try {
            String raw = Files.readString(info);
            String cleaned = preprocessJson(raw);
            JSONObject obj =new JSONObject(new JSONTokener(cleaned));

            String id = obj.getString("id");
            if (id == null || !enabledIds.contains(id)) {
                return Optional.empty();
            }

            JSONArray jarsArr = obj.getJSONArray("jars");
            if (jarsArr == null) {
                Log.warn("[LaunchRedirector] Missing 'jars' array in " + info);
                return Optional.empty();
            }

            List<Path> validJars = collectSpiJars(dir, jarsArr);
            if (validJars.isEmpty()) {
                return Optional.empty();
            }

            Log.info("[LaunchRedirector] Found Enabled Mod: " + id);
            return Optional.of(new ModSpec(id, validJars));
        } catch (IOException e) {
            Log.error("[LaunchRedirector] Failed to read mod_info file " + info, e);
            return Optional.empty();
        } catch (Exception e) {
            Log.error("[LaunchRedirector] Unexpected error processing " + info, e);
            return Optional.empty();
        }
    }


    private static String preprocessJson(String json) {
        return json.lines()
                .map(line -> {
                    int idx = line.indexOf("#");
                    if (idx == -1) {
                        return line;
                    }
                    return line.substring(0, idx).trim();
                })
                .collect(Collectors.joining("\n"));
    }


    private static List<Path> collectSpiJars(Path modDir, JSONArray jarsArr) {
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < jarsArr.length(); i++) {
            if (!(jarsArr.get(i) instanceof String jarName)) continue;
            Path jar = modDir.resolve(jarName);
            if (Files.isRegularFile(jar) && hasSpiService(jar)) {
                result.add(jar);
            }
        }
        return result;
    }

    private static Set<String> getEnabledModIds(Path modsDir) {
        Path jsonPath = modsDir.resolve("enabled_mods.json");
        if (!Files.isReadable(jsonPath)) {
            Log.warn("[LaunchRedirector] enabled_mods.json not found or not readable: " + jsonPath);
            return Collections.emptySet();
        }

        try (FileReader reader = new FileReader(jsonPath.toFile())) {
            JSONObject obj = new JSONObject(new JSONTokener(reader));
            JSONArray arr = obj.getJSONArray("enabledMods");

            if (arr == null) {
                Log.warn("[LaunchRedirector] Missing 'enabledMods' array in " + jsonPath);
                return Collections.emptySet();
            }

            final Set<String> result = new HashSet<>();

            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }

            return result;
        } catch (IOException e) {
            Log.warn("[LaunchRedirector] Failed to read " + jsonPath + ": " + e);
            return Collections.emptySet();
        }

    }


    private static boolean hasSpiService(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getJarEntry(SPI_SERVICE_PATH) != null;
        } catch (IOException e) {
            Log.warn("[LaunchRedirector] Cannot open mod jar " + jar + ": " + e);
            return false;
        }
    }


    private static Path resolveModsDir(Path workingDir) {
        String prop = System.getProperty(MODS_PATH_PROP);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return workingDir != null ? workingDir.resolve("mods") : Path.of("mods");
    }

    public record ModSpec(String id, List<Path> jars) {
    }
}
