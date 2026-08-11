package io.github.brainage04.actionassist.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brainage04.actionassist.client.ClientRuntime;
import io.github.brainage04.actionassist.core.AutomationEngine;
import io.github.brainage04.actionassist.core.AutomationSettings;
import io.github.brainage04.actionassist.core.SettingsFile;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class ActionAssistProductionClientGameTest implements FabricClientGameTest {
    private static final String TOGGLE_KEY = "key.actionassist.toggle";
    private static final String SNEAK_MODE_KEY = "key.actionassist.cycle_sneak";
    private static final String DUMP_KEY = "key.actionassist.dump_hotbar";
    private static final String COMPANION_KEY = "key.actionassist.companion_hold";
    private static final String RECORDING_START_ENV = "CLIENT_GAMETEST_RECORDING_START_SIGNAL";
    private static final String RECORDING_READY_ENV = "CLIENT_GAMETEST_RECORDING_READY_SIGNAL";
    private static final int ARENA_Y = 64;
    private static final BlockPos TARGET = new BlockPos(0, ARENA_Y, 0);
    private static final List<Item> HOTBAR_ITEMS =
            List.of(
                    Items.DIRT,
                    Items.GRAVEL,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.GLASS,
                    Items.BRICKS,
                    Items.OBSIDIAN,
                    Items.NETHERRACK,
                    Items.END_STONE);

    @Override
    public void runTest(ClientGameTestContext context) {
        assertFirstLaunchConfiguration();
        assertTranslationsAndDefaultKeys(context);

        Properties serverProperties = flatServerProperties();
        try (TestDedicatedServerContext server =
                context.worldBuilder().createServer(serverProperties)) {
            connectToDedicatedServer(context, server, "Action Assist production GameTest");
            server.runOnServer(ActionAssistProductionClientGameTest::prepareArena);
            context.waitTicks(30);

            startRecording(context);
            showStep(
                    context,
                    "ready",
                    "Action Assist complete production test",
                    "The packaged Fabric jar exercises every action, mode, safety path, and inventory transfer");
            context.waitTicks(40);

            demonstrateReboundControl(context);
            demonstrateStatusSuppression(context);
            demonstrateRepeatedUse(context, server);
            demonstrateRepeatedAttack(context, server);
            demonstrateSneakModesAndScreenSafety(context);
            demonstrateHotbarTransfers(context, server);
            demonstrateDisconnectAndShutdownSafety(context, server);
        } finally {
            restoreDefaultKeys(context);
            if (context.computeOnClient(client -> client.level != null)) {
                disconnectFromDedicatedServer(context);
            }
        }
    }

    private static void demonstrateReboundControl(ClientGameTestContext context) {
        showStep(
                context,
                "rebound-control",
                "Rebindable controls",
                "The toggle is temporarily rebound to H and consumed through its new key");
        InputConstants.Key rebound = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H);
        context.runOnClient(
                client -> {
                    KeyMapping toggle = mapping(client.options.keyMappings, TOGGLE_KEY);
                    toggle.setKey(rebound);
                    KeyMapping.resetMapping();
                });
        replaceEngine(context, AutomationSettings.defaults());
        click(context, rebound);
        context.waitTicks(3);
        assertTrue(engine(context).isEnabled(), "Expected the rebound toggle to enable automation");
        click(context, rebound);
        context.waitTicks(3);
        assertFalse(
                engine(context).isEnabled(), "Expected the rebound toggle to disable automation");
        restoreDefaultKeys(context);
        context.waitTicks(25);
    }

    private static void demonstrateStatusSuppression(ClientGameTestContext context) {
        replaceEngine(context, new AutomationSettings(AutomationSettings.Action.USE, 20, 8, false));
        showStep(
                context,
                "quiet-status",
                "Optional status messages",
                "With statusMessages=false, controls still work without Action Assist action-bar messages");
        click(context, TOGGLE_KEY);
        context.waitTicks(5);
        assertTrue(engine(context).isEnabled(), "Expected quiet-status automation to enable");
        click(context, SNEAK_MODE_KEY);
        context.waitTicks(5);
        assertEquals(
                AutomationEngine.SneakMode.SPAM,
                engine(context).sneakMode(),
                "Expected quiet-status sneak cycling to remain functional");
        click(context, TOGGLE_KEY);
        context.waitTicks(25);
        assertFalse(engine(context).isEnabled(), "Expected quiet-status automation to disable");
    }

    private static void demonstrateRepeatedUse(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        server.runOnServer(ActionAssistProductionClientGameTest::prepareUseTarget);
        context.waitTicks(25);
        aimClientAt(context, Vec3.atCenterOf(TARGET));
        context.waitTicks(3);
        assertClientTargets(context, TARGET, "note block");
        replaceEngine(context, new AutomationSettings(AutomationSettings.Action.USE, 5, 8, true));
        int initialNote = noteValue(server);

        showStep(
                context,
                "repeated-use",
                "Repeated use action",
                "Five real right-clicks per second change the note block through the packaged mod");
        click(context, TOGGLE_KEY);
        context.waitTicks(20);
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        int changedNote = noteValue(server);
        assertTrue(
                changedNote != initialNote,
                "Expected repeated use actions to change the note block");
        context.waitTicks(25);
    }

    private static void demonstrateRepeatedAttack(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        server.runOnServer(ActionAssistProductionClientGameTest::prepareAttackTarget);
        context.waitTicks(25);
        aimClientAt(context, Vec3.atCenterOf(TARGET).add(0.0, 0.8, 0.0));
        context.waitTicks(3);
        replaceEngine(
                context, new AutomationSettings(AutomationSettings.Action.ATTACK, 20, 8, true));

        showStep(
                context,
                "repeated-attack",
                "Repeated attack action",
                "The attack configuration drives real left-click damage against the marked target");
        click(context, TOGGLE_KEY);
        context.waitTicks(30);
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        float health =
                server.computeOnServer(
                        minecraftServer -> attackTarget(minecraftServer).getHealth());
        assertTrue(health < 100.0F, "Expected repeated attack actions to damage the target");
        context.waitTicks(25);
    }

    private static void demonstrateSneakModesAndScreenSafety(ClientGameTestContext context) {
        replaceEngine(context, new AutomationSettings(AutomationSettings.Action.USE, 1, 8, true));
        showStep(
                context,
                "sneak-hold",
                "Sneak mode: hold",
                "Automation holds the real Minecraft sneak mapping until paused or disabled");
        click(context, TOGGLE_KEY);
        context.waitTicks(5);
        assertClientKeyDown(context, client -> client.options.keyShift, true, "sneak hold");
        context.waitTicks(25);

        showStep(
                context,
                "screen-safety",
                "Screen-open safety release",
                "Opening inventory immediately releases synthetic sneak; closing it resumes hold mode");
        context.runOnClient(client -> client.setScreenAndShow(new InventoryScreen(client.player)));
        context.waitTicks(5);
        assertClientKeyDown(
                context, client -> client.options.keyShift, false, "screen-open sneak release");
        assertMappingDown(context, COMPANION_KEY, false, "screen-open companion release");
        context.waitTicks(35);
        context.runOnClient(client -> client.setScreenAndShow(null));
        context.waitTicks(5);
        assertClientKeyDown(context, client -> client.options.keyShift, true, "sneak resume");
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        replaceEngine(context, new AutomationSettings(AutomationSettings.Action.USE, 1, 8, true));
        click(context, SNEAK_MODE_KEY);
        click(context, TOGGLE_KEY);
        showStep(
                context,
                "sneak-spam",
                "Sneak mode: spam + companion",
                "Eight sneak taps per second run while the companion binding remains held");
        int sneakPresses = countSneakPresses(context, 20);
        assertEquals(8, sneakPresses, "Expected exactly eight sneak presses in one second");
        assertMappingDown(context, COMPANION_KEY, true, "companion hold");
        context.waitTicks(20);

        click(context, SNEAK_MODE_KEY);
        context.waitTicks(3);
        showStep(
                context,
                "sneak-none",
                "Sneak mode: none",
                "Use or attack automation continues without synthetic sneak or companion input");
        assertClientKeyDown(context, client -> client.options.keyShift, false, "none-mode sneak");
        assertMappingDown(context, COMPANION_KEY, false, "none-mode companion");
        context.waitTicks(35);
        click(context, TOGGLE_KEY);
        context.waitTicks(3);
    }

    private static void demonstrateHotbarTransfers(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        server.runOnServer(minecraftServer -> prepareHotbar(minecraftServer, false));
        context.waitTicks(20);
        replaceEngine(context, AutomationSettings.defaults());
        click(context, SNEAK_MODE_KEY);
        click(context, SNEAK_MODE_KEY);
        showStep(
                context,
                "disabled-transfer",
                "Disabled hotbar transfer is ignored",
                "F6 leaves all nine slots untouched until automation is enabled");
        click(context, DUMP_KEY);
        context.waitTicks(8);
        assertHotbarOccupied(server, 9);
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        showStep(
                context,
                "hotbar-transfer",
                "Complete hotbar transfer",
                "F6 quick-moves every occupied hotbar slot into the available main inventory");
        click(context, DUMP_KEY);
        context.waitTicks(12);
        assertHotbarOccupied(server, 0);
        context.runOnClient(client -> client.setScreenAndShow(new InventoryScreen(client.player)));
        context.waitTicks(45);
        context.runOnClient(client -> client.setScreenAndShow(null));
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        server.runOnServer(minecraftServer -> prepareHotbar(minecraftServer, true));
        context.waitTicks(20);
        replaceEngine(context, AutomationSettings.defaults());
        click(context, SNEAK_MODE_KEY);
        click(context, SNEAK_MODE_KEY);
        click(context, TOGGLE_KEY);
        context.waitTicks(3);

        showStep(
                context,
                "limited-capacity",
                "Inventory-capacity boundary",
                "Only two stacks move when the main inventory has two free slots; seven remain safely in the hotbar");
        click(context, DUMP_KEY);
        context.waitTicks(12);
        assertHotbarOccupied(server, 7);
        context.runOnClient(client -> client.setScreenAndShow(new InventoryScreen(client.player)));
        context.waitTicks(45);
        context.runOnClient(client -> client.setScreenAndShow(null));
        click(context, TOGGLE_KEY);
        context.waitTicks(3);
    }

    private static void demonstrateDisconnectAndShutdownSafety(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        replaceEngine(context, AutomationSettings.defaults());
        click(context, TOGGLE_KEY);
        context.waitTicks(5);
        assertClientKeyDown(
                context, client -> client.options.keyShift, true, "pre-disconnect sneak");
        showStep(
                context,
                "disconnect-safety",
                "Disconnect safety",
                "Leaving the server stops automation and releases every synthetic key");
        context.waitTicks(25);
        disconnectFromDedicatedServer(context);
        assertClientKeyDown(
                context, client -> client.options.keyShift, false, "disconnect sneak release");
        assertMappingDown(context, COMPANION_KEY, false, "disconnect companion release");
        assertFalse(engine(context).isEnabled(), "Expected disconnect to stop automation");
        context.waitTicks(35);

        connectToDedicatedServer(context, server, "Action Assist shutdown safety");
        context.waitTicks(20);
        replaceEngine(context, AutomationSettings.defaults());
        click(context, SNEAK_MODE_KEY);
        click(context, TOGGLE_KEY);
        context.waitTicks(5);
        assertMappingDown(context, COMPANION_KEY, true, "pre-shutdown companion hold");

        showStep(
                context,
                "shutdown-safety",
                "Client shutdown safety",
                "Shutdown stops automation and releases sneak plus the companion binding");
        context.waitTicks(25);
        context.runOnClient(client -> ClientRuntime.shutdown());
        context.waitTicks(3);
        assertClientKeyDown(
                context, client -> client.options.keyShift, false, "shutdown sneak release");
        assertMappingDown(context, COMPANION_KEY, false, "shutdown companion release");
        assertFalse(engine(context).isEnabled(), "Expected shutdown to stop automation");

        showStep(
                context,
                "complete",
                "All Action Assist functionality passed",
                "Use, attack, three sneak modes, rebinds, transfers, screen pause, disconnect, and shutdown are verified");
        context.waitTicks(60);
    }

    private static void prepareArena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, ARENA_Y - 1, z),
                        (x + z & 1) == 0
                                ? Blocks.SMOOTH_STONE.defaultBlockState()
                                : Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        positionPlayer(player);
    }

    private static void prepareUseTarget(MinecraftServer server) {
        clearTargets(server);
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        level.setBlockAndUpdate(TARGET, Blocks.NOTE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(TARGET.north(), Blocks.GOLD_BLOCK.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        positionPlayer(player);
    }

    private static void prepareAttackTarget(MinecraftServer server) {
        clearTargets(server);
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Cow target = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        if (target == null) {
            throw new AssertionError("Expected to create the repeated-attack target");
        }
        target.setPos(Vec3.atBottomCenterOf(TARGET));
        target.setNoAi(true);
        target.setCustomName(Component.literal("Repeated attack target"));
        target.setCustomNameVisible(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        target.setHealth(100.0F);
        level.addFreshEntity(target);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        positionPlayer(player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target, EntityAnchorArgument.Anchor.EYES);
    }

    private static void prepareHotbar(MinecraftServer server, boolean limitedCapacity) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.getInventory().clearContent();
        for (int slot = 0; slot < HOTBAR_ITEMS.size(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(HOTBAR_ITEMS.get(slot)));
        }
        if (limitedCapacity) {
            for (int slot = 9; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot != 9 && slot != 10) {
                    player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
        }
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void clearTargets(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.setBlockAndUpdate(TARGET, Blocks.AIR.defaultBlockState());
        level.getEntitiesOfClass(Cow.class, new AABB(-5, ARENA_Y - 2, -5, 6, ARENA_Y + 4, 6))
                .forEach(Cow::discard);
    }

    private static void positionPlayer(ServerPlayer player) {
        player.teleportTo(
                player.level(),
                TARGET.getX() + 0.5,
                TARGET.getY() + 1.0,
                TARGET.getZ() + 3.5,
                Set.of(),
                180.0F,
                20.0F,
                false);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(TARGET));
    }

    private static int noteValue(TestDedicatedServerContext server) {
        return server.computeOnServer(
                minecraftServer ->
                        minecraftServer.overworld().getBlockState(TARGET).getValue(NoteBlock.NOTE));
    }

    private static Cow attackTarget(MinecraftServer server) {
        return server
                .overworld()
                .getEntitiesOfClass(Cow.class, new AABB(-5, ARENA_Y - 2, -5, 6, ARENA_Y + 4, 6))
                .stream()
                .filter(
                        cow ->
                                cow.getCustomName() != null
                                        && cow.getCustomName()
                                                .getString()
                                                .equals("Repeated attack target"))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Expected the repeated-attack target to remain present"));
    }

    private static void assertHotbarOccupied(TestDedicatedServerContext server, int expected) {
        int occupied =
                server.computeOnServer(
                        minecraftServer -> {
                            ServerPlayer player =
                                    minecraftServer.getPlayerList().getPlayers().getFirst();
                            int count = 0;
                            for (int slot = 0; slot < 9; slot++) {
                                if (!player.getInventory().getItem(slot).isEmpty()) {
                                    count++;
                                }
                            }
                            return count;
                        });
        assertEquals(expected, occupied, "Unexpected occupied hotbar slot count");
    }

    private static int countSneakPresses(ClientGameTestContext context, int ticks) {
        boolean previous = context.computeOnClient(client -> client.options.keyShift.isDown());
        int presses = 0;
        for (int tick = 0; tick < ticks; tick++) {
            context.waitTick();
            boolean current = context.computeOnClient(client -> client.options.keyShift.isDown());
            if (current && !previous) {
                presses++;
            }
            previous = current;
        }
        return presses;
    }

    private static void assertFirstLaunchConfiguration() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(SettingsFile.FILE_NAME);
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Expected first launch to create " + path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new AssertionError(
                    "Could not inspect the generated Action Assist configuration", exception);
        }
        assertEquals("use", properties.getProperty("action"), "Unexpected generated action");
        assertEquals(
                "20",
                properties.getProperty("actionsPerSecond"),
                "Unexpected generated action rate");
        assertEquals(
                "8",
                properties.getProperty("sneakTapsPerSecond"),
                "Unexpected generated sneak rate");
        assertEquals(
                "true",
                properties.getProperty("statusMessages"),
                "Unexpected generated status setting");
    }

    private static void assertTranslationsAndDefaultKeys(ClientGameTestContext context) {
        context.runOnClient(
                client -> {
                    for (String translation :
                            List.of(
                                    "key.categories.actionassist",
                                    "key.category.actionassist.keys",
                                    TOGGLE_KEY,
                                    SNEAK_MODE_KEY,
                                    DUMP_KEY,
                                    COMPANION_KEY,
                                    "message.actionassist.enabled",
                                    "message.actionassist.disabled",
                                    "message.actionassist.sneak_mode",
                                    "message.actionassist.sneak_mode.hold",
                                    "message.actionassist.sneak_mode.spam",
                                    "message.actionassist.sneak_mode.none",
                                    "message.actionassist.dump_queued",
                                    "message.actionassist.dump_ignored")) {
                        assertFalse(
                                I18n.get(translation).equals(translation),
                                "Missing translation " + translation);
                    }
                    assertKey(
                            mapping(client.options.keyMappings, TOGGLE_KEY),
                            InputConstants.Type.MOUSE,
                            GLFW.GLFW_MOUSE_BUTTON_5,
                            "toggle");
                    assertKey(
                            mapping(client.options.keyMappings, SNEAK_MODE_KEY),
                            InputConstants.Type.MOUSE,
                            GLFW.GLFW_MOUSE_BUTTON_4,
                            "sneak-mode cycle");
                    assertKey(
                            mapping(client.options.keyMappings, DUMP_KEY),
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_F6,
                            "hotbar transfer");
                    assertKey(
                            mapping(client.options.keyMappings, COMPANION_KEY),
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_GRAVE_ACCENT,
                            "companion hold");
                });
    }

    private static void assertKey(
            KeyMapping mapping,
            InputConstants.Type expectedType,
            int expectedValue,
            String description) {
        InputConstants.Key key = mapping.getDefaultKey();
        assertTrue(
                key.getType() == expectedType && key.getValue() == expectedValue,
                "Unexpected default " + description + " key: " + key.getName());
    }

    private static void restoreDefaultKeys(ClientGameTestContext context) {
        context.runOnClient(
                client -> {
                    for (String name :
                            List.of(TOGGLE_KEY, SNEAK_MODE_KEY, DUMP_KEY, COMPANION_KEY)) {
                        KeyMapping mapping = mapping(client.options.keyMappings, name);
                        mapping.setKey(mapping.getDefaultKey());
                    }
                    KeyMapping.resetMapping();
                });
    }

    private static void replaceEngine(ClientGameTestContext context, AutomationSettings settings) {
        context.runOnClient(
                client -> {
                    ClientRuntime.shutdown();
                    try {
                        Field field = ClientRuntime.class.getDeclaredField("engine");
                        field.setAccessible(true);
                        field.set(null, new AutomationEngine(settings));
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError(
                                "Could not install deterministic GameTest settings", exception);
                    }
                });
    }

    private static AutomationEngine engine(ClientGameTestContext context) {
        return context.computeOnClient(
                client -> {
                    try {
                        Field field = ClientRuntime.class.getDeclaredField("engine");
                        field.setAccessible(true);
                        return (AutomationEngine) field.get(null);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError(
                                "Could not inspect the Action Assist engine", exception);
                    }
                });
    }

    private static void click(ClientGameTestContext context, String mappingName) {
        context.runOnClient(
                client ->
                        KeyMapping.click(
                                mapping(client.options.keyMappings, mappingName).getDefaultKey()));
    }

    private static void click(ClientGameTestContext context, InputConstants.Key key) {
        context.runOnClient(client -> KeyMapping.click(key));
    }

    private static KeyMapping mapping(KeyMapping[] mappings, String name) {
        for (KeyMapping mapping : mappings) {
            if (mapping.getName().equals(name)) {
                return mapping;
            }
        }
        throw new AssertionError("Missing key mapping " + name);
    }

    private static void aimClientAt(ClientGameTestContext context, Vec3 target) {
        context.runOnClient(
                client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, target));
    }

    private static void assertClientTargets(
            ClientGameTestContext context, BlockPos expected, String description) {
        context.runOnClient(
                client -> {
                    if (!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                            || !hit.getBlockPos().equals(expected)) {
                        String actual =
                                client.hitResult
                                                instanceof
                                                net.minecraft.world.phys.BlockHitResult blockHit
                                        ? blockHit.getBlockPos().toString()
                                        : String.valueOf(client.hitResult);
                        throw new AssertionError(
                                "Expected the client to target the "
                                        + description
                                        + " at "
                                        + expected
                                        + ", found "
                                        + actual
                                        + " from "
                                        + client.player.position());
                    }
                });
    }

    private static void assertClientKeyDown(
            ClientGameTestContext context,
            java.util.function.Function<net.minecraft.client.Minecraft, KeyMapping> mapping,
            boolean expected,
            String description) {
        boolean actual = context.computeOnClient(client -> mapping.apply(client).isDown());
        assertEquals(expected, actual, "Unexpected state for " + description);
    }

    private static void assertMappingDown(
            ClientGameTestContext context,
            String mappingName,
            boolean expected,
            String description) {
        boolean actual =
                context.computeOnClient(
                        client -> mapping(client.options.keyMappings, mappingName).isDown());
        assertEquals(expected, actual, "Unexpected state for " + description);
    }

    private static void showStep(
            ClientGameTestContext context, String id, String title, String subtitle) {
        String message = "[ACTIONASSIST_GAMETEST] " + id + " | " + title + " | " + subtitle;
        System.out.println(message);
        context.runOnClient(
                client -> {
                    displayMessage(client, Component.literal(title));
                    displayMessage(client, Component.literal(subtitle));
                });
    }

    private static void displayMessage(Minecraft minecraft, Component message) {
        try {
            for (var method : minecraft.player.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 2
                        && parameters[0] == Component.class
                        && parameters[1] == boolean.class) {
                    method.invoke(minecraft.player, message, false);
                    return;
                }
                if (parameters.length == 1 && parameters[0] == Component.class) {
                    method.invoke(minecraft.player, message);
                    return;
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not display the GameTest showcase step", exception);
        }
        throw new AssertionError("Unsupported Minecraft client message API");
    }

    private static Properties flatServerProperties() {
        Properties properties = new Properties();
        properties.setProperty("server-port", Integer.toString(findAvailablePort()));
        properties.setProperty("simulation-distance", "5");
        properties.setProperty("view-distance", "5");
        properties.setProperty("level-type", "minecraft:flat");
        properties.setProperty("generate-structures", "false");
        properties.setProperty("generator-settings", "{}");
        properties.setProperty("spawn-protection", "0");
        return properties;
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new AssertionError(
                    "Expected to find an available client GameTest port", exception);
        }
    }

    private static void connectToDedicatedServer(
            ClientGameTestContext context, TestDedicatedServerContext server, String serverName) {
        String address = "localhost:" + server.computeOnServer(MinecraftServer::getPort);
        context.runOnClient(
                client -> {
                    ServerData serverData =
                            new ServerData(serverName, address, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            client.gui.screen(),
                            client,
                            ServerAddress.parseString(address),
                            serverData,
                            false,
                            null);
                });
        for (int tick = 0; tick < 1_200; tick++) {
            if (context.computeOnClient(
                    client ->
                            client.level != null
                                    && client.player != null
                                    && !(client.gui.screen() instanceof LevelLoadingScreen))) {
                return;
            }
            context.waitTick();
        }
        throw new AssertionError("Timed out joining the dedicated client GameTest server");
    }

    private static void disconnectFromDedicatedServer(ClientGameTestContext context) {
        context.runOnClient(
                client -> {
                    if (client.level == null) {
                        return;
                    }
                    client.level.disconnect(Component.literal("Disconnecting"));
                    client.disconnectWithSavingScreen();
                });
        context.waitFor(client -> client.level == null);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }

    private static void startRecording(ClientGameTestContext context) {
        Path startSignal = environmentPath(RECORDING_START_ENV);
        if (startSignal == null) {
            return;
        }
        try {
            if (startSignal.getParent() != null) {
                Files.createDirectories(startSignal.getParent());
            }
            Files.writeString(startSignal, Long.toString(System.currentTimeMillis()));
        } catch (IOException exception) {
            throw new AssertionError("Could not signal the client GameTest recorder", exception);
        }

        Path readySignal = environmentPath(RECORDING_READY_ENV);
        if (readySignal == null) {
            return;
        }
        for (int tick = 0; tick < 400; tick++) {
            if (Files.exists(readySignal)) {
                return;
            }
            context.waitTick();
        }
        throw new AssertionError("Recorder did not acknowledge the client within 400 ticks");
    }

    private static Path environmentPath(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", found " + actual);
        }
    }
}
