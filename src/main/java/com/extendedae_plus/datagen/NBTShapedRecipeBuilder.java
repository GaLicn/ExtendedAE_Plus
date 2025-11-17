package com.extendedae_plus.datagen;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.RequirementsStrategy;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.data.recipes.CraftingRecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.ConditionalRecipe;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.crafting.conditions.NotCondition;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class NBTShapedRecipeBuilder extends CraftingRecipeBuilder implements RecipeBuilder {

    private final RecipeCategory category;
    private final ItemStack result;
    private final List<String> rows = Lists.newArrayList();
    private final Map<Character, JsonObject> key = Maps.newLinkedHashMap();
    private final Advancement.Builder advancement = Advancement.Builder.recipeAdvancement();
    private final List<ICondition> conditions = new ArrayList<>();

    @Nullable private String group;
    private boolean showNotification = true;

    private NBTShapedRecipeBuilder(RecipeCategory category, ItemStack result) {
        this.category = category;
        this.result = result;
    }

    public static NBTShapedRecipeBuilder shaped(RecipeCategory category, ItemStack result) {
        return new NBTShapedRecipeBuilder(category, result);
    }

    public static NBTShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result) {
        return shaped(category, new ItemStack(result));
    }

    public static NBTShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result, int count) {
        return shaped(category, new ItemStack(result, count));
    }

    /** 普通物品 */
    public NBTShapedRecipeBuilder define(Character symbol, ItemLike item) {
        JsonObject json = new JsonObject();
        json.addProperty("item", Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item.asItem())).toString());
        this.key.put(symbol, json);
        return this;
    }

    public NBTShapedRecipeBuilder define(Character symbol, TagKey<Item> tag) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", tag.location().toString());
        this.key.put(symbol, json);
        return this;
    }

    /** forge:nbt 输入（无 count，结构化 NBT） */
    public NBTShapedRecipeBuilder defineNbt(Character symbol, ItemStack stackWithNbt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forge:nbt");
        json.addProperty("item", Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stackWithNbt.getItem())).toString());
        if (stackWithNbt.hasTag() && stackWithNbt.getTag() != null) {
            json.add("nbt", nbtToJson(stackWithNbt.getTag()));
        }
        this.key.put(symbol, json);
        return this;
    }

    public NBTShapedRecipeBuilder pattern(String pattern) {
        if (!this.rows.isEmpty() && pattern.length() != this.rows.get(0).length()) {
            throw new IllegalArgumentException("Pattern must be the same width");
        }
        this.rows.add(pattern);
        return this;
    }

    public @NotNull NBTShapedRecipeBuilder unlockedBy(@NotNull String name, @NotNull CriterionTriggerInstance criterion) {
        this.advancement.addCriterion(name, criterion);
        return this;
    }

    public @NotNull NBTShapedRecipeBuilder group(@Nullable String group) {
        this.group = group;
        return this;
    }

    public NBTShapedRecipeBuilder showNotification(boolean show) {
        this.showNotification = show;
        return this;
    }

    /** 添加 Forge 条件 */
    public NBTShapedRecipeBuilder condition(ICondition condition) {
        this.conditions.add(Objects.requireNonNull(condition));
        return this;
    }

    /** 便捷：mod loaded 条件 */
    public NBTShapedRecipeBuilder requiresMod(String modid) {
        return condition(new ModLoadedCondition(modid));
    }

    /** NOT 条件：mod 未加载时生效 */
    public NBTShapedRecipeBuilder notRequiresMod(String modid) {
        return condition(new NotCondition(new ModLoadedCondition(modid)));
    }

    @Override
    public @NotNull Item getResult() {
        return this.result.getItem();
    }

    // === save 方法重载 ===

    @Override
    public void save(@NotNull Consumer<FinishedRecipe> consumer, @NotNull String id) {
        this.save(consumer, new ResourceLocation(id));
    }

    @Override
    public void save(@NotNull Consumer<FinishedRecipe> consumer, @NotNull ResourceLocation id) {
        this.ensureValid(id);

        // 构建 advancement
        Advancement.Builder advancementBuilder = this.advancement
                .parent(ROOT_RECIPE_ADVANCEMENT)
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(RequirementsStrategy.OR);

        // 构建普通配方结果
        FinishedRecipe plainRecipe = new NBTResult(
                id, this.result, this.group == null ? "" : this.group,
                determineBookCategory(this.category), this.rows, this.key,
                advancementBuilder, id.withPrefix("recipes/" + this.category.getFolderName() + "/"),
                this.showNotification
        );

        if (this.conditions.isEmpty()) {
            // 无条件 → 直接保存
            consumer.accept(plainRecipe);
        } else {
            // 有条件 → 使用 ConditionalRecipe 包装
            ConditionalRecipe.Builder builder = ConditionalRecipe.builder();
            for (ICondition cond : this.conditions) {
                builder.addCondition(cond);
            }
            builder.addRecipe(c -> c.accept(plainRecipe));
            builder.generateAdvancement();
            builder.build(consumer, id);
        }
    }

    private void ensureValid(ResourceLocation id) {
        if (this.rows.isEmpty()) throw new IllegalStateException("No pattern for " + id);
        Set<Character> defined = Sets.newHashSet(this.key.keySet());
        defined.remove(' ');
        for (String row : this.rows) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (!this.key.containsKey(c) && c != ' ') throw new IllegalStateException("Undefined symbol '" + c + "'");
                defined.remove(c);
            }
        }
        if (!defined.isEmpty()) throw new IllegalStateException("Unused ingredients: " + defined);
        if (this.rows.size() == 1 && this.rows.get(0).length() == 1) throw new IllegalStateException("Use shapeless for 1x1");
        if (this.advancement.getCriteria().isEmpty()) throw new IllegalStateException("No unlock criterion for " + id);
    }

    /** NBT → JsonElement（结构化） */
    private static JsonElement nbtToJson(CompoundTag tag) {
        JsonObject obj = new JsonObject();
        tag.getAllKeys().forEach(key -> {
            Tag value = tag.get(key);
            if (value instanceof CompoundTag c) {
                obj.add(key, nbtToJson(c));
            } else if (value instanceof ListTag list) {
                JsonArray arr = new JsonArray();
                for (Tag item : list) {
                    arr.add(nbtToJson(item));
                }
                obj.add(key, arr);
            } else if (value instanceof StringTag s) {
                obj.addProperty(key, s.getAsString());
            } else if (value instanceof NumericTag n) {
                if (value instanceof ByteTag) obj.addProperty(key, n.getAsByte());
                else if (value instanceof ShortTag) obj.addProperty(key, n.getAsShort());
                else if (value instanceof IntTag) obj.addProperty(key, n.getAsInt());
                else if (value instanceof LongTag) obj.addProperty(key, n.getAsLong());
                else if (value instanceof FloatTag) obj.addProperty(key, n.getAsFloat());
                else if (value instanceof DoubleTag) obj.addProperty(key, n.getAsDouble());
            } else {
                if (value != null) {
                    obj.addProperty(key, value.getAsString());
                }
            }
        });
        return obj;
    }

    private static JsonElement nbtToJson(Tag tag) {
        if (tag instanceof CompoundTag c) return nbtToJson(c);
        if (tag instanceof StringTag s) return new JsonPrimitive(s.getAsString());
        if (tag instanceof NumericTag n) {
            if (tag instanceof ByteTag) return new JsonPrimitive(n.getAsByte());
            if (tag instanceof ShortTag) return new JsonPrimitive(n.getAsShort());
            if (tag instanceof IntTag) return new JsonPrimitive(n.getAsInt());
            if (tag instanceof LongTag) return new JsonPrimitive(n.getAsLong());
            if (tag instanceof FloatTag) return new JsonPrimitive(n.getAsFloat());
            if (tag instanceof DoubleTag) return new JsonPrimitive(n.getAsDouble());
        }
        return new JsonPrimitive(tag.getAsString());
    }

    /** 完美 Result */
    public static class NBTResult extends CraftingRecipeBuilder.CraftingResult {
        private final ResourceLocation id;
        private final ItemStack result;
        private final String group;
        private final List<String> pattern;
        private final Map<Character, JsonObject> key;
        private final Advancement.Builder advancement;
        private final ResourceLocation advancementId;
        private final boolean showNotification;

        public NBTResult(ResourceLocation id, ItemStack result, String group,
                         CraftingBookCategory category, List<String> pattern,
                         Map<Character, JsonObject> key, Advancement.Builder advancement,
                         ResourceLocation advancementId, boolean showNotification) {
            super(category);
            this.id = id;
            this.result = result;
            this.group = group;
            this.pattern = pattern;
            this.key = key;
            this.advancement = advancement;
            this.advancementId = advancementId;
            this.showNotification = showNotification;
        }

        @Override
        public void serializeRecipeData(@NotNull JsonObject json) {
            if (!this.group.isEmpty()) json.addProperty("group", this.group);

            JsonArray patternArray = new JsonArray();
            this.pattern.forEach(patternArray::add);
            json.add("pattern", patternArray);

            JsonObject keyObj = new JsonObject();
            this.key.forEach((c, jsonObj) -> keyObj.add(String.valueOf(c), jsonObj));
            json.add("key", keyObj);

            JsonObject resultObj = new JsonObject();
            resultObj.addProperty("type", "forge:partial_nbt");
            resultObj.addProperty("item", Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(this.result.getItem())).toString());
            if (this.result.getCount() > 1) resultObj.addProperty("count", this.result.getCount());
            if (this.result.hasTag() && this.result.getTag() != null) {
                resultObj.add("nbt", nbtToJson(this.result.getTag()));
            }
            json.add("result", resultObj);

            json.addProperty("show_notification", this.showNotification);
        }

        @Override public @NotNull RecipeSerializer<?> getType() { return RecipeSerializer.SHAPED_RECIPE; }
        @Override public @NotNull ResourceLocation getId() { return this.id; }
        @Nullable @Override public JsonObject serializeAdvancement() { return this.advancement.serializeToJson(); }
        @Nullable @Override public ResourceLocation getAdvancementId() { return this.advancementId; }
    }
}