package com.extendedae_plus.items;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;

/**
 * 吞噬核心物品。
 *
 * <p>实现 {@link Equipable} 并声明为头部槽位，使其与头盔一致，可通过右键直接装备到头部；
 * 装备完成后由 {@code BlackHoleHeadRenderer} 在头顶绘制黑洞。</p>
 */
public class InfinityCoreItem extends Item implements Equipable {

    public InfinityCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }
}