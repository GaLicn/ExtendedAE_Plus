package com.extendedae_plus.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExtendedAEPlusMixinPlugin implements IMixinConfigPlugin {
	private static final String MIXIN_PACKAGE = "com.extendedae_plus.mixin.";

	private static final String CRAFT_CONFIRM_MENU_GO_BACK_MIXIN =
			MIXIN_PACKAGE + "ae2.menu.CraftConfirmMenuGoBackMixin";
	private static final String CRAFTING_CPU_CLUSTER_MIXIN =
			MIXIN_PACKAGE + "ae2.CraftingCPUClusterMixin";
	private static final String PATTERN_PROVIDER_LOGIC_CONTAINS_REDIRECT_MIXIN =
			MIXIN_PACKAGE + "ae2.autopattern.PatternProviderLogicContainsRedirectMixin";
	private static final String ADV_PATTERN_PROVIDER_LOGIC_CONTAINS_REDIRECT_MIXIN =
			MIXIN_PACKAGE + "ae2.autopattern.AdvPatternProviderLogicContainsRedirectMixin";

	/**
	 * 类探测结果缓存。shouldApplyMixin 对每个 mixin 条目都会调用，NeoForge 上还可能并行；
	 * Class.forName 失败要构造异常并填充栈，代价不低，同一类名只应探测一次。
	 */
	private static final Map<String, Boolean> CLASS_PRESENCE = new ConcurrentHashMap<>();

	private static boolean isClassPresent(String className) {
		return CLASS_PRESENCE.computeIfAbsent(className, name -> {
			try {
				ClassLoader cl = Thread.currentThread().getContextClassLoader();
				Class.forName(name, false, cl);
				return true;
			} catch (ClassNotFoundException | LinkageError ignored) {
				// 类不存在，或类存在但依赖缺失（NoClassDefFoundError）；initialize=false 不会执行静态初始化。
				return false;
			}
		});
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
		// 先按包前缀短路，命中才做类探测：本配置有上百条 mixin，
		// 把探测放在前缀判断之前会让每条 entry 都跑一遍无关的类查找。
		if (mixinClassName.startsWith(MIXIN_PACKAGE + "ae2WTlib.") && !isAe2WtLibPresent()) {
			return false;
		}
		if (mixinClassName.startsWith(MIXIN_PACKAGE + "appflux.") && !isAppfluxPresent()) {
			return false;
		}
		if ((mixinClassName.startsWith(MIXIN_PACKAGE + "jei") || mixinClassName.equals(CRAFT_CONFIRM_MENU_GO_BACK_MIXIN))
				&& !isJeiPresent()) {
			return false;
		}
		if (mixinClassName.startsWith(MIXIN_PACKAGE + "emi.") && !isEmiPresent()) {
			return false;
		}
		if (mixinClassName.startsWith(MIXIN_PACKAGE + "advancedae.") && !isAdvancedAePresent()) {
			return false;
		}
		if (mixinClassName.startsWith(MIXIN_PACKAGE + "neoecoae.")) {
			// 新版 ECO 在自己的投料事务中处理虚拟卡记账，旧 api.me 实现类和 getLogic 已迁移。
			// 检测到新版兼容入口时停用旧访问器和完成回调；旧版 ECO 继续使用原兼容逻辑。
			if (!isNeoECOAEPresent() || isClassPresent(
					"cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusVirtualCrafting")) {
				return false;
			}
		}
		if (mixinClassName.equals(CRAFTING_CPU_CLUSTER_MIXIN) && (isUfoPresent() || isBiggerAePresent())) {
			return false;
		}
		if ((mixinClassName.equals(PATTERN_PROVIDER_LOGIC_CONTAINS_REDIRECT_MIXIN)
				|| mixinClassName.equals(ADV_PATTERN_PROVIDER_LOGIC_CONTAINS_REDIRECT_MIXIN))
				&& isExpandedAePresent()) {
			return false;
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
