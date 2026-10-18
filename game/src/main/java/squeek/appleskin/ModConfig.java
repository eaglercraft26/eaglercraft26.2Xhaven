package squeek.appleskin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Same options and defaults as the original AppleSkin config. The original persists with Jankson
 * (JSON5) in the Fabric config dir; here a plain "key = value" file in the game directory's
 * config folder is used instead (no Fabric/Jankson dependency).
 */
public class ModConfig {
    public static ModConfig INSTANCE;
    public static File PATH;

    // If true, shows the hunger and saturation values of food in its tooltip while holding SHIFT
    public boolean showFoodValuesInTooltip = true;
    // If true, shows the hunger and saturation values of food in its tooltip automatically (without needing to hold SHIFT)
    public boolean showFoodValuesInTooltipAlways = true;
    // If true, shows your current saturation level overlayed on the hunger bar
    public boolean showSaturationHudOverlay = true;
    // If true, shows the hunger (and saturation if showSaturationHudOverlay is true) that would be restored by food you are currently holding
    public boolean showFoodValuesHudOverlay = true;
    // If true, enables the hunger/saturation/health overlays for food in your off-hand
    public boolean showFoodValuesHudOverlayWhenOffhand = true;
    // If true, shows your food exhaustion as a progress bar behind the hunger bar
    public boolean showFoodExhaustionHudUnderlay = true;
    // If true, shows estimated health restored by food on the health bar
    public boolean showFoodHealthHudOverlay = true;
    // If true, health/hunger overlay will shake to match Minecraft's icon animations
    public boolean showVanillaAnimationsOverlay = true;
    // Alpha value of the flashing icons at their most visible point (1.0 = fully opaque, 0.0 = fully transparent)
    public float maxHudOverlayFlashAlpha = 0.65f;

    public static void init(File gameDirectory) {
        INSTANCE = new ModConfig();
        try {
            PATH = new File(new File(gameDirectory, "config"), "appleskin.properties");
            INSTANCE.load();
        } catch (Throwable e) {
            AppleSkin.LOGGER.warn("AppleSkin: could not load config, using defaults", e);
        }
    }

    public void save() throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("showFoodValuesInTooltip", String.valueOf(showFoodValuesInTooltip));
        m.put("showFoodValuesInTooltipAlways", String.valueOf(showFoodValuesInTooltipAlways));
        m.put("showSaturationHudOverlay", String.valueOf(showSaturationHudOverlay));
        m.put("showFoodValuesHudOverlay", String.valueOf(showFoodValuesHudOverlay));
        m.put("showFoodValuesHudOverlayWhenOffhand", String.valueOf(showFoodValuesHudOverlayWhenOffhand));
        m.put("showFoodExhaustionHudUnderlay", String.valueOf(showFoodExhaustionHudUnderlay));
        m.put("showFoodHealthHudOverlay", String.valueOf(showFoodHealthHudOverlay));
        m.put("showVanillaAnimationsOverlay", String.valueOf(showVanillaAnimationsOverlay));
        m.put("maxHudOverlayFlashAlpha", String.valueOf(maxHudOverlayFlashAlpha));
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(k).append(" = ").append(v).append('\n'));
        File parent = PATH.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(PATH.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void load() throws IOException {
        if (!PATH.exists()) {
            this.save();
            return;
        }
        for (String line : Files.readAllLines(PATH.toPath(), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq < 0 || line.trim().startsWith("#")) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            try {
                switch (k) {
                    case "showFoodValuesInTooltip" -> showFoodValuesInTooltip = Boolean.parseBoolean(v);
                    case "showFoodValuesInTooltipAlways" -> showFoodValuesInTooltipAlways = Boolean.parseBoolean(v);
                    case "showSaturationHudOverlay" -> showSaturationHudOverlay = Boolean.parseBoolean(v);
                    case "showFoodValuesHudOverlay" -> showFoodValuesHudOverlay = Boolean.parseBoolean(v);
                    case "showFoodValuesHudOverlayWhenOffhand" -> showFoodValuesHudOverlayWhenOffhand = Boolean.parseBoolean(v);
                    case "showFoodExhaustionHudUnderlay" -> showFoodExhaustionHudUnderlay = Boolean.parseBoolean(v);
                    case "showFoodHealthHudOverlay" -> showFoodHealthHudOverlay = Boolean.parseBoolean(v);
                    case "showVanillaAnimationsOverlay" -> showVanillaAnimationsOverlay = Boolean.parseBoolean(v);
                    case "maxHudOverlayFlashAlpha" -> maxHudOverlayFlashAlpha = Float.parseFloat(v);
                    default -> { }
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
