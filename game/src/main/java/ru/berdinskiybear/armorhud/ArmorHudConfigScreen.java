package ru.berdinskiybear.armorhud;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ArmorHudConfigScreen extends Screen {
   private final Screen parent;
   private int page;

   public ArmorHudConfigScreen(final Screen parent) {
      super(Component.translatable("armorhud.config"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearWidgets();
      int left = this.width / 2 - 154;
      int right = this.width / 2 + 4;
      int y = 44;

      if (this.page == 0) {
         this.addToggle("armorhud.option.enabled", () -> ArmorHudPort.CONFIG.enabled, value -> ArmorHudPort.CONFIG.enabled = value, left, y);
         this.addCycle("armorhud.option.anchor", ArmorHudConfig.Anchor.values(), () -> ArmorHudPort.CONFIG.anchor, value -> ArmorHudPort.CONFIG.anchor = value, right, y);
         y += 25;
         this.addCycle("armorhud.option.side", ArmorHudConfig.Side.values(), () -> ArmorHudPort.CONFIG.side, value -> ArmorHudPort.CONFIG.side = value, left, y);
         this.addCycle("armorhud.option.style", ArmorHudConfig.Style.values(), () -> ArmorHudPort.CONFIG.style, value -> ArmorHudPort.CONFIG.style = value, right, y);
         y += 25;
         this.addCycle("armorhud.option.orientation", ArmorHudConfig.Orientation.values(), () -> ArmorHudPort.CONFIG.orientation, value -> ArmorHudPort.CONFIG.orientation = value, left, y);
         this.addCycle("armorhud.option.widgetShown", ArmorHudConfig.WidgetShown.values(), () -> ArmorHudPort.CONFIG.widgetShown, value -> ArmorHudPort.CONFIG.widgetShown = value, right, y);
         y += 25;
         this.addCycle("armorhud.option.offhandSlotBehavior", ArmorHudConfig.OffhandSlotBehavior.values(), () -> ArmorHudPort.CONFIG.offhandSlotBehavior, value -> ArmorHudPort.CONFIG.offhandSlotBehavior = value, left, y);
         this.addToggle("armorhud.option.reversed", () -> ArmorHudPort.CONFIG.reversed, value -> ArmorHudPort.CONFIG.reversed = value, right, y);
         y += 25;
         this.addToggle("armorhud.option.showIcons", () -> ArmorHudPort.CONFIG.iconsShown, value -> ArmorHudPort.CONFIG.iconsShown = value, left, y);
         this.addCycle("armorhud.option.durabilityDisplay", ArmorHudConfig.DurabilityDisplay.values(), () -> ArmorHudPort.CONFIG.durabilityDisplay, value -> ArmorHudPort.CONFIG.durabilityDisplay = value, right, y);
         y += 25;
         this.addNumberInput("armorhud.option.offsetX", Integer.toString(ArmorHudPort.CONFIG.offsetX), left, y, value -> ArmorHudPort.CONFIG.offsetX = clamp(value, -2000, 2000));
         this.addNumberInput("armorhud.option.offsetY", Integer.toString(ArmorHudPort.CONFIG.offsetY), right, y, value -> ArmorHudPort.CONFIG.offsetY = clamp(value, -2000, 2000));
      } else {
         this.addToggle("armorhud.option.pushBossbars", () -> ArmorHudPort.CONFIG.pushBossbars, value -> ArmorHudPort.CONFIG.pushBossbars = value, left, y);
         this.addToggle("armorhud.option.pushIcons", () -> ArmorHudPort.CONFIG.pushStatusEffectIcons, value -> ArmorHudPort.CONFIG.pushStatusEffectIcons = value, right, y);
         y += 25;
         this.addToggle("armorhud.option.pushSubtitles", () -> ArmorHudPort.CONFIG.pushSubtitles, value -> ArmorHudPort.CONFIG.pushSubtitles = value, left, y);
         this.addToggle("armorhud.option.showWarning", () -> ArmorHudPort.CONFIG.warningShown, value -> ArmorHudPort.CONFIG.warningShown = value, right, y);
         y += 25;
         this.addToggle("armorhud.option.playBreakSound", () -> ArmorHudPort.CONFIG.playBreakSound, value -> ArmorHudPort.CONFIG.playBreakSound = value, left, y);
         this.addNumberInput("armorhud.option.minDuraValue", Integer.toString(ArmorHudPort.CONFIG.minDurabilityValue), right, y, value -> ArmorHudPort.CONFIG.minDurabilityValue = clamp(value, 0, 100000));
         y += 25;
         this.addNumberInput(
            "armorhud.option.minDuraPercent",
            Integer.toString((int)Math.round(ArmorHudPort.CONFIG.minDurabilityPercentage * 100.0)),
            left,
            y,
            value -> ArmorHudPort.CONFIG.minDurabilityPercentage = clamp(value, 0, 100) / 100.0
         );
         this.addNumberInput("armorhud.option.iconBobIntensity", Integer.toString(ArmorHudPort.CONFIG.warningBobIntensity), right, y, value -> ArmorHudPort.CONFIG.warningBobIntensity = clamp(value, 0, 20));
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("armorhud.screen.switchPage"), button -> {
            this.page = (this.page + 1) % 2;
            ArmorHudPort.CONFIG.save();
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 154, this.height - 29, 74, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("armorhud.screen.page", this.page + 1, 2), button -> {
            this.page = (this.page + 1) % 2;
            ArmorHudPort.CONFIG.save();
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 74, this.height - 29, 148, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 + 80, this.height - 29, 74, 20)
            .build()
      );
   }

   private void addToggle(
      final String translationKey,
      final Supplier<Boolean> current,
      final Consumer<Boolean> update,
      final int x,
      final int y
   ) {
      this.addRenderableWidget(
         Button.builder(toggleLabel(translationKey, current.get()), button -> {
            update.accept(!current.get());
            ArmorHudPort.CONFIG.save();
            this.init(this.width, this.height);
         }).bounds(x, y, 150, 20).build()
      );
   }

   private <E extends Enum<E>> void addCycle(
      final String translationKey,
      final E[] values,
      final Supplier<E> current,
      final Consumer<E> update,
      final int x,
      final int y
   ) {
      this.addRenderableWidget(
         Button.builder(cycleLabel(translationKey, current.get()), button -> {
            E[] options = values;
            update.accept(options[(current.get().ordinal() + 1) % options.length]);
            ArmorHudPort.CONFIG.save();
            this.init(this.width, this.height);
         }).bounds(x, y, 150, 20).build()
      );
   }

   private void addNumberInput(
      final String translationKey,
      final String initialValue,
      final int x,
      final int y,
      final Consumer<Integer> update
   ) {
      Button label = Button.builder(Component.translatable(translationKey), button -> {
      }).bounds(x, y, 72, 20).build();
      label.active = false;
      this.addRenderableWidget(label);

      EditBox input = new EditBox(this.font, x + 74, y, 76, 20, Component.translatable(translationKey));
      input.setMaxLength(8);
      input.setValue(initialValue);
      input.setResponder(value -> {
         try {
            update.accept(Integer.parseInt(value));
         } catch (NumberFormatException ignored) {
         }
      });
      this.addRenderableWidget(input);
   }

   @Override
   public void onClose() {
      ArmorHudPort.CONFIG.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void removed() {
      ArmorHudPort.CONFIG.save();
   }

   private static Component toggleLabel(final String key, final boolean value) {
      return Component.translatable(key).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
   }

   private static Component cycleLabel(final String key, final Enum<?> value) {
      return Component.translatable(key).append(": ").append(Component.translatable("armorhud.option." + enumTranslationKey(value.name())));
   }

   private static String enumTranslationKey(final String name) {
      return switch (name) {
         case "TOP_CENTER" -> "topCenter";
         case "ROUNDED_CORNERS" -> "roundedCorners";
         case "IF_ANY_PRESENT" -> "ifAnyPresent";
         case "NOT_EMPTY" -> "notEmpty";
         case "DAMAGED_PIECES" -> "damagedPieces";
         case "ALWAYS_IGNORE" -> "alwaysIgnore";
         case "ALWAYS_LEAVE_SPACE" -> "alwaysLeaveSpace";
         default -> name.toLowerCase(java.util.Locale.ROOT);
      };
   }

   private static int clamp(final int value, final int min, final int max) {
      return Math.max(min, Math.min(max, value));
   }
}
