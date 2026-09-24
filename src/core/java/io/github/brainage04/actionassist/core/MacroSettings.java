package io.github.brainage04.actionassist.core;

import java.util.List;
import java.util.Objects;

public record MacroSettings(String veinMineKey, List<String> compactItems, boolean statusMessages) {
    /** FTB Ultimine's key mapping name. */
    public static final String DEFAULT_VEIN_MINE_KEY = "key.ftbultimine";

    /** Ex Deorum pebbles; each has a 2x2 recipe producing its stone block. */
    public static final List<String> DEFAULT_COMPACT_ITEMS = List.of(
            "exdeorum:stone_pebble",
            "exdeorum:andesite_pebble",
            "exdeorum:basalt_pebble",
            "exdeorum:blackstone_pebble",
            "exdeorum:calcite_pebble",
            "exdeorum:deepslate_pebble",
            "exdeorum:diorite_pebble",
            "exdeorum:granite_pebble",
            "exdeorum:tuff_pebble");

    public MacroSettings {
        Objects.requireNonNull(veinMineKey, "veinMineKey");
        if (veinMineKey.isBlank()) {
            throw new IllegalArgumentException("veinMineKey must not be blank");
        }
        compactItems = List.copyOf(compactItems);
        for (String item : compactItems) {
            int separator = item.indexOf(':');
            if (separator <= 0 || separator == item.length() - 1 || item.indexOf(':', separator + 1) >= 0) {
                throw new IllegalArgumentException("compactItems entry '" + item + "' is not a namespaced item id");
            }
        }
    }

    public static MacroSettings defaults() {
        return new MacroSettings(DEFAULT_VEIN_MINE_KEY, DEFAULT_COMPACT_ITEMS, true);
    }
}
