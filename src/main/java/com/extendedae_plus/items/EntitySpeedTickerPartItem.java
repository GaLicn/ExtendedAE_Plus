package com.extendedae_plus.items;

import appeng.items.parts.PartItem;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;


public class EntitySpeedTickerPartItem extends PartItem<EntitySpeedTickerPart> {
    public EntitySpeedTickerPartItem(Properties properties) {
        super(properties, EntitySpeedTickerPart.class, EntitySpeedTickerPart::new);
    }
}