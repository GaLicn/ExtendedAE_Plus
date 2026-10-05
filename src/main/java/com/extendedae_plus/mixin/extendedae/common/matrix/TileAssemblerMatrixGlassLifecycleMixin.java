package com.extendedae_plus.mixin.extendedae.common.matrix;

import appeng.blockentity.grid.AENetworkBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// TileAssemblerMatrixGlass inherits these lifecycle methods from AENetworkBlockEntity,
// so inject into the declaring class and filter for glass instances at runtime.
@Mixin(value = AENetworkBlockEntity.class, remap = false)
public abstract class TileAssemblerMatrixGlassLifecycleMixin {

    @Inject(method = "onChunkUnloaded", at = @At("HEAD"), remap = false)
    private void eap$detachSuperMatrixBeforeChunkUnload(CallbackInfo ci) {
        if ((Object) this instanceof TileAssemblerMatrixGlass
                && (Object) this instanceof SuperAssemblerMatrixPart part) {
            part.eap$destroySuperMatrixClusterQuietly();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), remap = true)
    private void eap$detachSuperMatrixBeforeRemoval(CallbackInfo ci) {
        if ((Object) this instanceof TileAssemblerMatrixGlass
                && (Object) this instanceof SuperAssemblerMatrixPart part) {
            part.eap$destroySuperMatrixClusterQuietly();
        }
    }
}
