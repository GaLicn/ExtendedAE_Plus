package com.extendedae_plus.network;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: 创建样板并上传到装配矩阵（合成书签分支）。
 */
public class CreateAndUploadPatternC2SPacket implements CustomPacketPayload {
	public static final Type<CreateAndUploadPatternC2SPacket> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "create_and_upload_pattern"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CreateAndUploadPatternC2SPacket> STREAM_CODEC = StreamCodec.of(
		(buf, pkt) -> {
			buf.writeResourceLocation(pkt.recipeId);
			buf.writeBoolean(pkt.isCraftingPattern);
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, pkt.selectedIngredients);
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, pkt.outputs);
		},
		buf -> {
			ResourceLocation recipeId = buf.readResourceLocation();
			boolean isCraftingPattern = buf.readBoolean();
			List<ItemStack> ingredients = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
			List<ItemStack> outputs = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
			return new CreateAndUploadPatternC2SPacket(recipeId, isCraftingPattern, ingredients, outputs);
		}
	);

	private final ResourceLocation recipeId;
	private final boolean isCraftingPattern;
	private final List<ItemStack> selectedIngredients;
	private final List<ItemStack> outputs;

	public CreateAndUploadPatternC2SPacket(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs
	) {
		this.recipeId = recipeId;
		this.isCraftingPattern = isCraftingPattern;
		this.selectedIngredients = selectedIngredients;
		this.outputs = outputs;
	}

	public static void handle(final CreateAndUploadPatternC2SPacket msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer player)) {
				return;
			}

			var recipeOpt = player.level().getRecipeManager().byKey(msg.recipeId);
			if (recipeOpt.isEmpty()) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), false);
				return;
			}
			RecipeHolder<?> recipeHolder = recipeOpt.get();

			IGrid grid = CtrlQPendingUploadUtil.findPlayerGrid(player);
			if (grid == null) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_network"), false);
				return;
			}

			if (!consumeBlankPattern(player, grid)) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_blank_pattern"), false);
				return;
			}

			ItemStack pattern = createPattern(recipeHolder, msg.isCraftingPattern, msg.selectedIngredients, msg.outputs, player);
			if (pattern.isEmpty()) {
				refundBlankPattern(player, grid);
				player.displayClientMessage(Component.translatable("message.extendedae_plus.pattern_creation_failed"), false);
				return;
			}

			boolean uploaded = ExtendedAEPatternUploadUtil.uploadPatternToMatrix(player, pattern, grid);
			if (!uploaded) {
				refundBlankPattern(player, grid);
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

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

	private static ItemStack createPattern(
		RecipeHolder<?> recipeHolder,
		boolean isCrafting,
		List<ItemStack> selectedIngredients,
		List<ItemStack> selectedOutputs,
		ServerPlayer player
	) {
		try {
			if (isCrafting && recipeHolder.value() instanceof CraftingRecipe) {
				@SuppressWarnings("unchecked")
				RecipeHolder<CraftingRecipe> craftingHolder = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) recipeHolder;

				ItemStack[] inputs = new ItemStack[9];
				for (int i = 0; i < 9; i++) {
					if (i < selectedIngredients.size()) {
						inputs[i] = selectedIngredients.get(i).copy();
					} else {
						inputs[i] = ItemStack.EMPTY;
					}
				}

				ItemStack output = ItemStack.EMPTY;
				if (!selectedOutputs.isEmpty()) {
					output = selectedOutputs.get(0).copy();
				}
				if (output.isEmpty()) {
					output = recipeHolder.value().getResultItem(player.level().registryAccess()).copy();
				}

				ItemStack encodedPattern = PatternDetailsHelper.encodeCraftingPattern(
					craftingHolder,
					inputs,
					output,
					true,
					false
				);
				CustomData.update(DataComponents.CUSTOM_DATA, encodedPattern, tag -> tag.putString("encodePlayer", player.getGameProfile().getName()));
				return encodedPattern;
			}

			List<GenericStack> inputs = new ArrayList<>();
			List<GenericStack> outputs = new ArrayList<>();

			for (ItemStack item : selectedIngredients) {
				if (!item.isEmpty()) {
					GenericStack stack = GenericStack.unwrapItemStack(item);
					if (stack == null) {
						stack = GenericStack.fromItemStack(item);
					}
					if (stack != null) {
						inputs.add(stack);
					}
				}
			}

			for (ItemStack item : selectedOutputs) {
				if (!item.isEmpty()) {
					GenericStack stack = GenericStack.unwrapItemStack(item);
					if (stack == null) {
						stack = GenericStack.fromItemStack(item);
					}
					if (stack != null) {
						outputs.add(stack);
					}
				}
			}

			ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
			CustomData.update(DataComponents.CUSTOM_DATA, encodedPattern, tag -> tag.putString("encodePlayer", player.getGameProfile().getName()));
			return encodedPattern;
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}
}
