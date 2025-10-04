package com.extendedae_plus.mixin;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class MixinPlayer {
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void eaep$cancelCheck(BlockPos pos, double distance, CallbackInfoReturnable<Boolean> cir) {
        var self = (Player)(Object) this;
        var level = self.level();
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock()
                    instanceof AEBaseEntityBlock)
                cir.setReturnValue(true);
        }
    }
}
