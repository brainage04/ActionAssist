package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.core.Macro;
import io.github.brainage04.actionassist.core.MacroEngine;
import io.github.brainage04.actionassist.mixin.MinecraftActionInvoker;
import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class MinecraftMacroOutput implements MacroEngine.Output {
    /** Vanilla click-kind ordinals for {@link MacroEngine.ClickKind} PICKUP, QUICK_MOVE, QUICK_CRAFT. */
    private static final int[] CLICK_KIND_ORDINALS = {0, 1, 5};

    private final Minecraft minecraft;
    private final String veinMineKeyName;
    private final StatusMessages statusMessages;

    private boolean sneakPressed;
    private boolean veinPressed;
    private KeyMapping veinMineKey;
    private boolean veinMineKeyResolved;
    private BlockPos depositContainer;
    private Method clickMethod;
    private Object[] clickKinds;

    MinecraftMacroOutput(Minecraft minecraft, String veinMineKeyName, StatusMessages statusMessages) {
        this.minecraft = minecraft;
        this.veinMineKeyName = veinMineKeyName;
        this.statusMessages = statusMessages;
    }

    /**
     * Held inputs are re-asserted every tick so focus changes that release all key mappings do not
     * silently drop them; a key is only released if this output pressed it.
     */
    @Override
    public void setSneak(boolean down) {
        if (down) {
            minecraft.options.keyShift.setDown(true);
            sneakPressed = true;
        } else if (sneakPressed) {
            minecraft.options.keyShift.setDown(false);
            sneakPressed = false;
        }
    }

    @Override
    public void setVeinKey(boolean down) {
        KeyMapping mapping = veinMineKey();
        if (mapping == null) {
            return;
        }
        if (down) {
            mapping.setDown(true);
            veinPressed = true;
        } else if (veinPressed) {
            mapping.setDown(false);
            veinPressed = false;
        }
    }

    @Override
    public void use() {
        ((MinecraftActionInvoker) minecraft).actionassist$startUseItem();
    }

    @Override
    public void click(int menuSlot, int button, MacroEngine.ClickKind kind) {
        click(minecraft.player.inventoryMenu, menuSlot, button, kind);
    }

    @Override
    public void depositSlot(int inventorySlot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        var inventory = minecraft.player.getInventory();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                click(menu, index, 0, MacroEngine.ClickKind.QUICK_MOVE);
                return;
            }
        }
    }

    @Override
    public boolean hasDepositContainer() {
        return depositContainer != null;
    }

    @Override
    public boolean openDepositContainer() {
        if (depositContainer == null) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(depositContainer), Direction.UP, depositContainer, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    @Override
    public void closeContainer() {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
    }

    @Override
    public void status(MacroEngine.Status status, Macro macro) {
        ActionAssist.LOGGER.info("{} macro: {}", macro, status);
        statusMessages.show(
                "message.actionassist." + status.name().toLowerCase(Locale.ROOT),
                Component.translatable(
                        "message.actionassist.macro." + macro.name().toLowerCase(Locale.ROOT)));
    }

    BlockPos depositContainer() {
        return depositContainer;
    }

    void setDepositContainer(BlockPos position) {
        depositContainer = position;
    }

    /** The configured vein-mining key mapping, or {@code null} when no loaded mod registers it. */
    KeyMapping veinMineKey() {
        if (!veinMineKeyResolved) {
            veinMineKeyResolved = true;
            for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (mapping.getName().equals(veinMineKeyName)) {
                    veinMineKey = mapping;
                    break;
                }
            }
        }
        return veinMineKey;
    }

    String veinMineKeyName() {
        return veinMineKeyName;
    }

    void release() {
        setSneak(false);
        setVeinKey(false);
    }

    private void click(AbstractContainerMenu menu, int slot, int button, MacroEngine.ClickKind kind) {
        if (clickMethod == null) {
            resolveClickMethod();
        }
        try {
            clickMethod.invoke(minecraft.gameMode, menu.containerId, slot, button, clickKinds[kind.ordinal()], minecraft.player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not click inventory slot " + slot, exception);
        }
    }

    /**
     * Minecraft 1.21.x names the click method {@code handleInventoryMouseClick(..., ClickType, ...)} and
     * 26.x names it {@code handleContainerInput(..., ContainerInput, ...)}. Both keep the shape
     * {@code (int containerId, int slot, int button, enum kind, Player)} and the constant order
     * {@code PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL}. Matching by shape and
     * ordinal stays valid under production intermediary names, where member names differ.
     */
    private void resolveClickMethod() {
        for (Method method : minecraft.gameMode.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 5
                    && parameters[0] == int.class
                    && parameters[1] == int.class
                    && parameters[2] == int.class
                    && parameters[3].isEnum()
                    && parameters[4].isAssignableFrom(minecraft.player.getClass())) {
                Object[] constants = parameters[3].getEnumConstants();
                if (constants.length < CLICK_KIND_ORDINALS[CLICK_KIND_ORDINALS.length - 1] + 1) {
                    continue;
                }
                Object[] resolved = new Object[CLICK_KIND_ORDINALS.length];
                for (int kind = 0; kind < resolved.length; kind++) {
                    resolved[kind] = constants[CLICK_KIND_ORDINALS[kind]];
                }
                clickKinds = resolved;
                clickMethod = method;
                return;
            }
        }
        throw new IllegalStateException("Unsupported Minecraft inventory click API");
    }
}
