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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 接管 EAE 矩阵方块在超级结构中的重算、拆除与玻璃菜单入口。 */
@Mixin(value = BlockAssemblerMatrixBase.class, remap = false)
public abstract class BlockAssemblerMatrixBaseMixin {

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void eap$handleSuperMatrixNeighborChange(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos fromPos, BlockState fromState, RandomSource random,
            CallbackInfoReturnable<BlockState> cir) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
            return;
        }

        SuperAssemblerMatrixCalculator.scheduleAfterNeighborChange(serverLevel, pos, fromPos);
        if (part.eap$getSuperMatrixCluster() != null) {
            // 超级集群已经接管该部件，跳过 EAE 原版同步多方块扫描。
            cir.setReturnValue(state);
        }
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void eap$openGlassSuperMatrixMenu(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!this.eap$isGlassSuperMatrixPart(level, pos)) {
            return;
        }
        this.eap$openSuperMatrixMenu(level, pos, player);
        cir.setReturnValue(level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private void eap$openGlassSuperMatrixMenuWithItem(TileAssemblerMatrixBase tile, ItemStack stack, Level level, BlockPos pos,
            BlockHitResult hit, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (!this.eap$isGlassSuperMatrixPart(level, pos)) {
            return;
        }
        this.eap$openSuperMatrixMenu(level, pos, player);
        cir.setReturnValue(level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    private boolean eap$isGlassSuperMatrixPart(Level level, BlockPos pos) {
        if (!((Object) this instanceof BlockAssemblerMatrixGlass)
                || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
            return false;
        }
        // 只有实际加入超级集群的玻璃才接管菜单，原版成型状态不能作为超装判据。
        return part.eap$getSuperMatrixCluster() != null;
    }

    private void eap$openSuperMatrixMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SuperAssemblerMatrixPart part)) {
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
