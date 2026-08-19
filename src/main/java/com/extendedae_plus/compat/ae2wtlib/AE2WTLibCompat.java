package com.extendedae_plus.compat.ae2wtlib;

import appeng.api.networking.IGrid;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.locator.MenuLocator;
import com.extendedae_plus.menu.host.CuriosWTMenuHost;
import com.extendedae_plus.menu.host.CuriosWTSubMenuHost;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * AE2WTLib 的可选兼容层。通用代码不得直接引用 WTLib 类型，避免未安装时类加载失败。
 */
public final class AE2WTLibCompat {
    private AE2WTLibCompat() {
    }

    public static boolean isWirelessTerminal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return getDefinition(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static IGrid getConnectedGrid(Player player, ItemStack stack, MenuLocator locator,
                                         @Nullable Integer inventorySlot, @Nullable String curiosSlotId,
                                         int curiosIndex) {
        WTMenuHost host = createHost(player, stack, locator, inventorySlot, curiosSlotId, curiosIndex);
        if (host == null || !host.rangeCheck()) {
            return null;
        }
        var node = host.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    public static boolean useTerminalPower(Player player, ItemStack stack, MenuLocator locator,
                                           @Nullable Integer inventorySlot, @Nullable String curiosSlotId,
                                           int curiosIndex) {
        WTMenuHost host = createHost(player, stack, locator, inventorySlot, curiosSlotId, curiosIndex);
        return host != null && host.rangeCheck() && host.drainPower();
    }

    @Nullable
    public static <T> T locateMenuHost(ItemStack stack, Player player, MenuLocator locator,
                                       Class<T> hostInterface, String curiosSlotId, int curiosIndex) {
        WTDefinition definition = getDefinition(stack);
        if (definition == null) {
            return null;
        }

        try {
            if (hostInterface == ISubMenuHost.class && curiosSlotId != null && curiosIndex >= 0) {
                var resolved = CuriosApi.getCuriosInventory(player).resolve();
                if (resolved.isEmpty()) {
                    return null;
                }
                ICurioStacksHandler handler = resolved.get().getCurios().get(curiosSlotId);
                if (handler == null) {
                    return null;
                }
                var host = new CuriosWTSubMenuHost(player, null, stack, handler, curiosIndex,
                        (ignoredPlayer, ignoredMenu) -> reopenTerminal(ignoredPlayer, locator));
                return hostInterface.isInstance(host) ? hostInterface.cast(host) : null;
            }

            WTMenuHost host = definition.wTMenuHostFactory().create(player, null, stack,
                    (ignoredPlayer, ignoredMenu) -> reopenTerminal(ignoredPlayer, locator));
            return hostInterface.isInstance(host) ? hostInterface.cast(host) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void reopenTerminal(Player player, MenuLocator locator) {
        try {
            WUTHandler.open(player, locator, true);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static WTDefinition getDefinition(ItemStack stack) {
        String current = WUTHandler.getCurrentTerminal(stack);
        return current == null || current.isEmpty() ? null : WUTHandler.wirelessTerminals.get(current);
    }

    @Nullable
    private static WTMenuHost createHost(Player player, ItemStack stack, MenuLocator locator,
                                         @Nullable Integer inventorySlot, @Nullable String curiosSlotId,
                                         int curiosIndex) {
        WTDefinition definition = getDefinition(stack);
        if (definition == null) {
            return null;
        }

        try {
            if (curiosSlotId != null && curiosIndex >= 0) {
                var resolved = CuriosApi.getCuriosInventory(player).resolve();
                if (resolved.isEmpty()) {
                    return null;
                }
                ICurioStacksHandler handler = resolved.get().getCurios().get(curiosSlotId);
                if (handler == null) {
                    return null;
                }
                return new CuriosWTMenuHost(player, null, stack, handler, curiosIndex,
                        (ignoredPlayer, ignoredMenu) -> reopenTerminal(ignoredPlayer, locator));
            }
            return definition.wTMenuHostFactory().create(player, inventorySlot, stack,
                    (ignoredPlayer, ignoredMenu) -> reopenTerminal(ignoredPlayer, locator));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
