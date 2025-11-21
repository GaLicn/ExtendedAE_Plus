package com.extendedae_plus.api.ids;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public final class EAPComponents {

    public static final DeferredRegister<DataComponentType<?>> DR =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ExtendedAEPlus.MODID);

    // 2. 你的自定义组件（下面举几个常见例子）

    // 布尔值：高级阻塞模式
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ADVANCED_BLOCKING =
            register("advanced_blocking", builder ->
                    builder.persistent(Codec.BOOL)                     // 存档持久化
                           .networkSynchronized(ByteBufCodecs.BOOL)); // 网络同步（客户端要看到）

     public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SMART_DOUBLING =
            register("smart_doubling", builder ->
                    builder.persistent(Codec.BOOL)                     // 存档持久化
                           .networkSynchronized(ByteBufCodecs.BOOL)); // 网络同步（客户端要看到）

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return DR.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }
}