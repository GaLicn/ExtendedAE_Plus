package com.extendedae_plus.menu;

import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class NetworkPatternControllerMenu extends AbstractContainerMenu {
    private final BlockPos bePos;

    public NetworkPatternControllerMenu(int id, Inventory inv, BlockPos bePos) {
        super(ModMenuTypes.NETWORK_PATTERN_CONTROLLER.get(), id);
        this.bePos = bePos;
    }

    public NetworkPatternControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public BlockPos getBlockEntityPos() { return bePos; }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.bePos) instanceof NetworkPatternControllerBlockEntity
                && player.distanceToSqr(
                        this.bePos.getX() + 0.5,
                        this.bePos.getY() + 0.5,
                        this.bePos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 无物品槽的容器，直接返回空堆以禁用快速转移
        return ItemStack.EMPTY;
    }
}
