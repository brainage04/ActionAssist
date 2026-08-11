package io.github.brainage04.actionassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsFileCoverageTest {
    @Test
    void firstLaunchCreatesDefaultFile(@TempDir Path temporaryDirectory) throws IOException {
        Path configDirectory = temporaryDirectory.resolve("nested/config");
        List<String> warnings = new ArrayList<>();

        AutomationSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(AutomationSettings.defaults(), settings);
        assertTrue(warnings.isEmpty());
        Properties properties = new Properties();
        try (InputStream input =
                Files.newInputStream(configDirectory.resolve(SettingsFile.FILE_NAME))) {
            properties.load(input);
        }
        assertEquals("use", properties.getProperty("action"));
        assertEquals("20", properties.getProperty("actionsPerSecond"));
        assertEquals("8", properties.getProperty("sneakTapsPerSecond"));
        assertEquals("true", properties.getProperty("statusMessages"));
    }

    @Test
    void completeValidPropertiesAreLoaded(@TempDir Path configDirectory) throws IOException {
        Files.writeString(
                configDirectory.resolve(SettingsFile.FILE_NAME),
                "action=attack\nactionsPerSecond=1\nsneakTapsPerSecond=10\n"
                        + "statusMessages=false\n");
        List<String> warnings = new ArrayList<>();

        AutomationSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(AutomationSettings.Action.ATTACK, settings.action());
        assertEquals(1, settings.actionsPerSecond());
        assertEquals(10, settings.sneakTapsPerSecond());
        assertFalse(settings.statusMessages());
        assertTrue(warnings.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "action=break",
                "actionsPerSecond=0",
                "actionsPerSecond=21",
                "actionsPerSecond=fast",
                "sneakTapsPerSecond=0",
                "sneakTapsPerSecond=11",
                "sneakTapsPerSecond=fast",
                "statusMessages=maybe"
            })
    void everyInvalidPropertyFallsBackToAllDefaults(
            String invalidProperty, @TempDir Path configDirectory) throws IOException {
        Files.writeString(
                configDirectory.resolve(SettingsFile.FILE_NAME), invalidProperty + "\n");
        List<String> warnings = new ArrayList<>();

        AutomationSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(AutomationSettings.defaults(), settings);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().startsWith("Could not load "));
    }

    @Test
    void unwritableConfigPathWarnsAndUsesDefaults(@TempDir Path temporaryDirectory)
            throws IOException {
        Path configDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(configDirectory, "occupied");
        List<String> warnings = new ArrayList<>();

        AutomationSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(AutomationSettings.defaults(), settings);
        assertEquals(1, warnings.size());
        assertTrue(
                warnings
                        .getFirst()
                        .contains(configDirectory.resolve(SettingsFile.FILE_NAME).toString()));
    }
}
