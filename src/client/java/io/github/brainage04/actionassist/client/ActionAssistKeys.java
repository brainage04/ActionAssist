package io.github.brainage04.actionassist.client;

import net.minecraft.client.KeyMapping;

public record ActionAssistKeys(
        KeyMapping pebbleMacro,
        KeyMapping cropMacro,
        KeyMapping selectContainer
) {
}
