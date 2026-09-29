package io.github.brainage04.actionassist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shows Action Assist messages on the action bar through the target version's {@link ActionBar}. */
final class StatusMessages {
    private final Minecraft minecraft;
    private final boolean enabled;

    StatusMessages(Minecraft minecraft, boolean enabled) {
        this.minecraft = minecraft;
        this.enabled = enabled;
    }

    void show(String translationKey, Object... arguments) {
        if (!enabled || minecraft.player == null) {
            return;
        }
        ActionBar.show(minecraft.player, Component.translatable(translationKey, arguments));
    }
}
