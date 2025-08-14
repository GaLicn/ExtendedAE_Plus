package com.extendedae_plus.menu.locator;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.locator.MenuLocator;
import com.extendedae_plus.menu.host.CuriosWirelessTerminalMenuHost;

// Curios API (软依赖)
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
                        if (it.getItem() instanceof WirelessTerminalItem) {
                            // 为 Curios 构建一个带回写能力的宿主
                            WirelessTerminalMenuHost host = new CuriosWirelessTerminalMenuHost(
                                    player,
                                    it,
                                    stacksHandler,
                                    index,
                                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this)
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
