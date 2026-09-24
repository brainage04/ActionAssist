package io.github.brainage04.actionassist.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public final class SettingsFile {
    public static final String FILE_NAME = "actionassist.properties";
    static final String VEIN_MINE_KEY = "veinMineKey";
    static final String COMPACT_ITEMS = "compactItems";
    static final String STATUS_MESSAGES = "statusMessages";

    private SettingsFile() {}

    public static MacroSettings load(Path configDirectory, Consumer<String> warningSink) {
        Path path = configDirectory.resolve(FILE_NAME);
        MacroSettings defaults = MacroSettings.defaults();
        Properties properties = defaults(defaults);

        if (Files.notExists(path)) {
            save(path, properties, warningSink);
            return defaults;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return parse(properties);
        } catch (IOException | IllegalArgumentException exception) {
            warningSink.accept(
                    "Could not load " + path + ": " + exception.getMessage() + "; using defaults");
            return defaults;
        }
    }

    private static MacroSettings parse(Properties properties) {
        return new MacroSettings(
                properties.getProperty(VEIN_MINE_KEY).strip(),
                parseList(properties.getProperty(COMPACT_ITEMS)),
                parseBoolean(properties.getProperty(STATUS_MESSAGES)));
    }

    private static List<String> parseList(String value) {
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String stripped = item.strip();
            if (!stripped.isEmpty()) {
                items.add(stripped);
            }
        }
        return items;
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value.strip())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.strip())) {
            return false;
        }
        throw new IllegalArgumentException(STATUS_MESSAGES + " must be true or false");
    }

    private static Properties defaults(MacroSettings settings) {
        Properties properties = new Properties();
        properties.setProperty(VEIN_MINE_KEY, settings.veinMineKey());
        properties.setProperty(COMPACT_ITEMS, String.join(",", settings.compactItems()));
        properties.setProperty(STATUS_MESSAGES, Boolean.toString(settings.statusMessages()));
        return properties;
    }

    private static void save(Path path, Properties properties, Consumer<String> warningSink) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Action Assist macro settings");
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
