package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 网络方块实体统一转发区块卸载与永久移除生命周期。 */
@Mixin(value = AENetworkedBlockEntity.class, remap = false)
public abstract class ChannelCardNetworkedBlockEntityLifecycleMixin {
    @Inject(method = "onChunkUnloaded", at = @At("HEAD"))
    private void eap$unloadChannelControllers(CallbackInfo ci) {
        ChannelCardConnectionController.unloadFor((net.minecraft.world.level.block.entity.BlockEntity) (Object) this);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void eap$removeChannelControllers(CallbackInfo ci) {
        ChannelCardConnectionController.unloadFor((net.minecraft.world.level.block.entity.BlockEntity) (Object) this);
    }
}
