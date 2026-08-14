package com.extendedae_plus.content.matrix;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.mixin.minecraft.accessor.BlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 混合核心复用样板核心的库存与网络节点，并由超级矩阵额外提供合成、加速效果。
 */
public class HybridCoreBlockEntity extends PatternCorePlusBlockEntity {

    public HybridCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(pos, blockState);

        // 父类构造时会写入样板核心类型，这里替换为混合核心自己的实体类型。
        ((BlockEntityAccessor) (Object) this)
                .extendedae_plus$setType(ModBlockEntities.ASSEMBLER_MATRIX_HYBRID_PLUS_BE.get());
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var icon = AEItemKey.of(ModItems.ASSEMBLER_MATRIX_HYBRID_PLUS.get());
        var name = this.hasCustomName() ? this.getCustomName() : icon.getDisplayName();
        return new PatternContainerGroup(
                icon,
                name,
                List.of(Component.translatable("gui.extendedae_plus.assembler_matrix.hybrid"))
        );
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.ASSEMBLER_MATRIX_HYBRID_PLUS_BE.get();
    }
}
