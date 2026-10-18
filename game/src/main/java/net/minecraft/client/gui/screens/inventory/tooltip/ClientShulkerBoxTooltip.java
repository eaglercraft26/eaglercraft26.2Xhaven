package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class ClientShulkerBoxTooltip implements ClientTooltipComponent {
   private static final int COLUMNS = 9;
   private static final int ROWS = 3;
   private static final int SLOT = 18;
   private final List<ItemStack> items;

   public ClientShulkerBoxTooltip(final ItemContainerContents contents) {
      this.items = contents.allItemsCopyStream().toList();
   }

   @Override
   public int getHeight(final Font font) {
      return ROWS * SLOT + 2;
   }

   @Override
   public int getWidth(final Font font) {
      return COLUMNS * SLOT;
   }

   @Override
   public boolean showTooltipWithItemInHand() {
      return true;
   }

   @Override
   public void extractImage(final Font font, final int x, final int y, final int w, final int h, final GuiGraphicsExtractor graphics) {
      for (int row = 0; row < ROWS; row++) {
         for (int col = 0; col < COLUMNS; col++) {
            int sx = x + col * SLOT;
            int sy = y + row * SLOT;
            graphics.fill(sx, sy, sx + SLOT, sy + SLOT, 0x80000000);
            graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xFF8B8B8B);
            int idx = row * COLUMNS + col;
            if (idx < this.items.size()) {
               ItemStack stack = this.items.get(idx);
               if (!stack.isEmpty()) {
                  graphics.item(stack, sx + 1, sy + 1, idx);
                  graphics.itemDecorations(font, stack, sx + 1, sy + 1);
               }
            }
         }
      }
   }
}
