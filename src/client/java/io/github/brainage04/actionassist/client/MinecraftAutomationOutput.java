package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.core.AutomationEngine;
import io.github.brainage04.actionassist.core.AutomationSettings;
import io.github.brainage04.actionassist.mixin.MinecraftActionInvoker;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class MinecraftAutomationOutput implements AutomationEngine.Output {
    private final Minecraft minecraft;
    private final KeyMapping companionKey;

    private boolean sneakDown;
    private boolean companionDown;
    private Method quickMoveMethod;
    private Object quickMoveType;
    private boolean quickMoveUnavailable;

    public MinecraftAutomationOutput(Minecraft minecraft, KeyMapping companionKey) {
        this.minecraft = minecraft;
        this.companionKey = companionKey;
    }

    @Override
    public void setSneak(boolean down) {
        if (sneakDown == down) {
            return;
        }
        sneakDown = down;
        minecraft.options.keyShift.setDown(down);
    }

    @Override
    public void setCompanionKey(boolean down) {
        if (companionDown == down) {
            return;
        }
        companionDown = down;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.same(companionKey)) {
                mapping.setDown(down);
            }
        }
    }

    @Override
    public void perform(AutomationSettings.Action action) {
        MinecraftActionInvoker invoker = (MinecraftActionInvoker) minecraft;
        if (action == AutomationSettings.Action.USE) {
            invoker.actionassist$startUseItem();
        } else {
            invoker.actionassist$startAttack();
        }
    }

    @Override
    public void dumpHotbar() {
        if (minecraft.player == null || minecraft.gameMode == null || quickMoveUnavailable) {
            return;
        }

        var menu = minecraft.player.inventoryMenu;
        ensureQuickMoveMethod();
        if (quickMoveUnavailable) {
            return;
        }
        for (int slot = 36; slot <= 44; slot++) {
            if (menu.getSlot(slot).hasItem()) {
                try {
                    quickMoveMethod.invoke(
                            minecraft.gameMode,
                            menu.containerId,
                            slot,
                            0,
                            quickMoveType,
                            minecraft.player
                    );
                } catch (ReflectiveOperationException exception) {
                    quickMoveUnavailable = true;
                    throw new IllegalStateException("Could not transfer a hotbar slot", exception);
                }
            }
        }
    }

    private void ensureQuickMoveMethod() {
        if (quickMoveMethod != null || quickMoveUnavailable) {
            return;
        }
        for (Method method : minecraft.gameMode.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 5
                    && parameters[0] == int.class
                    && parameters[1] == int.class
                    && parameters[2] == int.class
                    && parameters[3].isEnum()
                    && parameters[4].isAssignableFrom(minecraft.player.getClass())) {
                Object[] clickTypes = parameters[3].getEnumConstants();
                if (clickTypes.length > 1) {
                    quickMoveMethod = method;
                    quickMoveType = clickTypes[1];
                    return;
                }
            }
        }
        quickMoveUnavailable = true;
        throw new IllegalStateException("Unsupported Minecraft inventory click API");
    }

    public void release() {
        setSneak(false);
        setCompanionKey(false);
    }
}
