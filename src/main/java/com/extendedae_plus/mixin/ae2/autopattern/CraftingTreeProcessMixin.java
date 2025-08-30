package com.extendedae_plus.mixin.ae2.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.service.CraftingService;
import com.extendedae_plus.api.SmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.content.ScaledProcessingPattern;
import com.extendedae_plus.util.PatternScaler;
import com.extendedae_plus.util.RequestedAmountHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.stream.StreamSupport;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * 注入 CraftingTreeProcess 构造器尾部：将 AEProcessingPattern 替换为 ScaledProcessingPattern
 * 以确保后续执行使用放大后的输入/输出视图。
 */
@Mixin(CraftingTreeProcess.class)
public abstract class CraftingTreeProcessMixin {

    @ModifyVariable(
            method = "<init>(Lappeng/api/networking/crafting/ICraftingService;Lappeng/crafting/CraftingCalculation;Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/CraftingTreeNode;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static IPatternDetails eap$replaceDetailsAtHead(IPatternDetails original, ICraftingService cc, CraftingCalculation job, IPatternDetails details, CraftingTreeNode craftingTreeNode) {
        try {
            // 若传入的 details 已经是缩放样板，且原始样板不允许缩放，则直接解包为原始样板
            if (details instanceof ScaledProcessingPattern sp) {
                var proc0 = sp.getOriginal();
                if (proc0 instanceof SmartDoublingAwarePattern aware0 && !aware0.eap$allowScaling()) {
                    return proc0;
                }
            }

            if (!(details instanceof AEProcessingPattern proc)) return original;

            // 若样板标记为不允许缩放，则直接跳过
            if (proc instanceof SmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
                return original;
            }

            CraftingTreeNodeAccessor parentAcc = (CraftingTreeNodeAccessor) craftingTreeNode;
            AEKey parentTarget = parentAcc.eap$getWhat();
            long requested = RequestedAmountHolder.get();

            // 根据配置决定是否在 provider 间轮询分配请求量（默认开启）
            long perProvider = 1L;
            if (!ModConfigs.PROVIDER_ROUND_ROBIN_ENABLE.get()) {
                // 关闭轮询：直接使用完整请求量，不需要查询 provider 列表
                perProvider = requested;
                if (perProvider <= 0) perProvider = 1L;
            } else {
                CraftingService craftingService = (CraftingService) cc;
                Iterable<ICraftingProvider> providers = craftingService.getProviders(original);

                // 计算 provider 数量；避免直接反射目标类字段，优先使用 mixin Accessor 暴露的列表
                int size;
                try {
                    // 使用 NetworkCraftingProvidersAccessor（通过 Mixin 生成）来获取内部列表
                    var acc = (NetworkCraftingProvidersAccessor) providers;
                    var list = acc.eap$getProvidersList();
                    size = list == null ? 0 : list.size();
                } catch (ClassCastException ccx) {
                    // 访问器不可用：回退为遍历计数（会消费迭代器）
                    size = (int) StreamSupport.stream(providers.spliterator(), false).count();
                }

                // 将 requested 在 providers 间均分，向上取整保证每个 provider 分配整数且总量不少于 requested
                if (size > 0) {
                    perProvider = requested / size + ((requested % size) == 0 ? 0 : 1);
                    if (perProvider <= 0) perProvider = 1L;
                }
            }

            // 使用每-provider 的分配量来缩放样板
            var scaled = PatternScaler.scale(proc, parentTarget, perProvider);
            return scaled != null ? scaled : original;
        } catch (Exception e) {
            LOGGER.warn("构建倍增样板出错", e);
            e.printStackTrace();
            return original;
        }
    }
}
