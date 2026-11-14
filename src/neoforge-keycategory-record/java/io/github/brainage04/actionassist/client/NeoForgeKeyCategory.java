package io.github.brainage04.actionassist.client;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class NeoForgeKeyCategory {
    private NeoForgeKeyCategory() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(ActionAssistKeyMapping.CATEGORY);
    }
}
