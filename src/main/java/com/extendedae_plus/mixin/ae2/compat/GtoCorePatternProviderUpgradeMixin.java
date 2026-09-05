package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.compat.DynamicSizeInternalInventory;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** GTOCore 覆盖原兼容层时，恢复扩展供应器的升级库存与动态页数。 */
@Mixin(value = PatternProviderLogic.class, priority = 400, remap = false)
public abstract class GtoCorePatternProviderUpgradeMixin
        implements IUpgradeableObject, PatternProviderPageUnlockBridge {

    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private AppEngInternalInventory patternInventory;
    @Shadow public abstract void updatePatterns();
    @Unique private IUpgradeInventory eap$upgrades = UpgradeInventories.empty();
    @Unique private InternalInventory eap$dynamicPatternInventory;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host,
            int patternInventorySize, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            return;
        }
        this.eap$upgrades = UpgradeInventories.forMachine(
                host.getTerminalIcon().getItem(),
                UpgradeSlotCompat.getPatternProviderLocalUpgradeSlots(host),
                this::eap$onUpgradesChanged);
    }

    @Inject(method = "getPatternInv", at = @At("RETURN"), cancellable = true)
    private void eap$exposeDynamicPatternInventory(CallbackInfoReturnable<InternalInventory> cir) {
        if (!this.eap$isExtendedPatternProviderHost()) {
            return;
        }
        if (this.eap$dynamicPatternInventory == null) {
            this.eap$dynamicPatternInventory = new DynamicSizeInternalInventory(
                    this.patternInventory, this::eap$computeUnlockedPatternSlots);
        }
        cir.setReturnValue(this.eap$dynamicPatternInventory);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$saveUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$upgrades.writeToNBT(tag, "compat_upgrades");
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loadUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$upgrades.readFromNBT(tag, "compat_upgrades");
            this.eap$onUpgradesChanged();
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            for (ItemStack stack : this.eap$upgrades) {
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$clearUpgrades(CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$upgrades.clear();
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.eap$upgrades;
    }

    @Unique
    private void eap$onUpgradesChanged() {
        this.host.saveChanges();
        this.updatePatterns();
    }

    @Unique
    private int eap$computeUnlockedPatternSlots() {
        return Math.min(this.patternInventory.size(),
                UpgradeSlotCompat.getUnlockedExtendedPatternProviderSlots(this.eap$upgrades));
    }

    @Override
    public boolean eap$isExtendedPatternProviderHost() {
        return UpgradeSlotCompat.isExtendedPatternProviderHost(this.host);
    }

    @Override
    public int eap$getUnlockedPatternPages() {
        return Math.max(1, (this.eap$getUnlockedPatternSlots() + 35) / 36);
    }

    @Override
    public int eap$getUnlockedPatternSlots() {
        return this.eap$isExtendedPatternProviderHost()
                ? this.eap$computeUnlockedPatternSlots()
                : this.patternInventory.size();
    }

    @Override
    public int eap$getLegacyUnlockedPatternSlots() {
        return 0;
    }
}
