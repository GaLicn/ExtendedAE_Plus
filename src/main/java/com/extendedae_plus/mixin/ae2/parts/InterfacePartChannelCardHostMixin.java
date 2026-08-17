package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.misc.InterfacePart;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;

/** 接口部件把进出世界生命周期转发给接口逻辑。 */
@Mixin(value = InterfacePart.class, remap = false)
public abstract class InterfacePartChannelCardHostMixin implements IInterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((InterfacePart) (Object) this).getInterfaceLogic();
        return logic instanceof IInterfaceWirelessLinkBridge bridge
                ? bridge.eap$getChannelCardController() : null;
    }
}
