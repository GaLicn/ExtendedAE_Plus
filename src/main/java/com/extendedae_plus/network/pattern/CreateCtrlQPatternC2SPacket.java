package com.extendedae_plus.network.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S: Ctrl+Q快速创建样板数据包
 *
 * <p>从客户端发送配方ID和选择的材料到服务器，服务器消耗空白样板并创建编码样板掉落到玩家脚下</p>
 */
public class CreateCtrlQPatternC2SPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAE Plus - CtrlQPattern");

    private final ResourceLocation recipeId;
    private final boolean isCraftingPattern;
    private final List<ItemStack> selectedIngredients;

    public CreateCtrlQPatternC2SPacket(ResourceLocation recipeId, boolean isCraftingPattern, List<ItemStack> selectedIngredients) {
        this.recipeId = recipeId;
        this.isCraftingPattern = isCraftingPattern;
        this.selectedIngredients = selectedIngredients;
    }

    public static void encode(CreateCtrlQPatternC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.recipeId);
        buf.writeBoolean(msg.isCraftingPattern);
        buf.writeInt(msg.selectedIngredients.size());
        for (ItemStack stack : msg.selectedIngredients) {
            buf.writeItem(stack);
        }
    }

    public static CreateCtrlQPatternC2SPacket decode(FriendlyByteBuf buf) {
        ResourceLocation recipeId = buf.readResourceLocation();
        boolean isCraftingPattern = buf.readBoolean();
        int count = buf.readInt();
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ingredients.add(buf.readItem());
        }
        return new CreateCtrlQPatternC2SPacket(recipeId, isCraftingPattern, ingredients);
    }

    public static void handle(CreateCtrlQPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                LOGGER.warn("[CtrlQPattern] No sender found");
                return;
            }


            // 1. 验证配方存在
            RecipeManager recipeManager = player.level().getRecipeManager();
            var recipeOpt = recipeManager.byKey(msg.recipeId);

            if (recipeOpt.isEmpty()) {
                LOGGER.error("[CtrlQPattern] Recipe not found: {}", msg.recipeId);
                player.displayClientMessage(
                    Component.translatable("message.extendedae_plus.recipe_not_found"),
                    false
                );
                return;
            }

            Recipe<?> recipe = recipeOpt.get();

            // 2. 消耗空白样板
            if (!consumeBlankPattern(player)) {
                LOGGER.warn("[CtrlQPattern] No blank pattern found in inventory");
                player.displayClientMessage(
                    Component.translatable("message.extendedae_plus.no_blank_pattern"),
                    false
                );
                return;
            }

            // 3. 创建样板
            ItemStack pattern = createPattern(recipe, msg.isCraftingPattern, msg.selectedIngredients, player);

            if (pattern.isEmpty()) {
                LOGGER.error("[CtrlQPattern] Pattern creation failed");
                // 创建失败，退还空白样板
                player.getInventory().add(AEItems.BLANK_PATTERN.stack());
                player.displayClientMessage(
                    Component.translatable("message.extendedae_plus.pattern_creation_failed"),
                    false
                );
                return;
            }

            // 4. 根据样板类型选择交付方式
            if (msg.isCraftingPattern) {
                // 合成样板：始终掉落到玩家脚下
                player.drop(pattern, false);
            } else {
                // 处理样板：优先放入背包，满了再掉落
                boolean added = player.getInventory().add(pattern);
                if (added) {
                } else {
                    player.drop(pattern, false);
                }
            }

            // 5. 移除成功消息（仅失败时提示）
        });
        ctx.setPacketHandled(true);
    }

    /**
     * 消耗玩家背包中的一个空白样板
     *
     * @param player 玩家
     * @return 是否成功消耗
     */
    private static boolean consumeBlankPattern(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        // 遍历背包查找空白样板
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(AEItems.BLANK_PATTERN.asItem())) {
                stack.shrink(1); // 消耗一个
                return true;
            }
        }

        return false; // 未找到
    }

    /**
     * 从配方创建样板
     *
     * @param recipe 配方
     * @param isCrafting 是否为合成样板
     * @param selectedIngredients 客户端选择的材料（应用JEI优先级后）
     * @param player 玩家
     * @return 编码的样板物品
     */
    private static ItemStack createPattern(Recipe<?> recipe, boolean isCrafting, List<ItemStack> selectedIngredients, ServerPlayer player) {
        try {
            if (isCrafting && recipe instanceof CraftingRecipe craftingRecipe) {
                // ===== 合成样板创建路径 =====

                // 准备9格工作台输入（3x3布局）
                ItemStack[] inputs = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    if (i < selectedIngredients.size()) {
                        inputs[i] = selectedIngredients.get(i).copy();
                    } else {
                        inputs[i] = ItemStack.EMPTY;
                    }
                }

                // 准备输出
                ItemStack output = recipe.getResultItem(player.level().registryAccess()).copy();

                // 使用 encodeCraftingPattern 创建合成样板
                // 直接传递 CraftingRecipe 对象而非 RecipeHolder
                ItemStack encodedPattern = PatternDetailsHelper.encodeCraftingPattern(
                    craftingRecipe,
                    inputs,
                    output,
                    true,  // allowSubstitutes - 允许替代材料
                    false  // allowFluidSubstitutes - 不允许流体替代
                );

                return encodedPattern;

            } else {
                // ===== 处理样板创建路径 =====

                List<GenericStack> inputs = new ArrayList<>();
                List<GenericStack> outputs = new ArrayList<>();

                // 处理输入 - 使用客户端传入的材料选择
                for (ItemStack item : selectedIngredients) {
                    if (!item.isEmpty()) {
                        inputs.add(new GenericStack(
                            AEItemKey.of(item),
                            item.getCount()
                        ));
                    }
                }

                // 处理输出
                ItemStack result = recipe.getResultItem(player.level().registryAccess());
                if (!result.isEmpty()) {
                    outputs.add(new GenericStack(
                        AEItemKey.of(result),
                        result.getCount()
                    ));
                }

                // 使用 encodeProcessingPattern 创建处理样板
                ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                    inputs.toArray(new GenericStack[0]),
                    outputs.toArray(new GenericStack[0])
                );

                return encodedPattern;
            }

        } catch (Exception e) {
            LOGGER.error("[CtrlQPattern] Exception during pattern creation", e);
            return ItemStack.EMPTY;
        }
    }
}
