package io.github.brainage04.actionassist.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

/**
 * The open screen on Minecraft 26.x: 26.1.2 keeps it in {@code Minecraft.screen}, 26.2 moved it to {@code Gui.screen()}.
 * 26.x runs with Mojang's names on every loader, so vanilla's member is found by name. A lookup by type would also
 * match members other mods add, such as Fabric API's {@code Gui.getTickingScreen()}, which is null outside the screen
 * tick; the JVM lists methods in no particular order ({@link Class#getMethods()}), so such a lookup can take it and
 * then miss every open screen.
 */
public final class ScreenAccess {
    private static final MethodHandle CURRENT_SCREEN = currentScreenHandle();

    private ScreenAccess() {
    }

    public static boolean isScreenOpen(Minecraft minecraft) {
        return currentScreen(minecraft) != null;
    }

    /** The open screen, or {@code null}. */
    public static Screen currentScreen(Minecraft minecraft) {
        try {
            return (Screen) CURRENT_SCREEN.invokeExact(minecraft);
        } catch (Throwable exception) {
            throw new IllegalStateException("Could not inspect the current Minecraft screen", exception);
        }
    }

    private static MethodHandle currentScreenHandle() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            try {
                return lookup.findGetter(Minecraft.class, "screen", Screen.class);
            } catch (NoSuchFieldException movedToGui) {
                return MethodHandles.filterArguments(
                        lookup.findVirtual(Gui.class, "screen", MethodType.methodType(Screen.class)),
                        0,
                        lookup.findGetter(Minecraft.class, "gui", Gui.class));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Minecraft screen API", exception);
        }
    }
}
