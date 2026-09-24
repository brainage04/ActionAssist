package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.core.InventoryView;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Reads the local player's inventory without copying it. */
final class ClientInventoryView implements InventoryView {
    private final Minecraft minecraft;
    private final Map<Item, String> itemIds = new IdentityHashMap<>();

    ClientInventoryView(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public String itemId(int slot) {
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return null;
        }
        return itemIds.computeIfAbsent(stack.getItem(), item -> String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
    }

    @Override
    public int count(int slot) {
        return minecraft.player.getInventory().getItem(slot).getCount();
    }

    @Override
    public boolean mainHandEmpty() {
        return minecraft.player.getMainHandItem().isEmpty();
    }

    @Override
    public boolean craftingGridEmpty() {
        var menu = minecraft.player.inventoryMenu;
        for (int slot = 1; slot <= 4; slot++) {
            if (menu.getSlot(slot).hasItem()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean cursorEmpty() {
        return minecraft.player.inventoryMenu.getCarried().isEmpty();
    }
}
