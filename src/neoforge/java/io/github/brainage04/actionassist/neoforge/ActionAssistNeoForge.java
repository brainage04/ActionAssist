package io.github.brainage04.actionassist.neoforge;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.client.ActionAssistKeys;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.client.KeyMappingFactory;
import io.github.brainage04.actionassist.client.NeoForgeKeyCategory;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = ActionAssist.MOD_ID, dist = Dist.CLIENT)
public final class ActionAssistNeoForge {
    private final ActionAssistKeys keys = KeyMappingFactory.create();

    public ActionAssistNeoForge(IEventBus modBus) {
        ClientRuntime.initialize(FMLPaths.CONFIGDIR.get(), keys);
        ClientRuntime.setItemHandlerProbe(ActionAssistNeoForge::exposesItemHandler);
        modBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);
    }

    // Looked up by name because the capability's Java field differs by NeoForge version (Capabilities.ItemHandler.BLOCK
    // up to 21.8, Capabilities.Item.BLOCK in 26.2, Capabilities.java:38); both are registered as neoforge:item_handler.
    // Mods register providers in RegisterCapabilitiesEvent, which CommonModLoader.java:79 posts on the client too.
    @SuppressWarnings("unchecked")
    private static boolean exposesItemHandler(Level level, BlockPos position) {
        for (BlockCapability<?, ?> capability : BlockCapability.getAll()) {
            if (capability.name().toString().equals("neoforge:item_handler")) {
                return level.getCapability((BlockCapability<Object, Object>) capability, position, null) != null;
            }
        }
        return false;
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        NeoForgeKeyCategory.register(event);
        event.register(keys.pebbleMacro());
        event.register(keys.cropMacro());
        event.register(keys.selectContainer());
    }

    private void onClientTick(ClientTickEvent.Post event) {
        ClientRuntime.tick(net.minecraft.client.Minecraft.getInstance());
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        ClientRuntime.shutdown();
    }
}
