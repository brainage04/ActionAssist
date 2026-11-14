package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Action Assist key mappings on Minecraft 1.21.11 and 26.x, where a category is a registered
 * {@link KeyMapping.Category} with an {@code Identifier} id.
 */
final class ActionAssistKeyMapping {
    /** Registered by the loader: {@code FabricKeyCategory} or {@code NeoForgeKeyCategory}. */
    static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("actionassist", "keys"));

    private ActionAssistKeyMapping() {
    }

    static KeyMapping create(String translationKey, InputConstants.Type inputType, int defaultKey) {
        return new KeyMapping(translationKey, inputType, defaultKey, CATEGORY);
    }
}
