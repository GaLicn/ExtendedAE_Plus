package com.extendedae_plus.mixin.ae2.parts;

import appeng.parts.crafting.PatternProviderPart;
import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Mixin;

/** 让样板供应器部件本身参与统一部件生命周期。 */
@Mixin(value = PatternProviderPart.class, remap = false)
public abstract class PatternProviderPartChannelCardHostMixin implements InterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((PatternProviderPart) (Object) this).getLogic();
        return logic instanceof InterfaceWirelessLinkBridge bridge ? bridge.eap$getChannelCardController() : null;
    }
}
