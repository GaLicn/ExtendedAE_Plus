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

	private static Class<?> eap$getIngredientTypeClass() throws ClassNotFoundException {
		return Class.forName("mezz.jei.api.ingredients.IIngredientType");
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
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Method getRecipesGui = rt.getClass().getMethod("getRecipesGui");
			Object gui = getRecipesGui.invoke(rt);
			if (gui == null) return Optional.empty();
			Object ingredientManager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Class<?> vanillaTypes = Class.forName("mezz.jei.api.constants.VanillaTypes");
			Object itemType = vanillaTypes.getField("ITEM_STACK").get(null);
			Method getUnder = gui.getClass().getMethod("getIngredientUnderMouse", ingredientTypeClass);
			Object valueOpt = getUnder.invoke(gui, itemType);
			if (!(valueOpt instanceof Optional<?> value) || value.isEmpty()) return Optional.empty();
			Method createTyped = ingredientManager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
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
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Method getType = typed.getClass().getMethod("getType");
			Object type = getType.invoke(typed);
			Method getHelper = manager.getClass().getMethod("getIngredientHelper", ingredientTypeClass);
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

	/**
	 * 获取鼠标下的配方书签（RecipeBookmark），用于 Ctrl+Q 直接识别“配方书签”而非普通配料。
	 */
	public static Optional<?> getRecipeBookmarkUnderMouse() {
		Object rt = RUNTIME;
		if (rt == null) return Optional.empty();
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) return Optional.empty();

			// JEI 1.21 优先路径：直接拿鼠标下 clickable element 的 bookmark
			try {
				double mouseX = getGuiMouseX();
				double mouseY = getGuiMouseY();
				Object clickableStream = overlay.getClass()
					.getMethod("getIngredientUnderMouse", double.class, double.class)
					.invoke(overlay, mouseX, mouseY);
				if (clickableStream instanceof java.util.stream.Stream<?> stream) {
					Object firstClickable = stream.findFirst().orElse(null);
					if (firstClickable != null) {
						Object element = firstClickable.getClass().getMethod("getElement").invoke(firstClickable);
						if (element != null) {
							Object bookmarkOpt = element.getClass().getMethod("getBookmark").invoke(element);
							if (bookmarkOpt instanceof Optional<?> b && b.isPresent()) {
								Object bookmark = b.get();
								if (bookmark != null && "RecipeBookmark".equals(bookmark.getClass().getSimpleName())) {
									return Optional.of(bookmark);
								}
							}
						}
					}
				}
			} catch (Throwable ignored) {
			}

			// 兼容回退：基于 typed ingredient 匹配 bookmarkList 元素
			Object ingredientOpt = overlay.getClass().getMethod("getIngredientUnderMouse").invoke(overlay);
			if (!(ingredientOpt instanceof Optional<?> opt) || opt.isEmpty()) return Optional.empty();
			Object hoveredIngredient = opt.get();

			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object bookmarkList = f.get(overlay);
			if (bookmarkList == null) return Optional.empty();

			Object elementsObj = bookmarkList.getClass().getMethod("getElements").invoke(bookmarkList);
			if (!(elementsObj instanceof List<?> elements)) return Optional.empty();

			for (Object element : elements) {
				if (element == null) continue;
				Object typedIngredient = null;
				try {
					typedIngredient = element.getClass().getMethod("getTypedIngredient").invoke(element);
				} catch (Throwable ignored) {
				}
				if (typedIngredient == null || !typedIngredient.equals(hoveredIngredient)) continue;

				Object bookmarkOpt = null;
				try {
					bookmarkOpt = element.getClass().getMethod("getBookmark").invoke(element);
				} catch (Throwable ignored) {
				}
				if (bookmarkOpt instanceof Optional<?> b && b.isPresent()) {
					Object bookmark = b.get();
					if (bookmark != null && "RecipeBookmark".equals(bookmark.getClass().getSimpleName())) {
						return Optional.of(bookmark);
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return Optional.empty();
	}

	private static double getGuiMouseX() {
		try {
			Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
			Object mc = mcCls.getMethod("getInstance").invoke(null);
			Object mouseHandler = mcCls.getField("mouseHandler").get(mc);
			Object window = mcCls.getMethod("getWindow").invoke(mc);

			double xpos = (double) mouseHandler.getClass().getMethod("xpos").invoke(mouseHandler);
			int guiW = (int) window.getClass().getMethod("getGuiScaledWidth").invoke(window);
			int screenW = (int) window.getClass().getMethod("getScreenWidth").invoke(window);
			if (screenW <= 0) return xpos;
			return xpos * ((double) guiW / (double) screenW);
		} catch (Throwable ignored) {
			return 0.0D;
		}
	}

	private static double getGuiMouseY() {
		try {
			Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
			Object mc = mcCls.getMethod("getInstance").invoke(null);
			Object mouseHandler = mcCls.getField("mouseHandler").get(mc);
			Object window = mcCls.getMethod("getWindow").invoke(mc);

			double ypos = (double) mouseHandler.getClass().getMethod("ypos").invoke(mouseHandler);
			int guiH = (int) window.getClass().getMethod("getGuiScaledHeight").invoke(window);
			int screenH = (int) window.getClass().getMethod("getScreenHeight").invoke(window);
			if (screenH <= 0) return ypos;
			return ypos * ((double) guiH / (double) screenH);
		} catch (Throwable ignored) {
			return 0.0D;
		}
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
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
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
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Object fluidType = Class.forName("mezz.jei.api.neoforge.NeoForgeTypes").getField("FLUID_STACK").get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
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
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			String mekanismJeiClass = "mekanism.client.recipe_viewer.jei.MekanismJEI";
			Class<?> jeiCls = Class.forName(mekanismJeiClass);
			Field typeField = null;
			if ("mekanism.api.chemical.ChemicalStack".equals(chemicalStack.getClass().getName())) {
				typeField = jeiCls.getField("TYPE_CHEMICAL");
			}
			if (typeField == null) return;
			Object typeConst = typeField.get(null);
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
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
