package com.extendedae_plus.mixin.ae2WTlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为通用无线样板访问终端（AE2WTlib 集成）容器注册通用动作（CGenericPacket 分发）
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.xmod.wt.ContainerUWirelessExPAT", remap = false)
public abstract class ContainerUWirelessExPatternTerminalMixin {

    // 1.21 环境下，Glodium IActionHolder 已被移除，改为 no-op 保留占位，以便未来扩展。
    // 明确目标构造签名：<init>(int, Inventory, HostUWirelessExPAT)
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lcom/glodblock/github/extendedae/xmod/wt/HostUWirelessExPAT;)V", at = @At("TAIL"), remap = false)
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, com.glodblock.github.extendedae.xmod.wt.HostUWirelessExPAT host, CallbackInfo ci) {
        // no-op
    }
}
