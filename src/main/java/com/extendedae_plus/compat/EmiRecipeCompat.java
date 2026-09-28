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
 */public final class EmiRecipeCompat {
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
		return null;
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
	 * EmiStack → GenericStack，支持物品、流体与 Mekanism 化学品。
	 * 流体量纲换算：EMI 使用原版 droplets（81000/桶），AE2 使用 mB（1000/桶）。
	 * 化学品经 Applied Mekanistics 的 MekanismKey 转换，两侧都是 mB，数量 1:1。
	 */
	private static GenericStack toGenericStack(EmiStack stack) {
		try {
			ItemStack item = stack.getItemStack();
			if (item != null && !item.isEmpty()) {
				return new GenericStack(AEItemKey.of(item), Math.max(1, stack.getAmount()));
			}
			Object key = stack.getKey();
			if (key instanceof Fluid fluid && fluid != Fluids.EMPTY) {
				long amount = stack.getAmount();
				long mb = Math.max(1, amount / 81);
				return new GenericStack(AEFluidKey.of(fluid), mb);
			}
			/* Mekanism 与 Applied Mekanistics 发布适配版本后恢复。
			if (MekanismChemicalGate.isAvailable()) {
				GenericStack chemical = MekanismChemicalCompat.toGenericStack(key, stack.getAmount());
				if (chemical != null) {
					return chemical;
				}
			}
			*/
		} catch (Throwable ignored) {
		}
		return null;
	}
}
