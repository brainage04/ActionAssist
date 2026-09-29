package io.github.brainage04.actionassist.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Action-bar output for Minecraft 1.21.x, where the player shows overlay messages itself. */
final class ActionBar {
    private ActionBar() {
    }

    static void show(LocalPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}
