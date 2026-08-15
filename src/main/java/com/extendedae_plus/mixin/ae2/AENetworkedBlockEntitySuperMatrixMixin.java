package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版玻璃区块卸载时，清理其所属的超级矩阵集群。 */
@Mixin(value = AENetworkedBlockEntity.class, remap = false)
public abstract class AENetworkedBlockEntitySuperMatrixMixin {

    @Inject(method = "onChunkUnloaded", at = @At("HEAD"))
    private void eap$detachSuperMatrixOnChunkUnload(CallbackInfo ci) {
        if ((Object) this instanceof SuperAssemblerMatrixPart part) {
            part.eap$destroySuperMatrixClusterQuietly();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void eap$detachSuperMatrixOnRemoval(CallbackInfo ci) {
        if ((Object) this instanceof SuperAssemblerMatrixPart part) {
            part.eap$destroySuperMatrixClusterQuietly();
        }
    }
}
