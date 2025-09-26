package com.extendedae_plus.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {
//    @Inject(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
//            at = @At("HEAD"), cancellable = true)
//    private static void eaep$cancelCheck(ContainerLevelAccess access, Player player, Block targetBlock, CallbackInfoReturnable<Boolean> cir) {
//        cir.setReturnValue(access.evaluate(((level, pos) -> {
//            for (Direction direction : Direction.values()) {
//                if (level.getBlockState(pos.relative(direction)).getBlock()
//                        instanceof PatternProviderBlock)
//                    return true;
//            }
//            return level.getBlockState(pos).is(targetBlock) && player.canInteractWithBlock(pos, 4.0F);
//        }), true));
//    }
}
