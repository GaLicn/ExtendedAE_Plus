package com.extendedae_plus.client.model;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jetbrains.annotations.NotNull;

public final class MatrixFrameModel implements CustomUnbakedBlockStateModel {
    public static final MapCodec<MatrixFrameModel> MAP_CODEC = MapCodec.unit(MatrixFrameModel::new);
    private static final Variant BLOCK_OFF = variant("super_assembler_matrix_frame_block_off");
    private static final Variant BLOCK_ON = variant("super_assembler_matrix_frame_block_on");
    private static final Variant COLUMN_OFF = variant("super_assembler_matrix_frame_column_off");
    private static final Variant COLUMN_ON = variant("super_assembler_matrix_frame_column_on");

    private static Variant variant(String name) {
        return new Variant(Identifier.fromNamespaceAndPath("extendedae_plus", "block/" + name));
    }

    @Override
    public @NotNull BlockStateModel bake(@NotNull ModelBaker baker) {
        return new MatrixFrameBakedModel(
                new SingleVariant(BLOCK_OFF.bake(baker)), new SingleVariant(BLOCK_ON.bake(baker)),
                new SingleVariant(COLUMN_OFF.bake(baker)), new SingleVariant(COLUMN_ON.bake(baker)));
    }

    @Override
    public void resolveDependencies(@NotNull Resolver resolver) {
        BLOCK_OFF.resolveDependencies(resolver);
        BLOCK_ON.resolveDependencies(resolver);
        COLUMN_OFF.resolveDependencies(resolver);
        COLUMN_ON.resolveDependencies(resolver);
    }

    @Override
    public @NotNull MapCodec<MatrixFrameModel> codec() {
        return MAP_CODEC;
    }

}
