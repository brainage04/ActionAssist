package io.github.brainage04.actionassist.gametest;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.client.ScreenAccess;
import io.github.brainage04.actionassist.core.Macro;
import io.github.brainage04.actionassist.core.MacroEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One loader- and version-independent scenario that plays both macros end to end on every supported
 * target. Only vanilla blocks and recipes are used, with two server-side stand-ins for the modded
 * mechanics: sneaking right-clicks on dirt with an empty hand yield "pebbles" (as ATM10 To the Sky's
 * KubeJS script does), and each crouch grows nearby wheat while right-clicking mature wheat with the
 * vein-mining key held harvests the whole row (as Squat Grow and FTB Ultimine do). Everything the mod
 * itself does, from key handling to inventory clicks, crafting, and chest deposits, runs for real.
 *
 * <p>Minecraft APIs used here are restricted to those with identical signatures from 1.21.1 to 26.2;
 * version-sensitive operations such as teleporting go through commands.
 */
public final class PlatformScenario {
    public static final String ENABLED_PROPERTY = "actionassist.platformGameTest";
    public static final String TARGET_PROPERTY = "actionassist.platformTarget";
    public static final String REPORT_FILE = "actionassist-platform-report.properties";
    private static final String PEBBLE_KEY = "key.actionassist.pebble_macro";
    private static final String CROP_KEY = "key.actionassist.crop_macro";
    private static final String SELECT_KEY = "key.actionassist.select_container";
    /** Pebble stand-ins in runs of {@link #PEBBLE_RUN}; sticks are configured but have no 2x2 recipe. */
    private static final List<Item> PEBBLES = List.of(
            Items.CLAY_BALL, Items.SNOWBALL, Items.STICK, Items.QUARTZ, Items.FLINT, Items.AMETHYST_SHARD, Items.HONEYCOMB);
    private static final Map<Item, Item> COMPACTED = Map.of(
            Items.CLAY, Items.CLAY_BALL,
            Items.SNOW_BLOCK, Items.SNOWBALL,
            Items.QUARTZ_BLOCK, Items.QUARTZ,
            Items.AMETHYST_BLOCK, Items.AMETHYST_SHARD,
            Items.HONEYCOMB_BLOCK, Items.HONEYCOMB);
    private static final int PEBBLE_RUN = 8;
    private static final int PEBBLE_TOTAL = PEBBLES.size() * PEBBLE_RUN * 5;
    private static final int PEBBLE_FREE_MAIN_SLOTS = 6;
    private static final int CROP_FREE_SLOTS = MacroEngine.MIN_FREE_SLOTS;
    private static final int MAX_HAND_BLOCKED_TICKS = 12;
    private static final int MIN_CROP_TICKS = 400;
    private static final int PAUSE_AT_TICK = 200;
    private static final int PHASE_TIMEOUT_TICKS = 4_800;
    private static final int BOOT_TIMEOUT_TICKS = 2_400;

    private static volatile boolean active;
    private static boolean standalone;
    private static volatile boolean finished;
    private static volatile Throwable failure;

    private static Phase phase = Phase.BOOT;
    private static int phaseTicks;
    private static volatile boolean setupComplete;
    private static volatile BlockPos origin;
    private static BlockPos chest;

    private static int deposits;
    private static boolean wasDepositing;
    private static int handBlockedTicks;
    private static int longestHandBlockedTicks;
    private static int clickingTicks;
    private static int sneakTicks;
    private static int sneakPresses;
    private static boolean sneakWasDown;
    private static int veinTicks;
    private static int usesAtPause;

    private static volatile int pebblesGenerated;
    private static volatile int overflow;
    private static volatile int uses;
    private static volatile int cropHarvests;
    private static volatile int veinHarvests;
    private static volatile int growthSteps;
    private static volatile Map<String, Integer> chestContents = Map.of();
    private static volatile Map<String, Integer> serverInventory = Map.of();
    private static boolean serverWasCrouching;
    private static final Properties report = new Properties();

    private PlatformScenario() {
    }

    /** Standalone runs launch straight into the fixture world with {@code --quickPlaySingleplayer}. */
    public static boolean standaloneEnabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    /** Starts the scenario inside an already open singleplayer world driven by another harness. */
    public static void startEmbedded(String target) {
        System.setProperty(TARGET_PROPERTY, target);
        standalone = false;
        active = true;
    }

    public static boolean finished() {
        return finished;
    }

    public static Throwable failure() {
        return failure;
    }

    public static void clientTick(Minecraft minecraft) {
        if (!active && standaloneEnabled() && !finished) {
            standalone = true;
            active = true;
        }
        if (!active || finished) {
            return;
        }
        try {
            tick(minecraft);
        } catch (Throwable error) {
            fail(minecraft, error);
        }
    }

    /** Squat Grow stand-in: every new crouch on the ground grows wheat within three blocks by one age. */
    public static void serverTick(MinecraftServer server) {
        if (!active || origin == null || phase.ordinal() < Phase.RUN_CROP.ordinal()) {
            return;
        }
        for (Player player : server.getPlayerList().getPlayers()) {
            boolean crouching = player.isCrouching() && player.onGround();
            if (crouching && !serverWasCrouching) {
                Level level = player.level();
                for (BlockPos crop : cropPositions()) {
                    BlockState state = level.getBlockState(crop);
                    if (state.getBlock() instanceof CropBlock block && !block.isMaxAge(state)) {
                        level.setBlock(crop, block.getStateForAge(block.getAge(state) + 1), 3);
                        growthSteps++;
                    }
                }
            }
            serverWasCrouching = crouching;
        }
    }

    /**
     * Server-side right-click hook. Dirt yields one pebble stand-in per sneaking empty-hand click;
     * mature wheat is harvested and replanted, along the whole row while the vein-mining key is held.
     *
     * @return whether the click was consumed
     */
    public static boolean useBlock(Player player, BlockPos position, boolean mainHand) {
        if (!active || origin == null || !mainHand) {
            return false;
        }
        Level level = player.level();
        BlockState state = level.getBlockState(position);
        if (state.is(Blocks.DIRT) && position.equals(origin)) {
            uses++;
            if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || pebblesGenerated >= PEBBLE_TOTAL) {
                return false;
            }
            Item pebble = PEBBLES.get((pebblesGenerated / PEBBLE_RUN) % PEBBLES.size());
            pebblesGenerated++;
            give(player, new ItemStack(pebble));
            return true;
        }
        if (!(state.getBlock() instanceof CropBlock) || !position.equals(origin)) {
            return false;
        }
        uses++;
        boolean vein = Minecraft.getInstance().options.keyPlayerList.isDown();
        boolean harvested = false;
        for (BlockPos crop : cropPositions()) {
            if (!vein && !crop.equals(position)) {
                continue;
            }
            BlockState cropState = level.getBlockState(crop);
            if (cropState.getBlock() instanceof CropBlock block && block.isMaxAge(cropState)) {
                level.setBlock(crop, block.getStateForAge(0), 3);
                give(player, new ItemStack(Items.WHEAT));
                give(player, new ItemStack(Items.WHEAT_SEEDS));
                cropHarvests++;
                if (!crop.equals(position)) {
                    veinHarvests++;
                }
                harvested = true;
            }
        }
        return harvested;
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            overflow += stack.getCount();
        }
    }

    private static void tick(Minecraft minecraft) throws Exception {
        if (phase == Phase.BOOT) {
            check(++phaseTicks < BOOT_TIMEOUT_TICKS, "Expected quick play to open the fixture world; the screen is "
                    + ScreenAccess.currentScreen(minecraft));
            if (minecraft.player != null && minecraft.level != null && minecraft.getSingleplayerServer() != null
                    && !ScreenAccess.isScreenOpen(minecraft)) {
                ActionAssist.LOGGER.info("[Platform GameTest] World ready on {}", target());
                submitPebbleSetup(minecraft);
                enter(Phase.SELECT_CONTAINER, "Selecting the deposit chest");
            }
            return;
        }
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case SELECT_CONTAINER -> selectContainer(minecraft);
            case START_PEBBLE -> startPebble(minecraft);
            case RUN_PEBBLE -> runPebble(minecraft);
            case VERIFY_PEBBLE -> verifyPebble(minecraft);
            case PLANT_CROPS -> plantCrops(minecraft);
            case START_CROP -> startCrop(minecraft);
            case RUN_CROP -> runCrop(minecraft);
            case FINISH -> finish(minecraft);
            default -> throw new IllegalStateException("Unexpected phase " + phase);
        }
    }

    private static void selectContainer(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        if (phaseTicks == 20) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(origin).add(0.0, 0.0, 0.5));
        } else if (phaseTicks == 21) {
            assertTargeted(minecraft, origin, "dirt");
            click(minecraft, SELECT_KEY);
        } else if (phaseTicks == 23) {
            check(ClientRuntime.depositContainer() == null, "Expected F6 on dirt not to select a deposit container");
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(chest));
        } else if (phaseTicks == 24) {
            assertTargeted(minecraft, chest, "deposit chest");
            click(minecraft, SELECT_KEY);
        } else if (phaseTicks == 26) {
            check(chest.equals(ClientRuntime.depositContainer()),
                    "Expected F6 to select the chest at " + chest + ", found " + ClientRuntime.depositContainer());
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(origin).add(0.0, 0.0, 0.5));
            enter(Phase.START_PEBBLE, "Starting the pebble macro");
        }
    }

    private static void startPebble(Minecraft minecraft) {
        if (phaseTicks == 2) {
            assertTargeted(minecraft, origin, "dirt");
            click(minecraft, PEBBLE_KEY);
            resetObservations();
            enter(Phase.RUN_PEBBLE, "Running the pebble macro");
        }
    }

    private static void runPebble(Minecraft minecraft) {
        MacroEngine engine = ClientRuntime.engine();
        if (phaseTicks <= 2) {
            return;
        }
        check(engine.activeMacro() == Macro.PEBBLE,
                "Expected the pebble macro to keep running; stopped after " + phaseTicks + " ticks, "
                        + pebblesGenerated + " pebbles, " + deposits + " deposits");
        observeInputs(minecraft, engine);
        if (!engine.isDepositing() && !minecraft.player.getMainHandItem().isEmpty()) {
            handBlockedTicks++;
            longestHandBlockedTicks = Math.max(longestHandBlockedTicks, handBlockedTicks);
        } else {
            handBlockedTicks = 0;
        }
        assertPebbleFiller(minecraft);
        if (pebblesGenerated < PEBBLE_TOTAL) {
            check(phaseTicks < PHASE_TIMEOUT_TICKS, "Expected " + PEBBLE_TOTAL + " pebbles within "
                    + PHASE_TIMEOUT_TICKS + " ticks; generated " + pebblesGenerated + " with " + deposits + " deposits");
            return;
        }
        if (engine.isDepositing()) {
            return;
        }
        click(minecraft, PEBBLE_KEY);
        check(longestHandBlockedTicks <= MAX_HAND_BLOCKED_TICKS,
                "Expected the main hand cleared promptly; occupied for " + longestHandBlockedTicks + " ticks");
        check(sneakTicks == clickingTicks, "Expected sneak held on every clicking tick (" + sneakTicks + "/" + clickingTicks + ")");
        check(veinTicks == 0, "Expected the pebble macro not to hold the vein-mining key");
        check(deposits >= 2, "Expected at least two deposits, found " + deposits);
        report.setProperty("pebble.ticks", Integer.toString(phaseTicks));
        report.setProperty("pebble.deposits", Integer.toString(deposits));
        report.setProperty("pebble.longestHandBlockedTicks", Integer.toString(longestHandBlockedTicks));
        enter(Phase.VERIFY_PEBBLE, "Verifying pebble conservation");
    }

    private static void verifyPebble(Minecraft minecraft) {
        if (phaseTicks == 2) {
            check(ClientRuntime.engine().activeMacro() == null, "Expected the pebble key to stop the macro");
            check(!minecraft.options.keyShift.isDown(), "Expected stopping to release sneak");
        }
        if (phaseTicks == 10) {
            snapshotServer(minecraft);
        }
        if (phaseTicks < 20) {
            return;
        }
        check(minecraft.player.inventoryMenu.getCarried().isEmpty(), "Expected an empty cursor");
        for (int slot = 1; slot <= 4; slot++) {
            check(!minecraft.player.inventoryMenu.getSlot(slot).hasItem(), "Expected an empty crafting grid");
        }
        check(overflow == 0, "Expected every pebble to fit in the inventory; " + overflow + " overflowed");
        Map<String, Integer> equivalents = new TreeMap<>();
        Map<String, Integer> products = new TreeMap<>();
        addEquivalents(chestContents, equivalents, products);
        addEquivalents(serverInventory, equivalents, products);
        Map<String, Integer> expected = new TreeMap<>();
        for (int index = 0; index < PEBBLE_TOTAL; index++) {
            expected.merge(id(PEBBLES.get((index / PEBBLE_RUN) % PEBBLES.size())), 1, Integer::sum);
        }
        check(expected.equals(equivalents),
                "Expected every generated pebble in the chest or inventory, counting compacted blocks as four: expected "
                        + expected + ", found " + equivalents + " (chest " + chestContents + ", inventory " + serverInventory + ")");
        check(!products.isEmpty(), "Expected at least one compacted block");
        check(!chestContents.isEmpty(), "Expected deposits in the chest");
        report.setProperty("pebble.conserved", "true");
        report.setProperty("pebble.compactedTypes", String.join(",", new TreeSet<>(products.keySet())));
        report.setProperty("pebble.chest", chestContents.toString());
        submitCropSetup(minecraft);
        enter(Phase.PLANT_CROPS, "Pebble macro passed; preparing wheat");
    }

    private static void plantCrops(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 10) {
            return;
        }
        submitCropPlanting(minecraft);
        enter(Phase.START_CROP, "Starting the crop macro");
    }

    private static void startCrop(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 10) {
            return;
        }
        if (phaseTicks == 10) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(origin).add(0.0, 0.05, 0.3));
            return;
        }
        assertTargeted(minecraft, origin, "centre wheat");
        click(minecraft, CROP_KEY);
        resetObservations();
        enter(Phase.RUN_CROP, "Running the crop macro");
    }

    private static void runCrop(Minecraft minecraft) {
        MacroEngine engine = ClientRuntime.engine();
        if (phaseTicks <= 2) {
            return;
        }
        if (phaseTicks >= PAUSE_AT_TICK && phaseTicks <= PAUSE_AT_TICK + 40) {
            exercisePause(minecraft, engine);
            return;
        }
        check(engine.activeMacro() == Macro.CROP,
                "Expected the crop macro to keep running; stopped after " + phaseTicks + " ticks with " + deposits + " deposits");
        observeInputs(minecraft, engine);
        assertCropFiller(minecraft);
        if (phaseTicks % 10 == 0) {
            snapshotServer(minecraft);
        }
        boolean wheatDeposited = chestContents.getOrDefault(id(Items.WHEAT), 0) > 0;
        if (phaseTicks < MIN_CROP_TICKS || deposits < 1 || !wheatDeposited || veinHarvests == 0) {
            check(phaseTicks < PHASE_TIMEOUT_TICKS, "Expected growth, a vein harvest, and a deposit; deposits="
                    + deposits + ", chest=" + chestContents + ", harvests=" + cropHarvests + ", vein=" + veinHarvests
                    + ", growth=" + growthSteps);
            return;
        }
        if (engine.isDepositing()) {
            return;
        }
        int tolerance = 2 * (deposits + 2) + 2;
        check(Math.abs(sneakPresses * 2 - clickingTicks) <= tolerance,
                "Expected one sneak press every other clicking tick; " + sneakPresses + " over " + clickingTicks);
        check(veinTicks == clickingTicks, "Expected the vein-mining key held on every clicking tick (" + veinTicks + "/" + clickingTicks + ")");
        check(growthSteps > 0, "Expected crouches to grow wheat");
        click(minecraft, CROP_KEY);
        report.setProperty("crop.ticks", Integer.toString(phaseTicks));
        report.setProperty("crop.deposits", Integer.toString(deposits));
        report.setProperty("crop.harvests", Integer.toString(cropHarvests));
        report.setProperty("crop.veinHarvests", Integer.toString(veinHarvests));
        report.setProperty("crop.chest", chestContents.toString());
        enter(Phase.FINISH, "Crop macro passed");
    }

    /** Opening a screen must pause the macro and release its held keys; closing it resumes. */
    private static void exercisePause(Minecraft minecraft, MacroEngine engine) {
        int tick = phaseTicks - PAUSE_AT_TICK;
        if (tick == 0) {
            sneakWasDown = false;
            if (engine.isDepositing()) {
                phaseTicks--;
                return;
            }
            KeyMapping.click(minecraft.options.keyInventory.getDefaultKey());
        } else if (tick == 3) {
            check(ScreenAccess.isScreenOpen(minecraft), "Expected the inventory key to open the inventory screen");
            check(!minecraft.options.keyShift.isDown(), "Expected an open screen to release sneak");
            check(!minecraft.options.keyPlayerList.isDown(), "Expected an open screen to release the vein-mining key");
            usesAtPause = uses;
        } else if (tick == 20) {
            check(uses == usesAtPause, "Expected no right-clicks while a screen is open");
            check(engine.activeMacro() == Macro.CROP, "Expected the macro to stay enabled while paused");
            minecraft.player.closeContainer();
        } else if (tick == 40) {
            // The resumed macro may already be depositing into its chest.
            check(!ScreenAccess.isScreenOpen(minecraft) || engine.isDepositing(), "Expected the inventory screen to close");
            check(uses > usesAtPause, "Expected right-clicks to resume after the screen closed");
            report.setProperty("crop.pauseReleasedInputs", "true");
        }
    }

    private static void finish(Minecraft minecraft) throws IOException {
        if (phaseTicks == 2) {
            check(ClientRuntime.engine().activeMacro() == null, "Expected the crop key to stop the macro");
            check(!minecraft.options.keyShift.isDown(), "Expected stopping to release sneak");
            check(!minecraft.options.keyPlayerList.isDown(), "Expected stopping to release the vein-mining key");
        }
        if (phaseTicks < 20) {
            return;
        }
        report.setProperty("target", target());
        report.setProperty("result", "pass");
        if (standalone) {
            try (var output = Files.newOutputStream(Path.of(REPORT_FILE))) {
                report.store(output, "Action Assist platform GameTest");
            }
        }
        ActionAssist.LOGGER.info("ACTIONASSIST_PLATFORM_GAMETEST_PASS {} {}", target(), report);
        finished = true;
        active = false;
        if (standalone) {
            minecraft.stop();
        }
    }

    private static void observeInputs(Minecraft minecraft, MacroEngine engine) {
        boolean depositing = engine.isDepositing();
        boolean depositJustEnded = wasDepositing && !depositing;
        if (depositing && !wasDepositing) {
            deposits++;
        }
        wasDepositing = depositing;
        // A deposit releases the held keys; the macro re-applies them on the following tick.
        if (depositing || depositJustEnded || ScreenAccess.isScreenOpen(minecraft)) {
            sneakWasDown = false;
            return;
        }
        boolean sneak = minecraft.options.keyShift.isDown();
        clickingTicks++;
        if (sneak) {
            sneakTicks++;
            if (!sneakWasDown) {
                sneakPresses++;
            }
        }
        sneakWasDown = sneak;
        if (minecraft.options.keyPlayerList.isDown()) {
            veinTicks++;
        }
    }

    private static void resetObservations() {
        deposits = 0;
        wasDepositing = false;
        handBlockedTicks = 0;
        longestHandBlockedTicks = 0;
        clickingTicks = 0;
        sneakTicks = 0;
        sneakPresses = 0;
        sneakWasDown = false;
        veinTicks = 0;
        chestContents = Map.of();
    }

    private static void addEquivalents(Map<String, Integer> items, Map<String, Integer> equivalents, Map<String, Integer> products) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Item base = null;
            for (Map.Entry<Item, Item> compacted : COMPACTED.entrySet()) {
                if (id(compacted.getKey()).equals(entry.getKey())) {
                    base = compacted.getValue();
                }
            }
            if (base != null) {
                products.merge(entry.getKey(), entry.getValue(), Integer::sum);
                equivalents.merge(id(base), 4 * entry.getValue(), Integer::sum);
            } else if (PEBBLES.stream().anyMatch(pebble -> id(pebble).equals(entry.getKey()))) {
                equivalents.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
    }

    private static void submitPebbleSetup(Minecraft minecraft) {
        submitSetup(minecraft, (server, player) -> {
            BlockPos base = player.blockPosition();
            origin = base;
            chest = base.offset(2, 0, 1);
            Level level = player.level();
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 5; z++) {
                    for (int y = -1; y <= 3; y++) {
                        level.setBlock(base.offset(x, y, z),
                                (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                    }
                }
            }
            level.setBlock(origin, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            run(server, "difficulty peaceful");
            run(server, "gamemode survival @a");
            Inventory inventory = player.getInventory();
            inventory.clearContent();
            for (int slot = 9; slot <= 35 - PEBBLE_FREE_MAIN_SLOTS; slot++) {
                inventory.setItem(slot, new ItemStack(Items.PAPER));
            }
            inventory.setItem(8, new ItemStack(Items.WOODEN_SHOVEL));
            positionPlayer(server);
        });
    }

    private static void submitCropSetup(Minecraft minecraft) {
        submitSetup(minecraft, (server, player) -> {
            Level level = player.level();
            if (level.getBlockEntity(chest) instanceof Container container) {
                container.clearContent();
            }
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos crop : cropPositions()) {
                level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            }
            Inventory inventory = player.getInventory();
            inventory.clearContent();
            for (int slot = 1; slot < 36 - CROP_FREE_SLOTS + 1; slot++) {
                inventory.setItem(slot, new ItemStack(Items.PAPER));
            }
            positionPlayer(server);
        });
    }

    /** Wheat needs sky light, which is only recomputed a few ticks after the pebble dirt is removed. */
    private static void submitCropPlanting(Minecraft minecraft) {
        submitSetup(minecraft, (server, player) -> {
            for (BlockPos crop : cropPositions()) {
                player.level().setBlock(crop, Blocks.WHEAT.defaultBlockState(), 3);
            }
        });
    }

    private static void positionPlayer(MinecraftServer server) {
        run(server, "tp @p " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 1.35) + " 180 45");
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static BlockPos[] cropPositions() {
        return new BlockPos[] {origin.west(), origin, origin.east()};
    }

    private static void submitSetup(Minecraft minecraft, ServerSetup setup) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        setupComplete = false;
        server.execute(() -> {
            Player player = server.getPlayerList().getPlayer(playerId);
            setup.apply(server, player);
            setupComplete = true;
        });
    }

    /** Reads block entities and the authoritative inventory on the server thread. */
    private static void snapshotServer(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            Player player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            if (player.level().getBlockEntity(chest) instanceof Container container) {
                Map<String, Integer> contents = new TreeMap<>();
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    addStack(contents, container.getItem(slot));
                }
                chestContents = contents;
            }
            Map<String, Integer> inventory = new TreeMap<>();
            for (int slot = 0; slot < 36; slot++) {
                addStack(inventory, player.getInventory().getItem(slot));
            }
            serverInventory = inventory;
        });
    }

    private static void addStack(Map<String, Integer> contents, ItemStack stack) {
        if (!stack.isEmpty()) {
            contents.merge(id(stack.getItem()), stack.getCount(), Integer::sum);
        }
    }

    private static void assertPebbleFiller(Minecraft minecraft) {
        Inventory inventory = minecraft.player.getInventory();
        check(inventory.getItem(8).is(Items.WOODEN_SHOVEL), "Expected the reserved shovel to stay in slot 8");
        for (int slot = 9; slot <= 35 - PEBBLE_FREE_MAIN_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            check(stack.is(Items.PAPER) && stack.getCount() == 1, "Expected reserved paper in slot " + slot + ", found " + stack);
        }
    }

    private static void assertCropFiller(Minecraft minecraft) {
        Inventory inventory = minecraft.player.getInventory();
        for (int slot = 1; slot < 36 - CROP_FREE_SLOTS + 1; slot++) {
            ItemStack stack = inventory.getItem(slot);
            check(stack.is(Items.PAPER) && stack.getCount() == 1, "Expected reserved paper in slot " + slot + ", found " + stack);
        }
    }

    private static void assertTargeted(Minecraft minecraft, BlockPos expected, String description) {
        boolean targeted = minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected);
        check(targeted, "Expected the crosshair on the " + description + " at " + expected + ", found "
                + (minecraft.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : minecraft.hitResult)
                + " while " + minecraft.level.getBlockState(expected) + " is there; player at " + minecraft.player.position());
    }

    private static void click(Minecraft minecraft, String name) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals(name)) {
                KeyMapping.click(mapping.getDefaultKey());
                return;
            }
        }
        throw new AssertionError("Missing key mapping " + name);
    }

    private static String id(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    private static String target() {
        return System.getProperty(TARGET_PROPERTY, "unknown");
    }

    private static void enter(Phase next, String message) {
        phase = next;
        phaseTicks = 0;
        ActionAssist.LOGGER.info("[Platform GameTest] {}", message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fail(Minecraft minecraft, Throwable error) {
        failure = error;
        finished = true;
        active = false;
        KeyMapping.releaseAll();
        ActionAssist.LOGGER.error("ACTIONASSIST_PLATFORM_GAMETEST_FAIL {}", target(), error);
        if (standalone) {
            List<String> lines = new ArrayList<>();
            lines.add("target=" + target());
            lines.add("result=fail");
            try {
                Files.write(Path.of(REPORT_FILE), lines);
            } catch (IOException ignored) {
                // The log line above already records the failure.
            }
            System.exit(1);
        }
    }

    private enum Phase {
        BOOT,
        SELECT_CONTAINER,
        START_PEBBLE,
        RUN_PEBBLE,
        VERIFY_PEBBLE,
        PLANT_CROPS,
        START_CROP,
        RUN_CROP,
        FINISH
    }

    @FunctionalInterface
    private interface ServerSetup {
        void apply(MinecraftServer server, Player player);
    }
}
