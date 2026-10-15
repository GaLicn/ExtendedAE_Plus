package com.extendedae_plus.util.uploadPattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * 配方类型名称映射与供应器搜索关键字配置。
 *
 * <p>承载三块职责：</p>
 * <ul>
 *   <li>配置读写：{@code recipe_type_names.json} 的加载、增删与快照；</li>
 *   <li>映射解析：配方类型 / 别名 → 实际搜索关键字，含跨模组重名 path 的消歧；</li>
 *   <li>会话状态：最近一次供应器搜索关键字，以及供应器名称的模糊匹配。</li>
 * </ul>
 */
public final class RecipeTypeNameConfig {

    // --------------------------- 配置：RecipeType 中文名称映射 ---------------------------
    private static final String CONFIG_RELATIVE = "extendedae_plus/recipe_type_names.json";
    private static final Map<ResourceLocation, String> CUSTOM_NAMES = new ConcurrentHashMap<>();
    private static final Map<String, String> CUSTOM_ALIASES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // 最近一次打开供应器选择界面时的预设搜索关键字。
    // 处理样板会写入映射后的配方类型关键字，合成样板固定使用 crafting（可通过映射改名）。
    public static final String DEFAULT_CRAFTING_SEARCH_KEY = "crafting";
    private static volatile String lastProviderSearchKey = null;
    // 供应器名称匹配：优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配。
    private static Boolean JEC_AVAILABLE = null;
    private static Method JEC_CONTAINS = null;

    // path -> 使用该 path 的 namespace 集合，用于识别跨模组的配方类型重名（如 mekanism:reaction 与 advanced_ae:reaction）。
    // 惰性构建并缓存，避免在类加载期访问尚未就绪的注册表。
    private static volatile Map<String, Set<String>> PATH_NAMESPACES = null;

    public record RecipeTypeMapping(String key, String value) {}

    private RecipeTypeNameConfig() {
    }

    static {
        try {
            loadRecipeTypeNames();
        } catch (Throwable t) {
            // 配置加载失败时保持空映射，不影响配方记录流程。
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
                // 默认不预置映射，分类标题由配方查看器统一提供。
                Files.createDirectories(cfgPath.getParent());
                Files.writeString(cfgPath, GSON.toJson(new JsonObject()));
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
                                var rl = ResourceLocation.tryParse(k);
                                if (rl != null) {
                                    map.put(rl, name);
                                }
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

    public static void setLastProcessingName(String name) {
        setLastProviderSearchKey(name);
    }

    public static void setLastProviderSearchKey(String name) {
        if (name == null) {
            lastProviderSearchKey = null;
            return;
        }
        String trimmed = name.trim();
        String resolved = resolveProviderSearchKey(trimmed);
        lastProviderSearchKey = resolved.isEmpty() ? null : resolved;
    }

    public static void presetCraftingProviderSearchKey() {
        setLastProviderSearchKey(resolveSearchKeyAlias(DEFAULT_CRAFTING_SEARCH_KEY));
    }

    public static String consumeLastProviderSearchKey() {
        String searchKey = lastProviderSearchKey;
        lastProviderSearchKey = null;
        return searchKey;
    }

    /**
     * 读取但不消费最近一次的供应器搜索词。
     *
     * <p>编码按钮点击时需要把搜索词随包上传给服务端做「唯一匹配」判定，而弹窗打开时仍需要
     * 消费同一个搜索词。两者互不影响：本方法只读不清，{@link #consumeLastProviderSearchKey()}
     * 保留原有清空语义。</p>
     */
    public static String peekLastProviderSearchKey() {
        return lastProviderSearchKey;
    }

    /**
     * 供应器名称与搜索词的匹配判定（唯一匹配语义的公共实现）。
     *
     * <p>逻辑从 {@code ProviderSelectScreen.nameMatches} 原样迁移：优先使用 JEC 的拼音
     * {@code contains}（含大小写不敏感二次尝试），失败时回退到大小写不敏感子串匹配。
     * 客户端弹窗与服务端自动直传共用此方法，保证「唯一」判定两端一致。</p>
     *
     * @param name 供应器显示名
     * @param key  搜索关键字；为空表示不过滤，视为匹配
     * @return 是否匹配
     */
    public static boolean matchesProviderName(String name, String key) {
        if (name == null) return false;
        if (key == null || key.isEmpty()) return true;
        try {
            if (JEC_AVAILABLE == null) {
                try {
                    Class<?> cls = Class.forName("me.towdium.jecharacters.utils.Match");
                    JEC_CONTAINS = cls.getMethod("contains", CharSequence.class, CharSequence.class);
                    JEC_AVAILABLE = true;
                } catch (Throwable t) {
                    JEC_AVAILABLE = false;
                }
            }
            if (Boolean.TRUE.equals(JEC_AVAILABLE) && JEC_CONTAINS != null) {
                Object r = JEC_CONTAINS.invoke(null, name, key);
                if (r instanceof Boolean && (Boolean) r) return true;
                String nL = name.toLowerCase();
                String kL = key.toLowerCase();
                Object r2 = JEC_CONTAINS.invoke(null, nL, kL);
                if (r2 instanceof Boolean && (Boolean) r2) return true;
            }
        } catch (Throwable ignored) {
            // 回退到子串匹配
        }
        return name.toLowerCase().contains(key.toLowerCase());
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
                    var rl = ResourceLocation.tryParse(key);
                    if (rl != null) {
                        CUSTOM_NAMES.put(rl, cnValue);
                    }
                } catch (Exception ignored) {}
            } else {
                CUSTOM_ALIASES.put(key.toLowerCase(), cnValue);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean addOrUpdateRecipeTypeMapping(String key, String value) {
        return addOrUpdateAliasMapping(key, value);
    }

    /** 返回当前生效的映射快照，供可视化管理界面展示。 */
    public static List<RecipeTypeMapping> getRecipeTypeMappings() {
        List<RecipeTypeMapping> mappings = new ArrayList<>();
        CUSTOM_NAMES.forEach((key, value) -> mappings.add(new RecipeTypeMapping(key.toString(), value)));
        CUSTOM_ALIASES.forEach((key, value) -> mappings.add(new RecipeTypeMapping(key, value)));
        mappings.sort(Comparator.comparing(RecipeTypeMapping::key, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(mappings);
    }

    /** 按配置键删除单条映射，别名键匹配时忽略大小写。 */
    public static synchronized boolean removeRecipeTypeMapping(String mappingKey) {
        if (mappingKey == null || mappingKey.isBlank()) {
            return false;
        }
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_RELATIVE);
            if (!Files.exists(cfgPath)) {
                return false;
            }
            JsonObject obj = GSON.fromJson(Files.readString(cfgPath), JsonObject.class);
            if (obj == null) {
                return false;
            }

            String requested = mappingKey.trim();
            String storedKey = obj.keySet().stream()
                    .filter(key -> mappingKeysEqual(key, requested))
                    .findFirst()
                    .orElse(null);
            if (storedKey == null) {
                return false;
            }

            obj.remove(storedKey);
            Files.writeString(cfgPath, GSON.toJson(obj));
            loadRecipeTypeNames();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean mappingKeysEqual(String first, String second) {
        if (first.contains(":") || second.contains(":")) {
            return Objects.equals(ResourceLocation.tryParse(first), ResourceLocation.tryParse(second));
        }
        return first.equalsIgnoreCase(second);
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

            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
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
                        var rl = ResourceLocation.tryParse(k);
                        if (rl != null) {
                            String cur = CUSTOM_NAMES.get(rl);
                            if (target.equals(cur)) {
                                CUSTOM_NAMES.remove(rl);
                            }
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
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 供搜索使用的关键字映射：
     * - 有中文映射则返回中文；
     * - 否则返回配方类型的 path（不含命名空间），例如 assembler。
     */
    public static String resolveSearchKeyAlias(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String normalized = rawKey.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        // 纯 path 且该 path 被多个模组共用时不套用别名，强制用户改用完整 ID。
        if (normalized.indexOf(':') < 0 && isAmbiguousPath(normalized)) {
            return normalized;
        }
        String alias = CUSTOM_ALIASES.get(normalized.toLowerCase());
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return normalized;
    }

    /** 将供应器界面的原始输入转换为映射后的实际搜索关键字。 */
    public static String resolveProviderSearchKey(String rawKey) {
        String normalized = rawKey == null ? "" : rawKey.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        String alias = resolveSearchKeyAlias(normalized);
        if (!normalized.equals(alias)) {
            return alias;
        }

        // 仅完整 ID（含冒号）才解析配方类型映射；纯 path 一律交由别名解析，
        // 避免 ResourceLocation.tryParse("reaction") 被错误补成 minecraft:reaction。
        if (normalized.indexOf(':') >= 0) {
            ResourceLocation recipeType = ResourceLocation.tryParse(normalized);
            if (recipeType != null) {
                String mapped = resolveRecipeTypeSearchKey(recipeType, null);
                if (mapped != null && !mapped.isBlank() && !mapped.equals(recipeType.getPath())) {
                    return mapped;
                }
            }
        }
        return normalized;
    }

    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (recipe == null) return null;
        return resolveRecipeTypeSearchKey(resolveRecipeTypeId(recipe.getType()), null);
    }

    /** 统一解析配方类型，优先配置映射，其次使用配方查看器提供的分类标题。 */
    public static String resolveRecipeTypeSearchKey(ResourceLocation key, String displayName) {
        if (key == null) return null;

        String custom = CUSTOM_NAMES.get(key);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }

        // 该 path 被多个模组共用时，纯 path 别名不生效，直接回落到完整 ID，避免跨模组串味。
        if (isAmbiguousPath(key.getPath())) {
            return key.toString();
        }

        String alias = CUSTOM_ALIASES.get(key.getPath().toLowerCase(Locale.ROOT));
        if (alias != null && !alias.isBlank()) {
            return alias;
        }

        return displayName != null && !displayName.isBlank() ? displayName : key.getPath();
    }

    /** 惰性获取 path -> namespaces 索引，首次访问时构建。 */
    private static Map<String, Set<String>> pathNamespaces() {
        Map<String, Set<String>> idx = PATH_NAMESPACES;
        if (idx == null) {
            idx = buildPathNamespaces();
            PATH_NAMESPACES = idx;
        }
        return idx;
    }

    /** 遍历配方类型注册表统计每个 path 对应的 namespace；键统一小写，避免大小写差异绕过消歧判定。 */
    private static Map<String, Set<String>> buildPathNamespaces() {
        Map<String, Set<String>> idx = new HashMap<>();
        for (ResourceLocation id : BuiltInRegistries.RECIPE_TYPE.keySet()) {
            idx.computeIfAbsent(id.getPath().toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(id.getNamespace());
        }
        return idx;
    }

    /** 该 path 是否被多个 namespace 共用（跨模组重名）。查询前统一小写，与索引键保持一致。 */
    public static boolean isAmbiguousPath(String path) {
        if (path == null) return false;
        Set<String> namespaces = pathNamespaces().get(path.toLowerCase(Locale.ROOT));
        return namespaces != null && namespaces.size() > 1;
    }

    /** 返回与该 path 冲突的 namespace 集合，供界面提示使用；无冲突时返回空集。 */
    public static Set<String> namespacesForPath(String path) {
        if (path == null) return Set.of();
        Set<String> namespaces = pathNamespaces().get(path.toLowerCase(Locale.ROOT));
        return namespaces == null ? Set.of() : Set.copyOf(namespaces);
    }

    /** 注册表反查失败时，RecipeType.simple 创建的类型仍会通过 toString 暴露其 ID。 */
    private static ResourceLocation resolveRecipeTypeId(RecipeType<?> type) {
        if (type == null) return null;
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key != null) {
            return key;
        }
        return ResourceLocation.tryParse(type.toString());
    }

    /**
     * 当 JEI 传入的 recipeBase 不是原版 Recipe<?> 时，优先解析类型 ID，再根据类名推导搜索关键字。
     */
    public static String deriveSearchKeyFromUnknownRecipe(Object recipeBase) {
        if (recipeBase == null) return null;
        // 优先尝试反射 getType()：覆盖“单一配方类 + 类型字段”设计的模组（如 Oritech），
        // 避免所有机器配方被类名推导成同一个关键字。
        try {
            Object type = recipeBase.getClass().getMethod("getType").invoke(recipeBase);
            // 非原版类型也可通过其 namespace:path 标识走同一套映射。
            ResourceLocation key = type instanceof RecipeType<?> rt
                    ? resolveRecipeTypeId(rt)
                    : type == null ? null : ResourceLocation.tryParse(type.toString());
            if (key != null) {
                String resolved = resolveRecipeTypeSearchKey(key, null);
                if (resolved != null && !resolved.isBlank()) return resolved;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            LOGGER.debug("无法从配方类型解析搜索关键字，改用类名: {}", recipeBase.getClass().getName(), e);
        }
        try {
            Class<?> cls = recipeBase.getClass();
            String simple = cls.getSimpleName();
            String pkg = cls.getName();

            String ns = null;
            String lower = pkg.toLowerCase();
            if (lower.contains("projecte")) ns = "projecte";
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
        } catch (RuntimeException e) {
            LOGGER.warn("无法根据配方类名推导搜索关键字: {}", recipeBase.getClass().getName(), e);
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
