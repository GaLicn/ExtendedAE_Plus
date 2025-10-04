package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.world.entity.player.Player;

/**
 * 使用 AE2 原生 WirelessTerminalItem 作为泛型参数，避免在运行时因为 wtlib API 类加载差异而无法强转为 ItemWT。
 * 实现 ISubMenuHost 以满足 CraftAmountMenu.open 所需宿主接口。
 */
public class CuriosWTSubMenuHost2 extends WirelessTerminalMenuHost<WirelessTerminalItem> implements ISubMenuHost {
    public CuriosWTSubMenuHost2(WirelessTerminalItem item,
                                Player player,
                                ItemMenuHostLocator locator,
                                java.util.function.BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
