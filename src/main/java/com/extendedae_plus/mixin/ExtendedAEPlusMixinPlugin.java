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

	private static boolean isAdvancedAePresent() {
		return isClassPresent("net.pedroksl.advanced_ae.AdvancedAE");
	}

	private static boolean isUfoPresent() {
		return isClassPresent("com.raishxn.ufo.UfoMod");
	}

	private static boolean isBiggerAePresent() {
		return isClassPresent("cn.dancingsnow.bigger_ae2.BiggerAE2Mod");
	}

	@Override
	public void onLoad(String mixinPackage) { }

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!isJeiPresent()) {
			// Disable all JEI package mixins and any mixins that reference JEI-only helpers
			if (mixinClassName.startsWith("com.extendedae_plus.mixin.jei")) return false;
			if (mixinClassName.equals("com.extendedae_plus.mixin.ae2.menu.CraftConfirmMenuGoBackMixin")) return false;
		}
		if (!isAdvancedAePresent()) {
			if (mixinClassName.equals("com.extendedae_plus.mixin.advancedae.compat.PatternProviderLogicVirtualCompletionMixin")) {
				return false;
			}
		}
		if (mixinClassName.equals("com.extendedae_plus.mixin.ae2.CraftingCPUClusterMixin")) {
			if (isUfoPresent() || isBiggerAePresent()) {
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