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
 * NeoForge constructs every {@code @Mod} class on a dedicated server as well unless the annotation limits it to
 * {@code Dist.CLIENT}. Action Assist's entrypoint creates key mappings, and the dedicated server jar has no
 * {@code net.minecraft.client} classes, so a server with the mod installed would fail to start. The unit test
 * classpath has no NeoForge, so this guard reads the entrypoint sources instead.
 */
class NeoForgeEntrypointSourceTest {
    private static final Pattern MOD_ANNOTATION = Pattern.compile("@Mod\\s*\\(([^)]*)\\)");
    private static final Pattern CLIENT_ONLY = Pattern.compile("\\bdist\\s*=\\s*\\{?\\s*Dist\\.CLIENT\\s*}?");
    private static final Path SOURCES = Path.of("src");

    @Test
    void everyNeoForgeEntrypointIsClientOnly() throws IOException {
        assertTrue(Files.isDirectory(SOURCES.resolve("neoforge")),
                "Run from the repository root; no " + SOURCES.toAbsolutePath().resolve("neoforge"));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getName(1).toString().startsWith("neoforge"))
                    .sorted()
                    .toList();
        }
        List<String> entrypoints = new ArrayList<>();
        List<String> notClientOnly = new ArrayList<>();
        for (Path file : files) {
            Matcher matcher = MOD_ANNOTATION.matcher(Files.readString(file));
            while (matcher.find()) {
                entrypoints.add(file.toString());
                if (!CLIENT_ONLY.matcher(matcher.group(1)).find()) {
                    notClientOnly.add(file + ": " + matcher.group().strip());
                }
            }
        }
        assertFalse(entrypoints.isEmpty(), "No @Mod entrypoint under " + SOURCES.resolve("neoforge*"));
        assertEquals(List.of(), notClientOnly);
    }

    @Test
    void guardAcceptsOnlyAClientDist() {
        assertTrue(CLIENT_ONLY.matcher("value = ActionAssist.MOD_ID, dist = Dist.CLIENT").find());
        assertTrue(CLIENT_ONLY.matcher("value = \"actionassist\", dist = { Dist.CLIENT }").find());
        assertFalse(CLIENT_ONLY.matcher("ActionAssist.MOD_ID").find());
        assertFalse(CLIENT_ONLY.matcher("value = ActionAssist.MOD_ID, dist = Dist.DEDICATED_SERVER").find());
    }
}
