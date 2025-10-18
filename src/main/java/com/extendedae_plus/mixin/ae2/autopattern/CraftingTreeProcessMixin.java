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
import com.extendedae_plus.ae.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import com.extendedae_plus.util.smartDoubling.RequestedAmountHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.stream.StreamSupport;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 注入 CraftingTreeProcess 构造器尾部：将 AEProcessingPattern 替换为 ScaledProcessingPattern
 * 以确保后续执行使用放大后的输入/输出视图。
 */
@Mixin(CraftingTreeProcess.class)
public abstract class CraftingTreeProcessMixin {

    @ModifyVariable(
            method = "<init>(Lappeng/api/networking/crafting/ICraftingService;Lappeng/crafting/CraftingCalculation;Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/CraftingTreeNode;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false
    )
    private static IPatternDetails eap$replaceDetailsAtHead(IPatternDetails original,
                                                            ICraftingService cc,
                                                            CraftingCalculation job,
                                                            IPatternDetails details,
                                                            CraftingTreeNode craftingTreeNode) {
        try {
            // 若传入的 details 已经是缩放样板，且原始样板不允许缩放，则直接解包为原始样板
            if (details instanceof ScaledProcessingPattern sp) {
                var originalPattern = sp.getOriginal();
                if (originalPattern instanceof ISmartDoublingAwarePattern scalingAwarePattern && !scalingAwarePattern.eap$allowScaling()) {
                    return originalPattern;
                }
            }

            if (!(details instanceof AEProcessingPattern processingPattern)) return original;

            // 若样板标记为不允许缩放，则直接跳过
            if (processingPattern instanceof ISmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
                return original;
            }

            CraftingTreeNodeAccessor parentAcc = (CraftingTreeNodeAccessor) craftingTreeNode;
            AEKey parentTarget = parentAcc.eap$getWhat();
            long requested = RequestedAmountHolder.get();
            RequestedAmountHolder.pop();

            // 根据配置决定是否在 provider 间轮询分配请求量（默认开启）
            long perProvider = 1L;
            if (!ModConfig.INSTANCE.providerRoundRobinEnable) {
                // 关闭轮询：直接使用完整请求量，不需要查询 provider 列表
                perProvider = requested;
                if (perProvider <= 0) perProvider = 1L;
            } else {
                CraftingService craftingService = (CraftingService) cc;
                Iterable<ICraftingProvider> providers = craftingService.getProviders(original);

                // 计算 provider 数量；尝试用反射读取内部 providers 列表以避免消费迭代器
                int size;
                try {
                    var cls = providers.getClass();
                    var f = cls.getDeclaredField("providers"); // private ArrayList<ICraftingProvider>
                    f.setAccessible(true);
                    List<?> list = (List<?>) f.get(providers);
                    size = list == null ? 0 : list.size();
                } catch (Exception ex) {
                    // 反射失败回退为遍历计数（会消费迭代器）
                    size = (int) StreamSupport.stream(providers.spliterator(), false).count();
                }

                // 将 requested 在 providers 间均分，向上取整保证每个 provider 分配整数且总量不少于 requested
                if (size > 0) {
                    perProvider = requested / size + ((requested % size) == 0 ? 0 : 1);
                    if (perProvider <= 0) perProvider = 1L;
                }
            }

            // 使用每-provider 的分配量来缩放样板
            var scaled = PatternScaler.scale(processingPattern, parentTarget, perProvider);
            return scaled != null ? scaled : original;
        } catch (Exception e) {
            EAP$LOGGER.warn("构建倍增样板出错", e);
            e.printStackTrace();
            return original;
        }
    }
}
