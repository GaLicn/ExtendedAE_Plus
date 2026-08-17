package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.crafting.PatternProviderPart;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;

/** 样板供应器部件把进出世界生命周期转发给供应器逻辑。 */
@Mixin(value = PatternProviderPart.class, remap = false)
public abstract class PatternProviderPartChannelCardHostMixin implements IInterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((PatternProviderPart) (Object) this).getLogic();
        return logic instanceof IInterfaceWirelessLinkBridge bridge
                ? bridge.eap$getChannelCardController() : null;
    }
}
