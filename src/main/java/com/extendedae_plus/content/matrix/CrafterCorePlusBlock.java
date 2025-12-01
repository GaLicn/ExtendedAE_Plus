package com.extendedae_plus.content.matrix;

import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.world.item.Item;

public class CrafterCorePlusBlock extends BlockAssemblerMatrixBase<CrafterCorePlusBlockEntity> {

    public CrafterCorePlusBlock() {
        super();
    }

    @Override
    public Item getPresentItem() {
        return ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get();
    }
}
