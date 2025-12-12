package com.extendedae_plus.datagen;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.BasicCoreItem;
import com.extendedae_plus.items.materials.EntitySpeedCardItem;
import com.extendedae_plus.util.ModCheckUtils;
import com.glodblock.github.appflux.common.AFItemAndBlock;
import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import gripe._90.megacells.definition.MEGAItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.pedroksl.advanced_ae.common.definitions.AAEBlocks;
import net.pedroksl.advanced_ae.common.definitions.AAEFluids;
import net.pedroksl.advanced_ae.common.definitions.AAEItems;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class CraftingRecipes extends RecipeProvider {
    public CraftingRecipes(PackOutput output) {
        super(output);
    }

    @Override
    public void buildRecipes(@NotNull Consumer<FinishedRecipe> consumer) {
        addCraftingAccelerators(consumer);
        addCardRecipes(consumer);
        addCoreRecipes(consumer);
        addTransformRecipes(consumer);
        addReactionChamberRecipes(consumer);

        // 吞噬盘
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_BIGINTEGER_CELL.get())
                .pattern("GOG")
                .pattern("NIN")
                .pattern("BBB")
                .define('G', AEBlocks.QUARTZ_VIBRANT_GLASS)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('I', ModItems.INFINITY_CORE.get())
                .define('B', Items.NETHERITE_BLOCK)
                .unlockedBy("has_oblivion_singularity", has(ModItems.OBLIVION_SINGULARITY.get()))
                .save(consumer);

        // 状态控制器
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NETWORK_PATTERN_CONTROLLER.get())
                .requires(ConventionTags.ILLUMINATED_PANEL)
                .requires(ConventionTags.PATTERN_PROVIDER)
                .requires(AEItems.NETWORK_TOOL)
                .unlockedBy("has_network_tool", has(AEItems.NETWORK_TOOL))
                .unlockedBy("has_pattern_provider", has(ConventionTags.PATTERN_PROVIDER))
                .save(consumer);

        // 无线收发器
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WIRELESS_TRANSCEIVER.get())
                .pattern("RRR")
                .pattern("RLR")
                .pattern("RRR")
                .define('R', AEBlocks.QUANTUM_RING)
                .define('L', AEBlocks.QUANTUM_LINK)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(consumer);

        // 实体加速器
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ENTITY_TICKER_PART_ITEM.get())
                .pattern("SZS")
                .pattern("QXQ")
                .pattern("SIS")
                .defineNbt('S', EntitySpeedCardItem.withMultiplier(2))
                .define('Z', AEBlocks.DENSE_ENERGY_CELL)
                .define('Q', AEItems.SINGULARITY)
                .define('X', Items.NETHER_STAR)
                .define('I', EPPItemAndBlock.EX_IO_PORT)
                .unlockedBy("has_entity_speed_card_x2", has(EntitySpeedCardItem.withMultiplier(2).getItem()))
                .unlockedBy("has_singularity", has(AEItems.SINGULARITY))
                .save(consumer);

        // 上传核心
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_UPLOAD_CORE.get())
                .requires(EPPItemAndBlock.ASSEMBLER_MATRIX_WALL)
                .requires(Items.LEVER)
                .unlockedBy("has_assembler_matrix_wall", has(EPPItemAndBlock.ASSEMBLER_MATRIX_WALL))
                .save(consumer);

        //超级装配矩阵速度核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_SPEED_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EPPItemAndBlock.ASSEMBLER_MATRIX_SPEED)
                .define('L', Items.NETHER_STAR)
                .define('B', EPPItemAndBlock.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(consumer);

        //超级装配矩阵合成核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_CRAFTER_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EPPItemAndBlock.ASSEMBLER_MATRIX_CRAFTER)
                .define('L', Items.NETHER_STAR)
                .define('B', EPPItemAndBlock.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(consumer);

        //超级装配矩阵样板核心
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ASSEMBLER_MATRIX_PATTERN_PLUS.get())
                .pattern("BRB")
                .pattern("RLR")
                .pattern("BRB")
                .define('R', EPPItemAndBlock.ASSEMBLER_MATRIX_PATTERN)
                .define('L', Items.NETHER_STAR)
                .define('B', EPPItemAndBlock.ASSEMBLER_MATRIX_WALL)
                .unlockedBy("has_quantum_ring", has(AEBlocks.QUANTUM_RING))
                .save(consumer);
    }

    private void addCraftingAccelerators(Consumer<FinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CRAFTING_ACCELERATOR_4x.get())
                .requires(AEBlocks.CRAFTING_ACCELERATOR)
                .requires(AEItems.CELL_COMPONENT_4K)
                .unlockedBy("has_accelerator", has(AEBlocks.CRAFTING_ACCELERATOR))
                .save(consumer, ExtendedAEPlus.id("network/crafting/" + ModItems.CRAFTING_ACCELERATOR_4x.get().toString().toLowerCase()));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CRAFTING_ACCELERATOR_16x.get())
                .requires(AEBlocks.CRAFTING_ACCELERATOR)
                .requires(AEItems.CELL_COMPONENT_16K)
                .unlockedBy("has_accelerator", has(AEBlocks.CRAFTING_ACCELERATOR))
                .save(consumer, ExtendedAEPlus.id("network/crafting/" + ModItems.CRAFTING_ACCELERATOR_16x.get().toString().toLowerCase()));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CRAFTING_ACCELERATOR_64x.get())
                .requires(AEBlocks.CRAFTING_ACCELERATOR)
                .requires(AEItems.CELL_COMPONENT_64K)
                .unlockedBy("has_accelerator", has(AEBlocks.CRAFTING_ACCELERATOR))
                .save(consumer, ExtendedAEPlus.id("network/crafting/" + ModItems.CRAFTING_ACCELERATOR_64x.get().toString().toLowerCase()));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CRAFTING_ACCELERATOR_256x.get())
                .requires(AEBlocks.CRAFTING_ACCELERATOR)
                .requires(AEItems.CELL_COMPONENT_256K)
                .unlockedBy("has_accelerator", has(AEBlocks.CRAFTING_ACCELERATOR))
                .save(consumer, ExtendedAEPlus.id("network/crafting/" + ModItems.CRAFTING_ACCELERATOR_256x.get().toString().toLowerCase()));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CRAFTING_ACCELERATOR_1024x.get())
                .pattern("   ")
                .pattern("ACA")
                .pattern("   ")
                .define('A', AEItems.CELL_COMPONENT_256K)
                .define('C', ModItems.CRAFTING_ACCELERATOR_256x.get())
                .unlockedBy("has_256x", has(ModItems.CRAFTING_ACCELERATOR_256x.get()))
                .save(consumer, ExtendedAEPlus.id("network/crafting/" + ModItems.CRAFTING_ACCELERATOR_1024x.get().toString().toLowerCase()));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.LABELED_WIRELESS_TRANSCEIVER.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', Items.PAPER)
                .define('B', ModItems.WIRELESS_TRANSCEIVER.get())
                .unlockedBy("has_wireless_transceiver", has(ModItems.WIRELESS_TRANSCEIVER.get()))
                .save( consumer)
        ;
    }

    private void addCardRecipes(Consumer<FinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CHANNEL_CARD.get())
                .requires(AEItems.ADVANCED_CARD)
                .requires(ModItems.WIRELESS_TRANSCEIVER.get())
                .unlockedBy("has_advanced_card", has(AEItems.ADVANCED_CARD))
                .save(consumer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.VIRTUAL_CRAFTING_CARD.get())
                .requires(AEItems.ADVANCED_CARD)
                .requires(Items.CRAFTING_TABLE)
                .requires(Items.LEVER)
                .unlockedBy("has_virtual_crafting_card_ingredients", has(AEItems.ADVANCED_CARD))
                .save(consumer);

        // 2x Entity Speed Card
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, EntitySpeedCardItem.withMultiplier(2))
                .pattern("SBS")
                .pattern("QXQ")
                .pattern("SBS")
                .define('S', AEItems.SPEED_CARD)
                .define('B', ModItems.CRAFTING_ACCELERATOR_64x.get())
                .define('Q', AEItems.SPATIAL_2_CELL_COMPONENT)
                .define('X', AEItems.CELL_COMPONENT_256K)
                .unlockedBy("has_speed_card", has(AEItems.SPEED_CARD))
                .unlockedBy("has_64x_accelerator", has(ModItems.CRAFTING_ACCELERATOR_64x.get()))
                .save(consumer, ExtendedAEPlus.id("entity_speed_card_2x"));

        // 4x Entity Speed Card
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, EntitySpeedCardItem.withMultiplier(4))
                .pattern("SBS")
                .pattern("QXQ")
                .pattern("SBS")
                .defineNbt('S', EntitySpeedCardItem.withMultiplier(2))
                .define('B', ModItems.CRAFTING_ACCELERATOR_256x.get())
                .define('Q', AEItems.SPATIAL_16_CELL_COMPONENT)
                .define('X', AEBlocks.DENSE_ENERGY_CELL)
                .unlockedBy("has_entity_speed_card_2x", has(EntitySpeedCardItem.withMultiplier(2).getItem()))
                .save(consumer, ExtendedAEPlus.id("entity_speed_card_4x"));

        // 8x Entity Speed Card
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, EntitySpeedCardItem.withMultiplier(8))
                .pattern("SBS")
                .pattern("QXQ")
                .pattern("SBS")
                .defineNbt('S', EntitySpeedCardItem.withMultiplier(4))
                .define('B', ModItems.CRAFTING_ACCELERATOR_1024x.get())
                .define('Q', AEItems.SPATIAL_128_CELL_COMPONENT)
                .define('X', Items.NETHER_STAR)
                .unlockedBy("has_entity_speed_card_4x", has(EntitySpeedCardItem.withMultiplier(4).getItem()))
                .save(consumer, ExtendedAEPlus.id("entity_speed_card_8x"));

        // 16x Entity Speed Card
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        EntitySpeedCardItem.withMultiplier(16))
                .pattern("SAS")
                .pattern("QXQ")
                .pattern("SBS")
                .defineNbt('S', EntitySpeedCardItem.withMultiplier(8))
                .define('A', Items.NETHER_STAR)
                .define('Q', AEItems.SPATIAL_128_CELL_COMPONENT)
                .define('X', Items.DRAGON_EGG)
                .define('B', Blocks.BEACON)
                .unlockedBy("has_entity_speed_card_8x", has(EntitySpeedCardItem.withMultiplier(8).getItem()))
                .save(consumer, ExtendedAEPlus.id("entity_speed_card_16x"));
    }

    private void addCoreRecipes(Consumer<FinishedRecipe> consumer) {
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
                .save(consumer, ExtendedAEPlus.id("core/basic_core"));

        ItemStack base = BasicCoreItem.of(null, 0); // 未定型核心
        // ====================== STORAGE LINE ======================
        // storage_1
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.storageStage(1))
                .pattern("MCM")
                .pattern("LBP")
                .pattern("MEM")
                .define('M', ConventionTags.CERTUS_QUARTZ_DUST)
                .define('C', AEItems.CELL_COMPONENT_16K)
                .define('L', AEItems.LOGIC_PROCESSOR)
                .defineNbt('B', base)
                .define('P', AEItems.CALCULATION_PROCESSOR)
                .define('E', AEItems.ENGINEERING_PROCESSOR)
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .save(consumer, ExtendedAEPlus.id("core/storage_core_1"));

        // storage_2
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.storageStage(2))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MOM")
                .define('M', ConventionTags.ALL_CERTUS_QUARTZ)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AEItems.CELL_COMPONENT_64K)
                .defineNbt('B', BasicCoreItem.storageStage(1))
                .unlockedBy("has_storage_stage_1", has(BasicCoreItem.storageStage(1).getItem()))
                .save(consumer, ExtendedAEPlus.id("core/storage_core_2"));

        // storage_3
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.storageStage(3))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MCM")
                .define('M', AEBlocks.QUARTZ_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AEItems.CELL_COMPONENT_256K)
                .defineNbt('B', BasicCoreItem.storageStage(2))
                .unlockedBy("has_storage_stage_2", has(BasicCoreItem.storageStage(2).getItem()))
                .save(consumer, ExtendedAEPlus.id("core/storage_core_3"));

        // ====================== SPATIAL LINE ======================
        // spatial_1
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.spatialStage(1))
                .pattern("MCM")
                .pattern("LBP")
                .pattern("MEM")
                .define('M', ConventionTags.FLUIX_DUST)
                .define('C', AEItems.SPATIAL_2_CELL_COMPONENT)
                .define('L', AEItems.LOGIC_PROCESSOR)
                .defineNbt('B', base)
                .define('P', AEItems.CALCULATION_PROCESSOR)
                .define('E', AEItems.ENGINEERING_PROCESSOR)
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .save(consumer, ExtendedAEPlus.id("core/spatial_core_1"));

        // spatial_2
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.spatialStage(2))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MOM")
                .define('M', ConventionTags.FLUIX_CRYSTAL)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AEItems.SPATIAL_16_CELL_COMPONENT)
                .defineNbt('B', BasicCoreItem.spatialStage(1))
                .unlockedBy("has_spatial_stage_1", has(BasicCoreItem.spatialStage(1).getItem()))
                .save(consumer, ExtendedAEPlus.id("core/spatial_core_2"));

        // spatial_3
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.spatialStage(3))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MCM")
                .define('M', AEBlocks.FLUIX_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AEItems.SPATIAL_128_CELL_COMPONENT)
                .defineNbt('B', BasicCoreItem.spatialStage(2))
                .unlockedBy("has_spatial_stage_2", has(BasicCoreItem.spatialStage(2).getItem()))
                .save(consumer, ExtendedAEPlus.id("core/spatial_core_3"));

        // ====================== ENERGY LINE (依赖 AppFlux) ======================
        // energy_storage_1
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.energyStage(1))
                .pattern("MCM")
                .pattern("EBE")
                .pattern("MEM")
                .define('M', AFItemAndBlock.REDSTONE_CRYSTAL)
                .define('C', AFItemAndBlock.CORE_16k)
                .define('E', AFItemAndBlock.ENERGY_PROCESSOR)
                .defineNbt('B', base)
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .save(consumer, ExtendedAEPlus.id("core/energy_storage_core_1"));

        // energy_storage_2
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.energyStage(2))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MOM")
                .define('M', AFItemAndBlock.CHARGED_REDSTONE)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AFItemAndBlock.CORE_64k)
                .defineNbt('B', BasicCoreItem.energyStage(1))
                .unlockedBy("has_energy_stage_1", has(BasicCoreItem.energyStage(1).getItem()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .save(consumer, ExtendedAEPlus.id("core/energy_storage_core_2"));

        // energy_storage_3
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.energyStage(3))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MCM")
                .define('M', AFItemAndBlock.SKY_HARDEN_INSULATING_RESIN)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AFItemAndBlock.CORE_256k)
                .defineNbt('B', BasicCoreItem.energyStage(2))
                .unlockedBy("has_energy_stage_2", has(BasicCoreItem.energyStage(2).getItem()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .save(consumer, ExtendedAEPlus.id("core/energy_storage_core_3"));

        // ====================== QUANTUM LINE (依赖 Advanced AE) ======================
        // quantum_storage_1
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.quantumStage(1))
                .pattern("MCM")
                .pattern("EBE")
                .pattern("MEM")
                .define('M', AAEItems.QUANTUM_ALLOY)
                .define('C', AAEItems.QUANTUM_STORAGE_COMPONENT)
                .define('E', AAEItems.QUANTUM_PROCESSOR)
                .defineNbt('B', base)
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/quantum_storage_core_1"));

        // quantum_storage_2
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.quantumStage(2))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MOM")
                .define('M', AAEBlocks.QUANTUM_ALLOY_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AAEItems.QUANTUM_STORAGE_COMPONENT)
                .defineNbt('B', BasicCoreItem.quantumStage(1))
                .unlockedBy("has_quantum_stage_1", has(BasicCoreItem.quantumStage(1).getItem()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/quantum_storage_core_2"));

        // quantum_storage_3
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, BasicCoreItem.quantumStage(3))
                .pattern("MOM")
                .pattern("CBC")
                .pattern("MCM")
                .define('M', AAEItems.QUANTUM_ALLOY_PLATE)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', AAEBlocks.QUANTUM_STORAGE_128M)
                .defineNbt('B', BasicCoreItem.quantumStage(2))
                .unlockedBy("has_quantum_stage_2", has(BasicCoreItem.quantumStage(2).getItem()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/quantum_storage_core_3"));

        // storage_core
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STORAGE_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', AEItems.CELL_COMPONENT_256K)
                .defineNbt('B', BasicCoreItem.storageStage(3))
                .unlockedBy("has_storage_stage_3", has(BasicCoreItem.storageStage(3).getItem()))
                .notRequiresMod(ModCheckUtils.MODID_MEGA)
                .save(consumer, ExtendedAEPlus.id("core/" + ModItems.STORAGE_CORE.get().toString().toLowerCase()));

        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STORAGE_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', MEGAItems.CELL_COMPONENT_256M)
                .defineNbt('B', BasicCoreItem.storageStage(3))
                .unlockedBy("has_storage_stage_3", has(BasicCoreItem.storageStage(3).getItem()))
                .requiresMod(ModCheckUtils.MODID_MEGA)
                .save(consumer, ExtendedAEPlus.id("core/compat/" + ModItems.STORAGE_CORE.get().toString().toLowerCase()));

        // spatial_core
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPATIAL_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', AEItems.SPATIAL_128_CELL_COMPONENT)
                .defineNbt('B', BasicCoreItem.spatialStage(3))
                .unlockedBy("has_spatial_stage_3", has(BasicCoreItem.spatialStage(3).getItem()))
                .save(consumer, ExtendedAEPlus.id("core/" + ModItems.SPATIAL_CORE.get().toString().toLowerCase()));

        // energy_storage_core
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ENERGY_STORAGE_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', AFItemAndBlock.CORE_256k)
                .defineNbt('B', BasicCoreItem.energyStage(3))
                .unlockedBy("has_energy_stage_3", has(BasicCoreItem.energyStage(3).getItem()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .notRequiresMod(ModCheckUtils.MODID_MEGA)
                .save(consumer, ExtendedAEPlus.id("core/" + ModItems.ENERGY_STORAGE_CORE.get().toString().toLowerCase()));

        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ENERGY_STORAGE_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', AFItemAndBlock.CORE_256M)
                .defineNbt('B', BasicCoreItem.energyStage(3))
                .unlockedBy("has_energy_stage_3", has(BasicCoreItem.energyStage(3).getItem()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .requiresMod(ModCheckUtils.MODID_MEGA)
                .save(consumer, ExtendedAEPlus.id("core/compat/" + ModItems.ENERGY_STORAGE_CORE.get().toString().toLowerCase()));

        // quantum_storage_core
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.QUANTUM_STORAGE_CORE.get())
                .pattern("MOM")
                .pattern("NBN")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('N', Items.NETHER_STAR)
                .define('C', AAEBlocks.QUANTUM_STORAGE_256M)
                .defineNbt('B', BasicCoreItem.quantumStage(3))
                .unlockedBy("has_quantum_stage_3", has(BasicCoreItem.quantumStage(3).getItem()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/" + ModItems.QUANTUM_STORAGE_CORE.get().toString().toLowerCase()));

        // infinity_core
        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_CORE.get())
                .pattern("MNM")
                .pattern("SOS")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('N', Items.NETHER_STAR)
                .define('S', ModItems.STORAGE_CORE.get())
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('C', ModItems.SPATIAL_CORE.get())
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .notRequiresMod(ModCheckUtils.MODID_APPFLUX)
                .notRequiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/" + ModItems.INFINITY_CORE.get().toString().toLowerCase()));

        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_CORE.get())
                .pattern("MNM")
                .pattern("SOE")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('N', Items.NETHER_STAR)
                .define('S', ModItems.STORAGE_CORE.get())
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('E', ModItems.ENERGY_STORAGE_CORE.get())
                .define('C', ModItems.SPATIAL_CORE.get())
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .notRequiresMod(ModCheckUtils.MODID_AAE)
                .save(consumer, ExtendedAEPlus.id("core/compat/" + ModItems.INFINITY_CORE.get().toString().toLowerCase() + "_1"));

        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_CORE.get())
                .pattern("MNM")
                .pattern("SOQ")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('N', Items.NETHER_STAR)
                .define('S', ModItems.STORAGE_CORE.get())
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('Q', ModItems.QUANTUM_STORAGE_CORE.get())
                .define('C', ModItems.SPATIAL_CORE.get())
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .notRequiresMod(ModCheckUtils.MODID_APPFLUX)
                .save(consumer, ExtendedAEPlus.id("core/compat/" + ModItems.INFINITY_CORE.get().toString().toLowerCase() + "_2"));

        NBTShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.INFINITY_CORE.get())
                .pattern("MQM")
                .pattern("SOE")
                .pattern("MCM")
                .define('M', Items.NETHERITE_BLOCK)
                .define('Q', ModItems.QUANTUM_STORAGE_CORE.get())
                .define('S', ModItems.STORAGE_CORE.get())
                .define('O', ModItems.OBLIVION_SINGULARITY.get())
                .define('E', ModItems.ENERGY_STORAGE_CORE.get())
                .define('C', ModItems.SPATIAL_CORE.get())
                .unlockedBy("has_basic_core", has(ModItems.BASIC_CORE.get()))
                .requiresMod(ModCheckUtils.MODID_AAE)
                .requiresMod(ModCheckUtils.MODID_APPFLUX)
                .save(consumer, ExtendedAEPlus.id("core/compat/" + ModItems.INFINITY_CORE.get().toString().toLowerCase() + "_3"));
    }

    private void addTransformRecipes(Consumer<FinishedRecipe> consumer) {
        TransformRecipeBuilder.transform(consumer,
                ExtendedAEPlus.id("transform/" + ModItems.OBLIVION_SINGULARITY.get().toString().toLowerCase()),
                ModItems.OBLIVION_SINGULARITY.get(), 1,
                TransformCircumstance.EXPLOSION,
                AEItems.SINGULARITY, Items.NETHER_STAR, Items.NETHERITE_BLOCK
        );
    }

    private void addReactionChamberRecipes(Consumer<FinishedRecipe> consumer) {
        ReactionChamberRecipeBuilder.react(ModItems.OBLIVION_SINGULARITY.get(), 1, 100000)
                .input(AEItems.SINGULARITY, 2)
                .input(Items.NETHER_STAR, 1)
                .input(AAEItems.QUANTUM_ALLOY_PLATE, 4)
                .fluid(AAEFluids.QUANTUM_INFUSION.source(), 2000)
                .save(consumer, ModItems.OBLIVION_SINGULARITY.get().toString().toLowerCase());
    }
}
