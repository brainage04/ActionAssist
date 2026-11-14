package io.github.brainage04.actionassist.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-independent macro state machine, advanced once per client tick.
 *
 * <p>Inventory operations are expressed as player-inventory-menu clicks. That menu is laid out as:
 * result {@code 0}, crafting grid {@code 1}–{@code 4}, armour {@code 5}–{@code 8}, main inventory
 * {@code 9}–{@code 35}, and hotbar {@code 36}–{@code 44}.
 */
public final class MacroEngine {
    /** Free slots a macro needs to start. */
    public static final int MIN_FREE_SLOTS = 3;
    /** The pebble macro compacts once this few storage slots remain free. */
    public static final int COMPACT_AT_FREE_SLOTS = 2;
    /** Items are deposited once this few storage slots remain free. */
    public static final int DEPOSIT_AT_FREE_SLOTS = 1;
    /** Ticks inputs stay paused after inventory clicks so the server's result reaches the client. */
    public static final int SETTLE_TICKS = 3;
    /** Ticks sneak is released before the deposit container is opened. */
    public static final int DEPOSIT_RELEASE_TICKS = 2;
    public static final int DEPOSIT_OPEN_TIMEOUT_TICKS = 40;
    /** Ticks waited after the container opens for its contents to synchronise. */
    public static final int DEPOSIT_SYNC_TICKS = 2;
    /** Ticks waited after depositing before free space is measured. */
    public static final int DEPOSIT_VERIFY_TICKS = 4;
    /**
     * Ticks a compaction may wait for its crafting result before the item counts as having no 2x2 recipe. Only the
     * server computes crafting results (vanilla {@code InventoryMenu.slotsChanged} needs a {@code ServerLevel},
     * 1.21.11 {@code InventoryMenu.java:81-85}), so the client sees a compaction work only when the server's slot
     * updates arrive, a round trip that can outlast {@link #SETTLE_TICKS}.
     */
    public static final int COMPACTION_RESULT_TIMEOUT_TICKS = 40;

    static final int RESULT_SLOT = 0;
    static final int FIRST_GRID_SLOT = 1;
    static final int LAST_GRID_SLOT = 4;
    static final int HOTBAR_MENU_OFFSET = 36;
    static final int OUTSIDE_SLOT = -999;
    static final int QUICK_CRAFT_START_EVEN_SPLIT = 0;
    static final int QUICK_CRAFT_ADD_SLOT_EVEN_SPLIT = 1;
    static final int QUICK_CRAFT_END_EVEN_SPLIT = 2;
    private static final int COMPACT_INPUTS = 4;

    private final MacroSettings settings;
    private final Set<String> compactItems;
    private final Set<String> uncompactable = new HashSet<>();
    private final Map<String, PendingCompaction> pendingCompactions = new HashMap<>();
    private final boolean[] reserved = new boolean[InventoryView.SIZE];
    private final String[] depositedIds = new String[InventoryView.SIZE];
    private final int[] depositedCounts = new int[InventoryView.SIZE];

    private Macro macro;
    private Phase phase = Phase.RUNNING;
    private int phaseTicks;
    private int gameplayTicks;
    private boolean sneakTapDown;
    private boolean depositProgress;

    public MacroEngine(MacroSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.compactItems = Set.copyOf(settings.compactItems());
    }

    /**
     * Starts {@code requested}, stops it when it is already running, or switches to it from the other
     * macro. Slots occupied when a macro starts are reserved and never touched by it.
     */
    public void toggle(Macro requested, InventoryView inventory, Output output) {
        Objects.requireNonNull(requested, "requested");
        if (macro == requested) {
            stop(output, Status.STOPPED);
            return;
        }
        if (macro != null) {
            stop(output, null);
        }
        if (requested == Macro.PEBBLE && !inventory.mainHandEmpty()) {
            output.status(Status.HAND_NOT_EMPTY, requested);
            return;
        }
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            reserved[slot] = inventory.itemId(slot) != null;
        }
        macro = requested;
        if (freeStorageSlots(inventory) < MIN_FREE_SLOTS) {
            macro = null;
            output.status(Status.NOT_ENOUGH_SPACE, requested);
            return;
        }
        uncompactable.clear();
        pendingCompactions.clear();
        sneakTapDown = false;
        enter(Phase.RUNNING);
        output.status(
                output.hasDepositContainer() ? Status.STARTED : Status.STARTED_WITHOUT_CONTAINER, requested);
    }

    /** Stops the active macro without a status message, e.g. on disconnect or shutdown. */
    public void stop(Output output) {
        if (macro != null) {
            stop(output, null);
        }
    }

    public void tick(
            boolean connected,
            boolean screenOpen,
            boolean containerOpen,
            InventoryView inventory,
            Output output) {
        if (macro == null) {
            return;
        }
        if (!connected) {
            macro = null;
            release(output);
            return;
        }
        phaseTicks++;
        switch (phase) {
            case DEPOSIT_RELEASE -> tickDepositRelease(output);
            case DEPOSIT_OPEN -> tickDepositOpen(containerOpen, output);
            case DEPOSIT_TRANSFER -> tickDepositTransfer(containerOpen, inventory, output);
            case DEPOSIT_VERIFY -> tickDepositVerify(containerOpen, inventory, output);
            case RUNNING, SETTLE -> tickGameplay(screenOpen, inventory, output);
        }
    }

    private void tickGameplay(boolean screenOpen, InventoryView inventory, Output output) {
        gameplayTicks++;
        recordCompactionResults(inventory);
        if (screenOpen) {
            release(output);
            return;
        }
        if (phase == Phase.SETTLE) {
            applyHeldInputs(output);
            if (phaseTicks < SETTLE_TICKS) {
                return;
            }
            enter(Phase.RUNNING);
        }
        if (manageInventory(inventory, output)) {
            if (macro != null && phase == Phase.RUNNING) {
                enter(Phase.SETTLE);
            }
            return;
        }
        applyHeldInputs(output);
        output.use();
    }

    /** Returns whether the tick was spent on inventory management instead of clicking. */
    private boolean manageInventory(InventoryView inventory, Output output) {
        int free = freeStorageSlots(inventory);
        if (!inventory.cursorEmpty() && free > 0) {
            output.click(menuSlot(firstFreeStorageSlot(inventory)), 0, ClickKind.PICKUP);
            return true;
        }
        if (!inventory.craftingGridEmpty() && free > 0) {
            for (int slot = FIRST_GRID_SLOT; slot <= LAST_GRID_SLOT; slot++) {
                output.click(slot, 0, ClickKind.QUICK_MOVE);
            }
            return true;
        }
        boolean handBlocked = macro == Macro.PEBBLE && !inventory.mainHandEmpty();
        if (handBlocked && free > 0) {
            boolean moved = false;
            for (int slot = 0; slot < InventoryView.HOTBAR_SIZE; slot++) {
                if (!reserved[slot] && inventory.itemId(slot) != null) {
                    output.click(menuSlot(slot), 0, ClickKind.QUICK_MOVE);
                    moved = true;
                }
            }
            if (!moved) {
                stop(output, Status.HAND_NOT_EMPTY);
            }
            return true;
        }
        if (macro == Macro.PEBBLE && free <= COMPACT_AT_FREE_SLOTS && compact(inventory, output)) {
            return true;
        }
        if (handBlocked || free <= DEPOSIT_AT_FREE_SLOTS) {
            beginDeposit(inventory, output);
            return true;
        }
        return false;
    }

    /**
     * Crafts every compactable stack of four or more into its 2x2 product: pick the stack up, split it
     * evenly across the crafting grid, return the remainder, shift-click the result, and shift-click
     * any grid leftovers back.
     */
    private boolean compact(InventoryView inventory, Output output) {
        boolean compacted = false;
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            String id = inventory.itemId(slot);
            if (reserved[slot]
                    || id == null
                    || inventory.count(slot) < COMPACT_INPUTS
                    || !compactItems.contains(id)
                    || uncompactable.contains(id)) {
                continue;
            }
            PendingCompaction pending = pendingCompactions.get(id);
            if (pending == null) {
                pendingCompactions.put(id, new PendingCompaction(unreservedTotal(inventory, id), gameplayTicks));
            } else if (pending.startedTick() != gameplayTicks) {
                // An earlier compaction of this item is still waiting for its result.
                continue;
            }
            int source = menuSlot(slot);
            output.click(source, 0, ClickKind.PICKUP);
            output.click(OUTSIDE_SLOT, QUICK_CRAFT_START_EVEN_SPLIT, ClickKind.QUICK_CRAFT);
            for (int grid = FIRST_GRID_SLOT; grid <= LAST_GRID_SLOT; grid++) {
                output.click(grid, QUICK_CRAFT_ADD_SLOT_EVEN_SPLIT, ClickKind.QUICK_CRAFT);
            }
            output.click(OUTSIDE_SLOT, QUICK_CRAFT_END_EVEN_SPLIT, ClickKind.QUICK_CRAFT);
            output.click(source, 0, ClickKind.PICKUP);
            output.click(RESULT_SLOT, 0, ClickKind.QUICK_MOVE);
            for (int grid = FIRST_GRID_SLOT; grid <= LAST_GRID_SLOT; grid++) {
                output.click(grid, 0, ClickKind.QUICK_MOVE);
            }
            compacted = true;
        }
        return compacted;
    }

    /**
     * An item whose total fell has compacted. One whose total has not fallen within
     * {@link #COMPACTION_RESULT_TIMEOUT_TICKS} has no 2x2 recipe here; stop retrying it for this run.
     */
    private void recordCompactionResults(InventoryView inventory) {
        if (pendingCompactions.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, PendingCompaction>> entries = pendingCompactions.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, PendingCompaction> entry = entries.next();
            PendingCompaction pending = entry.getValue();
            if (unreservedTotal(inventory, entry.getKey()) < pending.total()) {
                entries.remove();
            } else if (gameplayTicks - pending.startedTick() >= COMPACTION_RESULT_TIMEOUT_TICKS) {
                uncompactable.add(entry.getKey());
                entries.remove();
            }
        }
    }

    private void beginDeposit(InventoryView inventory, Output output) {
        boolean depositable = false;
        for (int slot = 0; slot < InventoryView.SIZE && !depositable; slot++) {
            depositable = !reserved[slot] && inventory.itemId(slot) != null;
        }
        if (!depositable) {
            stop(output, Status.NOT_ENOUGH_SPACE);
            return;
        }
        release(output);
        enter(Phase.DEPOSIT_RELEASE);
    }

    private void tickDepositRelease(Output output) {
        release(output);
        if (phaseTicks < DEPOSIT_RELEASE_TICKS) {
            return;
        }
        if (!output.openDepositContainer()) {
            stop(output, Status.NO_DEPOSIT_CONTAINER);
            return;
        }
        enter(Phase.DEPOSIT_OPEN);
    }

    private void tickDepositOpen(boolean containerOpen, Output output) {
        if (containerOpen) {
            enter(Phase.DEPOSIT_TRANSFER);
        } else if (phaseTicks >= DEPOSIT_OPEN_TIMEOUT_TICKS) {
            stop(output, Status.DEPOSIT_UNREACHABLE);
        }
    }

    private void tickDepositTransfer(boolean containerOpen, InventoryView inventory, Output output) {
        if (!containerOpen) {
            stop(output, Status.DEPOSIT_INTERRUPTED);
            return;
        }
        if (phaseTicks < DEPOSIT_SYNC_TICKS) {
            return;
        }
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            String id = reserved[slot] ? null : inventory.itemId(slot);
            depositedIds[slot] = id;
            depositedCounts[slot] = id == null ? 0 : inventory.count(slot);
        }
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            if (depositedIds[slot] != null) {
                output.depositSlot(slot);
            }
        }
        // Local menu clicks apply immediately, before incoming drops can refill the same stacks.
        depositProgress = depositMadeProgress(inventory);
        enter(Phase.DEPOSIT_VERIFY);
    }

    private void tickDepositVerify(boolean containerOpen, InventoryView inventory, Output output) {
        if (!containerOpen) {
            stop(output, Status.DEPOSIT_INTERRUPTED);
            return;
        }
        depositProgress |= depositMadeProgress(inventory);
        if (phaseTicks < DEPOSIT_VERIFY_TICKS) {
            return;
        }
        output.closeContainer();
        enter(Phase.SETTLE);
        if (!depositProgress) {
            stop(output, Status.DEPOSIT_FULL);
        }
    }

    /**
     * The container is full when no deposited stack shrank. Free space is no measure: items still in
     * flight are picked up into the emptied slots while the container is open.
     */
    private boolean depositMadeProgress(InventoryView inventory) {
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            String deposited = depositedIds[slot];
            if (deposited != null
                    && (!deposited.equals(inventory.itemId(slot)) || inventory.count(slot) < depositedCounts[slot])) {
                return true;
            }
        }
        return false;
    }

    private void applyHeldInputs(Output output) {
        if (macro == Macro.PEBBLE) {
            output.setSneak(true);
            output.setVeinKey(false);
        } else {
            sneakTapDown = !sneakTapDown;
            output.setSneak(sneakTapDown);
            output.setVeinKey(true);
        }
    }

    private void stop(Output output, Status status) {
        Macro stopped = macro;
        if (phase == Phase.DEPOSIT_TRANSFER || phase == Phase.DEPOSIT_VERIFY) {
            output.closeContainer();
        }
        macro = null;
        enter(Phase.RUNNING);
        pendingCompactions.clear();
        release(output);
        if (status != null) {
            output.status(status, stopped);
        }
    }

    private static void release(Output output) {
        output.setSneak(false);
        output.setVeinKey(false);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    /**
     * Storage the macro fills: the pebble macro keeps the hotbar clear, so only unreserved main
     * inventory slots count; the crop macro counts every unreserved slot.
     */
    private int freeStorageSlots(InventoryView inventory) {
        int free = 0;
        for (int slot = firstStorageSlot(); slot < InventoryView.SIZE; slot++) {
            if (!reserved[slot] && inventory.itemId(slot) == null) {
                free++;
            }
        }
        return free;
    }

    private int firstFreeStorageSlot(InventoryView inventory) {
        for (int slot = firstStorageSlot(); slot < InventoryView.SIZE; slot++) {
            if (!reserved[slot] && inventory.itemId(slot) == null) {
                return slot;
            }
        }
        throw new IllegalStateException("No free storage slot");
    }

    private int firstStorageSlot() {
        return macro == Macro.PEBBLE ? InventoryView.HOTBAR_SIZE : 0;
    }

    private int unreservedTotal(InventoryView inventory, String id) {
        int total = 0;
        for (int slot = 0; slot < InventoryView.SIZE; slot++) {
            if (!reserved[slot] && id.equals(inventory.itemId(slot))) {
                total += inventory.count(slot);
            }
        }
        return total;
    }

    static int menuSlot(int inventorySlot) {
        return inventorySlot < InventoryView.HOTBAR_SIZE ? HOTBAR_MENU_OFFSET + inventorySlot : inventorySlot;
    }

    public Macro activeMacro() {
        return macro;
    }

    public boolean isDepositing() {
        return macro != null
                && (phase == Phase.DEPOSIT_RELEASE
                        || phase == Phase.DEPOSIT_OPEN
                        || phase == Phase.DEPOSIT_TRANSFER
                        || phase == Phase.DEPOSIT_VERIFY);
    }

    public MacroSettings settings() {
        return settings;
    }

    /** A compaction waiting for its result: the item's unreserved total before it, and the gameplay tick it ran. */
    private record PendingCompaction(int total, int startedTick) {}

    private enum Phase {
        RUNNING,
        SETTLE,
        DEPOSIT_RELEASE,
        DEPOSIT_OPEN,
        DEPOSIT_TRANSFER,
        DEPOSIT_VERIFY
    }

    public enum ClickKind {
        PICKUP,
        QUICK_MOVE,
        QUICK_CRAFT
    }

    public enum Status {
        STARTED,
        STARTED_WITHOUT_CONTAINER,
        STOPPED,
        HAND_NOT_EMPTY,
        NOT_ENOUGH_SPACE,
        NO_DEPOSIT_CONTAINER,
        DEPOSIT_UNREACHABLE,
        DEPOSIT_INTERRUPTED,
        DEPOSIT_FULL
    }

    public interface Output {
        void setSneak(boolean down);

        void setVeinKey(boolean down);

        /** Right-clicks once, as a physical use-key press would. */
        void use();

        /** Clicks a slot of the player's own inventory menu. */
        void click(int menuSlot, int button, ClickKind kind);

        /** Shift-clicks an inventory slot into the open deposit container. */
        void depositSlot(int inventorySlot);

        boolean hasDepositContainer();

        /** Interacts with the selected deposit container; {@code false} when none is selected. */
        boolean openDepositContainer();

        void closeContainer();

        void status(Status status, Macro macro);
    }
}
