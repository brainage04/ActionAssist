package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Action Assist key mappings on Minecraft 1.21.1 to 1.21.8, where a category is a translation key. */
final class ActionAssistKeyMapping {
    private static final String CATEGORY = "key.categories.actionassist";

    private ActionAssistKeyMapping() {
    }

    static KeyMapping create(String translationKey, InputConstants.Type inputType, int defaultKey) {
        return new KeyMapping(translationKey, inputType, defaultKey, CATEGORY);
    }
}
