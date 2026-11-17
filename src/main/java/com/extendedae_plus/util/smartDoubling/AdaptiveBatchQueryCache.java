package com.extendedae_plus.util.smartDoubling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** 在单次批量计算内缓存不改变库存的查询结果。 */
public final class AdaptiveBatchQueryCache<K> {
    private final Function<K, ? extends Iterable<K>> fuzzyLookup;
    private final ToLongFunction<K> amountLookup;
    private final Map<K, List<K>> fuzzyTemplates = new HashMap<>();
    private final Map<K, Long> availableAmounts = new HashMap<>();

    public AdaptiveBatchQueryCache(
            Function<K, ? extends Iterable<K>> fuzzyLookup,
            ToLongFunction<K> amountLookup) {
        this.fuzzyLookup = fuzzyLookup;
        this.amountLookup = amountLookup;
    }

    public List<K> getFuzzyTemplates(K key) {
        return this.fuzzyTemplates.computeIfAbsent(key, query -> {
            List<K> result = new ArrayList<>();
            this.fuzzyLookup.apply(query).forEach(result::add);
            return List.copyOf(result);
        });
    }

    public long getAvailableAmount(K key) {
        return this.availableAmounts.computeIfAbsent(key, this.amountLookup::applyAsLong);
    }
}
