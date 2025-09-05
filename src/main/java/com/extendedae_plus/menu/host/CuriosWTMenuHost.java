package com.extendedae_plus.menu.host;

import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.BiConsumer;

/**
 * 针对 Curios 槽位的 ae2wtlib WTMenuHost 适配器：
 * - 复用 wtlib 的量子卡跨维/跨距逻辑（rangeCheck/isQuantumLinked）。
 * - 覆写槽位校验与回写，改为使用 Curios 实际槽位，避免 wtlib 的 Trinkets 平台判断失效。
 */
public class CuriosWTMenuHost extends WTMenuHost {
    private final ICurioStacksHandler curiosHandler;
    private final int curiosIndex;

    public CuriosWTMenuHost(Player player,
                            @Nullable Integer inventorySlot,
                            ItemStack is,
                            ICurioStacksHandler curiosHandler,
                            int curiosIndex,
                            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, is, returnToMainMenu);
        this.curiosHandler = curiosHandler;
        this.curiosIndex = curiosIndex;
        // 初始化内部库存（含奇点槽），以便量子桥判定能够读取到频率等 NBT
        try {
            super.readFromNbt();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected boolean ensureItemStillInSlot() {
        try {
            ItemStack cur = curiosHandler.getStacks().getStackInSlot(curiosIndex);
            return !cur.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        try {
            ItemStack current = getItemStack();
            curiosHandler.getStacks().setStackInSlot(curiosIndex, current);
        } catch (Throwable ignored) {
        }
        return super.onBroadcastChanges(menu);
    }
}
