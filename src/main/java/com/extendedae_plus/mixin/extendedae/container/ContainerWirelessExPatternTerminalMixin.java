package com.extendedae_plus.mixin.extendedae.container;

import com.glodblock.github.extendedae.xmod.wt.ContainerWirelessExPAT;
import com.glodblock.github.extendedae.xmod.wt.HostWirelessExPAT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为无线样板访问终端容器注册通用动作（CGenericPacket 分发）
 */
@Pseudo
@Mixin(value = ContainerWirelessExPAT.class, remap = false)
public abstract class ContainerWirelessExPatternTerminalMixin {
    // 1.21 版本中 ExtendedAE 不再使用 glodium IActionHolder。
    // 保留空混入以便后续需要时扩展。

    // 构造方法注入（显式签名），与 ExtendedAE 源码保持一致
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lcom/glodblock/github/extendedae/xmod/wt/HostWirelessExPAT;)V", at = @At("TAIL"), require = 0, remap = false)
    private void init$eap(int id, net.minecraft.world.entity.player.Inventory playerInventory, HostWirelessExPAT host, CallbackInfo ci) {
        // no-op
    }
}
