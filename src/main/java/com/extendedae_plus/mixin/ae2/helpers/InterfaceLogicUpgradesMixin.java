package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.bridge.CompatUpgradeProvider;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.neoforged.fml.ModList;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为ME接口增加升级槽数量
 * - 没有AppliedFlux时：从1个增加到2个
 * - 有AppliedFlux时：从2个增加到3个
 */
@Mixin(value = InterfaceLogic.class, priority = 1100, remap = false)
public abstract class InterfaceLogicUpgradesMixin implements CompatUpgradeProvider {

    @Shadow(remap = false)
    @Final
    @Mutable
    private IUpgradeInventory upgrades;

    @Unique
    private IUpgradeInventory eap$compatUpgrades;


    /**
     * 在AppliedFlux之后进一步增加升级槽数量
     * mixin优先级设置为1100，确保在AppliedFlux的mixin之后执行
     */
    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/InterfaceLogicHost;Lnet/minecraft/world/item/Item;I)V",
            at = @At("TAIL"), remap = false)
    private void eap$expandUpgradesAfterAppliedFlux(IManagedGridNode gridNode, InterfaceLogicHost host, Item is, int slots, CallbackInfo ci) {
        boolean hasAppliedFlux = ModList.get().isLoaded("appflux");
        int currentSlots = this.upgrades.size();
        int targetSlots;
        
        if (hasAppliedFlux) {
            // AppliedFlux已经将升级槽从1增加到2，我们再增加1个变成3
            targetSlots = 3;
        } else {
            // 没有AppliedFlux，从原始的1增加到2
            targetSlots = 2;
        }
        
        // 只有当当前槽数小于目标槽数时才需要扩展
        if (currentSlots < targetSlots) {
            this.upgrades = UpgradeInventories.forMachine(is, targetSlots, this::eap$onUpgradesChanged);
            
            // 设置兼容升级槽
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                this.eap$compatUpgrades = this.upgrades;
            }
        }
    }
    
    @Unique
    private void eap$onUpgradesChanged() {
        // 调用原始的onUpgradesChanged方法
        try {
            // 通过反射调用原始方法，因为它是protected的
            var method = InterfaceLogic.class.getDeclaredMethod("onUpgradesChanged");
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            ExtendedAELogger.LOGGER.error("[ME接口] 调用onUpgradesChanged失败", e);
        }
    }

    @Override
    public IUpgradeInventory eap$getCompatUpgrades() {
        return this.eap$compatUpgrades != null ? this.eap$compatUpgrades : this.upgrades;
    }
}
