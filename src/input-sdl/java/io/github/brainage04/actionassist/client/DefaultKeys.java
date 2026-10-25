package io.github.brainage04.actionassist.client;

import com.mojang.blaze3d.platform.InputConstants;

/** Default bindings in SDL3 codes, which Minecraft uses from 26.3: 1-based mouse buttons and scancodes. */
final class DefaultKeys {
    static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
    static final int PEBBLE_MACRO_BUTTON = InputConstants.MOUSE_BUTTON_5;
    static final int CROP_MACRO_BUTTON = InputConstants.MOUSE_BUTTON_4;
    static final int SELECT_CONTAINER_KEY = InputConstants.KEY_F6;

    private DefaultKeys() {
    }
}
