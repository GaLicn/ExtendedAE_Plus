package com.extendedae_plus.util.wireless;

import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.api.networking.IGrid;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.compat.ae2wtlib.AE2WTLibCompat;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.function.Consumer;

/**
 * 定位玩家身上的无线终端：
 * - 原版槽位：主手、副手、盔甲、背包
 * - 若加载了 Curios：遍历所有饰品槽
 * 返回一个可写回的结果，以便能量消耗等 NBT 变更能持久化。
 */
public final class WirelessTerminalLocator {
    private WirelessTerminalLocator() {}

    public static LocatedTerminal find(Player player) {
        if (player == null) return new LocatedTerminal(ItemStack.EMPTY, s -> {});

        // 1) 先检查主手/副手
        var main = player.getMainHandItem();
        if (isWirelessTerminal(main)) {
            return new LocatedTerminal(main, (ns) -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ns), -1, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        var off = player.getOffhandItem();
        if (isWirelessTerminal(off)) {
            return new LocatedTerminal(off, (ns) -> player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ns), -1, net.minecraft.world.InteractionHand.OFF_HAND);
        }

        // 2) 原版槽位
        var inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (isWirelessTerminal(st)) {
                final int slot = i;
                return new LocatedTerminal(st, (ns) -> inv.setItem(slot, ns), slot);
            }
        }

        // 3) Curios 饰品槽（若已加载）
        if (ModList.get().isLoaded("curios")) {
            try {
                var opt = CuriosApi.getCuriosInventory(player);
                if (opt.isPresent()) {
                    ICuriosItemHandler handler = opt.get();
                    for (var entry : handler.getCurios().entrySet()) {
                        String slotId = entry.getKey();
                        ICurioStacksHandler stacksHandler = entry.getValue();
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        int slots = stacks.getSlots();
                        for (int i = 0; i < slots; i++) {
                            ItemStack st = stacks.getStackInSlot(i);
                            if (isWirelessTerminal(st)) {
                                final int slot = i;
                                java.util.function.Consumer<ItemStack> setter = (ns) -> stacks.setStackInSlot(slot, ns);
                                return new LocatedTerminal(st, setter, -1, null, slotId, slot);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 若 Curios API 在运行时不可用或发生异常，则忽略并返回空
            }
        }

        return new LocatedTerminal(ItemStack.EMPTY, s -> {}, -1, null);
    }

    /**
     * 通过终端所属模组的菜单主机获取可访问网络，兼容 WTLib 的量子桥逻辑。
     */
    @Nullable
    public static IGrid getConnectedGrid(Player player, LocatedTerminal terminal) {
        if (player == null || terminal.isEmpty()) {
            return null;
        }

        var locator = terminal.createMenuLocator(player);
        if (locator == null) {
            return null;
        }

        // 优先调用物品自己的宿主 API。EAE 终端虽由 WTLib 注册，但这里会保留它的专用宿主类型。
        if (terminal.stack.getItem() instanceof WirelessTerminalItem wirelessTerminal) {
            ItemMenuHost menuHost = wirelessTerminal.getMenuHost(player, locator, null);
            if (menuHost instanceof WirelessTerminalMenuHost<?> wirelessHost) {
                if (wirelessHost.getLinkStatus().connected() && wirelessHost.getActionableNode() != null) {
                    return wirelessHost.getActionableNode().getGrid();
                }
                return null;
            }
        }

        if (ModList.get().isLoaded("ae2wtlib") && AE2WTLibCompat.isWirelessTerminal(terminal.stack)) {
            // 只有物品没有原生 AE2 宿主时，才通过 WTLib 兼容层构造菜单宿主。
            return AE2WTLibCompat.getConnectedGrid(player, terminal.stack, locator);
        }

        return null;
    }

    /**
     * WTLib 菜单主机会自行维护能耗，原版 AE2 终端才在快捷操作后额外扣电。
     */
    public static boolean useTerminalPower(Player player, LocatedTerminal terminal, double amount) {
        if (ModList.get().isLoaded("ae2wtlib") && AE2WTLibCompat.isWirelessTerminal(terminal.stack)) {
            return true;
        }
        if (terminal.stack.getItem() instanceof WirelessTerminalItem wirelessTerminal) {
            return wirelessTerminal.usePower(player, amount, terminal.stack);
        }
        return false;
    }

    private static boolean isWirelessTerminal(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof WirelessCraftingTerminalItem
                || stack.getItem() instanceof WirelessTerminalItem
                || (ModList.get().isLoaded("ae2wtlib") && AE2WTLibCompat.isWirelessTerminal(stack)));
    }

    public static final class LocatedTerminal {
        public final ItemStack stack;
        private final Consumer<ItemStack> setter;
        // 在玩家 Inventory 中的槽位索引（0..size-1）。若未知则为 -1。
        private final int slotIndex;
        // 若终端在玩家手上，则记录手别；否则为 null。
        private final net.minecraft.world.InteractionHand hand;
        // 若终端位于 Curios，则记录其槽位组 ID 与组内索引；否则 slotId 为 null，index 为 -1。
        private final String curiosSlotId;
        private final int curiosIndex;

        LocatedTerminal(ItemStack stack, Consumer<ItemStack> setter) {
            this(stack, setter, -1, null, null, -1);
        }

        LocatedTerminal(ItemStack stack, Consumer<ItemStack> setter, int slotIndex) {
            this(stack, setter, slotIndex, null, null, -1);
        }

        LocatedTerminal(ItemStack stack, Consumer<ItemStack> setter, int slotIndex, net.minecraft.world.InteractionHand hand) {
            this(stack, setter, slotIndex, hand, null, -1);
        }

        LocatedTerminal(ItemStack stack, Consumer<ItemStack> setter, int slotIndex, net.minecraft.world.InteractionHand hand, String curiosSlotId, int curiosIndex) {
            this.stack = stack;
            this.setter = setter;
            this.slotIndex = slotIndex;
            this.hand = hand;
            this.curiosSlotId = curiosSlotId;
            this.curiosIndex = curiosIndex;
        }

        public void set(ItemStack newStack) { this.setter.accept(newStack); }
        public void commit() { this.setter.accept(this.stack); }
        public boolean isEmpty() { return this.stack == null || this.stack.isEmpty(); }
        /** 若返回 -1，说明不是从原版 Inventory 槽位中找到（比如 Curios）。 */
        public int getSlotIndex() { return this.slotIndex; }
        /** 若不为 null，说明终端在玩家手上。 */
        public net.minecraft.world.InteractionHand getHand() { return this.hand; }
        /** 若不为 null，说明终端位于 Curios 指定槽位组。 */
        public String getCuriosSlotId() { return this.curiosSlotId; }
        /** Curios 组内索引，未知时为 -1。 */
        public int getCuriosIndex() { return this.curiosIndex; }

        @Nullable
        public ItemMenuHostLocator createMenuLocator(Player player) {
            if (this.curiosSlotId != null && this.curiosIndex >= 0) {
                return new CuriosItemLocator(this.curiosSlotId, this.curiosIndex);
            }
            if (this.hand != null) {
                return MenuLocators.forHand(player, this.hand);
            }
            if (this.slotIndex >= 0) {
                return MenuLocators.forInventorySlot(this.slotIndex);
            }
            return null;
        }
    }
}
