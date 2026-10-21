package ru.berdinskiybear.armorhud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArmorHudConfig {
   public static final String FILE_NAME = "ukus-armor-hud.properties";
   private static final Logger LOGGER = LoggerFactory.getLogger("uku's Armor HUD");
   private File file;

   public boolean enabled = true;
   public Anchor anchor = Anchor.HOTBAR;
   public Side side = Side.LEFT;
   public int offsetX;
   public int offsetY;
   public Style style = Style.HOTBAR;
   public Orientation orientation = Orientation.HORIZONTAL;
   public WidgetShown widgetShown = WidgetShown.NOT_EMPTY;
   public OffhandSlotBehavior offhandSlotBehavior = OffhandSlotBehavior.ADHERE;
   public DurabilityDisplay durabilityDisplay = DurabilityDisplay.BAR;
   public boolean pushBossbars = true;
   public boolean pushStatusEffectIcons = true;
   public boolean pushSubtitles = true;
   public boolean reversed;
   public boolean iconsShown = true;
   public boolean warningShown = true;
   public boolean playBreakSound = true;
   public int minDurabilityValue = 20;
   public double minDurabilityPercentage = 0.1;
   public int warningBobIntensity = 3;

   public enum Anchor {
      TOP_CENTER,
      TOP,
      BOTTOM,
      HOTBAR
   }

   public enum Side {
      LEFT,
      RIGHT
   }

   public enum Style {
      HOTBAR,
      ROUNDED_CORNERS,
      ROUNDED,
      NONE
   }

   public enum Orientation {
      HORIZONTAL,
      VERTICAL
   }

   public enum WidgetShown {
      ALWAYS,
      IF_ANY_PRESENT,
      NOT_EMPTY,
      DAMAGED_PIECES
   }

   public enum OffhandSlotBehavior {
      ALWAYS_IGNORE,
      ADHERE,
      ALWAYS_LEAVE_SPACE
   }

   public enum DurabilityDisplay {
      BAR,
      NUMERIC,
      PERCENTAGE
   }

   public void init(final File gameDirectory) {
      this.file = new File(new File(gameDirectory, "config"), FILE_NAME);
      this.load();
   }

   public void load() {
      if (this.file == null) {
         return;
      }
      if (!this.file.isFile()) {
         Map<String, String> legacyValues = this.readLegacyConfig();
         if (legacyValues == null) {
            this.save();
            return;
         }
         this.applyValues(legacyValues);
         this.save();
         return;
      }

      Map<String, String> values = new LinkedHashMap<>();
      try {
         for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
               int separator = line.indexOf('=');
               if (separator > 0) {
                  values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
               }
            }
         }
      } catch (IOException exception) {
         LOGGER.warn("Could not read Armor HUD configuration {}; using defaults", this.file, exception);
         return;
      }

      this.applyValues(values);
   }

   private Map<String, String> readLegacyConfig() {
      File configDirectory = this.file.getParentFile();
      File[] legacyFiles = {
         new File(configDirectory, "ukus-armor-hud.json"),
         new File(new File(configDirectory, "ukulib"), "ukus-armor-hud.json")
      };
      for (File legacyFile : legacyFiles) {
         if (!legacyFile.isFile()) {
            continue;
         }
         try {
            JsonElement root = JsonParser.parseString(Files.readString(legacyFile.toPath(), StandardCharsets.UTF_8));
            if (!(root instanceof JsonObject object)) {
               throw new JsonParseException("Expected a JSON object");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
               if (entry.getValue().isJsonPrimitive()) {
                  values.put(entry.getKey(), entry.getValue().getAsString());
               } else {
                  LOGGER.warn("Ignoring invalid Armor HUD value for {} in {}", entry.getKey(), legacyFile);
               }
            }
            return values;
         } catch (IOException | JsonParseException | IllegalStateException exception) {
            LOGGER.warn("Could not read legacy Armor HUD configuration {}; using defaults", legacyFile, exception);
            return null;
         }
      }
      return null;
   }

   private void applyValues(final Map<String, String> values) {
      this.enabled = parseBoolean(values, "enabled", this.enabled);
      this.anchor = parseEnum(values, "anchor", this.anchor, Anchor.class);
      this.side = parseEnum(values, "side", this.side, Side.class);
      this.offsetX = clamp(parseInt(values, "offsetX", this.offsetX), -2000, 2000);
      this.offsetY = clamp(parseInt(values, "offsetY", this.offsetY), -2000, 2000);
      this.style = parseEnum(values, "style", this.style, Style.class);
      this.orientation = parseEnum(values, "orientation", this.orientation, Orientation.class);
      this.widgetShown = parseEnum(values, "widgetShown", this.widgetShown, WidgetShown.class);
      this.offhandSlotBehavior = parseEnum(values, "offhandSlotBehavior", this.offhandSlotBehavior, OffhandSlotBehavior.class);
      this.durabilityDisplay = parseEnum(values, "durabilityDisplay", this.durabilityDisplay, DurabilityDisplay.class);
      this.pushBossbars = parseBoolean(values, "pushBossbars", this.pushBossbars);
      this.pushStatusEffectIcons = parseBoolean(values, "pushStatusEffectIcons", this.pushStatusEffectIcons);
      this.pushSubtitles = parseBoolean(values, "pushSubtitles", this.pushSubtitles);
      this.reversed = parseBoolean(values, "reversed", this.reversed);
      this.iconsShown = parseBoolean(values, "iconsShown", this.iconsShown);
      this.warningShown = parseBoolean(values, "warningShown", this.warningShown);
      this.playBreakSound = parseBoolean(values, "playBreakSound", this.playBreakSound);
      this.minDurabilityValue = clamp(parseInt(values, "minDurabilityValue", this.minDurabilityValue), 0, 100000);
      this.minDurabilityPercentage = clamp(parseDouble(values, "minDurabilityPercentage", this.minDurabilityPercentage), 0.0, 1.0);
      this.warningBobIntensity = clamp(parseInt(values, "warningBobIntensity", this.warningBobIntensity), 0, 20);
   }

   public void save() {
      if (this.file == null) {
         return;
      }

      try {
         File parent = this.file.getParentFile();
         if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create configuration directory " + parent);
         }

         StringBuilder contents = new StringBuilder("# uku's Armor HUD configuration\n");
         append(contents, "enabled", this.enabled);
         append(contents, "anchor", this.anchor);
         append(contents, "side", this.side);
         append(contents, "offsetX", this.offsetX);
         append(contents, "offsetY", this.offsetY);
         append(contents, "style", this.style);
         append(contents, "orientation", this.orientation);
         append(contents, "widgetShown", this.widgetShown);
         append(contents, "offhandSlotBehavior", this.offhandSlotBehavior);
         append(contents, "durabilityDisplay", this.durabilityDisplay);
         append(contents, "pushBossbars", this.pushBossbars);
         append(contents, "pushStatusEffectIcons", this.pushStatusEffectIcons);
         append(contents, "pushSubtitles", this.pushSubtitles);
         append(contents, "reversed", this.reversed);
         append(contents, "iconsShown", this.iconsShown);
         append(contents, "warningShown", this.warningShown);
         append(contents, "playBreakSound", this.playBreakSound);
         append(contents, "minDurabilityValue", this.minDurabilityValue);
         append(contents, "minDurabilityPercentage", this.minDurabilityPercentage);
         append(contents, "warningBobIntensity", this.warningBobIntensity);
         Files.writeString(this.file.toPath(), contents, StandardCharsets.UTF_8);
      } catch (IOException exception) {
         LOGGER.warn("Could not save Armor HUD configuration {}", this.file, exception);
      }
   }

   private static void append(final StringBuilder contents, final String key, final Object value) {
      contents.append(key).append('=').append(value).append('\n');
   }

   private static boolean parseBoolean(final Map<String, String> values, final String key, final boolean fallback) {
      String value = values.get(key);
      if (value == null) {
         return fallback;
      }
      if (value.equalsIgnoreCase("true")) {
         return true;
      }
      if (value.equalsIgnoreCase("false")) {
         return false;
      }
      LOGGER.warn("Ignoring invalid Armor HUD value for {}: {}", key, value);
      return fallback;
   }

   private static int parseInt(final Map<String, String> values, final String key, final int fallback) {
      String value = values.get(key);
      if (value == null) {
         return fallback;
      }
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException exception) {
         LOGGER.warn("Ignoring invalid Armor HUD value for {}: {}", key, value);
         return fallback;
      }
   }

   private static double parseDouble(final Map<String, String> values, final String key, final double fallback) {
      String value = values.get(key);
      if (value == null) {
         return fallback;
      }
      try {
         double parsed = Double.parseDouble(value);
         if (Double.isFinite(parsed)) {
            return parsed;
         }
      } catch (NumberFormatException ignored) {
      }
      LOGGER.warn("Ignoring invalid Armor HUD value for {}: {}", key, value);
      return fallback;
   }

   private static <T extends Enum<T>> T parseEnum(final Map<String, String> values, final String key, final T fallback, final Class<T> type) {
      String value = values.get(key);
      if (value == null) {
         return fallback;
      }
      try {
         return Enum.valueOf(type, value);
      } catch (IllegalArgumentException exception) {
         LOGGER.warn("Ignoring invalid Armor HUD value for {}: {}", key, value);
         return fallback;
      }
   }

   private static int clamp(final int value, final int min, final int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(final double value, final double min, final double max) {
      return Math.max(min, Math.min(max, value));
   }
}
