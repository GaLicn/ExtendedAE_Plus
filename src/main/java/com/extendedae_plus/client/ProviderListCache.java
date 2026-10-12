package com.extendedae_plus.client;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端样板供应器列表缓存，用于点击「编码」时本地判定唯一匹配。
 *
 * <p>服务端下发的供应器名称是 Component JSON，需在客户端解析取文本；判定放在客户端，名称与搜索词同处一个语言环境。</p>
 *
 * <p>列表由编码终端打开时与每次编码时发起的静默请求刷新。缓存只用于判定，服务端按名称在当前网络中
 * 重新定位供应器，因此缓存略旧也不会传错目标。</p>
 */
public final class ProviderListCache {

    /**
     * @param rawName    服务端下发的原始名称（Component JSON），回传给服务端用于定位
     * @param emptySlots 空槽位数
     */
    private record Provider(String rawName, int emptySlots) {
    }

    private static List<Provider> providers = List.of();

    private ProviderListCache() {
    }

    public static void update(List<String> names, List<Integer> emptySlots) {
        List<Provider> list = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            list.add(new Provider(names.get(i), emptySlots.get(i)));
        }
        providers = List.copyOf(list);
    }

    /**
     * 本地判定「供应器唯一匹配」，语义与 {@code ProviderSelectScreen} 一致：
     * 按显示名合并为组、组内取空位最多者为代表；搜索词非空且过滤后恰好 1 组时命中。
     *
     * @param rawSearchKey 原始搜索词（可被别名/配方类型映射解析）
     * @return 唯一命中组的原始名称（Component JSON）；搜索词为空或未唯一命中时返回 {@code null}
     */
    public static String findUniqueMatch(String rawSearchKey) {
        String searchKey = RecipeTypeNameConfig.resolveProviderSearchKey(rawSearchKey);
        if (searchKey.isBlank()) {
            return null;
        }

        Map<String, Provider> bestByName = new LinkedHashMap<>();
        for (Provider p : providers) {
            bestByName.merge(displayName(p.rawName()), p,
                    (prev, next) -> next.emptySlots() > prev.emptySlots() ? next : prev);
        }

        Provider hit = null;
        for (Map.Entry<String, Provider> e : bestByName.entrySet()) {
            if (!RecipeTypeNameConfig.matchesProviderName(e.getKey(), searchKey)) {
                continue;
            }
            if (hit != null) {
                return null;
            }
            hit = e.getValue();
        }
        return hit == null ? null : hit.rawName();
    }

    /**
     * 将服务端下发的名称解析为客户端语言的显示文本；非 JSON 名称原样返回。
     */
    public static String displayName(String rawName) {
        if (rawName == null) {
            return "";
        }
        try {
            if (rawName.startsWith("{") || rawName.startsWith("\"")) {
                Component component = Component.Serializer.fromJson(rawName);
                if (component != null) {
                    return component.getString();
                }
            }
        } catch (Exception ignored) {
        }
        return rawName;
    }
}
