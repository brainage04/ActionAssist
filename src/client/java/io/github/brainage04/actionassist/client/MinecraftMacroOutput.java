package io.github.brainage04.actionassist.client;

import io.github.brainage04.actionassist.ActionAssist;
import io.github.brainage04.actionassist.core.Macro;
import io.github.brainage04.actionassist.core.MacroEngine;
import io.github.brainage04.actionassist.mixin.MinecraftActionInvoker;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class MinecraftMacroOutput implements MacroEngine.Output {
    private final Minecraft minecraft;
    private final String veinMineKeyName;
    private final StatusMessages statusMessages;

    private boolean sneakPressed;
    private boolean veinPressed;
    private KeyMapping veinMineKey;
    private boolean veinMineKeyResolved;
    private BlockPos depositContainer;
    private ResourceKey<Level> depositDimension;
    private AbstractContainerMenu depositMenu;
    private boolean awaitingDepositMenu;

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
            setSneakKey(true);
            sneakPressed = true;
        } else if (sneakPressed) {
            setSneakKey(false);
            sneakPressed = false;
        }
    }

    /**
     * With vanilla's Sneak: Toggle option, {@code keyShift.setDown(true)} flips the key and {@code setDown(false)} does
     * nothing ({@code ToggleKeyMapping.setDown}, 1.21.11 {@code ToggleKeyMapping.java:30-38}, 26.2 {@code :36-44}), so
     * in that mode the key is flipped only when it is not already in the wanted state.
     */
    private void setSneakKey(boolean down) {
        KeyMapping sneak = minecraft.options.keyShift;
        if (!minecraft.options.toggleCrouch().get()) {
            sneak.setDown(down);
        } else if (sneak.isDown() != down) {
            sneak.setDown(true);
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
        AbstractContainerMenu menu = depositMenu;
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
        return depositContainer != null && depositDimension.equals(minecraft.level.dimension());
    }

    @Override
    public boolean openDepositContainer() {
        if (!hasDepositContainer()) {
            return false;
        }
        depositMenu = null;
        awaitingDepositMenu = true;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(depositContainer), Direction.UP, depositContainer, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    @Override
    public void closeContainer() {
        if (depositMenu != null && isDepositContainerOpen()) {
            minecraft.player.closeContainer();
        }
        depositMenu = null;
        awaitingDepositMenu = false;
    }

    /** Claims the first container response to our open request, then requires that exact menu. */
    boolean isDepositContainerOpen() {
        if (minecraft.player == null
                || minecraft.player.containerMenu == minecraft.player.inventoryMenu
                || !(ScreenAccess.currentScreen(minecraft) instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu() != minecraft.player.containerMenu) {
            return false;
        }
        if (awaitingDepositMenu) {
            depositMenu = minecraft.player.containerMenu;
            awaitingDepositMenu = false;
        }
        return depositMenu != null && minecraft.player.containerMenu == depositMenu;
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

    boolean isDepositContainer(BlockPos position) {
        return hasDepositContainer() && depositContainer.equals(position);
    }

    void setDepositContainer(BlockPos position, ResourceKey<Level> dimension) {
        depositContainer = position;
        depositDimension = dimension;
    }

    void clearDepositContainer() {
        setDepositContainer(null, null);
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
        depositMenu = null;
        awaitingDepositMenu = false;
    }

    private void click(AbstractContainerMenu menu, int slot, int button, MacroEngine.ClickKind kind) {
        ContainerClicks.click(minecraft.gameMode, menu.containerId, slot, button, kind, minecraft.player);
    }
}
