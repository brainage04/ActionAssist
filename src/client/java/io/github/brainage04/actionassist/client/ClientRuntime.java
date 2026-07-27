package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.core.AutomationEngine;
import io.github.brainage04.actionassist.core.AutomationSettings;
import io.github.brainage04.actionassist.core.SettingsFile;
import java.lang.reflect.Method;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientRuntime {
    private static AutomationEngine engine;
    private static MinecraftAutomationOutput output;
    private static ActionAssistKeys keys;
    private static boolean wasConnected;
    private static Method statusMethod;
    private static boolean statusMethodHasOverlayFlag;

    private ClientRuntime() {
    }

    public static void initialize(Path configDirectory, ActionAssistKeys registeredKeys) {
        if (engine != null) {
            throw new IllegalStateException("Action Assist is already initialized");
        }
        AutomationSettings settings = SettingsFile.load(
                configDirectory,
                warning -> ActionAssist.LOGGER.warn("{}", warning)
        );
        engine = new AutomationEngine(settings);
        keys = registeredKeys;
        ActionAssist.LOGGER.info(
                "Loaded {}: action={}, actionsPerSecond={}, sneakTapsPerSecond={}",
                SettingsFile.FILE_NAME,
                settings.action(),
                settings.actionsPerSecond(),
                settings.sneakTapsPerSecond()
        );
    }

    public static void tick(Minecraft minecraft) {
        requireInitialized();
        if (output == null) {
            output = new MinecraftAutomationOutput(minecraft, keys.companionHold());
        }

        consumeControls(minecraft);

        boolean connected = minecraft.player != null && minecraft.level != null && minecraft.gameMode != null;
        if (!connected && wasConnected) {
            engine.stop();
            output.release();
        }
        wasConnected = connected;

        boolean gameplayAvailable = connected && !ScreenAccess.isScreenOpen(minecraft);
        engine.tick(gameplayAvailable, output);
    }

    public static void shutdown() {
        if (engine == null) {
            return;
        }
        engine.stop();
        if (output != null) {
            output.release();
        }
    }

    private static void consumeControls(Minecraft minecraft) {
        while (keys.toggle().consumeClick()) {
            boolean enabled = engine.toggle();
            status(
                    minecraft,
                    enabled ? "message.actionassist.enabled" : "message.actionassist.disabled"
            );
        }
        while (keys.cycleSneakMode().consumeClick()) {
            AutomationEngine.SneakMode mode = engine.cycleSneakMode();
            status(
                    minecraft,
                    "message.actionassist.sneak_mode",
                    Component.translatable("message.actionassist.sneak_mode." + mode.name().toLowerCase())
            );
        }
        while (keys.dumpHotbar().consumeClick()) {
            boolean queued = engine.requestHotbarDump();
            status(
                    minecraft,
                    queued ? "message.actionassist.dump_queued" : "message.actionassist.dump_ignored"
            );
        }
    }

    private static void status(Minecraft minecraft, String translationKey, Object... arguments) {
        if (!engine.settings().statusMessages() || minecraft.player == null) {
            return;
        }
        Component message = Component.translatable(translationKey, arguments);
        try {
            if (statusMethod == null) {
                resolveStatusMethod(minecraft);
            }
            if (statusMethodHasOverlayFlag) {
                statusMethod.invoke(minecraft.player, message, true);
            } else {
                statusMethod.invoke(minecraft.player, message);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not display an Action Assist status message", exception);
        }
    }

    private static void resolveStatusMethod(Minecraft minecraft) throws NoSuchMethodException {
        Method componentOnly = null;
        for (Method method : minecraft.player.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2
                    && parameters[0] == Component.class
                    && parameters[1] == boolean.class) {
                statusMethod = method;
                statusMethodHasOverlayFlag = true;
                return;
            }
            if (parameters.length == 1 && parameters[0] == Component.class) {
                componentOnly = method;
            }
        }
        if (componentOnly != null) {
            statusMethod = componentOnly;
            return;
        }
        throw new NoSuchMethodException("Unsupported Minecraft status message API");
    }

    private static void requireInitialized() {
        if (engine == null || keys == null) {
            throw new IllegalStateException("Action Assist has not been initialized");
        }
    }
}
