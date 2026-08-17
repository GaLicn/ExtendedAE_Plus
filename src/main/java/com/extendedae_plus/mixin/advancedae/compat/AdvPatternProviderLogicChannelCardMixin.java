package com.extendedae_plus.mixin.advancedae.compat;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseBlockEntity;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.api.bridge.CompatUpgradeProvider;
import com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AdvancedAE 高级供应器的频道卡、升级槽和统一无线连接适配。 */
@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public abstract class AdvPatternProviderLogicChannelCardMixin
        implements CompatUpgradeProvider, IInterfaceWirelessLinkBridge {
    @Shadow @Final private AdvPatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;

    @Unique private IUpgradeInventory eap$channelCardUpgrades = UpgradeInventories.empty();
    @Unique private ChannelCardConnectionController eap$channelController;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;I)V", at = @At("TAIL"))
    private void eap$initChannelUpgrades(IManagedGridNode node, AdvPatternProviderLogicHost providerHost,
                                         int patternInventorySize, CallbackInfo ci) {
        if (!UpgradeSlotCompat.usesAppfluxUpgradeSlots()) {
            // 未安装 Applied Flux 时使用本模组自己的一个频道卡槽。
            this.eap$channelCardUpgrades = UpgradeInventories.forMachine(
                    providerHost.getTerminalIcon().getItem(), 1, this::eap$onUpgradesChanged);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$saveChannelUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$channelCardUpgrades.writeToNBT(tag, "eap_channel_card_upgrades");
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loadChannelUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$channelCardUpgrades.readFromNBT(tag, "eap_channel_card_upgrades");
        }
        this.eap$getChannelCardController().onLoaded();
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$dropChannelUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            for (var stack : this.eap$channelCardUpgrades) {
                if (!stack.isEmpty()) drops.add(stack.copy());
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$clearChannelUpgrades(CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$channelCardUpgrades.clear();
        }
        this.eap$getChannelCardController().onUnloaded();
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$nodeChanged(CallbackInfo ci) {
        this.eap$getChannelCardController().onNodeChanged();
    }

    @Override
    public IUpgradeInventory eap$getCompatUpgrades() {
        return this.eap$channelCardUpgrades;
    }

    @Override
    public ChannelCardConnectionController eap$getChannelCardController() {
        if (this.eap$channelController == null) {
            this.eap$channelController = new ChannelCardConnectionController(
                    this::eap$getEffectiveUpgrades,
                    this::eap$getFallbackOwner,
                    () -> new GenericNodeEndpointImpl(this.host::getBlockEntity, this.mainNode::getNode),
                    this::eap$notifyChanged,
                    this::eap$wakeNode,
                    this::eap$isClientSide);
            if (this.host.getBlockEntity() != null) {
                ChannelCardConnectionController.register(this.host.getBlockEntity(), this.eap$channelController);
            }
        }
        return this.eap$channelController;
    }

    @Override
    public boolean eap$shouldKeepTicking() {
        return UpgradeSlotCompat.usesAppfluxUpgradeSlots()
                || IInterfaceWirelessLinkBridge.super.eap$shouldKeepTicking();
    }

    @Unique
    private IUpgradeInventory eap$getEffectiveUpgrades() {
        if (UpgradeSlotCompat.usesAppfluxUpgradeSlots() && (Object) this instanceof IUpgradeableObject upgradeable) {
            return upgradeable.getUpgrades();
        }
        return this.eap$channelCardUpgrades;
    }

    @Unique
    @Nullable
    private UUID eap$getFallbackOwner() {
        var node = this.mainNode != null ? this.mainNode.getNode() : null;
        return node != null ? node.getOwningPlayerProfileId() : null;
    }

    @Unique
    private boolean eap$isClientSide() {
        var blockEntity = this.host.getBlockEntity();
        return blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide;
    }

    @Unique
    private void eap$onUpgradesChanged() {
        this.eap$getChannelCardController().onUpgradesChanged();
        this.host.saveChanges();
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
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }
}
