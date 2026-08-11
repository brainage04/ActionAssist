package io.github.brainage04.actionassist.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brainage04.actionassist.ActionAssist;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ActionAssist.MOD_ID, value = Dist.CLIENT)
public final class ModpackCompatibilityClientGameTest {
    private static final String ENABLED_PROPERTY = "actionassist.modpackGameTest";
    private static final String WORLD_ID = "actionassist-modpack-gametest";
    private static final String RECORDING_START_ENV = "CLIENT_GAMETEST_RECORDING_START_SIGNAL";
    private static final String RECORDING_READY_ENV = "CLIENT_GAMETEST_RECORDING_READY_SIGNAL";
    private static final ResourceLocation INFERIUM_CROP_ID = ResourceLocation.parse("mysticalagriculture:inferium_crop");
    private static final ResourceLocation INFERIUM_FARMLAND_ID = ResourceLocation.parse("mysticalagriculture:inferium_farmland");
    private static final ResourceLocation INFERIUM_SEEDS_ID = ResourceLocation.parse("mysticalagriculture:inferium_seeds");

    private static Phase phase = Phase.BOOT;
    private static boolean worldRequested;
    private static volatile boolean setupComplete;
    private static BlockPos targetPos;
    private static int phaseTicks;
    private static boolean companionObserved;

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
            case PREPARE_PEBBLES -> preparePebbles(minecraft);
            case FARM_PEBBLES -> farmPebbles(minecraft);
            case PREPARE_GROWTH -> prepareGrowth(minecraft);
            case GROW_CROP -> growCrop(minecraft);
            case PREPARE_HARVEST -> prepareHarvest(minecraft);
            case HARVEST_CROP -> harvestCrop(minecraft);
            case PREPARE_HOTBAR -> prepareHotbar(minecraft);
            case DUMP_HOTBAR -> dumpHotbar(minecraft);
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
        LevelSettings settings = new LevelSettings(
                "Action Assist Modpack GameTest",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
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

    private static void waitForRecorder(Minecraft minecraft) throws ReflectiveOperationException {
        String readyPath = System.getenv(RECORDING_READY_ENV);
        if (readyPath != null && !readyPath.isBlank() && !Files.exists(Path.of(readyPath))) {
            if (phaseTicks > 400) {
                throw new AssertionError("Recorder did not acknowledge the client within 400 ticks");
            }
            return;
        }
        validateLoadedMods();
        configureSquatGrow();
        validateDefaultKeys(minecraft);
        submitPebbleSetup(minecraft);
        enter(Phase.PREPARE_PEBBLES, "Preparing Ex Deorum pebble farming");
    }

    private static void preparePebbles(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        assertTargeted(minecraft, targetPos, "dirt pebble target");
        click(minecraft, "key.actionassist.toggle");
        click(minecraft, "key.actionassist.cycle_sneak");
        click(minecraft, "key.actionassist.cycle_sneak");
        enter(Phase.FARM_PEBBLES, "Farming Ex Deorum pebbles with repeated right-clicks");
    }

    private static void farmPebbles(Minecraft minecraft) {
        boolean pebbleCreated = hasNearbyItem(minecraft, item -> id(item).getPath().endsWith("_pebble"))
                || inventoryContains(minecraft, item -> id(item).getPath().endsWith("_pebble"));
        if (!pebbleCreated) {
            assertTrue(phaseTicks < 1_200,
                    "Expected repeated empty-hand right-clicks to create an Ex Deorum pebble");
            return;
        }
        click(minecraft, "key.actionassist.toggle");
        submitGrowthSetup(minecraft);
        enter(Phase.PREPARE_GROWTH, "Pebble farming passed; preparing Mystical Agriculture crop growth");
    }

    private static void prepareGrowth(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        if (phaseTicks == 20) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(targetPos).add(0.0, 0.1, 0.0));
            return;
        }
        if (phaseTicks == 21) {
            assertTargeted(minecraft, targetPos, "young inferium crop");
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(targetPos.east()));
            return;
        }
        assertTargeted(minecraft, targetPos.east(), "growth action target");
        click(minecraft, "key.actionassist.cycle_sneak");
        click(minecraft, "key.actionassist.cycle_sneak");
        click(minecraft, "key.actionassist.toggle");
        enter(Phase.GROW_CROP, "Growing a Mystical Agriculture crop with sneak spam and right-clicks");
    }

    private static void growCrop(Minecraft minecraft) throws ReflectiveOperationException {
        if (phaseTicks >= 20) {
            companionObserved |= ultimineKey().isDown();
        }
        if (phaseTicks < 180) {
            return;
        }
        CropBlock crop = inferiumCrop();
        int age = crop.getAge(minecraft.level.getBlockState(targetPos));
        assertTrue(age > 0, "Expected Squat Grow to advance the inferium crop beyond age zero");
        assertTrue(companionObserved, "Expected Action Assist's companion hold to press FTB Ultimine's grave key");
        click(minecraft, "key.actionassist.toggle");
        click(minecraft, "key.actionassist.cycle_sneak");
        submitHarvestSetup(minecraft);
        enter(Phase.PREPARE_HARVEST, "Squat Grow and FTB Ultimine hold passed; preparing crop harvest");
    }

    private static void prepareHarvest(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(targetPos));
        if (phaseTicks == 20) {
            return;
        }
        assertTargeted(minecraft, targetPos, "mature inferium crop");
        click(minecraft, "key.actionassist.toggle");
        enter(Phase.HARVEST_CROP, "Right-click harvesting and replanting a mature inferium crop");
    }

    private static void harvestCrop(Minecraft minecraft) {
        if (phaseTicks < 120) {
            return;
        }
        CropBlock crop = inferiumCrop();
        Block block = minecraft.level.getBlockState(targetPos).getBlock();
        assertTrue(block == crop, "Expected FTB Ultimine crop harvesting to leave the inferium crop planted");
        assertTrue(crop.getAge(minecraft.level.getBlockState(targetPos)) < crop.getMaxAge(),
                "Expected right-click harvesting to reset the replanted crop's age");
        assertTrue(hasNearbyItem(minecraft, item -> id(item).getNamespace().equals("mysticalagriculture"))
                        || inventoryContains(minecraft, item -> id(item).getNamespace().equals("mysticalagriculture")),
                "Expected right-click harvesting to produce Mystical Agriculture drops");
        click(minecraft, "key.actionassist.toggle");
        submitHotbarSetup(minecraft);
        enter(Phase.PREPARE_HOTBAR, "Crop harvest passed; preparing hotbar quick-move");
    }

    private static void prepareHotbar(Minecraft minecraft) {
        if (!setupComplete || phaseTicks < 20) {
            return;
        }
        click(minecraft, "key.actionassist.toggle");
        click(minecraft, "key.actionassist.dump_hotbar");
        enter(Phase.DUMP_HOTBAR, "Dumping the occupied hotbar with F6");
    }

    private static void dumpHotbar(Minecraft minecraft) {
        if (phaseTicks < 40) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(minecraft.player.getInventory().getItem(slot).isEmpty(),
                    "Expected hotbar slot " + slot + " to be quick-moved");
        }
        int dirt = 0;
        for (int slot = 9; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(Blocks.DIRT.asItem())) {
                dirt += minecraft.player.getInventory().getItem(slot).getCount();
            }
        }
        assertTrue(dirt == 9, "Expected all nine dirt items in the main inventory after hotbar dumping, found " + dirt);
        click(minecraft, "key.actionassist.toggle");
        enter(Phase.FINISH, "Hotbar dump passed");
    }

    private static void finish(Minecraft minecraft) {
        if (phaseTicks < 60) {
            return;
        }
        KeyMapping.releaseAll();
        ActionAssist.LOGGER.info("ACTIONASSIST_MODPACK_GAMETEST_PASS: pebbles, crop growth, Ultimine hold, crop harvest/replant, and hotbar dump passed");
        minecraft.stop();
    }

    private static void submitPebbleSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            targetPos = testOrigin(player);
            clearTestArea(level, targetPos);
            level.setBlockAndUpdate(targetPos, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(targetPos.offset(0, 0, 2), Blocks.STONE.defaultBlockState());
            player.getInventory().clearContent();
            positionPlayer(player, targetPos);
        });
    }

    private static void submitGrowthSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            clearDroppedItems(level, targetPos);
            player.getInventory().clearContent();
            level.setBlockAndUpdate(targetPos.below(), inferiumFarmland().defaultBlockState());
            level.setBlockAndUpdate(targetPos.east(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(targetPos, inferiumCrop().getStateForAge(0));
            positionPlayer(player, targetPos);
        });
    }

    private static void submitHarvestSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            clearDroppedItems(level, targetPos);
            player.getInventory().clearContent();
            level.setBlockAndUpdate(targetPos.below(), inferiumFarmland().defaultBlockState());
            level.setBlockAndUpdate(targetPos, inferiumCrop().getStateForAge(inferiumCrop().getMaxAge()));
            positionPlayer(player, targetPos);
            player.setGameMode(GameType.SURVIVAL);
        });
    }

    private static void submitHotbarSetup(Minecraft minecraft) {
        submitSetup(minecraft, (level, player) -> {
            player.getInventory().clearContent();
            for (int slot = 0; slot < 9; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Blocks.DIRT));
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
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
            player.setGameMode(GameType.CREATIVE);
            setup.apply(player.serverLevel(), player);
            setupComplete = true;
        });
    }

    private static BlockPos testOrigin(ServerPlayer player) {
        BlockPos current = player.blockPosition();
        return new BlockPos(current.getX(), 200, current.getZ());
    }

    private static void clearTestArea(ServerLevel level, BlockPos target) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 4; z++) {
                for (int y = -1; y <= 4; y++) {
                    level.setBlockAndUpdate(target.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void positionPlayer(ServerPlayer player, BlockPos target) {
        player.teleportTo(player.serverLevel(), target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 2.5, 180.0F, 35.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void clearDroppedItems(ServerLevel level, BlockPos target) {
        level.getEntitiesOfClass(ItemEntity.class, areaAround(target)).forEach(ItemEntity::discard);
    }

    private static void validateLoadedMods() {
        assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("exdeorum:oak_crucible")),
                "Expected Ex Deorum to be loaded");
        assertTrue(BuiltInRegistries.BLOCK.containsKey(INFERIUM_CROP_ID),
                "Expected Mystical Agriculture to be loaded");
        assertTrue(BuiltInRegistries.ITEM.containsKey(INFERIUM_SEEDS_ID),
                "Expected Cucumber/Mystical Agriculture item registration");
        try {
            ultimineKey();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Expected FTB Ultimine to be loaded", exception);
        }
    }

    private static void configureSquatGrow() throws ReflectiveOperationException {
        Class<?> squatGrow = Class.forName("dev.wuffs.squatgrow.SquatGrow");
        Object config = squatGrow.getField("config").get(null);
        Class<?> configType = config.getClass();
        configType.getField("chance").setFloat(config, 1.0F);
        configType.getField("randomTickMultiplier").setInt(config, 16);
        configType.getField("enableMysticalCrops").setBoolean(config, true);
    }

    private static void validateDefaultKeys(Minecraft minecraft) {
        KeyMapping toggle = mapping(minecraft, "key.actionassist.toggle");
        KeyMapping sneak = mapping(minecraft, "key.actionassist.cycle_sneak");
        KeyMapping dump = mapping(minecraft, "key.actionassist.dump_hotbar");
        KeyMapping companion = mapping(minecraft, "key.actionassist.companion_hold");
        assertKey(toggle, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "G502 G5 toggle");
        assertKey(sneak, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, "G502 G4 sneak-mode cycle");
        assertKey(dump, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "Wooting F6 hotbar dump");
        assertKey(companion, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "grave companion hold");
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

    private static boolean hasNearbyItem(Minecraft minecraft, Predicate<Item> predicate) {
        return minecraft.level.getEntitiesOfClass(ItemEntity.class, areaAround(targetPos)).stream()
                .map(entity -> entity.getItem().getItem())
                .anyMatch(predicate);
    }

    private static boolean inventoryContains(Minecraft minecraft, Predicate<Item> predicate) {
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static AABB areaAround(BlockPos target) {
        return new AABB(
                target.getX() - 4,
                target.getY() - 2,
                target.getZ() - 4,
                target.getX() + 5,
                target.getY() + 5,
                target.getZ() + 5
        );
    }

    private static void assertTargeted(Minecraft minecraft, BlockPos expected, String description) {
        String actual = minecraft.hitResult instanceof BlockHitResult hit
                ? hit.getBlockPos() + " (" + minecraft.level.getBlockState(hit.getBlockPos()) + ")"
                : String.valueOf(minecraft.hitResult);
        assertTrue(minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected),
                "Expected the crosshair to target the " + description + " at " + expected + ", found " + actual
                        + "; expected state is " + minecraft.level.getBlockState(expected)
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
        PREPARE_PEBBLES,
        FARM_PEBBLES,
        PREPARE_GROWTH,
        GROW_CROP,
        PREPARE_HARVEST,
        HARVEST_CROP,
        PREPARE_HOTBAR,
        DUMP_HOTBAR,
        FINISH
    }

    @FunctionalInterface
    private interface ServerSetup {
        void apply(ServerLevel level, ServerPlayer player);
    }
}
