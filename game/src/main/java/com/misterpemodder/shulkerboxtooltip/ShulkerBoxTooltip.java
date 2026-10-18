package com.misterpemodder.shulkerboxtooltip;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SeededContainerLoot;

public final class ShulkerBoxTooltip {
   public static final String CONFIG_FILE = "shulkerboxtooltip.properties";
   public static final Config CONFIG = new Config();
   private static boolean initialized;
   private static final Map<String, Integer> DEFAULT_COLORS = createDefaultColors();
   private static final Map<String, Integer> REGISTERED_COLORS = new LinkedHashMap<>();
   private static final Map<Identifier, PreviewProvider> PREVIEW_PROVIDERS = new LinkedHashMap<>();
   private static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath("shulkerboxtooltip", "shulker_box_tooltip");

   private ShulkerBoxTooltip() {
   }

   public static void registerPreviewProvider(final Identifier id, final PreviewProvider provider) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(provider, "provider");
      if (PREVIEW_PROVIDERS.putIfAbsent(id, provider) != null) {
         throw new IllegalArgumentException("A Shulker Box Tooltip preview provider is already registered with id " + id);
      }
   }

   public static void registerColor(final Identifier category, final String colorId, final int rgb) {
      Objects.requireNonNull(category, "category");
      if (colorId == null || !colorId.matches("[a-z0-9_.-]+") || rgb < 0 || rgb > 0xFFFFFF) {
         throw new IllegalArgumentException("Invalid Shulker Box Tooltip color key or RGB value");
      }
      String key = category + "/" + colorId;
      boolean overridesBuiltIn = category.toString().equals("shulkerboxtooltip:default") && DEFAULT_COLORS.containsKey(colorId);
      if (overridesBuiltIn || REGISTERED_COLORS.putIfAbsent(key, rgb) != null) {
         throw new IllegalArgumentException("A Shulker Box Tooltip color is already registered: " + key);
      }
   }

   private static Map.Entry<Identifier, PreviewProvider> providerFor(final ItemStack stack) {
      return PREVIEW_PROVIDERS.entrySet().stream()
         .sorted(Comparator.comparingInt((Map.Entry<Identifier, PreviewProvider> entry) -> entry.getValue().priority()).reversed())
         .filter(entry -> entry.getValue().supports(stack))
         .findFirst()
         .orElse(null);
   }

   private static boolean isSupportedContainer(final ItemStack stack) {
      String path = itemPath(stack);
      return path.endsWith("_chest")
         || path.equals("barrel")
         || path.equals("furnace")
         || path.equals("blast_furnace")
         || path.equals("smoker")
         || path.equals("dropper")
         || path.equals("dispenser")
         || path.equals("hopper")
         || path.equals("brewing_stand")
         || path.equals("chiseled_bookshelf")
         || path.equals("decorated_pot")
         || path.endsWith("_shelf");
   }

   private static boolean canUseLootTables(
      final ItemStack stack,
      final Map.Entry<Identifier, PreviewProvider> provider
   ) {
      if (provider != null && provider.getValue().supportsLootTables()) {
         return true;
      }
      String path = itemPath(stack);
      return stack.is(ItemTags.SHULKER_BOXES)
         || path.endsWith("_chest")
         || path.equals("barrel")
         || path.equals("dispenser")
         || path.equals("dropper")
         || path.equals("hopper");
   }

   private static ContainerLayout containerLayout(final ItemStack stack, final int itemCount) {
      return switch (itemPath(stack)) {
         case "furnace", "blast_furnace", "smoker" -> new ContainerLayout(3, 3, false);
         case "dropper", "dispenser" -> new ContainerLayout(9, 3, false);
         case "hopper", "brewing_stand" -> new ContainerLayout(5, 5, false);
         case "chiseled_bookshelf" -> new ContainerLayout(6, 3, false);
         case "decorated_pot" -> new ContainerLayout(1, 1, false);
         default -> isSupportedContainer(stack)
            ? new ContainerLayout(27, 9, false)
            : new ContainerLayout(Math.max(27, itemCount), CONFIG.maxRowSize, true);
      };
   }

   private static String itemPath(final ItemStack stack) {
      return stack.getItem().builtInRegistryHolder().unwrapKey().map(key -> key.identifier().getPath()).orElse("");
   }

   public static void init(final File gameDirectory) {
      if (!initialized) {
         CONFIG.load(gameDirectory);
         initialized = true;
      }
   }

   public static Optional<TooltipComponent> getTooltipImage(final ItemStack stack) {
      if (!CONFIG.enabled || stack.isEmpty() || !overrideBoolean(stack, "should_display", true)) {
         return Optional.empty();
      }

      boolean shulkerBox = stack.is(ItemTags.SHULKER_BOXES);
      BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
      ItemContainerContents container = stack.get(DataComponents.CONTAINER);
      Map.Entry<Identifier, PreviewProvider> selectedProvider = providerFor(stack);
      PreviewData providedPreview = selectedProvider == null ? null : Objects.requireNonNull(
         selectedProvider.getValue().createPreview(stack),
         "Preview provider " + selectedProvider.getKey() + " returned null preview data"
      );
      List<ItemStack> items;
      int activeSlots;
      int selectedSlot = -1;
      boolean isBundle = false;
      int color = -1;

      if (providedPreview != null) {
         items = providedPreview.items();
         activeSlots = providedPreview.activeSlotCount();
         selectedSlot = providedPreview.selectedSlot();
         isBundle = providedPreview.bundle();
         color = CONFIG.coloredPreview ? providedPreview.windowColor() : -1;
      } else if (stack.is(Items.ENDER_CHEST)) {
         items = getEnderChestContents();
         if (items.isEmpty()) {
            return Optional.empty();
         }
         activeSlots = items.size();
         items = padInventory(items, Math.max(27, activeSlots));
         color = CONFIG.coloredPreview ? CONFIG.paletteColor("ender_chest") : -1;
      } else if (isLecternBook(stack)) {
         ItemStack book = readLecternBook(stack);
         if (book.isEmpty()) {
            return Optional.empty();
         }
         items = padInventory(List.of(book), 1);
         activeSlots = 1;
         color = CONFIG.coloredPreview ? CONFIG.paletteColor("default") : -1;
      } else if (bundle != null && !bundle.isEmpty() && CONFIG.bundleTheme != 1) {
         List<ItemStack> bundleItems = bundle.itemCopyStream().toList();
         activeSlots = bundleItems.size();
         int firstActiveSlot = Math.max(0, 64 - activeSlots);
         items = padInventoryAt(bundleItems, 64, firstActiveSlot);
         selectedSlot = bundle.getSelectedItemIndex() < 0 ? -1 : firstActiveSlot + bundle.getSelectedItemIndex();
         isBundle = true;
         color = CONFIG.bundleColor(stack);
      } else if (container != null && (shulkerBox || CONFIG.genericContainerPreview || isSupportedContainer(stack))) {
         if (canUseLootTables(stack, selectedProvider) && hasUnresolvedLoot(stack)) {
            return Optional.empty();
         }
         items = container.allItemsCopyStream().toList();
         ContainerLayout layout = containerLayout(stack, items.size());
         activeSlots = layout.inventorySize();
         if (layout.generic()) {
            activeSlots = Math.max(activeSlots, items.size());
         }
         items = resizeInventory(items, activeSlots);
         int boxColor = shulkerBox
            ? getBoxColor(stack)
            : layout.generic() ? CONFIG.paletteColor("generic_container") : CONFIG.paletteColor("default");
         color = CONFIG.coloredPreview ? boxColor : -1;
      } else {
         return Optional.empty();
      }

      if (CONFIG.coloredPreview) {
         color = overrideWindowColor(stack, color);
      }
      Identifier texture = getItemOverrides(stack)
         .flatMap(tag -> tag.getString("texture"))
         .map(Identifier::tryParse)
         .filter(java.util.Objects::nonNull)
         .orElse(providedPreview != null ? providedPreview.texture() : DEFAULT_TEXTURE);
      int inventorySize = clamp(overrideInt(stack, "inventory_max_size", providedPreview == null ? items.size() : providedPreview.inventorySize()), 1, 256);
      items = resizeInventory(items, inventorySize);
      activeSlots = clamp(overrideInt(stack, "active_slot_count", activeSlots), 0, inventorySize);
      selectedSlot = clamp(overrideInt(stack, "selected_slot", selectedSlot), -1, inventorySize - 1);
      int defaultMaxRowSize = providedPreview != null
         ? providedPreview.maxRowSize()
         : stack.has(DataComponents.CONTAINER) ? containerLayout(stack, items.size()).maxRowSize() : CONFIG.maxRowSize;
      int maxRowSize = clamp(overrideInt(stack, "max_row_size", defaultMaxRowSize), 1, 9);
      int compactMaxRowSize = clamp(
         overrideInt(stack, "compact_max_row_size", providedPreview == null ? maxRowSize : providedPreview.compactMaxRowSize()),
         1,
         9
      );
      boolean fullPreviewAvailable = CONFIG.fullPreviewEnabled && overrideBoolean(stack, "full_preview_available", true);
      boolean previewKey = CONFIG.alwaysOn || isKeyPressed(CONFIG.previewKey);
      boolean fullKey = fullPreviewAvailable && isKeyPressed(CONFIG.fullPreviewKey);
      boolean fullPreview = previewKey && (CONFIG.swapModes ? !fullKey : fullKey);
      boolean preview = previewKey && (CONFIG.swapModes ? fullKey || !fullPreviewAvailable : true);
      if (!preview) {
         return Optional.empty();
      }
      if (!fullPreview && items.stream().allMatch(ItemStack::isEmpty)) {
         return Optional.empty();
      }

      return Optional.of(new net.minecraft.world.inventory.tooltip.ShulkerBoxTooltip(
         items,
         activeSlots,
         selectedSlot,
         fullPreview,
         isBundle,
         color,
         texture,
         CONFIG.theme,
         CONFIG.bundleTheme,
         CONFIG.position,
         maxRowSize,
         compactMaxRowSize,
         CONFIG.shortItemCounts,
         CONFIG.compactPreviewOrder,
         CONFIG.mergeStrategy
      ));
   }

   private static boolean hasUnresolvedLoot(final ItemStack stack) {
      return stack.has(DataComponents.CONTAINER_LOOT);
   }

   private static boolean isLecternBook(final ItemStack stack) {
      TypedEntityData<?> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
      return data != null && data.contains("Book");
   }

   private static ItemStack readLecternBook(final ItemStack stack) {
      TypedEntityData<?> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
      Minecraft minecraft = Minecraft.getInstance();
      if (data == null || minecraft.level == null) {
         return ItemStack.EMPTY;
      }
      return data.getUnsafe()
         .getCompound("Book")
         .flatMap(tag -> ItemStack.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).result())
         .orElse(ItemStack.EMPTY);
   }

   private static List<ItemStack> getEnderChestContents() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null) {
         return List.of();
      }
      ServerPlayer integratedPlayer = minecraft.getSingleplayerServer() == null
         ? null
         : minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
      net.minecraft.world.inventory.PlayerEnderChestContainer inventory = integratedPlayer != null
         ? integratedPlayer.getEnderChestInventory()
         : minecraft.player.getEnderChestInventory();
      ArrayList<ItemStack> items = new ArrayList<>(inventory.getContainerSize());
      for (int i = 0; i < inventory.getContainerSize(); i++) {
         items.add(inventory.getItem(i).copy());
      }
      return items.stream().allMatch(ItemStack::isEmpty) ? List.of() : List.copyOf(items);
   }

   private static List<ItemStack> padInventory(final List<ItemStack> contents, final int size) {
      ArrayList<ItemStack> slots = new ArrayList<>(size);
      slots.addAll(contents);
      while (slots.size() < size) {
         slots.add(ItemStack.EMPTY);
      }
      return List.copyOf(slots);
   }

   private static List<ItemStack> padInventoryAt(final List<ItemStack> contents, final int size, final int firstSlot) {
      ArrayList<ItemStack> slots = new ArrayList<>(size);
      for (int slot = 0; slot < size; slot++) {
         int itemIndex = slot - firstSlot;
         slots.add(itemIndex >= 0 && itemIndex < contents.size() ? contents.get(itemIndex) : ItemStack.EMPTY);
      }
      return List.copyOf(slots);
   }

   private static List<ItemStack> resizeInventory(final List<ItemStack> items, final int size) {
      if (items.size() >= size) {
         return List.copyOf(items.subList(0, size));
      }
      return padInventory(items, size);
   }

   private static Optional<CompoundTag> getItemOverrides(final ItemStack stack) {
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      return customData == null ? Optional.empty() : customData.copyTag().getCompound("shulkerboxtooltip");
   }

   private static int overrideWindowColor(final ItemStack stack, final int fallback) {
      Optional<CompoundTag> overrides = getItemOverrides(stack);
      if (overrides.isEmpty()) {
         return fallback;
      }
      Optional<Integer> rgb = overrides.get().getInt("window_color");
      if (rgb.isPresent()) {
         return rgb.get();
      }
      Optional<CompoundTag> colorKey = overrides.get().getCompound("window_color");
      if (colorKey.isEmpty()) {
         return fallback;
      }
      String category = colorKey.get().getString("category").orElse("");
      String id = colorKey.get().getString("id").orElse("");
      boolean supportedCategory = category.equals("shulkerboxtooltip:default")
         || category.equals("shulkerboxtooltip:shulker_boxes")
         || category.equals("shulkerboxtooltip:bundles");
      if (supportedCategory && DEFAULT_COLORS.containsKey(id)) {
         return CONFIG.paletteColor(id);
      }
      return CONFIG.paletteColor(category + "/" + id, fallback);
   }

   private static boolean overrideBoolean(final ItemStack stack, final String key, final boolean fallback) {
      return getItemOverrides(stack).flatMap(tag -> tag.getBoolean(key)).orElse(fallback);
   }

   private static int overrideInt(final ItemStack stack, final String key, final int fallback) {
      return getItemOverrides(stack).flatMap(tag -> tag.getInt(key)).orElse(fallback);
   }

   private static int clamp(final int value, final int min, final int max) {
      return Math.max(min, Math.min(max, value));
   }

   private record ContainerLayout(int inventorySize, int maxRowSize, boolean generic) {
   }

   private static String overrideString(final ItemStack stack, final String key, final String fallback) {
      return getItemOverrides(stack).flatMap(tag -> tag.getString(key)).filter(value -> !value.isBlank()).orElse(fallback);
   }

   public static void appendTooltipDetails(final ItemStack stack, final Consumer<Component> consumer) {
      if (!CONFIG.enabled || CONFIG.tooltipType != 1 || !isPreviewable(stack)) {
         return;
      }
      SeededContainerLoot loot = stack.get(DataComponents.CONTAINER_LOOT);
      Map.Entry<Identifier, PreviewProvider> provider = providerFor(stack);
      if (provider != null) {
         provider.getValue().tooltipLines(stack).forEach(consumer);
      }
      if (loot != null && canUseLootTables(stack, provider)) {
         switch (CONFIG.lootTableInfoType) {
            case 1 -> consumer.accept(Component.translatable("shulkerboxtooltip.hint.loot_table"));
            case 2 -> consumer.accept(Component.translatable("shulkerboxtooltip.hint.loot_table.advanced", loot.lootTable().identifier()));
            default -> {
            }
         }
         return;
      }
      if (provider != null && !stack.has(DataComponents.CONTAINER) && !stack.has(DataComponents.BUNDLE_CONTENTS)) {
         return;
      }
      if (CONFIG.tooltipType == 1 && !isPreviewShowingFull(stack)) {
         ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
         BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
         int itemCount = contents != null
            ? (int)contents.nonEmptyItemCopyStream().count()
            : bundle != null ? bundle.size() : 0;
         consumer.accept(itemCount == 0
            ? Component.translatable("container.shulkerbox.empty")
            : Component.translatable("container.shulkerbox.contains", itemCount));
      }
   }

   public static boolean hasRegisteredPreviewProvider(final ItemStack stack) {
      return providerFor(stack) != null;
   }

   public static boolean shouldShowVanillaContainerTooltip(final ItemStack stack) {
      return !CONFIG.enabled || CONFIG.tooltipType == 0 || !isPreviewable(stack);
   }

   public static boolean hideShulkerLore(final ItemStack stack) {
      return CONFIG.enabled && CONFIG.hideShulkerBoxLore && stack.is(ItemTags.SHULKER_BOXES);
   }

   public static boolean isPreviewable(final ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }
      Map.Entry<Identifier, PreviewProvider> provider = providerFor(stack);
      return overrideBoolean(stack, "should_display", true)
         && (provider != null
         || stack.is(ItemTags.SHULKER_BOXES)
         || stack.has(DataComponents.CONTAINER) && (CONFIG.genericContainerPreview || isSupportedContainer(stack))
         || stack.has(DataComponents.CONTAINER_LOOT) && canUseLootTables(stack, provider)
            || stack.has(DataComponents.BUNDLE_CONTENTS) && CONFIG.bundleTheme != 1
            || stack.is(Items.ENDER_CHEST)
            || isLecternBook(stack));
   }

   public static boolean isPreviewShowingFull(final ItemStack stack) {
      if (!CONFIG.fullPreviewEnabled
         || !overrideBoolean(stack, "full_preview_available", true)
         || !(CONFIG.alwaysOn || isKeyPressed(CONFIG.previewKey))) {
         return false;
      }
      boolean fullKey = isKeyPressed(CONFIG.fullPreviewKey);
      return CONFIG.swapModes ? !fullKey : fullKey;
   }

   public static void appendTooltipHints(final ItemStack stack, final List<Component> lines) {
      if (!CONFIG.enabled
         || !CONFIG.showKeyHints
         || !overrideBoolean(stack, "show_tooltip_hints", true)
         || !isPreviewable(stack)
         || CONFIG.tooltipType == 2) {
         return;
      }
      String previewKey = keyName(CONFIG.previewKey);
      String fullKey = keyName(CONFIG.fullPreviewKey);
      String compactHint = overrideString(
         stack,
         "tooltip_hint_lang_key",
         stack.has(DataComponents.BUNDLE_CONTENTS) && CONFIG.bundleTheme == 2
            ? "shulkerboxtooltip.hint.sorted"
            : "shulkerboxtooltip.hint.compact"
      );
      String fullHint = overrideString(stack, "full_tooltip_hint_lang_key", "shulkerboxtooltip.hint.full");
      lines.add(Component.literal("[" + previewKey + "] ").append(Component.translatable(CONFIG.swapModes ? fullHint : compactHint)));
      if (CONFIG.fullPreviewEnabled && overrideBoolean(stack, "full_preview_available", true)) {
         lines.add(
            Component.literal("[" + previewKey + " + " + fullKey + "] ")
               .append(
                  Component.translatable(
                     CONFIG.swapModes ? compactHint : fullHint
                  )
               )
         );
      }
      if (CONFIG.lockKey >= 0) {
         lines.add(
            Component.literal("[" + keyName(CONFIG.lockKey) + "] ")
               .append(Component.translatable(overrideString(stack, "lock_tooltip_hint_lang_key", "shulkerboxtooltip.hint.lock")))
         );
      }
   }

   public static String keyName(final int keyCode) {
      return keyCode < 0 ? "Unbound" : InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
   }

   private static boolean isKeyPressed(final int keyCode) {
      return keyCode >= 0 && Minecraft.getInstance() != null
         && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
   }

   public static boolean isLockKeyPressed() {
      return CONFIG.enabled && isKeyPressed(CONFIG.lockKey);
   }

   private static int getBoxColor(final ItemStack stack) {
      Identifier itemId = stack.getItem().builtInRegistryHolder().unwrapKey().map(key -> key.identifier()).orElse(null);
      if (itemId == null || !itemId.getPath().endsWith("_shulker_box")) {
         return CONFIG.paletteColor("shulker_box");
      }
      String name = itemId.getPath().substring(0, itemId.getPath().length() - "_shulker_box".length());
      DyeColor dye = DyeColor.byName(name, null);
      return dye == null ? CONFIG.paletteColor("shulker_box") : CONFIG.paletteColor(name + "_shulker_box");
   }

   private static Map<String, Integer> createDefaultColors() {
      LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
      colors.put("default", 0xFFFFFF);
      colors.put("ender_chest", 740161);
      colors.put("generic_container", 0xFFFFFF);
      colors.put("shulker_box", 0x976797);
      colors.put("bundle", 8669995);
      for (DyeColor dye : DyeColor.values()) {
         int color = dye.getTextureDiffuseColor();
         int red = Math.max(38, color >> 16 & 0xFF);
         int green = Math.max(38, color >> 8 & 0xFF);
         int blue = Math.max(38, color & 0xFF);
         int clampedColor = red << 16 | green << 8 | blue;
         colors.put(dye.getName() + "_shulker_box", clampedColor);
         colors.put(dye.getName() + "_bundle", clampedColor);
      }
      return Map.copyOf(colors);
   }

   public static List<String> paletteKeys() {
      ArrayList<String> keys = new ArrayList<>(DEFAULT_COLORS.keySet());
      keys.addAll(REGISTERED_COLORS.keySet());
      return keys.stream().sorted().toList();
   }

   public static final class Config {
      public boolean enabled = true;
      public boolean alwaysOn;
      public boolean swapModes;
      public boolean fullPreviewEnabled = true;
      public boolean genericContainerPreview = true;
      public boolean coloredPreview = true;
      public boolean showKeyHints = true;
      public boolean hideShulkerBoxLore;
      public boolean shortItemCounts = true;
      public int previewKey = 340;
      public int fullPreviewKey = 342;
      public int lockKey = 341;
      public int maxRowSize = 9;
      public int compactPreviewOrder;
      public int mergeStrategy = 2;
      public int theme;
      public int bundleTheme = 1;
      public int position;
      public int tooltipType = 1;
      public int lootTableInfoType;
      private File configFile;
      private final Map<String, Integer> palette = new LinkedHashMap<>();

      public int paletteColor(final String key) {
         return this.paletteColor(key, DEFAULT_COLORS.getOrDefault(key, REGISTERED_COLORS.getOrDefault(key, DEFAULT_COLORS.get("default"))));
      }

      private int paletteColor(final String key, final int fallback) {
         return this.palette.getOrDefault(key, DEFAULT_COLORS.getOrDefault(key, REGISTERED_COLORS.getOrDefault(key, fallback)));
      }

      public void setPaletteColor(final String key, final String hexColor) {
         Integer color = parseHexColor(hexColor);
         if ((DEFAULT_COLORS.containsKey(key) || REGISTERED_COLORS.containsKey(key)) && color != null) {
            this.palette.put(key, color & 0xFFFFFF);
            this.save();
         }
      }

      public void resetPaletteColor(final String key) {
         if (this.palette.remove(key) != null) {
            this.save();
         }
      }

      public void load(final File gameDirectory) {
         this.configFile = new File(new File(gameDirectory, "config"), CONFIG_FILE);
         if (!this.configFile.isFile()) {
            this.save();
            return;
         }
         try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(this.configFile.toPath(), StandardCharsets.UTF_8)) {
               int i = line.indexOf('=');
               if (i > 0 && !line.trim().startsWith("#")) {
                  values.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
               }
            }
            this.enabled = bool(values, "preview.enable", this.enabled);
            this.alwaysOn = bool(values, "preview.alwaysOn", this.alwaysOn);
            this.swapModes = bool(values, "preview.swapModes", this.swapModes);
            this.fullPreviewEnabled = bool(values, "preview.fullPreviewEnabled", this.fullPreviewEnabled);
            this.genericContainerPreview = bool(values, "preview.genericContainerPreview", this.genericContainerPreview);
            this.coloredPreview = bool(values, "colors.coloredPreview", this.coloredPreview);
            this.showKeyHints = bool(values, "tooltip.showKeyHints", this.showKeyHints);
            this.hideShulkerBoxLore = bool(values, "tooltip.hideShulkerBoxLore", this.hideShulkerBoxLore);
            this.shortItemCounts = bool(values, "preview.shortItemCounts", this.shortItemCounts);
            this.previewKey = integer(values, "controls.previewKey", this.previewKey);
            this.fullPreviewKey = integer(values, "controls.fullPreviewKey", this.fullPreviewKey);
            this.lockKey = integer(values, "controls.lockTooltipKey", this.lockKey);
            this.maxRowSize = clamp(integer(values, "preview.defaultMaxRowSize", this.maxRowSize), 1, 9);
            this.compactPreviewOrder = clamp(integer(values, "preview.compactPreviewOrder", this.compactPreviewOrder), 0, 1);
            this.mergeStrategy = clamp(integer(values, "preview.compactPreviewNbtBehavior", this.mergeStrategy), 0, 2);
            this.theme = clamp(integer(values, "preview.theme", this.theme), 0, 1);
            this.bundleTheme = clamp(integer(values, "preview.themeBundle", this.bundleTheme), 0, 2);
            this.position = clamp(integer(values, "preview.position", this.position), 0, 3);
            this.tooltipType = clamp(integer(values, "tooltip.type", this.tooltipType), 0, 2);
            this.lootTableInfoType = clamp(integer(values, "tooltip.lootTableInfoType", this.lootTableInfoType), 0, 2);
            Integer oldWindowColor = parseHexColor(values.get("colors.windowColor"));
            if (oldWindowColor != null) {
               this.palette.put("default", oldWindowColor & 0xFFFFFF);
            }
            for (Map.Entry<String, String> entry : values.entrySet()) {
               if (entry.getKey().startsWith("colors.palette.")) {
                  String colorKey = entry.getKey().substring("colors.palette.".length());
                  Integer color = parseHexColor(entry.getValue());
                  if (color != null && isValidPaletteKey(colorKey)) {
                     this.palette.put(colorKey, color & 0xFFFFFF);
                  }
               }
            }
         } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not read Shulker Box Tooltip configuration {}", this.configFile, e);
         }
      }

      public void save() {
         if (this.configFile == null) {
            return;
         }
         StringBuilder contents = new StringBuilder();
         contents.append("preview.enable = ").append(this.enabled).append('\n');
         contents.append("preview.alwaysOn = ").append(this.alwaysOn).append('\n');
         contents.append("preview.swapModes = ").append(this.swapModes).append('\n');
         contents.append("preview.fullPreviewEnabled = ").append(this.fullPreviewEnabled).append('\n');
         contents.append("preview.genericContainerPreview = ").append(this.genericContainerPreview).append('\n');
         contents.append("preview.defaultMaxRowSize = ").append(this.maxRowSize).append('\n');
         contents.append("preview.compactPreviewOrder = ").append(this.compactPreviewOrder).append('\n');
         contents.append("preview.compactPreviewNbtBehavior = ").append(this.mergeStrategy).append('\n');
         contents.append("preview.shortItemCounts = ").append(this.shortItemCounts).append('\n');
         contents.append("preview.theme = ").append(this.theme).append('\n');
         contents.append("preview.themeBundle = ").append(this.bundleTheme).append('\n');
         contents.append("preview.position = ").append(this.position).append('\n');
         contents.append("tooltip.type = ").append(this.tooltipType).append('\n');
         contents.append("tooltip.showKeyHints = ").append(this.showKeyHints).append('\n');
         contents.append("tooltip.lootTableInfoType = ").append(this.lootTableInfoType).append('\n');
         contents.append("tooltip.hideShulkerBoxLore = ").append(this.hideShulkerBoxLore).append('\n');
         contents.append("colors.coloredPreview = ").append(this.coloredPreview).append('\n');
         this.palette.forEach((key, color) ->
            contents.append("colors.palette.").append(key).append(" = ").append(String.format("%06X", color & 0xFFFFFF)).append('\n')
         );
         contents.append("controls.previewKey = ").append(this.previewKey).append('\n');
         contents.append("controls.fullPreviewKey = ").append(this.fullPreviewKey).append('\n');
         contents.append("controls.lockTooltipKey = ").append(this.lockKey).append('\n');
         try {
            File parent = this.configFile.getParentFile();
            if (parent != null) {
               Files.createDirectories(parent.toPath());
            }
            Files.writeString(this.configFile.toPath(), contents, StandardCharsets.UTF_8);
         } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not save Shulker Box Tooltip configuration {}", this.configFile, e);
         }
      }

      private int bundleColor(final ItemStack stack) {
         if (!this.coloredPreview) {
            return -1;
         }
         Identifier id = stack.getItem().builtInRegistryHolder().unwrapKey().map(key -> key.identifier()).orElse(null);
         if (id != null && id.getPath().endsWith("_bundle")) {
            String name = id.getPath().substring(0, id.getPath().length() - 7);
            if (DyeColor.byName(name, null) != null) {
               return this.paletteColor(name + "_bundle");
            }
         }
         return this.paletteColor("bundle");
      }

      private static boolean bool(final Map<String, String> values, final String key, final boolean fallback) {
         String value = values.get(key);
         return value == null ? fallback : Boolean.parseBoolean(value);
      }

      private static int integer(final Map<String, String> values, final String key, final int fallback) {
         try {
            String value = values.get(key);
            if (value == null) {
               return fallback;
            }
            return Integer.parseInt(value);
         } catch (NumberFormatException e) {
            return fallback;
         }
      }

      private static Integer parseHexColor(final String value) {
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
            return (int)Long.parseLong(normalized, 16);
         } catch (NumberFormatException e) {
            return null;
         }
      }

      private static boolean isValidPaletteKey(final String key) {
         return DEFAULT_COLORS.containsKey(key)
            || REGISTERED_COLORS.containsKey(key)
            || key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+/[a-z0-9_.-]+");
      }

      private static int clamp(final int value, final int min, final int max) {
         return Math.max(min, Math.min(max, value));
      }
   }
}
