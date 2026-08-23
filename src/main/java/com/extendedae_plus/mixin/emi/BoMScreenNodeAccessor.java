package com.extendedae_plus.mixin.emi;

import dev.emi.emi.bom.MaterialNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code BoMScreen$Node} 是 private 内部类，无法在普通代码里引用；
 * 用 accessor 取出节点几何，供合成树映射标记定位物品格子。
 */
@Mixin(targets = "dev.emi.emi.screen.BoMScreen$Node", remap = false)
public interface BoMScreenNodeAccessor {

	@Accessor("x")
	int eap$getX();

	@Accessor("y")
	int eap$getY();

	@Accessor("midOffset")
	int eap$getMidOffset();

	@Accessor("node")
	MaterialNode eap$getNode();
}
