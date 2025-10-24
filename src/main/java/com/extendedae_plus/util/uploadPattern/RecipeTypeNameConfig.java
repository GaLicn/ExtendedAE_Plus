package com.extendedae_plus.util.uploadPattern;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.extendedae_plus.util.GlobalSendMessage.sendPlayerMessage;

/**
 * 负责配置文件 extendedae_plus/recipe_type_names.json 的加载与写入，
 * 以及 recipeType -> 中文名称 / 搜索关键字 的映射逻辑。
 */
public final class RecipeTypeNameConfig {

    private RecipeTypeNameConfig() {}

    private static final String CONFIG_RELATIVE = "extendedae_plus/recipe_type_names.json";
    private static final Map<ResourceLocation, String> CUSTOM_NAMES = new ConcurrentHashMap<>();
    // 允许使用最终搜索关键字（通常为 path 或自定义短语）作为键，例如："assembler": "组装机"
    private static final Map<String, String> CUSTOM_ALIASES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static {
        try {
            loadRecipeTypeNames();
        } catch (Throwable t) {
            // 安静失败，使用内置映射
            sendPlayerMessage(Component.literal("ExtendedAE_Plus: 配置文件解析失败, " + t.getMessage()));
        }
    }

    /**
     * 从配置文件加载 RecipeType → 中文名称映射。文件不存在则生成模板。
     * 同时支持“别名”形式：不含冒号的键会被视为最终搜索关键字（大小写不敏感），如：
     * {
     *   "assembler": "组装机"
     * }
     */
    public static synchronized void loadRecipeTypeNames() {
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                // 创建目录并写入模板
                Files.createDirectories(cfgPath.getParent());
                JsonObject tmpl = new JsonObject();
                // 提供一些常见原版默认（仅作为示例，实际仍以内置 switch 为兜底）
                tmpl.addProperty("minecraft:smelting", "熔炉");
                tmpl.addProperty("minecraft:blasting", "高炉");
                tmpl.addProperty("minecraft:smoking", "烟熏");
                tmpl.addProperty("minecraft:campfire_cooking", "营火");
                // GTCEu 示例占位
                tmpl.addProperty("gtceu:assembler", "组装机");
                tmpl.addProperty("gtceu:arc_furnace", "电弧炉");
                tmpl.addProperty("gtceu:chemical_reactor", "化学反应器");
                // 也支持别名（最终搜索关键字）形式，例如：
                tmpl.addProperty("assembler", "组装机");
                Files.writeString(cfgPath, GSON.toJson(tmpl));
            }

            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            Map<ResourceLocation, String> map = new HashMap<>();
            Map<String, String> alias = new HashMap<>();
            if (obj != null) {
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    String k = e.getKey();
                    JsonElement v = e.getValue();
                    if (v != null && v.isJsonPrimitive()) {
                        String name = v.getAsString();
                        if (name == null || name.isBlank()) continue;
                        if (k.contains(":")) {
                            // 形如 namespace:path
                            try {
                                ResourceLocation rl = new ResourceLocation(k);
                                map.put(rl, name);
                            } catch (Exception ignored) {}
                        } else {
                            // 视为别名：最终搜索关键字（大小写不敏感）
                            alias.put(k.toLowerCase(), name);
                        }
                    }
                }
            }
            CUSTOM_NAMES.clear();
            CUSTOM_NAMES.putAll(map);
            CUSTOM_ALIASES.clear();
            CUSTOM_ALIASES.putAll(alias);
        } catch (IOException ignored) {
        }
    }

    // 最近一次通过 JEI 填充到编码终端的“处理配方”的中文名称（如：烧炼/高炉/烟熏...）
    public static volatile String lastProcessingName = null;

    public static void setLastProcessingName(String name) {
        lastProcessingName = name;
    }

    /**
     * 向配置中新增或更新“别名 -> 中文”映射，并刷新内存映射。
     * 仅用于非原版（或希望使用最终搜索关键字）场景。
     *
     * @param aliasKey 最终搜索关键字（不含冒号），大小写不敏感
     * @param cnValue  中文名称
     * @return 是否写入成功
     */
    public static synchronized boolean addOrUpdateAliasMapping(String aliasKey, String cnValue) {
        if (aliasKey == null || aliasKey.isBlank() || cnValue == null || cnValue.isBlank()) {
            return false;
        }
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                // 若文件不存在，先创建模板
                loadRecipeTypeNames();
            }
            JsonObject obj;
            if (Files.exists(cfgPath)) {
                String json = Files.readString(cfgPath);
                obj = GSON.fromJson(json, JsonObject.class);
                if (obj == null) obj = new JsonObject();
            } else {
                obj = new JsonObject();
            }
            String key = aliasKey.trim();
            // 仅允许作为别名写入（不含冒号），如包含冒号，仍按原样写入，但推荐别名
            obj.addProperty(key, cnValue);
            Files.createDirectories(cfgPath.getParent());
            Files.writeString(cfgPath, GSON.toJson(obj));

            // 更新内存映射
            if (key.contains(":")) {
                try {
                    ResourceLocation rl = new ResourceLocation(key);
                    CUSTOM_NAMES.put(rl, cnValue);
                } catch (Exception ignored) {}
            } else {
                CUSTOM_ALIASES.put(key.toLowerCase(), cnValue);
            }
            return true;
        } catch (JsonSyntaxException e) {
            sendPlayerMessage(Component.literal("ExtendedAE_Plus: 配置文件解析失败, " + e.getMessage()));
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * 按中文值精确匹配删除映射（支持别名与完整ID）。
     * 返回删除的条目数量。
     */
    public static synchronized int removeMappingsByCnValue(String cnValue) {
        if (cnValue == null) return 0;
        String target = cnValue.trim();
        if (target.isEmpty()) return 0;
        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path cfgPath = cfgDir.resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                return 0;
            }
            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return 0;

            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
                JsonElement v = e.getValue();
                if (v != null && v.isJsonPrimitive()) {
                    String name = v.getAsString();
                    if (target.equals(name)) {
                        toRemove.add(e.getKey());
                    }
                }
            }
            if (toRemove.isEmpty()) return 0;

            // 从 JSON 中移除
            for (String k : toRemove) {
                obj.remove(k);
            }
            Files.createDirectories(cfgPath.getParent());
            Files.writeString(cfgPath, GSON.toJson(obj));

            // 同步移除内存映射
            for (String k : toRemove) {
                if (k.contains(":")) {
                    try {
                        ResourceLocation rl = new ResourceLocation(k);
                        // 仅当值匹配才移除（双重保险）
                        String cur = CUSTOM_NAMES.get(rl);
                        if (target.equals(cur)) {
                            CUSTOM_NAMES.remove(rl);
                        }
                    } catch (Exception ignored) {}
                } else {
                    // 别名按小写存放
                    String lower = k.toLowerCase();
                    String cur = CUSTOM_ALIASES.get(lower);
                    if (target.equals(cur)) {
                        CUSTOM_ALIASES.remove(lower);
                    }
                }
            }
            return toRemove.size();
        } catch (JsonSyntaxException e) {
            sendPlayerMessage(Component.literal("ExtendedAE_Plus: 配置文件解析失败, " + e.getMessage()));
        } catch (IOException e) {
            return 0;
        }
        return 0;
    }

    /**
     * 供搜索使用的关键字映射：
     * - 有中文映射则返回中文；
     * - 否则返回配方类型的 path（不含命名空间），例如 assembler。
     */
    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (recipe == null) return null;
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return null;
        // 先查别名（按 path 匹配）
        String alias = CUSTOM_ALIASES.get(key.getPath().toLowerCase());
        if (alias != null && !alias.isBlank()) return alias;
        // 再查完整ID映射
        String custom = CUSTOM_NAMES.get(key);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return key.getPath();
    }

    /**
     * 仅使用反射的 GTCEu GTRecipe -> 搜索关键字（避免在运行时直接引用 GTCEu 类）。
     */
    public static String mapGTCEuRecipeToSearchKey(Object gtRecipeObj) {
        if (gtRecipeObj == null) return null;
        try {
            // 通过反射调用 getType()，其 toString() 应返回 registryName，即 namespace:path
            java.lang.reflect.Method mGetType = gtRecipeObj.getClass().getMethod("getType");
            Object typeObj = mGetType.invoke(gtRecipeObj);
            String idStr = String.valueOf(typeObj);
            if (idStr == null || idStr.isBlank()) return null;
            ResourceLocation rl = new ResourceLocation(idStr);
            // 1) 别名优先（使用 path 作为最终搜索关键字）
            String path = rl.getPath();
            if (path != null) {
                String alias = CUSTOM_ALIASES.get(path.toLowerCase());
                if (alias != null && !alias.isBlank()) return alias;
            }
            // 2) 再查完整ID映射
            String custom = CUSTOM_NAMES.get(rl);
            if (custom != null && !custom.isBlank()) return custom;
            // 3) 默认返回 path 作为搜索关键字
            return (path != null && !path.isBlank()) ? path : idStr;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 当 JEI 传入的 recipeBase 不是原版 Recipe<?> 时，根据类的包名/类名推导一个尽量可用的搜索关键字。
     * 例如："moe.gregtech.recipe.SomeAssemblerRecipe" -> "gtceu assembler"
     */
    public static String deriveSearchKeyFromUnknownRecipe(Object recipeBase) {
        if (recipeBase == null) return null;
        try {
            Class<?> cls = recipeBase.getClass();
            String simple = cls.getSimpleName();
            String pkg = cls.getName();

            String ns = null;
            String lower = pkg.toLowerCase();
            if (lower.contains("gtceu")) ns = "gtceu";
            else if (lower.contains("gregtech")) ns = "gregtech";
            else if (lower.contains("projecte")) ns = "projecte";
            else if (lower.contains("create")) ns = "create";
            else if (lower.contains("immersiveengineering")) ns = "immersive";

            String token = toSearchToken(simple);
            String key;
            if (ns != null && token != null && !token.isBlank()) key = ns + " " + token;
            else key = token != null && !token.isBlank() ? token : ns;
            if (key == null || key.isBlank()) return null;
            // 尝试别名映射（大小写不敏感）
            String alias = CUSTOM_ALIASES.get(key.toLowerCase());
            return (alias != null && !alias.isBlank()) ? alias : key;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toSearchToken(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return null;
        // 去掉常见后缀
        String s = simpleName
                .replaceAll("Recipe$", "")
                .replaceAll("Recipes$", "")
                .replaceAll("Category$", "")
                .replaceAll("JEI$", "");
        // 驼峰转空格并小写
        s = s.replaceAll("(?<!^)([A-Z])", " $1").toLowerCase();
        // 取首个关键词
        s = s.trim();
        return s;
    }
}
