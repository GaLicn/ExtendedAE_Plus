package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.pattern.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ctrl+Q键快速创建样板事件监听器
 *
 * <p>监听 Ctrl+Q 组合键，自动创建样板并掉落到玩家脚下</p>
 * <p>应用 JEI 书签优先级选择材料，优先选择工作台配方</p>
 */
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public class CtrlQPatternKeyHandler {

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed event) {
        Screen screen = event.getScreen();
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        // 使用 KeyMapping 检测按键（包含修饰键）
        if (!ModKeybindings.CREATE_PATTERN_KEY.matches(keyCode, scanCode)) {
            return;
        }

        // JEI 必须可用
        if (JeiRuntimeProxy.get() == null) {
            return;
        }

        // 获取鼠标悬浮的物品
        Optional<ITypedIngredient<?>> ingredient = JeiRuntimeProxy.getIngredientUnderMouse();

        if (ingredient.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.translatable("message.extendedae_plus.hover_item_first"),
                    true
                );
            }
            return;
        }

        // 查找相关配方
        Minecraft mc = Minecraft.getInstance();
        List<Recipe<?>> recipes = RecipeFinderUtil.findRecipesByIngredient(
            ingredient.get(),
            mc.level
        );

        if (recipes.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.translatable("message.extendedae_plus.no_recipes_found"),
                    true
                );
            }
            return;
        }

        // 自动选择最佳配方（优先CraftingRecipe）
        Recipe<?> selectedRecipe = RecipeFinderUtil.selectBestRecipe(recipes);
        if (selectedRecipe == null) {
            return;
        }

        boolean isCraftingPattern = selectedRecipe instanceof CraftingRecipe;

        // 应用JEI书签优先级选择材料
        List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(selectedRecipe);

        // 发送网络包到服务器
        ModNetwork.CHANNEL.sendToServer(new CreateCtrlQPatternC2SPacket(
            selectedRecipe.getId(),
            isCraftingPattern,
            selectedIngredients
        ));

        // 消耗事件，防止传播
        event.setCanceled(true);
    }

    /**
     * 应用JEI书签优先级选择配方材料
     *
     * <p>对配方的每个 Ingredient，选择 JEI 书签中优先级最高的物品</p>
     * <p>如果没有在书签中，则使用配方默认的第一个物品</p>
     *
     * @param recipe 配方
     * @return 选择的材料列表
     */
    private static List<ItemStack> selectIngredientsWithJeiPriority(Recipe<?> recipe) {
        // 获取JEI书签列表并构建优先级映射
        List<? extends ITypedIngredient<?>> bookmarks = JeiRuntimeProxy.getBookmarkList();
        Map<AEKey, Integer> priorities = new HashMap<>();
        AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);

        // 构建优先级映射 (数值越小 = 优先级越高，与EncodingHelperMixin逻辑一致)
        for (ITypedIngredient<?> ingredient : bookmarks) {
            ingredient.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(itemStack ->
                priorities.put(AEItemKey.of(itemStack), index.getAndDecrement())
            );
        }

        List<ItemStack> selected = new ArrayList<>();

        // 对每个 ingredient 选择优先级最高的物品
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                selected.add(ItemStack.EMPTY);
                continue;
            }

            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                selected.add(ItemStack.EMPTY);
                continue;
            }

            // 选择优先级最高的 (如果都不在书签中，选第一个)
            ItemStack best = items[0];
            int bestPriority = Integer.MAX_VALUE;

            // 检查第一个物品的优先级
            AEKey firstKey = AEItemKey.of(best);
            if (priorities.containsKey(firstKey)) {
                bestPriority = priorities.get(firstKey);
            }

            // 遍历其他选项
            for (int i = 1; i < items.length; i++) {
                AEKey key = AEItemKey.of(items[i]);
                int priority = priorities.getOrDefault(key, Integer.MAX_VALUE);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    best = items[i];
                }
            }

            selected.add(best.copy());
        }

        return selected;
    }
}
