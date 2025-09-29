package com.extendedae_plus.menu.locator;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.me.common.MEStorageMenu;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 适配 Curios 槽位的自定义 MenuLocator：
 * 通过 slotId + index 在两端查找 Curios 实际物品引用，确保 NBT 变化（如耗电）能持久化。
 */
public record CuriosItemLocator(String slotId, int index) implements ItemMenuHostLocator {
    @Override
    @Nullable
    public <T> T locate(Player player, Class<T> hostInterface) {
        try {
            // 先用 locateItem 取得实际物品，避免某些情况下 stacksHandler 为空
            ItemStack it0 = locateItem(player);
            if (it0 != null && !it0.isEmpty()) {
                // 0) AE2 原生：若是 WirelessTerminalItem，先尝试其原生 Host（与背包一致）
                if (it0.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem wtAe2) {
                    var aeHost = wtAe2.getMenuHost(player, this, null);
                    if (hostInterface.isInstance(aeHost)) {
                        try { return hostInterface.cast(aeHost); } catch (Throwable ignored) { }
                    }
                    if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                        var subHost2 = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost2(wtAe2, player, this,
                                (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                        try { return hostInterface.cast(subHost2); } catch (Throwable ignored) { }
                    }
                }

                WTDefinition def0 = WTDefinition.ofOrNull(it0);
                if (def0 != null) {
                    WTMenuHost wtHost = def0.wTMenuHostFactory().create(def0.item(), player, this,
                            (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                    if (hostInterface.isInstance(wtHost)) {
                        try { return hostInterface.cast(wtHost); } catch (Throwable ignored) { }
                    }
                    if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                        var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(def0.item(), player, this,
                                (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                        try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                    }
                } else if (it0.getItem() instanceof de.mari_023.ae2wtlib.api.terminal.ItemWT wtItem0) {
                    if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                        var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(wtItem0, player, this,
                                (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                        try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                    }
                } else {
                    // 额外兜底：尝试使用 WTDefinition.of(it0)（可能在 ofOrNull 为 null 时仍可获取）
                    try {
                        WTDefinition defStrict = WTDefinition.of(it0);
                        if (defStrict != null && "appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                            var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(defStrict.item(), player, this,
                                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                            try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                        }
                    } catch (Throwable ignored) {
                        
                    }
                }
            }

            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                var handler = opt.get();
                ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
                if (stacksHandler != null) {
                    ItemStack it = stacksHandler.getStacks().getStackInSlot(index);
                    if (!it.isEmpty()) {
                        // 1) wtlib 优先：WTDefinition 构造 WTMenuHost
                        // 0) AE2 原生：若是 WirelessTerminalItem，先尝试其原生 Host（与背包一致）
                        if (it.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem wtAe2) {
                            var aeHost = wtAe2.getMenuHost(player, this, null);
                            if (hostInterface.isInstance(aeHost)) {
                                try { return hostInterface.cast(aeHost); } catch (Throwable ignored) { }
                            }
                            if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                                var subHost2 = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost2(wtAe2, player, this,
                                        (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                                try { return hostInterface.cast(subHost2); } catch (Throwable ignored) { }
                            }
                        }

                        WTDefinition def = WTDefinition.ofOrNull(it);
                        if (def != null) {
                            WTMenuHost wtHost = def.wTMenuHostFactory().create(
                                    def.item(), player, this,
                                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                            if (hostInterface.isInstance(wtHost)) {
                                try { return hostInterface.cast(wtHost); } catch (Throwable ignored) { }
                            }
                            // 桥接 ISubMenuHost
                            if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                                var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(def.item(), player, this,
                                        (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                                try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                            }
                        } else {
                            // 2) def==null，但物品是 wtlib 的 ItemWT：直接桥接 ISubMenuHost
                            if (it.getItem() instanceof de.mari_023.ae2wtlib.api.terminal.ItemWT wtItem) {
                                if ("appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                                    var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(wtItem, player, this,
                                            (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                                    try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                                }
                            } else {
                                // 再次兜底：尝试 WTDefinition.of(it) 强制获取
                                try {
                                    WTDefinition defStrict2 = WTDefinition.of(it);
                                    if (defStrict2 != null && "appeng.api.storage.ISubMenuHost".equals(hostInterface.getName())) {
                                        var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost(defStrict2.item(), player, this,
                                                (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
                                        try { return hostInterface.cast(subHost); } catch (Throwable ignored) { }
                                    }
                                } catch (Throwable ignored) {
                                    
                                }
                            }
                        }

                        // 3) 回退：AE2 原生无线终端（IMenuItem）
                        if (it.getItem() instanceof WirelessTerminalItem) {
                            if (it.getItem() instanceof IMenuItem guiItem) {
                                ItemMenuHost<?> menuHost = guiItem.getMenuHost(player, this, null);
                                if (hostInterface.isInstance(menuHost)) {
                                    try { return hostInterface.cast(menuHost); } catch (Throwable ignored) { }
                                }
                            }
                        } else if (it.getItem() instanceof IMenuItem guiItem) {
                            ItemMenuHost<?> menuHost = guiItem.getMenuHost(player, this, null);
                            if (hostInterface.isInstance(menuHost)) {
                                try { return hostInterface.cast(menuHost); } catch (Throwable ignored) { }
                            }
                        }

                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public ItemStack locateItem(Player player) {
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                ICuriosItemHandler handler = opt.get();
                ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
                if (stacksHandler != null) {
                    return stacksHandler.getStacks().getStackInSlot(index);
                }
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeUtf(slotId);
        buf.writeVarInt(index);
    }

    public static CuriosItemLocator readFromPacket(FriendlyByteBuf buf) {
        String slotId = buf.readUtf();
        int index = buf.readVarInt();
        return new CuriosItemLocator(slotId, index);
    }
}
