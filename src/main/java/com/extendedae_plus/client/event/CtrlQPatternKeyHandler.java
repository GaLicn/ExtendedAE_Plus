package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.pattern.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

        // 查找相关配方（使用新的 API，包含完整数量信息）
        Minecraft mc = Minecraft.getInstance();
        List<RecipeInfo> recipes = RecipeFinderUtil.findRecipesByIngredient(
            ingredient.get()
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

        // 自动选择最佳配方（优先工作台配方）
        RecipeInfo selectedRecipeInfo = RecipeFinderUtil.selectBestRecipe(recipes);
        if (selectedRecipeInfo == null) {
            return;
        }

        // 应用JEI书签优先级选择材料
        List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(selectedRecipeInfo);

        // 发送网络包到服务器
        ModNetwork.CHANNEL.sendToServer(new CreateCtrlQPatternC2SPacket(
            selectedRecipeInfo.getRecipe().getId(),
            selectedRecipeInfo.isCraftingRecipe(),
            selectedIngredients
        ));

        // 消耗事件，防止传播
        event.setCanceled(true);
    }

    /**
     * 应用JEI书签优先级选择配方材料
     *
     * <p>对配方的每个输入槽位，选择 JEI 书签中优先级最高的物品</p>
     * <p>如果没有在书签中，则使用槽位的第一个物品</p>
     *
     * @param recipeInfo 配方信息（包含完整的输入输出数量）
     * @return 选择的材料列表
     */
    private static List<ItemStack> selectIngredientsWithJeiPriority(RecipeInfo recipeInfo) {
        // 获取JEI书签列表并构建优先级映射
        List<? extends ITypedIngredient<?>> bookmarks = JeiRuntimeProxy.getBookmarkList();
        Map<Item, Integer> priorities = new HashMap<>();
        AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);

        // 构建优先级映射 (数值越小 = 优先级越高)
        for (ITypedIngredient<?> ingredient : bookmarks) {
            ingredient.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(itemStack ->
                priorities.put(itemStack.getItem(), index.getAndDecrement())
            );
        }

        // 使用 RecipeInfo 的方法选择最佳输入
        return recipeInfo.selectBestInputs(priorities);
    }
}
