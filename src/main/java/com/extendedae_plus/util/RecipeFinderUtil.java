package com.extendedae_plus.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JEI 运行时查找配方，提取输入输出槽位与数量信息。
 */
public final class RecipeFinderUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAE Plus - RecipeFinder");

	private RecipeFinderUtil() {
	}

	public static List<RecipeInfo> findRecipesByIngredient(ITypedIngredient<?> ingredient) {
		if (ingredient == null) {
			return List.of();
		}

		IJeiRuntime jeiRuntime = JeiRuntimeCompat.getRuntime();
		if (jeiRuntime == null) {
			LOGGER.warn("[RecipeFinder] JEI runtime not available");
			return List.of();
		}

		IJeiHelpers jeiHelpers = jeiRuntime.getJeiHelpers();
		IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
		IFocusFactory focusFactory = jeiHelpers.getFocusFactory();
		IFocus<?> outputFocus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, ingredient);
		List<RecipeInfo> results = new ArrayList<>();

		// 1) Crafting recipes
		try {
			collectRecipes(recipeManager, RecipeTypes.CRAFTING, outputFocus, focusFactory, true, results);
		} catch (RuntimeException e) {
			LOGGER.warn("[RecipeFinder] Error searching crafting recipes", e);
		}

		// 2) Other recipe types
		try {
			for (RecipeType<?> recipeType : jeiHelpers.getAllRecipeTypes().toList()) {
				if (RecipeTypes.CRAFTING.equals(recipeType)) {
					continue;
				}
				try {
					// JEI 的 getAllRecipeTypes 只暴露通配符类型，这里统一抹成 Object 交给泛型辅助方法处理。
					@SuppressWarnings("unchecked")
					RecipeType<Object> typedType = (RecipeType<Object>) recipeType;
					collectRecipes(recipeManager, typedType, outputFocus, focusFactory, false, results);
				} catch (RuntimeException e) {
					// 单个分类失败不影响其余配方类型的检索，但留日志便于定位具体分类。
					LOGGER.debug("[RecipeFinder] Skipped recipe type {}", recipeType, e);
				}
			}
		} catch (RuntimeException e) {
			LOGGER.warn("[RecipeFinder] Error searching other recipes", e);
		}

		return results;
	}

	/**
	 * 在保持配方对象静态类型的前提下遍历某个配方类型，避免退化成 Object 后只能靠反射取值。
	 */
	private static <T> void collectRecipes(
		IRecipeManager recipeManager,
		RecipeType<T> recipeType,
		IFocus<?> outputFocus,
		IFocusFactory focusFactory,
		boolean isCrafting,
		List<RecipeInfo> results
	) {
		IRecipeCategory<T> category = recipeManager.getRecipeCategory(recipeType);
		recipeManager.createRecipeLookup(recipeType)
			.limitFocus(List.of(outputFocus))
			.get()
			.forEach(recipeObj -> recipeManager
				.createRecipeLayoutDrawable(category, recipeObj, focusFactory.getEmptyFocusGroup())
				.ifPresent(layout -> {
					RecipeInfo info = extractRecipeInfo(category, recipeObj, layout, isCrafting);
					if (info != null) {
						results.add(info);
					}
				}));
	}

	public static RecipeInfo selectBestRecipe(List<RecipeInfo> recipes) {
		if (recipes == null || recipes.isEmpty()) {
			return null;
		}
		for (RecipeInfo info : recipes) {
			if (info.isCraftingRecipe()) {
				return info;
			}
		}
		return recipes.get(0);
	}

	private static <T> RecipeInfo extractRecipeInfo(
		IRecipeCategory<T> category,
		T recipeObj,
		IRecipeLayoutDrawable<T> layout,
		boolean isCrafting
	) {
		try {
			// JEI 默认实现从 RecipeHolder.id() 取 ID，非原版配方可在自己的分类里覆写，
			// 直接走接口即可，不需要反射推断实现类的字段名。
			ResourceLocation recipeId = category.getRegistryName(recipeObj);
			if (recipeId == null) {
				return null;
			}

			IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
			List<List<GenericStack>> inputs = new ArrayList<>();
			List<GenericStack> outputs = new ArrayList<>();

			for (IRecipeSlotView slot : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
				List<GenericStack> slotStacks = new ArrayList<>();
				for (ITypedIngredient<?> typed : slot.getAllIngredientsList()) {
					if (typed == null) {
						continue;
					}
					GenericStack stack = convertToGenericStack(typed);
					if (stack != null) {
						slotStacks.add(stack);
					}
				}
				inputs.add(slotStacks);
			}

			for (IRecipeSlotView slot : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
				for (ITypedIngredient<?> typed : slot.getAllIngredientsList()) {
					if (typed == null) {
						continue;
					}
					GenericStack stack = convertToGenericStack(typed);
					if (stack != null) {
						outputs.add(stack);
					}
				}
			}

			return new RecipeInfo(recipeObj, recipeId, isCrafting, inputs, outputs);
		} catch (Throwable t) {
			return null;
		}
	}

	
	private static GenericStack convertToGenericStack(ITypedIngredient<?> typedIngredient) {
		if (typedIngredient.getType() == VanillaTypes.ITEM_STACK) {
			ItemStack itemStack = (ItemStack) typedIngredient.getIngredient();
			if (!itemStack.isEmpty()) {
				AEItemKey itemKey = AEItemKey.of(itemStack);
				if (itemKey != null) {
					return new GenericStack(itemKey, itemStack.getCount());
				}
			}
		} else if (typedIngredient.getType() == NeoForgeTypes.FLUID_STACK) {
			FluidStack fluidStack = (FluidStack) typedIngredient.getIngredient();
			if (!fluidStack.isEmpty()) {
				AEFluidKey fluidKey = AEFluidKey.of(fluidStack);
				if (fluidKey != null) {
					return new GenericStack(fluidKey, fluidStack.getAmount());
				}
			}
		}
		return null;
	}
}
