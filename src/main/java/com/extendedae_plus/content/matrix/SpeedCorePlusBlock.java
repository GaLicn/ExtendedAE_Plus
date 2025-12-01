package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class SpeedCorePlusBlock extends BlockAssemblerMatrixBase<SpeedCorePlusBlockEntity> {

    public SpeedCorePlusBlock() {
        super();
    }

    public SpeedCorePlusBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public Item getPresentItem() {
        return ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get();
    }
}
