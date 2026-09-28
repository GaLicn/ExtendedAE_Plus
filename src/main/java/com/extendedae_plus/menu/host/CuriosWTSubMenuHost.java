package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

/**
 * 适配 ae2wtlib 的 WTMenuHost 到 AE2 期望的 ISubMenuHost。
 * 仅作为类型桥接，具体行为由 WTMenuHost 超类实现。
 */
public class CuriosWTSubMenuHost extends CuriosWTMenuHost implements ISubMenuHost {
    public CuriosWTSubMenuHost(ItemWT item,
                               Player player,
                               ItemMenuHostLocator locator,
                               BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
