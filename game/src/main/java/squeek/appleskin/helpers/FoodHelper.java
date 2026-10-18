package squeek.appleskin.helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import squeek.appleskin.api.event.FoodValuesEvent;

public class FoodHelper {
    public static FoodProperties EMPTY_FOOD_COMPONENT = new FoodProperties.Builder().build();
    public static Consumable DEFAULT_CONSUMABLE_COMPONENT = Consumables.DEFAULT_FOOD;
    public static float REGEN_EXHAUSTION_INCREMENT = 6.0f;
    public static float MAX_EXHAUSTION = 4.0f;

    public static boolean isFood(ItemStack itemStack) {
        return itemStack.has(DataComponents.FOOD) && itemStack.has(DataComponents.CONSUMABLE);
    }

    public static boolean canConsume(Player player, FoodProperties foodComponent) {
        return player.canEat(foodComponent.canAlwaysEat());
    }

    public static ConsumableFood getDefaultFoodValues(ItemStack itemStack) {
        return new ConsumableFood(itemStack.getOrDefault(DataComponents.FOOD, EMPTY_FOOD_COMPONENT), itemStack.getOrDefault(DataComponents.CONSUMABLE, DEFAULT_CONSUMABLE_COMPONENT));
    }

    @Nullable
    public static QueriedFoodResult query(ItemStack itemStack, Player player) {
        if (!isFood(itemStack)) {
            return null;
        }
        ConsumableFood defaultFood = getDefaultFoodValues(itemStack);
        FoodValuesEvent event = new FoodValuesEvent(player, itemStack, defaultFood.food(), defaultFood.food());
        FoodValuesEvent.EVENT.fire(event);
        return new QueriedFoodResult(event.defaultFoodComponent, event.modifiedFoodComponent, defaultFood.consumable(), itemStack);
    }

    public static boolean isRotten(Consumable consumableComponent) {
        for (ConsumeEffect effect : consumableComponent.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply)) continue;
            for (MobEffectInstance statusEffect : apply.effects()) {
                if (statusEffect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
                return true;
            }
        }
        return false;
    }

    /** Equivalent of the original's server-synced naturalRegeneration flag (the natural_health_regeneration game rule). */
    public static boolean isNaturalRegenerationEnabled() {
        try {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                ServerLevel level = server.overworld();
                if (level != null) {
                    return level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return true;
    }

    public static float getEstimatedHealthIncrement(Player player, ConsumableFood consumableFood) {
        if (!player.isHurt()) {
            return 0.0f;
        }
        FoodData stats = player.getFoodData();
        int foodLevel = Math.min(stats.getFoodLevel() + consumableFood.food().nutrition(), 20);
        float healthIncrement = 0.0f;
        if ((float) foodLevel >= 18.0f && isNaturalRegenerationEnabled()) {
            float saturationLevel = Math.min(stats.getSaturationLevel() + consumableFood.food().saturation(), (float) foodLevel);
            float exhaustionLevel = ExhaustionHelper.getExhaustion(player);
            healthIncrement = getEstimatedHealthIncrement(foodLevel, saturationLevel, exhaustionLevel);
        }
        block0:
        for (ConsumeEffect effect : consumableFood.consumable().onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply)) continue;
            for (MobEffectInstance statusEffect : apply.effects()) {
                if (statusEffect.getEffect() != MobEffects.REGENERATION) continue;
                int amplifier = statusEffect.getAmplifier();
                int duration = statusEffect.getDuration();
                healthIncrement += (float) Math.floor(duration / Math.max(50 >> amplifier, 1));
                continue block0;
            }
        }
        return healthIncrement;
    }

    public static float getEstimatedHealthIncrement(int foodLevel, float saturationLevel, float exhaustionLevel) {
        float health = 0.0f;
        if (!Float.isFinite(exhaustionLevel) || !Float.isFinite(saturationLevel)) {
            return 0.0f;
        }
        while (foodLevel >= 18) {
            while (exhaustionLevel > MAX_EXHAUSTION) {
                exhaustionLevel -= MAX_EXHAUSTION;
                if (saturationLevel > 0.0f) {
                    saturationLevel = Math.max(saturationLevel - 1.0f, 0.0f);
                    continue;
                }
                --foodLevel;
            }
            if (foodLevel >= 20 && Float.compare(saturationLevel, Float.MIN_NORMAL) > 0) {
                float limitedSaturationLevel = Math.min(saturationLevel, REGEN_EXHAUSTION_INCREMENT);
                float exhaustionUntilAboveMax = Math.nextUp(MAX_EXHAUSTION) - exhaustionLevel;
                int numIterationsUntilAboveMax = Math.max(1, (int) Math.ceil(exhaustionUntilAboveMax / limitedSaturationLevel));
                health += limitedSaturationLevel / REGEN_EXHAUSTION_INCREMENT * (float) numIterationsUntilAboveMax;
                exhaustionLevel += limitedSaturationLevel * (float) numIterationsUntilAboveMax;
                continue;
            }
            if (foodLevel < 18) continue;
            health += 1.0f;
            exhaustionLevel += REGEN_EXHAUSTION_INCREMENT;
        }
        return health;
    }

    public static class QueriedFoodResult {
        public FoodProperties defaultFoodComponent;
        public FoodProperties modifiedFoodComponent;
        public Consumable consumableComponent;
        public final ItemStack itemStack;

        public QueriedFoodResult(FoodProperties defaultFoodComponent, FoodProperties modifiedFoodComponent, Consumable consumableComponent, ItemStack itemStack) {
            this.defaultFoodComponent = defaultFoodComponent;
            this.modifiedFoodComponent = modifiedFoodComponent;
            this.consumableComponent = consumableComponent;
            this.itemStack = itemStack;
        }
    }
}
