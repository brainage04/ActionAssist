package io.github.brainage04.actionassist.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.core.Macro;
import io.github.brainage04.actionassist.core.MacroEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Plays both macros in a 1.21.1 NeoForge client running the mods and scripts ATM10 To the Sky uses for
 * them: the pack's own Ex Deorum pebble KubeJS script (sneak + empty-hand right-click on dirt), Ex
 * Deorum's 2x2 pebble recipes, Squat Grow with the pack's configuration, Mystical Agriculture crops,
 * and FTB Ultimine right-click crop harvesting.
 */
@EventBusSubscriber(modid = ActionAssist.MOD_ID, value = Dist.CLIENT)
public final class ModpackCompatibilityClientGameTest {
    private static final String ENABLED_PROPERTY = "actionassist.modpackGameTest";
    private static final String WORLD_ID = "actionassist-modpack-gametest";
    private static final String RECORDING_START_ENV = "CLIENT_GAMETEST_RECORDING_START_SIGNAL";
    private static final String RECORDING_READY_ENV = "CLIENT_GAMETEST_RECORDING_READY_SIGNAL";
    private static final String PEBBLE_KEY = "key.actionassist.pebble_macro";
    private static final String CROP_KEY = "key.actionassist.crop_macro";
    private static final String SELECT_KEY = "key.actionassist.select_container";
    private static final ResourceLocation INFERIUM_CROP_ID = ResourceLocation.parse("mysticalagriculture:inferium_crop");
    private static final ResourceLocation INFERIUM_FARMLAND_ID = ResourceLocation.parse("mysticalagriculture:inferium_farmland");
    private static final String INFERIUM_ESSENCE = "mysticalagriculture:inferium_essence";
    private static final Set<String> COMPACTED_BLOCKS = Set.of(
            "minecraft:cobblestone",
            "minecraft:andesite",
            "minecraft:basalt",
            "minecraft:blackstone",
            "minecraft:cobbled_deepslate",
            "minecraft:diorite",
            "minecraft:granite");
    /** Main-inventory slots left free by the pebble phase's reserved filler. */
    private static final int PEBBLE_FREE_MAIN_SLOTS = 5;
    /** Slots left free by the crop phase's reserved filler: the minimum a macro starts with. */
    private static final int CROP_FREE_SLOTS = MacroEngine.MIN_FREE_SLOTS;
    private static final int PHASE_TIMEOUT_TICKS = 6_000;
    /** Each macro runs at least this long, so sustained operation across many cycles is exercised. */
    private static final int MIN_PHASE_TICKS = 600;
    /** Longest the pebble macro may leave the hand occupied outside a deposit. */
    private static final int MAX_HAND_BLOCKED_TICKS = 12;

    private static Phase phase = Phase.BOOT;
    private static boolean worldRequested;
    private static volatile boolean setupComplete;
    private static BlockPos origin;
    private static BlockPos dirt;
    private static BlockPos chest;
    private static int phaseTicks;
    private static int deposits;
    private static boolean wasDepositing;
    private static int handBlockedTicks;
    private static int longestHandBlockedTicks;
    private static int sneakTicks;
    private static int sneakPresses;
    private static boolean sneakWasDown;
    private static int veinTicks;
    private static int macroTicks;
    private static volatile Map<String, Integer> chestContents = Map.of();
    private static volatile boolean ultiminePressedOnServer;
    private static volatile boolean veinHarvestObserved;
    private static volatile int targetCropHarvests;
    private static final boolean[] sideCropWasMature = new boolean[2];
    private static boolean targetCropWasMature;
    private static boolean cropsPlanted;
    private static int plantedAt;

    private ModpackCompatibilityClientGameTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        try {
            tick(Minecraft.getInstance());
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    /** Samples crops every server tick: a matured crop is harvested by the very next right-click. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY) || phase != Phase.FARM_CROPS || origin == null) {
            return;
        }
        try {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                observeCrops(player.serverLevel(), player);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void tick(Minecraft minecraft) throws Exception {
        if (phase == Phase.BOOT) {
            boot(minecraft);
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        phaseTicks++;
        switch (phase) {
            case WAIT_FOR_RECORDER -> waitForRecorder(minecraft);
            case SELECT_CONTAINER -> selectContainer(minecraft);
            case START_PEBBLES -> startPebbles(minecraft);
            case FARM_PEBBLES -> farmPebbles(minecraft);
            case START_CROPS -> startCrops(minecraft);
            case FARM_CROPS -> farmCrops(minecraft);
            case FINISH -> finish(minecraft);
            default -> throw new IllegalStateException("Unexpected phase " + phase);
        }
    }

    private static void boot(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.level != null) {
            enter(Phase.WAIT_FOR_RECORDER, "Integrated test world ready");
            signalRecorderStart();
            return;
        }
        if (worldRequested || minecraft.screen == null) {
            return;
        }
        worldRequested = true;
        ActionAssist.LOGGER.info("Creating Action Assist ATM10TTS compatibility test world");
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(
                "Action Assist Modpack GameTest",
                GameType.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                rules,
                WorldDataConfiguration.DEFAULT
        );
        minecraft.createWorldOpenFlows().createFreshLevel(
                WORLD_ID,
                settings,
                WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createNormalWorldDimensions,
                minecraft.screen
        );
    }

    private static void waitForRecorder(Minecraft minecraft) {
        String readyPath = System.getenv(RECORDING_READY_ENV);
        if (readyPath != null && !readyPath.isBlank() && !Files.exists(Path.of(readyPath))) {
            if (phaseTicks > 400) {
                throw new AssertionError("Recorder did not acknowledge the client within 400 ticks");
            }
            return;
        }
        validateLoadedMods();
        validateDefaultKeys(minecraft);
        submitPebbleSetup(minecraft);
        enter(Phase.SELECT_CONTAINER, "Selecting the deposit chest with F6");
    }

    private static void selectContainer(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        if (phaseTicks == 20) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(chest));
            return;
        }
        if (phaseTicks == 21) {
            assertTargeted(minecraft, chest, "deposit chest");
            click(minecraft, SELECT_KEY);
            return;
        }
        assertTrue(chest.equals(ClientRuntime.depositContainer()),
                "Expected F6 to select the chest at " + chest + ", found " + ClientRuntime.depositContainer());
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(dirt).add(0.0, 0.0, 0.5));
        enter(Phase.START_PEBBLES, "Starting the pebble macro on the dirt block");
    }

    private static void startPebbles(Minecraft minecraft) {
        if (phaseTicks < 2) {
            return;
        }
        assertTargeted(minecraft, dirt, "pebble dirt");
        click(minecraft, PEBBLE_KEY);
        resetObservations();
        enter(Phase.FARM_PEBBLES, "Farming pebbles with the pack's KubeJS script, compacting, and depositing");
    }

    private static void farmPebbles(Minecraft minecraft) {
        MacroEngine engine = ClientRuntime.engine();
        if (phaseTicks <= 2) {
            return;
        }
        assertTrue(engine.activeMacro() == Macro.PEBBLE,
                "Expected the pebble macro to keep running; it stopped after " + phaseTicks + " ticks with "
                        + deposits + " deposits and chest " + chestContents);
        observeInputs(minecraft, engine);
        if (!engine.isDepositing() && !minecraft.player.getMainHandItem().isEmpty()) {
            handBlockedTicks++;
            longestHandBlockedTicks = Math.max(longestHandBlockedTicks, handBlockedTicks);
        } else {
            handBlockedTicks = 0;
        }
        if (phaseTicks % 10 == 0) {
            snapshotServer(minecraft);
        }
        assertReservedPebbleFiller(minecraft);

        Map<String, Integer> contents = chestContents;
        boolean pebblesDeposited = contents.keySet().stream().anyMatch(id -> id.endsWith("_pebble"));
        boolean compacted = contents.keySet().stream().anyMatch(COMPACTED_BLOCKS::contains)
                || inventoryContains(minecraft, COMPACTED_BLOCKS);
        if (phaseTicks < MIN_PHASE_TICKS || deposits < 2 || !pebblesDeposited || !compacted) {
            assertTrue(phaseTicks < PHASE_TIMEOUT_TICKS,
                    "Expected two deposits of pebbles and a compacted block within " + PHASE_TIMEOUT_TICKS
                            + " ticks; deposits=" + deposits + ", chest=" + contents
                            + ", compacted=" + compacted);
            return;
        }
        assertTrue(longestHandBlockedTicks <= MAX_HAND_BLOCKED_TICKS,
                "Expected the pebble macro to clear the main hand promptly; it stayed occupied for "
                        + longestHandBlockedTicks + " ticks");
        assertTrue(sneakTicks == macroTicks,
                "Expected sneak held on every clicking tick (" + sneakTicks + "/" + macroTicks + ")");
        assertTrue(veinTicks == 0, "Expected the pebble macro not to hold the vein-mining key");

        click(minecraft, PEBBLE_KEY);
        ActionAssist.LOGGER.info("[Modpack Client GameTest] Pebble macro: {} deposits, chest {}", deposits, contents);
        submitCropSetup(minecraft);
        enter(Phase.START_CROPS, "Pebble macro passed; preparing Mystical Agriculture crops");
    }

    private static void startCrops(Minecraft minecraft) throws ReflectiveOperationException {
        if (phaseTicks == 1) {
            assertTrue(ClientRuntime.engine().activeMacro() == null, "Expected G5 to stop the pebble macro");
            assertTrue(!minecraft.options.keyShift.isDown(), "Expected stopping to release sneak");
        }
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        if (!cropsPlanted) {
            cropsPlanted = true;
            submitCropPlanting(minecraft);
            plantedAt = phaseTicks;
            return;
        }
        if (phaseTicks < plantedAt + 10) {
            return;
        }
        if (phaseTicks == plantedAt + 10) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(origin).add(0.0, 0.1, 0.4));
            return;
        }
        assertTargeted(minecraft, origin, "centre inferium crop");
        click(minecraft, CROP_KEY);
        resetObservations();
        enter(Phase.FARM_CROPS, "Growing with Squat Grow, vein-harvesting with FTB Ultimine, and depositing");
    }

    private static void farmCrops(Minecraft minecraft) throws ReflectiveOperationException {
        MacroEngine engine = ClientRuntime.engine();
        if (phaseTicks == 2) {
            giveEssenceStack(minecraft);
        }
        if (phaseTicks <= 2) {
            return;
        }
        assertTrue(engine.activeMacro() == Macro.CROP,
                "Expected the crop macro to keep running; it stopped after " + phaseTicks + " ticks with "
                        + deposits + " deposits and chest " + chestContents);
        observeInputs(minecraft, engine);
        if (phaseTicks % 10 == 0) {
            snapshotServer(minecraft);
        }
        assertReservedCropFiller(minecraft);

        boolean essenceDeposited = chestContents.getOrDefault(INFERIUM_ESSENCE, 0) > 0;
        if (phaseTicks < MIN_PHASE_TICKS
                || deposits < 1
                || !essenceDeposited
                || !veinHarvestObserved
                || !ultiminePressedOnServer) {
            assertTrue(phaseTicks < PHASE_TIMEOUT_TICKS,
                    "Expected Squat Grow growth, an FTB Ultimine vein harvest, and a deposit within "
                            + PHASE_TIMEOUT_TICKS + " ticks; deposits=" + deposits + ", chest=" + chestContents
                            + ", veinHarvest=" + veinHarvestObserved + ", targetHarvests=" + targetCropHarvests
                            + ", ultiminePressed=" + ultiminePressedOnServer);
            return;
        }
        int tolerance = 2 * (deposits + 1) + 2;
        assertTrue(Math.abs(sneakPresses * 2 - macroTicks) <= tolerance,
                "Expected one sneak press every other clicking tick; " + sneakPresses + " presses over "
                        + macroTicks + " ticks");
        assertTrue(veinTicks == macroTicks,
                "Expected FTB Ultimine's key held on every clicking tick (" + veinTicks + "/" + macroTicks + ")");

        click(minecraft, CROP_KEY);
        ActionAssist.LOGGER.info("[Modpack Client GameTest] Crop macro: {} deposits, chest {}", deposits, chestContents);
        enter(Phase.FINISH, "Crop macro passed");
    }

    private static void finish(Minecraft minecraft) throws ReflectiveOperationException {
        if (phaseTicks == 2) {
            assertTrue(ClientRuntime.engine().activeMacro() == null, "Expected G4 to stop the crop macro");
            assertTrue(!ultimineKey().isDown(), "Expected stopping to release FTB Ultimine's key");
            assertTrue(!minecraft.options.keyShift.isDown(), "Expected stopping to release sneak");
        }
        if (phaseTicks < 60) {
            return;
        }
        KeyMapping.releaseAll();
        ActionAssist.LOGGER.info("ACTIONASSIST_MODPACK_GAMETEST_PASS: pebble macro (KubeJS pebbles, compaction, hotbar clearing, deposit) and crop macro (Squat Grow, FTB Ultimine vein harvest, deposit) passed");
        minecraft.stop();
    }

    private static void observeInputs(Minecraft minecraft, MacroEngine engine) {
        boolean depositing = engine.isDepositing();
        boolean depositJustEnded = wasDepositing && !depositing;
        if (depositing && !wasDepositing) {
            deposits++;
        }
        wasDepositing = depositing;
        // The deposit released the held inputs; the macro re-applies them on the following tick.
        if (depositing || depositJustEnded || minecraft.screen != null) {
            sneakWasDown = false;
            return;
        }
        boolean sneak = minecraft.options.keyShift.isDown();
        macroTicks++;
        if (sneak) {
            sneakTicks++;
            if (!sneakWasDown) {
                sneakPresses++;
            }
        }
        sneakWasDown = sneak;
        try {
            if (ultimineKey().isDown()) {
                veinTicks++;
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Expected FTB Ultimine's key mapping", exception);
        }
    }

    private static void resetObservations() {
        deposits = 0;
        wasDepositing = false;
        handBlockedTicks = 0;
        longestHandBlockedTicks = 0;
        sneakTicks = 0;
        sneakPresses = 0;
        sneakWasDown = false;
        veinTicks = 0;
        macroTicks = 0;
        chestContents = Map.of();
    }

    /** Reads server-owned state on the server thread, where block entities are accessible. */
    private static void snapshotServer(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(chest) instanceof Container container) {
                Map<String, Integer> contents = new HashMap<>();
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        contents.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                    }
                }
                chestContents = Map.copyOf(contents);
            }
        });
    }

    /**
     * The macro clicks only the centre crop, so an age reset of a side crop after it matured can only
     * come from FTB Ultimine harvesting the connected crops while its key is held.
     */
    private static void observeCrops(ServerLevel level, ServerPlayer player) {
        CropBlock crop = inferiumCrop();
        BlockPos[] sides = {origin.west(), origin.east()};
        for (int index = 0; index < sides.length; index++) {
            var state = level.getBlockState(sides[index]);
            if (state.getBlock() != crop) {
                continue;
            }
            if (crop.isMaxAge(state)) {
                sideCropWasMature[index] = true;
            } else if (sideCropWasMature[index]) {
                veinHarvestObserved = true;
                sideCropWasMature[index] = false;
            }
        }
        var target = level.getBlockState(origin);
        if (target.getBlock() == crop) {
            if (crop.isMaxAge(target)) {
                targetCropWasMature = true;
            } else if (targetCropWasMature) {
                targetCropHarvests++;
                targetCropWasMature = false;
            }
        }
        try {
            Class<?> ultimine = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
            Object instance = ultimine.getField("instance").get(null);
            Object data = ultimine.getMethod("getOrCreatePlayerData", net.minecraft.world.entity.player.Player.class)
                    .invoke(instance, player);
            if ((boolean) data.getClass().getMethod("isPressed").invoke(data)) {
                ultiminePressedOnServer = true;
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not read FTB Ultimine's server-side key state", exception);
        }
    }

    /**
     * Stands in for earlier harvests: an unreserved full essence stack means the next harvested essence
     * needs a fresh slot, so the natural harvest itself fills the inventory and triggers the deposit.
     */
    private static void giveEssenceStack(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            var essence = BuiltInRegistries.ITEM.get(ResourceLocation.parse(INFERIUM_ESSENCE));
            player.getInventory().setItem(35, new ItemStack(essence, 64));
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static void submitPebbleSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            origin = testOrigin(player);
            dirt = origin;
            chest = origin.offset(2, 0, 1);
            clearTestArea(level, origin);
            level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            fillInventory(player, PEBBLE_FREE_MAIN_SLOTS, true);
            positionPlayer(player, origin);
        });
    }

    private static void submitCropSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            clearDroppedItems(level, origin);
            if (level.getBlockEntity(chest) instanceof Container container) {
                container.clearContent();
            }
            level.setBlockAndUpdate(dirt, Blocks.AIR.defaultBlockState());
            for (BlockPos crop : new BlockPos[] {origin.west(), origin, origin.east()}) {
                level.setBlockAndUpdate(crop.below(), inferiumFarmland().defaultBlockState());
            }
            fillInventory(player, CROP_FREE_SLOTS, false);
            positionPlayer(player, origin);
        });
    }

    /**
     * Crops need sky light to survive neighbour updates, and the light where the pebble dirt stood is
     * only recomputed after it is removed, so planting happens in a later tick than the area setup.
     */
    private static void submitCropPlanting(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            for (BlockPos crop : new BlockPos[] {origin.west(), origin, origin.east()}) {
                level.setBlockAndUpdate(crop, inferiumCrop().getStateForAge(0));
            }
        });
    }

    /**
     * Occupies every slot except {@code free} with reserved single paper items: the macros must never
     * touch them. The pebble layout keeps the whole hotbar except a shovel in slot 8 empty.
     */
    private static void fillInventory(ServerPlayer player, int free, boolean pebbleLayout) {
        var inventory = player.getInventory();
        inventory.clearContent();
        int firstFiller = pebbleLayout ? 9 : 0;
        int lastFiller = 35 - free;
        for (int slot = firstFiller; slot <= lastFiller; slot++) {
            inventory.setItem(slot, new ItemStack(Items.PAPER));
        }
        if (pebbleLayout) {
            inventory.setItem(8, new ItemStack(Items.WOODEN_SHOVEL));
        } else {
            inventory.setItem(0, ItemStack.EMPTY);
            inventory.setItem(lastFiller + 1, new ItemStack(Items.PAPER));
        }
        inventory.selected = 0;
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private static void assertReservedPebbleFiller(Minecraft minecraft) {
        var inventory = minecraft.player.getInventory();
        assertTrue(inventory.getItem(8).is(Items.WOODEN_SHOVEL), "Expected the reserved shovel to stay in slot 8");
        for (int slot = 9; slot <= 35 - PEBBLE_FREE_MAIN_SLOTS; slot++) {
            assertTrue(inventory.getItem(slot).is(Items.PAPER) && inventory.getItem(slot).getCount() == 1,
                    "Expected reserved filler in slot " + slot + ", found " + inventory.getItem(slot));
        }
    }

    private static void assertReservedCropFiller(Minecraft minecraft) {
        var inventory = minecraft.player.getInventory();
        int reserved = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).is(Items.PAPER)) {
                assertTrue(inventory.getItem(slot).getCount() == 1, "Expected reserved paper to stay a single item");
                reserved++;
            }
        }
        assertTrue(reserved == 36 - CROP_FREE_SLOTS, "Expected " + (36 - CROP_FREE_SLOTS) + " reserved filler slots, found " + reserved);
    }

    private static void submitSetup(Minecraft minecraft, ServerSetup setup) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            throw new AssertionError("Expected an integrated server and local player");
        }
        setupComplete = false;
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                throw new AssertionError("Expected the client player on the integrated server");
            }
            player.setGameMode(GameType.SURVIVAL);
            setup.apply(player.serverLevel(), player);
            setupComplete = true;
        });
    }

    private static BlockPos testOrigin(ServerPlayer player) {
        BlockPos current = player.blockPosition();
        return new BlockPos(current.getX(), 200, current.getZ());
    }

    private static void clearTestArea(ServerLevel level, BlockPos target) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 4; z++) {
                for (int y = -1; y <= 4; y++) {
                    level.setBlockAndUpdate(target.offset(x, y, z),
                            y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** Stands the player on the floor directly south of the target, touching its south face. */
    private static void positionPlayer(ServerPlayer player, BlockPos target) {
        player.teleportTo(player.serverLevel(), target.getX() + 0.5, target.getY(), target.getZ() + 1.35, 180.0F, 45.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        player.getFoodData().setFoodLevel(20);
    }

    private static void clearDroppedItems(ServerLevel level, BlockPos target) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(6)).forEach(ItemEntity::discard);
    }

    private static void validateLoadedMods() {
        assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("exdeorum:stone_pebble")),
                "Expected Ex Deorum to be loaded");
        assertTrue(BuiltInRegistries.BLOCK.containsKey(INFERIUM_CROP_ID),
                "Expected Mystical Agriculture to be loaded");
        try {
            Class.forName("dev.wuffs.squatgrow.SquatGrow");
            ultimineKey();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Expected Squat Grow and FTB Ultimine to be loaded", exception);
        }
    }

    private static void validateDefaultKeys(Minecraft minecraft) {
        assertKey(mapping(minecraft, PEBBLE_KEY), InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "G502 G5 pebble macro");
        assertKey(mapping(minecraft, CROP_KEY), InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, "G502 G4 crop macro");
        assertKey(mapping(minecraft, SELECT_KEY), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "F6 container selection");
    }

    private static void assertKey(KeyMapping mapping, InputConstants.Type type, int value, String description) {
        InputConstants.Key key = mapping.getDefaultKey();
        assertTrue(key.getType() == type && key.getValue() == value,
                "Expected default " + description + ", found " + key.getName());
        mapping.setKey(key);
        KeyMapping.resetMapping();
    }

    private static void click(Minecraft minecraft, String name) {
        KeyMapping.click(mapping(minecraft, name).getDefaultKey());
    }

    private static KeyMapping mapping(Minecraft minecraft, String name) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals(name)) {
                return mapping;
            }
        }
        throw new AssertionError("Missing key mapping " + name);
    }

    private static KeyMapping ultimineKey() throws ReflectiveOperationException {
        Class<?> client = Class.forName("dev.ftb.mods.ftbultimine.client.FTBUltimineClient");
        return (KeyMapping) client.getField("keyBindUltimine").get(null);
    }

    private static CropBlock inferiumCrop() {
        Block block = BuiltInRegistries.BLOCK.get(INFERIUM_CROP_ID);
        if (!(block instanceof CropBlock crop)) {
            throw new AssertionError("Expected mysticalagriculture:inferium_crop to extend CropBlock, found " + block.getClass().getName());
        }
        return crop;
    }

    private static Block inferiumFarmland() {
        Block block = BuiltInRegistries.BLOCK.get(INFERIUM_FARMLAND_ID);
        if (block == Blocks.AIR) {
            throw new AssertionError("Expected mysticalagriculture:inferium_farmland to be registered");
        }
        return block;
    }

    private static boolean inventoryContains(Minecraft minecraft, Set<String> ids) {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return true;
            }
        }
        return false;
    }

    private static void assertTargeted(Minecraft minecraft, BlockPos expected, String description) {
        String actual = minecraft.hitResult instanceof BlockHitResult hit
                ? hit.getBlockPos() + " (" + minecraft.level.getBlockState(hit.getBlockPos()) + ")"
                : String.valueOf(minecraft.hitResult);
        assertTrue(minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected),
                "Expected the crosshair to target the " + description + " at " + expected + ", found " + actual
                        + "; expected state is " + minecraft.level.getBlockState(expected)
                        + " with shape " + minecraft.level.getBlockState(expected).getShape(minecraft.level, expected)
                        + "; player is at " + minecraft.player.position());
    }

    private static void signalRecorderStart() {
        String startPath = System.getenv(RECORDING_START_ENV);
        if (startPath == null || startPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(startPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, Long.toString(System.currentTimeMillis()));
        } catch (IOException exception) {
            throw new AssertionError("Could not signal the client GameTest recorder", exception);
        }
    }

    private static void enter(Phase next, String message) {
        phase = next;
        phaseTicks = 0;
        ActionAssist.LOGGER.info("[Modpack Client GameTest] {}", message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fail(Throwable failure) {
        System.setProperty(ENABLED_PROPERTY, "false");
        KeyMapping.releaseAll();
        ActionAssist.LOGGER.error("ACTIONASSIST_MODPACK_GAMETEST_FAIL", failure);
        System.exit(1);
    }

    private enum Phase {
        BOOT,
        WAIT_FOR_RECORDER,
        SELECT_CONTAINER,
        START_PEBBLES,
        FARM_PEBBLES,
        START_CROPS,
        FARM_CROPS,
        FINISH
    }

    @FunctionalInterface
    private interface ServerSetup {
        void apply(ServerLevel level, ServerPlayer player);
    }
}
