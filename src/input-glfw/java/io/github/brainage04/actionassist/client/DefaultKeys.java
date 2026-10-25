package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Default bindings in GLFW codes, which Minecraft uses up to 26.2. */
final class DefaultKeys {
    static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;
    static final int PEBBLE_MACRO_BUTTON = GLFW.GLFW_MOUSE_BUTTON_5;
    static final int CROP_MACRO_BUTTON = GLFW.GLFW_MOUSE_BUTTON_4;
    static final int SELECT_CONTAINER_KEY = GLFW.GLFW_KEY_F6;

    private DefaultKeys() {
    }
}
