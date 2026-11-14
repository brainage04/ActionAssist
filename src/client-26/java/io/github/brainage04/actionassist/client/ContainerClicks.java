package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.core.MacroEngine;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

/** Container slot clicks on Minecraft 26.x, through {@code MultiPlayerGameMode.handleContainerInput}. */
final class ContainerClicks {
    private ContainerClicks() {
    }

    static void click(MultiPlayerGameMode gameMode, int containerId, int slot, int button,
            MacroEngine.ClickKind kind, Player player) {
        ContainerInput input = switch (kind) {
            case PICKUP -> ContainerInput.PICKUP;
            case QUICK_MOVE -> ContainerInput.QUICK_MOVE;
            case QUICK_CRAFT -> ContainerInput.QUICK_CRAFT;
        };
        gameMode.handleContainerInput(containerId, slot, button, input, player);
    }
}
