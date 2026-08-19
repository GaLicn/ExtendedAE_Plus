package com.extendedae_plus.menu.locator;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.storage.ISubMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.compat.ae2wtlib.AE2WTLibCompat;
import com.extendedae_plus.menu.host.CuriosWirelessCraftingTerminalMenuHost;
import com.extendedae_plus.menu.host.CuriosWirelessTerminalMenuHost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 适配 Curios 槽位的自定义 MenuLocator：
 * 通过 slotId + index 在两端查找 Curios 实际物品引用，确保 NBT 变化（如耗电）能持久化。
 */
public record CuriosItemLocator(String slotId, int index) implements MenuLocator {
    @Override
    @Nullable
    public <T> T locate(Player player, Class<T> hostInterface) {
        try {
            var resolved = CuriosApi.getCuriosInventory(player).resolve();
            if (resolved.isPresent()) {
                var handler = resolved.get();
                ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
                if (stacksHandler != null) {
                    ItemStack it = stacksHandler.getStacks().getStackInSlot(index);
                    if (!it.isEmpty()) {
                        // WTLib 类型只在兼容模组存在时由隔离层解析。
                        if (ModList.get().isLoaded("ae2wtlib")) {
                            T wtHost = AE2WTLibCompat.locateMenuHost(it, player, this, hostInterface, slotId, index);
                            if (wtHost != null) {
                                return wtHost;
                            }
                        }

                        // 回退：AE2 原生无线终端
                        if (it.getItem() instanceof WirelessTerminalItem) {
                            if (it.getItem() instanceof WirelessCraftingTerminalItem craftingTerminal
                                    && hostInterface != ISubMenuHost.class) {
                                // 无线合成终端的主菜单要求 WirelessCraftingTerminalMenuHost。
                                var host = new CuriosWirelessCraftingTerminalMenuHost(
                                        player,
                                        it,
                                        stacksHandler,
                                        index,
                                        (p, sub) -> MenuOpener.open(craftingTerminal.getMenuType(), p, this, true)
                                );
                                if (hostInterface.isInstance(host)) {
                                    return hostInterface.cast(host);
                                }
                            }
                            // 首选：为 CraftAmountMenu 等需要网络/能量上下文的菜单提供 WirelessTerminalMenuHost
                            WirelessTerminalMenuHost host = new CuriosWirelessTerminalMenuHost(
                                    player,
                                    it,
                                    stacksHandler,
                                    index,
                                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this, true)
                            );
                            if (hostInterface.isInstance(host)) {
                                return hostInterface.cast(host);
                            }
                        } else if (it.getItem() instanceof IMenuItem guiItem) {
                            // 回退：非无线终端，按常规 IMenuItem 处理
                            ItemMenuHost menuHost = guiItem.getMenuHost(player, -1, it, null);
                            if (hostInterface.isInstance(menuHost)) {
                                return hostInterface.cast(menuHost);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
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
