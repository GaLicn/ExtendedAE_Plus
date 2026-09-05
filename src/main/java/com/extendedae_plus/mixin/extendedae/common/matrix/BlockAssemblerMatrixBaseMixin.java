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

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void eap$openGlassSuperMatrixMenu(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object) this instanceof BlockAssemblerMatrixGlass)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)
                || part.eap$getSuperMatrixCluster() == null) {
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

    // GTOCore 对扩展样板供应器使用 m_6861_ 覆盖邻居回调，两个名称都允许命中。
    @Inject(method = {"neighborChanged", "m_6861_"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void eap$handleSuperMatrixNeighborChange(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
            return;
        }

        SuperAssemblerMatrixCalculator.scheduleAfterNeighborChange(serverLevel, pos, fromPos);
        if (part.eap$getSuperMatrixCluster() != null) {
            // 超级集群已经接管该部件，跳过 EAE 原版同步多方块扫描。
            ci.cancel();
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
