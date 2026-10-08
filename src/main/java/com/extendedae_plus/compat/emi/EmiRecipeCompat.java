package com.extendedae_plus.compat.emi;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.util.RecipeInfo;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI 配方查询与 {@link RecipeInfo} 构建工厂（对应 JEI 路径的 {@code RecipeFinderUtil}）。
 * 仅在 ModList 确认 emi 已加载时调用；类内 EMI 引用随方法调用惰性解析，
 * 未装 EMI 时加载本类不会触发 dev.emi 类解析。
 */
public final class EmiRecipeCompat {
	private EmiRecipeCompat() {}

	/**
	 * 悬浮产物 → 产出它的配方列表。
	 * 对齐 JEI 路径的 OUTPUT focus 语义（findRecipesByIngredient 实为按输出反查）。
	 */
	public static List<RecipeInfo> findRecipesByOutput(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return List.of();
		}
		try {
			List<EmiRecipe> recipes = EmiApi.getRecipeManager().getRecipesByOutput(EmiStack.of(stack));
			List<RecipeInfo> results = new ArrayList<>();
			for (EmiRecipe recipe : recipes) {
				RecipeInfo info = fromEmiRecipe(recipe);
				if (info != null) {
					results.add(info);
				}
			}
			return results;
		} catch (Throwable ignored) {
			return List.of();
		}
	}

	/**
	 * EmiRecipe → RecipeInfo。
	 * 合成配方：材料布局取自客户端同步的原版注册表（行主序、含空槽），保证 3x3 槽位保真；
	 * 处理配方：材料/产物取自 EMI 栈（v1 仅支持物品栈，流体条目跳过）。
	 */
	public static RecipeInfo fromEmiRecipe(EmiRecipe recipe) {
		if (recipe == null || recipe.getId() == null || recipe.getOutputs().isEmpty()) {
			return null;
		}

		var mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}

		var holderOpt = mc.level.getRecipeManager().byKey(recipe.getId());
		boolean crafting = holderOpt.isPresent() && holderOpt.get().value() instanceof CraftingRecipe;

		GenericStack output = firstOutput(recipe);
		if (output == null) {
			return null;
		}

		List<List<GenericStack>> inputs;
		if (crafting && holderOpt.get().value() instanceof ShapedRecipe shaped) {
			// 有序配方：getIngredients() 是宽×高的紧凑行主序，必须按宽度还原到 3x3 的真实槽位
			// （如 1x3 竖条形的材料应位于槽位 0,3,6），否则 AE2 解码时 matches() 复验失败 → 无效样板。
			var ingredients = shaped.getIngredients();
			int width = Math.max(1, shaped.getWidth());
			List<List<GenericStack>> grid = new ArrayList<>(9);
			for (int i = 0; i < 9; i++) {
				grid.add(new ArrayList<>());
			}
			for (int i = 0; i < ingredients.size() && i < 9; i++) {
				int slot = (i / width) * 3 + (i % width);
				ItemStack[] items = ingredients.get(i).getItems();
				if (items.length > 0 && !items[0].isEmpty()) {
					GenericStack gs = GenericStack.fromItemStack(items[0].copy());
					if (gs != null) {
						grid.get(slot).add(gs);
					}
				}
			}
			inputs = grid;
		} else if (crafting) {
			// 无序合成：顺序无关，紧凑填充即可
			inputs = new ArrayList<>();
			for (var ingredient : ((CraftingRecipe) holderOpt.get().value()).getIngredients()) {
				List<GenericStack> candidates = new ArrayList<>();
				ItemStack[] items = ingredient.getItems();
				if (items.length > 0 && !items[0].isEmpty()) {
					GenericStack gs = GenericStack.fromItemStack(items[0].copy());
					if (gs != null) {
						candidates.add(gs);
					}
				}
				inputs.add(candidates);
			}
		} else {
			inputs = new ArrayList<>();
			for (EmiIngredient slot : recipe.getInputs()) {
				List<GenericStack> candidates = new ArrayList<>();
				for (EmiStack option : slot.getEmiStacks()) {
					GenericStack gs = toGenericStack(option);
					if (gs != null) {
						candidates.add(gs);
					}
				}
				inputs.add(candidates);
			}
		}

		return new RecipeInfo(recipe, recipe.getId(), crafting, inputs, List.of(output));
	}

	private static GenericStack firstOutput(EmiRecipe recipe) {
		for (EmiStack out : recipe.getOutputs()) {
			GenericStack gs = toGenericStack(out);
			if (gs != null) {
				return gs;
			}
		}
		return null;
	}

	/**
	 * EmiStack → GenericStack，物品、流体与 Mekanism 化学品统一交由 AE2 官方的
	 * EMI 转换器注册表解析：AE2 自身登记了物品与流体转换器，AppMek 登记了化学品转换器。
	 *
	 * <p>各转换器按 EMI 栈的内部形态取值，本类不假定 {@code EmiStack#getKey} 的具体类型，
	 * 也不自行做量纲换算——EMI 在 Forge / NeoForge 上以 mB 计量
	 * （{@code FluidUnit.literDivisor()} 返回 1），与 AE2 一致，无需按 Fabric 的
	 * droplets 口径（81000/桶）除以 81；该换算会使流体量缩小 81 倍。</p>
	 *
	 * <p>包内私有：入参声明为 {@link Object} 以避免方法签名直接引用 EMI 类型，
	 * 外部（如 EMI 中键下单）一律经 {@link EmiHelper#getSidebarGenericStackUnderMouse} 调用，
	 * 不直接接触本方法。入参不是 EmiStack 时返回 null。</p>
	 */
	@Nullable
	static GenericStack toGenericStack(@Nullable Object stack) {
		if (!(stack instanceof EmiStack emiStack)) {
			return null;
		}
		for (EmiStackConverter converter : EmiStackConverters.getConverters()) {
			GenericStack converted = converter.toGenericStack(emiStack);
			if (converted != null) {
				return converted;
			}
		}
		return null;
	}
}
