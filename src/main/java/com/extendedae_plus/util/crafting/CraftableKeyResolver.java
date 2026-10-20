package com.extendedae_plus.util.crafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * 把「客户端传来的 key」解析成「网络里真正可合成的 key」。
 *
 * <p>{@link AEKey} 的相等性包含数据组件，而 JEI 条目转出来的 key 未必与样板
 * 输出的 key 组件完全一致（例如被拆装过的方块物品带有方块实体数据）。此时
 * {@link ICraftingService#isCraftable(AEKey)} 的精确匹配会失败，表现为物品列表
 * 上既没有可制作标记、中键下单也静默无反应。
 *
 * <p>AE2 自身在合成树与配方填充里就是用 {@link ICraftingService#getFuzzyCraftable}
 * 处理这种情况的，这里沿用同一套语义：精确命中优先，否则回退到同一物品 / 流体
 * 的其它变体。
 */
public final class CraftableKeyResolver {
    private CraftableKeyResolver() {
    }

    /**
     * 解析出实际可合成的 key。
     *
     * @return 精确匹配的 key；精确不可合成时返回同一主键下的可合成变体；都不成立时返回 {@code null}
     */
    @Nullable
    public static AEKey resolve(@Nullable ICraftingService crafting, @Nullable AEKey what) {
        if (crafting == null || what == null) {
            return null;
        }
        if (crafting.isCraftable(what)) {
            return what;
        }
        // 精确 key 不可合成：回退到同一物品 / 流体的其它组件变体。
        // 过滤器必须真的校验可合成性，否则会返回一个不可合成的变体。
        return crafting.getFuzzyCraftable(what, crafting::isCraftable);
    }
}