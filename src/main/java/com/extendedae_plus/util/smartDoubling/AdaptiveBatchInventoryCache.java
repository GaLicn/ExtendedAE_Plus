package com.extendedae_plus.util.smartDoubling;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ICraftingInventory;
import com.google.common.collect.Iterables;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 在一次批量计算内复用输入候选和库存查询结果。 */
public final class AdaptiveBatchInventoryCache {
    private final Level level;
    private final AdaptiveBatchQueryCache<AEKey> cache;

    public AdaptiveBatchInventoryCache(ICraftingInventory inventory, Level level) {
        this.level = level;
        this.cache = new AdaptiveBatchQueryCache<>(
                inventory::findFuzzyTemplates,
                key -> inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    public Iterable<InputTemplate> getValidTemplates(IPatternDetails.IInput input) {
        List<InputTemplate> candidates = new ArrayList<>();
        GenericStack[] possibleInputs = input.getPossibleInputs();
        if (possibleInputs == null) {
            return candidates;
        }

        for (var possibleInput : possibleInputs) {
            if (possibleInput == null) {
                continue;
            }
            List<AEKey> matchingKeys = this.cache.getFuzzyTemplates(possibleInput.what());
            for (AEKey key : matchingKeys) {
                candidates.add(new InputTemplate(key, possibleInput.amount()));
            }
        }

        return Iterables.filter(candidates, candidate -> input.isValid(candidate.key(), this.level));
    }

    public long getAvailableAmount(AEKey key) {
        return this.cache.getAvailableAmount(key);
    }

    public record InputTemplate(AEKey key, long amount) {}
}
