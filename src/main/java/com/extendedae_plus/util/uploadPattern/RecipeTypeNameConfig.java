package com.extendedae_plus.util.uploadPattern;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.extendedae_plus.util.GlobalSendMessage.sendPlayerMessage;
import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 负责配置文件 extendedae_plus/recipe_type_names.json 的加载与写入，
 * 以及 recipeType -> 中文名称 / 搜索关键字 的映射逻辑。
 */
public final class RecipeTypeNameConfig {
    private static final String CONFIG_PATH = "extendedae_plus/recipe_type_names.json";
    private static final Map<ResourceLocation, String> CUSTOM_NAMES = new ConcurrentHashMap<>();
    // 允许使用最终搜索关键字（通常为 path 或自定义短语）作为键，例如："assembler": "组装机"
    private static final Map<String, String> CUSTOM_ALIASES = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static {
        try {
            loadRecipeTypeNames();
        } catch (Throwable t) {
            EAP$LOGGER.warn("ExtendedAE_Plus: 映射文件解析失败, {}", t.getMessage());
        }
    }

    private RecipeTypeNameConfig() {}

    // 最近一次通过 JEI 填充到编码终端的“处理配方”的中文名称（如：烧炼/高炉/烟熏...）
    public static volatile String lastProcessingName = null;
    public static void setLastProcessingName(String name) {
        lastProcessingName = name;
    }

    /**
     * 生成默认的配方类型映射，用于配置文件模板。
     *
     * @return 默认映射
     */
    private static Map<String, String> getDefaultMappings() {
        Map<String, String> mappings = new HashMap<>();
        // 添加原版和常见模组的默认映射
        mappings.put("minecraft:smelting", "熔炉");
        mappings.put("minecraft:blasting", "高炉");
        mappings.put("minecraft:smoking", "烟熏");
        mappings.put("minecraft:campfire_cooking", "营火");
        mappings.put("gtceu:assembler", "组装机");
        mappings.put("assembler", "组装机");
        return mappings;
    }

    /**
     * 创建默认配置文件模板。
     *
     * @return 默认的 JSON 对象
     */
    private static JsonObject createDefaultTemplate() {
        JsonObject tmpl = new JsonObject();
        // 将默认映射写入 JSON
        getDefaultMappings().forEach(tmpl::addProperty);
        return tmpl;
    }

    /**
     * 加载 JSON 配置文件，若文件不存在返回空对象。
     *
     * @param cfgPath 文件路径
     * @return JSON 对象
     * @throws IOException         如果文件读取失败
     * @throws JsonSyntaxException 如果 JSON 解析失败
     */
    private static JsonObject loadJsonConfig(Path cfgPath) throws IOException, JsonSyntaxException {
        // 文件不存在返回空对象
        if (!Files.exists(cfgPath)) return new JsonObject();
        String json = Files.readString(cfgPath);
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        // 确保返回非 null 对象
        return obj != null ? obj : new JsonObject();
    }

    /**
     * 保存 JSON 配置到文件。
     *
     * @param cfgPath 文件路径
     * @param config  JSON 对象
     * @throws IOException 如果文件写入失败
     */
    private static void saveJsonConfig(Path cfgPath, JsonObject config) throws IOException {
        Files.createDirectories(cfgPath.getParent());
        // 写入格式化 JSON
        Files.writeString(cfgPath, GSON.toJson(config));
    }

    /**
     * 加载配方类型名称映射。如果配置文件不存在，则生成默认模板。
     * 支持 ResourceLocation 格式（namespace:path）和别名格式（仅 path）。
     *
     * @throws IOException 如果文件读写失败
     */
    public static synchronized void loadRecipeTypeNames() throws IOException {
        // 获取配置文件路径
        Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_PATH);
        JsonObject config = loadJsonConfig(cfgPath);
        if (config.entrySet().isEmpty()) {
            // 文件为空或不存在时生成默认模板
            config = createDefaultTemplate();
            saveJsonConfig(cfgPath, config);
        }

        Map<ResourceLocation, String> nameMap = new HashMap<>();
        Map<String, String> alias = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                String name = value.getAsString();
                if (name == null || name.isBlank()) continue;
                if (key.contains(":")) {
                    try {
                        // 解析完整 ID
                        ResourceLocation rl = new ResourceLocation(key);
                        nameMap.put(rl, name);
                    } catch (Exception ignored) {}
                } else {
                    // 存入别名映射（小写）
                    alias.put(key.toLowerCase(), name);
                }
            }
        }

        CUSTOM_NAMES.clear();
        // 批量更新 ResourceLocation 映射
        CUSTOM_NAMES.putAll(nameMap);
        CUSTOM_ALIASES.clear();
        // 批量更新别名映射
        CUSTOM_ALIASES.putAll(alias);
    }

    /**
     * 新增或更新别名到名称的映射，并保存到配置文件。
     *
     * @param aliasKey 最终搜索关键字（不含冒号），大小写不敏感
     * @param value  名称
     * @return 是否写入成功
     */
    public static synchronized boolean addOrUpdateAliasMapping(String aliasKey, String value) {
        if (aliasKey == null || aliasKey.isBlank() || value == null || value.isBlank()) {
            return false; // 输入验证
        }
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_PATH); // 获取配置文件路径
            JsonObject config = loadJsonConfig(cfgPath); // 加载现有配置
            String key = aliasKey.trim();
            config.addProperty(key, value); // 更新或添加映射
            saveJsonConfig(cfgPath, config); // 保存到文件

            // 更新内存映射
            if (key.contains(":")) {
                try {
                    ResourceLocation rl = new ResourceLocation(key); // 解析完整 ID
                    CUSTOM_NAMES.put(rl, value); // 更新 ResourceLocation 映射
                } catch (Exception ignored) {}
            } else {
                CUSTOM_ALIASES.put(key.toLowerCase(), value); // 更新别名映射（小写）
            }
            return true;
        } catch (IOException | JsonSyntaxException e) {
            sendPlayerMessage(Component.translatable("extendedae_plus.message.config_update_failed", e.getMessage()));            return false;
        }
    }

    /**
     * 按值精确匹配删除映射（支持别名与完整ID）。
     *
     * @param delValue 名称
     * @return 删除的条目数量
     */
    public static synchronized int removeMappingsByCnValue(String delValue) {
        if (delValue == null || delValue.trim().isEmpty()) return 0; // 输入验证
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_PATH); // 获取配置文件路径
            JsonObject config = loadJsonConfig(cfgPath); // 加载现有配置

            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive() && delValue.equals(value.getAsString())) {
                    toRemove.add(entry.getKey()); // 收集匹配中文名称的键
                }
            }

            if (toRemove.isEmpty()) return 0;

            // 从 JSON 中移除
            toRemove.forEach(config::remove); // 移除匹配的键
            saveJsonConfig(cfgPath, config); // 保存更新后的配置

            // 更新内存映射
            for (String key : toRemove) {
                if (key.contains(":")) {
                    try {
                        ResourceLocation rl = new ResourceLocation(key); // 解析完整 ID
                        if (delValue.equals(CUSTOM_NAMES.get(rl))) {
                            CUSTOM_NAMES.remove(rl); // 移除匹配的 ResourceLocation 映射
                        }
                    } catch (Exception ignored) {}
                } else {
                    String lower = key.toLowerCase();
                    if (delValue.equals(CUSTOM_ALIASES.get(lower))) {
                        CUSTOM_ALIASES.remove(lower); // 移除匹配的别名映射
                    }
                }
            }
            return toRemove.size();
        } catch (IOException | JsonSyntaxException e) {
            sendPlayerMessage(Component.translatable("extendedae_plus.message.config_delete_failed", e.getMessage()));
            return 0;
        }
    }

    /**
     * 映射配方类型到搜索关键字，优先使用别名或自定义名称。
     *
     * @param recipe 配方对象
     * @return 搜索关键字（自定义名称、别名或类型路径），或 null 如果无效
     */
    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (recipe == null) return null;
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return null;
        String path = key.getPath().toLowerCase();
        // 优先查别名，再查完整 ID，最后用路径
        return CUSTOM_ALIASES.getOrDefault(path, CUSTOM_NAMES.getOrDefault(key, path));
    }

    /**
     * 通过反射映射 GTCEu 配方到搜索关键字。
     *
     * @param gtRecipeObj GTCEu 配方对象
     * @return 搜索关键字，或 null 如果映射失败
     */
    public static String mapGTCEuRecipeToSearchKey(Object gtRecipeObj) {
        if (gtRecipeObj == null) return null;
        try {
            // 获取配方类型
            Method mGetType = gtRecipeObj.getClass().getMethod("getType");
            Object typeObj = mGetType.invoke(gtRecipeObj);
            String idStr = String.valueOf(typeObj);
            if (idStr == null || idStr.isBlank()) return null;
            // 解析类型 ID
            ResourceLocation rl = new ResourceLocation(idStr);
            // 1) 别名优先（使用 path 作为最终搜索关键字）
            String path = rl.getPath();
            if (path != null) {
                String alias = CUSTOM_ALIASES.get(path.toLowerCase());
                if (alias != null && !alias.isBlank()) return alias;
            }
            // 2) 再查完整ID映射
            String custom = CUSTOM_NAMES.get(rl);
            // 3) 默认返回自定义名称或路径
            return custom != null && !custom.isBlank() ? custom : path;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 从未知配方类推导搜索关键字。
     *
     * @param recipeBase 配方对象
     * @return 推导的搜索关键字，或 null 如果失败
     */
    public static String deriveSearchKeyFromUnknownRecipe(Object recipeBase) {
        if (recipeBase == null) return null;
        try {
            Class<?> cls = recipeBase.getClass();
            String simple = cls.getSimpleName();
            String pkg = cls.getName();

            String namespace = null;
            String lower = pkg.toLowerCase();
            // 检测模组命名空间
            if (lower.contains("gtceu")) namespace = "gtceu";
            else if (lower.contains("gregtech")) namespace = "gregtech";
            else if (lower.contains("projecte")) namespace = "projecte";
            else if (lower.contains("create")) namespace = "create";
            else if (lower.contains("immersiveengineering")) namespace = "immersive";

            String token = toSearchToken(simple); // 转换类名为关键字
            String key = (namespace != null && token != null && !token.isBlank()) ?
                    namespace + " " + token :
                    token;
            if (key == null || key.isBlank()) return null;
            // 尝试别名映射（大小写不敏感）
            String alias = CUSTOM_ALIASES.get(key.toLowerCase());
            // 返回别名或推导的键
            return alias != null && !alias.isBlank() ? alias : key;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 将类名转换为搜索关键字。
     *
     * @param simpleName 类简单名称
     * @return 转换后的关键字，或 null 如果无效
     */
    private static String toSearchToken(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return null;
        // 去掉常见后缀
        String s = simpleName
                .replaceAll("Recipe(s)?$", "")
                .replaceAll("Category$", "")
                .replaceAll("JEI$", "")
                .replaceAll("(?<!^)([A-Z])", " $1") // 驼峰转空格
                .toLowerCase()
                .trim();
        return s.isBlank() ? null : s;
    }
}
