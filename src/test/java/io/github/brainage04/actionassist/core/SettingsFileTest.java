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

class SettingsFileTest {
    @Test
    void firstLaunchWritesLoadableDefaults(@TempDir Path temporaryDirectory) throws IOException {
        Path configDirectory = temporaryDirectory.resolve("nested/config");
        List<String> warnings = new ArrayList<>();

        assertEquals(MacroSettings.defaults(), SettingsFile.load(configDirectory, warnings::add));

        Properties written = new Properties();
        try (InputStream input = Files.newInputStream(configDirectory.resolve(SettingsFile.FILE_NAME))) {
            written.load(input);
        }
        assertEquals("key.ftbultimine", written.getProperty("veinMineKey"));
        assertEquals(String.join(",", MacroSettings.DEFAULT_COMPACT_ITEMS), written.getProperty("compactItems"));
        assertEquals("true", written.getProperty("statusMessages"));
        assertEquals(MacroSettings.defaults(), SettingsFile.load(configDirectory, warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void validPropertiesAreTrimmedAndBlankListEntriesSkipped(@TempDir Path configDirectory) throws IOException {
        Files.writeString(
                configDirectory.resolve(SettingsFile.FILE_NAME),
                "veinMineKey= key.veinminer \ncompactItems= minecraft:clay_ball ,, minecraft:snowball,\n"
                        + "statusMessages=FALSE\n");
        List<String> warnings = new ArrayList<>();

        MacroSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals("key.veinminer", settings.veinMineKey());
        assertEquals(List.of("minecraft:clay_ball", "minecraft:snowball"), settings.compactItems());
        assertFalse(settings.statusMessages());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void emptyCompactItemsDisablesCompaction(@TempDir Path configDirectory) throws IOException {
        Files.writeString(configDirectory.resolve(SettingsFile.FILE_NAME), "compactItems=\n");

        MacroSettings settings = SettingsFile.load(configDirectory, warning -> {});

        assertEquals(List.of(), settings.compactItems());
    }

    @Test
    void propertiesFromEarlierVersionsAreIgnoredAndMissingOnesDefault(@TempDir Path configDirectory)
            throws IOException {
        Files.writeString(
                configDirectory.resolve(SettingsFile.FILE_NAME),
                "action=attack\nactionsPerSecond=5\nsneakTapsPerSecond=8\nstatusMessages=false\n");
        List<String> warnings = new ArrayList<>();

        MacroSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(MacroSettings.DEFAULT_VEIN_MINE_KEY, settings.veinMineKey());
        assertEquals(MacroSettings.DEFAULT_COMPACT_ITEMS, settings.compactItems());
        assertFalse(settings.statusMessages());
        assertTrue(warnings.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "veinMineKey=",
                "compactItems=pebble",
                "compactItems=minecraft:",
                "compactItems=:pebble",
                "compactItems=a:b:c",
                "statusMessages=maybe"
            })
    void anyInvalidPropertyFallsBackToAllDefaults(String invalidProperty, @TempDir Path configDirectory)
            throws IOException {
        Files.writeString(
                configDirectory.resolve(SettingsFile.FILE_NAME),
                "statusMessages=false\n" + invalidProperty + "\n");
        List<String> warnings = new ArrayList<>();

        assertEquals(MacroSettings.defaults(), SettingsFile.load(configDirectory, warnings::add));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().startsWith("Could not load "));
    }

    @Test
    void unwritableConfigPathWarnsAndUsesDefaults(@TempDir Path temporaryDirectory) throws IOException {
        Path configDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(configDirectory, "occupied");
        List<String> warnings = new ArrayList<>();

        assertEquals(MacroSettings.defaults(), SettingsFile.load(configDirectory, warnings::add));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains(configDirectory.resolve(SettingsFile.FILE_NAME).toString()));
    }
}
