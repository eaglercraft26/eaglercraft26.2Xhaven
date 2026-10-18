package me.cominixo.betterf3;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.Util;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class BetterF3Fabric {
   private static final Map<Identifier, ModuleDefinition> BUILT_IN_MODULES = createBuiltInModules();
   private static final Map<Identifier, BetterF3Module> EXTENSION_MODULES = new LinkedHashMap<>();
   private static final Map<Identifier, List<String>> LAST_RENDERED_MODULE_LINES = new LinkedHashMap<>();
   private static final ModConfigFile CONFIG = ModConfigFile.INSTANCE;
   private static float openProgress;
   private static long lastAnimationUpdate;
   private static boolean overlayWasActive;

   private BetterF3Fabric() {
   }

   public static void init(final File gameDirectory) {
      CONFIG.init(gameDirectory);
   }

   public static void registerModule(final BetterF3Module module) {
      java.util.Objects.requireNonNull(module, "module");
      Identifier id = module.id();
      java.util.Objects.requireNonNull(id, "module.id");
      if (BUILT_IN_MODULES.containsKey(id) || EXTENSION_MODULES.putIfAbsent(id, module) != null) {
         throw new IllegalArgumentException("A BetterF3 module is already registered with id " + id);
      }
      CONFIG.setting(id);
   }

   public static boolean replacesVanillaOverlay(final Minecraft minecraft) {
      boolean active = CONFIG.enabled && minecraft.debugEntries.isOverlayVisible();
      if (!active || !overlayWasActive) {
         openProgress = 0.0F;
         lastAnimationUpdate = 0L;
      }
      overlayWasActive = active;
      return active;
   }

   public static Collection<Identifier> entriesToDisplay(final Minecraft minecraft) {
      if (!replacesVanillaOverlay(minecraft)) {
         return minecraft.debugEntries.getCurrentlyEnabled();
      }
      Set<Identifier> sources = new LinkedHashSet<>();
      for (Identifier moduleId : availableModules()) {
         ModConfigFile.ModuleSetting setting = CONFIG.setting(moduleId);
         if (!setting.enabled) {
            continue;
         }
         ModuleDefinition builtin = BUILT_IN_MODULES.get(moduleId);
         if (builtin != null) {
            sources.addAll(setting.sourceOverrides ? identifiers(setting.sources) : builtin.sources);
         }
         if (setting.custom || setting.sourceOverrides) {
            setting.sources.stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull).forEach(sources::add);
         }
      }
      return sources.stream()
         .filter(id -> {
            DebugScreenEntry entry = DebugScreenEntries.getEntry(id);
            return entry != null && entry.category() == DebugEntryCategory.SCREEN_TEXT && entry.isAllowed(minecraft.showOnlyReducedInfo());
         })
         .sorted()
         .toList();
   }

   public static void recordLine(final Map<Identifier, List<String>> sources, final Identifier id, final String line) {
      if (id != null && line != null && !line.isEmpty()) {
         sources.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line);
      }
   }

   public static void recordGroup(final Map<Identifier, List<String>> sources, final Identifier id, final Collection<String> lines) {
      if (id != null) {
         List<String> sourceLines = sources.computeIfAbsent(id, ignored -> new ArrayList<>());
         for (String line : lines) {
            if (line != null && !line.isEmpty()) {
               sourceLines.add(line);
            }
         }
      }
   }

   public static void recordLegacyLines(
      final Map<Identifier, List<String>> sources,
      final List<String> leftLines,
      final List<String> rightLines
   ) {
      Identifier left = Identifier.fromNamespaceAndPath("betterf3", "misc_left");
      Identifier right = Identifier.fromNamespaceAndPath("betterf3", "misc_right");
      recordGroup(sources, left, stripHandledLegacyLines(leftLines, false));
      recordGroup(sources, right, stripHandledLegacyLines(rightLines, true));
   }

   private static List<String> stripHandledLegacyLines(final List<String> lines, final boolean right) {
      List<String> ignoredPrefixes = right
         ? List.of("Java:", "Mem:", "Allocated:", "CPU:", "Display:", "Allocation rate:")
         : List.of(
            "Minecraft",
            "Integrated",
            "C:",
            "E:",
            "P:",
            "Client",
            "ServerChunkCache:",
            "minecraft:",
            "XYZ:",
            "Block:",
            "Chunk:",
            "Facing:",
            "Waiting",
            "SC:",
            "Sounds:",
            "CH",
            "SH",
            "Server Light:",
            "Biome:",
            "Local Difficulty:",
            "Chunks["
         );
      return lines.stream()
         .filter(line -> !line.isBlank())
         .filter(line -> ignoredPrefixes.stream().noneMatch(line::startsWith))
         .filter(line -> !line.contains("tx") || !line.contains("rx"))
         .filter(line -> {
            String[] words = line.split(" ", 3);
            return words.length < 2 || !words[1].equals("fps");
         })
         .toList();
   }

   public static void render(
      final GuiGraphicsExtractor graphics,
      final Font font,
      final Map<Identifier, List<String>> sources,
      final int rightTopOffset
   ) {
      LAST_RENDERED_MODULE_LINES.clear();
      List<Identifier> moduleOrder = CONFIG.orderedModules(availableModules());
      float scale = CONFIG.textScale;
      long now = Util.getMillis();
      if (CONFIG.animations) {
         long elapsed = lastAnimationUpdate == 0L ? 0L : Math.max(0L, now - lastAnimationUpdate);
         openProgress = Math.min(1.0F, openProgress + elapsed / 140.0F);
      } else {
         openProgress = 1.0F;
      }
      lastAnimationUpdate = now;
      graphics.pose().pushMatrix();
      graphics.pose().scale(scale, scale);
      try {
         int guiWidth = (int)(graphics.guiWidth() / scale);
         int maxWidth = Math.max(20, guiWidth / 2 - 8);
         int animatedOffset = CONFIG.animations ? Math.round((1.0F - openProgress) * 18.0F) : 0;
         int[] yPositions = {2 - animatedOffset, 2 + (int)(rightTopOffset / scale) - animatedOffset};
         for (Identifier moduleId : moduleOrder) {
            ModConfigFile.ModuleSetting setting = CONFIG.setting(moduleId);
            if (!setting.enabled) {
               continue;
            }
            ModuleDefinition builtin = BUILT_IN_MODULES.get(moduleId);
            List<String> moduleLines = collectLines(moduleId, builtin, setting, sources);
            LAST_RENDERED_MODULE_LINES.put(moduleId, List.copyOf(moduleLines));
            if (moduleLines.isEmpty()) {
               continue;
            }
            int side = setting.right ? 1 : 0;
            int y = yPositions[side];
            if (CONFIG.showModuleNames || setting.custom) {
               String title = setting.custom && !setting.name.isBlank()
                  ? setting.name
                  : moduleName(moduleId);
               y = drawLine(graphics, font, title, y, maxWidth, guiWidth, setting.right, withAlpha(setting.color, openProgress), true);
            }
            for (int i = 0; i < moduleLines.size(); i++) {
               if (!setting.hiddenLines.contains(i)) {
                  int moduleColor = lineColor(moduleId, setting.color, i);
                  y = drawLine(graphics, font, moduleLines.get(i), y, maxWidth, guiWidth, setting.right, withAlpha(moduleColor, openProgress), false);
               }
            }
            yPositions[side] = y + CONFIG.moduleSpacing;
         }
      } finally {
         graphics.pose().popMatrix();
      }
   }

   private static List<String> collectLines(
      final Identifier moduleId,
      final ModuleDefinition builtin,
      final ModConfigFile.ModuleSetting setting,
      final Map<Identifier, List<String>> sources
   ) {
      ArrayList<String> lines = new ArrayList<>();
      BetterF3Module extension = EXTENSION_MODULES.get(moduleId);
      if (extension != null) {
         lines.addAll(List.copyOf(extension.getLines(Minecraft.getInstance())));
      }
      if (builtin != null) {
         switch (moduleId.getPath()) {
            case "fps" -> lines.addAll(collectFpsLines());
            case "coords" -> lines.addAll(collectCoordinateLines());
            case "chunks" -> lines.addAll(collectChunkLines());
            case "entity" -> lines.addAll(collectEntityLines());
            case "target" -> lines.addAll(collectTargetLines());
            case "sound" -> lines.addAll(collectSoundLines());
            case "system" -> lines.addAll(collectSystemLines());
            case "minecraft" -> lines.add("Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name());
            case "graphics" -> {
               lines.addAll(collectGraphicsLines());
            }
            case "server" -> {
               lines.addAll(collectServerLines());
            }
            case "location" -> {
               lines.addAll(collectLocationLines());
            }
            default -> {
               if (moduleId.getPath().equals("misc_left") || moduleId.getPath().equals("misc_right")) {
                  lines.addAll(sources.getOrDefault(moduleId, List.of()));
               } else if (!List.of("fps", "coords", "chunks", "entity", "target", "sound", "system", "minecraft").contains(moduleId.getPath())) {
                  builtin.sources.forEach(source -> lines.addAll(sources.getOrDefault(source, List.of())));
               }
            }
         }
         if (!setting.sourceOverrides && List.of("chunks", "entity", "graphics", "location").contains(moduleId.getPath())) {
            builtin.sources.forEach(source -> lines.addAll(sources.getOrDefault(source, List.of())));
         }
         if (moduleId.getPath().equals("help")) {
            lines.add("F3 + Q: show debug shortcuts");
            lines.add("F3 + A: reload chunks");
            lines.add("F3 + T: reload resources");
         }
      }
      if (setting.custom || setting.sourceOverrides) {
         for (String sourceName : setting.sources) {
            Identifier source = Identifier.tryParse(sourceName);
            if (source != null) {
               lines.addAll(sources.getOrDefault(source, List.of()));
            }
         }
      }
      lines.removeIf(String::isBlank);
      return lines;
   }

   private static List<String> collectFpsLines() {
      Minecraft minecraft = Minecraft.getInstance();
      int fps = minecraft.getFps();
      int limit = minecraft.options.framerateLimit().get();
      String limitText = limit == 260 ? "Unlimited" : Integer.toString(limit);
      String vsync = minecraft.options.enableVsync().get() ? "VSync on" : "";
      return List.of(String.format(Locale.ROOT, "%d fps (limit %s) %s", fps, limitText, vsync).trim());
   }

   private static List<String> collectCoordinateLines() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.getCameraEntity() == null) {
         return List.of();
      }
      var camera = minecraft.getCameraEntity();
      var blockPos = camera.blockPosition();
      String position = String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", camera.getX(), camera.getY(), camera.getZ());
      String block = String.format(Locale.ROOT, "Block: %d / %d / %d", blockPos.getX(), blockPos.getY(), blockPos.getZ());
      String chunk = String.format(
         Locale.ROOT,
         "Chunk: %d / %d / %d [%d / %d / %d]",
         blockPos.getX() >> 4,
         blockPos.getY() >> 4,
         blockPos.getZ() >> 4,
         Math.floorMod(blockPos.getX(), 16),
         Math.floorMod(blockPos.getY(), 16),
         Math.floorMod(blockPos.getZ(), 16)
      );
      var velocity = camera.getDeltaMovement();
      String speed = String.format(
         Locale.ROOT,
         "Velocity: %.3f / %.3f / %.3f",
         velocity.x() * 20.0,
         velocity.y() * 20.0,
         velocity.z() * 20.0
      );
      return List.of(position, block, chunk, speed);
   }

   private static List<String> collectGraphicsLines() {
      Minecraft minecraft = Minecraft.getInstance();
      var options = minecraft.options;
      String preset = options.graphicsPreset().get().name().toLowerCase(Locale.ROOT);
      preset = Character.toUpperCase(preset.charAt(0)) + preset.substring(1);
      String clouds = switch (options.cloudStatus().get()) {
         case OFF -> "Off";
         case FAST -> "Fast";
         case FANCY -> "Fancy";
      };
      ArrayList<String> lines = new ArrayList<>();
      lines.add("Render distance: " + options.renderDistance().get() + " chunks");
      lines.add("Graphics: " + preset);
      lines.add("Clouds: " + clouds);
      lines.add("Biome blend: " + options.biomeBlendRadius().get());
      Identifier postEffect = minecraft.gameRenderer.currentPostEffect();
      if (postEffect != null) {
         lines.add("Shader: " + postEffect);
      }
      return lines;
   }

   private static List<String> collectServerLines() {
      Minecraft minecraft = Minecraft.getInstance();
      ArrayList<String> lines = new ArrayList<>();
      var connection = minecraft.getConnection();
      if (connection != null) {
         var network = connection.getConnection();
         lines.add("Packets sent: " + Math.round(network.getAverageSentPackets()));
         lines.add("Packets received: " + Math.round(network.getAverageReceivedPackets()));
         String brand = connection.serverBrand();
         if (brand != null && !brand.isBlank()) {
            lines.add("Server brand: " + brand);
         }
         lines.add("Online players: " + connection.getOnlinePlayers().size());
         if (minecraft.player != null) {
            var playerInfo = connection.getPlayerInfo(minecraft.player.getUUID());
            if (playerInfo != null) {
               lines.add("Latency: " + playerInfo.getLatency() + " ms");
            }
         }
      }
      var integratedServer = minecraft.getSingleplayerServer();
      if (integratedServer != null) {
         lines.add(String.format(Locale.ROOT, "Server tick: %.1f ms", integratedServer.getCurrentSmoothedTickTime()));
      }
      if (minecraft.level != null) {
         var tickRateManager = minecraft.level.tickRateManager();
         String status = tickRateManager.isFrozen()
            ? "Frozen"
            : tickRateManager.isSteppingForward() ? "Stepping" : "Normal";
         if (integratedServer != null && integratedServer.tickRateManager().isSprinting()) {
            status = "Sprinting";
         }
         lines.add("Tick status: " + status);
      }
      return lines;
   }

   private static List<String> collectLocationLines() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.getCameraEntity() == null) {
         return List.of();
      }
      var camera = minecraft.getCameraEntity();
      long dayTime = minecraft.level.getOverworldClockTime();
      ArrayList<String> lines = new ArrayList<>(List.of(
         "Dimension: " + minecraft.level.dimension().identifier(),
         "Facing: " + camera.getDirection(),
         String.format(Locale.ROOT, "Rotation: %.1f / %.1f", camera.getYRot(), camera.getXRot()),
         String.format(Locale.ROOT, "Time: %d (day %d)", Math.floorMod(dayTime, 24000L), Math.floorDiv(dayTime, 24000L))
      ));
      var position = camera.blockPosition();
      var biome = minecraft.level.getBiome(position);
      var biomeKey = biome.unwrapKey();
      biomeKey.ifPresent(key -> lines.add("Biome: " + key.identifier()));
      lines.add("Light: " + minecraft.level.getMaxLocalRawBrightness(position));
      lines.add("Sky light: " + minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, position));
      lines.add("Block light: " + minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, position));
      return lines;
   }

   private static List<String> collectChunkLines() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null) {
         return List.of();
      }
      ArrayList<String> lines = new ArrayList<>();
      String sectionStats = minecraft.levelExtractor.sectionStatistics();
      if (sectionStats != null && !sectionStats.isBlank()) {
         lines.add(sectionStats);
      }
      lines.add("Client chunks: " + minecraft.level.getChunkSource().getLoadedChunksCount());
      var dispatcher = minecraft.levelRenderer.sectionRenderDispatcher();
      if (dispatcher != null) {
         lines.add("Chunk builds: " + dispatcher.getCompileQueueSize() + " pending, " + dispatcher.getFreeBufferCount() + " buffers free");
      }
      return lines;
   }

   private static List<String> collectEntityLines() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null) {
         return List.of();
      }
      ArrayList<String> lines = new ArrayList<>();
      lines.add("Entities: " + minecraft.level.getEntityCount());
      lines.add("Particles: " + minecraft.particleEngine.countParticles());
      String entityStats = minecraft.levelExtractor.entityStatistics();
      if (entityStats != null && !entityStats.isBlank()) {
         lines.add(entityStats);
      }
      return lines;
   }

   private static List<String> collectTargetLines() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null) {
         return List.of();
      }
      ArrayList<String> lines = new ArrayList<>();
      var camera = minecraft.getCameraEntity();
      HitResult blockResult = camera == null ? null : camera.pick(20.0D, 0.0F, false);
      if (blockResult instanceof BlockHitResult blockHit) {
         BlockPos pos = blockHit.getBlockPos();
         var state = minecraft.level.getBlockState(pos);
         lines.add("Block " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
         lines.add(state.typeHolder().getRegisteredName());
         state.getValues().forEach(value -> lines.add(value.property().getName() + ": " + value.valueName()));
         state.tags().map(tag -> "#" + tag.location()).forEach(lines::add);
      }
      HitResult fluidResult = camera == null ? null : camera.pick(20.0D, 0.0F, true);
      if (fluidResult instanceof BlockHitResult fluidHit) {
         BlockPos pos = fluidHit.getBlockPos();
         var fluid = minecraft.level.getFluidState(pos);
         if (!fluid.isEmpty()) {
            lines.add("Fluid " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ": " + BuiltInRegistries.FLUID.getKey(fluid.getType()));
            fluid.getValues().forEach(value -> lines.add(value.property().getName() + ": " + value.valueName()));
            fluid.tags().map(tag -> "#" + tag.location()).forEach(lines::add);
         }
      }
      if (minecraft.crosshairPickEntity != null) {
         var target = minecraft.crosshairPickEntity;
         lines.add("Entity: " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
         lines.add("Name: " + target.getDisplayName().getString());
         lines.add(String.format(Locale.ROOT, "Position: %.2f / %.2f / %.2f", target.getX(), target.getY(), target.getZ()));
         if (minecraft.player != null) {
            lines.add(String.format(Locale.ROOT, "Distance: %.2f", minecraft.player.distanceTo(target)));
         }
         if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            lines.add(String.format(Locale.ROOT, "Health: %.1f / %.1f", living.getHealth(), living.getMaxHealth()));
         }
      }
      return lines;
   }

   private static List<String> collectSoundLines() {
      Minecraft minecraft = Minecraft.getInstance();
      String stats = minecraft.getSoundManager().getChannelDebugString();
      java.util.regex.Matcher counts = java.util.regex.Pattern.compile("Sounds: (\\d+)/(\\d+) \\+ (\\d+)/(\\d+)").matcher(stats);
      ArrayList<String> lines = new ArrayList<>();
      if (counts.find()) {
         lines.add("Active sounds: " + counts.group(1) + "/" + counts.group(2));
         lines.add("Streaming sounds: " + counts.group(3) + "/" + counts.group(4));
      }
      if (minecraft.player != null) {
         lines.add("Ambient mood: " + Math.round(minecraft.player.getCurrentMood() * 100.0F) + "%");
      }
      return lines;
   }

   private static List<String> collectSystemLines() {
      Minecraft minecraft = Minecraft.getInstance();
      long maximum = Runtime.getRuntime().maxMemory();
      long allocated = Runtime.getRuntime().totalMemory();
      long used = allocated - Runtime.getRuntime().freeMemory();
      ArrayList<String> lines = new ArrayList<>();
      var webHeapStats = net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.webHeapStats;
      long[] heap = webHeapStats == null ? null : webHeapStats.get();
      if (heap != null && heap.length >= 3 && heap[2] > 0L) {
         lines.add(String.format(Locale.ROOT, "Memory: %d%% (%d/%d MiB)", heap[0] * 100L / heap[2], heap[0] / 1048576L, heap[2] / 1048576L));
         lines.add(String.format(Locale.ROOT, "Allocated: %d MiB", heap[1] / 1048576L));
      } else if (maximum > 0L) {
         lines.add(String.format(Locale.ROOT, "Memory: %d%% (%d/%d MiB)", used * 100L / maximum, used / 1048576L, maximum / 1048576L));
         lines.add(String.format(Locale.ROOT, "Allocated: %d MiB", allocated / 1048576L));
      }
      var clientAllocationStats = net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.webClientAllocationStats;
      long[] allocations = clientAllocationStats == null ? null : clientAllocationStats.get();
      if (allocations != null && allocations.length >= 3) {
         lines.add(String.format(Locale.ROOT, "Client allocations: %d MiB live / %d MiB peak (%d)", allocations[0] / 1048576L, allocations[1] / 1048576L, allocations[2]));
      }
      var rendererMemoryStats = net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.webRendererMemoryStats;
      long[] rendererMemory = rendererMemoryStats == null ? null : rendererMemoryStats.get();
      if (rendererMemory != null && rendererMemory.length >= 4) {
         lines.add(String.format(Locale.ROOT, "Terrain GPU: %d / %d MiB (%d allocations)", rendererMemory[0] / 1048576L, rendererMemory[1] / 1048576L, rendererMemory[3]));
      }
      lines.add("CPU: " + GLX._getCpuInfo());
      var device = RenderSystem.getDevice().getDeviceInfo();
      lines.add(String.format(Locale.ROOT, "Display: %d x %d (%s)", minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), device.vendorName()));
      lines.add("GPU: " + device.name());
      lines.add("Renderer: " + device.backendName());
      double gpuUtilization = minecraft.getGpuUtilization();
      if (gpuUtilization > 0.0D) {
         lines.add(String.format(Locale.ROOT, "GPU utilization: %.0f%%", Math.min(gpuUtilization, 100.0D)));
      }
      return lines;
   }

   private static int lineColor(final Identifier moduleId, final int configuredColor, final int lineIndex) {
      if (moduleId.getPath().equals("fps") && lineIndex == 0 && configuredColor == 0xFFE0E0E0) {
         int fps = Minecraft.getInstance().getFps();
         return fps >= 60 ? 0xFF55FF55 : fps >= 20 ? 0xFFFFFF55 : 0xFFFF5555;
      }
      return configuredColor;
   }

   private static int drawLine(
      final GuiGraphicsExtractor graphics,
      final Font font,
      final String originalLine,
      final int y,
      final int maxWidth,
      final int guiWidth,
      final boolean right,
      final int color,
      final boolean heading
   ) {
      String line = font.width(originalLine) <= maxWidth
         ? originalLine
         : font.plainSubstrByWidth(originalLine, Math.max(0, maxWidth - font.width("..."))) + "...";
      int width = font.width(line);
      int x = right ? guiWidth - 2 - width : 2;
      graphics.fill(x - 2, y - 1, x + width + 2, y + CONFIG.lineSpacing - 1, withAlpha(CONFIG.backgroundColor, openProgress));
      Component text = Component.literal(line);
      graphics.text(font, text, x, y, color, CONFIG.textShadow);
      return y + CONFIG.lineSpacing;
   }

   private static int withAlpha(final int color, final float opacity) {
      int alpha = color >>> 24;
      int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alpha * opacity)));
      return adjustedAlpha << 24 | color & 0x00FFFFFF;
   }

   public static void openConfigScreen() {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.gui.setScreen(new BetterF3ConfigScreen(minecraft.gui.screen()));
   }

   public static List<Identifier> availableModules() {
      ArrayList<Identifier> ids = new ArrayList<>(BUILT_IN_MODULES.keySet());
      ids.addAll(EXTENSION_MODULES.keySet());
      CONFIG.customModuleIds().forEach(id -> {
         if (!ids.contains(id)) {
            ids.add(id);
         }
      });
      return CONFIG.orderedModules(ids);
   }

   public static List<Identifier> availableSources() {
      return DebugScreenEntries.allEntries().entrySet().stream()
         .filter(entry -> entry.getValue().category() == DebugEntryCategory.SCREEN_TEXT)
         .map(Map.Entry::getKey)
         .sorted()
         .toList();
   }

   public static List<Identifier> builtInModuleIds() {
      return List.copyOf(BUILT_IN_MODULES.keySet());
   }

   public static List<String> defaultSources(final Identifier moduleId) {
      ModuleDefinition module = BUILT_IN_MODULES.get(moduleId);
      return module == null ? List.of() : module.sources.stream().map(Identifier::toString).toList();
   }

   public static boolean defaultRight(final Identifier moduleId) {
      BetterF3Module extension = EXTENSION_MODULES.get(moduleId);
      if (extension != null) {
         return extension.defaultRight();
      }
      ModuleDefinition module = BUILT_IN_MODULES.get(moduleId);
      return module != null && module.right;
   }

   public static String moduleName(final Identifier moduleId) {
      BetterF3Module extension = EXTENSION_MODULES.get(moduleId);
      if (extension != null) {
         return extension.displayName();
      }
      ModuleDefinition definition = BUILT_IN_MODULES.get(moduleId);
      return definition == null ? moduleId.getPath() : definition.name;
   }

   public static boolean hasNativeData(final Identifier moduleId) {
      return moduleId.getNamespace().equals("betterf3")
         && (moduleId.getPath().equals("fps") || moduleId.getPath().equals("coords"));
   }

   private static List<Identifier> identifiers(final List<String> sourceNames) {
      return sourceNames.stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull).toList();
   }

   public static List<String> latestLines(final Identifier id) {
      return LAST_RENDERED_MODULE_LINES.getOrDefault(id, List.of());
   }

   public static ModConfigFile config() {
      return CONFIG;
   }

   private static Map<Identifier, ModuleDefinition> createBuiltInModules() {
      LinkedHashMap<Identifier, ModuleDefinition> modules = new LinkedHashMap<>();
      addModule(modules, "minecraft", "Minecraft", false, "game_version");
      addModule(modules, "fps", "FPS", false);
      addModule(modules, "graphics", "Graphics", false, "post_effect");
      addModule(modules, "server", "Server", false);
      addModule(modules, "coords", "Coordinates", false);
      addModule(modules, "chunks", "Chunks", false, "chunk_generation_stats");
      addModule(modules, "location", "Location", false, "biome", "light_levels", "heightmap", "local_difficulty");
      addModule(modules, "entity", "Entities", false, "entity_spawn_counts");
      addModule(modules, "sound", "Sound", false, "sound_mood", "sound_cache");
      addModule(modules, "help", "Help", false);
      addModule(modules, "misc_left", "Additional left", false);
      addModule(modules, "system", "System", true, "memory", "detailed_memory", "system_specs", "gpu_utilization");
      addModule(modules, "misc_right", "Additional right", true);
      addModule(modules, "target", "Target", true);
      return Collections.unmodifiableMap(modules);
   }

   private static void addModule(
      final Map<Identifier, ModuleDefinition> modules,
      final String id,
      final String name,
      final boolean right,
      final String... sourceNames
   ) {
      Identifier moduleId = Identifier.fromNamespaceAndPath("betterf3", id);
      List<Identifier> sources = new ArrayList<>(sourceNames.length);
      for (String sourceName : sourceNames) {
         sources.add(Identifier.withDefaultNamespace(sourceName));
      }
      modules.put(moduleId, new ModuleDefinition(name, right, List.copyOf(sources)));
   }

   private record ModuleDefinition(String name, boolean right, List<Identifier> sources) {
   }
}
