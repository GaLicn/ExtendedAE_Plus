package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 确保在模型烘焙/资源重载期间也会注册内置模型，避免在刷新资源后丢失内置模型映射。
 */
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModelEvents {
    private ClientModelEvents() {}

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        // 在每次模型重载开始时确保内置模型已注册
        // 先显式登记这些模型ID，使其在首次加载阶段被请求，从而触发我们的内置模型拦截
        event.register(ExtendedAEPlus.id("block/crafting/4x_accelerator_formed_v2"));
        event.register(ExtendedAEPlus.id("block/crafting/16x_accelerator_formed_v2"));
        event.register(ExtendedAEPlus.id("block/crafting/64x_accelerator_formed_v2"));
        event.register(ExtendedAEPlus.id("block/crafting/256x_accelerator_formed_v2"));
        event.register(ExtendedAEPlus.id("block/crafting/1024x_accelerator_formed_v2"));
        ClientProxy.init();
    }
}
