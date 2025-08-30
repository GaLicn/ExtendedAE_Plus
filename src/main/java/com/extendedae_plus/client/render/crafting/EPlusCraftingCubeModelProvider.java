package com.extendedae_plus.client.render.crafting;

import appeng.client.render.crafting.AbstractCraftingUnitModelProvider;
import appeng.client.render.crafting.LightBakedModel;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 形成态光照模型。
 */
public class EPlusCraftingCubeModelProvider
        extends AbstractCraftingUnitModelProvider<EPlusCraftingUnitType> {

    public static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutout());
    private static final List<Material> MATERIALS = new ArrayList<>();

    //将环形边框与基础发光底图放在本模组命名空间
    protected static final Material RING_CORNER = texture(ExtendedAEPlus.MODID, "ring_corner");
    protected static final Material RING_SIDE_HOR = texture(ExtendedAEPlus.MODID, "ring_side_hor");
    protected static final Material RING_SIDE_VER = texture(ExtendedAEPlus.MODID, "ring_side_ver");
    protected static final Material LIGHT_BASE = texture(ExtendedAEPlus.MODID, "light_base");

    // 亮面贴图（formed 时使用）
    protected static final Material ACCELERATOR_4X_LIGHT = texture(ExtendedAEPlus.MODID,
            "4x_accelerator_light");
    protected static final Material ACCELERATOR_16X_LIGHT = texture(ExtendedAEPlus.MODID,
            "16x_accelerator_light");
    protected static final Material ACCELERATOR_64X_LIGHT = texture(ExtendedAEPlus.MODID,
            "64x_accelerator_light");
    protected static final Material ACCELERATOR_256X_LIGHT = texture(ExtendedAEPlus.MODID,
            "256x_accelerator_light");
    protected static final Material ACCELERATOR_1024X_LIGHT = texture(ExtendedAEPlus.MODID,
            "1024x_accelerator_light");

    public EPlusCraftingCubeModelProvider(EPlusCraftingUnitType type) {
        super(type);
    }

    @Override
    public List<Material> getMaterials() {
        return Collections.unmodifiableList(MATERIALS);
    }

    @Override
    public BakedModel getBakedModel(Function<Material, TextureAtlasSprite> spriteGetter) {
        TextureAtlasSprite ringCorner = spriteGetter.apply(RING_CORNER);
        TextureAtlasSprite ringSideHor = spriteGetter.apply(RING_SIDE_HOR);
        TextureAtlasSprite ringSideVer = spriteGetter.apply(RING_SIDE_VER);

        return new LightBakedModel(ringCorner, ringSideHor, ringSideVer,
                spriteGetter.apply(LIGHT_BASE), this.getLightMaterial(spriteGetter)) {
            public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
                return CUTOUT;
            }
        };
    }

    private TextureAtlasSprite getLightMaterial(Function<Material, TextureAtlasSprite> textureGetter) {
        return switch (this.type) {
            case ACCELERATOR_4x -> textureGetter.apply(ACCELERATOR_4X_LIGHT);
            case ACCELERATOR_16x -> textureGetter.apply(ACCELERATOR_16X_LIGHT);
            case ACCELERATOR_64x -> textureGetter.apply(ACCELERATOR_64X_LIGHT);
            case ACCELERATOR_256x -> textureGetter.apply(ACCELERATOR_256X_LIGHT);
            case ACCELERATOR_1024x -> textureGetter.apply(ACCELERATOR_1024X_LIGHT);
        };
    }

    private static Material texture(String namespace, String name) {
        var mat = new Material(TextureAtlas.LOCATION_BLOCKS,
                new ResourceLocation(namespace, "block/crafting/" + name));
        MATERIALS.add(mat);
        return mat;
    }
}
