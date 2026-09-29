package io.github.brainage04.actionassist.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Action-bar output for Minecraft 26.x, which replaced {@code displayClientMessage(Component, true)}. */
final class ActionBar {
    private ActionBar() {
    }

    static void show(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
