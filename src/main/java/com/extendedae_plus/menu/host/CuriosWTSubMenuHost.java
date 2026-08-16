package com.extendedae_plus.menu.host;

import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.BiConsumer;

/**
 * 为 Curios 中的 WTLib 终端提供 AE2 下单子菜单所需的宿主接口。
 */
public final class CuriosWTSubMenuHost extends CuriosWTMenuHost implements ISubMenuHost {
    public CuriosWTSubMenuHost(Player player,
                               @Nullable Integer inventorySlot,
                               ItemStack stack,
                               ICurioStacksHandler curiosHandler,
                               int curiosIndex,
                               BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, curiosHandler, curiosIndex, returnToMainMenu);
    }
}
