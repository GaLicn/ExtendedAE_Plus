package com.extendedae_plus.mixin.advancedae.compat;

import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.pedroksl.advanced_ae.common.parts.AdvPatternProviderPart;
import org.spongepowered.asm.mixin.Mixin;

/** AdvancedAE 部件把频道卡生命周期转发给共享逻辑。 */
@Mixin(value = AdvPatternProviderPart.class, remap = false)
public abstract class AdvPatternProviderPartChannelCardHostMixin implements IInterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((AdvPatternProviderPart) (Object) this).getLogic();
        return logic instanceof IInterfaceWirelessLinkBridge bridge
                ? bridge.eap$getChannelCardController() : null;
    }
}
