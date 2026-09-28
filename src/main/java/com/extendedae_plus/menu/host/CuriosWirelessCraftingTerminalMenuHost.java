package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

/**
 * 保留 AE2 无线合成终端的原生宿主类型，同时补充 CraftAmountMenu 所需的 ISubMenuHost。
 */
public final class CuriosWirelessCraftingTerminalMenuHost
        extends WirelessCraftingTerminalMenuHost<WirelessCraftingTerminalItem> implements ISubMenuHost {
    public CuriosWirelessCraftingTerminalMenuHost(WirelessCraftingTerminalItem item,
                                                   Player player,
                                                   ItemMenuHostLocator locator,
                                                   BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
