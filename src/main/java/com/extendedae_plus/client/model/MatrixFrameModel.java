package com.extendedae_plus.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class MatrixFrameModel implements IUnbakedGeometry<MatrixFrameModel> {

    @Override
    public @NotNull BakedModel bake(@NotNull IGeometryBakingContext context, @NotNull ModelBaker baker,
            @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState,
            @NotNull ItemOverrides overrides) {
        return new MatrixFrameBakedModel(spriteGetter);
    }

    public static final class Loader implements IGeometryLoader<MatrixFrameModel> {

        @Override
        public @NotNull MatrixFrameModel read(@NotNull JsonObject json,
                @NotNull JsonDeserializationContext context) throws JsonParseException {
            return new MatrixFrameModel();
        }
    }
}
