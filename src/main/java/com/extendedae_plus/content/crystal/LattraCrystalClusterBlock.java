package com.extendedae_plus.content.crystal;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class LattraCrystalClusterBlock extends AmethystClusterBlock {
    public LattraCrystalClusterBlock(int height, int width, BlockBehaviour.Properties properties) {
        super(height, width, properties);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // 支撑方块被移除时不生成掉落物，避免自动生长链产生悬空水晶。
        if (builder.getOptionalParameter(LootContextParams.THIS_ENTITY) == null) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }
}
