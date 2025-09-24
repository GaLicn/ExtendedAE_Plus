package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.world.entity.player.Player;

/**
 * 适配 ae2wtlib 的 WTMenuHost 到 AE2 期望的 ISubMenuHost。
 * 仅作为类型桥接，具体行为由 WTMenuHost 超类实现。
 */
public class CuriosWTSubMenuHost extends WTMenuHost implements ISubMenuHost {
    public CuriosWTSubMenuHost(ItemWT item,
                               Player player,
                               ItemMenuHostLocator locator,
                               java.util.function.BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
