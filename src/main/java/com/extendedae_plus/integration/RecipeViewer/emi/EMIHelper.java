package com.extendedae_plus.integration.RecipeViewer.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.integration.RecipeViewer.IRecipeViewerHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;

public class EMIHelper implements IRecipeViewerHelper {
    private final Object mekHelper = getMekHelper();
    private final boolean mekChecked =
            ModList.get().isLoaded("mekanism") ||
                    ModList.get().isLoaded("appmek");

    @Override
    public List<GenericStack> getHoveredStacks(double mouseX, double mouseY) {
        return EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false).getStack().getEmiStacks()
                .stream().map(EmiStackHelper::toGenericStack).toList();
    }

    @Override
    public List<GenericStack> getHoveredStacks() {
        return EmiApi.getHoveredStack(false).getStack().getEmiStacks()
                .stream().map(EmiStackHelper::toGenericStack).toList();
    }

    @Override
    public List<GenericStack> getFavorites() {
        return EmiFavorites.favorites.stream()
                .map(EmiFavorite::getEmiStacks)
                .map(List::getFirst)
                .map(EmiStackHelper::toGenericStack)
                .toList();
    }

    @Override
    public void addFavorite(GenericStack stack) {
        AEKey key = stack.what();
        if (key instanceof AEItemKey itemKey)
            EmiFavorites.addFavorite(EmiStack.of(itemKey.toStack((int) stack.amount())));
        else if (key instanceof AEFluidKey fluidKey)
            EmiFavorites.addFavorite(EmiStack.of(fluidKey.getFluid(), stack.amount()));
        else if (mekChecked && mekHelper != null) {
            try {
                Class<?> keyClass = key.getClass();
                if (keyClass.getName().contains("MekanismKey")) {
                    Method getChemicalStackMethod = keyClass.getMethod("withAmount", long.class);
                    Object chemicalStack = getChemicalStackMethod.invoke(key, stack.amount());

                    Method createEmiStack = mekHelper.getClass().getMethod("createEmiStack",
                            Class.forName("mekanism.api.chemical.ChemicalStack"));
                    EmiStack mekEmiStack = (EmiStack) createEmiStack.invoke(mekHelper, chemicalStack);
                    if (mekEmiStack == null) return;
                    EmiFavorites.addFavorite(mekEmiStack);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void setSearch(String text) {
        EmiApi.setSearchText(text);
    }

    private Object getMekHelper() {
        try {
            Class<?> mekanismAPIClass = Class.forName("mekanism.api.MekanismAPI");
            Method getServiceMethod = mekanismAPIClass.getMethod("getService", Class.class);

            Class<?> mekanismAccessClass = Class.forName("mekanism.api.IMekanismAccess");
            Object mekanismAccess = getServiceMethod.invoke(null, mekanismAccessClass);

            Method emiHelperMethod = mekanismAccessClass.getMethod("emiHelper");
            return emiHelperMethod.invoke(mekanismAccess);
        } catch (Exception e) {
            ExtendedAEPlus.LOGGER.error("Failed to get Mekanism EMI Helper: {}", String.valueOf(e));
            return null;
        }
    }
}
