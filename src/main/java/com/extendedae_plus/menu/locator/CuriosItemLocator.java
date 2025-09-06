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
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                var handler = opt.get();
                ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
                if (stacksHandler != null) {
                    ItemStack it = stacksHandler.getStacks().getStackInSlot(index);
                    if (!it.isEmpty()) {
                        // 1) ae2wtlib: 优先通过 WTDefinition 工厂创建 WTMenuHost（支持量子桥逻辑）
                        WTDefinition def = WTDefinition.ofOrNull(it);
                        if (def != null) {
                            WTMenuHost wtHost = def.wTMenuHostFactory().create(
                                    def.item(),
                                    player,
                                    this,
                                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this)
                            );
                            if (hostInterface.isInstance(wtHost)) {
                                return hostInterface.cast(wtHost);
                            }
                        }

                        // 2) 回退：AE2 原生无线终端（IMenuItem）
                        if (it.getItem() instanceof WirelessTerminalItem) {
                            // 由 IMenuItem 提供菜单宿主，定位器传入当前 CuriosItemLocator
                            if (it.getItem() instanceof IMenuItem guiItem) {
                                ItemMenuHost menuHost = guiItem.getMenuHost(player, this, null);
                                if (hostInterface.isInstance(menuHost)) {
                                    return hostInterface.cast(menuHost);
                                }
                            }
                        } else if (it.getItem() instanceof IMenuItem guiItem) {
                            // 回退：非无线终端，按常规 IMenuItem 处理
                            ItemMenuHost menuHost = guiItem.getMenuHost(player, this, null);
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
