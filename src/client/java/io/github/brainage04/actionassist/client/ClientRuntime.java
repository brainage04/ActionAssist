package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.core.Macro;
import io.github.brainage04.actionassist.core.MacroEngine;
import io.github.brainage04.actionassist.core.MacroSettings;
import io.github.brainage04.actionassist.core.SettingsFile;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ClientRuntime {
    private static MacroEngine engine;
    private static ActionAssistKeys keys;
    private static MinecraftMacroOutput output;
    private static ClientInventoryView inventory;
    private static StatusMessages statusMessages;
    private static boolean wasConnected;

    private ClientRuntime() {
    }

    public static void initialize(Path configDirectory, ActionAssistKeys registeredKeys) {
        if (engine != null) {
            throw new IllegalStateException("Action Assist is already initialized");
        }
        MacroSettings settings = SettingsFile.load(
                configDirectory,
                warning -> ActionAssist.LOGGER.warn("{}", warning)
        );
        engine = new MacroEngine(settings);
        keys = registeredKeys;
        ActionAssist.LOGGER.info(
                "Loaded {}: veinMineKey={}, compactItems={}",
                SettingsFile.FILE_NAME,
                settings.veinMineKey(),
                settings.compactItems()
        );
    }

    public static void tick(Minecraft minecraft) {
        requireInitialized();
        if (output == null) {
            statusMessages = new StatusMessages(minecraft, engine.settings().statusMessages());
            output = new MinecraftMacroOutput(minecraft, engine.settings().veinMineKey(), statusMessages);
            inventory = new ClientInventoryView(minecraft);
        }

        boolean connected = minecraft.player != null && minecraft.level != null && minecraft.gameMode != null;
        consumeControls(minecraft, connected);
        if (!connected && wasConnected) {
            output.release();
            output.setDepositContainer(null);
        }
        wasConnected = connected;

        boolean screenOpen = connected && ScreenAccess.isScreenOpen(minecraft);
        boolean containerOpen = connected && minecraft.player.containerMenu != minecraft.player.inventoryMenu;
        engine.tick(connected, screenOpen, containerOpen, inventory, output);
    }

    public static void shutdown() {
        if (engine == null || output == null) {
            return;
        }
        engine.stop(output);
        output.release();
    }

    /** The engine driving the macros; exposed for the client GameTests. */
    public static MacroEngine engine() {
        requireInitialized();
        return engine;
    }

    /** The selected deposit container, or {@code null}; exposed for the client GameTests. */
    public static BlockPos depositContainer() {
        return output == null ? null : output.depositContainer();
    }

    private static void consumeControls(Minecraft minecraft, boolean connected) {
        while (keys.pebbleMacro().consumeClick()) {
            if (connected) {
                toggle(Macro.PEBBLE);
            }
        }
        while (keys.cropMacro().consumeClick()) {
            if (connected) {
                toggle(Macro.CROP);
            }
        }
        while (keys.selectContainer().consumeClick()) {
            if (connected) {
                selectContainer(minecraft);
            }
        }
    }

    private static void toggle(Macro macro) {
        engine.toggle(macro, inventory, output);
        if (engine.activeMacro() == Macro.CROP && output.veinMineKey() == null) {
            statusMessages.show("message.actionassist.vein_key_missing", output.veinMineKeyName());
        }
    }

    private static void selectContainer(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            statusMessages.show("message.actionassist.container_invalid");
            return;
        }
        BlockPos position = blockHit.getBlockPos();
        if (position.equals(output.depositContainer())) {
            output.setDepositContainer(null);
            statusMessages.show("message.actionassist.container_cleared");
            return;
        }
        if (minecraft.level.getBlockEntity(position) == null) {
            statusMessages.show("message.actionassist.container_invalid");
            return;
        }
        output.setDepositContainer(position.immutable());
        statusMessages.show(
                "message.actionassist.container_selected", position.getX(), position.getY(), position.getZ());
    }

    private static void requireInitialized() {
        if (engine == null || keys == null) {
            throw new IllegalStateException("Action Assist has not been initialized");
        }
    }
}
