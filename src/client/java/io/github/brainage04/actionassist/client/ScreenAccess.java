package io.github.brainage04.actionassist.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ScreenAccess {
    private static Accessor accessor;

    private ScreenAccess() {
    }

    static boolean isScreenOpen(Minecraft minecraft) {
        try {
            if (accessor == null) {
                accessor = resolve(minecraft);
            }
            return accessor.get(minecraft) != null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect the current Minecraft screen", exception);
        }
    }

    private static Accessor resolve(Minecraft minecraft) throws ReflectiveOperationException {
        for (Class<?> type = Minecraft.class; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Screen.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return instance -> field.get(instance);
                }
            }
        }

        Object gui = minecraft.gui;
        for (Method method : gui.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && Screen.class.isAssignableFrom(method.getReturnType())) {
                return instance -> method.invoke(instance.gui);
            }
        }
        throw new NoSuchMethodException("Unsupported Minecraft screen API");
    }

    @FunctionalInterface
    private interface Accessor {
        Object get(Minecraft minecraft) throws ReflectiveOperationException;
    }
}
