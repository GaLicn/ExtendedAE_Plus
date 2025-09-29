package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.AEBasePart;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为所有AE2 Part添加无线连接状态的客户端同步功能
 */
@Mixin(value = AEBasePart.class, remap = false)
public class AEBasePartClientSyncMixin {

    @Inject(method = "writeToStream", at = @At("TAIL"))
    private void eap$writeWirelessState(FriendlyByteBuf data, CallbackInfo ci) {
        // 检查是否实现了无线链接桥接接口
        if (this instanceof IInterfaceWirelessLinkBridge) {
            IInterfaceWirelessLinkBridge bridge = (IInterfaceWirelessLinkBridge) this;
            // 同步无线连接状态到客户端
            boolean connected = false;
            try {
                // 只在服务端获取真实连接状态
                AEBasePart part = (AEBasePart)(Object)this;
                if (!part.isClientSide()) {
                    connected = bridge.eap$isWirelessConnected();
                }
            } catch (Exception e) {
                // 忽略异常，默认为false
            }
            data.writeBoolean(connected);
        } else {
            // 不是无线链接Part，写入false
            data.writeBoolean(false);
        }
    }

    @Inject(method = "readFromStream", at = @At("TAIL"))
    private void eap$readWirelessState(FriendlyByteBuf data, CallbackInfoReturnable<Boolean> cir) {
        // 读取无线连接状态
        boolean connected = data.readBoolean();
        
        // 检查是否实现了无线链接桥接接口
        if (this instanceof IInterfaceWirelessLinkBridge) {
            IInterfaceWirelessLinkBridge bridge = (IInterfaceWirelessLinkBridge) this;
            try {
                // 更新客户端状态
                bridge.eap$setClientWirelessState(connected);
            } catch (Exception e) {
                // 忽略异常
            }
        }
    }
}
