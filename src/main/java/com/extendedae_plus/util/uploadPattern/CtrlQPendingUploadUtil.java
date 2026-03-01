package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
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
			player.getPersistentData().put(PENDING_STACK_KEY, remain.saveOptional(player.registryAccess()));
		}
		return true;
	}

	public static List<PatternContainer> listAvailableProvidersFromPlayerNetwork(ServerPlayer player) {
		return listAvailableProvidersFromGrid(findPlayerGrid(player));
	}

	public static IGrid findPlayerGrid(ServerPlayer player) {
		WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
		ItemStack terminal = located.stack;
		if (terminal.isEmpty()) {
			return null;
		}

		WirelessTerminalItem wt = terminal.getItem() instanceof WirelessTerminalItem t ? t : null;
		if (wt != null) {
			return wt.getLinkedGrid(terminal, player.serverLevel(), null);
		}

		String curiosSlotId = located.getCuriosSlotId();
		int curiosIndex = located.getCuriosIndex();
		if (curiosSlotId != null && curiosIndex >= 0) {
			try {
				WTDefinition def = WTDefinition.ofOrNull(terminal);
				if (def == null) return null;
				WTMenuHost wtHost = def.wTMenuHostFactory().create(
					def.item(),
					player,
					new CuriosItemLocator(curiosSlotId, curiosIndex),
					(p, sub) -> {
					}
				);
				if (wtHost == null || wtHost.getActionableNode() == null) return null;
				return wtHost.getActionableNode().getGrid();
			} catch (Throwable ignored) {
				return null;
			}
		}

		return null;
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
						boolean hasEmpty = false;
						for (int i = 0; i < inv.size(); i++) {
							if (inv.getStackInSlot(i).isEmpty()) {
								hasEmpty = true;
								break;
							}
						}
						if (hasEmpty) list.add(container);
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

			ItemStack nextRemain = new FilteredInternalInventory(inv, new CtrlQPatternFilter()).addItems(remain.copy());
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

