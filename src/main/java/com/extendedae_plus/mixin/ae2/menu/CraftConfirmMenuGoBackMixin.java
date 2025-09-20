package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 CraftConfirmMenu.goBack() 结束后（RETURN）处理：
 * 若客户端按住 Shift，则将缺失条目添加到 JEI 书签，便于后续合成或标记。
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public class CraftConfirmMenuGoBackMixin {

    @Inject(method = "goBack", at = @At("RETURN"))
    private void eap$afterGoBack(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        try {
            // 仅客户端执行
            if (!self.isClientSide()) return;

            // 检测是否按住 Shift
            boolean shiftDown = false;
            try {
                shiftDown = Screen.hasShiftDown();
            } catch (Throwable ignored) {}
            if (!shiftDown) return;

            // 获取合成计划摘要与条目
            CraftingPlanSummary plan = self.getPlan();
            if (plan == null) return;
            List<CraftingPlanSummaryEntry> entries = plan.getEntries();
            if (entries == null || entries.isEmpty()) return;

            // 为缺失的条目添加 JEI 书签
            for (CraftingPlanSummaryEntry entry : entries) {
                if (entry.getMissingAmount() > 0) {
                    var what = entry.getWhat();
                    if (what instanceof AEItemKey aeItemKey) {
                        JeiRuntimeProxy.addBookmark(aeItemKey.getReadOnlyStack());
                    } else if (what instanceof AEFluidKey aeFluidKey) {
                        JeiRuntimeProxy.addBookmark(aeFluidKey.toStack(1000));
                    } else if (ModList.get().isLoaded("appmek") && ModList.get().isLoaded("mekanism")) {
                        try {
                            if (what != null) {
                                // avoid compile-time dependency on MekanismKey by reflection
                                Class<?> mekanismKeyCls = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey");
                                if (mekanismKeyCls.isInstance(what)) {
                                    java.lang.reflect.Method m = mekanismKeyCls.getMethod("getStack");
                                    Object stack = m.invoke(what);
                                    JeiRuntimeProxy.addBookmark(stack);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
