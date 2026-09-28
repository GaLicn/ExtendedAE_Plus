package com.extendedae_plus.mixin.advancedae.compat;

import com.extendedae_plus.api.bridge.InterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.pedroksl.advanced_ae.common.parts.AdvPatternProviderPart;
import org.spongepowered.asm.mixin.Mixin;

/** AdvancedAE 部件侧生命周期桥接到供应器逻辑控制器。 */
@Mixin(value = AdvPatternProviderPart.class, remap = false)
public abstract class AdvPatternProviderPartChannelCardHostMixin implements InterfaceWirelessLinkBridge {
    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        var logic = ((AdvPatternProviderPart) (Object) this).getLogic();
        return logic instanceof InterfaceWirelessLinkBridge bridge ? bridge.eap$getChannelCardController() : null;
    }
}
