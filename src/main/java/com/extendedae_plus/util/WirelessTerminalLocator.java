package com.extendedae_plus.util;

import java.util.function.Consumer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.LazyOptional;

import appeng.items.tools.powered.WirelessTerminalItem;

// Curios API (软依赖)
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * 定位玩家身上的无线终端：
 * - 原版槽位：主手、副手、盔甲、背包
 * - 若加载了 Curios：遍历所有饰品槽
 * 返回一个可写回的结果，以便能量消耗等 NBT 变更能持久化。
 */
public final class WirelessTerminalLocator {
    private WirelessTerminalLocator() {}

    public static final class LocatedTerminal {
        public final ItemStack stack;
        private final Consumer<ItemStack> setter;

        public LocatedTerminal(ItemStack stack, Consumer<ItemStack> setter) {
            this.stack = stack;
            this.setter = setter;
        }

        public void set(ItemStack newStack) { this.setter.accept(newStack); }
        public void commit() { this.setter.accept(this.stack); }
        public boolean isEmpty() { return this.stack == null || this.stack.isEmpty(); }
    }

    public static LocatedTerminal find(Player player) {
        if (player == null) return new LocatedTerminal(ItemStack.EMPTY, s -> {});

        // 1) 原版槽位
        var inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() instanceof WirelessTerminalItem) {
                final int slot = i;
                return new LocatedTerminal(st, (ns) -> inv.setItem(slot, ns));
            }
        }

        // 2) Curios 饰品槽（若已加载）
        if (ModList.get().isLoaded("curios")) {
            try {
                // Curios 1.20.x: 通过 CuriosApi.getCuriosInventory 获取 LazyOptional
                var resolved = CuriosApi.getCuriosInventory(player).resolve();
                if (resolved.isPresent()) {
                    ICuriosItemHandler handler = resolved.get();
                    for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        int slots = stacks.getSlots();
                        for (int i = 0; i < slots; i++) {
                            ItemStack st = stacks.getStackInSlot(i);
                            if (!st.isEmpty() && st.getItem() instanceof WirelessTerminalItem) {
                                final int slot = i;
                                return new LocatedTerminal(st, (ns) -> stacks.setStackInSlot(slot, ns));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 若 Curios API 在运行时不可用或发生异常，则忽略并返回空
            }
        }

        return new LocatedTerminal(ItemStack.EMPTY, s -> {});
    }
}
