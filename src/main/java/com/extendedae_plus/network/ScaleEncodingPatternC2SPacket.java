package com.extendedae_plus.network;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
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
        MUL2, DIV2, MUL3, DIV3, MUL5, DIV5, SWAP_OUTPUTS, RESTORE_RATIO
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

            if (msg.op == Operation.SWAP_OUTPUTS) {
                rotateProcessingOutputs(menu);
                menu.broadcastChanges();
                return;
            }

            if (msg.op == Operation.RESTORE_RATIO) {
                var accessor = (PatternEncodingTermMenuAccessor) (Object) menu;
                long gcd = computeSharedGcd(accessor.eap$getEncodedInputsInv(), accessor.eap$getEncodedOutputsInv());
                if (gcd <= 1L) {
                    return;
                }

                var reducedOutputs = reduceInventory(accessor.eap$getEncodedOutputsInv(), gcd);
                var reducedInputs = reduceInventory(accessor.eap$getEncodedInputsInv(), gcd);
                applyScaled(accessor.eap$getEncodedOutputsInv(), reducedOutputs);
                applyScaled(accessor.eap$getEncodedInputsInv(), reducedInputs);
                menu.broadcastChanges();
                return;
            }

            int scale = switch (msg.op) {
                case MUL2, DIV2 -> 2;
                case MUL3, DIV3 -> 3;
                case MUL5, DIV5 -> 5;
                default -> 1;
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

    private static GenericStack[] reduceInventory(ConfigInventory inventory, long gcd) {
        var result = new GenericStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack stack = inventory.getStack(slot);
            if (stack == null) {
                continue;
            }
            result[slot] = new GenericStack(stack.what(), stack.amount() / gcd);
        }
        return result;
    }

    private static long computeSharedGcd(ConfigInventory inputs, ConfigInventory outputs) {
        long gcd = 0L;
        gcd = updateGcd(gcd, inputs);
        gcd = updateGcd(gcd, outputs);
        return gcd;
    }

    private static long updateGcd(long currentGcd, ConfigInventory inventory) {
        long gcd = currentGcd;
        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack stack = inventory.getStack(slot);
            if (stack == null || stack.amount() <= 0L) {
                continue;
            }
            gcd = gcd == 0L ? stack.amount() : gcd(gcd, stack.amount());
            if (gcd == 1L) {
                return 1L;
            }
        }
        return gcd;
    }

    private static long gcd(long a, long b) {
        long left = Math.abs(a);
        long right = Math.abs(b);
        while (right != 0L) {
            long temp = left % right;
            left = right;
            right = temp;
        }
        return left;
    }

    private static void rotateProcessingOutputs(PatternEncodingTermMenu menu) {
        FakeSlot[] outputSlots = menu.getProcessingOutputSlots();
        int nonEmptyCount = 0;
        for (FakeSlot slot : outputSlots) {
            if (!slot.getItem().isEmpty()) {
                nonEmptyCount++;
            }
        }
        if (nonEmptyCount < 2) {
            return;
        }

        var newOutputs = new net.minecraft.world.item.ItemStack[outputSlots.length];
        for (int i = 0; i < outputSlots.length; i++) {
            newOutputs[i] = outputSlots[i].getItem().copy();
        }

        for (int i = 0; i < outputSlots.length; i++) {
            if (outputSlots[i].getItem().isEmpty()) {
                continue;
            }

            for (int j = 1; j < outputSlots.length; j++) {
                var nextItem = outputSlots[(i + j) % outputSlots.length].getItem();
                if (!nextItem.isEmpty()) {
                    newOutputs[i] = nextItem.copy();
                    break;
                }
            }
        }

        for (int i = 0; i < newOutputs.length; i++) {
            outputSlots[i].set(newOutputs[i]);
        }
    }
}
