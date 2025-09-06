package com.extendedae_plus.menu.host;

import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.world.entity.player.Player;

/**
 * 基于 ae2wtlib API 的最小封装：
 * 直接复用 wtlib 的 WTMenuHost 构造，不再自定义回写逻辑，
 * 由 AE2 的 ItemMenuHostLocator 在 Curios 槽位就地更新物品。
 */
public class CuriosWTMenuHost extends WTMenuHost {
    public CuriosWTMenuHost(ItemWT item,
                            Player player,
                            ItemMenuHostLocator locator,
                            java.util.function.BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
