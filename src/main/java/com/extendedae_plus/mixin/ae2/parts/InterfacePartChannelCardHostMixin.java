package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.misc.InterfacePart;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;

/** 让接口部件本身参与统一部件生命周期，避免只移除部件时连接残留。 */
@Mixin(value = InterfacePart.class, remap = false)
public abstract class InterfacePartChannelCardHostMixin implements InterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((InterfacePart) (Object) this).getInterfaceLogic();
        return logic instanceof InterfaceWirelessLinkBridge bridge ? bridge.eap$getChannelCardController() : null;
    }
}
