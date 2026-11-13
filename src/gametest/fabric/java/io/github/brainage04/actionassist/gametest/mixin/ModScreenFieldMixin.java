package io.github.brainage04.actionassist.gametest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds a Screen-typed member to {@link Minecraft} that is null at the end of every client tick, like Fabric API's
 * {@code Gui.getTickingScreen()} (fabric-screen-api-v1 {@code GuiMixin}, Fabric API 0.156.0+26.2), which holds the
 * screen only while it ticks. A screen lookup that picks members by type, in the order the JVM lists them (unspecified,
 * see {@link Class#getMethods()}), can take such a member and then misses every open screen. On 26.2, where
 * {@code Minecraft} has no vanilla Screen field, a lookup by type sees this field first, so the platform scenario fails
 * on every launch unless the mod reads vanilla's own screen member.
 */
@Mixin(Minecraft.class)
abstract class ModScreenFieldMixin {
    @Unique
    private Screen actionassistGametest$modScreen;
}
