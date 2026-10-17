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

/** 将 EMI 侧边栏条目转换成 AEKey，交给共享叠加层绘制。 */
public final class EmiStockOverlay {
    // 按条目身份缓存转换结果，避免逐帧分配；达到上限后清空以限制内存占用。
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
                // 横向补齐 EMI 图标的 1px 内边距，纵向沿用共享叠加层的基线。
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
        GenericStack genericStack = EmiStackCompat.toGenericStack(stacks.get(0));
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
