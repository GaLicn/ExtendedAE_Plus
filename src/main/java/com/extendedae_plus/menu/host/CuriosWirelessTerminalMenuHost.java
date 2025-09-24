package com.extendedae_plus.menu.host;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 针对 Curios 槽位的无线终端菜单宿主。
 * 通过传入 CuriosItemLocator 让 AE2 的 ItemMenuHost 在 Curios 槽位上就地修改 ItemStack。
 */
public class CuriosWirelessTerminalMenuHost extends WirelessTerminalMenuHost<WirelessTerminalItem> {
    private final ICurioStacksHandler curiosHandler;
    private final int curiosIndex;
    private final String curiosSlotId;

    public CuriosWirelessTerminalMenuHost(Player player,
                                          String curiosSlotId,
                                          ItemStack itemStack,
                                          ICurioStacksHandler curiosHandler,
                                          int curiosIndex,
                                          java.util.function.BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super((WirelessTerminalItem) itemStack.getItem(), player,
                (ItemMenuHostLocator) new CuriosItemLocator(curiosSlotId, curiosIndex),
                returnToMainMenu);
        this.curiosHandler = curiosHandler;
        this.curiosIndex = curiosIndex;
        this.curiosSlotId = curiosSlotId;
    }
}
