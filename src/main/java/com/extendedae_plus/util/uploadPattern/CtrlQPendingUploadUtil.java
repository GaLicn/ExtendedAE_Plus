package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.UploadOutcome;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ctrl+Q 临时样板缓存与上传逻辑（pending provider upload）。
 */
public final class CtrlQPendingUploadUtil {
	private static final String PENDING_DATA_KEY = "eap_ctrlq_pending_provider_upload_id";
	private static final String PENDING_STACK_KEY = "eap_ctrlq_pending_provider_upload_stack";

	private CtrlQPendingUploadUtil() {
	}

	public static String beginPendingCtrlQUpload(ServerPlayer player, ItemStack pattern) {
		if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
			return null;
		}
		clearPendingCtrlQUpload(player);
		String id = UUID.randomUUID().toString();
		player.getPersistentData().putString(PENDING_DATA_KEY, id);
		player.getPersistentData().put(PENDING_STACK_KEY, pattern.saveOptional(player.registryAccess()));
		return id;
	}

	public static void clearPendingCtrlQUpload(ServerPlayer player) {
		if (player == null) return;
		player.getPersistentData().remove(PENDING_DATA_KEY);
		player.getPersistentData().remove(PENDING_STACK_KEY);
	}

	public static boolean hasPendingCtrlQPattern(ServerPlayer player) {
		if (player == null) return false;
		String id = player.getPersistentData().getString(PENDING_DATA_KEY);
		if (id == null || id.isBlank()) return false;
		return !getPendingCtrlQPattern(player).isEmpty();
	}

	/**
	 * 将 Ctrl+Q 缓存样板写入指定供应器。
	 *
	 * <p>若 index 指定的目标供应器已含「除编码作者以外完全相同」的样板，
	 * 直接返回 {@link UploadOutcome#DUPLICATE}，不写入任何槽位（样板由调用方退回背包）。
	 * 检测只针对 index 指定的那一个供应器。</p>
	 */
	public static UploadOutcome uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
		if (player == null) return UploadOutcome.FAILED;
		ItemStack pending = getPendingCtrlQPattern(player);
		if (pending.isEmpty()) return UploadOutcome.FAILED;

		if (pendingTargetContainsPattern(player, providerId, pending)) {
			return UploadOutcome.DUPLICATE;
		}

		ItemStack remain = insertPatternIntoProviderFromPlayerNetwork(player, pending, providerId);
		if (remain.getCount() >= pending.getCount()) {
			return UploadOutcome.FAILED;
		}

		if (remain.isEmpty()) {
			clearPendingCtrlQUpload(player);
		} else {
			player.getPersistentData().put(PENDING_STACK_KEY, remain.saveOptional(player.registryAccess()));
		}
		return UploadOutcome.SUCCESS;
	}

	/**
	 * 判定 index 指定的目标供应器是否已存在同一缓存样板（剔除编码作者后完全相同）。
	 */
	private static boolean pendingTargetContainsPattern(ServerPlayer player, long providerId, ItemStack pattern) {
		int index = decodeProviderIndex(providerId);
		if (index < 0) return false;
		List<PatternContainer> providers = listAvailableProvidersFromPlayerNetwork(player);
		if (index >= providers.size()) return false;
		PatternContainer target = providers.get(index);
		if (target == null) return false;
		return ProviderUploadUtil.providerContainsPattern(target, pattern);
	}

	public static boolean returnPendingCtrlQPatternToInventory(ServerPlayer player){
		if (player == null) return false;
		ItemStack pending = getPendingCtrlQPattern(player);
		if(pending.isEmpty()) return false;

		clearPendingCtrlQUpload(player);
		if (!(player.getInventory().add(pending))) {
			player.drop(pending.copy(),false);
		}
		return true;
	}

	public static List<PatternContainer> listAvailableProvidersFromPlayerNetwork(ServerPlayer player) {
		return listAvailableProvidersFromGrid(findPlayerGrid(player));
	}

    public static IGrid findPlayerGrid(ServerPlayer player) {
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        return WirelessTerminalLocator.getConnectedGrid(player, located);
    }

	public static List<PatternContainer> listAvailableProvidersFromGrid(IGrid grid) {
		List<PatternContainer> list = new ArrayList<>();
		if (grid == null) return list;
		try {
			for (var machineClass : grid.getMachineClasses()) {
				if (PatternContainer.class.isAssignableFrom(machineClass)) {
					@SuppressWarnings("unchecked")
					Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
					for (var container : grid.getActiveMachines(containerClass)) {
						if (container == null || !container.isVisibleInTerminal()) continue;
						InternalInventory inv = container.getTerminalPatternInventory();
						if (inv == null || inv.size() <= 0) continue;
						if (ProviderUploadUtil.getAvailableSlots(container) > 0) {
							list.add(container);
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return list;
	}

	private static ItemStack getPendingCtrlQPattern(ServerPlayer player) {
		if (player == null) return ItemStack.EMPTY;
		String id = player.getPersistentData().getString(PENDING_DATA_KEY);
		if (id == null || id.isBlank()) return ItemStack.EMPTY;

		CompoundTag data = player.getPersistentData();
		if (!data.contains(PENDING_STACK_KEY)) return ItemStack.EMPTY;
		CompoundTag stackTag = data.getCompound(PENDING_STACK_KEY);
		ItemStack stack = ItemStack.parseOptional(player.registryAccess(), stackTag);
		if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
			clearPendingCtrlQUpload(player);
			return ItemStack.EMPTY;
		}
		return stack;
	}

	private static ItemStack insertPatternIntoProviderFromPlayerNetwork(ServerPlayer player, ItemStack pattern, long providerId) {
		if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
			return pattern == null ? ItemStack.EMPTY : pattern;
		}

		int index = decodeProviderIndex(providerId);
		if (index < 0) return pattern;

		List<PatternContainer> providers = listAvailableProvidersFromPlayerNetwork(player);
		if (index >= providers.size()) return pattern;

		PatternContainer target = providers.get(index);
		if (target == null) return pattern;

		ItemStack remain = pattern.copy();
		for (PatternContainer container : buildSameNameTryList(providers, target)) {
			InternalInventory inv = container.getTerminalPatternInventory();
			if (inv == null || inv.size() <= 0) continue;

			ItemStack nextRemain = ProviderUploadUtil.insertIntoAccessiblePatternSlots(
					container, remain.copy(), new CtrlQPatternFilter());
			if (nextRemain.getCount() < remain.getCount()) {
				remain = nextRemain;
				if (remain.isEmpty()) {
					return ItemStack.EMPTY;
				}
			}
		}
		return remain;
	}

	private static int decodeProviderIndex(long providerId) {
		if (providerId >= 0) return -1;
		long idx = -1L - providerId;
		if (idx > Integer.MAX_VALUE) return -1;
		return (int) idx;
	}

	private static List<PatternContainer> buildSameNameTryList(List<PatternContainer> all, PatternContainer target) {
		String targetName = ProviderUploadUtil.getProviderDisplayName(target);
		List<PatternContainer> tryList = new ArrayList<>();
		tryList.add(target);
		for (PatternContainer container : all) {
			if (container == null || container == target) continue;
			String name = ProviderUploadUtil.getProviderDisplayName(container);
			if (name != null && name.equals(targetName)) {
				tryList.add(container);
			}
		}
		return tryList;
	}

	private static class CtrlQPatternFilter implements IAEItemFilter {
		@Override
		public boolean allowExtract(InternalInventory inv, int slot, int amount) {
			return true;
		}

		@Override
		public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
			return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
		}
	}
}
