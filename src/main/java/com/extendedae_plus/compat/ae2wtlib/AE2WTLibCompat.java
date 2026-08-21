package com.extendedae_plus.compat.ae2wtlib;

import appeng.api.networking.IGrid;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import com.extendedae_plus.menu.host.CuriosWTSubMenuHost;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * AE2WTLib 的可选兼容层；禁止在非兼容代码中直接引用 WTLib 类型。
 */
public final class AE2WTLibCompat {
    private AE2WTLibCompat() {
    }

    public static boolean isWirelessTerminalItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemWT;
    }

    public static boolean isWirelessTerminal(ItemStack stack) {
        try {
            return WTDefinition.ofOrNull(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static IGrid getConnectedGrid(Player player, ItemStack stack, ItemMenuHostLocator locator) {
        WTMenuHost host = createHost(stack, player, locator, (ignoredPlayer, ignoredMenu) -> {
        });
        if (host == null) {
            return null;
        }

        host.updateConnectedAccessPoint();
        host.updateLinkStatus();
        if (!host.getLinkStatus().connected() || host.getActionableNode() == null) {
            return null;
        }
        return host.getActionableNode().getGrid();
    }

    @Nullable
    public static <T> T locateMenuHost(ItemStack stack, Player player, ItemMenuHostLocator locator,
                                       Class<T> hostInterface, BiConsumer<Player, Object> openSubMenu) {
        WTDefinition definition;
        try {
            definition = WTDefinition.ofOrNull(stack);
        } catch (Throwable ignored) {
            return null;
        }
        if (definition == null) {
            return null;
        }

        // CraftAmountMenu 只要求 ISubMenuHost，直接使用 Curios 桥接宿主，
        // 避免先创建终端专用宿主后因接口不匹配而失败。
        if (hostInterface == ISubMenuHost.class) {
            var subHost = new CuriosWTSubMenuHost(getHostItem(stack, definition), player, locator,
                    (menuPlayer, menu) -> openSubMenu.accept(menuPlayer, menu));
            return hostInterface.cast(subHost);
        }

        WTMenuHost host = definition.wTMenuHostFactory().create(getHostItem(stack, definition), player, locator,
                (menuPlayer, menu) -> openSubMenu.accept(menuPlayer, menu));
        if (hostInterface.isInstance(host)) {
            return hostInterface.cast(host);
        }
        return null;
    }

    /**
     * 通过 WTLib 已注册的终端定义返回主菜单，保留各终端自己的菜单类型。
     */
    public static void reopenTerminal(Player player, ItemMenuHostLocator locator) {
        try {
            WTDefinition definition = WTDefinition.ofOrNull(locator.locateItem(player));
            if (definition != null) {
                definition.containerOpener().tryOpen(player, locator, true);
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static WTMenuHost createHost(ItemStack stack, Player player, ItemMenuHostLocator locator,
                                         BiConsumer<Player, Object> openSubMenu) {
        try {
            WTDefinition definition = WTDefinition.ofOrNull(stack);
            if (definition == null) {
                return null;
            }
            return definition.wTMenuHostFactory().create(getHostItem(stack, definition), player, locator,
                    (menuPlayer, menu) -> openSubMenu.accept(menuPlayer, menu));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemWT getHostItem(ItemStack stack, WTDefinition definition) {
        // 通用终端必须保留自身的动态升级库存，量子桥卡可能位于子终端固定槽位之外。
        return stack.getItem() instanceof ItemWT item ? item : definition.item();
    }
}
