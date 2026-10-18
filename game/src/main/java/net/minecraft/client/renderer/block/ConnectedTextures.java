package net.minecraft.client.renderer.block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built-in connected textures (Continuity default pack behaviour, hard-coded).
 * Covers glass, stained glass, tinted glass, glass panes, bookshelves and sandstone variants.
 */
public final class ConnectedTextures {
   public static final int METHOD_CTM = 0;
   public static final int METHOD_HORIZONTAL = 1;
   public static final int METHOD_TOP = 2;

   // bit order: U, UR, R, DR, D, DL, L, UL
   private static final int[] CTM_LOOKUP = {
      0,36,0,36,1,16,1,37,0,36,0,36,1,16,1,37,12,24,12,24,4,6,4,30,12,24,12,24,13,28,13,25,
      0,36,0,36,1,16,1,37,0,36,0,36,1,16,1,37,12,24,12,24,4,6,4,30,12,24,12,24,13,28,13,25,
      3,17,3,17,2,18,2,40,3,17,3,17,2,18,2,40,5,19,5,19,7,46,7,8,5,19,5,19,31,9,31,23,
      3,17,3,17,2,18,2,40,3,17,3,17,2,18,2,40,15,43,15,43,29,21,29,34,15,43,15,43,14,22,14,45,
      0,36,0,36,1,16,1,37,0,36,0,36,1,16,1,37,12,24,12,24,4,6,4,30,12,24,12,24,13,28,13,25,
      0,36,0,36,1,16,1,37,0,36,0,36,1,16,1,37,12,24,12,24,4,6,4,30,12,24,12,24,13,28,13,25,
      3,39,3,39,2,42,2,38,3,39,3,39,2,42,2,38,5,41,5,41,7,20,7,11,5,41,5,41,31,35,31,33,
      3,39,3,39,2,42,2,38,3,39,3,39,2,42,2,38,15,27,15,27,29,10,29,32,15,27,15,27,14,44,14,26
   };

   private static final String[] COLORS = {
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
      "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };

   private record Spec(String name, int method, int count) {
   }

   private static final List<Spec> SPECS = new ArrayList<>();
   private static final java.util.HashMap<String, Integer> SPEC_INDEX = new java.util.HashMap<>();

   static {
      add("glass", METHOD_CTM, 47);
      for (String c : COLORS) {
         add(c + "_stained_glass", METHOD_CTM, 47);
      }
      add("tinted_glass", METHOD_CTM, 47);
      add("bookshelf", METHOD_HORIZONTAL, 4);
      add("chiseled_sandstone", METHOD_HORIZONTAL, 4);
      add("cut_sandstone", METHOD_HORIZONTAL, 4);
      add("chiseled_red_sandstone", METHOD_HORIZONTAL, 4);
      add("cut_red_sandstone", METHOD_HORIZONTAL, 4);
      add("sandstone", METHOD_TOP, 1);
      add("red_sandstone", METHOD_TOP, 1);
   }

   private static void add(final String name, final int method, final int count) {
      SPEC_INDEX.put(name, SPECS.size());
      SPECS.add(new Spec(name, method, count));
   }

   public static final class Family {
      public final int method;
      public final float su0;
      public final float su1;
      public final float sv0;
      public final float sv1;
      /** u0, u1, v0, v1 per tile. */
      public final float[] tiles;

      public Family(final int method, final float su0, final float su1, final float sv0, final float sv1, final float[] tiles) {
         this.method = method;
         this.su0 = su0;
         this.su1 = su1;
         this.sv0 = sv0;
         this.sv1 = sv1;
         this.tiles = tiles;
      }
   }

   private static volatile Family[] families = new Family[0];
   private static final ConcurrentHashMap<Block, Integer> BLOCK_SPEC = new ConcurrentHashMap<>();

   private ConnectedTextures() {
   }

   public static Family[] snapshot() {
      return families;
   }

   public static void install(final Family[] installed) {
      families = installed;
   }

   public static int specCount() {
      return SPECS.size();
   }

   /** Main thread: resolve sprite UV rectangles after the block atlas has been stitched. */
   public static void populate(final TextureAtlas atlas) {
      Family[] result = new Family[SPECS.size()];
      TextureAtlasSprite missing = atlas.missingSprite();
      for (int i = 0; i < result.length; ++i) {
         Spec spec = SPECS.get(i);
         TextureAtlasSprite source = atlas.getSprite(Identifier.withDefaultNamespace("block/" + spec.name()));
         if (source == missing) {
            continue;
         }
         float[] tiles = new float[spec.count() * 4];
         boolean ok = true;
         for (int k = 0; k < spec.count(); ++k) {
            TextureAtlasSprite tile = atlas.getSprite(Identifier.withDefaultNamespace("block/ctm/" + spec.name() + "/" + k));
            if (tile == missing) {
               ok = false;
               break;
            }
            tiles[k * 4] = tile.getU0();
            tiles[k * 4 + 1] = tile.getU1();
            tiles[k * 4 + 2] = tile.getV0();
            tiles[k * 4 + 3] = tile.getV1();
         }
         if (ok) {
            result[i] = new Family(spec.method(), source.getU0(), source.getU1(), source.getV0(), source.getV1(), tiles);
         }
      }
      families = result;
   }

   private static int specIndex(final Block block) {
      Integer cached = BLOCK_SPEC.get(block);
      if (cached != null) {
         return cached;
      }
      int idx = -1;
      Identifier key = BuiltInRegistries.BLOCK.getKey(block);
      if (key != null && "minecraft".equals(key.getNamespace())) {
         String p = key.getPath();
         if (p.endsWith("_pane")) {
            p = p.substring(0, p.length() - 5);
         }
         Integer found = SPEC_INDEX.get(p);
         if (found != null) {
            idx = found;
         }
      }
      BLOCK_SPEC.put(block, idx);
      return idx;
   }

   public static BakedQuad apply(final BlockAndTintGetter level, final BlockState state, final BlockPos pos, final BakedQuad quad) {
      Family[] fs = families;
      if (fs.length == 0) {
         return quad;
      }
      Block block = state.getBlock();
      int idx = specIndex(block);
      if (idx < 0 || idx >= fs.length) {
         return quad;
      }
      Family f = fs[idx];
      if (f == null) {
         return quad;
      }
      if (!insideSource(f, quad.packedUV0()) || !insideSource(f, quad.packedUV2())) {
         return quad;
      }
      Direction dir = quad.direction();
      int tile;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      switch (f.method) {
         case METHOD_CTM:
            tile = ctmTile(level, block, pos, m, dir);
            break;
         case METHOD_HORIZONTAL: {
            if (dir.getAxis().isVertical()) {
               return quad;
            }
            int rx = rightX(dir);
            int rz = rightZ(dir);
            boolean right = same(level, block, pos, m, rx, 0, rz);
            boolean left = same(level, block, pos, m, -rx, 0, -rz);
            tile = left && right ? 1 : left ? 2 : right ? 0 : 3;
            break;
         }
         case METHOD_TOP:
            if (dir.getAxis().isVertical() || !same(level, block, pos, m, 0, 1, 0)) {
               return quad;
            }
            tile = 0;
            break;
         default:
            return quad;
      }
      if (tile < 0 || tile * 4 + 3 >= f.tiles.length) {
         return quad;
      }
      return new BakedQuad(
         quad.position0(), quad.position1(), quad.position2(), quad.position3(),
         remap(f, tile, quad.packedUV0()), remap(f, tile, quad.packedUV1()),
         remap(f, tile, quad.packedUV2()), remap(f, tile, quad.packedUV3()),
         dir, quad.materialInfo()
      );
   }

   private static boolean insideSource(final Family f, final long packed) {
      float u = UVPair.unpackU(packed);
      float v = UVPair.unpackV(packed);
      float e = 1.0E-5F;
      return u >= f.su0 - e && u <= f.su1 + e && v >= f.sv0 - e && v <= f.sv1 + e;
   }

   private static long remap(final Family f, final int tile, final long packed) {
      float u = UVPair.unpackU(packed);
      float v = UVPair.unpackV(packed);
      float fu = (u - f.su0) / (f.su1 - f.su0);
      float fv = (v - f.sv0) / (f.sv1 - f.sv0);
      int o = tile * 4;
      return UVPair.pack(f.tiles[o] + fu * (f.tiles[o + 1] - f.tiles[o]), f.tiles[o + 2] + fv * (f.tiles[o + 3] - f.tiles[o + 2]));
   }

   private static boolean same(final BlockAndTintGetter level, final Block block, final BlockPos pos, final BlockPos.MutableBlockPos m,
      final int dx, final int dy, final int dz) {
      return level.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).getBlock() == block;
   }

   private static int rightX(final Direction d) {
      return switch (d) {
         case NORTH -> -1;
         case SOUTH, UP, DOWN -> 1;
         default -> 0;
      };
   }

   private static int rightZ(final Direction d) {
      return switch (d) {
         case EAST -> -1;
         case WEST -> 1;
         default -> 0;
      };
   }

   private static int ctmTile(final BlockAndTintGetter level, final Block block, final BlockPos pos, final BlockPos.MutableBlockPos m, final Direction dir) {
      int rx = rightX(dir);
      int rz = rightZ(dir);
      int ux = 0;
      int uy = 0;
      int uz = 0;
      switch (dir) {
         case UP -> uz = -1;
         case DOWN -> uz = 1;
         default -> uy = 1;
      }
      // offsets in (up, right) space: U, UR, R, DR, D, DL, L, UL
      int[] ur = {1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1};
      int mask = 0;
      for (int i = 0; i < 8; ++i) {
         int a = ur[i * 2];
         int b = ur[i * 2 + 1];
         if (same(level, block, pos, m, ux * a + rx * b, uy * a, uz * a + rz * b)) {
            mask |= 1 << i;
         }
      }
      return CTM_LOOKUP[mask];
   }
}
