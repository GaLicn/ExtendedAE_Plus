package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import com.extendedae_plus.content.ae2.TagInventoryMEInterfaceBlockEntity;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.HybridCoreBlockEntity;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixFrameBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixWallBlockEntity;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlockEntity;
import com.extendedae_plus.content.cutter.SuperCircuitCutterBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.concurrent.atomic.AtomicReference;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExtendedAEPlus.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessTransceiverBlockEntity>> WIRELESS_TRANSCEIVER_BE =
            BLOCK_ENTITY_TYPES.register("wireless_transceiver",
                    () -> new BlockEntityType<>(WirelessTransceiverBlockEntity::new,
                            ModBlocks.WIRELESS_TRANSCEIVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabeledWirelessTransceiverBlockEntity>> LABELED_WIRELESS_TRANSCEIVER_BE =
            BLOCK_ENTITY_TYPES.register("labeled_wireless_transceiver",
                    () -> new BlockEntityType<>(LabeledWirelessTransceiverBlockEntity::new,
                            ModBlocks.LABELED_WIRELESS_TRANSCEIVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkPatternControllerBlockEntity>> NETWORK_PATTERN_CONTROLLER_BE =
            BLOCK_ENTITY_TYPES.register("network_pattern_controller",
                    () -> new BlockEntityType<>(NetworkPatternControllerBlockEntity::new,
                            ModBlocks.NETWORK_PATTERN_CONTROLLER.get()));

    // 提供一个 CraftingBlockEntity 的类型，允许附着在本模组自定义加速器方块上，绕过 AE2 默认类型的“有效方块列表”校验
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingBlockEntity>> EPLUS_CRAFTING_UNIT_BE =
            BLOCK_ENTITY_TYPES.register("eplus_crafting_unit",
                    () -> {
                        AtomicReference<BlockEntityType<CraftingBlockEntity>> ref = new AtomicReference<>();
                        BlockEntityType.BlockEntitySupplier<CraftingBlockEntity> supplier = (pos, state) -> new CraftingBlockEntity(ref.get(), pos, state);
                        BlockEntityType<CraftingBlockEntity> type = new BlockEntityType<>(
                                supplier,
                                ModBlocks.ACCELERATOR_4x.get(),
                                ModBlocks.ACCELERATOR_16x.get(),
                                ModBlocks.ACCELERATOR_64x.get(),
                                ModBlocks.ACCELERATOR_256x.get(),
                                ModBlocks.ACCELERATOR_1024x.get()
                        );
                        ref.set(type);
                        return type;
                    });

    // 装配矩阵上传核心方块实体
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UploadCoreBlockEntity>> UPLOAD_CORE_BE =
            BLOCK_ENTITY_TYPES.register("upload_core",
                    () -> new BlockEntityType<>(UploadCoreBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_UPLOAD_CORE.get()));

    //超级装配矩阵速度核心
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SpeedCorePlusBlockEntity>> ASSEMBLER_MATRIX_SPEED_PLUS_BE=
            BLOCK_ENTITY_TYPES.register("assembler_matrix_speed_plus",
                    ()->new BlockEntityType<>(SpeedCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_SPEED_PLUS.get()));

    //超级装配矩阵合成核心
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CrafterCorePlusBlockEntity>> ASSEMBLER_MATRIX_CRAFTER_PLUS_BE=
            BLOCK_ENTITY_TYPES.register("assembler_matrix_crafter_plus",
                    ()-> new BlockEntityType<>(CrafterCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_CRAFTER_PLUS.get()));

    //超级装配矩阵样板核心
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<PatternCorePlusBlockEntity>> ASSEMBLER_MATRIX_PATTERN_PLUS_BE=
            BLOCK_ENTITY_TYPES.register("assembler_matrix_pattern_plus",
                    ()-> new BlockEntityType<>(PatternCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_PATTERN_PLUS.get()));

    // 超级装配矩阵混合核心
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HybridCoreBlockEntity>> ASSEMBLER_MATRIX_HYBRID_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("assembler_matrix_hybrid_plus",
                    () -> new BlockEntityType<>(HybridCoreBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_HYBRID_PLUS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperAssemblerMatrixFrameBlockEntity>> SUPER_ASSEMBLER_MATRIX_FRAME_BE =
            BLOCK_ENTITY_TYPES.register("super_assembler_matrix_frame",
                    () -> new BlockEntityType<>(SuperAssemblerMatrixFrameBlockEntity::new,
                            ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperAssemblerMatrixWallBlockEntity>> SUPER_ASSEMBLER_MATRIX_WALL_BE =
            BLOCK_ENTITY_TYPES.register("super_assembler_matrix_wall",
                    () -> new BlockEntityType<>(SuperAssemblerMatrixWallBlockEntity::new,
                            ModBlocks.SUPER_ASSEMBLER_MATRIX_WALL.get()));

    //镜像样板供应器
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MirrorPatternProviderBlockEntity>> MIRROR_PATTERN_PROVIDER_BE=
            BLOCK_ENTITY_TYPES.register("mirror_pattern_provider",
                    ()-> new BlockEntityType<>(MirrorPatternProviderBlockEntity::new,
                            ModBlocks.MIRROR_PATTERN_PROVIDER_BLOCK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TagInventoryMEInterfaceBlockEntity>> TAG_INVENTORY_ME_INTERFACE_BE =
            BLOCK_ENTITY_TYPES.register("tag_inventory_me_interface",
                    () -> new BlockEntityType<>(TagInventoryMEInterfaceBlockEntity::new,
                            ModBlocks.TAG_INVENTORY_ME_INTERFACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperCrystalAssemblerBlockEntity>> CRYSTAL_ASSEMBLER_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("crystal_assembler_plus",
                    () -> new BlockEntityType<>(SuperCrystalAssemblerBlockEntity::new,
                            ModBlocks.CRYSTAL_ASSEMBLER_PLUS.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperCircuitCutterBlockEntity>> CIRCUIT_CUTTER_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("circuit_cutter_plus",
                    () -> new BlockEntityType<>(SuperCircuitCutterBlockEntity::new,
                            ModBlocks.CIRCUIT_CUTTER_PLUS.get()));
}
