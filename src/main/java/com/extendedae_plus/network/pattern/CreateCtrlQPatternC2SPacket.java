package com.extendedae_plus.network.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.provider.RequestProvidersListC2SPacket;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S: Ctrl+Q quick-create pattern request.
 */
public class CreateCtrlQPatternC2SPacket {

    private final ResourceLocation recipeId;
    private final boolean isCraftingPattern;
    private final List<ItemStack> selectedIngredients;
    private final List<ItemStack> outputs;
    private final boolean openProviderSelector;
    private final boolean isAllowSubstitutes;
    private final boolean isFluidSubstitutes;

    public CreateCtrlQPatternC2SPacket(ResourceLocation recipeId, boolean isCraftingPattern, List<ItemStack> selectedIngredients, List<ItemStack> outputs,boolean isAllowSubstitutes,boolean isFluidSubstitutes) {
        this(recipeId, isCraftingPattern, selectedIngredients, outputs, false, isAllowSubstitutes, isFluidSubstitutes);
    }

    public CreateCtrlQPatternC2SPacket(ResourceLocation recipeId, boolean isCraftingPattern, List<ItemStack> selectedIngredients, List<ItemStack> outputs, boolean openProviderSelector,boolean isAllowSubstitutes,boolean isFluidSubstitutes) {
        this.recipeId = recipeId;
        this.isCraftingPattern = isCraftingPattern;
        this.selectedIngredients = selectedIngredients;
        this.outputs = outputs;
        this.openProviderSelector = openProviderSelector;
        this.isAllowSubstitutes = isAllowSubstitutes;
        this.isFluidSubstitutes = isFluidSubstitutes;
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
        buf.writeBoolean(msg.isAllowSubstitutes);
        buf.writeBoolean(msg.isFluidSubstitutes);
        buf.writeBoolean(msg.openProviderSelector);
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

        boolean isAllowSubstitutes = buf.readBoolean();
        boolean isFluidSubstitutes = buf.readBoolean();
        boolean openProviderSelector = buf.readableBytes() > 0 && buf.readBoolean();
        return new CreateCtrlQPatternC2SPacket(recipeId, isCraftingPattern, ingredients, outputs, openProviderSelector,isAllowSubstitutes,isFluidSubstitutes);
    }

    public static void handle(CreateCtrlQPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            RecipeManager recipeManager = player.level().getRecipeManager();
            var recipeOpt = recipeManager.byKey(msg.recipeId);
            if (recipeOpt.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), false);
                return;
            }

            Recipe<?> recipe = recipeOpt.get();

            if (!consumeBlankPattern(player)) {
                player.displayClientMessage(Component.translatable("message.extendedae_plus.no_blank_pattern"), false);
                return;
            }

            ItemStack pattern = createPattern(recipe, msg.isCraftingPattern, msg.selectedIngredients, msg.outputs,msg.isAllowSubstitutes,msg.isFluidSubstitutes, player);
            if (pattern.isEmpty()) {
                player.getInventory().add(AEItems.BLANK_PATTERN.stack());
                player.displayClientMessage(Component.translatable("message.extendedae_plus.pattern_creation_failed"), false);
                return;
            }

            if (msg.openProviderSelector) {
                ProviderUploadUtil.beginPendingCtrlQUpload(player, pattern);
                ModNetwork.CHANNEL.sendToServer(new RequestProvidersListC2SPacket());
                return;
            }

            if (!player.getInventory().add(pattern)) {
                player.drop(pattern, false);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static boolean consumeBlankPattern(ServerPlayer player) {
        //先从背包消耗
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(AEItems.BLANK_PATTERN.asItem())) {
                stack.shrink(1);
                return true;
            }
        }
        //没有再从网络消耗
        if (tryExtractFromNetwork(player)) {
            return true;
        }
        return false;
    }

    private static boolean tryExtractFromNetwork(ServerPlayer player) {
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        ItemStack terminal = located.stack;
        if (terminal.isEmpty()) {
            return false;
        }

        IGrid grid = WirelessTerminalLocator.getConnectedGrid(player, located);
        if (grid == null) return false;

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

        if (extracted > 0) {
            WirelessTerminalLocator.useTerminalPower(player, located, 0.5);
            located.commit();
            return true;
        }

        return false;
    }

    private static ItemStack createPattern(Recipe<?> recipe, boolean isCrafting, List<ItemStack> selectedIngredients, List<ItemStack> selectedOutputs, boolean isAllowSubstitutes,boolean isFluidSubstitutes, ServerPlayer player) {
        try {
            if (isCrafting && recipe instanceof CraftingRecipe craftingRecipe) {
                ItemStack[] inputs = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    if (i < selectedIngredients.size()) {
                        inputs[i] = selectedIngredients.get(i).copy();
                    } else {
                        inputs[i] = ItemStack.EMPTY;
                    }
                }

                // 客户端产物在服务端注册表上偶发为空时，回退使用客户端解析的产物
                ItemStack output = recipe.getResultItem(player.level().registryAccess()).copy();
                if (output.isEmpty()) {
                    for (ItemStack s : selectedOutputs) {
                        if (!s.isEmpty()) { output = s.copy(); break; }
                    }
                }
                ItemStack encodedPattern = PatternDetailsHelper.encodeCraftingPattern(
                    craftingRecipe,
                    inputs,
                    output,
                    isAllowSubstitutes,
                    isFluidSubstitutes
                );

                encodedPattern.getOrCreateTag().putString("encodePlayer", player.getName().getString());
                return encodedPattern;
            }

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
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
