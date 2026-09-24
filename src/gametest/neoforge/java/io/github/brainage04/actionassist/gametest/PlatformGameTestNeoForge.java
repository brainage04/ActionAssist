package io.github.brainage04.actionassist.gametest;

import io.github.brainage04.actionassist.ActionAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Connects {@link PlatformScenario} to NeoForge's client tick, server tick, and block-use events. */
@EventBusSubscriber(modid = ActionAssist.MOD_ID, value = Dist.CLIENT)
public final class PlatformGameTestNeoForge {
    private PlatformGameTestNeoForge() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PlatformScenario.clientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PlatformScenario.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            PlatformScenario.useBlock(event.getEntity(), event.getPos(), event.getHand() == InteractionHand.MAIN_HAND);
        }
    }
}
