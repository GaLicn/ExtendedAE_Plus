package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.model.MatrixFrameModel;
import com.extendedae_plus.client.render.crafting.EPlusCraftingCubeModel;
import com.extendedae_plus.content.crafting.EPlusCraftingUnitType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;

@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class ClientModelEvents {
    private ClientModelEvents() {
    }

    @SubscribeEvent
    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(EPlusCraftingCubeModel.Unbaked.ID, EPlusCraftingCubeModel.Unbaked.MAP_CODEC);

        event.registerModel(Identifier.fromNamespaceAndPath(ExtendedAEPlus.MODID, "matrix_frame"),
                MatrixFrameModel.MAP_CODEC);
    }
}
