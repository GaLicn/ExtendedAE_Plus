package com.extendedae_plus.client.emi;

import com.extendedae_plus.compat.EmiRecipeCompat;
import com.extendedae_plus.util.RecipeInfo;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 合成链（EMI 配方树 / BoM）节点的可编码状态判定。
 * <p>
 * 映射表由客户端的 {@code recipe_type_names.json} 维护（见 {@code RecipeTypeMappingScreen}），
 * 所以判定与搜索词都在客户端算，再随 {@code BatchCreateAndUploadPatternC2SPacket} 传给服务端。
 * <p>
 * 判定结果按 {@link EmiRecipe} 身份缓存：{@code fromEmiRecipe} 会重建 3x3 材料布局，
 * 不能在渲染时逐帧调用。
 */
public final class BoMMappingStatus {

	public enum Status {
		/** 可编码，且（处理配方）已有供应器映射。 */
		OK,
		/**
		 * 配方 ID 无法解析成原版配方，样板根本编不出来（服务端会回 recipe_not_found）。
		 * EMI 的 tag 解析配方、世界互动配方都落在这里；加映射也救不了，因此不阻止一键编码，只跳过。
		 */
		NO_RECIPE_ID,
		/** 处理配方缺少配方类型 → 供应器搜索词的映射，会阻止一键编码。 */
		NO_PROVIDER_MAPPING
	}

	private static final Map<EmiRecipe, Status> STATUS_CACHE = new IdentityHashMap<>();
	private static final Map<EmiRecipe, String> SEARCH_KEY_CACHE = new IdentityHashMap<>();
	private static int cachedMappingVersion = -1;
	/** 缓存所属的世界，重进世界后配方注册表会变，必须整体失效。 */
	private static Object cachedLevel;

	private BoMMappingStatus() {}

	public static Status of(EmiRecipe recipe) {
		if (recipe == null) {
			return Status.NO_RECIPE_ID;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			// 没有世界就查不到原版配方，此时的判定不能进缓存，否则重进世界后仍是错的。
			return Status.NO_RECIPE_ID;
		}
		ensureFresh();
		Status cached = STATUS_CACHE.get(recipe);
		if (cached != null) {
			return cached;
		}
		Status status = compute(recipe);
		STATUS_CACHE.put(recipe, status);
		return status;
	}

	/** 映射出的供应器搜索词；无映射或不适用时返回空串（封包约定：空串 = 无映射）。 */
	public static String searchKeyOf(EmiRecipe recipe) {
		if (recipe == null) {
			return "";
		}
		ensureFresh();
		String cached = SEARCH_KEY_CACHE.get(recipe);
		if (cached != null) {
			return cached;
		}
		String key = "";
		Recipe<?> vanilla = resolveVanillaRecipe(recipe);
		if (vanilla != null) {
			String mapped = ExtendedAEPatternUploadUtil.mappedSearchKeyOrNull(vanilla);
			if (mapped != null) {
				key = mapped;
			}
		}
		SEARCH_KEY_CACHE.put(recipe, key);
		return key;
	}

	public static void invalidate() {
		STATUS_CACHE.clear();
		SEARCH_KEY_CACHE.clear();
		cachedMappingVersion = ExtendedAEPatternUploadUtil.getMappingVersion();
		cachedLevel = Minecraft.getInstance().level;
	}

	/** 该配方类型的映射键（即映射管理界面里要填的键），无法解析时返回 null。 */
	public static ResourceLocation mappingKeyOf(EmiRecipe recipe) {
		Recipe<?> vanilla = resolveVanillaRecipe(recipe);
		return vanilla == null ? null : ExtendedAEPatternUploadUtil.getRecipeTypeId(vanilla);
	}

	private static void ensureFresh() {
		int version = ExtendedAEPatternUploadUtil.getMappingVersion();
		Object level = Minecraft.getInstance().level;
		if (version != cachedMappingVersion || level != cachedLevel) {
			STATUS_CACHE.clear();
			SEARCH_KEY_CACHE.clear();
			cachedMappingVersion = version;
			cachedLevel = level;
		}
	}

	private static Status compute(EmiRecipe recipe) {
		Recipe<?> vanilla = resolveVanillaRecipe(recipe);
		if (vanilla == null) {
			return Status.NO_RECIPE_ID;
		}
		// 编码路径还要求能构建出材料布局，否则服务端会拿到空样板。
		RecipeInfo info = EmiRecipeCompat.fromEmiRecipe(recipe);
		if (info == null || info.getRecipeId() == null) {
			return Status.NO_RECIPE_ID;
		}
		// 合成样板走装配矩阵，不需要供应器映射。
		if (vanilla instanceof CraftingRecipe) {
			return Status.OK;
		}
		return ExtendedAEPatternUploadUtil.mappedSearchKeyOrNull(vanilla) == null
			? Status.NO_PROVIDER_MAPPING
			: Status.OK;
	}

	private static Recipe<?> resolveVanillaRecipe(EmiRecipe recipe) {
		if (recipe == null || recipe.getId() == null) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		return mc.level.getRecipeManager().byKey(recipe.getId())
			.map(holder -> (Recipe<?>) holder.value())
			.orElse(null);
	}
}
