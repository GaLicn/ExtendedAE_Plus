package com.extendedae_plus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.PickFromWirelessC2SPacket;


@Mixin(Minecraft.class)
public class PickFromWirelessMixin {
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void extendedae_plus$pickFromAeWireless(CallbackInfo ci) {
        if (this.player == null || this.hitResult == null || this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // 仅生存模式
        GameType type = Minecraft.getInstance().gameMode != null ? Minecraft.getInstance().gameMode.getPlayerMode() : null;
        if (type == null || type.isCreative()) {
            return;
        }
        // 发送到服务端处理
        BlockHitResult bhr = (BlockHitResult) this.hitResult;
        ModNetwork.CHANNEL.sendToServer(new PickFromWirelessC2SPacket(bhr.getBlockPos(), bhr.getDirection()));
        ci.cancel();
    }
}
