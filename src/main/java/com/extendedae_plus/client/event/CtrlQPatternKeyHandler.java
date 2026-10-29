package com.extendedae_plus.client.event;

import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.pattern.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.pattern.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.ModCheckUtils;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Ctrl+Q键快速创建样板事件监听器
 *
 * <p>应用 JEI 书签优先级选择材料，优先选择工作台配方</p>
 */
public class CtrlQPatternKeyHandler {

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed event) {
        Screen screen = event.getScreen();
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        boolean isAllowSubstitutes = Screen.hasShiftDown();
        boolean isFluidSubstitutes = Screen.hasAltDown();

        // 使用 KeyMapping
        // isActiveAndMatches 才会校验修饰键（Ctrl）与冲突上下文；KeyMapping.matches 对裸键也会命中
        if (!ModKeybindings.CREATE_PATTERN_KEY.isActiveAndMatches(
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(keyCode))) {
            return;
        }

        // 双查看器仲裁：EMI 在场时由 EmiCtrlQHandler 接管（与 InputEvents 的分派优先级一致）。
        if (ModCheckUtils.isLoaded(ModCheckUtils.MODID_EMI)) {
            return;
        }

        // JEI 必须可用
        if (JeiRuntimeProxy.get() == null) {
            return;
        }

        // 检查鼠标下是否为配方书签
        Optional<RecipeBookmark<?, ?>> recipeBookmark = JeiRuntimeProxy.getRecipeBookmarkUnderMouse();

        if (recipeBookmark.isPresent()) {
            // 配方书签分支：处理带有配方类型的书签
            handleRecipeBookmark(recipeBookmark.get(),isAllowSubstitutes,isFluidSubstitutes);
            event.setCanceled(true);
            return;
        }

        // 普通书签分支：保持原有逻辑
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

        // 获取输出材料（转换为 ItemStack，流体会被包装）
        List<ItemStack> selectedOutputs = convertOutputsToItemStacks(selectedRecipeInfo);


        // 发送网络包到服务器
        ModNetwork.CHANNEL.sendToServer(new CreateCtrlQPatternC2SPacket(
            selectedRecipeInfo.getRecipe().getId(),
            selectedRecipeInfo.isCraftingRecipe(),
            selectedIngredients,
            selectedOutputs,
            false,
            isAllowSubstitutes,
            isFluidSubstitutes
        ));

        // 消耗事件，防止传播
        event.setCanceled(true);
    }

    /** 使用书签保存的准确配方与分类，避免按同名输出重新选到另一条配方。 */
    private static <R> void handleRecipeBookmark(
            RecipeBookmark<R, ?> bookmark, boolean allowSubstitutes, boolean fluidSubstitutes) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        var recipeId = bookmark.getRecipeUid();
        if (minecraft.level.getRecipeManager().byKey(recipeId).isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.recipe_not_found"), true);
            }
            return;
        }

        RecipeInfo recipeInfo = RecipeFinderUtil.getRecipeInfo(bookmark.getRecipeCategory(), bookmark.getRecipe());
        if (recipeInfo == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.no_recipes_found"), true);
            }
            return;
        }

        var recipeType = bookmark.getRecipeCategory().getRecipeType();
        boolean uploadToMatrix = RecipeTypes.CRAFTING.equals(recipeType)
                || RecipeTypes.STONECUTTING.equals(recipeType) || RecipeTypes.SMITHING.equals(recipeType);
        List<ItemStack> inputs = selectIngredientsWithJeiPriority(recipeInfo);
        List<ItemStack> outputs = convertOutputsToItemStacks(recipeInfo);
        if (uploadToMatrix) {
            RecipeTypeNameConfig.presetCraftingProviderSearchKey();
            ModNetwork.CHANNEL.sendToServer(new CreateAndUploadPatternC2SPacket(
                    recipeId, recipeInfo.isCraftingRecipe(), inputs, outputs, allowSubstitutes, fluidSubstitutes));
        } else {
            String searchKey = RecipeTypeNameConfig.resolveRecipeTypeSearchKey(
                    recipeType.getUid(), bookmark.getRecipeCategory().getTitle().getString());
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
            ModNetwork.CHANNEL.sendToServer(new CreateCtrlQPatternC2SPacket(
                    recipeId, recipeInfo.isCraftingRecipe(), inputs, outputs, true,
                    allowSubstitutes, fluidSubstitutes));
        }
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
        int index = 0;

        // 构建优先级映射 (数值越小 = 优先级越高)
        for (ITypedIngredient<?> ingredient : bookmarks) {
            var item = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
            if (item.isPresent()) {
                // RecipeInfo 取最小优先级，书签顺序越靠前越优先。
                priorities.putIfAbsent(item.get().getItem(), index++);
            }
        }

        // 使用 RecipeInfo 的方法选择最佳输入
        return recipeInfo.selectBestInputs(priorities);
    }

    /**
     * 将配方输出转换为 ItemStack 列表（用于网络传输）
     *
     * <p>物品直接转换，流体会被包装为 GenericStack.wrapInItemStack</p>
     *
     * @param recipeInfo 配方信息
     * @return ItemStack 列表（流体已包装）
     */
    private static List<ItemStack> convertOutputsToItemStacks(RecipeInfo recipeInfo) {
        return recipeInfo.getOutputs().stream()
            .map(genericStack -> {
                if (genericStack.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                    return itemKey.toStack((int) genericStack.amount());
                } else {
                    // 流体或其他类型，使用包装
                    return appeng.api.stacks.GenericStack.wrapInItemStack(genericStack);
                }
            })
            .toList();
    }
}
