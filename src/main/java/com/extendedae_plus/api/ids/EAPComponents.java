package com.extendedae_plus.api.ids;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.items.BasicCoreItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public final class EAPComponents {

    public static final DeferredRegister<DataComponentType<?>> DR =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ExtendedAEPlus.MODID);

    // 布尔值：高级阻塞模式
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ADVANCED_BLOCKING =
            register("advanced_blocking", builder ->
                    builder.persistent(Codec.BOOL)                     // 存档持久化
                           .networkSynchronized(ByteBufCodecs.BOOL)); // 网络同步（客户端要看到）

     public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SMART_DOUBLING =
            register("smart_doubling", builder ->
                    builder.persistent(Codec.BOOL)                     // 存档持久化
                           .networkSynchronized(ByteBufCodecs.BOOL)); // 网络同步（客户端要看到）

    // 核心类型组件（CoreType）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BasicCoreItem.CoreType>> CORE_TYPE =
            register("core_type", builder ->
                    builder.persistent(Codec.STRING.xmap(BasicCoreItem.CoreType::valueOf, Enum::name))
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeEnum,
                                   buf -> buf.readEnum(BasicCoreItem.CoreType.class)
                           )));

    // 核心阶段组件（Stage）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CORE_STAGE =
            register("core_stage", builder ->
                    builder.persistent(Codec.INT)
                           .networkSynchronized(ByteBufCodecs.INT));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return DR.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }
}