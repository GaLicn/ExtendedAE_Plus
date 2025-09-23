package com.extendedae_plus.mixin.hooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import com.extendedae_plus.hooks.BuiltInModelHooks;

/**
 * 复制 MAE2/AE2 的做法：在模型加载时优先查询我们的内置模型表，
 * 若命中则缓存并阻止继续查找 JSON 模型。
 */
@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    private void eap$loadModelHook(ResourceLocation id, CallbackInfo ci) {
        var model = BuiltInModelHooks.getBuiltInModel(id);
        if (model != null) {
            cacheAndQueueDependencies(id, model);
            ci.cancel();
        }
    }

    @Shadow
    protected void cacheAndQueueDependencies(ResourceLocation id, UnbakedModel unbakedModel) {
    }
}
