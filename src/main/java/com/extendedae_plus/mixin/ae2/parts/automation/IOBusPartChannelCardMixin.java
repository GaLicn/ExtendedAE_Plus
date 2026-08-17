package com.extendedae_plus.mixin.ae2.parts.automation;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.AEBasePart;
import appeng.parts.automation.IOBusPart;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2 输入/输出总线的统一频道卡生命周期接入。 */
@Mixin(value = IOBusPart.class, remap = false)
public abstract class IOBusPartChannelCardMixin implements IInterfaceWirelessLinkBridge, IUpgradeableObject {
    @Unique private ChannelCardConnectionController eap$channelController;

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void eap$upgradesChanged(CallbackInfo ci) {
        this.eap$getChannelCardController().onUpgradesChanged();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loaded(CompoundTag tag, CallbackInfo ci) {
        this.eap$getChannelCardController().onLoaded();
    }

    @Inject(method = "getTickingRequest", at = @At("RETURN"), cancellable = true)
    private void eap$keepTickerAwake(IGridNode node, CallbackInfoReturnable<TickingRequest> cir) {
        if (this.eap$getChannelCardController().shouldKeepTicking()) {
            var original = cir.getReturnValue();
            cir.setReturnValue(new TickingRequest(
                    original.minTickRate(), original.maxTickRate(), false, original.canBeAlerted()));
        }
    }

    @Inject(method = "tickingRequest", at = @At("TAIL"), cancellable = true)
    private void eap$tickChannelCard(IGridNode node, int ticksSinceLastCall,
                                     CallbackInfoReturnable<TickRateModulation> cir) {
        var controller = this.eap$getChannelCardController();
        controller.tick();
        if (controller.shouldKeepTicking() && cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }
    }

    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        if (this.eap$channelController == null) {
            this.eap$channelController = new ChannelCardConnectionController(
                    this::getUpgrades,
                    this::eap$getFallbackOwner,
                    () -> new GenericNodeEndpointImpl(
                            () -> ((AEBasePart) (Object) this).getHost().getBlockEntity(),
                            () -> ((IActionHost) (Object) this).getActionableNode()),
                    () -> ((AEBasePart) (Object) this).getHost().markForUpdate(),
                    this::eap$wakeNode,
                    () -> ((AEBasePart) (Object) this).isClientSide());
            var blockEntity = ((AEBasePart) (Object) this).getHost().getBlockEntity();
            if (blockEntity != null) {
                ChannelCardConnectionController.register(blockEntity, this.eap$channelController);
            }
        }
        return this.eap$channelController;
    }

    @Unique
    private java.util.UUID eap$getFallbackOwner() {
        var node = ((IActionHost) (Object) this).getActionableNode();
        return node != null ? node.getOwningPlayerProfileId() : null;
    }

    @Unique
    private void eap$wakeNode() {
        var node = ((IActionHost) (Object) this).getActionableNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().getTickManager().wakeDevice(node);
        }
    }
}
