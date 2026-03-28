package com.extendedae_plus.network;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ScaleEncodingPatternC2SPacket implements CustomPacketPayload {
    public enum Operation {
        MUL2, DIV2, MUL3, DIV3, MUL5, DIV5
    }

    public static final Type<ScaleEncodingPatternC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "scale_encoding_pattern"));

    public static final StreamCodec<FriendlyByteBuf, ScaleEncodingPatternC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeEnum(pkt.op),
            buf -> new ScaleEncodingPatternC2SPacket(buf.readEnum(Operation.class))
    );

    private final Operation op;

    public ScaleEncodingPatternC2SPacket(Operation op) {
        this.op = op;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ScaleEncodingPatternC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
                return;
            }
            if (menu.getMode() != EncodingMode.PROCESSING) {
                return;
            }

            int scale = switch (msg.op) {
                case MUL2, DIV2 -> 2;
                case MUL3, DIV3 -> 3;
                case MUL5, DIV5 -> 5;
            };
            boolean divide = switch (msg.op) {
                case DIV2, DIV3, DIV5 -> true;
                default -> false;
            };

            var accessor = (PatternEncodingTermMenuAccessor) (Object) menu;
            var scaledOutputs = scaleInventory(accessor.eap$getEncodedOutputsInv(), scale, divide);
            if (scaledOutputs == null) {
                return;
            }

            var scaledInputs = scaleInventory(accessor.eap$getEncodedInputsInv(), scale, divide);
            if (scaledInputs == null) {
                return;
            }

            applyScaled(accessor.eap$getEncodedOutputsInv(), scaledOutputs);
            applyScaled(accessor.eap$getEncodedInputsInv(), scaledInputs);
            menu.broadcastChanges();
        });
    }

    private static GenericStack[] scaleInventory(ConfigInventory inventory, int scale, boolean divide) {
        var result = new GenericStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack stack = inventory.getStack(slot);
            if (stack == null) {
                continue;
            }

            long nextAmount;
            if (divide) {
                if (stack.amount() % scale != 0) {
                    return null;
                }
                nextAmount = stack.amount() / scale;
            } else {
                try {
                    nextAmount = Math.multiplyExact(stack.amount(), scale);
                } catch (ArithmeticException ex) {
                    return null;
                }
            }

            result[slot] = new GenericStack(stack.what(), nextAmount);
        }
        return result;
    }

    private static void applyScaled(ConfigInventory inventory, GenericStack[] scaledStacks) {
        for (int slot = 0; slot < scaledStacks.length; slot++) {
            if (scaledStacks[slot] != null) {
                inventory.setStack(slot, scaledStacks[slot]);
            }
        }
    }
}
