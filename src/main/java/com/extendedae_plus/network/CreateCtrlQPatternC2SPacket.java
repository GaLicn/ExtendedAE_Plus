package com.extendedae_plus.network;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.PlayerSource;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: Ctrl+Q 快速创建样板请求。
 */
public class CreateCtrlQPatternC2SPacket implements CustomPacketPayload {
	public static final Type<CreateCtrlQPatternC2SPacket> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "create_ctrlq_pattern"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CreateCtrlQPatternC2SPacket> STREAM_CODEC = StreamCodec.of(
		(buf, pkt) -> {
			buf.writeResourceLocation(pkt.recipeId);
			buf.writeBoolean(pkt.isCraftingPattern);
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, pkt.selectedIngredients);
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, pkt.outputs);
			buf.writeBoolean(pkt.isAllowSubstitutes);
			buf.writeBoolean(pkt.isFluidSubstitutes);
			buf.writeBoolean(pkt.openProviderSelector);
		},
		buf -> {
			ResourceLocation recipeId = buf.readResourceLocation();
			boolean isCraftingPattern = buf.readBoolean();
			List<ItemStack> ingredients = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
			List<ItemStack> outputs = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);

			boolean isAllowSubstitutes = buf.readBoolean();
			boolean isFluidSubstitutes = buf.readBoolean();
			boolean openProviderSelector = buf.readableBytes() > 0 && buf.readBoolean();
			return new CreateCtrlQPatternC2SPacket(
				recipeId,
				isCraftingPattern,
				ingredients,
				outputs,
				openProviderSelector,
				isAllowSubstitutes,
				isFluidSubstitutes
			);
		}
	);

	private final ResourceLocation recipeId;
	private final boolean isCraftingPattern;
	private final List<ItemStack> selectedIngredients;
	private final List<ItemStack> outputs;
	private final boolean openProviderSelector;
	private final boolean isAllowSubstitutes;
	private final boolean isFluidSubstitutes;

	public CreateCtrlQPatternC2SPacket(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs
	) {
		this(recipeId, isCraftingPattern, selectedIngredients, outputs, false, true, false);
	}

	public CreateCtrlQPatternC2SPacket(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs,
		boolean openProviderSelector
	) {
		this(recipeId, isCraftingPattern, selectedIngredients, outputs, openProviderSelector, true, false);
	}

	public CreateCtrlQPatternC2SPacket(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs,
		boolean isAllowSubstitutes,
		boolean isFluidSubstitutes
	) {
		this(recipeId, isCraftingPattern, selectedIngredients, outputs, false, isAllowSubstitutes, isFluidSubstitutes);
	}

	public CreateCtrlQPatternC2SPacket(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs,
		boolean openProviderSelector,
		boolean isAllowSubstitutes,
		boolean isFluidSubstitutes
	) {
		this.recipeId = recipeId;
		this.isCraftingPattern = isCraftingPattern;
		this.selectedIngredients = selectedIngredients;
		this.outputs = outputs;
		this.openProviderSelector = openProviderSelector;
		this.isAllowSubstitutes = isAllowSubstitutes;
		this.isFluidSubstitutes = isFluidSubstitutes;
	}

	public static void handle(final CreateCtrlQPatternC2SPacket msg, final IPayloadContext ctx) {
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

			if (!consumeBlankPattern(player)) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_blank_pattern"), false);
				return;
			}

			ItemStack pattern = createPattern(
				recipeHolder,
				msg.isCraftingPattern,
				msg.selectedIngredients,
				msg.outputs,
				msg.isAllowSubstitutes,
				msg.isFluidSubstitutes,
				player
			);
			if (pattern.isEmpty()) {
				player.getInventory().add(AEItems.BLANK_PATTERN.stack());
				player.displayClientMessage(Component.translatable("message.extendedae_plus.pattern_creation_failed"), false);
				return;
			}

			if (msg.openProviderSelector) {
				CtrlQPendingUploadUtil.beginPendingCtrlQUpload(player, pattern);
				PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
				return;
			}

			if (!player.getInventory().add(pattern)) {
				player.drop(pattern, false);
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static boolean consumeBlankPattern(ServerPlayer player) {
		//优先从背包消耗
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (AEItems.BLANK_PATTERN.is(stack)) {
				stack.shrink(1);
				return true;
			}
		}
		//没有再从终端消耗
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

		WirelessTerminalItem wt = terminal.getItem() instanceof WirelessTerminalItem t ? t : null;
		if (wt == null) {
			return false;
		}

		IGrid grid = wt.getLinkedGrid(terminal, player.serverLevel(), null);
		if (grid == null) {
			return false;
		}
		if (!wt.hasPower(player, 0.5, terminal)) {
			return false;
		}

		IEnergyService energy = grid.getEnergyService();
		MEStorage storage = grid.getStorageService().getInventory();
		long extracted = StorageHelper.poweredExtraction(
			energy,
			storage,
			appeng.api.stacks.AEItemKey.of(AEItems.BLANK_PATTERN.stack()),
			1,
			new PlayerSource(player)
		);
		if (extracted > 0) {
			wt.usePower(player, 0.5, terminal);
			located.commit();
			return true;
		}
		return false;
	}

	private static ItemStack createPattern(
		RecipeHolder<?> recipeHolder,
		boolean isCrafting,
		List<ItemStack> selectedIngredients,
		List<ItemStack> selectedOutputs,
		boolean isAllowSubstitutes,
		boolean isFluidSubstitutes,
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
					isAllowSubstitutes,
					isFluidSubstitutes
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
