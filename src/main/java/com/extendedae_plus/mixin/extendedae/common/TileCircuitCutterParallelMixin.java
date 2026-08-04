package com.extendedae_plus.mixin.extendedae.common;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.extendedae_plus.init.ModBlocks;
import com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.extendedae.util.FCUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TileCircuitCutter.class, remap = false)
public abstract class TileCircuitCutterParallelMixin {
    private static final int MAX_PARALLEL_RECIPES = 8;
    private static final int SUPER_POWER_CAPACITY = 64_000;
    private static final int MAX_PROGRESS = 200;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$increaseSuperCutterCapacity(CallbackInfo ci) {
        var host = (TileCircuitCutter) (Object) this;
        if (isSuperCutter(host)) {
            host.setInternalMaxPower(SUPER_POWER_CAPACITY);
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void eap$runParallelRecipes(IGridNode node, int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        var host = (TileCircuitCutter) (Object) this;
        if (!isSuperCutter(host)) {
            return;
        }

        if (tryAutoExport(host)) {
            cir.setReturnValue(TickRateModulation.URGENT);
            return;
        }

        var context = host.getContext();
        if (context.currentRecipe == null && context.shouldTick()) {
            context.findRecipe();
        }
        var recipe = context.currentRecipe;
        if (recipe == null) {
            host.setWorking(false);
            cir.setReturnValue(TickRateModulation.FASTER);
            return;
        }

        int operations = getParallelOperations(host, recipe);
        if (operations == 0) {
            context.currentRecipe = null;
            host.setWorking(false);
            cir.setReturnValue(TickRateModulation.FASTER);
            return;
        }

        host.setWorking(true);
        int speed = FCUtil.speedCardMap(host.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
        if (consumePower(host, 10D * speed * operations)) {
            host.addProgress(speed);
        }

        if (host.getProgress() >= MAX_PROGRESS) {
            host.setProgress(0);
            operations = getParallelOperations(host, recipe);
            if (operations > 0) {
                // 每份配方独立扣除输入与流体，避免并行时少扣或重复产出。
                consumeInputs(host, recipe, operations);
                host.getOutput().insertItem(0, createOutput(recipe, operations), false);
            }
            context.currentRecipe = null;
        }

        host.markForUpdate();
        cir.setReturnValue(TickRateModulation.URGENT);
    }

    private static boolean isSuperCutter(TileCircuitCutter host) {
        return host.getBlockState().is(ModBlocks.CIRCUIT_CUTTER_PLUS.get());
    }

    private static int getParallelOperations(TileCircuitCutter host, CircuitCutterRecipe recipe) {
        for (int operations = MAX_PARALLEL_RECIPES; operations > 0; operations--) {
            if (host.getOutput().insertItem(0, createOutput(recipe, operations), true).isEmpty()
                    && hasInputs(host, recipe, operations)) {
                return operations;
            }
        }
        return 0;
    }

    private static boolean hasInputs(TileCircuitCutter host, CircuitCutterRecipe recipe, int operations) {
        var available = copyInputs(host);
        for (int operation = 0; operation < operations; operation++) {
            for (var requirement : recipe.getSample()) {
                for (var stack : available) {
                    if (requirement.checkType(stack)) {
                        requirement.consume(stack);
                    }
                    if (requirement.isEmpty()) {
                        break;
                    }
                }
                if (!requirement.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Object> copyInputs(TileCircuitCutter host) {
        var copied = new ArrayList<Object>();
        for (var item : host.getInput()) {
            if (!item.isEmpty()) {
                copied.add(item.copy());
            }
        }
        var storedFluid = host.getTank().getStack(0);
        if (storedFluid != null && storedFluid.what() instanceof AEFluidKey fluidKey) {
            copied.add(fluidKey.toStack((int) storedFluid.amount()));
        }
        return copied;
    }

    private static void consumeInputs(TileCircuitCutter host, CircuitCutterRecipe recipe, int operations) {
        FluidStack fluidStack = null;
        var storedFluid = host.getTank().getStack(0);
        if (storedFluid != null && storedFluid.what() instanceof AEFluidKey fluidKey) {
            fluidStack = fluidKey.toStack((int) storedFluid.amount());
        }

        for (int operation = 0; operation < operations; operation++) {
            for (var requirement : recipe.getSample()) {
                for (int slot = 0; slot < host.getInput().size(); slot++) {
                    var item = host.getInput().getStackInSlot(slot);
                    if (requirement.checkType(item)) {
                        requirement.consume(item);
                        host.getInput().setItemDirect(slot, item);
                    }
                    if (requirement.isEmpty()) {
                        break;
                    }
                }
                if (fluidStack != null && !requirement.isEmpty() && requirement.checkType(fluidStack)) {
                    requirement.consume(fluidStack);
                }
            }
        }

        if (fluidStack != null) {
            host.getTank().setStack(0, fluidStack.isEmpty() ? null
                    : new GenericStack(AEFluidKey.of(fluidStack), fluidStack.getAmount()));
        }
    }

    private static ItemStack createOutput(CircuitCutterRecipe recipe, int operations) {
        var output = recipe.output.copy();
        output.setCount(output.getCount() * operations);
        return output;
    }

    private static boolean consumePower(TileCircuitCutter host, double amount) {
        double threshold = amount - 0.01;
        IEnergySource source = host;
        double extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (extracted <= threshold && host.getMainNode().getGrid() != null) {
            source = host.getMainNode().getGrid().getEnergyService();
            extracted = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        }
        if (extracted > threshold) {
            source.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
            return true;
        }
        return false;
    }

    private static boolean tryAutoExport(TileCircuitCutter host) {
        return !host.getOutput().getStackInSlot(0).isEmpty()
                && host.getConfigManager().getSetting(Settings.AUTO_EXPORT) == YesNo.YES
                && FCUtil.ejectInv(host.getLevel(), host.getBlockPos(), host.getOutput(),
                        blockEntity -> blockEntity instanceof TileCircuitCutter);
    }
}
