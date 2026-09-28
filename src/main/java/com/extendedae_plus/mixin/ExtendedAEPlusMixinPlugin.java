package com.extendedae_plus.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ExtendedAEPlusMixinPlugin implements IMixinConfigPlugin {
	private static boolean isClassPresent(String className) {
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Class.forName(className, false, cl);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isJeiPresent() {
		return isClassPresent("mezz.jei.api.IModPlugin");
	}

	// EMI 26.1.2 发布后恢复 isEmiPresent() 与 shouldApplyMixin 中的 EMI 条件。

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

	/* NeoECOAE 发布适配版本后恢复。
	private static boolean isNeoECOAEPresent() {
		return isClassPresent("cn.dancingsnow.neoecoae.NeoECOAE");
	}
	*/

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
		if (!isAdvancedAePresent()) {
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.advancedae.")) {
				return false;
			}
		}
		/* NeoECOAE 发布适配版本后恢复。
		if (!isNeoECOAEPresent()) {
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.neoecoae.")) {
				return false;
			}
		}
		*/
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
