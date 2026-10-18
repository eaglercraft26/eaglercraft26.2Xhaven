package squeek.appleskin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import org.joml.Matrix3x2fStack;
import squeek.appleskin.ModConfig;
import squeek.appleskin.api.event.TooltipOverlayEvent;
import squeek.appleskin.helpers.ColorHelper;
import squeek.appleskin.helpers.FoodHelper;
import squeek.appleskin.helpers.KeyHelper;
import squeek.appleskin.helpers.TextureHelper;

public class TooltipOverlayHandler {
    public static TooltipOverlayHandler INSTANCE;

    public static void init() {
        INSTANCE = new TooltipOverlayHandler();
    }

    /** Called from ItemStack#getTooltipLines (replaces the original's ItemStackMixin). */
    public static void onItemTooltip(ItemStack hoveredStack, Player player, Item.TooltipContext context, TooltipFlag type, List<Component> tooltip) {
        if (INSTANCE != null) INSTANCE.handleItemTooltip(hoveredStack, player, context, type, tooltip);
    }

    private void handleItemTooltip(ItemStack hoveredStack, Player player, Item.TooltipContext context, TooltipFlag type, List<Component> tooltip) {
        if (hoveredStack == null || tooltip == null || ModConfig.INSTANCE == null) {
            return;
        }
        if (!this.shouldShowTooltip(hoveredStack, type)) {
            return;
        }
        FoodHelper.QueriedFoodResult queriedFoodResult = FoodHelper.query(hoveredStack, player);
        if (queriedFoodResult == null) {
            return;
        }
        FoodProperties defaultFood = queriedFoodResult.defaultFoodComponent;
        FoodProperties modifiedFood = queriedFoodResult.modifiedFoodComponent;
        TooltipOverlayEvent.Pre prerenderEvent = new TooltipOverlayEvent.Pre(hoveredStack, defaultFood, modifiedFood);
        TooltipOverlayEvent.Pre.EVENT.fire(prerenderEvent);
        if (prerenderEvent.isCanceled) {
            return;
        }
        FoodOverlay foodOverlay = new FoodOverlay(prerenderEvent.itemStack, defaultFood, modifiedFood, queriedFoodResult.consumableComponent, player);
        if (foodOverlay.shouldRenderHungerBars()) {
            try {
                tooltip.add(new FoodOverlayTextComponent(foodOverlay));
            }
            catch (UnsupportedOperationException unsupportedOperationException) {
                // empty catch block
            }
        }
    }

    public void onRenderTooltip(GuiGraphicsExtractor context, FoodOverlay foodOverlay, int toolTipX, int toolTipY, Font textRenderer) {
        if (context == null || ModConfig.INSTANCE == null) {
            return;
        }
        if (foodOverlay == null) {
            return;
        }
        ItemStack itemStack = foodOverlay.itemStack;
        FoodProperties defaultFood = foodOverlay.defaultFood;
        FoodProperties modifiedFood = foodOverlay.modifiedFood;
        int x = toolTipX;
        int y = toolTipY;
        TooltipOverlayEvent.Render renderEvent = new TooltipOverlayEvent.Render(itemStack, x, y, context, defaultFood, modifiedFood);
        TooltipOverlayEvent.Render.EVENT.fire(renderEvent);
        if (renderEvent.isCanceled) {
            return;
        }
        x = renderEvent.x;
        y = renderEvent.y;
        context = renderEvent.context;
        itemStack = renderEvent.itemStack;
        Matrix3x2fStack matrixStack = context.pose();
        int defaultFoodHunger = defaultFood.nutrition();
        int modifiedFoodHunger = modifiedFood.nutrition();
        x += (foodOverlay.hungerBars - 1) * 9;
        boolean isRotten = FoodHelper.isRotten(foodOverlay.consumableComponent);
        for (int i = 0; i < foodOverlay.hungerBars * 2; i += 2) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, TextureHelper.FOOD_EMPTY_TEXTURE, x, y, 9, 9);
            FoodOutline outline = FoodOutline.get(modifiedFoodHunger, defaultFoodHunger, i);
            if (outline != FoodOutline.NORMAL) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, TextureHelper.HUNGER_OUTLINE_SPRITE, x, y, 9, 9, outline.argb());
            }
            boolean isDefaultHalf = defaultFoodHunger - 1 == i;
            Identifier defaultFoodIcon = TextureHelper.getFoodTexture(isRotten, isDefaultHalf ? TextureHelper.FoodType.HALF : TextureHelper.FoodType.FULL);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, defaultFoodIcon, x, y, 9, 9, ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, 0.25f));
            if (modifiedFoodHunger > i) {
                boolean isModifiedHalf = modifiedFoodHunger - 1 == i;
                Identifier modifiedFoodIcon = TextureHelper.getFoodTexture(isRotten, isModifiedHalf ? TextureHelper.FoodType.HALF : TextureHelper.FoodType.FULL);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, modifiedFoodIcon, x, y, 9, 9);
            }
            x -= 9;
        }
        if (foodOverlay.hungerBarsText != null) {
            matrixStack.pushMatrix();
            matrixStack.translate((float)(x += 18), (float)y);
            matrixStack.scale(0.75f, 0.75f);
            context.text(textRenderer, foodOverlay.hungerBarsText, 2, 2, -5592406);
            matrixStack.popMatrix();
        }
        x = toolTipX;
        y += 10;
        float modifiedSaturationIncrement = modifiedFood.saturation();
        float absModifiedSaturationIncrement = Math.abs(modifiedSaturationIncrement);
        x += (foodOverlay.saturationBars - 1) * 7;
        for (int i = 0; i < foodOverlay.saturationBars * 2; i += 2) {
            int color;
            float effectiveSaturationOfBar = (absModifiedSaturationIncrement - (float)i) / 2.0f;
            boolean shouldBeFaded = absModifiedSaturationIncrement <= (float)i;
            int n = color = shouldBeFaded ? ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, 0.5f) : ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, 1.0f);
            context.blit(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, x, y, effectiveSaturationOfBar >= 1.0f ? 21.0f : ((double)effectiveSaturationOfBar > 0.5 ? 14.0f : ((double)effectiveSaturationOfBar > 0.25 ? 7.0f : (effectiveSaturationOfBar > 0.0f ? 0.0f : 28.0f))), modifiedSaturationIncrement >= 0.0f ? 27.0f : 34.0f, 7, 7, 256, 256, color);
            x -= 7;
        }
        if (foodOverlay.saturationBarsText != null) {
            matrixStack.pushMatrix();
            matrixStack.translate((float)(x += 14), (float)y);
            matrixStack.scale(0.75f, 0.75f);
            context.text(textRenderer, foodOverlay.saturationBarsText, 2, 1, -5592406);
            matrixStack.popMatrix();
        }
    }

    private boolean shouldShowTooltip(ItemStack hoveredStack, TooltipFlag type) {
        boolean shouldShowTooltip;
        if (hoveredStack.isEmpty()) {
            return false;
        }
        if (!type.isCreative() && hoveredStack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()) {
            return false;
        }
        boolean bl = shouldShowTooltip = ModConfig.INSTANCE.showFoodValuesInTooltip && KeyHelper.isShiftKeyDown() || ModConfig.INSTANCE.showFoodValuesInTooltipAlways;
        if (!shouldShowTooltip) {
            return false;
        }
        return FoodHelper.isFood(hoveredStack);
    }

    public static class FoodOverlay
    implements ClientTooltipComponent,
    TooltipComponent {
        private FoodProperties defaultFood;
        private FoodProperties modifiedFood;
        private Consumable consumableComponent;
        private int biggestHunger;
        private float biggestSaturationIncrement;
        private int hungerBars;
        private String hungerBarsText;
        private int saturationBars;
        private String saturationBarsText;
        private ItemStack itemStack;

        FoodOverlay(ItemStack itemStack, FoodProperties defaultFood, FoodProperties modifiedFood, Consumable consumableComponent, Player player) {
            this.itemStack = itemStack;
            this.defaultFood = defaultFood;
            this.modifiedFood = modifiedFood;
            this.consumableComponent = consumableComponent;
            this.biggestHunger = Math.max(defaultFood.nutrition(), modifiedFood.nutrition());
            this.biggestSaturationIncrement = Math.max(defaultFood.saturation(), modifiedFood.saturation());
            this.hungerBars = (int)Math.ceil((float)Math.abs(this.biggestHunger) / 2.0f);
            if (this.hungerBars > 10) {
                this.hungerBarsText = "x" + (this.biggestHunger < 0 ? -1 : 1) * this.hungerBars;
                this.hungerBars = 1;
            }
            this.saturationBars = (int)Math.ceil(Math.abs(this.biggestSaturationIncrement) / 2.0f);
            if (this.saturationBars > 10 || this.saturationBars == 0) {
                this.saturationBarsText = "x" + (this.biggestSaturationIncrement < 0.0f ? -1 : 1) * this.saturationBars;
                this.saturationBars = 1;
            }
        }

        boolean shouldRenderHungerBars() {
            return this.hungerBars > 0;
        }

        public int getHeight(Font textRenderer) {
            return 20;
        }

        public int getWidth(Font textRenderer) {
            int hungerBarLength = this.hungerBars * 9;
            if (this.hungerBarsText != null) {
                hungerBarLength += textRenderer.width(this.hungerBarsText);
            }
            int saturationBarLength = this.saturationBars * 7;
            if (this.saturationBarsText != null) {
                saturationBarLength += textRenderer.width(this.saturationBarsText);
            }
            return Math.max(hungerBarLength, saturationBarLength);
        }

        public void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor context) {
            if (INSTANCE != null) {
                INSTANCE.onRenderTooltip(context, this, x, y, textRenderer);
            }
        }
    }

    public static class FoodOverlayTextComponent
    extends EmptyText
    implements FormattedCharSequence {
        public FoodOverlay foodOverlay;

        FoodOverlayTextComponent(FoodOverlay foodOverlay) {
            this.foodOverlay = foodOverlay;
        }

        public FormattedCharSequence getVisualOrderText() {
            return this;
        }

        public boolean accept(FormattedCharSink visitor) {
            return StringDecomposer.iterateFormatted((FormattedText)this, (Style)this.getStyle(), (FormattedCharSink)visitor);
        }
    }

    static enum FoodOutline {
        NEGATIVE,
        EXTRA,
        NORMAL,
        PARTIAL,
        MISSING;


        public int argb() {
            return switch (this.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> ColorHelper.argbFromRGBA(1.0f, 1.0f, 1.0f, 1.0f);
                case 1 -> ColorHelper.argbFromRGBA(0.06f, 0.32f, 0.02f, 1.0f);
                case 2 -> ColorHelper.argbFromRGBA(0.0f, 0.0f, 0.0f, 1.0f);
                case 3 -> ColorHelper.argbFromRGBA(0.53f, 0.21f, 0.08f, 1.0f);
                case 4 -> ColorHelper.argbFromRGBA(0.62f, 0.0f, 0.0f, 0.5f);
            };
        }

        public static FoodOutline get(int modifiedFoodHunger, int defaultFoodHunger, int i) {
            if (modifiedFoodHunger < 0) {
                return NEGATIVE;
            }
            if (modifiedFoodHunger > defaultFoodHunger && defaultFoodHunger <= i) {
                return EXTRA;
            }
            if (modifiedFoodHunger > i + 1 || defaultFoodHunger == modifiedFoodHunger) {
                return NORMAL;
            }
            if (modifiedFoodHunger == i + 1) {
                return PARTIAL;
            }
            return MISSING;
        }
    }

    static abstract class EmptyText
    implements Component {
        static List<Component> emptySiblings = new ArrayList<Component>();

        EmptyText() {
        }

        public Style getStyle() {
            return Style.EMPTY;
        }

        public ComponentContents getContents() {
            return PlainTextContents.EMPTY;
        }

        public List<Component> getSiblings() {
            return emptySiblings;
        }
    }
}

