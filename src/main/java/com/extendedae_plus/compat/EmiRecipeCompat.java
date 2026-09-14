package com.extendedae_plus.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.util.RecipeInfo;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

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
	 * 处理配方：材料/产物取自 EMI 栈（支持物品与流体）。
	 */
	public static RecipeInfo fromEmiRecipe(EmiRecipe recipe) {
		if (recipe == null || recipe.getId() == null || recipe.getOutputs().isEmpty()) {
			return null;
		}

		var mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}

		// 1.20.1：byKey 直接返回 Optional<Recipe<?>>；要求配方存在于客户端注册表，
		// 否则服务端也无法解析（recipe_not_found），直接跳过。
		var recipeOpt = mc.level.getRecipeManager().byKey(recipe.getId());
		if (recipeOpt.isEmpty()) {
			return null;
		}
		net.minecraft.world.item.crafting.Recipe<?> vanilla = recipeOpt.get();
		boolean crafting = vanilla instanceof CraftingRecipe;

		GenericStack output = firstOutput(recipe);
		if (output == null) {
			return null;
		}

		List<List<GenericStack>> inputs;
		if (crafting && vanilla instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
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
			for (var ingredient : ((CraftingRecipe) vanilla).getIngredients()) {
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

		return new RecipeInfo(vanilla, crafting, inputs, List.of(output));
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
	 * EmiStack → GenericStack，支持物品与流体。
	 * 流体量纲换算：EMI 使用原版 droplets（81000/桶），AE2 使用 mB（1000/桶）。
	 */
	private static GenericStack toGenericStack(EmiStack stack) {
		try {
			ItemStack item = stack.getItemStack();
			if (item != null && !item.isEmpty()) {
				return new GenericStack(AEItemKey.of(item), Math.max(1, stack.getAmount()));
			}
			if (stack.getKey() instanceof Fluid fluid && fluid != Fluids.EMPTY) {
				long amount = stack.getAmount();
				long mb = Math.max(1, amount / 81);
				return new GenericStack(AEFluidKey.of(fluid), mb);
			}
		} catch (Throwable ignored) {
		}
		return null;
	}
}
