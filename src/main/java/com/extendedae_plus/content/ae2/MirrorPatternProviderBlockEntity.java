package com.extendedae_plus.content.ae2;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MirrorPatternProviderBlockEntity extends PatternProviderBlockEntity {
    public MirrorPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MIRROR_PATTERN_PROVIDER_BE.get(), pos, blockState);
    }
}
