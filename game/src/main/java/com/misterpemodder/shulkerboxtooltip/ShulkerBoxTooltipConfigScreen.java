package com.misterpemodder.shulkerboxtooltip;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ShulkerBoxTooltipConfigScreen extends Screen {
   private final Screen parent;
   private int page;
   private int waitingForKey;
   private int paletteColorIndex;

   public ShulkerBoxTooltipConfigScreen(final Screen parent) {
      super(Component.literal("Shulker Box Tooltip"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearWidgets();
      ShulkerBoxTooltip.Config config = ShulkerBoxTooltip.CONFIG;
      int left = this.width / 2 - 154;
      int right = this.width / 2 + 4;
      int y = 50;
      if (this.page == 0) {
         this.addToggle("Preview enabled", () -> config.enabled, value -> config.enabled = value, left, y);
         this.addToggle("Always on", () -> config.alwaysOn, value -> config.alwaysOn = value, right, y);
         y += 23;
         this.addToggle("Swap preview modes", () -> config.swapModes, value -> config.swapModes = value, left, y);
         this.addToggle("Full preview enabled", () -> config.fullPreviewEnabled, value -> config.fullPreviewEnabled = value, right, y);
         y += 23;
         this.addToggle("Generic container preview", () -> config.genericContainerPreview, value -> config.genericContainerPreview = value, left, y);
         this.addCycle("Theme: " + new String[]{"Shulker", "Vanilla"}[config.theme], () -> config.theme = (config.theme + 1) % 2, left, y + 23);
         this.addCycle("Bundle: " + new String[]{"Shulker", "Vanilla", "Vanilla+"}[config.bundleTheme], () -> config.bundleTheme = (config.bundleTheme + 1) % 3, right, y + 23);
         this.addCycle("Position: " + new String[]{"Inside", "Auto outside", "Above", "Below"}[config.position], () -> config.position = (config.position + 1) % 4, right, y);
      } else if (this.page == 1) {
         this.addToggle("Color previews", () -> config.coloredPreview, value -> config.coloredPreview = value, left, y);
         this.addToggle("Short stack counts", () -> config.shortItemCounts, value -> config.shortItemCounts = value, right, y);
         this.addRenderableWidget(Button.builder(Component.literal("Edit color palette"), button -> {
            this.page = 3;
            this.init(this.width, this.height);
         }).bounds(left, y + 23, 148, 20).build());
         this.addCycle("Rows: " + config.maxRowSize, () -> config.maxRowSize = config.maxRowSize % 9 + 1, right, y + 23);
         y += 48;
         this.addCycle("Compact order: " + new String[]{"stack size", "inventory"}[config.compactPreviewOrder], () -> config.compactPreviewOrder = (config.compactPreviewOrder + 1) % 2, left, y);
         this.addCycle("Stack merge: " + new String[]{"ignore data", "first item", "separate"}[config.mergeStrategy], () -> config.mergeStrategy = (config.mergeStrategy + 1) % 3, right, y);
         y += 23;
         this.addToggle("Key hints", () -> config.showKeyHints, value -> config.showKeyHints = value, left, y);
         this.addCycle("Tooltip: " + new String[]{"vanilla", "modded", "none"}[config.tooltipType], () -> config.tooltipType = (config.tooltipType + 1) % 3, right, y);
         y += 23;
         this.addCycle("Loot info: " + new String[]{"hide", "simple", "advanced"}[config.lootTableInfoType], () -> config.lootTableInfoType = (config.lootTableInfoType + 1) % 3, left, y);
         this.addToggle("Hide shulker lore", () -> config.hideShulkerBoxLore, value -> config.hideShulkerBoxLore = value, right, y);
      } else if (this.page == 2) {
         this.addKeyButton("Preview", config.previewKey, 0, left, y);
         this.addKeyButton("Full preview", config.fullPreviewKey, 1, right, y);
         this.addKeyButton("Lock tooltip", config.lockKey, 2, left, y + 24);
         this.addRenderableWidget(Button.builder(Component.literal("Reset keys"), button -> {
            config.previewKey = 340;
            config.fullPreviewKey = 342;
            config.lockKey = 341;
            this.saveAndRefresh();
         }).bounds(right, y + 24, 148, 20).build());
      } else {
         java.util.List<String> colors = ShulkerBoxTooltip.paletteKeys();
         this.paletteColorIndex = Math.floorMod(this.paletteColorIndex, colors.size());
         String key = colors.get(this.paletteColorIndex);
         String displayName = key.replace('_', ' ');
         this.addRenderableWidget(Button.builder(Component.literal("Color: " + displayName), button -> {
            this.paletteColorIndex = (this.paletteColorIndex + 1) % colors.size();
            this.init(this.width, this.height);
         }).bounds(left, y, 148, 20).build());
         EditBox color = new EditBox(this.font, right, y, 148, 20, Component.literal("RGB hex color"));
         color.setMaxLength(7);
         color.setValue(String.format("%06X", config.paletteColor(key)));
         color.setResponder(value -> config.setPaletteColor(key, value));
         this.addRenderableWidget(color);
         this.addRenderableWidget(Button.builder(Component.literal("Reset selected color"), button -> {
            config.resetPaletteColor(key);
            this.init(this.width, this.height);
         }).bounds(left, y + 24, 148, 20).build());
      }

      this.addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
         this.page = (this.page + 3) % 4;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 154, this.height - 29, 74, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal((this.page + 1) + " / 4"), button -> {
         this.page = (this.page + 1) % 4;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 74, this.height - 29, 148, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
         .bounds(this.width / 2 + 80, this.height - 29, 74, 20).build());
   }

   private void addToggle(
      final String name,
      final BooleanSupplier current,
      final Consumer<Boolean> update,
      final int x,
      final int y
   ) {
      this.addRenderableWidget(Button.builder(Component.literal((current.getAsBoolean() ? "[x] " : "[ ] ") + name), button -> {
         update.accept(!current.getAsBoolean());
         this.saveAndRefresh();
      }).bounds(x, y, 148, 20).build());
   }

   private void addCycle(final String name, final Runnable action, final int x, final int y) {
      this.addRenderableWidget(Button.builder(Component.literal(name), button -> {
         action.run();
         this.saveAndRefresh();
      }).bounds(x, y, 148, 20).build());
   }

   private void addKeyButton(final String name, final int key, final int keySlot, final int x, final int y) {
      this.addRenderableWidget(Button.builder(Component.literal(name + ": " + ShulkerBoxTooltip.keyName(key)), button -> {
         this.waitingForKey = keySlot + 1;
      }).bounds(x, y, 148, 20).build());
   }

   private void saveAndRefresh() {
      ShulkerBoxTooltip.CONFIG.save();
      this.init(this.width, this.height);
   }

   @Override
   public boolean keyPressed(final KeyEvent event) {
      if (this.waitingForKey != 0) {
         if (event.isEscape()) {
            this.waitingForKey = 0;
            return true;
         }
         if (event.key() >= 0) {
            switch (this.waitingForKey) {
               case 1 -> ShulkerBoxTooltip.CONFIG.previewKey = event.key();
               case 2 -> ShulkerBoxTooltip.CONFIG.fullPreviewKey = event.key();
               case 3 -> ShulkerBoxTooltip.CONFIG.lockKey = event.key();
               default -> throw new IllegalStateException("Unknown ShulkerBoxTooltip key capture");
            }
            this.waitingForKey = 0;
            this.saveAndRefresh();
            return true;
         }
      }
      return super.keyPressed(event);
   }

   @Override
   public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
      String help = this.waitingForKey != 0
         ? "Press a key to bind; Esc cancels."
         : this.page == 0
            ? "Container previews use their stored item data and preserve full-mode slots."
            : this.page == 1
               ? "Compact previews merge actual stacks according to the selected item-data rule."
               : this.page == 2
                  ? "Defaults: left Shift preview, left Alt full mode, left Control lock."
                  : "Edit the original built-in color keys; enter six hexadecimal RGB digits.";
      graphics.centeredText(this.font, Component.literal(help), this.width / 2, 29, 0xFFB0B0B0);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
