package com.extendedae_plus.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ExtendedAEPlusMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedAEPlus/MixinPlugin");

	private static final String JEI_BOOKMARK_OVERLAY_MIXIN =
		"com.extendedae_plus.mixin.jei.BookmarkOverlayMixin";

	/**
	 * JEI 内部（非 API）类。{@code jei.BookmarkOverlayMixin} 及其创建的书签栏按钮会把这些类
	 * 以字节码成员引用硬绑定：Mixin 在 attach 阶段由
	 * {@code MixinPreProcessorStandard#transformMemberReference} 解析引用，解析失败时抛出
	 * ClassNotFoundException 并包装为 MixinPreProcessorException，直接中止整个 Mixin transformer。
	 * <p>
	 * 该失败无法由 {@code @Pseudo} 或 {@code require = 0} 兜住 —— 二者都在 attach 之后才生效，
	 * 因此一旦 JEI 再次搬动或移除这些内部类，游戏会在进入主菜单之前崩溃，并连带拖垮同一批次加载的
	 * 其他模组（见上游 issue #148 / #158。JEI 19.56.0 已把 CombinedInputHandler、IUserInputHandler
	 * 等由 mezz.jei.gui.* 迁至 mezz.jei.common.*，上游提交 ae7ce103 "Move shared GUI types to Common"）。
	 * <p>
	 * 这里做一次存在性预检：只要缺任何一个，就跳过该 mixin，让按钮功能降级而不是让游戏崩溃。
	 */
	private static final String[] JEI_BOOKMARK_OVERLAY_DEPENDENCIES = {
		// mixin 方法体内的 INVOKESPECIAL 目标
		"mezz.jei.common.input.handlers.CombinedInputHandler",
		// 注入方法的参数 / 返回值类型
		"mezz.jei.common.input.IUserInputHandler",
		"mezz.jei.common.util.ImmutableRect2i",
		// 辅助类 JeiNetworkOverlayButton 在实例化时链接的类型
		"mezz.jei.common.input.IInternalKeyMappings",
		"mezz.jei.common.input.UserInput",
		// @Shadow 字段类型，以及 @At 注入目标所引用的类型
		"mezz.jei.gui.elements.IconButton",
	};

	private static boolean isClassPresent(String className) {
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Class.forName(className, false, cl);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isJeiBookmarkOverlayDependencyPresent() {
		for (String className : JEI_BOOKMARK_OVERLAY_DEPENDENCIES) {
			if (!isClassPresent(className)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isJeiPresent() {
		return isClassPresent("mezz.jei.api.IModPlugin");
	}

	private static boolean isEmiPresent() {
		return isClassPresent("dev.emi.emi.api.EmiApi");
	}

	private static boolean isAdvancedAePresent() {
		return isClassPresent("net.pedroksl.advanced_ae.AdvancedAE");
	}

	private static boolean isUfoPresent() {
		return isClassPresent("com.raishxn.ufo.UfoMod");
	}

	private static boolean isBiggerAePresent() {
		return isClassPresent("cn.dancingsnow.bigger_ae2.BiggerAE2Mod");
	}

	private static boolean isExpandedAePresent() {
		return isClassPresent("lu.kolja.expandedae.ExpandedAE");
	}

	private static boolean isAppfluxPresent() {
		return isClassPresent("com.glodblock.github.appflux.AppFlux");
	}

	private static boolean isNeoECOAEPresent() {
		return isClassPresent("cn.dancingsnow.neoecoae.NeoECOAE");
	}

	private static boolean isAe2WtLibPresent() {
		return isClassPresent("de.mari_023.ae2wtlib.api.registration.WTDefinition");
	}

	@Override
	public void onLoad(String mixinPackage) { }

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!isAe2WtLibPresent() && mixinClassName.startsWith("com.extendedae_plus.mixin.ae2WTlib.")) {
			return false;
		}
		if (!isAppfluxPresent() && mixinClassName.startsWith("com.extendedae_plus.mixin.appflux.")) {
			return false;
		}
		if (!isJeiPresent()) {
			// Disable all JEI package mixins and any mixins that reference JEI-only helpers
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.jei")) return false;
			if (mixinClassName.equals("com.extendedae_plus.mixin.ae2.menu.CraftConfirmMenuGoBackMixin")) return false;
		}

		// JEI 内部类可能在任意次版本中搬家或更名：缺失时必须跳过，而不是让整个 Mixin transformer 崩掉。
		if (mixinClassName.equals(JEI_BOOKMARK_OVERLAY_MIXIN) && !isJeiBookmarkOverlayDependencyPresent()) {
			LOGGER.warn("Skipping {}: the JEI internal classes it binds to are missing, so JEI changed its layout."
				+ " The AE2 network stock overlay button will be unavailable instead of crashing the game.",
				JEI_BOOKMARK_OVERLAY_MIXIN);
			return false;
		}
		if (!isEmiPresent() && mixinClassName.startsWith("com.extendedae_plus.mixin.emi.")) {
			return false;
		}
		if (!isAdvancedAePresent()) {
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.advancedae.")) {
				return false;
			}
		}
		if (!isNeoECOAEPresent()) {
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.neoecoae.")) {
				return false;
			}
		}
		if (mixinClassName.equals("com.extendedae_plus.mixin.ae2.CraftingCPUClusterMixin")) {
			if (isUfoPresent() || isBiggerAePresent()) {
				return false;
			}
		}
		if (isExpandedAePresent()) {
			if (mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.PatternProviderLogicContainsRedirectMixin") ||
				mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.AdvPatternProviderLogicContainsRedirectMixin")) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {return null;}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
