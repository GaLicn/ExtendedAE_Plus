package com.extendedae_plus.menu.locator;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.MenuLocator;

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
                    if (!it.isEmpty() && it.getItem() instanceof IMenuItem guiItem) {
                        ItemMenuHost menuHost = guiItem.getMenuHost(player, -1, it, null);
                        if (hostInterface.isInstance(menuHost)) {
                            return hostInterface.cast(menuHost);
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
