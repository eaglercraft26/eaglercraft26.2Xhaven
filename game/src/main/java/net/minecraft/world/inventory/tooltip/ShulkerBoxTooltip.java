package net.minecraft.world.inventory.tooltip;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record ShulkerBoxTooltip(
   List<ItemStack> items,
   int activeSlots,
   int selectedSlot,
   boolean fullPreview,
   boolean bundle,
   int color,
   Identifier texture,
   int theme,
   int bundleTheme,
   int position,
   int maxRowSize,
   int compactMaxRowSize,
   boolean shortCounts,
   int compactOrder,
   int mergeStrategy
) implements TooltipComponent {
   public ShulkerBoxTooltip {
      items = List.copyOf(items);
      activeSlots = Math.max(0, Math.min(items.size(), activeSlots));
   }
}
