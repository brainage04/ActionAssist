package io.github.brainage04.actionassist.core;

/**
 * Live read-only view of the local player's inventory.
 *
 * <p>Slots use {@code Inventory} numbering: {@code 0}–{@code 8} are the hotbar and {@code 9}–{@code 35}
 * are the main inventory.
 */
public interface InventoryView {
    int HOTBAR_SIZE = 9;
    int SIZE = 36;

    /** Namespaced item id in {@code slot}, or {@code null} when the slot is empty. */
    String itemId(int slot);

    int count(int slot);

    boolean mainHandEmpty();

    /** Whether the player's 2x2 crafting grid holds no items. */
    boolean craftingGridEmpty();

    /** Whether the inventory menu's cursor holds no items. */
    boolean cursorEmpty();
}
