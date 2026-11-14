package io.github.brainage04.actionassist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The mod must reach game and loader members through compiled references or by exact name. Taking the first member
 * of a matching shape from {@code getMethods()} and its relatives depends on an order the JVM leaves unspecified, and
 * other mods add members of the same shape (register A-0310: Fabric API's {@code Gui.getTickingScreen()}). The unit
 * test classpath has no Minecraft, so this guard reads the mod's sources instead of running the lookups.
 */
class MemberLookupSourceTest {
    private static final Pattern MEMBER_ENUMERATION = Pattern.compile(
            "\\.\\s*get(Declared)?(Methods|Fields|Constructors)\\s*\\(");
    private static final Path SOURCES = Path.of("src");
    private static final List<Path> NOT_SHIPPED = List.of(SOURCES.resolve("test"), SOURCES.resolve("gametest"));

    @Test
    void modSourcesNeverEnumerateMembers() throws IOException {
        assertTrue(Files.isDirectory(SOURCES.resolve("client")),
                "Run from the repository root; no " + SOURCES.toAbsolutePath().resolve("client"));
        List<String> findings = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> NOT_SHIPPED.stream().noneMatch(path::startsWith))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                if (MEMBER_ENUMERATION.matcher(lines.get(index)).find()) {
                    findings.add(file + ":" + (index + 1) + ": " + lines.get(index).strip());
                }
            }
        }
        assertEquals(List.of(), findings);
    }

    @Test
    void guardFlagsEnumerationsButNotJavadocLinks() {
        assertTrue(MEMBER_ENUMERATION.matcher("for (Method method : helper.getMethods()) {").find());
        assertTrue(MEMBER_ENUMERATION.matcher("Constructor<?> c = type.getConstructors()[0];").find());
        assertTrue(MEMBER_ENUMERATION.matcher("for (Field field : type.getDeclaredFields()) {").find());
        assertFalse(MEMBER_ENUMERATION.matcher(" * order ({@link Class#getMethods()}), so").find());
        assertFalse(MEMBER_ENUMERATION.matcher("lookup.findGetter(Minecraft.class, \"screen\", Screen.class)").find());
    }
}
