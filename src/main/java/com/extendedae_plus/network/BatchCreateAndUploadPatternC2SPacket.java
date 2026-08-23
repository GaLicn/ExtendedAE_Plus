package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: 合成链（EMI 配方树 / BoM）一键批量编码并上传。
 * <p>
 * 相比逐个发送 {@link CreateAndUploadPatternC2SPacket}，本封包一次带走整棵树：
 * 服务端只查一次网络与供应器列表，逐个样板按「装配矩阵 → 依映射匹配的供应器 → 背包」三段回退，
 * 最后只回一条汇总消息（避免 N 个节点刷 N 条聊天）。
 * <p>
 * {@code providerSearchKey} 由客户端依据自己的 {@code recipe_type_names.json} 解析后传入：
 * 映射表由客户端的映射管理界面维护，服务端那份对本流程无作用，
 * 若改为服务端解析会导致合成树上的映射标记与实际上传目标不一致。
 */
public class BatchCreateAndUploadPatternC2SPacket implements CustomPacketPayload {
	public static final Type<BatchCreateAndUploadPatternC2SPacket> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "batch_create_and_upload_pattern"));

	/** 单封包条目上限，客户端超出时自行分批发送。 */
	public static final int MAX_ENTRIES = 64;

	/**
	 * @param providerSearchKey 映射出的供应器搜索词；空串表示该配方类型没有自定义映射。
	 */
	public record Entry(
		ResourceLocation recipeId,
		boolean isCraftingPattern,
		List<ItemStack> selectedIngredients,
		List<ItemStack> outputs,
		String providerSearchKey
	) {}

	public static final StreamCodec<RegistryFriendlyByteBuf, BatchCreateAndUploadPatternC2SPacket> STREAM_CODEC =
		StreamCodec.of(
			(buf, pkt) -> {
				buf.writeBoolean(pkt.isAllowSubstitutes);
				buf.writeBoolean(pkt.isFluidSubstitutes);
				buf.writeVarInt(pkt.entries.size());
				for (Entry entry : pkt.entries) {
					buf.writeResourceLocation(entry.recipeId());
					buf.writeBoolean(entry.isCraftingPattern());
					ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, entry.selectedIngredients());
					ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, entry.outputs());
					buf.writeUtf(entry.providerSearchKey() == null ? "" : entry.providerSearchKey());
				}
			},
			buf -> {
				boolean isAllowSubstitutes = buf.readBoolean();
				boolean isFluidSubstitutes = buf.readBoolean();
				int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
				List<Entry> entries = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					ResourceLocation recipeId = buf.readResourceLocation();
					boolean isCraftingPattern = buf.readBoolean();
					List<ItemStack> ingredients = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
					List<ItemStack> outputs = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
					String searchKey = buf.readUtf();
					entries.add(new Entry(recipeId, isCraftingPattern, ingredients, outputs, searchKey));
				}
				return new BatchCreateAndUploadPatternC2SPacket(entries, isAllowSubstitutes, isFluidSubstitutes);
			}
		);

	private final List<Entry> entries;
	private final boolean isAllowSubstitutes;
	private final boolean isFluidSubstitutes;

	public BatchCreateAndUploadPatternC2SPacket(List<Entry> entries,
	                                            boolean isAllowSubstitutes,
	                                            boolean isFluidSubstitutes) {
		this.entries = entries;
		this.isAllowSubstitutes = isAllowSubstitutes;
		this.isFluidSubstitutes = isFluidSubstitutes;
	}

	public static void handle(final BatchCreateAndUploadPatternC2SPacket msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer player)) {
				return;
			}
			if (msg.entries.isEmpty()) {
				return;
			}

			IGrid grid = CtrlQPendingUploadUtil.findPlayerGrid(player);
			if (grid == null) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_network"), false);
				return;
			}

			// 供应器列表整批复用一次快照：逐个查会在大树上重复遍历整个网络的机器类。
			List<PatternContainer> providers = CtrlQPendingUploadUtil.listAvailableProvidersFromPlayerNetwork(player);

			int toMatrix = 0;
			int toProvider = 0;
			int toInventory = 0;
			int noRecipe = 0;
			int duplicate = 0;
			int failed = 0;
			boolean outOfBlankPatterns = false;

			for (Entry entry : msg.entries) {
				var recipeOpt = player.level().getRecipeManager().byKey(entry.recipeId());
				if (recipeOpt.isEmpty()) {
					noRecipe++;
					continue;
				}

				// 先编码再扣空白样板：编码本身不消耗材料，这样重复项与编码失败都不会白扣。
				ItemStack pattern = CreateAndUploadPatternC2SPacket.encodePattern(
					recipeOpt.get(),
					entry.isCraftingPattern(),
					entry.selectedIngredients(),
					entry.outputs(),
					msg.isAllowSubstitutes,
					msg.isFluidSubstitutes,
					player
				);
				if (pattern.isEmpty()) {
					failed++;
					continue;
				}

				// 反复按一键编码时不该在背包里堆一摞重复样板。
				if (ExtendedAEPatternUploadUtil.matrixHasPattern(grid, pattern)) {
					duplicate++;
					continue;
				}

				if (!CreateAndUploadPatternC2SPacket.consumeBlankPattern(player, grid)) {
					outOfBlankPatterns = true;
					break;
				}

				// 装配矩阵只收合成/锻造/切石样板，处理样板必然落到下一段。
				if (ExtendedAEPatternUploadUtil.uploadPatternToMatrix(player, pattern, grid, true)) {
					toMatrix++;
					continue;
				}

				if (ExtendedAEPatternUploadUtil.uploadPatternToMatchingProvider(
						player, pattern, providers, entry.providerSearchKey())) {
					toProvider++;
					continue;
				}

				if (!player.getInventory().add(pattern)) {
					player.drop(pattern.copy(), false);
				}
				toInventory++;
			}

			player.displayClientMessage(Component.translatable(
				"message.extendedae_plus.bom_encode.summary",
				toMatrix, toProvider, toInventory), false);

			if (duplicate > 0) {
				player.displayClientMessage(Component.translatable(
					"message.extendedae_plus.bom_encode.skipped_duplicate", duplicate), false);
			}
			if (noRecipe > 0) {
				player.displayClientMessage(Component.translatable(
					"message.extendedae_plus.bom_encode.skipped_no_recipe", noRecipe), false);
			}
			if (failed > 0) {
				player.displayClientMessage(Component.translatable(
					"message.extendedae_plus.bom_encode.failed", failed), false);
			}
			if (outOfBlankPatterns) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_blank_pattern"), false);
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
