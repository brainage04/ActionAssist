package io.github.brainage04.actionassist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brainage04.actionassist.core.MacroEngine.ClickKind;
import io.github.brainage04.actionassist.core.MacroEngine.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MacroEngineTest {
    private static final String PEBBLE = "exdeorum:stone_pebble";
    private static final String TOOL = "minecraft:diamond_pickaxe";

    private final FakeInventory inventory = new FakeInventory();
    private final RecordingOutput output = new RecordingOutput();
    private final MacroEngine engine = new MacroEngine(MacroSettings.defaults());

    @Test
    void pebbleMacroHoldsSneakAndRightClicksEveryTick() {
        engine.toggle(Macro.PEBBLE, inventory, output);
        tick(20);

        assertEquals(List.of(Status.STARTED_WITHOUT_CONTAINER), output.statuses);
        assertEquals(20, output.uses);
        assertEquals(20, output.sneakDownTicks);
        assertEquals(1, output.sneakPresses);
        assertEquals(0, output.veinDownTicks);
    }

    @Test
    void cropMacroTapsSneakEveryOtherTickHoldsVeinKeyAndRightClicksEveryTick() {
        engine.toggle(Macro.CROP, inventory, output);
        tick(20);

        assertEquals(20, output.uses);
        assertEquals(10, output.sneakPresses);
        assertEquals(10, output.sneakDownTicks);
        assertEquals(20, output.veinDownTicks);
    }

    @Test
    void togglingTheRunningMacroStopsAndReleasesInputs() {
        engine.toggle(Macro.CROP, inventory, output);
        tick(3);
        engine.toggle(Macro.CROP, inventory, output);
        tick(5);

        assertNull(engine.activeMacro());
        assertEquals(Status.STOPPED, output.statuses.getLast());
        assertFalse(output.sneak);
        assertFalse(output.vein);
        assertEquals(3, output.uses);
    }

    @Test
    void togglingTheOtherMacroSwitchesWithoutAStopMessage() {
        engine.toggle(Macro.CROP, inventory, output);
        tick(1);
        engine.toggle(Macro.PEBBLE, inventory, output);

        assertEquals(Macro.PEBBLE, engine.activeMacro());
        assertEquals(List.of(Status.STARTED_WITHOUT_CONTAINER, Status.STARTED_WITHOUT_CONTAINER), output.statuses);
        assertFalse(output.vein);
    }

    @Test
    void pebbleMacroRefusesToStartWithAnOccupiedHand() {
        inventory.set(0, TOOL, 1);
        inventory.mainHandEmpty = false;

        engine.toggle(Macro.PEBBLE, inventory, output);
        tick(5);

        assertNull(engine.activeMacro());
        assertEquals(List.of(Status.HAND_NOT_EMPTY), output.statuses);
        assertEquals(0, output.uses);
    }

    @Test
    void macrosRefuseToStartWithFewerThanThreeFreeStorageSlots() {
        for (int slot = 9; slot < 34; slot++) {
            inventory.set(slot, TOOL, 1);
        }

        engine.toggle(Macro.PEBBLE, inventory, output);

        assertNull(engine.activeMacro());
        assertEquals(List.of(Status.NOT_ENOUGH_SPACE), output.statuses);
    }

    @Test
    void anOpenScreenPausesAndReleasesInputsUntilItCloses() {
        engine.toggle(Macro.CROP, inventory, output);
        tick(2);
        engine.tick(true, true, false, inventory, output);
        engine.tick(true, true, false, inventory, output);

        assertEquals(2, output.uses);
        assertFalse(output.sneak);
        assertFalse(output.vein);

        tick(1);
        assertEquals(3, output.uses);
        assertTrue(output.vein);
    }

    @Test
    void disconnectingStopsSilently() {
        engine.toggle(Macro.PEBBLE, inventory, output);
        tick(2);
        engine.tick(false, false, false, inventory, output);

        assertNull(engine.activeMacro());
        assertFalse(output.sneak);
        assertEquals(List.of(Status.STARTED_WITHOUT_CONTAINER), output.statuses);
    }

    @Test
    void pebbleMacroMovesUnreservedHotbarItemsOutOfTheWayOfTheHand() {
        inventory.set(8, TOOL, 1);
        engine.toggle(Macro.PEBBLE, inventory, output);
        tick(1);
        inventory.set(0, PEBBLE, 3);
        inventory.set(1, "exdeorum:granite_pebble", 1);
        inventory.mainHandEmpty = false;

        tick(1);

        assertEquals(
                List.of(click(36, 0, ClickKind.QUICK_MOVE), click(37, 0, ClickKind.QUICK_MOVE)),
                output.clicks);
        inventory.clear(0);
        inventory.clear(1);
        inventory.mainHandEmpty = true;
        tick(MacroEngine.SETTLE_TICKS - 1);
        assertEquals(1, output.uses);
        tick(1);
        assertEquals(2, output.uses);
    }

    @Test
    void compactionSplitsEachEligibleStackAcrossTheCraftingGridAndCraftsIt() {
        inventory.set(10, TOOL, 1);
        engine.toggle(Macro.PEBBLE, inventory, output);
        fillStorageExcept(2, 9, "minecraft:cobblestone", 64);
        inventory.set(9, PEBBLE, 63);
        inventory.set(11, "exdeorum:granite_pebble", 3);

        tick(1);

        List<Click> expected = new ArrayList<>();
        expected.add(click(9, 0, ClickKind.PICKUP));
        expected.add(click(-999, 0, ClickKind.QUICK_CRAFT));
        for (int grid = 1; grid <= 4; grid++) {
            expected.add(click(grid, 1, ClickKind.QUICK_CRAFT));
        }
        expected.add(click(-999, 2, ClickKind.QUICK_CRAFT));
        expected.add(click(9, 0, ClickKind.PICKUP));
        expected.add(click(0, 0, ClickKind.QUICK_MOVE));
        for (int grid = 1; grid <= 4; grid++) {
            expected.add(click(grid, 0, ClickKind.QUICK_MOVE));
        }
        assertEquals(expected, output.clicks);
        assertEquals(0, output.uses);
    }

    @Test
    void anItemThatDoesNotCompactIsNotRetried() {
        MacroEngine custom = new MacroEngine(new MacroSettings("key.ftbultimine", List.of("minecraft:dirt"), true));
        custom.toggle(Macro.PEBBLE, inventory, output);
        fillStorageExcept(2, 9, "minecraft:cobblestone", 64);
        inventory.set(9, "minecraft:dirt", 64);

        custom.tick(true, false, false, inventory, output);
        assertEquals(13, output.clicks.size());
        for (int tick = 0; tick < MacroEngine.SETTLE_TICKS; tick++) {
            custom.tick(true, false, false, inventory, output);
        }
        output.clicks.clear();
        int uses = output.uses;

        custom.tick(true, false, false, inventory, output);

        assertTrue(output.clicks.isEmpty());
        assertEquals(uses + 1, output.uses);
        assertFalse(custom.isDepositing());
    }

    @Test
    void depositOpensTheSelectedContainerMovesUnreservedItemsAndResumes() {
        output.depositContainer = true;
        inventory.set(35, TOOL, 1);
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "mysticalagriculture:inferium_essence", 64);

        tick(1);
        assertTrue(engine.isDepositing());
        assertFalse(output.sneak);
        assertFalse(output.vein);
        tick(MacroEngine.DEPOSIT_RELEASE_TICKS);
        assertEquals(1, output.opens);

        engine.tick(true, true, true, inventory, output);
        tick(MacroEngine.DEPOSIT_SYNC_TICKS, true);
        List<Integer> expectedDeposits = new ArrayList<>();
        for (int slot = 0; slot < 34; slot++) {
            expectedDeposits.add(slot);
        }
        assertEquals(expectedDeposits, output.deposits);

        for (int slot = 0; slot < 34; slot++) {
            inventory.clear(slot);
        }
        tick(MacroEngine.DEPOSIT_VERIFY_TICKS, true);
        assertEquals(1, output.closes);
        assertFalse(engine.isDepositing());
        int usesBeforeResume = output.uses;
        tick(MacroEngine.SETTLE_TICKS);
        assertEquals(usesBeforeResume + 1, output.uses);
        assertEquals(Macro.CROP, engine.activeMacro());
    }

    @Test
    void aFullDepositContainerStopsTheMacro() {
        output.depositContainer = true;
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS);
        engine.tick(true, true, true, inventory, output);
        tick(MacroEngine.DEPOSIT_SYNC_TICKS + MacroEngine.DEPOSIT_VERIFY_TICKS, true);

        assertNull(engine.activeMacro());
        assertEquals(Status.DEPOSIT_FULL, output.statuses.getLast());
        assertEquals(1, output.closes);
    }

    @Test
    void itemsPickedUpDuringADepositDoNotCountAsAFullContainer() {
        output.depositContainer = true;
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS);
        engine.tick(true, true, true, inventory, output);
        tick(MacroEngine.DEPOSIT_SYNC_TICKS, true);
        // Everything was deposited, then drops still in flight refilled the emptied slots.
        for (int slot = 0; slot < InventoryView.SIZE - 1; slot++) {
            inventory.set(slot, "minecraft:wheat_seeds", 1);
        }
        tick(MacroEngine.DEPOSIT_VERIFY_TICKS, true);

        assertEquals(Macro.CROP, engine.activeMacro());
        assertFalse(engine.isDepositing());
    }

    @Test
    void aPartiallyAcceptedDepositKeepsTheMacroRunning() {
        output.depositContainer = true;
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS);
        engine.tick(true, true, true, inventory, output);
        tick(MacroEngine.DEPOSIT_SYNC_TICKS, true);
        inventory.set(0, "minecraft:wheat_seeds", 10);
        tick(MacroEngine.DEPOSIT_VERIFY_TICKS, true);

        assertEquals(Macro.CROP, engine.activeMacro());
    }

    @Test
    void depositWithoutASelectedContainerStopsTheMacro() {
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS);

        assertNull(engine.activeMacro());
        assertEquals(Status.NO_DEPOSIT_CONTAINER, output.statuses.getLast());
    }

    @Test
    void aContainerThatNeverOpensStopsTheMacro() {
        output.depositContainer = true;
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS + MacroEngine.DEPOSIT_OPEN_TIMEOUT_TICKS);

        assertNull(engine.activeMacro());
        assertEquals(Status.DEPOSIT_UNREACHABLE, output.statuses.getLast());
        assertEquals(0, output.closes);
    }

    @Test
    void closingTheDepositContainerEarlyStopsTheMacro() {
        output.depositContainer = true;
        engine.toggle(Macro.CROP, inventory, output);
        fillStorageExcept(1, 0, "minecraft:wheat_seeds", 64);

        tick(1 + MacroEngine.DEPOSIT_RELEASE_TICKS);
        engine.tick(true, true, true, inventory, output);
        tick(1);

        assertNull(engine.activeMacro());
        assertEquals(Status.DEPOSIT_INTERRUPTED, output.statuses.getLast());
        assertTrue(output.deposits.isEmpty());
    }

    private void fillStorageExcept(int free, int firstStorageSlot, String id, int count) {
        int remainingFree = free;
        for (int slot = InventoryView.SIZE - 1; slot >= firstStorageSlot; slot--) {
            if (inventory.ids[slot] != null) {
                continue;
            }
            if (remainingFree > 0) {
                remainingFree--;
                continue;
            }
            inventory.set(slot, id, count);
        }
    }

    private void tick(int ticks) {
        tick(ticks, false);
    }

    private void tick(int ticks, boolean containerOpen) {
        for (int tick = 0; tick < ticks; tick++) {
            engine.tick(true, containerOpen, containerOpen, inventory, output);
        }
    }

    private static Click click(int slot, int button, ClickKind kind) {
        return new Click(slot, button, kind);
    }

    private record Click(int slot, int button, ClickKind kind) {}

    private static final class FakeInventory implements InventoryView {
        private final String[] ids = new String[SIZE];
        private final int[] counts = new int[SIZE];
        private boolean mainHandEmpty = true;

        void set(int slot, String id, int count) {
            ids[slot] = id;
            counts[slot] = count;
        }

        void clear(int slot) {
            set(slot, null, 0);
        }

        @Override
        public String itemId(int slot) {
            return ids[slot];
        }

        @Override
        public int count(int slot) {
            return counts[slot];
        }

        @Override
        public boolean mainHandEmpty() {
            return mainHandEmpty;
        }

        @Override
        public boolean craftingGridEmpty() {
            return true;
        }

        @Override
        public boolean cursorEmpty() {
            return true;
        }
    }

    private static final class RecordingOutput implements MacroEngine.Output {
        private final List<Click> clicks = new ArrayList<>();
        private final List<Integer> deposits = new ArrayList<>();
        private final List<Status> statuses = new ArrayList<>();
        private boolean depositContainer;
        private boolean sneak;
        private boolean vein;
        private int sneakPresses;
        private int sneakDownTicks;
        private int veinDownTicks;
        private int uses;
        private int opens;
        private int closes;

        @Override
        public void setSneak(boolean down) {
            if (down && !sneak) {
                sneakPresses++;
            }
            sneak = down;
        }

        @Override
        public void setVeinKey(boolean down) {
            vein = down;
        }

        @Override
        public void use() {
            uses++;
            if (sneak) {
                sneakDownTicks++;
            }
            if (vein) {
                veinDownTicks++;
            }
        }

        @Override
        public void click(int menuSlot, int button, ClickKind kind) {
            clicks.add(new Click(menuSlot, button, kind));
        }

        @Override
        public void depositSlot(int inventorySlot) {
            deposits.add(inventorySlot);
        }

        @Override
        public boolean hasDepositContainer() {
            return depositContainer;
        }

        @Override
        public boolean openDepositContainer() {
            if (depositContainer) {
                opens++;
            }
            return depositContainer;
        }

        @Override
        public void closeContainer() {
            closes++;
        }

        @Override
        public void status(Status status, Macro macro) {
            statuses.add(status);
        }
    }
}
