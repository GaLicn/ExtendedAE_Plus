package com.extendedae_plus.menu;

import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 标签无线收发器菜单（暂无线槽，仅用于打开客户端界面）。
 */
public class LabeledWirelessTransceiverMenu extends AbstractContainerMenu {
    private final BlockPos bePos;

    public LabeledWirelessTransceiverMenu(int id, Inventory inv, BlockPos bePos) {
        super(ModMenuTypes.LABELED_WIRELESS_TRANSCEIVER.get(), id);
        this.bePos = bePos;
    }

    public LabeledWirelessTransceiverMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public BlockPos getBlockEntityPos() {
        return bePos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level() != null
                && player.level().getBlockEntity(bePos) != null
                && player.distanceToSqr(bePos.getX() + 0.5, bePos.getY() + 0.5, bePos.getZ() + 0.5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
