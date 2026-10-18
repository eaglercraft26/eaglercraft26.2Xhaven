package squeek.appleskin.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import squeek.appleskin.api.handler.EventHandler;

public class FoodValuesEvent {
    public FoodProperties defaultFoodComponent;
    public FoodProperties modifiedFoodComponent;
    public final ItemStack itemStack;
    public final Player player;
    public static EventHandler.Event<EventHandler<FoodValuesEvent>> EVENT = EventHandler.createArrayBacked();

    public FoodValuesEvent(Player player, ItemStack itemStack, FoodProperties defaultFoodValues, FoodProperties modifiedFoodComponent) {
        this.player = player;
        this.itemStack = itemStack;
        this.defaultFoodComponent = defaultFoodValues;
        this.modifiedFoodComponent = modifiedFoodComponent;
    }
}
