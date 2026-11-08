package com.extendedae_plus.compat.emi;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * EmiStack → AE2 {@link GenericStack} 转换（EMI 路径）。
 *
 * <p>转换优先委托 AE2 的 {@link EmiStackConverters} 扩展点：AE2 自身注册物品与流体转换器，
 * 附属模组注册各自的自定义键类型转换器（Applied Mekanistics 即以此注册 Mekanism 化学品）。
 * 经该扩展点可获得与查看器渲染一致的键覆盖范围，而本模组无需引用这些模组的类型，
 * 因此也不存在可选模组缺失时的类链接风险。</p>
 *
 * <p>扩展点未覆盖时回退到物品与流体的直接构造，保证扩展点注册缺失或异常时行为不退化。</p>
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

		for (EmiStackConverter converter : EmiStackConverters.getConverters()) {
			try {
				GenericStack converted = converter.toGenericStack(stack);
				if (converted != null) {
					return converted;
				}
			} catch (Throwable ignored) {
				// 单个转换器抛错不阻断其它转换器：化学品转换器在对应模组缺失时可能不可用。
			}
		}

		return fallback(stack);
	}

	/** 物品与流体的直接构造，作为扩展点未覆盖时的回退路径。 */
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
