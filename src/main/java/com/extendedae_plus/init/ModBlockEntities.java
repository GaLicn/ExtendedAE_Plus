package com.extendedae_plus.init;

import appeng.blockentity.crafting.CraftingBlockEntity;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExtendedAEPlus.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessTransceiverBlockEntity>> WIRELESS_TRANSCEIVER_BE =
            BLOCK_ENTITY_TYPES.register("wireless_transceiver",
                    () -> BlockEntityType.Builder.of(WirelessTransceiverBlockEntity::new,
                            ModBlocks.WIRELESS_TRANSCEIVER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkPatternControllerBlockEntity>> NETWORK_PATTERN_CONTROLLER_BE =
            BLOCK_ENTITY_TYPES.register("network_pattern_controller",
                    () -> BlockEntityType.Builder.of(NetworkPatternControllerBlockEntity::new,
                            ModBlocks.NETWORK_PATTERN_CONTROLLER.get()).build(null));

    // 提供一个 CraftingBlockEntity 的类型，允许附着在本模组自定义加速器方块上，绕过 AE2 默认类型的“有效方块列表”校验
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingBlockEntity>> EPLUS_CRAFTING_UNIT_BE =
            BLOCK_ENTITY_TYPES.register("eplus_crafting_unit",
                    () -> {
                        java.util.concurrent.atomic.AtomicReference<BlockEntityType<CraftingBlockEntity>> ref = new java.util.concurrent.atomic.AtomicReference<>();
                        BlockEntityType.BlockEntitySupplier<CraftingBlockEntity> supplier = (pos, state) -> new CraftingBlockEntity(ref.get(), pos, state);
                        BlockEntityType<CraftingBlockEntity> type = BlockEntityType.Builder.of(
                                supplier,
                                ModBlocks.ACCELERATOR_4x.get(),
                                ModBlocks.ACCELERATOR_16x.get(),
                                ModBlocks.ACCELERATOR_64x.get(),
                                ModBlocks.ACCELERATOR_256x.get(),
                                ModBlocks.ACCELERATOR_1024x.get()
                        ).build(null);
                        ref.set(type);
                        return type;
                    });

    // 装配矩阵上传核心方块实体
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UploadCoreBlockEntity>> UPLOAD_CORE_BE =
            BLOCK_ENTITY_TYPES.register("upload_core",
                    () -> BlockEntityType.Builder.of(UploadCoreBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_UPLOAD_CORE.get()).build(null));
}
