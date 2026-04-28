package com.extendedae_plus.integration.jei;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
		return getIngredientUnderMouse(getGuiMouseX(), getGuiMouseY());
	}

	public static Optional<?> getIngredientUnderMouse(double mouseX, double mouseY) {
		Object rt = RUNTIME;
		if (rt == null) {
			return Optional.empty();
		}

		Optional<Object> ingredient = getRuntimeComponent(rt, "getIngredientListOverlay")
			.flatMap(overlay -> getTypedIngredientFromClickableSource(overlay, mouseX, mouseY));
		if (ingredient.isPresent()) {
			return ingredient;
		}

		Optional<Object> bookmarkOverlay = getRuntimeComponent(rt, "getBookmarkOverlay");
		ingredient = bookmarkOverlay.flatMap(overlay -> getTypedIngredientFromClickableSource(overlay, mouseX, mouseY));
		if (ingredient.isPresent()) {
			return ingredient;
		}

		ingredient = getRuntimeComponent(rt, "getIngredientListOverlay")
			.flatMap(overlay -> invokeOptionalValue(overlay, "getIngredientUnderMouse"));
		if (ingredient.isPresent()) {
			return ingredient;
		}

		ingredient = bookmarkOverlay.flatMap(overlay -> invokeOptionalValue(overlay, "getIngredientUnderMouse"));
		if (ingredient.isPresent()) {
			return ingredient;
		}

		Optional<Object> bookmark = bookmarkOverlay.flatMap(overlay -> getBookmarkUnderMouse(overlay, mouseX, mouseY));
		if (bookmark.isPresent()) {
			ingredient = getTypedIngredientFromBookmark(bookmark.get());
			if (ingredient.isPresent()) {
				return ingredient;
			}
		}

		try {
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Method getRecipesGui = rt.getClass().getMethod("getRecipesGui");
			Object gui = getRecipesGui.invoke(rt);
			if (gui == null) {
				return Optional.empty();
			}
			Object ingredientManager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			Class<?> vanillaTypes = Class.forName("mezz.jei.api.constants.VanillaTypes");
			Object itemType = vanillaTypes.getField("ITEM_STACK").get(null);
			Method getUnder = gui.getClass().getMethod("getIngredientUnderMouse", ingredientTypeClass);
			Object valueOpt = getUnder.invoke(gui, itemType);
			if (!(valueOpt instanceof Optional<?> value) || value.isEmpty()) {
				return Optional.empty();
			}
			Method createTyped = ingredientManager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
			Object typedOpt = createTyped.invoke(ingredientManager, itemType, value.get());
			return typedOpt instanceof Optional<?> o ? o : Optional.empty();
		} catch (Throwable ignored) {
		}
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
		if (rt == null || typed == null) {
			return "";
		}
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
			if (display == null) {
				return "";
			}
			try {
				Class<?> comp = Class.forName("net.minecraft.network.chat.Component");
				if (comp.isInstance(display)) {
					Method getString = comp.getMethod("getString");
					Object s = getString.invoke(display);
					return s == null ? "" : s.toString();
				}
			} catch (Throwable ignored) {
			}
			return display.toString();
		} catch (Throwable ignored) {
		}
		return "";
	}

	public static List<?> getBookmarkList() {
		Object rt = RUNTIME;
		if (rt == null) {
			return Collections.emptyList();
		}
		try {
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) {
				return Collections.emptyList();
			}
			try {
				Field f = overlay.getClass().getDeclaredField("bookmarkList");
				f.setAccessible(true);
				Object list = f.get(overlay);
				Method getElements = list.getClass().getMethod("getElements");
				Object elements = getElements.invoke(list);
				if (elements instanceof List<?> l) {
					try {
						Method getTyped = Class.forName("mezz.jei.gui.overlay.elements.IElement").getMethod("getTypedIngredient");
						return l.stream().map(e -> {
							try {
								return getTyped.invoke(e);
							} catch (Throwable ignored) {
								return null;
							}
						}).filter(x -> x != null).toList();
					} catch (Throwable ignored) {
					}
				}
			} catch (Throwable ignored) {
			}
		} catch (Throwable ignored) {
		}
		return Collections.emptyList();
	}

	/**
	 * 获取鼠标下的配方书签（RecipeBookmark），用于 Ctrl+Q 直接识别“配方书签”而非普通配料。
	 */
	public static Optional<?> getRecipeBookmarkUnderMouse() {
		Optional<?> bookmark = getBookmarkUnderMouse();
		if (bookmark.isPresent() && isRecipeBookmark(bookmark.get())) {
			return bookmark;
		}
		return Optional.empty();
	}

	public static Optional<?> getBookmarkUnderMouse() {
		Object rt = RUNTIME;
		if (rt == null) {
			return Optional.empty();
		}
		return getRuntimeComponent(rt, "getBookmarkOverlay")
			.flatMap(overlay -> getBookmarkUnderMouse(overlay, getGuiMouseX(), getGuiMouseY()));
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
			if (screenW <= 0) {
				return xpos;
			}
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
			if (screenH <= 0) {
				return ypos;
			}
			return ypos * ((double) guiH / (double) screenH);
		} catch (Throwable ignored) {
			return 0.0D;
		}
	}

	public static void addBookmark(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		try {
			Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
			eap$addBookmarkInternal(itemType, stack);
		} catch (Throwable ignored) {
		}
	}

	public static void addBookmark(FluidStack fluidStack) {
		if (fluidStack == null) {
			return;
		}
		try {
			Object fluidType = Class.forName("mezz.jei.api.neoforge.NeoForgeTypes").getField("FLUID_STACK").get(null);
			eap$addBookmarkInternal(fluidType, fluidStack);
		} catch (Throwable ignored) {
		}
	}

	public static void addBookmark(Object chemicalStack) {
		if ((!ModList.get().isLoaded("mekanism") && !ModList.get().isLoaded("appmek")) || chemicalStack == null) {
			return;
		}
		try {
			String mekanismJeiClass = "mekanism.client.recipe_viewer.jei.MekanismJEI";
			Class<?> jeiCls = Class.forName(mekanismJeiClass);
			Field typeField = null;
			if ("mekanism.api.chemical.ChemicalStack".equals(chemicalStack.getClass().getName())) {
				typeField = jeiCls.getField("TYPE_CHEMICAL");
			}
			if (typeField == null) {
				return;
			}
			Object typeConst = typeField.get(null);
			eap$addBookmarkInternal(typeConst, chemicalStack);
		} catch (Throwable ignored) {
		}
	}

	/**
	 * 内部方法：将指定类型的原料添加到 JEI 书签列表
	 * 兼容新旧版本 JEI：
	 * - 新版本 (>= 19.27.0.336)：使用 BookmarkFactory.create()
	 * - 旧版本 (<= 19.27.0.335)：使用 IngredientBookmark.create()
	 * @param ingredientType 原料类型（如 ITEM_STACK、FLUID_STACK 等）
	 * @param ingredient 原料对象（如 ItemStack、FluidStack 等）
	 */
	private static void eap$addBookmarkInternal(Object ingredientType, Object ingredient) {
		Object rt = RUNTIME;
		if (rt == null) {
			return;
		}
		try {
			// 获取书签覆盖层对象
			Object overlay = rt.getClass().getMethod("getBookmarkOverlay").invoke(rt);
			if (overlay == null) {
				return;
			}
			// 获取书签列表对象
			Field f = overlay.getClass().getDeclaredField("bookmarkList");
			f.setAccessible(true);
			Object list = f.get(overlay);
			// 获取原料管理器
			Class<?> ingredientTypeClass = eap$getIngredientTypeClass();
			Object manager = rt.getClass().getMethod("getIngredientManager").invoke(rt);
			// 创建类型化的原料对象
			Method createTyped = manager.getClass().getMethod("createTypedIngredient", ingredientTypeClass, Object.class);
			Object typedOpt = createTyped.invoke(manager, ingredientType, ingredient);
			if (typedOpt instanceof Optional<?> opt && opt.isPresent()) {
				Object typed = opt.get();
				Object bookmark = null;

				// 【新版本 JEI >= JEI 19.27.0.336】使用 BookmarkFactory 创建书签
				try {
					Field factoryField = list.getClass().getDeclaredField("bookmarkFactory");
					factoryField.setAccessible(true);
					Object factory = factoryField.get(list);
					Method create = factory.getClass().getMethod("create", Class.forName("mezz.jei.api.ingredients.ITypedIngredient"));
					bookmark = create.invoke(factory, typed);
				} catch (NoSuchFieldException e) {
					// 【旧版本 <= JEI 19.27.0.335】使用 IngredientBookmark.create() 静态方法创建书签
					Class<?> ingredientBookmarkCls = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
					Method create = ingredientBookmarkCls.getMethod("create", Class.forName("mezz.jei.api.ingredients.ITypedIngredient"), Class.forName("mezz.jei.api.runtime.IIngredientManager"));
					bookmark = create.invoke(null, typed, manager);
				}

				// 将书签添加到列表
				if (bookmark != null) {
					// 获取 bookmark 的实际类（IngredientBookmark）
					Class<?> bookmarkClass = bookmark.getClass();
					// 查找 add 方法
					// 【新旧版本兼容】遍历所有方法找到参数类型兼容的 add 方法
					// 新版本：add(IBookmark)  旧版本：add(IBookmark) 或 add(IngredientBookmark)
					Method addMethod = null;
					for (Method m : list.getClass().getMethods()) {
						if (m.getName().equals("add") && m.getParameterCount() == 1) {
							Class<?> paramType = m.getParameterTypes()[0];
							// 检查参数类型是否是 bookmark 的父类或接口
							if (paramType.isAssignableFrom(bookmarkClass)) {
								addMethod = m;
								break;
							}
						}
					}
					if (addMethod != null) {
						addMethod.invoke(list, bookmark);
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static Optional<Object> getRuntimeComponent(Object runtime, String methodName) {
		if (runtime == null) {
			return Optional.empty();
		}
		try {
			Object value = runtime.getClass().getMethod(methodName).invoke(runtime);
			return Optional.ofNullable(value);
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Object> invokeOptionalValue(Object owner, String methodName) {
		if (owner == null) {
			return Optional.empty();
		}
		try {
			Object value = owner.getClass().getMethod(methodName).invoke(owner);
			if (value instanceof Optional<?> optional && optional.isPresent()) {
				return Optional.ofNullable(optional.get());
			}
		} catch (Throwable ignored) {
		}
		return Optional.empty();
	}

	private static Optional<Object> getClickableIngredientUnderMouse(Object owner, double mouseX, double mouseY) {
		if (owner == null) {
			return Optional.empty();
		}
		try {
			Object streamObj = owner.getClass()
				.getMethod("getIngredientUnderMouse", double.class, double.class)
				.invoke(owner, mouseX, mouseY);
			if (streamObj instanceof java.util.stream.Stream<?> stream) {
				return Optional.ofNullable(stream.findFirst().orElse(null));
			}
		} catch (Throwable ignored) {
		}
		return Optional.empty();
	}

	private static Optional<Object> getTypedIngredientFromClickableSource(Object owner, double mouseX, double mouseY) {
		return getClickableIngredientUnderMouse(owner, mouseX, mouseY)
			.flatMap(JeiRuntimeProxy::getElementFromClickable)
			.flatMap(JeiRuntimeProxy::getTypedIngredientFromElement);
	}

	private static Optional<Object> getBookmarkUnderMouse(Object overlay, double mouseX, double mouseY) {
		Optional<Object> bookmark = getClickableIngredientUnderMouse(overlay, mouseX, mouseY)
			.flatMap(JeiRuntimeProxy::getElementFromClickable)
			.flatMap(JeiRuntimeProxy::getBookmarkFromElement);
		if (bookmark.isPresent()) {
			return bookmark;
		}

		Optional<Object> hoveredIngredient = getTypedIngredientFromClickableSource(overlay, mouseX, mouseY);
		if (hoveredIngredient.isEmpty()) {
			hoveredIngredient = invokeOptionalValue(overlay, "getIngredientUnderMouse");
		}
		if (hoveredIngredient.isEmpty()) {
			return Optional.empty();
		}

		return findBookmarkByTypedIngredient(overlay, hoveredIngredient.get());
	}

	private static Optional<Object> getElementFromClickable(Object clickable) {
		if (clickable == null) {
			return Optional.empty();
		}
		try {
			Object element = clickable.getClass().getMethod("getElement").invoke(clickable);
			return Optional.ofNullable(element);
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Object> getBookmarkFromElement(Object element) {
		if (element == null) {
			return Optional.empty();
		}
		try {
			Object bookmarkOpt = element.getClass().getMethod("getBookmark").invoke(element);
			if (bookmarkOpt instanceof Optional<?> optional && optional.isPresent()) {
				return Optional.ofNullable(optional.get());
			}
		} catch (Throwable ignored) {
		}
		return Optional.empty();
	}

	private static Optional<Object> getTypedIngredientFromElement(Object element) {
		if (element == null) {
			return Optional.empty();
		}
		try {
			Object typed = element.getClass().getMethod("getTypedIngredient").invoke(element);
			return Optional.ofNullable(typed);
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Object> getTypedIngredientFromBookmark(Object bookmark) {
		if (bookmark == null) {
			return Optional.empty();
		}
		try {
			Object typed = bookmark.getClass().getMethod("getDisplayIngredient").invoke(bookmark);
			if (typed != null) {
				return Optional.of(typed);
			}
		} catch (Throwable ignored) {
		}
		try {
			Object typed = bookmark.getClass().getMethod("getIngredient").invoke(bookmark);
			if (typed != null) {
				return Optional.of(typed);
			}
		} catch (Throwable ignored) {
		}
		try {
			Object element = bookmark.getClass().getMethod("getElement").invoke(bookmark);
			return getTypedIngredientFromElement(element);
		} catch (Throwable ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Object> findBookmarkByTypedIngredient(Object overlay, Object hoveredIngredient) {
		if (overlay == null || hoveredIngredient == null) {
			return Optional.empty();
		}
		Object firstMatch = null;
		for (Object bookmark : getBookmarksFromOverlay(overlay)) {
			Optional<Object> typedIngredient = getTypedIngredientFromBookmark(bookmark);
			if (typedIngredient.isPresent() && hoveredIngredient.equals(typedIngredient.get())) {
				if (isRecipeBookmark(bookmark)) {
					return Optional.of(bookmark);
				}
				if (firstMatch == null) {
					firstMatch = bookmark;
				}
			}
		}
		return Optional.ofNullable(firstMatch);
	}

	private static List<Object> getBookmarksFromOverlay(Object overlay) {
		if (overlay == null) {
			return List.of();
		}

		List<Object> bookmarks = new ArrayList<>();
		Object bookmarkList = readFieldValue(overlay, "bookmarkList");
		Object bookmarkValues = readFieldValue(bookmarkList, "bookmarksList");
		addBookmarks(bookmarks, bookmarkValues);

		Object lookupHistoryOverlay = readFieldValue(overlay, "lookupHistoryOverlay");
		Object lookupHistory = readFieldValue(lookupHistoryOverlay, "lookupHistory");
		Object historyValues = readFieldValue(lookupHistory, "elements");
		addBookmarks(bookmarks, historyValues);
		return bookmarks;
	}

	private static void addBookmarks(List<Object> target, Object source) {
		if (!(source instanceof List<?> list)) {
			return;
		}
		for (Object value : list) {
			if (value != null) {
				target.add(value);
			}
		}
	}

	private static Object readFieldValue(Object owner, String fieldName) {
		if (owner == null) {
			return null;
		}
		try {
			Field field = owner.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(owner);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean isRecipeBookmark(Object bookmark) {
		if (bookmark == null) {
			return false;
		}
		if ("RecipeBookmark".equals(bookmark.getClass().getSimpleName())) {
			return true;
		}
		try {
			bookmark.getClass().getMethod("getRecipeCategory");
			bookmark.getClass().getMethod("getRecipe");
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
