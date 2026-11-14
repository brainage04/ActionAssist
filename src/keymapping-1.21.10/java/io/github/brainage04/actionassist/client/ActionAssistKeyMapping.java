package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * Action Assist key mappings on Minecraft 1.21.10, where a category is a registered {@link KeyMapping.Category}
 * with a {@code ResourceLocation} id.
 */
final class ActionAssistKeyMapping {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("actionassist", "keys"));

    private ActionAssistKeyMapping() {
    }

    static KeyMapping create(String translationKey, InputConstants.Type inputType, int defaultKey) {
        return new KeyMapping(translationKey, inputType, defaultKey, CATEGORY);
    }
}
