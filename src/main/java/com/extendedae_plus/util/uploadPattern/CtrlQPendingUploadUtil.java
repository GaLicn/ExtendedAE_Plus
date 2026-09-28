package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
		player.getPersistentData().put(PENDING_STACK_KEY, ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), pattern).getOrThrow());
		return id;
	}

	public static void clearPendingCtrlQUpload(ServerPlayer player) {
		if (player == null) return;
		player.getPersistentData().remove(PENDING_DATA_KEY);
		player.getPersistentData().remove(PENDING_STACK_KEY);
	}

	public static boolean hasPendingCtrlQPattern(ServerPlayer player) {
		if (player == null) return false;
		String id = player.getPersistentData().getString(PENDING_DATA_KEY).orElse("");
		if (id == null || id.isBlank()) return false;
		return !getPendingCtrlQPattern(player).isEmpty();
	}

	public static boolean uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
		if (player == null) return false;
		ItemStack pending = getPendingCtrlQPattern(player);
		if (pending.isEmpty()) return false;

		ItemStack remain = insertPatternIntoProviderFromPlayerNetwork(player, pending, providerId);
		if (remain.getCount() >= pending.getCount()) {
			return false;
		}

		if (remain.isEmpty()) {
			clearPendingCtrlQUpload(player);
		} else {
			player.getPersistentData().put(PENDING_STACK_KEY, ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), remain).getOrThrow());
		}
		return true;
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
						if (ExtendedAEPatternUploadUtil.getAvailableSlots(container) > 0) {
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
		String id = player.getPersistentData().getString(PENDING_DATA_KEY).orElse("");
		if (id == null || id.isBlank()) return ItemStack.EMPTY;

		CompoundTag data = player.getPersistentData();
		if (!data.contains(PENDING_STACK_KEY)) return ItemStack.EMPTY;
		CompoundTag stackTag = data.getCompound(PENDING_STACK_KEY).orElseThrow();
		ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stackTag).getOrThrow();
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

			ItemStack nextRemain = ExtendedAEPatternUploadUtil.insertIntoAccessiblePatternSlots(
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
		String targetName = ExtendedAEPatternUploadUtil.getProviderDisplayName(target);
		List<PatternContainer> tryList = new ArrayList<>();
		tryList.add(target);
		for (PatternContainer container : all) {
			if (container == null || container == target) continue;
			String name = ExtendedAEPatternUploadUtil.getProviderDisplayName(container);
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
