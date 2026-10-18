package com.misterpemodder.shulkerboxtooltip;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record PreviewData(
   List<ItemStack> items,
   int inventorySize,
   int activeSlotCount,
   int selectedSlot,
   int maxRowSize,
   int compactMaxRowSize,
   int windowColor,
   Identifier texture,
   boolean bundle
) {
   public PreviewData {
      items = Objects.requireNonNull(items, "items").stream().map(item -> Objects.requireNonNull(item, "item").copy()).toList();
      texture = Objects.requireNonNull(texture, "texture");
      inventorySize = Math.max(items.size(), inventorySize);
      activeSlotCount = Math.max(0, Math.min(inventorySize, activeSlotCount));
      selectedSlot = Math.max(-1, Math.min(inventorySize - 1, selectedSlot));
      maxRowSize = Math.max(1, Math.min(9, maxRowSize));
      compactMaxRowSize = Math.max(1, Math.min(9, compactMaxRowSize));
   }
}
