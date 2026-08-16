package com.extendedae_plus.menu.locator;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.storage.ISubMenuHost;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import com.extendedae_plus.compat.ae2wtlib.AE2WTLibCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
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
            ItemStack stack = locateItem(player);
            if (stack.isEmpty()) {
                return null;
            }

            if (hostInterface == ISubMenuHost.class
                    && stack.getItem() instanceof WirelessCraftingTerminalItem craftingTerminal) {
                // 与背包槽位保持同一 AE2 宿主类型，只增加 Curios 槽位定位能力。
                var subHost = new com.extendedae_plus.menu.host.CuriosWirelessCraftingTerminalMenuHost(
                        craftingTerminal, player, this,
                        (p, sub) -> craftingTerminal.openFromInventory(p, this));
                return hostInterface.cast(subHost);
            }

            if (stack.getItem() instanceof WirelessTerminalItem wirelessTerminal) {
                ItemMenuHost host = wirelessTerminal.getMenuHost(player, this, null);
                if (host != null && hostInterface.isInstance(host)) {
                    return hostInterface.cast(host);
                }
                if (hostInterface == ISubMenuHost.class
                        && (!ModList.get().isLoaded("ae2wtlib") || !AE2WTLibCompat.isWirelessTerminal(stack))) {
                    // WTLib 终端必须继续走下面的 WTLib 宿主，不能被 AE2 基础宿主提前截断。
                    var subHost = new com.extendedae_plus.menu.host.CuriosWTSubMenuHost2(wirelessTerminal, player, this,
                            (p, sub) -> wirelessTerminal.openFromInventory(p, this));
                    return hostInterface.cast(subHost);
                }
            }

            if (ModList.get().isLoaded("ae2wtlib")) {
                // 仅在终端自身没有返回兼容宿主时，使用 WTLib 的宿主定义。
                T wtHost = AE2WTLibCompat.locateMenuHost(stack, player, this, hostInterface,
                        (p, sub) -> AE2WTLibCompat.reopenTerminal(p, this));
                if (wtHost != null) {
                    return wtHost;
                }
            }

            if (stack.getItem() instanceof IMenuItem guiItem) {
                ItemMenuHost host = guiItem.getMenuHost(player, this, null);
                if (host != null && hostInterface.isInstance(host)) {
                    return hostInterface.cast(host);
                }
            }
        } catch (Throwable error) {
            // 保留异常上下文，避免 Curios 菜单宿主失败时只能看到“没有反应”。
            ItemMenuHostLocator.LOG.error("Failed to locate Curios terminal menu host", error);
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
                    ItemStack s = stacksHandler.getStacks().getStackInSlot(index);
                    return s;
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
