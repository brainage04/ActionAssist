package io.github.brainage04.actionassist.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsFileTest {
    @Test
    void missingPropertiesRetainTheirDefaults(@TempDir Path configDirectory) throws IOException {
        Files.writeString(configDirectory.resolve(SettingsFile.FILE_NAME), "action=attack\n");
        List<String> warnings = new ArrayList<>();

        AutomationSettings settings = SettingsFile.load(configDirectory, warnings::add);

        assertEquals(AutomationSettings.Action.ATTACK, settings.action());
        assertEquals(AutomationSettings.defaults().actionsPerSecond(), settings.actionsPerSecond());
        assertEquals(AutomationSettings.defaults().sneakTapsPerSecond(), settings.sneakTapsPerSecond());
        assertEquals(AutomationSettings.defaults().statusMessages(), settings.statusMessages());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidPropertiesFallBackToDefaults(@TempDir Path configDirectory) throws IOException {
        Files.writeString(configDirectory.resolve(SettingsFile.FILE_NAME), "actionsPerSecond=0\n");
        List<String> warnings = new ArrayList<>();

        assertEquals(AutomationSettings.defaults(), SettingsFile.load(configDirectory, warnings::add));
        assertEquals(1, warnings.size());
    }
}
