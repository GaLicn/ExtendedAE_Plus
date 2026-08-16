package com.extendedae_plus.menu.host;

import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.menu.ISubMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.BiConsumer;

/**
 * 让原生无线合成终端在 Curios 中仍保留其专用主菜单宿主类型。
 */
public final class CuriosWirelessCraftingTerminalMenuHost extends WirelessCraftingTerminalMenuHost {
    private final ICurioStacksHandler curiosHandler;
    private final int curiosIndex;

    public CuriosWirelessCraftingTerminalMenuHost(Player player,
                                                  ItemStack itemStack,
                                                  ICurioStacksHandler curiosHandler,
                                                  int curiosIndex,
                                                  BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, null, itemStack, returnToMainMenu);
        this.curiosHandler = curiosHandler;
        this.curiosIndex = curiosIndex;
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        // 直接回写饰品槽，持久化合成格与能量等终端 NBT。
        try {
            curiosHandler.getStacks().setStackInSlot(curiosIndex, getItemStack());
        } catch (Throwable ignored) {
        }
        return super.onBroadcastChanges(menu);
    }
}
