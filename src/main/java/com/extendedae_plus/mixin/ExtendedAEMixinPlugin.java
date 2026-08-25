package com.extendedae_plus.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：按可选依赖的存在与否裁剪 mixin。
 * 当前仅用于 EMI：未安装 EMI 时禁用 mixin.emi 包（其目标类 dev.emi.* 不存在）。
 */
public class ExtendedAEMixinPlugin implements IMixinConfigPlugin {

	private static boolean isClassPresent(String className) {
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Class.forName(className, false, cl);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean isEmiPresent() {
		return isClassPresent("dev.emi.emi.api.EmiApi");
	}

	@Override
	public void onLoad(String mixinPackage) { }

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!isEmiPresent() && mixinClassName.startsWith("com.extendedae_plus.mixin.emi.")) {
			return false;
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

	@Override
	public List<String> getMixins() { return null; }

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
