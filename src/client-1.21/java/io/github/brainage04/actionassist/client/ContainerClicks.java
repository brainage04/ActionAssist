package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.core.MacroEngine;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;

/** Container slot clicks on Minecraft 1.21.x, through {@code MultiPlayerGameMode.handleInventoryMouseClick}. */
final class ContainerClicks {
    private ContainerClicks() {
    }

    static void click(MultiPlayerGameMode gameMode, int containerId, int slot, int button,
            MacroEngine.ClickKind kind, Player player) {
        ClickType input = switch (kind) {
            case PICKUP -> ClickType.PICKUP;
            case QUICK_MOVE -> ClickType.QUICK_MOVE;
            case QUICK_CRAFT -> ClickType.QUICK_CRAFT;
        };
        gameMode.handleInventoryMouseClick(containerId, slot, button, input, player);
    }
}
