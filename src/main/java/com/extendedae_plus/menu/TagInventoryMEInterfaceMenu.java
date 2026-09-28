package com.extendedae_plus.menu;

import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlockEntity;
import com.extendedae_plus.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class TagInventoryMEInterfaceMenu extends AbstractContainerMenu {

    private final BlockPos blockEntityPos;
    private final String whiteListExpression;
    private final String blackListExpression;

    public TagInventoryMEInterfaceMenu(int id, Inventory inventory, BlockPos blockEntityPos,
            String whiteListExpression, String blackListExpression) {
        super(ModMenuTypes.TAG_INVENTORY_ME_INTERFACE.get(), id);
        this.blockEntityPos = blockEntityPos;
        this.whiteListExpression = whiteListExpression;
        this.blackListExpression = blackListExpression;
    }

    public TagInventoryMEInterfaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(
                id,
                inventory,
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUtf());
    }

    public BlockPos getBlockEntityPos() {
        return this.blockEntityPos;
    }

    public String getWhiteListExpression() {
        return this.whiteListExpression;
    }

    public String getBlackListExpression() {
        return this.blackListExpression;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.blockEntityPos) instanceof TagInventoryMEInterfaceBlockEntity
                && player.distanceToSqr(
                        this.blockEntityPos.getX() + 0.5,
                        this.blockEntityPos.getY() + 0.5,
                        this.blockEntityPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
