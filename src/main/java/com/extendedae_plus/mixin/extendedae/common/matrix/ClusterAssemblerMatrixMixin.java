package com.extendedae_plus.mixin.extendedae.common.matrix;

import com.extendedae_plus.content.matrix.CrafterCorePlusBlockEntity;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClusterAssemblerMatrix.class, remap = false)
public class ClusterAssemblerMatrixMixin {

    @Shadow
    @Final
    private ReferenceSet<TileAssemblerMatrixCrafter> availableCrafters;

    @Shadow
    @Final
    private ReferenceSet<TileAssemblerMatrixCrafter> busyCrafters;

    /**
     * 重写 addCrafter 逻辑，对 CrafterCorePlusBlockEntity 使用正确的线程上限判断
     */
    @Inject(method = "addCrafter", at = @At("HEAD"), cancellable = true)
    private void onAddCrafter(TileAssemblerMatrixCrafter crafter, CallbackInfo ci) {
        if (crafter instanceof CrafterCorePlusBlockEntity plusCrafter) {
            // 对于超级合成核心，使用32线程上限判断
            if (plusCrafter.usedThread() < CrafterCorePlusBlockEntity.MAX_THREAD) {
                this.availableCrafters.add(crafter);
            } else {
                this.busyCrafters.add(crafter);
            }
            ci.cancel();
        }
        // 其他类型的Crafter走原版逻辑
    }
}
