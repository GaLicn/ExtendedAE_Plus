package com.extendedae_plus.util.crafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * 将请求键解析为网络中可合成的实际输出键。
 *
 * <p>{@link AEKey} 的相等性包含物品数据。JEI 条目与样板输出可能因方块实体数据等
 * 信息不一致，使 {@link ICraftingService#isCraftable(AEKey)} 的精确匹配失败。
 * 精确匹配失败时使用 {@link ICraftingService#getFuzzyCraftable} 解析变体；
 * 不可损坏物品的变体索引可能返回同一主键下的全部记录，因此过滤器必须校验可合成性。
 * 该解析用于下单目标，不用于库存数量或物品提取。</p>
 */
public final class CraftableKeyResolver {
    private CraftableKeyResolver() {
    }

    /**
     * 解析可合成输出键。
     *
     * @return 精确可合成键或变体对应的可合成键；无匹配项时返回 {@code null}
     */
    @Nullable
    public static AEKey resolve(@Nullable ICraftingService crafting, @Nullable AEKey what) {
        if (crafting == null || what == null) {
            return null;
        }
        if (crafting.isCraftable(what)) {
            return what;
        }
        return crafting.getFuzzyCraftable(what, crafting::isCraftable);
    }
}
