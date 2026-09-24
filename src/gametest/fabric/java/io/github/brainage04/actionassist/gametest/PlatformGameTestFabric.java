package io.github.brainage04.actionassist.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Connects {@link PlatformScenario} to Fabric's client tick, server tick, and block-use events. */
public final class PlatformGameTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(PlatformScenario::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(PlatformScenario::serverTick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                !level.isClientSide()
                        && PlatformScenario.useBlock(player, hit.getBlockPos(), hand == InteractionHand.MAIN_HAND)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS);
    }
}
