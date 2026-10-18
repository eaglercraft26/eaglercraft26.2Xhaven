package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class ClientShulkerBoxTooltip implements ClientTooltipComponent {
   private static final int CUSTOM_SLOT = 18;
   private static final int CUSTOM_PAD = 8;
   private static final int BUNDLE_SLOT = 24;
   private static final Identifier BUNDLE_SLOT_BACKGROUND = Identifier.withDefaultNamespace("container/bundle/slot_background");
   private static final Identifier BUNDLE_SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");
   private static final Identifier BUNDLE_SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/bundle/slot_highlight_front");
   private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
   private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
   private final net.minecraft.world.inventory.tooltip.ShulkerBoxTooltip tooltip;
   private final List<ItemStack> compactItems;
   private final int compactSelectedSlot;
   private final int columns;
   private final int rows;

   public ClientShulkerBoxTooltip(final net.minecraft.world.inventory.tooltip.ShulkerBoxTooltip tooltip) {
      this.tooltip = tooltip;
      List<Merged> merged = mergeItems(tooltip.items(), tooltip.mergeStrategy(), tooltip.compactOrder());
      this.compactItems = merged.stream().map(Merged::stack).toList();
      this.compactSelectedSlot = findCompactSelectedSlot(merged, tooltip.selectedSlot());
      int configuredColumns = tooltip.fullPreview() ? tooltip.maxRowSize() : tooltip.compactMaxRowSize();
      this.columns = tooltip.bundle()
         ? Math.min(8, Math.max(1, configuredColumns == 9 ? 8 : configuredColumns))
         : Math.min(configuredColumns, this.slotCount());
      this.rows = Math.max(1, (int)Math.ceil(this.slotCount() / (double)this.columns));
   }

   @Override
   public int getHeight(final Font font) {
      return this.tooltip.position() == 0 ? this.previewHeight() + 4 : 0;
   }

   @Override
   public int getWidth(final Font font) {
      return this.tooltip.position() == 0 ? this.previewWidth() : 0;
   }

   @Override
   public boolean showTooltipWithItemInHand() {
      return true;
   }

   @Override
   public void extractImage(final Font font, final int x, final int y, final int w, final int h, final GuiGraphicsExtractor graphics) {
      if (this.tooltip.position() == 0) {
         this.drawPreview(x, y, graphics, font);
      } else {
         this.drawOutsidePreview(graphics, font);
      }
   }

   private void drawOutsidePreview(final GuiGraphicsExtractor graphics, final Font font) {
      int width = this.previewWidth();
      int height = this.previewHeight();
      int tooltipX = graphics.tooltipX();
      int tooltipY = graphics.tooltipY();
      int tooltipWidth = graphics.tooltipWidth();
      int tooltipHeight = graphics.tooltipHeight();
      int gap = 8;
      int x = tooltipX;
      int y = tooltipY;
      if (this.tooltip.position() == 1 || this.tooltip.position() == 2 || this.tooltip.position() == 3) {
         int position = this.tooltip.position();
         if (position == 1) {
            y = tooltipY + tooltipHeight + 4;
            if (y + height > graphics.guiHeight()) {
               y = tooltipY - height - 4;
            }
         } else if (position == 2) {
            y = tooltipY - height - 4;
         } else {
            y = tooltipY + tooltipHeight + 4;
         }
         if (y < 0 || y + height > graphics.guiHeight()) {
            boolean cursorLeftOfTooltip = graphics.mouseX() < tooltipX + tooltipWidth / 2;
            x = cursorLeftOfTooltip ? tooltipX + tooltipWidth + gap : tooltipX - width - gap;
            y = tooltipY + (tooltipHeight - height) / 2;
         }
      }
      x = Math.max(0, Math.min(x, graphics.guiWidth() - width));
      y = Math.max(0, Math.min(y, graphics.guiHeight() - height));
      this.drawPreview(x, y, graphics, font);
   }

   private void drawPreview(final int x, final int y, final GuiGraphicsExtractor graphics, final Font font) {
      if (this.tooltip.bundle() && this.tooltip.bundleTheme() == 2) {
         this.drawVanillaBundle(x, y, graphics, font);
         return;
      }
      if (this.tooltip.theme() == 1) {
         TooltipRenderUtil.extractTooltipBackground(graphics, x, y, this.previewWidth(), this.previewHeight(), null);
      } else {
         graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.tooltip.texture(),
            x,
            y,
            this.previewWidth(),
            this.previewHeight(),
            this.tint()
         );
      }
      int slotCount = this.slotCount();
      int hoveredSlot = this.hoveredSlot(x, y, false, graphics);
      int highlightedSlot = this.tooltip.fullPreview()
         ? this.tooltip.selectedSlot() >= 0 ? this.tooltip.selectedSlot() : hoveredSlot
         : this.compactSelectedSlot >= 0 ? this.compactSelectedSlot : hoveredSlot;
      ItemStack hoveredItem = ItemStack.EMPTY;
      for (int slot = 0; slot < slotCount; slot++) {
         int sx = x + CUSTOM_PAD + slot % this.columns * CUSTOM_SLOT;
         int sy = y + CUSTOM_PAD + slot / this.columns * CUSTOM_SLOT;
         int firstActiveSlot = this.tooltip.items().size() - this.tooltip.activeSlots();
         boolean selected = slot == highlightedSlot;
         boolean inactive = this.tooltip.fullPreview() && slot < firstActiveSlot;
         graphics.fill(sx, sy, sx + 16, sy + 16, inactive ? 0x70000000 : 0x50000000);
         if (selected) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, sx - 4, sy - 4, 24, 24);
         }
         ItemStack item = this.tooltip.fullPreview() ? this.tooltip.items().get(slot) : this.compactItems.get(slot);
         if (!item.isEmpty()) {
            String countLabel = this.tooltip.shortCounts() ? abbreviateCount(item.getCount()) : "";
            graphics.item(item, sx, sy, slot);
            graphics.itemDecorations(font, item, sx, sy, countLabel);
            if (slot == hoveredSlot) {
               hoveredItem = item;
            }
         }
         if (selected) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, sx - 4, sy - 4, 24, 24);
         }
      }
      this.drawItemTooltip(hoveredItem, graphics, font);
   }

   private void drawVanillaBundle(final int x, final int y, final GuiGraphicsExtractor graphics, final Font font) {
      int hoveredSlot = this.hoveredSlot(x, y, true, graphics);
      int highlightedSlot = this.tooltip.fullPreview()
         ? this.tooltip.selectedSlot() >= 0 ? this.tooltip.selectedSlot() : hoveredSlot
         : this.compactSelectedSlot >= 0 ? this.compactSelectedSlot : hoveredSlot;
      ItemStack hoveredItem = ItemStack.EMPTY;
      for (int slot = 0; slot < this.slotCount(); slot++) {
         int sx = x + slot % this.columns * BUNDLE_SLOT;
         int sy = y + slot / this.columns * BUNDLE_SLOT;
         boolean selected = slot == highlightedSlot;
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? BUNDLE_SLOT_HIGHLIGHT_BACK : BUNDLE_SLOT_BACKGROUND, sx, sy, BUNDLE_SLOT, BUNDLE_SLOT);
         ItemStack item = this.tooltip.fullPreview() ? this.tooltip.items().get(slot) : this.compactItems.get(slot);
         if (!item.isEmpty()) {
            graphics.item(item, sx + 4, sy + 4, slot);
            graphics.itemDecorations(font, item, sx + 4, sy + 4, this.tooltip.shortCounts() ? abbreviateCount(item.getCount()) : "");
            if (slot == hoveredSlot) {
               hoveredItem = item;
            }
         }
         if (selected) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUNDLE_SLOT_HIGHLIGHT_FRONT, sx, sy, BUNDLE_SLOT, BUNDLE_SLOT);
         }
      }
      this.drawItemTooltip(hoveredItem, graphics, font);
   }

   private int hoveredSlot(final int x, final int y, final boolean bundleLayout, final GuiGraphicsExtractor graphics) {
      int slotSize = bundleLayout ? BUNDLE_SLOT : CUSTOM_SLOT;
      int offset = bundleLayout ? 0 : CUSTOM_PAD;
      int localX = graphics.mouseX() - x - offset;
      int localY = graphics.mouseY() - y - offset;
      if (localX < 0 || localY < 0) {
         return -1;
      }
      int column = localX / slotSize;
      int row = localY / slotSize;
      if (column >= this.columns) {
         return -1;
      }
      int slot = row * this.columns + column;
      return slot < this.slotCount() ? slot : -1;
   }

   private void drawItemTooltip(final ItemStack stack, final GuiGraphicsExtractor graphics, final Font font) {
      if (stack.isEmpty()) {
         return;
      }
      Minecraft minecraft = Minecraft.getInstance();
      List<ClientTooltipComponent> components = Screen.getTooltipFromItem(minecraft, stack).stream()
         .map(Component::getVisualOrderText)
         .map(ClientTooltipComponent::create)
         .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
      Optional<TooltipComponent> image = stack.getTooltipImage();
      image.ifPresent(component -> components.add(components.isEmpty() ? 0 : 1, ClientTooltipComponent.create(component)));
      graphics.tooltip(
         font,
         components,
         graphics.mouseX(),
         graphics.mouseY(),
         DefaultTooltipPositioner.INSTANCE,
         stack.get(DataComponents.TOOLTIP_STYLE)
      );
   }

   private int slotCount() {
      if (!this.tooltip.fullPreview()) {
         return Math.max(1, Math.min(this.compactItems.size(), 27));
      }
      return this.tooltip.items().size();
   }

   private int previewWidth() {
      return this.tooltip.bundle() && this.tooltip.bundleTheme() == 2
         ? this.columns * BUNDLE_SLOT
         : 14 + this.columns * CUSTOM_SLOT;
   }

   private int previewHeight() {
      return this.tooltip.bundle() && this.tooltip.bundleTheme() == 2
         ? this.rows * BUNDLE_SLOT
         : 14 + this.rows * CUSTOM_SLOT;
   }

   private int tint() {
      return this.tooltip.color() < 0 ? -1 : this.tooltip.color() | 0xFF000000;
   }

   private static List<Merged> mergeItems(final List<ItemStack> slots, final int strategy, final int order) {
      List<Merged> merged = new ArrayList<>();
      for (int slot = 0; slot < slots.size(); slot++) {
         ItemStack source = slots.get(slot);
         if (source.isEmpty()) {
            continue;
         }
         Merged found = null;
         for (Merged candidate : merged) {
            boolean same = strategy == 2
               ? ItemStack.isSameItemSameComponents(candidate.stack, source)
               : candidate.stack.is(source.getItem());
            if (same) {
               found = candidate;
               break;
            }
         }
         if (found == null) {
            ItemStack stack = strategy == 0 ? new ItemStack(source.getItem(), source.getCount()) : source.copy();
            found = new Merged(stack, slot, new ArrayList<>());
            merged.add(found);
         } else {
            found.stack.grow(source.getCount());
         }
         found.sourceSlots().add(slot);
      }
      if (order == 0) {
         merged.sort(Comparator.comparingInt((Merged item) -> item.stack.getCount()).reversed()
            .thenComparing(Comparator.comparingInt(Merged::firstSlot).reversed()));
      } else {
         merged.sort(Comparator.comparingInt(Merged::firstSlot));
      }
      return merged;
   }

   private static int findCompactSelectedSlot(final List<Merged> merged, final int selectedSlot) {
      if (selectedSlot < 0) {
         return -1;
      }
      for (int i = 0; i < merged.size(); i++) {
         if (merged.get(i).sourceSlots().contains(selectedSlot)) {
            return i;
         }
      }
      return -1;
   }

   private static String abbreviateCount(final int count) {
      if (count >= 1_000_000) {
         return String.format("%.1fm", count / 1_000_000.0);
      }
      if (count >= 10_000) {
         return (count / 1_000) + "k";
      }
      if (count >= 1_000) {
         return String.format("%.1fk", count / 1_000.0);
      }
      return count <= 1 ? "" : Integer.toString(count);
   }

   private record Merged(ItemStack stack, int firstSlot, List<Integer> sourceSlots) {
   }
}
