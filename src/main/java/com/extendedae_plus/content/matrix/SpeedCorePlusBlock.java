package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.world.item.Item;

public class SpeedCorePlusBlock extends BlockAssemblerMatrixBase<SpeedCorePlusBlockEntity> {

    public SpeedCorePlusBlock(Properties props) {
        super(props);
    }

    @Override
    public Item getPresentItem() {
        return ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get();
    }
}
