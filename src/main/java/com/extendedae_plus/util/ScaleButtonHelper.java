package com.extendedae_plus.util;

import com.extendedae_plus.ae.client.gui.NewIcon;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 工具类：统一管理“倍增/除法”按钮的创建、注册与布局
 */
public final class ScaleButtonHelper {
    private ScaleButtonHelper() {}

    /**
     * 创建六个倍增/除法按钮
     *
     * @param handler 回调：参数 (divide, factor)
     * @return ScaleButtonSet
     */
    public static ScaleButtonSet createButtons(BiConsumer<Boolean, Integer> handler) {
        ActionEPPButton div2 = new ActionEPPButton(b -> handler.accept(true, 2), NewIcon.DIVIDE2);
        ActionEPPButton x2 = new ActionEPPButton(b -> handler.accept(false, 2), NewIcon.MULTIPLY2);
        ActionEPPButton div5 = new ActionEPPButton(b -> handler.accept(true, 5), NewIcon.DIVIDE5);
        ActionEPPButton x5 = new ActionEPPButton(b -> handler.accept(false, 5), NewIcon.MULTIPLY5);
        ActionEPPButton div10 = new ActionEPPButton(b -> handler.accept(true, 10), NewIcon.DIVIDE10);
        ActionEPPButton x10 = new ActionEPPButton(b -> handler.accept(false, 10), NewIcon.MULTIPLY10);

        for (var b : List.of(x2, div2, x5, div5, x10, div10)) {
            b.setVisibility(true);
            b.setTooltip(null);
        }

        return new ScaleButtonSet(x2, div2, x5, div5, x10, div10);
    }

    /**
     * 返回六个按钮的集合（便于注册/渲染）
     */
    public static List<ActionEPPButton> all(ScaleButtonSet set) {
        return new ArrayList<>(List.of(
                set.divide2(), set.multiply2(),
                set.divide5(), set.multiply5(),
                set.divide10(), set.multiply10()
        ));
    }

    /**
     * 在 GUI 外侧统一布局按钮
     */
    public static void layoutButtons(ScaleButtonSet set, int baseX, int baseY, int spacing, Side side) {
        int bx = baseX + (side == Side.LEFT ? -1 : 1);
        int by = baseY;

        set.divide2().setX(bx);
        set.divide2().setY(by);
        set.multiply2().setX(bx);
        set.multiply2().setY(by + spacing);
        set.divide5().setX(bx);
        set.divide5().setY(by + spacing * 2);
        set.multiply5().setX(bx);
        set.multiply5().setY(by + spacing * 3);
        set.divide10().setX(bx);
        set.divide10().setY(by + spacing * 4);
        set.multiply10().setX(bx);
        set.multiply10().setY(by + spacing * 5);
    }

    /**
     * 创建并返回集合，同时直接布局
     *
     * @param baseX   基准 X
     * @param baseY   基准 Y
     * @param spacing 间距
     * @param side    左侧/右侧
     * @param handler 点击回调
     * @return 按钮集合
     */
    public static List<ActionEPPButton> createAndLayout(int baseX, int baseY, int spacing, Side side, BiConsumer<Boolean, Integer> handler) {
        ScaleButtonSet set = createButtons(handler);
        layoutButtons(set, baseX, baseY, spacing, side);
        return all(set);
    }

    public enum Side {LEFT, RIGHT}

    public record ScaleButtonSet(
            ActionEPPButton multiply2,
            ActionEPPButton divide2,
            ActionEPPButton multiply5,
            ActionEPPButton divide5,
            ActionEPPButton multiply10,
            ActionEPPButton divide10
    ) {
    }
}
