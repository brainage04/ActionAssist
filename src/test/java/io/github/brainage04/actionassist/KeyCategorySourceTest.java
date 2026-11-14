package io.github.brainage04.actionassist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * NeoForge constructs mods in parallel, and vanilla's {@code KeyMapping.Category.register} adds to an unsynchronised
 * list, so NeoForge deprecates it for {@code RegisterKeyMappingsEvent#registerCategory}. Only Fabric-only sources may
 * call it. The unit test classpath has no Minecraft, so this guard reads the sources.
 */
class KeyCategorySourceTest {
    private static final Pattern CATEGORY_REGISTER = Pattern.compile("\\bCategory\\s*\\.\\s*register\\s*\\(");
    private static final Path SOURCES = Path.of("src");

    @Test
    void sourcesBuiltForNeoForgeNeverRegisterCategoriesWithVanilla() throws IOException {
        assertTrue(Files.isDirectory(SOURCES.resolve("neoforge")),
                "Run from the repository root; no " + SOURCES.toAbsolutePath().resolve("neoforge"));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(KeyCategorySourceTest::builtForNeoForge)
                    .sorted()
                    .toList();
        }
        List<String> findings = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file);
            Matcher matcher = CATEGORY_REGISTER.matcher(source);
            while (matcher.find()) {
                long line = source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
                findings.add(file + ":" + line);
            }
        }
        assertEquals(List.of(), findings);
    }

    @Test
    void guardFlagsVanillaCategoryRegistration() {
        assertTrue(CATEGORY_REGISTER.matcher("KeyMapping.Category.register(Identifier.parse(\"a:b\"))").find());
        assertTrue(CATEGORY_REGISTER.matcher("Category\n        .register(id)").find());
        assertFalse(CATEGORY_REGISTER.matcher("event.registerCategory(CATEGORY)").find());
    }

    private static boolean builtForNeoForge(Path file) {
        String root = file.getName(1).toString();
        return !root.equals("test") && !root.equals("gametest") && !root.startsWith("fabric");
    }
}
