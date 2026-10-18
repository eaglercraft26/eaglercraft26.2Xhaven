package squeek.appleskin.client;

import java.util.Random;
import java.util.Vector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import squeek.appleskin.ModConfig;
import squeek.appleskin.api.event.HUDOverlayEvent;
import squeek.appleskin.helpers.ColorHelper;
import squeek.appleskin.helpers.ConsumableFood;
import squeek.appleskin.helpers.ExhaustionHelper;
import squeek.appleskin.helpers.FoodHelper;
import squeek.appleskin.helpers.TextureHelper;
import squeek.appleskin.util.IntPoint;

public class HUDOverlayHandler {
    public static HUDOverlayHandler INSTANCE;
    private float unclampedFlashAlpha = 0.0f;
    private float flashAlpha = 0.0f;
    private byte alphaDir = 1;
    private boolean needDisableBlend = false;
    public final OffsetsCache barOffsets = new OffsetsCache();
    public final HeldFoodCache heldFood = new HeldFoodCache();

    public static void init() {
        INSTANCE = new HUDOverlayHandler();
    }

    // Static entry points called from Hud (replace the original's HudMixin injections).
    public static void preRenderFood(GuiGraphicsExtractor context, Player player, int top, int right) {
        if (INSTANCE != null) INSTANCE.onPreRenderFood(context, player, top, right);
    }

    public static void postRenderFood(GuiGraphicsExtractor context, Player player, int top, int right) {
        if (INSTANCE != null) INSTANCE.onRenderFood(context, player, top, right);
    }

    public static void postRenderHealth(GuiGraphicsExtractor context, Player player, int left, int top, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking) {
        if (INSTANCE != null) INSTANCE.onRenderHealth(context, player, left, top, lines, regeneratingHeartIndex, maxHealth, lastHealth, health, absorption, blinking);
    }

    public static void onClientTick() {
        if (INSTANCE != null && ModConfig.INSTANCE != null) INSTANCE.tickFlash();
    }

    public void onPreRenderFood(GuiGraphicsExtractor context, Player player, int top, int right) {
        if (ModConfig.INSTANCE == null) {
            return;
        }
        if (!ModConfig.INSTANCE.showFoodExhaustionHudUnderlay) {
            return;
        }
        assert (player != null);
        float exhaustion = ExhaustionHelper.getExhaustion(player);
        HUDOverlayEvent.Exhaustion renderEvent = new HUDOverlayEvent.Exhaustion(exhaustion, right, top, context);
        HUDOverlayEvent.Exhaustion.EVENT.fire(renderEvent);
        if (!renderEvent.isCanceled) {
            this.drawExhaustionOverlay(renderEvent, 1.0f);
        }
    }

    public void onRenderFood(GuiGraphicsExtractor context, Player player, int top, int right) {
        FoodHelper.QueriedFoodResult result;
        if (ModConfig.INSTANCE == null) {
            return;
        }
        if (!this.shouldRenderAnyOverlays()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        assert (player != null);
        FoodData stats = player.getFoodData();
        HUDOverlayEvent.Saturation saturationRenderEvent = new HUDOverlayEvent.Saturation(stats.getSaturationLevel(), right, top, context);
        if (!ModConfig.INSTANCE.showSaturationHudOverlay) {
            saturationRenderEvent.isCanceled = true;
        }
        if (!saturationRenderEvent.isCanceled) {
            HUDOverlayEvent.Saturation.EVENT.fire(saturationRenderEvent);
        }
        if (!saturationRenderEvent.isCanceled) {
            this.drawSaturationOverlay(saturationRenderEvent, mc, 0.0f, 1.0f, mc.gui.hud.getGuiTicks());
        }
        if ((result = this.heldFood.result(mc.gui.hud.getGuiTicks(), player)) == null) {
            this.resetFlash();
            return;
        }
        if (ModConfig.INSTANCE.showFoodValuesHudOverlay) {
            HUDOverlayEvent.HungerRestored hungerRenderEvent = new HUDOverlayEvent.HungerRestored(stats.getFoodLevel(), result.itemStack, result.modifiedFoodComponent, right, top, context);
            HUDOverlayEvent.HungerRestored.EVENT.fire(hungerRenderEvent);
            if (hungerRenderEvent.isCanceled) {
                return;
            }
            int foodHunger = result.modifiedFoodComponent.nutrition();
            float foodSaturationIncrement = result.modifiedFoodComponent.saturation();
            this.drawHungerOverlay(hungerRenderEvent, mc, foodHunger, this.flashAlpha, FoodHelper.isRotten(result.consumableComponent), mc.gui.hud.getGuiTicks());
            int newFoodValue = stats.getFoodLevel() + foodHunger;
            float newSaturationValue = stats.getSaturationLevel() + foodSaturationIncrement;
            if (!saturationRenderEvent.isCanceled) {
                float saturationGained = newSaturationValue > (float)newFoodValue ? (float)newFoodValue - stats.getSaturationLevel() : foodSaturationIncrement;
                this.drawSaturationOverlay(saturationRenderEvent, mc, saturationGained, this.flashAlpha, mc.gui.hud.getGuiTicks());
            }
        }
    }

    public void onRenderHealth(GuiGraphicsExtractor context, Player player, int left, int top, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking) {
        if (ModConfig.INSTANCE == null) {
            return;
        }
        if (!this.shouldRenderAnyOverlays()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        assert (player != null);
        FoodHelper.QueriedFoodResult result = this.heldFood.result(mc.gui.hud.getGuiTicks(), player);
        if (result == null) {
            this.resetFlash();
            return;
        }
        if (this.shouldShowEstimatedHealth(player, mc.gui.hud.getGuiTicks())) {
            float foodHealthIncrement = FoodHelper.getEstimatedHealthIncrement(player, new ConsumableFood(result.modifiedFoodComponent, result.consumableComponent));
            float currentHealth = player.getHealth();
            float modifiedHealth = Math.min(currentHealth + foodHealthIncrement, player.getMaxHealth());
            HUDOverlayEvent.HealthRestored healthRenderEvent = null;
            if (currentHealth < modifiedHealth) {
                healthRenderEvent = new HUDOverlayEvent.HealthRestored(modifiedHealth, result.itemStack, result.modifiedFoodComponent, left, top, context);
            }
            if (healthRenderEvent != null) {
                HUDOverlayEvent.HealthRestored.EVENT.fire(healthRenderEvent);
            }
            if (healthRenderEvent != null && !healthRenderEvent.isCanceled) {
                this.drawHealthOverlay(healthRenderEvent, mc, this.flashAlpha, mc.gui.hud.getGuiTicks());
            }
        }
    }

    public void drawSaturationOverlay(GuiGraphicsExtractor context, float saturationGained, float saturationLevel, Minecraft mc, int right, int top, float alpha, int guiTicks) {
        if (saturationLevel + saturationGained < 0.0f) {
            return;
        }
        int alphaColor = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, alpha);
        float modifiedSaturation = Math.max(0.0f, Math.min(saturationLevel + saturationGained, 20.0f));
        int startSaturationBar = 0;
        int endSaturationBar = (int)Math.ceil(modifiedSaturation / 2.0f);
        if (saturationGained != 0.0f) {
            startSaturationBar = (int)Math.max(saturationLevel / 2.0f, 0.0f);
        }
        int iconSize = 9;
        Vector<IntPoint> foodBarOffsets = this.barOffsets.foodBarOffsets(guiTicks, mc.player);
        for (int i = startSaturationBar; i < endSaturationBar; ++i) {
            IntPoint offset;
            IntPoint intPoint = offset = i < foodBarOffsets.size() ? foodBarOffsets.get(i) : new IntPoint();
            if (offset == null) continue;
            int x = right + offset.x;
            int y = top + offset.y;
            int v = 0;
            int u = 0;
            float effectiveSaturationOfBar = modifiedSaturation / 2.0f - (float)i;
            if (effectiveSaturationOfBar >= 1.0f) {
                u = 3 * iconSize;
            } else if ((double)effectiveSaturationOfBar > 0.5) {
                u = 2 * iconSize;
            } else if ((double)effectiveSaturationOfBar > 0.25) {
                u = 1 * iconSize;
            }
            context.blit(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, x, y, (float)u, (float)v, iconSize, iconSize, 256, 256, alphaColor);
        }
    }

    public void drawHungerOverlay(GuiGraphicsExtractor context, int hungerRestored, int foodLevel, Minecraft mc, int right, int top, float alpha, boolean useRottenTextures, int guiTicks) {
        if (hungerRestored <= 0) {
            return;
        }
        int alphaColor = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, alpha);
        int modifiedFood = Math.max(0, Math.min(20, foodLevel + hungerRestored));
        int startFoodBars = Math.max(0, foodLevel / 2);
        int endFoodBars = (int)Math.ceil((float)modifiedFood / 2.0f);
        int iconSize = 9;
        Vector<IntPoint> foodBarOffsets = this.barOffsets.foodBarOffsets(guiTicks, mc.player);
        for (int i = startFoodBars; i < endFoodBars; ++i) {
            IntPoint offset;
            IntPoint intPoint = offset = i < foodBarOffsets.size() ? foodBarOffsets.get(i) : new IntPoint();
            if (offset == null) continue;
            int x = right + offset.x;
            int y = top + offset.y;
            Identifier backgroundSprite = TextureHelper.getFoodTexture(useRottenTextures, TextureHelper.FoodType.EMPTY);
            int bgColor = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, alpha * 0.25f);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, backgroundSprite, x, y, iconSize, iconSize, bgColor);
            boolean isHalf = i * 2 + 1 == modifiedFood;
            Identifier iconSprite = TextureHelper.getFoodTexture(useRottenTextures, isHalf ? TextureHelper.FoodType.HALF : TextureHelper.FoodType.FULL);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, x, y, iconSize, iconSize, alphaColor);
        }
    }

    public void drawHealthOverlay(GuiGraphicsExtractor context, float health, float modifiedHealth, Minecraft mc, int right, int top, float alpha, int guiTicks) {
        if (modifiedHealth <= health) {
            return;
        }
        int alphaColor = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, alpha);
        int fixedModifiedHealth = (int)Math.ceil(modifiedHealth);
        boolean isHardcore = mc.player.level() != null && mc.player.level().getLevelData().isHardcore();
        int startHealthBars = (int)Math.max(0.0, Math.ceil(health) / 2.0);
        int endHealthBars = (int)Math.max(0.0, Math.ceil(modifiedHealth / 2.0f));
        int iconSize = 9;
        Vector<IntPoint> healthBarOffsets = this.barOffsets.healthBarOffsets(guiTicks, mc.player);
        for (int i = startHealthBars; i < endHealthBars; ++i) {
            IntPoint offset;
            IntPoint intPoint = offset = i < healthBarOffsets.size() ? healthBarOffsets.get(i) : new IntPoint();
            if (offset == null) continue;
            int x = right + offset.x;
            int y = top + offset.y;
            Identifier backgroundSprite = TextureHelper.getHeartTexture(isHardcore, TextureHelper.HeartType.CONTAINER);
            int bgColor = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, alpha * 0.25f);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, backgroundSprite, x, y, iconSize, iconSize, bgColor);
            boolean isHalf = i * 2 + 1 == fixedModifiedHealth;
            Identifier iconSprite = TextureHelper.getHeartTexture(isHardcore, isHalf ? TextureHelper.HeartType.HALF : TextureHelper.HeartType.FULL);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, x, y, iconSize, iconSize, alphaColor);
        }
    }

    public void drawExhaustionOverlay(GuiGraphicsExtractor context, float exhaustion, int right, int top, float alpha) {
        float maxExhaustion = FoodHelper.MAX_EXHAUSTION;
        float ratio = Math.min(1.0f, Math.max(0.0f, exhaustion / maxExhaustion));
        int width = (int)(ratio * 81.0f);
        int height = 9;
        int color = ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, 0.75f);
        context.blit(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, right - width, top, (float)(81 - width), 18.0f, width, height, 256, 256, color);
    }

    private void drawSaturationOverlay(HUDOverlayEvent.Saturation event, Minecraft mc, float saturationGained, float alpha, int guiTicks) {
        this.drawSaturationOverlay(event.context, saturationGained, event.saturationLevel, mc, event.x, event.y, alpha, guiTicks);
    }

    private void drawHungerOverlay(HUDOverlayEvent.HungerRestored event, Minecraft mc, int hunger, float alpha, boolean useRottenTextures, int guiTicks) {
        this.drawHungerOverlay(event.context, hunger, event.currentFoodLevel, mc, event.x, event.y, alpha, useRottenTextures, guiTicks);
    }

    private void drawHealthOverlay(HUDOverlayEvent.HealthRestored event, Minecraft mc, float alpha, int guiTicks) {
        this.drawHealthOverlay(event.context, mc.player.getHealth(), event.modifiedHealth, mc, event.x, event.y, alpha, guiTicks);
    }

    private void drawExhaustionOverlay(HUDOverlayEvent.Exhaustion event, float alpha) {
        this.drawExhaustionOverlay(event.context, event.exhaustion, event.x, event.y, alpha);
    }

    private boolean shouldRenderAnyOverlays() {
        return ModConfig.INSTANCE.showFoodValuesHudOverlay || ModConfig.INSTANCE.showSaturationHudOverlay || ModConfig.INSTANCE.showFoodHealthHudOverlay;
    }

    private void tickFlash() {
        this.unclampedFlashAlpha += (float)this.alphaDir * 0.125f;
        if (this.unclampedFlashAlpha >= 1.5f) {
            this.alphaDir = (byte)-1;
        } else if (this.unclampedFlashAlpha <= -0.5f) {
            this.alphaDir = 1;
        }
        this.flashAlpha = Math.max(0.0f, Math.min(1.0f, this.unclampedFlashAlpha)) * Math.max(0.0f, Math.min(1.0f, ModConfig.INSTANCE.maxHudOverlayFlashAlpha));
    }

    public void resetFlash() {
        this.flashAlpha = 0.0f;
        this.unclampedFlashAlpha = 0.0f;
        this.alphaDir = 1;
    }

    private boolean shouldShowEstimatedHealth(Player player, int guiTicks) {
        if (!ModConfig.INSTANCE.showFoodHealthHudOverlay) {
            return false;
        }
        if (this.barOffsets.healthBarOffsets(guiTicks, player).isEmpty()) {
            return false;
        }
        FoodData stats = player.getFoodData();
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (stats.getFoodLevel() >= 18) {
            return false;
        }
        if (player.hasEffect(MobEffects.POISON)) {
            return false;
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            return false;
        }
        return !player.hasEffect(MobEffects.REGENERATION);
    }

    public static class OffsetsCache {
        protected final Vector<IntPoint> foodBarOffsets = new Vector();
        protected final Vector<IntPoint> healthBarOffsets = new Vector();
        public int lastGuiTick = 0;
        protected final Random random = new Random();

        private OffsetsCache() {
        }

        protected void generate(int guiTicks, Player player) {
            int i;
            float absorptionHealth;
            int preferHealthBars = 10;
            int preferFoodBars = 10;
            float maxHealth = player.getMaxHealth();
            int healthBars = (int)Math.ceil((maxHealth + (absorptionHealth = (float)Math.ceil(player.getAbsorptionAmount()))) / 2.0f);
            if (healthBars < 0 || healthBars > 1000) {
                healthBars = 0;
            }
            int healthRows = (int)Math.ceil((float)healthBars / 10.0f);
            int healthRowHeight = Math.max(10 - (healthRows - 2), 3);
            boolean shouldAnimatedHealth = false;
            boolean shouldAnimatedFood = false;
            if (ModConfig.INSTANCE.showVanillaAnimationsOverlay) {
                FoodData hungerManager = player.getFoodData();
                float saturationLevel = hungerManager.getSaturationLevel();
                int foodLevel = hungerManager.getFoodLevel();
                shouldAnimatedFood = saturationLevel <= 0.0f && guiTicks % (foodLevel * 3 + 1) == 0;
                shouldAnimatedHealth = Math.ceil(player.getHealth()) <= 4.0;
            }
            this.random.setSeed(guiTicks * 312871);
            if (this.healthBarOffsets.size() != healthBars) {
                this.healthBarOffsets.setSize(healthBars);
            }
            if (this.foodBarOffsets.size() != 10) {
                this.foodBarOffsets.setSize(10);
            }
            for (i = healthBars - 1; i >= 0; --i) {
                IntPoint point;
                int row = (int)Math.ceil((float)(i + 1) / 10.0f) - 1;
                int x = i % 10 * 8;
                int y = -(row * healthRowHeight);
                if (shouldAnimatedHealth) {
                    y += this.random.nextInt(2);
                }
                if ((point = this.healthBarOffsets.get(i)) == null) {
                    point = new IntPoint();
                    this.healthBarOffsets.set(i, point);
                }
                point.x = x;
                point.y = y;
            }
            for (i = 0; i < 10; ++i) {
                IntPoint point;
                int x = -(i * 8) - 9;
                int y = 0;
                if (shouldAnimatedFood) {
                    y += this.random.nextInt(3) - 1;
                }
                if ((point = this.foodBarOffsets.get(i)) == null) {
                    point = new IntPoint();
                    this.foodBarOffsets.set(i, point);
                }
                point.x = x;
                point.y = y;
            }
        }

        public Vector<IntPoint> healthBarOffsets(int guiTick, Player player) {
            if (guiTick != this.lastGuiTick) {
                this.generate(guiTick, player);
                this.lastGuiTick = guiTick;
            }
            return this.healthBarOffsets;
        }

        public Vector<IntPoint> foodBarOffsets(int guiTicks, Player player) {
            if (guiTicks != this.lastGuiTick) {
                this.generate(guiTicks, player);
                this.lastGuiTick = guiTicks;
            }
            return this.foodBarOffsets;
        }
    }

    public static class HeldFoodCache {
        protected FoodHelper.@Nullable QueriedFoodResult result;
        public int lastGuiTick = 0;

        protected void query(Player player) {
            boolean shouldRenderHeldItemValues;
            boolean canConsume;
            ItemStack heldItem = player.getMainHandItem();
            FoodHelper.QueriedFoodResult heldFood = FoodHelper.query(heldItem, player);
            boolean bl = canConsume = heldFood != null && FoodHelper.canConsume(player, heldFood.modifiedFoodComponent);
            if (ModConfig.INSTANCE.showFoodValuesHudOverlayWhenOffhand && !canConsume) {
                heldItem = player.getOffhandItem();
                heldFood = FoodHelper.query(heldItem, player);
                canConsume = heldFood != null && FoodHelper.canConsume(player, heldFood.modifiedFoodComponent);
            }
            boolean bl2 = shouldRenderHeldItemValues = !heldItem.isEmpty() && canConsume;
            if (!shouldRenderHeldItemValues) {
                this.result = null;
                return;
            }
            this.result = heldFood;
        }

        public FoodHelper.QueriedFoodResult result(int guiTick, Player player) {
            if (guiTick != this.lastGuiTick) {
                this.query(player);
                this.lastGuiTick = guiTick;
            }
            return this.result;
        }
    }
}

