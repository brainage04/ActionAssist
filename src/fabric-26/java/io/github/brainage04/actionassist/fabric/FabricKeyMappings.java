package io.github.brainage04.actionassist.fabric;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Key mapping registration through Fabric API for Minecraft 26.x, which renamed {@code KeyBindingHelper}. */
final class FabricKeyMappings {
    private FabricKeyMappings() {
    }

    static void register(KeyMapping mapping) {
        KeyMappingHelper.registerKeyMapping(mapping);
    }
}
