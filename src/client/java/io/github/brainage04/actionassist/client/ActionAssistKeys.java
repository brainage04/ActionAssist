package io.github.brainage04.actionassist.client;

import net.minecraft.client.KeyMapping;

public record ActionAssistKeys(
        KeyMapping toggle,
        KeyMapping cycleSneakMode,
        KeyMapping dumpHotbar,
        KeyMapping companionHold
) {
}
