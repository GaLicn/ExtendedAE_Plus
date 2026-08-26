package com.extendedae_plus.util.uploadPattern;

import appeng.api.networking.IGrid;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.network.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键批量编码里「映射没有唯一命中」的样板队列。
 * <p>
 * 服务端只在映射精确命中唯一一台机器时自动上传；命中 0 台或多台的样板进本队列，
 * 逐个下发 {@link ProvidersListS2CPacket} 让玩家在供应器选择界面指定目标，
 * 选完（或跳过）才轮到下一项。
 * <p>
 * 样板本体存在玩家 persistentData 而不是内存里：编码时空白样板已经扣掉，
 * 队列一丢就等于凭空吞掉玩家的材料。跳过或放弃的项不会生成样板，
 * 编码时扣掉的空白样板会还回网络；玩家中途退出时留在队列里的项也按这个规则在下次登录时结算。
 */
public final class BatchPendingUploadUtil {
	private static final String QUEUE_KEY = "eap_batch_pending_queue";
	private static final String UPLOADED_KEY = "eap_batch_pending_uploaded";
	private static final String DISCARDED_KEY = "eap_batch_pending_discarded";
	private static final String ITEM_PATTERN = "pattern";
	private static final String ITEM_OUTPUT = "output";
	private static final String ITEM_SEARCH_KEY = "search_key";
	private static final String ITEM_RECIPE_ID = "recipe_id";
	private static final String ITEM_CANDIDATES = "candidates";
	/** 队列上限：一棵大树也不该让玩家点上几百次，超出的直接落背包。 */
	private static final int MAX_QUEUE = 256;

	private BatchPendingUploadUtil() {
	}

	public static boolean hasPending(ServerPlayer player) {
		return player != null && !queue(player).isEmpty();
	}

	/**
	 * 把一份「需要玩家指定目标」的样板排进队列。
	 *
	 * @param recipeId   配方 ID，只用于界面上把材料一并画出来
	 * @param candidates 服务端按搜索词匹配到的机器数，0 表示映射一台都没命中
	 * @return 入队成功；false 表示队列已满，调用方应自行落背包
	 */
	public static boolean enqueue(ServerPlayer player, ItemStack pattern, String searchKey,
	                              ItemStack output, ResourceLocation recipeId, int candidates) {
		if (player == null || pattern == null || pattern.isEmpty()) {
			return false;
		}
		ListTag queue = queue(player);
		if (queue.size() >= MAX_QUEUE) {
			return false;
		}

		CompoundTag item = new CompoundTag();
		item.put(ITEM_PATTERN, pattern.saveOptional(player.registryAccess()));
		item.put(ITEM_OUTPUT, output == null || output.isEmpty()
				? new CompoundTag()
				: output.saveOptional(player.registryAccess()));
		item.putString(ITEM_SEARCH_KEY, searchKey == null ? "" : searchKey);
		item.putString(ITEM_RECIPE_ID, recipeId == null ? "" : recipeId.toString());
		item.putInt(ITEM_CANDIDATES, Math.max(0, candidates));
		queue.add(item);
		player.getPersistentData().put(QUEUE_KEY, queue);
		return true;
	}

	/**
	 * 下发队首项的供应器列表，队列空则改为发汇总消息收尾。
	 * 每一项都重新枚举一次网络：上一次上传会改变各机器的空位，甚至让某台机器从列表里消失。
	 */
	public static void sendCurrentSelection(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ListTag queue = queue(player);
		if (queue.isEmpty()) {
			finish(player);
			return;
		}

		CompoundTag head = queue.getCompound(0);
		ItemStack output = ItemStack.parseOptional(player.registryAccess(), head.getCompound(ITEM_OUTPUT));
		int resolved = stat(player, UPLOADED_KEY) + stat(player, DISCARDED_KEY);
		var pending = new ProvidersListS2CPacket.PendingSelection(
				output,
				ResourceLocation.tryParse(head.getString(ITEM_RECIPE_ID)),
				resolved + 1,
				resolved + queue.size(),
				head.getString(ITEM_SEARCH_KEY),
				head.getInt(ITEM_CANDIDATES));

		// 负数 ID 表示按索引，与 RequestProvidersListC2SPacket 的 pending 分支同一约定：
		// 索引必须相对未过滤的枚举结果，上传时 CtrlQPendingUploadUtil 按同一顺序解码。
		List<PatternContainer> containers = CtrlQPendingUploadUtil.listAvailableProvidersFromPlayerNetwork(player);
		List<Long> ids = new ArrayList<>(containers.size());
		List<Component> names = new ArrayList<>(containers.size());
		List<Integer> slots = new ArrayList<>(containers.size());
		for (int i = 0; i < containers.size(); i++) {
			PatternContainer container = containers.get(i);
			if (container == null) {
				continue;
			}
			int empty = ExtendedAEPatternUploadUtil.getAvailableSlots(container);
			if (empty <= 0) {
				continue;
			}
			ids.add(-1L - i);
			names.add(ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(container));
			slots.add(empty);
		}

		if (ids.isEmpty()) {
			// 网络里已经没有带空位的供应器了：弹一个空列表只能让玩家一项项点跳过。
			abortAll(player);
			return;
		}

		player.connection.send(ProvidersListS2CPacket.pendingSelection(ids, names, slots, pending));
	}

	/**
	 * 玩家点选了目标机器：插进去才算这份样板真正生成。
	 * <p>
	 * 选中的机器刚好被塞满时既不消耗样板也不前进队列，只提示一声让玩家换一台：
	 * 硬塞进背包会让玩家多出一份没进网络的样板，还得自己找出是哪个配方。
	 */
	public static void chooseForHead(ServerPlayer player, long providerId) {
		if (!hasPending(player)) {
			return;
		}
		ItemStack pattern = peekHeadPattern(player);
		if (pattern.isEmpty()) {
			// 样板 NBT 读不出来，这一项没救了，扣掉的空白样板还回去。
			popHead(player);
			discard(player);
			sendCurrentSelection(player);
			return;
		}

		ItemStack remain = CtrlQPendingUploadUtil
				.insertPatternIntoProviderFromPlayerNetwork(player, pattern, providerId);
		if (remain.isEmpty()) {
			popHead(player);
			bump(player, UPLOADED_KEY);
		} else {
			player.displayClientMessage(
					Component.translatable("message.extendedae_plus.bom_encode.provider_full"), true);
		}
		sendCurrentSelection(player);
	}

	/** 跳过队首项：这份样板压根不生成，空白样板还回去，继续问下一项。 */
	public static void skipHead(ServerPlayer player) {
		if (!hasPending(player)) {
			return;
		}
		popHead(player);
		discard(player);
		sendCurrentSelection(player);
	}

	/** 放弃整条队列：剩下的样板一份都不生成，扣掉的空白样板全部还回去，只发一条汇总。 */
	public static void abortAll(ServerPlayer player) {
		if (player == null) {
			return;
		}
		int remaining = queue(player).size();
		player.getPersistentData().remove(QUEUE_KEY);
		for (int i = 0; i < remaining; i++) {
			discard(player);
		}
		finish(player);
	}

	/**
	 * 玩家上线时清空遗留队列：队列里的样板都已经扣过空白样板，
	 * 留在 NBT 里等于白扣，按「未生成」处理把空白样板还回去。
	 * 掉线时不处理，因为那时往背包塞东西未必还能保存进存档。
	 */
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && hasPending(player)) {
			abortAll(player);
		}
	}

	// --------------------------- 内部实现 ---------------------------

	/** 队列空了：发一条手动指定的汇总并清掉计数，避免和下一次一键编码的统计串味。 */
	private static void finish(ServerPlayer player) {
		int uploaded = stat(player, UPLOADED_KEY);
		int discarded = stat(player, DISCARDED_KEY);
		player.getPersistentData().remove(QUEUE_KEY);
		player.getPersistentData().remove(UPLOADED_KEY);
		player.getPersistentData().remove(DISCARDED_KEY);
		if (uploaded > 0 || discarded > 0) {
			player.displayClientMessage(Component.translatable(
					"message.extendedae_plus.bom_encode.manual_summary", uploaded, discarded), false);
		}
	}

	private static ListTag queue(ServerPlayer player) {
		if (player == null) {
			return new ListTag();
		}
		return player.getPersistentData().getList(QUEUE_KEY, Tag.TAG_COMPOUND);
	}

	/** 只看队首样板，不出队：插不进目标机器时这一项还要留在队列里让玩家换一台。 */
	private static ItemStack peekHeadPattern(ServerPlayer player) {
		ListTag queue = queue(player);
		if (queue.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return ItemStack.parseOptional(player.registryAccess(),
				queue.getCompound(0).getCompound(ITEM_PATTERN));
	}

	/** 移除队首项。 */
	private static void popHead(ServerPlayer player) {
		ListTag queue = queue(player);
		if (queue.isEmpty()) {
			return;
		}
		queue.remove(0);
		if (queue.isEmpty()) {
			player.getPersistentData().remove(QUEUE_KEY);
		} else {
			player.getPersistentData().put(QUEUE_KEY, queue);
		}
	}

	/**
	 * 这一项作废：编好的样板直接丢掉（它从未进入世界），把编码时扣掉的空白样板还回网络。
	 * 网络不在或塞不下时给到背包，总之不能让玩家什么都没选却少一张空白样板。
	 */
	private static void discard(ServerPlayer player) {
		bump(player, DISCARDED_KEY);
		IGrid grid = CtrlQPendingUploadUtil.findPlayerGrid(player);
		if (grid != null && CreateAndUploadPatternC2SPacket.refundBlankPattern(player, grid)) {
			return;
		}
		ItemStack blank = AEItems.BLANK_PATTERN.stack();
		if (!player.getInventory().add(blank)) {
			player.drop(blank, false);
		}
	}

	private static int stat(ServerPlayer player, String key) {
		return player == null ? 0 : player.getPersistentData().getInt(key);
	}

	private static void bump(ServerPlayer player, String key) {
		player.getPersistentData().putInt(key, stat(player, key) + 1);
	}
}
