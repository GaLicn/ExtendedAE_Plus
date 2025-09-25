package com.extendedae_plus.mixin.ae2.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.util.ExtendedAELogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 去重样板供应器升级槽的菜单注入：
 * - 多个模组（AppliedFlux、expandedae等）都会在 PatternProviderMenu 的构造中调用 setupUpgrades，
 *   导致同一套升级槽 UI 被重复添加（渲染为四个）。
 * - 拦截 AEBaseMenu.setupUpgrades，在目标菜单为 PatternProviderMenu 且已存在升级槽时取消后续注入。
 */
@Mixin(value = AEBaseMenu.class, priority = 2000, remap = false)
public abstract class AEBaseMenuUpgradesDedupMixin {

    @Inject(method = "setupUpgrades(Lappeng/api/upgrades/IUpgradeInventory;)V", at = @At("HEAD"), cancellable = true)
    private void eap$dedupPatternProviderUpgradeSlots(IUpgradeInventory upgrades, CallbackInfo ci) {
        // 仅对样板供应器菜单去重，避免影响其他菜单
        if (((Object) this) instanceof PatternProviderMenu) {
            var self = (AEBaseMenu) (Object) this;
            var existing = self.getSlots(SlotSemantics.UPGRADE);
            if (existing != null && !existing.isEmpty()) {
                ci.cancel();
            }
        }
    }
}
