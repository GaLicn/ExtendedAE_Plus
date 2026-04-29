package com.extendedae_plus.datagen;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.EAESingletons;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.pedroksl.advanced_ae.common.definitions.AAEFluids;
import net.pedroksl.advanced_ae.common.definitions.AAEItems;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipeBuilder;
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

        //标签无线收发器
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LABELED_WIRELESS_TRANSCEIVER.get())
                .pattern("CAC")
                .pattern("ABA")
                .pattern("CAC")
                .unlockedBy("has_wireless_transceiver", has(ModItems.WIRELESS_TRANSCEIVER.get()))
                .define('A', Items.PAPER)
                .define('B', ModItems.WIRELESS_TRANSCEIVER.get())
                .define('C',Items.EMERALD)
                .save(output);

        //镜像样板供应器
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MIRROR_PATTERN_PROVIDER.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .unlockedBy("has_mirror_pattern_provider",has(ModItems.MIRROR_PATTERN_PROVIDER.get()))
                .define('A',Items.GLASS)
                .define('B',AEBlocks.PATTERN_PROVIDER)
                .save(output);

        //镜像样板绑定工具
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MIRROR_PATTERN_BINDING_TOOL.get())
                .pattern("  A")
                .pattern("BCD")
                .pattern("BBB")
                .unlockedBy("has_mirror_pattern_binding_tool",has(ModItems.MIRROR_PATTERN_BINDING_TOOL.get()))
                .define('A', AEItems.WIRELESS_RECEIVER)
                .define('B', Items.IRON_INGOT)
                .define('C', Items.REDSTONE)
                .define('D', AEItems.CALCULATION_PROCESSOR)
                .save(output);

        // 湮灭奇点 - 爆炸转换
        TransformRecipeBuilder.transform(output,
                                         ExtendedAEPlus.id("transform/oblivion_singularity"),
                                         ModItems.OBLIVION_SINGULARITY.get(), 1,
                                         TransformCircumstance.EXPLOSION,
                                         AEItems.SINGULARITY, Items.NETHER_STAR, Items.NETHERITE_BLOCK
        );

        // 湮灭奇点 - AAE反应仓配方
        ReactionChamberRecipeBuilder.react(ModItems.OBLIVION_SINGULARITY.get(), 1, 100000)
                                    .input(AEItems.SINGULARITY, 2)
                                    .input(Items.NETHER_STAR, 1)
                                    .input(AAEItems.QUANTUM_ALLOY_PLATE, 4)
                                    .fluid(AAEFluids.QUANTUM_INFUSION.source(), 2000)
                                    .save(output, "oblivion_singularity");

        // 基础核心配方
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BASIC_CORE.get())
                .pattern("ABA")
                .pattern("CDE")
                .pattern("AFA")
                .define('A', Items.NETHERITE_BLOCK)
                .define('B', Items.NETHER_STAR)
                .define('C', AEItems.LOGIC_PROCESSOR)
                .define('D', AEItems.FLUIX_PEARL)
                .define('E', AEItems.ENGINEERING_PROCESSOR)
                .define('F', AEItems.CALCULATION_PROCESSOR)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(output);

        // 吞噬盘
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_BIGINTEGER_CELL_ITEM.get())
                           .pattern("GOG")
                           .pattern("NIN")
                           .pattern("BBB")
                           .define('G', AEBlocks.QUARTZ_VIBRANT_GLASS)
                           .define('O', ModItems.OBLIVION_SINGULARITY.get())
                           .define('N', Items.NETHER_STAR)
                           .define('I', ModItems.INFINITY_CORE.get())
                           .define('B', Items.NETHERITE_BLOCK)
                           .unlockedBy("has_oblivion_singularity", has(ModItems.OBLIVION_SINGULARITY.get()))
                           .save(output);
    }
}
