package com.misterpemodder.shulkerboxtooltip;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public interface PreviewProvider {
   boolean supports(ItemStack stack);

   PreviewData createPreview(ItemStack stack);

   default List<Component> tooltipLines(final ItemStack stack) {
      return List.of();
   }

   default boolean supportsLootTables() {
      return false;
   }

   default int priority() {
      return 0;
   }
}
