package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class KeyMappingFactory {
    private KeyMappingFactory() {
    }

    public static ActionAssistKeys create() {
        return new ActionAssistKeys(
                ActionAssistKeyMapping.create(
                        "key.actionassist.pebble_macro", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5),
                ActionAssistKeyMapping.create(
                        "key.actionassist.crop_macro", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4),
                ActionAssistKeyMapping.create(
                        "key.actionassist.select_container", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6)
        );
    }
}
