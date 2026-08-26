package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil.MatrixInventoryTarget;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量编码上传专用的工具：全网重复样板检测、依映射挑供应器插入。
 * <p>
 * 与单个样板上传共用的部分（空白样板扣减、编码、矩阵插入）仍在
 * {@link ExtendedAEPatternUploadUtil}；这里只放批量路径独有的逻辑，
 * 避免把那个本就很大的共享工具类继续撑大。
 */
public final class BatchPatternUploadUtil {

	private BatchPatternUploadUtil() {}

	// --------------------------- 重复样板检测 ---------------------------

	/**
	 * 忽略编码者信息后的样板副本，用于判定「是不是同一份样板」。
	 * {@code encodePlayer} 只记录是谁编的，不影响样板功能，必须排除在比较之外。
	 */
	private static ItemStack normalizePatternForCompare(ItemStack pattern) {
		ItemStack copy = pattern.copy();
		CompoundTag tag = copy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
		tag.remove("encodePlayer");
		copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return copy;
	}

	/** 建立「忽略编码者」的样板集合：元素一律先经 {@link #normalizePatternForCompare} 规范化。 */
	private static Set<ItemStack> newPatternSet() {
		return new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
			@Override
			public int hashCode(ItemStack stack) {
				return stack == null ? 0 : ItemStack.hashItemAndComponents(stack);
			}

			@Override
			public boolean equals(ItemStack a, ItemStack b) {
				return a == b || (a != null && b != null && ItemStack.isSameItemSameComponents(a, b));
			}
		});
	}

	/**
	 * 收集网络中已存在的样板（装配矩阵 + 样板供应器），供批量编码一次性查重。
	 * <p>
	 * 批量编码若逐个样板扫全网，复杂度是 O(样板数 × 供应器数 × 槽位数)，
	 * 大网络上足以造成明显卡顿；因此扫一遍建成哈希集合，之后每次查重都是 O(1)。
	 * 新放进去的样板要由调用方 {@link #rememberPattern} 补进集合，集合不会自动跟随库存变化。
	 *
	 * @param providers 供应器快照；传 null 表示只看装配矩阵
	 */
	public static Set<ItemStack> collectExistingPatterns(IGrid grid, List<PatternContainer> providers) {
		Set<ItemStack> existing = newPatternSet();
		if (grid == null) {
			return existing;
		}

		for (MatrixInventoryTarget target : ExtendedAEPatternUploadUtil.findAllMatrixPatternInventories(grid)) {
			if (target == null) {
				continue;
			}
			InternalInventory inv = target.patternInventory();
			collectFrom(inv, inv == null ? 0 : inv.size(), existing);
		}

		if (providers != null) {
			for (PatternContainer container : providers) {
				if (container == null) {
					continue;
				}
				collectFrom(container.getTerminalPatternInventory(),
						ExtendedAEPatternUploadUtil.getAccessiblePatternSlotCount(container), existing);
			}
		}
		return existing;
	}

	private static void collectFrom(InternalInventory inv, int slotLimit, Set<ItemStack> out) {
		if (inv == null) {
			return;
		}
		int limit = Math.max(0, Math.min(inv.size(), slotLimit));
		for (int i = 0; i < limit; i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (stack != null && !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
				out.add(normalizePatternForCompare(stack));
			}
		}
	}

	/** 集合里是否已有这份样板。 */
	public static boolean containsPattern(Set<ItemStack> existing, ItemStack pattern) {
		if (existing == null || existing.isEmpty() || pattern == null || pattern.isEmpty()) {
			return false;
		}
		return existing.contains(normalizePatternForCompare(pattern));
	}

	/** 样板成功放入网络后登记进集合，让同一批里的后续项也能查到。 */
	public static void rememberPattern(Set<ItemStack> existing, ItemStack pattern) {
		if (existing == null || pattern == null || pattern.isEmpty()) {
			return;
		}
		existing.add(normalizePatternForCompare(pattern));
	}

	// --------------------------- 依映射挑供应器 ---------------------------

	// 优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配
	private static boolean JEC_RESOLVED = false;
	private static Method JEC_CONTAINS = null;

	/**
	 * 供应器名称与搜索词的匹配（客户端选择界面与服务端定向上传共用同一语义）。
	 * 空搜索词视为全部匹配。
	 */
	public static boolean providerNameMatches(String name, String key) {
		if (name == null) {
			return false;
		}
		if (key == null || key.isEmpty()) {
			return true;
		}
		if (jecContains() != null && jecMatches(name, key)) {
			return true;
		}
		// 默认大小写不敏感子串
		return name.toLowerCase().contains(key.toLowerCase());
	}

	/** JEC 的 Match.contains；未装 JEC 时返回 null。反射解析只做一次，结果缓存。 */
	private static Method jecContains() {
		if (JEC_RESOLVED) {
			return JEC_CONTAINS;
		}
		JEC_RESOLVED = true;
		try {
			JEC_CONTAINS = Class.forName("me.towdium.jecharacters.utils.Match")
					.getMethod("contains", CharSequence.class, CharSequence.class);
		} catch (ReflectiveOperationException notInstalled) {
			JEC_CONTAINS = null;
		}
		return JEC_CONTAINS;
	}

	/** 原样匹配一次，再用双方小写各匹配一次（JEC 自身不保证大小写不敏感）。 */
	private static boolean jecMatches(String name, String key) {
		return jecContains(name, key) || jecContains(name.toLowerCase(), key.toLowerCase());
	}

	private static boolean jecContains(String name, String key) {
		try {
			return Boolean.TRUE.equals(JEC_CONTAINS.invoke(null, name, key));
		} catch (ReflectiveOperationException failed) {
			return false;
		}
	}

	/**
	 * 依映射搜索词挑出候选供应器：先按显示名精确相等收集，一个都没有再退回模糊匹配（含 JEC 拼音）。
	 * <p>
	 * 映射值是在供应器选择界面点选机器时写下的完整机器名，精确优先才不会让「熔炉」
	 * 连带命中「高级熔炉」——那样服务端会在两台不相干的机器之间自行挑一台。
	 * 手输的映射值可能只是片段，所以精确落空时仍要保留模糊这一层。
	 */
	public static List<PatternContainer> matchProviders(List<PatternContainer> providers, String searchKey) {
		if (providers == null || providers.isEmpty() || searchKey == null || searchKey.isBlank()) {
			return List.of();
		}
		String key = searchKey.trim();
		List<PatternContainer> exact = new ArrayList<>();
		List<PatternContainer> fuzzy = new ArrayList<>();
		for (PatternContainer container : providers) {
			if (container == null) {
				continue;
			}
			String name = ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(container).getString();
			if (name == null) {
				continue;
			}
			if (name.trim().equalsIgnoreCase(key)) {
				exact.add(container);
			} else if (providerNameMatches(name, key)) {
				fuzzy.add(container);
			}
		}
		return exact.isEmpty() ? fuzzy : exact;
	}

	/**
	 * 候选里不同显示名的个数，即「玩家眼里有几台机器可选」。
	 * 同名机器在选择界面里本就合并成一条、上传时可互相顶替，因此算一个候选。
	 */
	public static int distinctProviderNames(List<PatternContainer> providers) {
		if (providers == null || providers.isEmpty()) {
			return 0;
		}
		Set<String> names = new HashSet<>();
		for (PatternContainer container : providers) {
			if (container == null) {
				continue;
			}
			String name = ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(container).getString();
			if (name != null) {
				names.add(name.trim());
			}
		}
		return names.size();
	}

	/**
	 * 把样板插进候选供应器，空位多的优先，避免把整棵树的样板全挤到同一台机器上。
	 * 批量上传专用：不记录 last uploaded provider（一批 N 个样板的“最后一个”没有意义）。
	 */
	public static boolean insertPatternIntoProviders(ItemStack pattern, List<PatternContainer> candidates) {
		if (pattern == null || pattern.isEmpty() || candidates == null || candidates.isEmpty()) {
			return false;
		}

		List<PatternContainer> matched = new ArrayList<>(candidates);
		// getAvailableSlots 有重载，方法引用无法在 reversed() 处推断类型，显式声明比较器元素类型。
		Comparator<PatternContainer> bySlotsDesc = Comparator.comparingInt(
				(PatternContainer c) -> ExtendedAEPatternUploadUtil.getAvailableSlots(c)).reversed();
		matched.sort(bySlotsDesc);

		// 查重不在这里做：会把「已存在」和「插不进去」混成同一个 false，
		// 导致调用方误判为失败而把样板塞进背包。查重由调用方在尝试上传前用
		// collectExistingPatterns 一次性完成。
		for (PatternContainer container : matched) {
			ItemStack remain = ExtendedAEPatternUploadUtil
					.insertIntoAccessiblePatternSlots(container, pattern.copy(), null);
			if (remain.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 列出网络中所有终端可见的样板供应器（不筛空位）。
	 * 供“为配方类型挑选机器建立映射”使用：映射只关心名字，机器当下满不满无关。
	 */
	public static List<PatternContainer> listAllProvidersFromGrid(IGrid grid) {
		List<PatternContainer> list = new ArrayList<>();
		if (grid == null) {
			return list;
		}

		for (Class<?> machineClass : grid.getMachineClasses()) {
			if (!PatternContainer.class.isAssignableFrom(machineClass)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
			for (PatternContainer container : grid.getActiveMachines(containerClass)) {
				if (container == null || !container.isVisibleInTerminal()) {
					continue;
				}
				InternalInventory inv = container.getTerminalPatternInventory();
				if (inv == null || inv.size() <= 0) {
					continue;
				}
				list.add(container);
			}
		}
		return list;
	}
}
