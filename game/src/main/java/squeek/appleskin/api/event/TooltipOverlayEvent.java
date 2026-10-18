package squeek.appleskin.api.event;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import squeek.appleskin.api.handler.EventHandler;

public class TooltipOverlayEvent {
    public final FoodProperties defaultFood;
    public final FoodProperties modifiedFood;
    public final ItemStack itemStack;
    public boolean isCanceled = false;

    private TooltipOverlayEvent(ItemStack itemStack, FoodProperties defaultFood, FoodProperties modifiedFood) {
        this.itemStack = itemStack;
        this.defaultFood = defaultFood;
        this.modifiedFood = modifiedFood;
    }

    public static class Render extends TooltipOverlayEvent {
        public int x;
        public int y;
        public GuiGraphicsExtractor context;
        public static EventHandler.Event<EventHandler<Render>> EVENT = EventHandler.createArrayBacked();

        public Render(ItemStack itemStack, int x, int y, GuiGraphicsExtractor context, FoodProperties defaultFood, FoodProperties modifiedFood) {
            super(itemStack, defaultFood, modifiedFood);
            this.context = context;
            this.x = x;
            this.y = y;
        }
    }

    public static class Pre extends TooltipOverlayEvent {
        public static EventHandler.Event<EventHandler<Pre>> EVENT = EventHandler.createArrayBacked();

        public Pre(ItemStack itemStack, FoodProperties defaultFood, FoodProperties modifiedFood) {
            super(itemStack, defaultFood, modifiedFood);
        }
    }
}
