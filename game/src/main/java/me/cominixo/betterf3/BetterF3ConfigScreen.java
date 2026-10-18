package me.cominixo.betterf3;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BetterF3ConfigScreen extends Screen {
   private final Screen parent;
   private int modulePage;
   private Identifier selectedModule;
   private int entryPage;
   private int linePage;
   private int modulePageCount = 1;
   private int entryPageCount = 1;
   private int linePageCount = 1;
   private boolean showingLines;

   public BetterF3ConfigScreen(final Screen parent) {
      super(Component.literal("BetterF3 Configuration"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearWidgets();
      ModConfigFile config = BetterF3Fabric.config();
      int center = this.width / 2;
      int buttonY = 34;
      this.addRenderableWidget(Button.builder(label("HUD", config.enabled), button -> {
         config.enabled = !config.enabled;
         config.save();
         this.init(this.width, this.height);
      }).bounds(center - 190, buttonY, 68, 20).build());
      this.addRenderableWidget(Button.builder(label("Shadow", config.textShadow), button -> {
         config.textShadow = !config.textShadow;
         config.save();
         this.init(this.width, this.height);
      }).bounds(center - 118, buttonY, 68, 20).build());
      this.addRenderableWidget(Button.builder(label("Names", config.showModuleNames), button -> {
         config.showModuleNames = !config.showModuleNames;
         config.save();
         this.init(this.width, this.height);
      }).bounds(center - 46, buttonY, 68, 20).build());
      this.addRenderableWidget(Button.builder(label("Animation", config.animations), button -> {
         config.animations = !config.animations;
         config.save();
         this.init(this.width, this.height);
      }).bounds(center + 26, buttonY, 84, 20).build());
      this.addColorInput(config, null, center + 114, buttonY, 88);

      this.addRenderableWidget(Button.builder(Component.literal("Module spacing -"), button -> {
         config.moduleSpacing = Math.max(0, config.moduleSpacing - 1);
         config.save();
      }).bounds(center - 190, buttonY + 24, 92, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Module spacing +"), button -> {
         config.moduleSpacing = Math.min(20, config.moduleSpacing + 1);
         config.save();
      }).bounds(center - 94, buttonY + 24, 92, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Line spacing -"), button -> {
         config.lineSpacing = Math.max(8, config.lineSpacing - 1);
         config.save();
      }).bounds(center + 2, buttonY + 24, 86, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Line spacing +"), button -> {
         config.lineSpacing = Math.min(20, config.lineSpacing + 1);
         config.save();
      }).bounds(center + 92, buttonY + 24, 86, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Scale -"), button -> {
         config.textScale = Math.max(0.5F, config.textScale - 0.1F);
         config.save();
      }).bounds(center - 190, buttonY + 48, 74, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Scale +"), button -> {
         config.textScale = Math.min(2.0F, config.textScale + 0.1F);
         config.save();
      }).bounds(center - 112, buttonY + 48, 74, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Reset colors"), button -> {
         for (Identifier id : BetterF3Fabric.availableModules()) {
            config.setting(id).color = 0xFFE0E0E0;
         }
         config.save();
         this.init(this.width, this.height);
      }).bounds(center - 34, buttonY + 48, 82, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Shulker settings"), button ->
         this.minecraft.gui.setScreen(new com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipConfigScreen(this))
      ).bounds(center + 54, buttonY + 48, 120, 20).build());

      if (this.selectedModule == null) {
         this.addModuleControls(config);
      } else if (this.showingLines) {
         this.addLineControls(config);
      } else {
         this.addEntryControls(config);
      }
      this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
         .bounds(center - 100, this.height - 28, 200, 20).build());
   }

   private void addModuleControls(final ModConfigFile config) {
      List<Identifier> modules = BetterF3Fabric.availableModules();
      int pageSize = Math.max(1, Math.min(7, (this.height - 190) / 24));
      int pageCount = Math.max(1, (modules.size() + pageSize - 1) / pageSize);
      this.modulePageCount = pageCount;
      this.modulePage = Math.max(0, Math.min(this.modulePage, pageCount - 1));
      int start = this.modulePage * pageSize;
      int end = Math.min(modules.size(), start + pageSize);
      int y = 118;
      int left = Math.max(4, this.width / 2 - 159);
      for (int i = start; i < end; i++) {
         Identifier id = modules.get(i);
         ModConfigFile.ModuleSetting setting = config.setting(id);
         String moduleName = setting.custom && !setting.name.isBlank() ? setting.name : BetterF3Fabric.moduleName(id);
         this.addRenderableWidget(Button.builder(label(moduleName, setting.enabled), button -> {
            setting.enabled = !setting.enabled;
            config.save();
            this.init(this.width, this.height);
         }).bounds(left, y, 112, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal(setting.right ? "Right" : "Left"), button -> {
            setting.right = !setting.right;
            config.save();
            this.init(this.width, this.height);
         }).bounds(left + 116, y, 46, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Color"), button -> {
            setting.cycleColor();
            config.save();
            this.init(this.width, this.height);
         }).bounds(left + 166, y, 44, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
            config.moveModule(id, -1);
            this.init(this.width, this.height);
         }).bounds(left + 214, y, 22, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
            config.moveModule(id, 1);
            this.init(this.width, this.height);
         }).bounds(left + 240, y, 22, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Edit"), button -> {
            this.selectedModule = id;
            this.showingLines = false;
            this.entryPage = 0;
            this.init(this.width, this.height);
         }).bounds(left + 266, y, 50, 20).build());
         y += 24;
      }
      this.addRenderableWidget(Button.builder(Component.literal("Add custom module"), button -> {
         this.selectedModule = config.createCustomModule();
         this.showingLines = false;
         this.entryPage = 0;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 100, this.height - 78, 200, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
         this.modulePage = Math.max(0, this.modulePage - 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 150, this.height - 54, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
         this.modulePage = Math.min(pageCount - 1, this.modulePage + 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 + 60, this.height - 54, 90, 20).build());
   }

   private void addEntryControls(final ModConfigFile config) {
      ModConfigFile.ModuleSetting setting = config.setting(this.selectedModule);
      int controlsY = 118;
      this.addColorInput(config, this.selectedModule, this.width / 2 - 150, controlsY, 220);
      if (setting.custom) {
         EditBox name = new EditBox(this.font, this.width / 2 - 150, controlsY + 24, 220, 20, Component.literal("Module name"));
         name.setMaxLength(48);
         name.setValue(setting.name);
         name.setResponder(value -> {
            if (!value.isBlank()) {
               setting.name = value;
               config.save();
            }
         });
         this.addRenderableWidget(name);
         this.addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
            config.removeCustomModule(this.selectedModule);
            this.selectedModule = null;
            this.init(this.width, this.height);
         }).bounds(this.width / 2 + 74, controlsY + 24, 76, 20).build());
         controlsY += 48;
      } else {
         controlsY += 24;
      }
      if (!setting.custom && BetterF3Fabric.hasNativeData(this.selectedModule)) {
         this.addRenderableWidget(Button.builder(Component.literal("Hide rendered lines"), button -> {
            this.showingLines = true;
            this.linePage = 0;
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 150, controlsY, 300, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Back to modules"), button -> {
            this.selectedModule = null;
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 150, this.height - 54, 90, 20).build());
         return;
      }
      if (!setting.custom) {
         this.addRenderableWidget(Button.builder(Component.literal(setting.sourceOverrides ? "Use defaults" : "Customize entries"), button -> {
            if (setting.sourceOverrides) {
               setting.sources = new java.util.ArrayList<>(BetterF3Fabric.defaultSources(this.selectedModule));
               setting.sourceOverrides = false;
            } else {
               setting.sourceOverrides = true;
            }

            config.save();
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 150, controlsY, 300, 20).build());
         controlsY += 24;
      }
      this.addRenderableWidget(Button.builder(Component.literal("Select entries"), button -> {
         this.showingLines = false;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 150, controlsY, 145, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Hide rendered lines"), button -> {
         this.showingLines = true;
         this.linePage = 0;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 + 5, controlsY, 145, 20).build());

      List<Identifier> sources = BetterF3Fabric.availableSources();
      int pageSize = Math.max(1, Math.min(7, (this.height - controlsY - 145) / 24));
      int pageCount = Math.max(1, (sources.size() + pageSize - 1) / pageSize);
      this.entryPageCount = pageCount;
      this.entryPage = Math.max(0, Math.min(this.entryPage, pageCount - 1));
      int start = this.entryPage * pageSize;
      int end = Math.min(sources.size(), start + pageSize);
      int y = controlsY + 28;
      for (int i = start; i < end; i++) {
         Identifier source = sources.get(i);
         boolean selected = setting.sources.contains(source.toString());
         String label = source.getPath().replace('_', ' ') + " (" + source.getNamespace() + ")";
         this.addRenderableWidget(Button.builder(label(label, selected), button -> {
            setting.sourceOverrides = true;
            if (selected) {
               setting.sources.remove(source.toString());
            } else if (!setting.sources.contains(source.toString())) {
               setting.sources.add(source.toString());
            }
            config.save();
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 150, y, 300, 20).build());
         y += 24;
      }
      this.addRenderableWidget(Button.builder(Component.literal("Back to modules"), button -> {
         this.selectedModule = null;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 150, this.height - 54, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Previous entries"), button -> {
         this.entryPage = Math.max(0, this.entryPage - 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 50, this.height - 54, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Next entries"), button -> {
         this.entryPage = Math.min(pageCount - 1, this.entryPage + 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 + 60, this.height - 54, 90, 20).build());
   }

   private void addColorInput(final ModConfigFile config, final Identifier module, final int x, final int y, final int width) {
      EditBox color = new EditBox(this.font, x, y, width, 20, Component.literal("ARGB hex color"));
      color.setMaxLength(9);
      int initial = module == null ? config.backgroundColor : config.setting(module).color;
      color.setValue(String.format("%08X", initial));
      color.setResponder(value -> {
         if (module == null) {
            config.setBackgroundColor(value);
         } else {
            config.setModuleColor(module, value);
         }
      });
      this.addRenderableWidget(color);
   }

   private void addLineControls(final ModConfigFile config) {
      List<String> lines = BetterF3Fabric.latestLines(this.selectedModule);
      ModConfigFile.ModuleSetting setting = config.setting(this.selectedModule);
      int pageSize = Math.max(1, Math.min(8, (this.height - 180) / 24));
      int pageCount = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
      this.linePageCount = pageCount;
      this.linePage = Math.max(0, Math.min(this.linePage, pageCount - 1));
      int start = this.linePage * pageSize;
      int end = Math.min(lines.size(), start + pageSize);
      int y = 118;
      for (int i = start; i < end; i++) {
         int lineIndex = i;
         String line = lines.get(i);
         boolean enabled = !setting.hiddenLines.contains(i);
         this.addRenderableWidget(Button.builder(label(line, enabled), button -> {
            if (!setting.hiddenLines.add(lineIndex)) {
               setting.hiddenLines.remove(lineIndex);
            }
            config.save();
            this.init(this.width, this.height);
         }).bounds(this.width / 2 - 150, y, 300, 20).build());
         y += 24;
      }
      this.addRenderableWidget(Button.builder(Component.literal("Back to entries"), button -> {
         this.showingLines = false;
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 150, this.height - 54, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Previous lines"), button -> {
         this.linePage = Math.max(0, this.linePage - 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 - 50, this.height - 54, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Next lines"), button -> {
         this.linePage = Math.min(pageCount - 1, this.linePage + 1);
         this.init(this.width, this.height);
      }).bounds(this.width / 2 + 60, this.height - 54, 90, 20).build());
   }

   private static Component label(final String name, final boolean enabled) {
      return Component.literal((enabled ? "[x] " : "[ ] ") + name);
   }

   @Override
   public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
      String page = this.selectedModule == null
         ? "Modules " + (this.modulePage + 1) + " / " + this.modulePageCount
         : this.showingLines
            ? "Rendered lines " + (this.linePage + 1) + " / " + this.linePageCount
            : "Entries " + (this.entryPage + 1) + " / " + this.entryPageCount;
      graphics.centeredText(this.font, Component.literal(page), this.width / 2, 102, 0xFFB0B0B0);
      if (this.showingLines && BetterF3Fabric.latestLines(this.selectedModule).isEmpty()) {
         graphics.centeredText(
            this.font,
            Component.literal("Rendered lines appear when this module has live data."),
            this.width / 2,
            126,
            0xFFB0B0B0
         );
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
