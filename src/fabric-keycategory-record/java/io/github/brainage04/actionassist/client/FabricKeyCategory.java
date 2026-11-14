package io.github.brainage04.actionassist.client;

import net.minecraft.client.KeyMapping;

public final class FabricKeyCategory {
    private FabricKeyCategory() {
    }

    public static void register() {
        KeyMapping.Category.register(ActionAssistKeyMapping.CATEGORY.id());
    }
}
