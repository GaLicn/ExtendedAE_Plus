package com.extendedae_plus.content.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class TagInventoryMEInterfaceBlock extends Block implements EntityBlock {

    public TagInventoryMEInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TagInventoryMEInterfaceBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TagInventoryMEInterfaceBlockEntity tagInterface) {
            // 将当前筛选条件随菜单打开包同步至客户端。
            NetworkHooks.openScreen(serverPlayer, (MenuProvider) tagInterface, buf -> {
                buf.writeBlockPos(pos);
                buf.writeUtf(tagInterface.getWhiteListExpression(), TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
                buf.writeUtf(tagInterface.getBlackListExpression(), TagInventoryMEInterfaceBlockEntity.MAX_FILTER_LENGTH);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}
