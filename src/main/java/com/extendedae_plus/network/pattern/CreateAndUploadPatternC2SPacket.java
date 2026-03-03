package com.extendedae_plus.network.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.util.uploadPattern.MatrixUploadUtil;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S: 创建样板并上传到装配矩阵
 *
 * <p>从客户端发送配方ID、选择的材料和输出到服务器，服务器消耗空白样板并创建编码样板，然后直接上传到装配矩阵</p>
 */
public class CreateAndUploadPatternC2SPacket {

    private final ResourceLocation recipeId;
    private final boolean isCraftingPattern;
    private final List<ItemStack> selectedIngredients;
    private final List<ItemStack> outputs;
    private final boolean isAllowSubstitutes;
    private final boolean isFluidSubstitutes;

    public CreateAndUploadPatternC2SPacket(ResourceLocation recipeId, boolean isCraftingPattern, List<ItemStack> selectedIngredients, List<ItemStack> outputs, boolean isAllowSubstitutes, boolean isFluidSubstitutes) {
        this.recipeId = recipeId;
        this.isCraftingPattern = isCraftingPattern;
        this.selectedIngredients = selectedIngredients;
        this.outputs = outputs;
        this.isAllowSubstitutes = isAllowSubstitutes;
        this.isFluidSubstitutes = isFluidSubstitutes;
    }

    public static void encode(CreateAndUploadPatternC2SPacket msg, FriendlyByteBuf buf) {
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
        buf.writeBoolean(msg.isAllowSubstitutes);
        buf.writeBoolean(msg.isFluidSubstitutes);
    }

    public static CreateAndUploadPatternC2SPacket decode(FriendlyByteBuf buf) {
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
        boolean isAllowSubstitutes = buf.readBoolean();
        boolean isFluidSubstitutes = buf.readBoolean();
        return new CreateAndUploadPatternC2SPacket(recipeId, isCraftingPattern, ingredients, outputs,isAllowSubstitutes,isFluidSubstitutes);
    }

    public static void handle(CreateAndUploadPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
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

            // 2. 获取AE网络
            IGrid grid = getPlayerGrid(player);
            if (grid == null) {
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.no_network"),
                        false
                );
                return;
            }

            // 3. 消耗空白样板
            if (!consumeBlankPattern(player, grid)) {
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.no_blank_pattern"),
                        false
                );
                return;
            }

            // 4. 创建样板
            ItemStack pattern = createPattern(recipe, msg.isCraftingPattern, msg.selectedIngredients, msg.outputs,msg.isAllowSubstitutes,msg.isFluidSubstitutes, player);

            if (pattern.isEmpty()) {
                // 创建失败，退还空白样板到网络
                refundBlankPattern(player, grid);
                player.displayClientMessage(
                        Component.translatable("message.extendedae_plus.pattern_creation_failed"),
                        false
                );
                return;
            }

            // 5. 上传样板到装配矩阵
            boolean uploaded = MatrixUploadUtil.uploadPatternToMatrix(player, pattern, grid);

            if (!uploaded) {
                // 上传失败，退还空白样板到网络
                refundBlankPattern(player, grid);
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * 获取玩家的AE网络
     */
    private static IGrid getPlayerGrid(ServerPlayer player) {
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        ItemStack terminal = located.stack;
        if (terminal.isEmpty()) {
            return null;
        }

        String curiosSlotId = located.getCuriosSlotId();
        int curiosIndex = located.getCuriosIndex();

        if (curiosSlotId != null && curiosIndex >= 0) {
            try {
                String current = WUTHandler.getCurrentTerminal(terminal);
                WTDefinition def = WUTHandler.wirelessTerminals.get(current);
                if (def != null) {
                    WTMenuHost wtHost = def.wTMenuHostFactory().create(player, null, terminal, (p, sub) -> {});
                    if (wtHost != null) {
                        IGridNode node = wtHost.getActionableNode();
                        if (node != null) {
                            return node.getGrid();
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        } else {
            WirelessTerminalItem wt = terminal.getItem() instanceof WirelessTerminalItem t ? t : null;
            if (wt != null) {
                return wt.getLinkedGrid(terminal, player.serverLevel(), player);
            }
        }

        return null;
    }

    /**
     * 消耗空白样板：优先从AE网络提取
     */
    private static boolean consumeBlankPattern(ServerPlayer player, IGrid grid) {
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack());
        IEnergyService energy = grid.getEnergyService();
        MEStorage storage = grid.getStorageService().getInventory();

        long extracted = StorageHelper.poweredExtraction(
                energy,
                storage,
                blankPatternKey,
                1,
                new PlayerSource(player)
        );

        return extracted > 0;
    }

    /**
     * 退还空白样板到网络
     */
    private static void refundBlankPattern(ServerPlayer player, IGrid grid) {
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack());
        IEnergyService energy = grid.getEnergyService();
        MEStorage storage = grid.getStorageService().getInventory();

        StorageHelper.poweredInsert(
                energy,
                storage,
                blankPatternKey,
                1,
                new PlayerSource(player)
        );
    }

    /**
     * 从配方创建样板
     */
    private static ItemStack createPattern(Recipe<?> recipe, boolean isCrafting, List<ItemStack> selectedIngredients, List<ItemStack> selectedOutputs, boolean isAllowSubstitutes, boolean isFluidSubstitutes, ServerPlayer player) {
        try {
            if (isCrafting && recipe instanceof CraftingRecipe craftingRecipe) {
                // 合成样板
                ItemStack[] inputs = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    if (i < selectedIngredients.size()) {
                        inputs[i] = selectedIngredients.get(i).copy();
                    } else {
                        inputs[i] = ItemStack.EMPTY;
                    }
                }

                ItemStack output = recipe.getResultItem(player.level().registryAccess()).copy();

                ItemStack encodedPattern = PatternDetailsHelper.encodeCraftingPattern(
                        craftingRecipe,
                        inputs,
                        output,
                        isAllowSubstitutes,
                        isFluidSubstitutes
                );

                encodedPattern.getOrCreateTag().putString("encodePlayer", player.getName().getString());
                return encodedPattern;

            } else {
                // 处理样板
                List<GenericStack> inputs = new ArrayList<>();
                List<GenericStack> outputs = new ArrayList<>();

                for (ItemStack item : selectedIngredients) {
                    if (!item.isEmpty()) {
                        GenericStack genericStack = GenericStack.unwrapItemStack(item);
                        if (genericStack != null) {
                            inputs.add(genericStack);
                        } else {
                            AEItemKey itemKey = AEItemKey.of(item);
                            if (itemKey != null) {
                                inputs.add(new GenericStack(itemKey, item.getCount()));
                            }
                        }
                    }
                }

                for (ItemStack item : selectedOutputs) {
                    if (!item.isEmpty()) {
                        GenericStack genericStack = GenericStack.unwrapItemStack(item);
                        if (genericStack != null) {
                            outputs.add(genericStack);
                        } else {
                            AEItemKey itemKey = AEItemKey.of(item);
                            if (itemKey != null) {
                                outputs.add(new GenericStack(itemKey, item.getCount()));
                            }
                        }
                    }
                }

                ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                        inputs.toArray(new GenericStack[0]),
                        outputs.toArray(new GenericStack[0])
                );

                encodedPattern.getOrCreateTag().putString("encodePlayer", player.getName().getString());
                return encodedPattern;
            }

        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
