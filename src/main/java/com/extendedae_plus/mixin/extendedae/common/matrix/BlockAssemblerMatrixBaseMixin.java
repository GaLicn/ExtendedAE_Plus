package com.extendedae_plus.mixin.extendedae.common.matrix;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.init.ModMenuTypes;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixGlass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让原版矩阵方块的邻居变化也驱动超级矩阵重算。 */
@Mixin(value = BlockAssemblerMatrixBase.class, remap = false)
public abstract class BlockAssemblerMatrixBaseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void eap$openGlassSuperMatrixMenu(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object) this instanceof BlockAssemblerMatrixGlass)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)
                || (!state.getValue(BlockAssemblerMatrixBase.FORMED)
                && part.eap$getSuperMatrixCluster() == null)) {
            return;
        }

        if (!level.isClientSide) {
            var cluster = part.eap$getSuperMatrixCluster();
            var core = cluster == null ? null : cluster.getCore();
            if (core != null && core.getMainNode().isActive()) {
                MenuOpener.open(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), player,
                        MenuLocators.forBlockEntity(core));
            }
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void eap$recalculateSuperMatrix(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart) {
            SuperAssemblerMatrixCalculator.scheduleRecalculate(serverLevel, pos);
        }
    }

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void eap$breakSuperMatrix(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean isMoving, CallbackInfo ci) {
        if (newState.getBlock() != state.getBlock()
                && level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part) {
            part.eap$breakSuperMatrixCluster();
        }
    }
}
