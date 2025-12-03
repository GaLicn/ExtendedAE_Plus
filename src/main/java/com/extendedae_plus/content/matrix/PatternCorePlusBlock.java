package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.world.item.Item;

public class PatternCorePlusBlock extends BlockAssemblerMatrixBase<PatternCorePlusBlockEntity> {

    public PatternCorePlusBlock(Properties props) {
        super(props);
    }

    @Override
    public Item getPresentItem() {
        return ModItems.ASSEMBLER_MATRIX_PATTERN_PLUS.get();
    }
}
