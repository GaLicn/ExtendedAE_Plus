package com.extendedae_plus.integration.jei;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 线程安全地缓存并访问 JEI Runtime（纯反射，避免在未安装 JEI 时触发类加载）。
 */
public final class JeiRuntimeProxy {
	// 持有为 Object，避免类加载
	private static volatile Object RUNTIME;

	private JeiRuntimeProxy() {}

	static void setRuntime(Object runtime) {
		RUNTIME = runtime;
	}

	@Nullable
	public static Object get() {
		return RUNTIME;
	}

	public static Optional<?> getIngredientUnderMouse() {
		Object rt = RUNTIME;
		if (rt == null) return Optional.empty();
		try {
			Method getIngredientListOverlay = rt.getClass().getMethod("getIngredientListOverlay");
			Object list = getIngredientListOverlay.invoke(rt);
			if (list != null) {
				Method m = list.getClass().getMethod("getIngredientUnderMouse");
				Object opt = m.invoke(list);
				if (opt instanceof Optional<?> o && o.isPresent()) return o;
			}
			Method getBookmarkOverlay = rt.getClass().getMethod("getBookmarkOverlay");
			Object bm = getBookmarkOverlay.invoke(rt);
			if (bm != null) {
				Method m = bm.getClass().getMethod("getIngredientUnderMouse");
				Object opt = m.invoke(bm);
				if (opt instanceof Optional<?> o && o.isPresent()) return o;
			}
		} catch (Throwable ignored) {}
		return Optional.empty();
	}

	public static Optional<?> getIngredientUnderMouse(double mouseX, double mouseY) {
		Object rt = RUNTIME;
		if (rt == null) return Optional.empty();
		try {
			Method getRecipesGui = rt.getClass().getMethod("getRecipesGui");
			Object gui = getRecipesGui.invoke(rt);
			if (gui == null) return Optional.empty();
			Object ingredientManager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Class<?> vanillaTypes = Class.forName("mezz.jei.api.constants.VanillaTypes");
			Object itemType = vanillaTypes.getField("ITEM_STACK").get(null);
			Method getUnder = gui.getClass().getMethod("getIngredientUnderMouse", itemType.getClass());
			Object valueOpt = getUnder.invoke(gui, itemType);
			if (!(valueOpt instanceof Optional<?> value) || value.isEmpty()) return Optional.empty();
			Method createTyped = ingredientManager.getClass().getMethod("createTypedIngredient", itemType.getClass(), Object.class);
			Object typedOpt = createTyped.invoke(ingredientManager, itemType, value.get());
			return typedOpt instanceof Optional<?> o ? o : Optional.empty();
		} catch (Throwable ignored) {}
		return Optional.empty();
	}

	public static boolean isJeiCheatModeEnabled() {
		try {
			Class<?> internal = Class.forName("mezz.jei.common.Internal");
			Object toggle = internal.getMethod("getClientToggleState").invoke(null);
			Method isCheat = toggle.getClass().getMethod("isCheatItemsEnabled");
			Object r = isCheat.invoke(toggle);
			return r instanceof Boolean b && b;
		} catch (Throwable t) {
			return false;
		}
	}

	public static String getTypedIngredientDisplayName(Object typed) {
		Object rt = RUNTIME;
		if (rt == null || typed == null) return "";
		try {
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Method getType = typed.getClass().getMethod("getType");
			Object type = getType.invoke(typed);
			Method getHelper = manager.getClass().getMethod("getIngredientHelper", type.getClass());
			Object helper = getHelper.invoke(manager, type);
			Method getIngredient = typed.getClass().getMethod("getIngredient");
			Object ingredient = getIngredient.invoke(typed);
			Object display = helper.getClass().getMethod("getDisplayName", ingredient.getClass()).invoke(helper, ingredient);
			if (display == null) return "";
			try {
				Class<?> comp = Class.forName("net.minecraft.network.chat.Component");
				if (comp.isInstance(display)) {
					Method getString = comp.getMethod("getString");
					Object s = getString.invoke(display);
					return s == null ? "" : s.toString();
				}
			} catch (Throwable ignored) {}
			return display.toString();
		} catch (Throwable ignored) {}
		return "";
	}

	public static List<?> getBookmarkList() {
		Object rt = RUNTIME;
		if (rt == null) return Collections.emptyList();
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return Collections.emptyList();
			try {
				Field f = overlay.getClass().getDeclaredField("bookmarkList");
				f.setAccessible(true);
				Object list = f.get(overlay);
				Method getElements = list.getClass().getMethod("getElements");
				Object elements = getElements.invoke(list);
				if (elements instanceof List<?> l) {
					// map(IElement::getTypedIngredient)
					try {
						Method getTyped = Class.forName("mezz.jei.gui.overlay.elements.IElement").getMethod("getTypedIngredient");
						return l.stream().map(e -> {
							try { return getTyped.invoke(e); } catch (Throwable ignored) { return null; }
						}).filter(x -> x != null).toList();
					} catch (Throwable ignored) {}
				}
			} catch (Throwable ignored) {}
		} catch (Throwable ignored) {}
		return Collections.emptyList();
	}

	public static void addBookmark(ItemStack stack) {
		Object rt = RUNTIME;
		if (rt == null || stack == null || stack.isEmpty()) return;
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return;
			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object list = f.get(overlay);
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", itemType.getClass(), Object.class);
			Object typedOpt = createTyped.invoke(manager, itemType, stack);
			if (typedOpt instanceof Optional<?> opt && opt.isPresent()) {
				Object typed = opt.get();
				Class<?> ibCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
				Method create = null;
				for (Method m : ibCls.getMethods()) {
					if (m.getName().equals("create") && m.getParameterCount() == 2) {
						create = m; break;
					}
				}
				if (create != null) {
					Object bookmark = create.invoke(null, typed, manager);
					list.getClass().getMethod("add", ibCls).invoke(list, bookmark);
				}
			}
		} catch (Throwable ignored) {}
	}

	public static void addBookmark(FluidStack fluidStack) {
		Object rt = RUNTIME;
		if (rt == null) return;
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return;
			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object list = f.get(overlay);
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Object fluidType = Class.forName("mezz.jei.api.neoforge.NeoForgeTypes").getField("FLUID_STACK").get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", fluidType.getClass(), Object.class);
			Object typedOpt = createTyped.invoke(manager, fluidType, fluidStack);
			if (typedOpt instanceof Optional<?> opt && opt.isPresent()) {
				Object typed = opt.get();
				Class<?> ibCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
				Method create = null;
				for (Method m : ibCls.getMethods()) {
					if (m.getName().equals("create") && m.getParameterCount() == 2) { create = m; break; }
				}
				if (create != null) {
					Object bookmark = create.invoke(null, typed, manager);
					list.getClass().getMethod("add", ibCls).invoke(list, bookmark);
				}
			}
		} catch (Throwable ignored) {}
	}

	public static void addBookmark(Object chemicalStack) {
		if (!ModList.get().isLoaded("mekanism") && !ModList.get().isLoaded("appmek")) return;
		Object rt = RUNTIME;
		if (rt == null || chemicalStack == null) return;
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return;
			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object list = f.get(overlay);
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			String mekanismJeiClass = "mekanism.client.recipe_viewer.jei.MekanismJEI";
			Class<?> jeiCls = Class.forName(mekanismJeiClass);
			Field typeField = null;
			if ("mekanism.api.chemical.ChemicalStack".equals(chemicalStack.getClass().getName())) {
				typeField = jeiCls.getField("TYPE_CHEMICAL");
			}
			if (typeField == null) return;
			Object typeConst = typeField.get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", typeConst.getClass(), Object.class);
			Object typedOpt = createTyped.invoke(manager, typeConst, chemicalStack);
			if (typedOpt instanceof Optional<?> opt && opt.isPresent()) {
				Object typed = opt.get();
				Class<?> ibCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
				Method create = null;
				for (Method m : ibCls.getMethods()) {
					if (m.getName().equals("create") && m.getParameterCount() == 2) { create = m; break; }
				}
				if (create != null) {
					Object bookmark = create.invoke(null, typed, manager);
					list.getClass().getMethod("add", ibCls).invoke(list, bookmark);
				}
			}
		} catch (Throwable ignored) {}
	}

	public static void removeBookmark(ItemStack stack) {
		Object rt = RUNTIME;
		if (rt == null || stack == null || stack.isEmpty()) return;
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return;
			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object list = f.get(overlay);
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", itemType.getClass(), Object.class);
			Object typedOpt = createTyped.invoke(manager, itemType, stack);
			if (typedOpt instanceof Optional<?> opt && opt.isPresent()) {
				Object typed = opt.get();
				Class<?> ibCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
				Method create = null;
				for (Method m : ibCls.getMethods()) {
					if (m.getName().equals("create") && m.getParameterCount() == 2) { create = m; break; }
				}
				if (create != null) {
					Object bookmark = create.invoke(null, typed, manager);
					list.getClass().getMethod("remove", ibCls).invoke(list, bookmark);
				}
			}
		} catch (Throwable ignored) {}
	}
}
