package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.ae.wireless.endpoint.InterfaceNodeEndpointImpl;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 接口只负责向统一频道卡控制器提供宿主信息。 */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicChannelCardMixin implements IInterfaceWirelessLinkBridge {
    @Shadow(remap = false) protected InterfaceLogicHost host;
    @Shadow(remap = false) protected IManagedGridNode mainNode;
    @Unique private ChannelCardConnectionController eap$channelController;

    @Shadow(remap = false)
    public abstract IUpgradeInventory getUpgrades();

    @Inject(method = "onUpgradesChanged", at = @At("TAIL"), remap = false)
    private void eap$upgradesChanged(CallbackInfo ci) {
        this.eap$getChannelCardController().onUpgradesChanged();
    }

    @Inject(method = "gridChanged", at = @At("TAIL"), remap = false)
    private void eap$gridChanged(CallbackInfo ci) {
        this.eap$getChannelCardController().onNodeChanged();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void eap$loaded(CompoundTag tag, CallbackInfo ci) {
        this.eap$getChannelCardController().onLoaded();
    }

    @Inject(method = "clearContent", at = @At("HEAD"), remap = false)
    private void eap$cleared(CallbackInfo ci) {
        this.eap$getChannelCardController().onUnloaded();
    }

    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        if (this.eap$channelController == null) {
            this.eap$channelController = new ChannelCardConnectionController(
                    this::getUpgrades,
                    this::eap$getFallbackOwner,
                    () -> new InterfaceNodeEndpointImpl(this.host, this.mainNode::getNode),
                    this::eap$notifyChanged,
                    this::eap$wakeNode,
                    this::eap$isClientSide);
            if (this.host.getBlockEntity() != null) {
                ChannelCardConnectionController.register(this.host.getBlockEntity(), this.eap$channelController);
            }
        }
        return this.eap$channelController;
    }

    @Unique
    private boolean eap$isClientSide() {
        var blockEntity = this.host.getBlockEntity();
        return blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide;
    }

    @Unique
    private java.util.UUID eap$getFallbackOwner() {
        var node = this.mainNode != null ? this.mainNode.getNode() : null;
        return node != null ? node.getOwningPlayerProfileId() : null;
    }

    @Unique
    private void eap$notifyChanged() {
        this.host.saveChanges();
        if (this.host.getBlockEntity() instanceof AEBaseBlockEntity blockEntity) {
            blockEntity.markForUpdate();
        }
    }

    @Unique
    private void eap$wakeNode() {
        if (this.mainNode != null) {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
    }
}
