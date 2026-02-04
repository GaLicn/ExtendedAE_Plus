package com.extendedae_plus.mixin;

import com.extendedae_plus.network.PickFromWirelessC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


//TODO废弃功能，暂时不再启用
@Mixin(Minecraft.class)
public class PickFromWirelessMixin {
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void eap$pickFromAeWireless(CallbackInfo ci) {
        if (this.player == null || this.hitResult == null || this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // 仅生存模式
        GameType type = Minecraft.getInstance().gameMode != null ? Minecraft.getInstance().gameMode.getPlayerMode() : null;
        if (type == null || type.isCreative()) {
            return;
        }
        BlockHitResult bhr = (BlockHitResult) this.hitResult;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            try {
                BlockState state = level.getBlockState(bhr.getBlockPos());
                if (state != null && !state.isAir()) {
                    ItemStack picked = state.getBlock().getCloneItemStack(state, bhr, level, bhr.getBlockPos(), this.player);
                    if (picked.isEmpty()) {
                        picked = state.getBlock().asItem().getDefaultInstance();
                    }
                    if (!picked.isEmpty()) {
                        if (!ItemStack.isSameItemSameComponents(picked, this.player.getMainHandItem())) {
                            int slot = this.player.getInventory().findSlotMatchingItem(picked);
                            if (slot != -1) {
                                return; // 交给原版 pickBlock 处理
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                return;
            }
        }

        Vec3 loc = bhr.getLocation();
        PacketDistributor.sendToServer(new PickFromWirelessC2SPacket(bhr.getBlockPos(), bhr.getDirection(), loc));
        ci.cancel();
    }
}
