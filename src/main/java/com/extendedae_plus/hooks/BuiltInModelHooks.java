package com.extendedae_plus.hooks;

import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 复刻 Fabric 的内置模型注册能力（与 AE2/MAE2 相同实现）。
 */
public final class BuiltInModelHooks {
    private static final Map<Identifier, UnbakedModel> BUILTIN_MODELS = new ConcurrentHashMap<>();

    private BuiltInModelHooks() {}

    public static void addBuiltInModel(Identifier id, UnbakedModel model) {
        var prev = BUILTIN_MODELS.putIfAbsent(id, model);
        if (prev != null) {
            throw new IllegalStateException("Duplicate built-in model ID: " + id);
        }
    }

    public static UnbakedModel getBuiltInModel(Identifier id) {
        return BUILTIN_MODELS.get(id);
    }
}
