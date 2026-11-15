package io.github.brainage04.actionassist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Each NeoForge jar is compiled against one NeoForge build, so it must refuse an older one: the API it calls may not
 * exist there. The range is {@code [<the build it compiles against>,)}, the same shape every other workspace mod uses,
 * and it is filled in per target because one metadata file serves every target. The unit test classpath has no
 * NeoForge, so this guard reads the metadata and the build script.
 */
class NeoForgeVersionRangeTest {
    private static final Path TOML = Path.of("src/neoforge/resources/META-INF/neoforge.mods.toml");
    private static final Path BUILD = Path.of("build.gradle");
    private static final Pattern NEOFORGE_DEPENDENCY = Pattern.compile(
            "modId=\"neoforge\"[^\\[]*?versionRange=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern TARGET = Pattern.compile(
            "(mc\\d+)\\s*:\\s*\\[[^\\]]*?neoForge:\\s*\"([^\"]+)\"");
    private static final Pattern EXPANDED = Pattern.compile(
            "neoforge_version\\s*:\\s*target\\.neoForge");

    @Test
    void theNeoForgeRangeStartsAtTheBuildEachJarCompilesAgainst() throws IOException {
        assertTrue(Files.isRegularFile(TOML), "Run from the repository root; no " + TOML.toAbsolutePath());
        Matcher dependency = NEOFORGE_DEPENDENCY.matcher(Files.readString(TOML));
        assertTrue(dependency.find(), "No neoforge dependency in " + TOML);
        String range = dependency.group(1);

        Map<String, String> compiledAgainst = compiledAgainst();
        assertTrue(compiledAgainst.size() >= 8, "Expected every target in " + BUILD + ", found " + compiledAgainst);

        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> target : compiledAgainst.entrySet()) {
            String expected = "[" + target.getValue() + ",)";
            String rendered = range.replace("${neoforge_version}", target.getValue());
            if (!expected.equals(rendered)) {
                wrong.add(target.getKey() + " compiles against " + target.getValue() + ", so it needs " + expected
                        + ", but the metadata renders " + rendered);
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void theBuildFillsTheRangeInFromTheTarget() throws IOException {
        assertTrue(EXPANDED.matcher(Files.readString(BUILD)).find(),
                "processResources must expand neoforge_version from target.neoForge, or the range stays literal");
    }

    /** The NeoForge build each target compiles against, from {@code minecraftTargets} in the build script. */
    private static Map<String, String> compiledAgainst() throws IOException {
        Map<String, String> targets = new LinkedHashMap<>();
        Matcher matcher = TARGET.matcher(Files.readString(BUILD));
        while (matcher.find()) {
            targets.put(matcher.group(1), matcher.group(2));
        }
        return targets;
    }
}
