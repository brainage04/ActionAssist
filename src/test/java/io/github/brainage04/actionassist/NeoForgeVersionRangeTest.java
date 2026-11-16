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
 * The one metadata file serves every target, so the NeoForge range is rendered per target: {@code [0,)} unless the
 * target has a floor in {@code neoForgeFloors}. A floor must not be higher than the NeoForge its players run, so only
 * the target whose pack version is known declares one. The unit test classpath has no NeoForge, so this guard reads
 * the metadata and the build script.
 */
class NeoForgeVersionRangeTest {
    private static final Path TOML = Path.of("src/neoforge/resources/META-INF/neoforge.mods.toml");
    private static final Path BUILD = Path.of("build.gradle");
    private static final Pattern NEOFORGE_DEPENDENCY =
            Pattern.compile("modId=\"neoforge\"[^\\[]*?versionRange=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern TARGET =
            Pattern.compile("(mc\\d+)\\s*:\\s*\\[[^\\]]*?minecraft:\\s*\"([^\"]+)\"");
    private static final Pattern FLAT_MAP = Pattern.compile("ext\\.neoForgeFloors\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern FLOOR = Pattern.compile("(mc\\d+)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPANDED =
            Pattern.compile("neoforge_floor\\s*:\\s*(rootProject\\.)?neoForgeFloor");
    private static final Pattern FLOOR_ASSIGNMENT =
            Pattern.compile("def\\s+neoForgeFloor\\s*=\\s*([^\\n]*)");

    @Test
    void theRangeStartsAtTheFloorATargetDeclaresAndAtZeroOtherwise() throws IOException {
        assertTrue(Files.isRegularFile(TOML), "Run from the repository root; no " + TOML.toAbsolutePath());
        Matcher dependency = NEOFORGE_DEPENDENCY.matcher(Files.readString(TOML));
        assertTrue(dependency.find(), "No neoforge dependency in " + TOML);
        String range = dependency.group(1);

        Map<String, String> targets = targets();
        assertTrue(targets.size() >= 8, "Expected every target in " + BUILD + ", found " + targets);
        Map<String, String> floors = floors();
        assertTrue(floors.keySet().stream().allMatch(targets::containsKey),
                "A floor names no target: " + floors.keySet() + " against " + targets.keySet());

        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> target : targets.entrySet()) {
            String floor = floors.getOrDefault(target.getKey(), "0");
            String expected = "[" + floor + ",)";
            String rendered = range.replace("${neoforge_floor}", floor);
            if (!expected.equals(rendered)) {
                wrong.add(target.getKey() + " declares NeoForge " + floor + ", so its range is " + expected
                        + ", but the metadata renders " + rendered);
            }
        }
        assertEquals(List.of(), wrong);
    }

    /**
     * ATM10 To the Sky 2.3.1 (CurseForge file 9368148) runs NeoForge 21.1.250, and it is the pack the mod targets, so
     * the 1.21.1 floor must admit that build. Every other target keeps {@code [0,)}: no API the jar calls is missing
     * from that line's earliest NeoForge, so a floor there would refuse players for no reason.
     */
    @Test
    void theOnlyFloorIsTheBuildTheTargetPackRuns() throws IOException {
        assertEquals(Map.of("mc1211", "21.1.244"), floors(), "Only 1.21.1 declares a NeoForge floor");

        assertEquals("1.21.1", targets().get("mc1211"), "mc1211 must stay the 1.21.1 target");
        String floor = floors().get("mc1211");
        assertTrue(
                compare(floor, "21.1.250") <= 0,
                "ATM10 To the Sky runs NeoForge 21.1.250, so the floor " + floor + " would refuse it");
    }

    @Test
    void theBuildFillsTheRangeInFromTheTarget() throws IOException {
        assertTrue(EXPANDED.matcher(Files.readString(BUILD)).find(),
                "processResources must expand neoforge_floor, or the range stays literal");
    }

    /**
     * The map is only a declaration: the range is what {@code processResources} renders, and it renders whatever
     * {@code neoForgeFloor} holds. A constant there (or a fallback of the compiled-against build) leaves the map
     * unread and every target back on a floor nobody needs, while the two tests above still pass.
     */
    @Test
    void theBuildReadsTheFloorFromTheMapAndDefaultsToZero() throws IOException {
        Matcher assignment = FLOOR_ASSIGNMENT.matcher(Files.readString(BUILD));
        assertTrue(assignment.find(), "No 'def neoForgeFloor = ...' in " + BUILD);
        String expression = assignment.group(1).trim();
        assertTrue(
                expression.contains("neoForgeFloors[targetId]"),
                "neoForgeFloor must come from neoForgeFloors[targetId], not " + expression);
        assertTrue(
                expression.contains("?: \"0\""),
                "A target with no floor must render [0,), so the fallback must be \"0\", not " + expression);
    }

    /** Compares two NeoForge versions numerically, so "21.1.244" is below "21.1.250". */
    private static int compare(String left, String right) {
        String[] a = left.split("[.-]");
        String[] b = right.split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int difference = Integer.compare(
                    i < a.length ? Integer.parseInt(a[i]) : 0,
                    i < b.length ? Integer.parseInt(b[i]) : 0);
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    /** The Minecraft version of each target, from {@code minecraftTargets} in the build script. */
    private static Map<String, String> targets() throws IOException {
        Map<String, String> targets = new LinkedHashMap<>();
        Matcher matcher = TARGET.matcher(Files.readString(BUILD));
        while (matcher.find()) {
            targets.put(matcher.group(1), matcher.group(2));
        }
        return targets;
    }

    /** The declared floors, from {@code neoForgeFloors} in the build script. */
    private static Map<String, String> floors() throws IOException {
        Matcher map = FLAT_MAP.matcher(Files.readString(BUILD));
        assertTrue(map.find(), "No ext.neoForgeFloors in " + BUILD);
        Map<String, String> floors = new LinkedHashMap<>();
        Matcher floor = FLOOR.matcher(map.group(1));
        while (floor.find()) {
            floors.put(floor.group(1), floor.group(2));
        }
        return floors;
    }
}
