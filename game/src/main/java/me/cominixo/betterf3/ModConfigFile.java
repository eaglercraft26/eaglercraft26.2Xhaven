package me.cominixo.betterf3;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModConfigFile {
   public static final String FILE_NAME = "betterf3.properties";
   public static final ModConfigFile INSTANCE = new ModConfigFile();
   private static final Logger LOGGER = LoggerFactory.getLogger("BetterF3");
   private static final int[] TEXT_COLORS = {0xFFE0E0E0, 0xFFFFD966, 0xFF9FE3A5, 0xFF8FD3FF, 0xFFFFA680};
   private final Map<String, ModuleSetting> moduleSettings = new LinkedHashMap<>();
   private final List<String> moduleOrder = new ArrayList<>();
   private File file;
   public boolean enabled = true;
   public boolean textShadow = true;
   public boolean animations = true;
   public boolean showModuleNames;
   public int backgroundColor = 0x6F101010;
   public int moduleSpacing = 4;
   public int lineSpacing = 9;
   public float textScale = 1.0F;

   private ModConfigFile() {
   }

   public void init(final File gameDirectory) {
      this.file = new File(new File(gameDirectory, "config"), FILE_NAME);
      this.load();
   }

   public ModuleSetting setting(final Identifier id) {
      String key = id.toString();
      ModuleSetting setting = this.moduleSettings.get(key);
      if (setting == null) {
         setting = new ModuleSetting();
         setting.enabled = true;
         setting.right = BetterF3Fabric.defaultRight(id);
         setting.sources = new ArrayList<>(BetterF3Fabric.defaultSources(id));
         this.moduleSettings.put(key, setting);
      }
      if (!this.moduleOrder.contains(key)) {
         this.moduleOrder.add(key);
      }
      return setting;
   }

   public List<Identifier> orderedModules(final Iterable<Identifier> candidates) {
      List<Identifier> available = new ArrayList<>();
      for (Identifier id : candidates) {
         this.setting(id);
         available.add(id);
      }
      available.sort((first, second) -> Integer.compare(
         this.moduleOrder.indexOf(first.toString()),
         this.moduleOrder.indexOf(second.toString())
      ));
      return available;
   }

   public List<Identifier> customModuleIds() {
      return this.moduleSettings.entrySet().stream()
         .filter(entry -> entry.getValue().custom)
         .map(entry -> Identifier.tryParse(entry.getKey()))
         .filter(java.util.Objects::nonNull)
         .toList();
   }

   public Identifier createCustomModule() {
      int suffix = 1;
      Identifier id;
      do {
         id = Identifier.fromNamespaceAndPath("betterf3", "custom_" + suffix++);
      } while (this.moduleSettings.containsKey(id.toString()));
      ModuleSetting setting = this.setting(id);
      setting.custom = true;
      setting.name = "Custom Module " + (suffix - 1);
      this.save();
      return id;
   }

   public void removeCustomModule(final Identifier id) {
      ModuleSetting setting = this.moduleSettings.get(id.toString());
      if (setting != null && setting.custom) {
         this.moduleSettings.remove(id.toString());
         this.moduleOrder.remove(id.toString());
         this.save();
      }
   }

   public void setModuleColor(final Identifier id, final String input) {
      Integer parsed = parseColorInput(input);
      if (parsed != null) {
         this.setting(id).color = parsed;
         this.save();
      }
   }

   public void setBackgroundColor(final String input) {
      Integer parsed = parseColorInput(input);
      if (parsed != null) {
         this.backgroundColor = parsed;
         this.save();
      }
   }

   public static Integer parseColorInput(final String value) {
      if (value == null) {
         return null;
      }
      String normalized = value.startsWith("#") ? value.substring(1) : value;
      if (normalized.length() != 6 && normalized.length() != 8) {
         return null;
      }
      for (int i = 0; i < normalized.length(); i++) {
         if (Character.digit(normalized.charAt(i), 16) < 0) {
            return null;
         }
      }
      try {
         long parsed = Long.parseLong(normalized, 16);
         return normalized.length() == 6 ? (int)(0xFF000000L | parsed) : (int)parsed;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   public void moveModule(final Identifier id, final int direction) {
      this.setting(id);
      int index = this.moduleOrder.indexOf(id.toString());
      int target = Math.max(0, Math.min(this.moduleOrder.size() - 1, index + direction));
      if (index != target) {
         this.moduleOrder.remove(index);
         this.moduleOrder.add(target, id.toString());
         this.save();
      }
   }

   public void load() {
      this.moduleSettings.clear();
      this.moduleOrder.clear();
      if (this.file == null || !this.file.isFile()) {
         this.loadDefaultOrder();
         this.save();
         return;
      }
      try {
         Map<String, String> values = new LinkedHashMap<>();
         for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
            int index = line.indexOf('=');
            if (index > 0 && !line.trim().startsWith("#")) {
               values.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
            }
         }
         this.enabled = parseBoolean(values.get("enabled"), this.enabled);
         this.textShadow = parseBoolean(values.get("textShadow"), this.textShadow);
         this.animations = parseBoolean(values.get("animations"), this.animations);
         this.showModuleNames = parseBoolean(values.get("showModuleNames"), this.showModuleNames);
         this.backgroundColor = parseColor(values.get("backgroundColor"), this.backgroundColor);
         this.moduleSpacing = clamp(parseInt(values.get("moduleSpacing"), this.moduleSpacing), 0, 20);
         this.lineSpacing = clamp(parseInt(values.get("lineSpacing"), this.lineSpacing), 8, 20);
         this.textScale = clamp(parseFloat(values.get("textScale"), this.textScale), 0.5F, 2.0F);

         String order = values.get("moduleOrder");
         if (order != null) {
            for (String module : order.split(",")) {
               String normalized = module.trim();
               if (Identifier.tryParse(normalized) != null && !this.moduleOrder.contains(normalized)) {
                  this.moduleOrder.add(normalized);
               }
            }
         }
         for (Map.Entry<String, String> entry : values.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals("module")) {
               continue;
            }
            Identifier id = Identifier.tryParse(parts[1]);
            if (id == null) {
               continue;
            }
            ModuleSetting setting = this.setting(id);
            switch (parts[2]) {
               case "enabled" -> setting.enabled = parseBoolean(entry.getValue(), setting.enabled);
               case "color" -> setting.color = parseColor(entry.getValue(), setting.color);
               case "right" -> setting.right = parseBoolean(entry.getValue(), setting.right);
               case "hiddenLines" -> setting.hiddenLines = parseIndexes(entry.getValue());
               case "custom" -> setting.custom = parseBoolean(entry.getValue(), setting.custom);
               case "name" -> setting.name = entry.getValue();
               case "sources" -> setting.sources = parseSources(entry.getValue());
               case "sourceOverrides" -> setting.sourceOverrides = parseBoolean(entry.getValue(), setting.sourceOverrides);
               default -> {
               }
            }
         }
         this.loadDefaultOrder();
      } catch (IOException e) {
         LOGGER.warn("Could not read BetterF3 configuration {}; using defaults", this.file, e);
         this.moduleSettings.clear();
         this.moduleOrder.clear();
         this.loadDefaultOrder();
         this.save();
      }
   }

   public void save() {
      if (this.file == null) {
         return;
      }
      File parent = this.file.getParentFile();
      try {
         if (parent != null) {
            Files.createDirectories(parent.toPath());
         }
         StringBuilder contents = new StringBuilder();
         contents.append("enabled = ").append(this.enabled).append('\n');
         contents.append("textShadow = ").append(this.textShadow).append('\n');
         contents.append("animations = ").append(this.animations).append('\n');
         contents.append("showModuleNames = ").append(this.showModuleNames).append('\n');
         contents.append("backgroundColor = ").append(String.format("%08X", this.backgroundColor)).append('\n');
         contents.append("moduleSpacing = ").append(this.moduleSpacing).append('\n');
         contents.append("lineSpacing = ").append(this.lineSpacing).append('\n');
         contents.append("textScale = ").append(this.textScale).append('\n');
         contents.append("moduleOrder = ").append(String.join(",", this.moduleOrder)).append('\n');
         this.moduleSettings.forEach((id, setting) -> {
            contents.append("module|").append(id).append("|enabled = ").append(setting.enabled).append('\n');
            contents.append("module|").append(id).append("|color = ").append(String.format("%08X", setting.color)).append('\n');
            contents.append("module|").append(id).append("|right = ").append(setting.right).append('\n');
            contents.append("module|").append(id).append("|hiddenLines = ").append(joinIndexes(setting.hiddenLines)).append('\n');
            contents.append("module|").append(id).append("|custom = ").append(setting.custom).append('\n');
            contents.append("module|").append(id).append("|name = ").append(setting.name.replace('\n', ' ')).append('\n');
            if (setting.custom || setting.sourceOverrides) {
               contents.append("module|").append(id).append("|sources = ").append(String.join(",", setting.sources)).append('\n');
            }
            contents.append("module|").append(id).append("|sourceOverrides = ").append(setting.sourceOverrides).append('\n');
         });
         Files.writeString(this.file.toPath(), contents, StandardCharsets.UTF_8);
      } catch (IOException e) {
         LOGGER.error("Could not save BetterF3 configuration {}", this.file, e);
      }
   }

   private void loadDefaultOrder() {
      for (Identifier id : BetterF3Fabric.builtInModuleIds()) {
         if (!this.moduleOrder.contains(id.toString())) {
            this.moduleOrder.add(id.toString());
         }
         this.setting(id);
      }
      this.customModuleIds().forEach(id -> {
         if (!this.moduleOrder.contains(id.toString())) {
            this.moduleOrder.add(id.toString());
         }
      });
   }

   private static boolean parseBoolean(final String value, final boolean fallback) {
      if ("true".equalsIgnoreCase(value)) {
         return true;
      }
      return "false".equalsIgnoreCase(value) ? false : fallback;
   }

   private static int parseInt(final String value, final int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException | NullPointerException e) {
         return fallback;
      }
   }

   private static float parseFloat(final String value, final float fallback) {
      try {
         float parsed = Float.parseFloat(value);
         return Float.isFinite(parsed) ? parsed : fallback;
      } catch (NumberFormatException | NullPointerException e) {
         return fallback;
      }
   }

   private static int parseColor(final String value, final int fallback) {
      Integer parsed = parseColorInput(value);
      return parsed == null ? fallback : parsed;
   }

   private static List<String> parseSources(final String value) {
      ArrayList<String> sources = new ArrayList<>();
      for (String source : value.split(",")) {
         String trimmed = source.trim();
         if (Identifier.tryParse(trimmed) != null && !sources.contains(trimmed)) {
            sources.add(trimmed);
         }
      }
      return sources;
   }

   private static Set<Integer> parseIndexes(final String value) {
      Set<Integer> indexes = new HashSet<>();
      for (String token : value.split(",")) {
         try {
            int index = Integer.parseInt(token);
            if (index >= 0) {
               indexes.add(index);
            }
         } catch (NumberFormatException ignored) {
         }
      }
      return indexes;
   }

   private static String joinIndexes(final Set<Integer> indexes) {
      return indexes.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
   }

   private static int clamp(final int value, final int min, final int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static float clamp(final float value, final float min, final float max) {
      return Math.max(min, Math.min(max, value));
   }

   public static final class ModuleSetting {
      public boolean enabled = true;
      public boolean right;
      public int color = TEXT_COLORS[0];
      public Set<Integer> hiddenLines = new HashSet<>();
      public boolean custom;
      public boolean sourceOverrides;
      public String name = "";
      public List<String> sources = new ArrayList<>();

      public void cycleColor() {
         int index = 0;
         for (int i = 0; i < TEXT_COLORS.length; i++) {
            if (TEXT_COLORS[i] == this.color) {
               index = i;
               break;
            }
         }
         this.color = TEXT_COLORS[(index + 1) % TEXT_COLORS.length];
      }
   }
}
