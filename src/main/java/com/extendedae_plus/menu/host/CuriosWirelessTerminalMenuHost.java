package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 针对 Curios 槽位的无线终端菜单宿主。
 * 关键点：在 onBroadcastChanges 周期性把 getItemStack() 回写到 Curios 槽位，
 * 以持久化能量消耗等 NBT 变化。
 */
public class CuriosWirelessTerminalMenuHost extends WirelessTerminalMenuHost implements ISubMenuHost {
    private final ICurioStacksHandler curiosHandler;
    private final int curiosIndex;

    public CuriosWirelessTerminalMenuHost(Player player,
                                          ItemStack itemStack,
                                          ICurioStacksHandler curiosHandler,
                                          int curiosIndex,
                                          java.util.function.BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, null, itemStack, returnToMainMenu);
        this.curiosHandler = curiosHandler;
        this.curiosIndex = curiosIndex;
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        // 将当前 ItemStack 写回 Curios 槽位，保证 NBT 改动（如耗电）持久化
        try {
            ItemStack current = getItemStack();
            curiosHandler.getStacks().setStackInSlot(curiosIndex, current);
        } catch (Throwable ignored) {
        }
        return super.onBroadcastChanges(menu);
    }
}
