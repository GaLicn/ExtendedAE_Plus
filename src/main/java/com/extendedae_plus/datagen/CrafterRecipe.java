package com.extendedae_plus.datagen;

import appeng.core.definitions.AEBlocks;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.EAESingletons;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * ExtendedAE Plus 配方数据生成器
 * 用于 NeoForge 1.21.1
 */
public class CrafterRecipe extends RecipeProvider {

    public CrafterRecipe(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput output) {

        //超级装配矩阵速度核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EAESingletons.ASSEMBLER_MATRIX_SPEED)
                .define('L', Items.NETHER_STAR)
                .define('B', EAESingletons.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(output);

        //超级装配矩阵合成核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EAESingletons.ASSEMBLER_MATRIX_CRAFTER)
                .define('L', Items.NETHER_STAR)
                .define('B', EAESingletons.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(output);

        //超级装配矩阵样板核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_PATTERN_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EAESingletons.ASSEMBLER_MATRIX_PATTERN)
                .define('L', Items.NETHER_STAR)
                .define('B', EAESingletons.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(output);

    }
}
