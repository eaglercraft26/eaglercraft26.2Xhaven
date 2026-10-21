package ru.berdinskiybear.armorhud;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ArmorHudPort {
   private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
   private static final Identifier HOTBAR_OFFHAND_LEFT_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
   private static final Identifier WARNING_TEXTURE = Identifier.fromNamespaceAndPath("ukus-armor-hud", "warn.png");
   private static final Identifier ARMOR_BREAKING_SOUND_ID = Identifier.fromNamespaceAndPath("ukus-armor-hud", "armor_breaking");
   private static final SoundEvent ARMOR_BREAKING_SOUND = SoundEvent.createVariableRangeEvent(ARMOR_BREAKING_SOUND_ID);
   private static final EquipmentSlot[] ARMOR_SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final int SLOT_SIZE = 22;
   private static final int SLOT_STEP = 20;
   private static final int WARNING_SIZE = 8;
   private static final RandomSource WARNING_RANDOM = RandomSource.create();
   public static final ArmorHudConfig CONFIG = new ArmorHudConfig();

   private static GuiGraphicsExtractor cachedGraphics;
   private static @Nullable Layout cachedLayout;
   private static @Nullable Player lastPlayer;
   private static final Item[] previousItems = new Item[ARMOR_SLOTS.length];
   private static final int[] previousDamage = new int[ARMOR_SLOTS.length];

   private ArmorHudPort() {
   }

   public static void init(final File gameDirectory) {
      CONFIG.init(gameDirectory);
   }

   public static void beginFrame() {
      cachedGraphics = null;
      cachedLayout = null;
   }

   public static void toggle() {
      CONFIG.enabled = !CONFIG.enabled;
      CONFIG.save();
   }

   public static void tick() {
      Player player = getCameraPlayer();
      if (player == null) {
         resetDamageTracking();
      } else {
         updateDamageTracking(player);
      }
   }

   public static void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
      if (!CONFIG.enabled) {
         return;
      }

      Player player = getCameraPlayer();
      if (player == null) {
         return;
      }

      Layout layout = getLayout(graphics, player);
      if (layout == null) {
         return;
      }

      drawWidget(graphics, deltaTracker, player, layout);
   }

   public static int bossBarYOffset(final GuiGraphicsExtractor graphics, final int defaultOffset) {
      if (!CONFIG.enabled || !CONFIG.pushBossbars || CONFIG.anchor != ArmorHudConfig.Anchor.TOP_CENTER) {
         return defaultOffset;
      }
      Layout layout = getLayout(graphics, getCameraPlayer());
      return layout == null ? defaultOffset : layout.effectiveY + layout.effectiveHeight;
   }

   public static int statusEffectYOffset(final GuiGraphicsExtractor graphics) {
      if (!CONFIG.enabled || !CONFIG.pushStatusEffectIcons || CONFIG.anchor != ArmorHudConfig.Anchor.TOP || CONFIG.side != ArmorHudConfig.Side.RIGHT) {
         return 0;
      }
      Layout layout = getLayout(graphics, getCameraPlayer());
      return layout == null ? 0 : layout.effectiveY + layout.effectiveHeight;
   }

   public static int subtitleYOffset(final GuiGraphicsExtractor graphics) {
      if (!CONFIG.enabled || !CONFIG.pushSubtitles || CONFIG.anchor != ArmorHudConfig.Anchor.BOTTOM || CONFIG.side != ArmorHudConfig.Side.RIGHT) {
         return 0;
      }
      Layout layout = getLayout(graphics, getCameraPlayer());
      return layout == null ? 0 : Math.max(0, layout.effectiveHeight - 25);
   }

   public static boolean shouldShowWarning(final ItemStack stack) {
      if (stack.isEmpty() || !stack.isDamageableItem()) {
         return false;
      }
      int remaining = stack.getMaxDamage() - stack.getDamageValue();
      double remainingPercentage = (double)remaining / stack.getMaxDamage();
      return remainingPercentage <= CONFIG.minDurabilityPercentage || remaining <= CONFIG.minDurabilityValue;
   }

   private static @Nullable Player getCameraPlayer() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.getCameraEntity() instanceof Player player ? player : null;
   }

   private static @Nullable Layout getLayout(final GuiGraphicsExtractor graphics, final @Nullable Player player) {
      if (cachedGraphics == graphics) {
         return cachedLayout;
      }

      cachedGraphics = graphics;
      cachedLayout = player == null ? null : createLayout(graphics, player);
      return cachedLayout;
   }

   private static @Nullable Layout createLayout(final GuiGraphicsExtractor graphics, final Player player) {
      List<ArmorPiece> pieces = new ArrayList<>(ARMOR_SLOTS.length);
      boolean anyPresent = false;
      for (EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack stack = player.getItemBySlot(slot);
         anyPresent |= !stack.isEmpty();
         pieces.add(new ArmorPiece(slot, stack));
      }

      switch (CONFIG.widgetShown) {
         case ALWAYS -> {
         }
         case IF_ANY_PRESENT -> {
            if (!anyPresent) {
               return null;
            }
         }
         case NOT_EMPTY -> pieces.removeIf(piece -> piece.stack.isEmpty());
         case DAMAGED_PIECES -> pieces.removeIf(piece -> piece.stack.isEmpty() || !piece.stack.isDamageableItem() || !piece.stack.isDamaged());
      }
      if (pieces.isEmpty()) {
         return null;
      }
      if (CONFIG.reversed) {
         java.util.Collections.reverse(pieces);
      }

      int width = CONFIG.orientation == ArmorHudConfig.Orientation.VERTICAL ? SLOT_SIZE : SLOT_SIZE + SLOT_STEP * (pieces.size() - 1);
      int height = CONFIG.orientation == ArmorHudConfig.Orientation.VERTICAL ? SLOT_SIZE + SLOT_STEP * (pieces.size() - 1) : SLOT_SIZE;
      int offhandOffset = getOffhandOffset(player);
      int x;
      if (CONFIG.anchor == ArmorHudConfig.Anchor.TOP_CENTER) {
         int offsetDirection = CONFIG.side == ArmorHudConfig.Side.LEFT ? 1 : -1;
         x = (graphics.guiWidth() - width) / 2 + CONFIG.offsetX * offsetDirection;
      } else if (CONFIG.anchor == ArmorHudConfig.Anchor.HOTBAR) {
         boolean left = CONFIG.side == ArmorHudConfig.Side.LEFT;
         x = graphics.guiWidth() / 2 + (left ? -1 : 1) * (98 + offhandOffset) + (left ? -width : 0) + CONFIG.offsetX * (left ? -1 : 1);
      } else {
         boolean right = CONFIG.side == ArmorHudConfig.Side.RIGHT;
         x = (right ? graphics.guiWidth() - width : 0) + CONFIG.offsetX * (right ? -1 : 1);
      }

      boolean top = isTopAnchor();
      int y = top ? CONFIG.offsetY : graphics.guiHeight() - height - CONFIG.offsetY;
      int extraHeight = 0;
      if (CONFIG.orientation == ArmorHudConfig.Orientation.HORIZONTAL) {
         if (CONFIG.warningShown) {
            extraHeight += 10 + CONFIG.warningBobIntensity / 2;
         }
         if (CONFIG.durabilityDisplay != ArmorHudConfig.DurabilityDisplay.BAR) {
            extraHeight += 9;
         }
      }
      int effectiveY = top ? y : y - extraHeight;
      return new Layout(pieces, x, y, width, height, effectiveY, height + extraHeight);
   }

   private static int getOffhandOffset(final Player player) {
      if (CONFIG.anchor != ArmorHudConfig.Anchor.HOTBAR) {
         return 0;
      }
      return switch (CONFIG.offhandSlotBehavior) {
         case ALWAYS_IGNORE -> 0;
         case ADHERE -> player.getMainArm() == asArm(CONFIG.side) ? 23 : 29;
         case ALWAYS_LEAVE_SPACE -> {
            if (player.getMainArm() == asArm(CONFIG.side)) {
               yield Minecraft.getInstance().options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR
                     && player.getAttackStrengthScale(0.0F) < 1.0F
                  ? 23
                  : 0;
            }
            yield player.getOffhandItem().isEmpty() ? 0 : 29;
         }
      };
   }

   private static HumanoidArm asArm(final ArmorHudConfig.Side side) {
      return side == ArmorHudConfig.Side.LEFT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
   }

   private static void drawWidget(
      final GuiGraphicsExtractor graphics,
      final DeltaTracker deltaTracker,
      final Player player,
      final Layout layout
   ) {
      graphics.pose().pushMatrix();
      graphics.pose().translate(layout.x, layout.y);
      if (CONFIG.orientation == ArmorHudConfig.Orientation.VERTICAL) {
         graphics.pose().rotate((float)(Math.PI / 2.0));
         graphics.pose().translate(0.0F, -SLOT_SIZE);
      }

      drawBackground(graphics, layout);
      Font font = Minecraft.getInstance().font;
      boolean textOnLeft = CONFIG.anchor == ArmorHudConfig.Anchor.HOTBAR
         ? CONFIG.side == ArmorHudConfig.Side.LEFT
         : CONFIG.side == ArmorHudConfig.Side.RIGHT;

      for (int index = 0; index < layout.pieces.size(); index++) {
         ArmorPiece piece = layout.pieces.get(index);
         int x = CONFIG.orientation == ArmorHudConfig.Orientation.HORIZONTAL ? SLOT_STEP * index : 0;
         int y = CONFIG.orientation == ArmorHudConfig.Orientation.VERTICAL ? SLOT_STEP * index : 0;
         if (piece.stack.isEmpty()) {
            if (CONFIG.iconsShown && drawsEmptySlots()) {
               graphics.blitSprite(
                  RenderPipelines.GUI_TEXTURED,
                  emptySlotSprite(piece.slot),
                  x + 3,
                  y + 3,
                  16,
                  16
               );
            }
         } else {
            extractItem(graphics, deltaTracker, player, piece.stack, x + 3, y + 3, index + 1);
            graphics.itemDecorations(font, piece.stack, x + 3, y + 3);
         }

         if (CONFIG.orientation == ArmorHudConfig.Orientation.HORIZONTAL && isTopAnchor()) {
            y += SLOT_SIZE;
         } else if (CONFIG.orientation == ArmorHudConfig.Orientation.VERTICAL && !textOnLeft) {
            x += SLOT_SIZE;
         }

         if (!piece.stack.isEmpty() && piece.stack.isDamageableItem() && CONFIG.durabilityDisplay != ArmorHudConfig.DurabilityDisplay.BAR) {
            String durability = CONFIG.durabilityDisplay == ArmorHudConfig.DurabilityDisplay.NUMERIC
               ? Integer.toString(piece.stack.getMaxDamage() - piece.stack.getDamageValue())
               : (piece.stack.getDamageValue() == 0
                  ? ""
                  : ((int)Math.floor((1.0 - (double)piece.stack.getDamageValue() / piece.stack.getMaxDamage()) * 100.0)) + "%");
            if (!durability.isEmpty()) {
               int color = piece.stack.getBarColor() | 0xFF000000;
               if (CONFIG.orientation == ArmorHudConfig.Orientation.HORIZONTAL) {
                  if (!isTopAnchor()) {
                     y -= 9;
                  }
                  graphics.centeredText(font, durability, x + 11, y, color);
                  if (isTopAnchor()) {
                     y += 9;
                  }
               } else {
                  int labelWidth = font.width(durability) + 2;
                  if (textOnLeft) {
                     x -= labelWidth;
                  }
                  graphics.text(font, durability, x + 1, y + (SLOT_SIZE - 9) / 2, color);
                  if (!textOnLeft) {
                     x += labelWidth;
                  }
               }
            }
         }

         if (CONFIG.warningShown && shouldShowWarning(piece.stack)) {
            int bob = CONFIG.warningBobIntensity == 0
               ? 0
               : WARNING_RANDOM.nextInt(CONFIG.warningBobIntensity) - (CONFIG.warningBobIntensity + 1) / 2;
            int warningX;
            int warningY;
            if (CONFIG.orientation == ArmorHudConfig.Orientation.HORIZONTAL) {
               if (!isTopAnchor()) {
                  y -= 10;
               }
               warningX = x + 7;
               warningY = y + 1 + bob;
            } else {
               if (textOnLeft) {
                  x -= 10;
               }
               warningX = x + 1;
               warningY = y + 7 + bob;
            }
            graphics.blit(
               RenderPipelines.GUI_TEXTURED,
               WARNING_TEXTURE,
               warningX,
               warningY,
               0.0F,
               0.0F,
               WARNING_SIZE,
               WARNING_SIZE,
               WARNING_SIZE,
               WARNING_SIZE
            );
         }
      }
      graphics.pose().popMatrix();
   }

   private static void drawBackground(final GuiGraphicsExtractor graphics, final Layout layout) {
      int width = SLOT_SIZE + SLOT_STEP * (layout.pieces.size() - 1);
      if (CONFIG.style == ArmorHudConfig.Style.NONE) {
         return;
      }
      if (CONFIG.style == ArmorHudConfig.Style.HOTBAR) {
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 0, 0, 0, 0, width - 3, SLOT_SIZE);
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 179, 0, width - 3, 0, 3, SLOT_SIZE);
         return;
      }
      if (CONFIG.style == ArmorHudConfig.Style.ROUNDED) {
         if (layout.pieces.size() == 1) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 0, 1, 0, 0, 22, 22);
            return;
         }
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 0, 1, 0, 0, 22, 22);
         for (int index = 1; index < layout.pieces.size() - 1; index++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 1, 1, 1 + index * 20, 0, 20, 22);
         }
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 1, 1, width - 21, 0, 22, 21);
         return;
      }

      if (layout.pieces.size() == 1) {
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 0, 1, 0, 0, 22, 22);
         return;
      }
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 0, 1, 0, 0, 3, 22);
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 3, 0, 3, 0, width - 6, 22);
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, 29, 24, 19, 1, width - 3, 0, 3, 22);
   }

   private static boolean drawsEmptySlots() {
      return CONFIG.widgetShown == ArmorHudConfig.WidgetShown.ALWAYS || CONFIG.widgetShown == ArmorHudConfig.WidgetShown.IF_ANY_PRESENT;
   }

   private static boolean isTopAnchor() {
      return CONFIG.anchor == ArmorHudConfig.Anchor.TOP || CONFIG.anchor == ArmorHudConfig.Anchor.TOP_CENTER;
   }

   private static Identifier emptySlotSprite(final EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
         case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
         case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
         case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
         default -> throw new IllegalArgumentException("Not an armor slot: " + slot);
      };
   }

   private static void extractItem(
      final GuiGraphicsExtractor graphics,
      final DeltaTracker deltaTracker,
      final LivingEntity owner,
      final ItemStack stack,
      final int x,
      final int y,
      final int seed
   ) {
      float pop = stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
      if (pop > 0.0F) {
         float squeeze = 1.0F + pop / 5.0F;
         graphics.pose().pushMatrix();
         graphics.pose().translate(x + 8, y + 12);
         graphics.pose().scale(1.0F / squeeze, (squeeze + 1.0F) / 2.0F);
         graphics.pose().translate(-(x + 8), -(y + 12));
      }
      graphics.item(owner, stack, x, y, seed);
      if (pop > 0.0F) {
         graphics.pose().popMatrix();
      }
   }

   private static void updateDamageTracking(final Player player) {
      if (lastPlayer != player) {
         lastPlayer = player;
         java.util.Arrays.fill(previousItems, null);
         java.util.Arrays.fill(previousDamage, 0);
      }

      boolean playSound = false;
      for (int index = 0; index < ARMOR_SLOTS.length; index++) {
         ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[index]);
         Item item = stack.isEmpty() ? null : stack.getItem();
         int damage = stack.isEmpty() ? 0 : stack.getDamageValue();
         if (item != null && item == previousItems[index] && damage != previousDamage[index] && shouldShowWarning(stack)) {
            playSound = true;
         }
         previousItems[index] = item;
         previousDamage[index] = damage;
      }

      if (CONFIG.playBreakSound && playSound) {
         Minecraft.getInstance().getSoundManager().play(
            new SimpleSoundInstance(
               ARMOR_BREAKING_SOUND,
               SoundSource.PLAYERS,
               1.0F,
               1.0F,
               RandomSource.create(),
               player.getX(),
               player.getY(),
               player.getZ()
            )
         );
      }
   }

   private static void resetDamageTracking() {
      lastPlayer = null;
      java.util.Arrays.fill(previousItems, null);
      java.util.Arrays.fill(previousDamage, 0);
   }

   private record ArmorPiece(EquipmentSlot slot, ItemStack stack) {
   }

   private record Layout(List<ArmorPiece> pieces, int x, int y, int width, int height, int effectiveY, int effectiveHeight) {
   }
}
