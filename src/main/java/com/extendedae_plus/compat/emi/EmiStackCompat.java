package com.extendedae_plus.compat.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * EmiStack → AE2 {@link GenericStack} 转换（EMI 路径）。
 *
 * <p>转换优先委托 AE2 的 {@link EmiStackHelper}：上游 AE2 的该助手内部遍历
 * {@code appeng.api.integrations.emi.EmiStackConverters} 扩展点，因此 AE2 自身注册的物品与流体、
 * 附属模组注册的自定义键类型（Applied Mekanistics 的 Mekanism 化学品等）都能覆盖。
 * 本模组不直接引用该扩展点 API，因为 GTO 使用的 AE2 分支（15.267.x）已将其整体移除；
 * 经助手调用在两种 AE2 上都能工作，且不会因可选 API 缺失而触发类链接错误。</p>
 *
 * <p>助手未覆盖或调用失败时回退到物品与流体的直接构造，保证行为不退化。</p>
 *
 * <p>量纲：EMI 与 AE2 在 Forge 平台上均以 mB 计量流体，两侧一致，不做换算。</p>
 */
public final class EmiStackCompat {
	private EmiStackCompat() {}

	/**
	 * EmiStack → GenericStack。
	 *
	 * @param stack EMI 悬浮栈
	 * @return 对应的 AE2 GenericStack；入参为空栈或无法识别时返回 null
	 */
	@Nullable
	public static GenericStack toGenericStack(@Nullable EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		try {
			GenericStack converted = EmiStackHelper.toGenericStack(stack);
			if (converted != null) {
				return converted;
			}
		} catch (Throwable ignored) {
			// 助手不可用（部分 AE2 分支未包含 EMI 集成）或转换器抛错时走本地回退。
		}

		return fallback(stack);
	}

	/** 物品与流体的直接构造，作为助手未覆盖时的回退路径。 */
	@Nullable
	private static GenericStack fallback(EmiStack stack) {
		try {
			ItemStack item = stack.getItemStack();
			if (item != null && !item.isEmpty()) {
				return new GenericStack(AEItemKey.of(item), Math.max(1, stack.getAmount()));
			}
			if (stack.getKey() instanceof Fluid fluid && fluid != Fluids.EMPTY) {
				return new GenericStack(AEFluidKey.of(fluid), Math.max(1, stack.getAmount()));
			}
		} catch (Throwable ignored) {
		}
		return null;
	}
}
