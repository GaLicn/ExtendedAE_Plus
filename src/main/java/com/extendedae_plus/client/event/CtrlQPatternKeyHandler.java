package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.pattern.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import mezz.jei.api.constants.RecipeTypes;
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

        // 检查鼠标下是否为配方书签
        Optional<?> recipeBookmark = JeiRuntimeProxy.getRecipeBookmarkUnderMouse();

        if (recipeBookmark.isPresent()) {
            // 配方书签分支：处理带有配方类型的书签
            handleRecipeBookmark(recipeBookmark.get());
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
            selectedOutputs
        ));

        // 消耗事件，防止传播
        event.setCanceled(true);
    }

    /**
     * 处理配方书签的逻辑
     *
     * @param recipeBookmark 配方书签对象（RecipeBookmark<?, ?>）
     */
    private static void handleRecipeBookmark(Object recipeBookmark) {
        // 判断配方类型
        if (isCraftingRecipe(recipeBookmark)) {
            // 合成配方分支
            handleCraftingRecipeBookmark(recipeBookmark);
        } else {
            // 其他配方分支（加工配方等）
            handleProcessingRecipeBookmark(recipeBookmark);
        }
    }

    /**
     * 判断配方书签是否为能上传到装配矩阵的配方类型
     *
     * @param recipeBookmark 配方书签对象
     * @return true 如果是合成配方
     */
    private static boolean isCraftingRecipe(Object recipeBookmark) {
        try {
            // 通过反射获取 RecipeBookmark 的 recipeCategory
            var getRecipeCategoryMethod = recipeBookmark.getClass().getMethod("getRecipeCategory");
            Object recipeCategory = getRecipeCategoryMethod.invoke(recipeBookmark);

            // 获取 recipeCategory 的 recipeType
            var getRecipeTypeMethod = recipeCategory.getClass().getMethod("getRecipeType");
            Object recipeType = getRecipeTypeMethod.invoke(recipeCategory);

            // 判断是否为能上传到装配矩阵的配方类型
            return RecipeTypes.CRAFTING.equals(recipeType) || RecipeTypes.STONECUTTING.equals(recipeType) || RecipeTypes.SMITHING.equals(recipeType);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 处理合成配方书签
     *
     * @param recipeBookmark 配方书签对象
     */
    private static void handleCraftingRecipeBookmark(Object recipeBookmark) {
        try {
            // 1. 获取配方ID
            var getRecipeUidMethod = recipeBookmark.getClass().getMethod("getRecipeUid");
            net.minecraft.resources.ResourceLocation recipeId = 
                (net.minecraft.resources.ResourceLocation) getRecipeUidMethod.invoke(recipeBookmark);
            
            if (recipeId == null) {
                return;
            }
            
            // 2. 获取Minecraft实例
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            
            // 3. 验证配方存在
            var recipeManager = mc.level.getRecipeManager();
            var recipeOpt = recipeManager.byKey(recipeId);
            
            if (recipeOpt.isEmpty()) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.recipe_not_found"),
                        true
                    );
                }
                return;
            }
            
            // 4. 从JEI获取配方的详细信息（输入输出槽位）
            // 使用RecipeBookmark的getRecipe方法获取配方对象
            var getRecipeMethod = recipeBookmark.getClass().getMethod("getRecipe");
            Object recipe = getRecipeMethod.invoke(recipeBookmark);
            
            if (recipe == null) {
                return;
            }
            
            // 5. 通过JEI的RecipeManager获取配方布局信息
            mezz.jei.api.runtime.IJeiRuntime jeiRuntime = JeiRuntimeProxy.get();
            if (jeiRuntime == null) {
                return;
            }
            
            // 6. 获取配方类别
            var getRecipeCategoryMethod = recipeBookmark.getClass().getMethod("getRecipeCategory");
            Object recipeCategory = getRecipeCategoryMethod.invoke(recipeBookmark);
            
            // 7. 创建RecipeInfo来应用JEI书签优先级
            List<RecipeInfo> recipeInfos = RecipeFinderUtil.findRecipesByIngredient(
                JeiRuntimeProxy.getIngredientUnderMouse().orElse(null)
            );
            
            // 如果找不到，尝试通过配方输出物品查找
            if (recipeInfos.isEmpty()) {
                var getRecipeOutputMethod = recipeBookmark.getClass().getMethod("getRecipeOutput");
                Object recipeOutput = getRecipeOutputMethod.invoke(recipeBookmark);
                
                if (recipeOutput instanceof mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient) {
                    recipeInfos = RecipeFinderUtil.findRecipesByIngredient(typedIngredient);
                }
            }
            
            if (recipeInfos.isEmpty()) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.no_recipes_found"),
                        true
                    );
                }
                return;
            }
            
            // 8. 找到匹配的RecipeInfo
            RecipeInfo matchingRecipeInfo = null;
            for (RecipeInfo info : recipeInfos) {
                if (info.getRecipe().getId().equals(recipeId)) {
                    matchingRecipeInfo = info;
                    break;
                }
            }
            
            if (matchingRecipeInfo == null) {
                matchingRecipeInfo = recipeInfos.get(0);
            }
            
            // 9. 应用JEI书签优先级选择材料
            List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(matchingRecipeInfo);
            
            // 10. 获取输出材料
            List<ItemStack> selectedOutputs = convertOutputsToItemStacks(matchingRecipeInfo);
            
            // 11. 发送网络包创建样板并上传到装配矩阵
            ModNetwork.CHANNEL.sendToServer(new com.extendedae_plus.network.pattern.CreateAndUploadPatternC2SPacket(
                recipeId,
                matchingRecipeInfo.isCraftingRecipe(),
                selectedIngredients,
                selectedOutputs
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理加工配方书签
     *
     * @param recipeBookmark 配方书签对象
     */
    private static void handleProcessingRecipeBookmark(Object recipeBookmark) {
        System.out.println("chuli");
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
