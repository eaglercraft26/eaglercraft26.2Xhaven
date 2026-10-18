package squeek.appleskin.api.event;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import squeek.appleskin.api.handler.EventHandler;

public class HUDOverlayEvent {
    public int x;
    public int y;
    public GuiGraphicsExtractor context;
    public boolean isCanceled = false;

    private HUDOverlayEvent(int x, int y, GuiGraphicsExtractor context) {
        this.x = x;
        this.y = y;
        this.context = context;
    }

    public static class HealthRestored extends HUDOverlayEvent {
        public final FoodProperties foodComponent;
        public final ItemStack itemStack;
        public final float modifiedHealth;
        public static EventHandler.Event<EventHandler<HealthRestored>> EVENT = EventHandler.createArrayBacked();

        public HealthRestored(float modifiedHealth, ItemStack itemStack, FoodProperties foodComponent, int x, int y, GuiGraphicsExtractor context) {
            super(x, y, context);
            this.modifiedHealth = modifiedHealth;
            this.itemStack = itemStack;
            this.foodComponent = foodComponent;
        }
    }

    public static class HungerRestored extends HUDOverlayEvent {
        public final FoodProperties foodComponent;
        public final ItemStack itemStack;
        public final int currentFoodLevel;
        public static EventHandler.Event<EventHandler<HungerRestored>> EVENT = EventHandler.createArrayBacked();

        public HungerRestored(int foodLevel, ItemStack itemStack, FoodProperties foodComponent, int x, int y, GuiGraphicsExtractor context) {
            super(x, y, context);
            this.currentFoodLevel = foodLevel;
            this.itemStack = itemStack;
            this.foodComponent = foodComponent;
        }
    }

    public static class Saturation extends HUDOverlayEvent {
        public final float saturationLevel;
        public static EventHandler.Event<EventHandler<Saturation>> EVENT = EventHandler.createArrayBacked();

        public Saturation(float saturationLevel, int x, int y, GuiGraphicsExtractor context) {
            super(x, y, context);
            this.saturationLevel = saturationLevel;
        }
    }

    public static class Exhaustion extends HUDOverlayEvent {
        public final float exhaustion;
        public static EventHandler.Event<EventHandler<Exhaustion>> EVENT = EventHandler.createArrayBacked();

        public Exhaustion(float exhaustion, int x, int y, GuiGraphicsExtractor context) {
            super(x, y, context);
            this.exhaustion = exhaustion;
        }
    }
}
