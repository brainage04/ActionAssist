package io.github.brainage04.actionassist.fabric;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Key mapping registration through Fabric API for Minecraft 1.21.x. */
final class FabricKeyMappings {
    private FabricKeyMappings() {
    }

    static void register(KeyMapping mapping) {
        KeyBindingHelper.registerKeyBinding(mapping);
    }
}
