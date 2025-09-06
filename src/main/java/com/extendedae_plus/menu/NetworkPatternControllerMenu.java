package com.extendedae_plus.menu;

import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.FriendlyByteBuf;
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

    public NetworkPatternControllerMenu(int id, Inventory inv, @Nullable FriendlyByteBuf buf) {
        this(id, inv, buf != null ? buf.readBlockPos() : BlockPos.ZERO);
    }

    public BlockPos getBlockEntityPos() { return bePos; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 无物品槽的容器，直接返回空堆以禁用快速转移
        return ItemStack.EMPTY;
    }
}
