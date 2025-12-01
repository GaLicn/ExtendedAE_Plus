package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlockEntity;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ExtendedAEPlus.MODID);

    public static final RegistryObject<BlockEntityType<WirelessTransceiverBlockEntity>> WIRELESS_TRANSCEIVER_BE =
            BLOCK_ENTITY_TYPES.register("wireless_transceiver",
                    () -> BlockEntityType.Builder.of(WirelessTransceiverBlockEntity::new,
                            ModBlocks.WIRELESS_TRANSCEIVER.get()).build(null));

    public static final RegistryObject<BlockEntityType<NetworkPatternControllerBlockEntity>> NETWORK_PATTERN_CONTROLLER_BE =
            BLOCK_ENTITY_TYPES.register("network_pattern_controller",
                    () -> BlockEntityType.Builder.of(NetworkPatternControllerBlockEntity::new,
                            ModBlocks.NETWORK_PATTERN_CONTROLLER.get()).build(null));

    //超级装配矩阵速度核心
    public static final RegistryObject<BlockEntityType<SpeedCorePlusBlockEntity>> ASSEMBLER_MATRIX_SPEED_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("assembler_matrix_speed_plus",
                    ()-> BlockEntityType.Builder.of(SpeedCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_SPEED_PLUS.get()).build(null));

    //超级装配矩阵合成核心
    public static final RegistryObject<BlockEntityType<CrafterCorePlusBlockEntity>> ASSEMBLER_MATRIX_CRAFTER_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("assembler_matrix_crafter_plus",
                    ()-> BlockEntityType.Builder.of(CrafterCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_CRAFTER_PLUS.get()).build(null));

    //超级装配矩阵样板核心
    public static final RegistryObject<BlockEntityType<PatternCorePlusBlockEntity>> ASSEMBLER_MATRIX_PATTERN_PLUS_BE =
            BLOCK_ENTITY_TYPES.register("assmbler_matrix_pattern_plus",
                    ()-> BlockEntityType.Builder.of(PatternCorePlusBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_PATTERN_PLUS.get()).build(null));

    // 装配矩阵上传核心
    public static final RegistryObject<BlockEntityType<UploadCoreBlockEntity>> UPLOAD_CORE_BE =
            BLOCK_ENTITY_TYPES.register("assembler_matrix_upload_core",
                    () -> BlockEntityType.Builder.of(UploadCoreBlockEntity::new,
                            ModBlocks.ASSEMBLER_MATRIX_UPLOAD_CORE.get()).build(null));
}
