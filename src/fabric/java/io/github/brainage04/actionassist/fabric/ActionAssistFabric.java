package io.github.brainage04.actionassist.fabric;

import io.github.brainage04.actionassist.client.ActionAssistKeys;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.client.FabricKeyCategory;
import io.github.brainage04.actionassist.client.KeyMappingFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class ActionAssistFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ActionAssistKeys keys = KeyMappingFactory.create();
        FabricKeyCategory.register();
        FabricKeyMappings.register(keys.pebbleMacro());
        FabricKeyMappings.register(keys.cropMacro());
        FabricKeyMappings.register(keys.selectContainer());

        ClientRuntime.initialize(FabricLoader.getInstance().getConfigDir(), keys);
        ClientTickEvents.END_CLIENT_TICK.register(ClientRuntime::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientRuntime.shutdown());
    }
}
