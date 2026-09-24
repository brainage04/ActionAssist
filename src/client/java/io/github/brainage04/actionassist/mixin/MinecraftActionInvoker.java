package io.github.brainage04.actionassist.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftActionInvoker {
    @Invoker("startUseItem")
    void actionassist$startUseItem();
}
