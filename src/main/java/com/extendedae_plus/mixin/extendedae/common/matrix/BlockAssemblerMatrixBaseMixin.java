package com.extendedae_plus.mixin.extendedae.common.matrix;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.init.ModMenuTypes;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixGlass;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 接管原版矩阵玻璃在超级结构中的重算、拆除与菜单入口。 */
@Mixin(value = BlockAssemblerMatrixBase.class, remap = false)
public abstract class BlockAssemblerMatrixBaseMixin {

    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void eap$recalculateGlassSuperMatrix(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart) {
            SuperAssemblerMatrixCalculator.scheduleRecalculate(serverLevel, pos);
        }
    }

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void eap$breakGlassSuperMatrix(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean isMoving, CallbackInfo ci) {
        if (newState.getBlock() != state.getBlock()
                && level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part) {
            part.eap$breakSuperMatrixCluster();
        }
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void eap$openGlassSuperMatrixMenu(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!this.eap$isGlassSuperMatrixPart(level, pos)) {
            return;
        }
        this.eap$openSuperMatrixMenu(level, pos, player);
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private void eap$openGlassSuperMatrixMenuWithItem(TileAssemblerMatrixBase tile, ItemStack stack, Level level, BlockPos pos,
            BlockHitResult hit, Player player, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!this.eap$isGlassSuperMatrixPart(level, pos)) {
            return;
        }
        this.eap$openSuperMatrixMenu(level, pos, player);
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
    }

    private boolean eap$isGlassSuperMatrixPart(Level level, BlockPos pos) {
        if (!((Object) this instanceof BlockAssemblerMatrixGlass)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
            return false;
        }
        var state = level.getBlockState(pos);
        return state.getValue(BlockAssemblerMatrixBase.FORMED)
                || part.eap$getSuperMatrixCluster() != null;
    }

    private void eap$openSuperMatrixMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
            return;
        }
        var cluster = part.eap$getSuperMatrixCluster();
        var core = cluster == null ? null : cluster.getCore();
        if (core != null && core.getMainNode().isActive()) {
            MenuOpener.open(ModMenuTypes.SUPER_ASSEMBLER_MATRIX.get(), player,
                    MenuLocators.forBlockEntity(core));
        }
    }
}
