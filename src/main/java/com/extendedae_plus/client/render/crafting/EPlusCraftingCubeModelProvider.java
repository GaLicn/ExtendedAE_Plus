package com.extendedae_plus.client.render.crafting;

import appeng.client.render.crafting.AbstractCraftingUnitModelProvider;
import appeng.client.render.crafting.LightBakedModel;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;

public final class EPlusCraftingCubeModelProvider extends AbstractCraftingUnitModelProvider<EPlusCraftingUnitType> {
    private static final Material RING_CORNER = texture("ring_corner");
    private static final Material RING_SIDE_HOR = texture("ring_side_hor");
    private static final Material RING_SIDE_VER = texture("ring_side_ver");
    private static final Material LIGHT_BASE = texture("light_base");
    private static final Material LIGHT_4X = texture("4x_accelerator_light");
    private static final Material LIGHT_16X = texture("16x_accelerator_light");
    private static final Material LIGHT_64X = texture("64x_accelerator_light");
    private static final Material LIGHT_256X = texture("256x_accelerator_light");
    private static final Material LIGHT_1024X = texture("1024x_accelerator_light");

    public EPlusCraftingCubeModelProvider(EPlusCraftingUnitType type) {
        super(type);
    }

    @Override
    public BlockStateModel bake(MaterialBaker baker) {
        ModelDebugName name = getClass()::getName;
        return new LightBakedModel(
                baker.get(RING_CORNER, name),
                baker.get(RING_SIDE_HOR, name),
                baker.get(RING_SIDE_VER, name),
                baker.get(LIGHT_BASE, name),
                baker.get(lightMaterial(), name));
    }

    private Material lightMaterial() {
        return switch (type) {
            case ACCELERATOR_4x -> LIGHT_4X;
            case ACCELERATOR_16x -> LIGHT_16X;
            case ACCELERATOR_64x -> LIGHT_64X;
            case ACCELERATOR_256x -> LIGHT_256X;
            case ACCELERATOR_1024x -> LIGHT_1024X;
        };
    }

    private static Material texture(String name) {
        return new Material(ExtendedAEPlus.id("block/crafting/" + name));
    }
}
