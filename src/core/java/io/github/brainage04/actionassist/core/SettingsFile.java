package io.github.brainage04.actionassist.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

public final class SettingsFile {
    public static final String FILE_NAME = "actionassist.properties";

    private SettingsFile() {
    }

    public static AutomationSettings load(Path configDirectory, Consumer<String> warningSink) {
        Path path = configDirectory.resolve(FILE_NAME);
        AutomationSettings defaults = AutomationSettings.defaults();
        Properties properties = defaults(defaults);

        if (Files.notExists(path)) {
            save(path, properties, warningSink);
            return defaults;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return parse(properties);
        } catch (IOException | IllegalArgumentException exception) {
            warningSink.accept("Could not load " + path + ": " + exception.getMessage() + "; using defaults");
            return defaults;
        }
    }

    private static AutomationSettings parse(Properties properties) {
        return new AutomationSettings(
                AutomationSettings.Action.parse(properties.getProperty("action")),
                Integer.parseInt(properties.getProperty("actionsPerSecond")),
                Integer.parseInt(properties.getProperty("sneakTapsPerSecond")),
                Boolean.parseBoolean(properties.getProperty("statusMessages"))
        );
    }

    private static Properties defaults(AutomationSettings settings) {
        Properties properties = new Properties();
        properties.setProperty("action", settings.action().name().toLowerCase());
        properties.setProperty("actionsPerSecond", Integer.toString(settings.actionsPerSecond()));
        properties.setProperty("sneakTapsPerSecond", Integer.toString(settings.sneakTapsPerSecond()));
        properties.setProperty("statusMessages", Boolean.toString(settings.statusMessages()));
        return properties;
    }

    private static void save(Path path, Properties properties, Consumer<String> warningSink) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Action Assist client automation settings");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            warningSink.accept("Could not create " + path + ": " + exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A stale temporary file is harmless and will be replaced on the next save.
            }
        }
    }
}
