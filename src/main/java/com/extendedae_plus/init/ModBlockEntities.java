package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.content.controller.NetworkPatternControllerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
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
}
