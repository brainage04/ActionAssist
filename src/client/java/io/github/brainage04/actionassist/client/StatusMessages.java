package io.github.brainage04.actionassist.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shows Action Assist messages on the action bar across the supported status-message APIs. */
final class StatusMessages {
    private final Minecraft minecraft;
    private final boolean enabled;
    private Method method;
    private boolean methodHasOverlayFlag;

    StatusMessages(Minecraft minecraft, boolean enabled) {
        this.minecraft = minecraft;
        this.enabled = enabled;
    }

    void show(String translationKey, Object... arguments) {
        if (!enabled || minecraft.player == null) {
            return;
        }
        Component message = Component.translatable(translationKey, arguments);
        try {
            if (method == null) {
                resolve();
            }
            if (methodHasOverlayFlag) {
                method.invoke(minecraft.player, message, true);
            } else {
                method.invoke(minecraft.player, message);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not display an Action Assist status message", exception);
        }
    }

    private void resolve() throws NoSuchMethodException {
        Method componentOnly = null;
        for (Method candidate : minecraft.player.getClass().getMethods()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == Component.class && parameters[1] == boolean.class) {
                method = candidate;
                methodHasOverlayFlag = true;
                return;
            }
            if (parameters.length == 1 && parameters[0] == Component.class) {
                componentOnly = candidate;
            }
        }
        if (componentOnly != null) {
            method = componentOnly;
            return;
        }
        throw new NoSuchMethodException("Unsupported Minecraft status message API");
    }
}
