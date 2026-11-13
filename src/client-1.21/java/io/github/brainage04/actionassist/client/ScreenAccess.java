package io.github.brainage04.actionassist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The open screen on Minecraft 1.21.x, which keeps it in {@code Minecraft.screen}. */
public final class ScreenAccess {
    private ScreenAccess() {
    }

    public static boolean isScreenOpen(Minecraft minecraft) {
        return minecraft.screen != null;
    }

    /** The open screen, or {@code null}. */
    public static Screen currentScreen(Minecraft minecraft) {
        return minecraft.screen;
    }
}
