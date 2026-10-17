package com.extendedae_plus.compat.emi;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.compat.NetworkStockOverlay;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * EMI 侧边栏的「AE2 网络存量 + 可合成」叠加（对应 JEI 的 {@code IngredientListOverlayHelper}）。
 * <p>
 * 条目枚举由 {@code EmiScreenManagerScreenSpaceMixin} 完成，本类只负责把 EMI 条目解析成
 * {@link AEKey} 并交给 {@link NetworkStockOverlay} 绘制。EMI 侧边栏（索引 / 收藏 / 查询历史等）
 * 在 {@code StackBatcher} 中统一剥掉了 {@code RENDER_AMOUNT}，因此自身不画数量，叠加不会撞字；
 * 例外是带配方的收藏项（EMI 在右下角画批量数、右上角画配方角标），这些条目直接跳过。
 */
public final class EmiStockOverlay {
    /**
     * 条目 → AEKey 的缓存。EMI 侧边栏条目是长生命周期对象，逐帧重新走转换器会为每个可见槽位
     * 分配 ItemStack / AEKey；以身份为键缓存后每帧只剩哈希查找。条目列表更新后旧键自然失效，
     * 超过上限时整体清空即可（不做 LRU，避免不必要的复杂度）。
     */
    private static final int MAX_CACHED_KEYS = 4096;
    private static final Map<EmiIngredient, AEKey> KEY_CACHE = new IdentityHashMap<>();

    private EmiStockOverlay() {
    }

    /** 在侧边栏渲染末尾叠加数量与可合成标识，{@code startIndex} 与 EMI 自身的分页起点一致。 */
    public static void render(GuiGraphics guiGraphics, EmiScreenManager.ScreenSpace space, int startIndex) {
        if (!NetworkStockOverlay.isEnabled()) {
            return;
        }
        List<? extends EmiIngredient> stacks = space.getStacks();
        int index = startIndex;
        outer:
        for (int row = 0; row < space.th; row++) {
            for (int column = 0; column < space.getWidth(row); column++) {
                if (index >= stacks.size()) {
                    break outer;
                }
                EmiIngredient ingredient = stacks.get(index++);
                AEKey key = toKey(ingredient);
                if (key == null) {
                    continue;
                }
                // EMI 的图标绘制在 (槽位+1, 槽位+1)，自身数量文字以图标左上角为锚点（右边缘 = 图标x+17）；
                // 横向 +1 与之对齐，否则整体偏左 1px。纵向沿用叠加层自身基线，实测位置正确。
                NetworkStockOverlay.renderSlot(guiGraphics, key,
                        space.getX(column, row) + 1, space.getY(column, row));
            }
        }
    }

    private static AEKey toKey(EmiIngredient ingredient) {
        if (ingredient.isEmpty() || ingredient instanceof EmiFavorite favorite && favorite.getRecipe() != null) {
            // 配方收藏自带右下角批量数与右上角配方角标，再叠库存会互相遮挡。
            return null;
        }
        AEKey cached = KEY_CACHE.get(ingredient);
        if (cached != null) {
            return cached;
        }
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.size() != 1) {
            // 标签等多项条目无法对应唯一 AEKey，跳过（不缓存 null，避免条目类型变化后误判）。
            return null;
        }
        GenericStack genericStack = EmiRecipeCompat.toGenericStack(stacks.getFirst());
        if (genericStack == null) {
            return null;
        }
        if (KEY_CACHE.size() >= MAX_CACHED_KEYS) {
            KEY_CACHE.clear();
        }
        KEY_CACHE.put(ingredient, genericStack.what());
        return genericStack.what();
    }
}
