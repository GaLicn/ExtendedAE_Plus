package com.extendedae_plus.compat;

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

		GenericStack output = firstItemOutput(recipe);
		if (output == null) {
			return null;
		}

		List<List<GenericStack>> inputs;
		if (crafting && holderOpt.get().value() instanceof CraftingRecipe craftingRecipe) {
			inputs = new ArrayList<>();
			for (var ingredient : craftingRecipe.getIngredients()) {
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
					GenericStack gs = toItemGenericStack(option);
					if (gs != null) {
						candidates.add(gs);
					}
				}
				inputs.add(candidates);
			}
		}

		return new RecipeInfo(recipe, recipe.getId(), crafting, inputs, List.of(output));
	}

	private static GenericStack firstItemOutput(EmiRecipe recipe) {
		for (EmiStack out : recipe.getOutputs()) {
			GenericStack gs = toItemGenericStack(out);
			if (gs != null) {
				return gs;
			}
		}
		return null;
	}

	/** EmiStack → GenericStack；v1 仅支持物品栈，流体返回 null。 */
	private static GenericStack toItemGenericStack(EmiStack stack) {
		try {
			ItemStack item = stack.getItemStack();
			if (item == null || item.isEmpty()) {
				return null;
			}
			return new GenericStack(AEItemKey.of(item), Math.max(1, stack.getAmount()));
		} catch (Throwable ignored) {
			return null;
		}
	}
}
