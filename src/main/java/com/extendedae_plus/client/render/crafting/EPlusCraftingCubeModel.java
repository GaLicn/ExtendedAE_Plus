package com.extendedae_plus.client.render.crafting;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

public final class EPlusCraftingCubeModel {
    private EPlusCraftingCubeModel() {
    }

    public record Unbaked(EPlusCraftingUnitType type) implements CustomUnbakedBlockStateModel {
        public static final Identifier ID = ExtendedAEPlus.id("crafting_cube");
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                EPlusCraftingUnitType.CODEC.fieldOf("unit_type").forGetter(Unbaked::type))
                .apply(instance, Unbaked::new));

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new EPlusCraftingCubeModelProvider(this.type).bake(baker.materials());
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
        }

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }
    }
}
