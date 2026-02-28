package com.extendedae_plus.network.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.extendedae_plus.util.wireless.WirelessTerminalLocator;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
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

/**
 * C2S: Ctrl+Q快速创建样板数据包
 *
 * <p>
 * 从客户端发送配方ID、选择的材料和输出到服务器，服务器消耗空白样板并创建编码样板掉落到玩家脚下</p>
 */
public class CreateCtrlQPatternC2SPacket {

    private final ResourceLocation recipeId;
    private final boolean isCraftingPattern;
    private final List<ItemStack> selectedIngredients;
    private final List<ItemStack> outputs;  // 输出材料（物品或包装的流体）

    public CreateCtrlQPatternC2SPacket(ResourceLocation recipeId, boolean isCraftingPattern, List<ItemStack> selectedIngredients, List<ItemStack> outputs) {
        this.recipeId = recipeId;
        this.isCraftingPattern = isCraftingPattern;
        this.selectedIngredients = selectedIngredients;
        this.outputs = outputs;
    }

    public static void encode(CreateCtrlQPatternC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.recipeId);
        buf.writeBoolean(msg.isCraftingPattern);
        buf.writeInt(msg.selectedIngredients.size());
        for (ItemStack stack : msg.selectedIngredients) {
            buf.writeItem(stack);
        }
        buf.writeInt(msg.outputs.size());
        for (ItemStack stack : msg.outputs) {
            buf.writeItem(stack);
        }
    }

    public static CreateCtrlQPatternC2SPacket decode(FriendlyByteBuf buf) {
        ResourceLocation recipeId = buf.readResourceLocation();
        boolean isCraftingPattern = buf.readBoolean();
        int ingredientCount = buf.readInt();
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.add(buf.readItem());
        }
        int outputCount = buf.readInt();
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < outputCount; i++) {
            outputs.add(buf.readItem());
        }
        return new CreateCtrlQPatternC2SPacket(recipeId, isCraftingPattern, ingredients, outputs);
    }

    public static void handle(CreateCtrlQPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            // 1. 验证配方存在
            RecipeManager recipeManager = player.level().getRecipeManager();
            var recipeOpt = recipeManager.byKey(msg.recipeId);

            if (recipeOpt.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.recipe_not_found"),
                        false
                );
                return;
            }

            Recipe<?> recipe = recipeOpt.get();

            // 2. 消耗空白样板
            if (!consumeBlankPattern(player)) {
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.no_blank_pattern"),
                        false
                );
                return;
            }

            // 3. 创建样板
            ItemStack pattern = createPattern(recipe, msg.isCraftingPattern, msg.selectedIngredients, msg.outputs, player);

            if (pattern.isEmpty()) {
                // 创建失败，退还空白样板
                player.getInventory().add(AEItems.BLANK_PATTERN.stack());
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.pattern_creation_failed"),
                        false
                );
                return;
            }

            // 4. 交付样板：优先放入背包，满了再掉落
            if (!player.getInventory().add(pattern)) {
                player.drop(pattern, false);
            }

        });
        ctx.setPacketHandled(true);
    }

    /**
     * 消耗空白样板：优先从AE网络提取，网络无货才从玩家背包消耗
     *
     * @param player 玩家
     * @return 是否成功消耗
     */
    private static boolean consumeBlankPattern(ServerPlayer player) {
        // 1. 尝试从AE网络提取（需要玩家持有无线终端）
        if (tryExtractFromNetwork(player)) {
            return true;
        }

        // 2. 网络提取失败，从背包消耗
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(AEItems.BLANK_PATTERN.asItem())) {
                stack.shrink(1);
                return true;
            }
        }

        return false; // 未找到
    }

    /**
     * 尝试从AE网络提取空白样板
     *
     * @param player 玩家
     * @return 是否成功提取
     */
    private static boolean tryExtractFromNetwork(ServerPlayer player) {
        // 定位玩家身上的无线终端
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        ItemStack terminal = located.stack;
        if (terminal.isEmpty()) {
            return false; // 没有无线终端
        }

        IGrid grid;
        boolean usedWtHost;

        // 若来自 Curios：优先通过 ae2wtlib 的 WTMenuHost 获取量子桥网络
        String curiosSlotId = located.getCuriosSlotId();
        int curiosIndex = located.getCuriosIndex();

        if (curiosSlotId != null && curiosIndex >= 0) {
            try {
                String current = WUTHandler.getCurrentTerminal(terminal);
                WTDefinition def = WUTHandler.wirelessTerminals.get(current);
                if (def != null) {
                    WTMenuHost wtHost = def.wTMenuHostFactory().create(player, null, terminal, (p, sub) -> {
                    });
                    if (wtHost != null) {
                        var node = wtHost.getActionableNode();
                        if (node != null) {
                            grid = node.getGrid();
                            if (grid != null && wtHost.drainPower()) {
                                usedWtHost = true;
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        } else {
            // 非 Curios：按 AE2 原生路径处理
            WirelessCraftingTerminalItem wct = terminal.getItem() instanceof WirelessCraftingTerminalItem c ? c : null;
            WirelessTerminalItem wt = wct != null ? wct : (terminal.getItem() instanceof WirelessTerminalItem t ? t : null);
            if (wt == null) {
                return false;
            }
            grid = wt.getLinkedGrid(terminal, player.serverLevel(), player);
            if (grid == null) {
                return false;
            }
            if (!wt.hasPower(player, 0.5, terminal)) {
                return false; // 能量不足
            }
            usedWtHost = false;
        }

        // 从网络提取空白样板
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack());
        IEnergyService energy = grid.getEnergyService();
        MEStorage storage = grid.getStorageService().getInventory();

        long extracted = StorageHelper.poweredExtraction(
                energy,
                storage,
                blankPatternKey,
                1, // 只提取1个
                new PlayerSource(player)
        );

        if (extracted > 0) {
            // 提取成功，消耗无线终端能量
            if (usedWtHost) {
                // WTMenuHost 已在 drainPower 中处理能量消耗
            } else {
                // 原生 AE2 扣能
                WirelessCraftingTerminalItem wct2 = terminal.getItem() instanceof WirelessCraftingTerminalItem c2 ? c2 : null;
                WirelessTerminalItem wt2 = wct2 != null ? wct2 : (terminal.getItem() instanceof WirelessTerminalItem t2 ? t2 : null);
                if (wt2 != null) {
                    wt2.usePower(player, 0.5, terminal);
                }
            }
            // 确保写回终端（若位于 Curios 等需要显式写回的容器）
            located.commit();
            return true;
        }

        return false; // 网络中没有空白样板
    }

    /**
     * 从配方创建样板（支持物品和流体）
     *
     * @param recipe 配方
     * @param isCrafting 是否为合成样板
     * @param selectedIngredients 客户端选择的材料（应用JEI优先级后，流体已包装为 GenericStack.wrapInItemStack）
     * @param selectedOutputs 客户端传递的输出材料（物品或包装的流体）
     * @param player 玩家
     * @return 编码的样板物品
     */
    private static ItemStack createPattern(Recipe<?> recipe, boolean isCrafting, List<ItemStack> selectedIngredients, List<ItemStack> selectedOutputs, ServerPlayer player) {
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
                        true, // allowSubstitutes - 允许替代材料
                        false // allowFluidSubstitutes - 不允许流体替代
                );

                // 添加编码玩家信息到NBT
                encodedPattern.getOrCreateTag().putString("encodePlayer", player.getName().getString());

                return encodedPattern;

            } else {
                // ===== 处理样板创建路径（支持物品和流体）=====

                List<GenericStack> inputs = new ArrayList<>();
                List<GenericStack> outputs = new ArrayList<>();

                // 处理输入 - 使用客户端传入的材料选择（支持流体）
                for (ItemStack item : selectedIngredients) {
                    if (!item.isEmpty()) {
                        // 尝试解包 GenericStack（流体会被包装在特殊的 ItemStack 中）
                        GenericStack genericStack = GenericStack.unwrapItemStack(item);
                        if (genericStack != null) {
                            // 这是一个包装的 GenericStack（可能是流体）
                            inputs.add(genericStack);
                        } else {
                            // 普通物品
                            AEItemKey itemKey = AEItemKey.of(item);
                            if (itemKey != null) {
                                inputs.add(new GenericStack(itemKey, item.getCount()));
                            }
                        }
                    }
                }

                // 处理输出 - 使用客户端传入的输出（支持流体）
                for (ItemStack item : selectedOutputs) {
                    if (!item.isEmpty()) {
                        // 尝试解包 GenericStack（流体会被包装在特殊的 ItemStack 中）
                        GenericStack genericStack = GenericStack.unwrapItemStack(item);
                        if (genericStack != null) {
                            // 这是一个包装的 GenericStack（可能是流体）
                            outputs.add(genericStack);
                        } else {
                            // 普通物品
                            AEItemKey itemKey = AEItemKey.of(item);
                            if (itemKey != null) {
                                outputs.add(new GenericStack(itemKey, item.getCount()));
                            }
                        }
                    }
                }

                // 使用 encodeProcessingPattern 创建处理样板
                ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                        inputs.toArray(new GenericStack[0]),
                        outputs.toArray(new GenericStack[0])
                );

                // 添加编码玩家信息到NBT
                encodedPattern.getOrCreateTag().putString("encodePlayer", player.getName().getString());

                return encodedPattern;
            }

        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
