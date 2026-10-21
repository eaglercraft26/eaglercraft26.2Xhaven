package net.minecraft.client.renderer.block;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Resource-pack implementation of the OptiFine/Continuity connected-texture properties subset. */
public final class ConnectedTextures {
   private static final Logger LOGGER = LogUtils.getLogger();
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
   private static final int[][] CTM_OFFSETS = {
      {0,1}, {1,1}, {1,0}, {1,-1}, {0,-1}, {-1,-1}, {-1,0}, {-1,1}
   };
   private static final String RESOURCE_DIR = "optifine/ctm";
   private static volatile List<Rule> rules = List.of();
   private static volatile Map<Identifier, TextureAtlasSprite> emissiveSprites = Map.of();

   private ConnectedTextures() {
   }

   static int horizontalTile(final boolean left, final boolean right) {
      return left && right ? 1 : left ? 2 : right ? 0 : 3;
   }

   static int verticalTile(final boolean up, final boolean down) {
      return up && down ? 1 : up ? 2 : down ? 0 : 3;
   }

   public static void loadDefinitions(final ResourceManager manager) {
      List<Rule> loaded = new ArrayList<>();
      manager.listResources(RESOURCE_DIR, id -> id.getPath().endsWith(".properties"))
         .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
               Properties properties = new Properties();
               properties.load(reader);
               Rule rule = parse(entry.getKey(), properties, manager);
               if (rule != null) {
                  loaded.add(rule);
               }
            } catch (Exception exception) {
               LOGGER.warn("Ignoring invalid connected-texture definition {}", entry.getKey(), exception);
            }
         });
      loaded.sort(java.util.Comparator
         .comparing((Rule rule) -> "continuity".equals(rule.location.getNamespace()))
         .thenComparing(rule -> rule.conditionPack == null));
      rules = List.copyOf(loaded);
      emissiveSprites = Map.of();
   }

   public static void addSpriteLoaders(final ResourceManager manager, final SpriteResourceLoaderAccess loaderAccess,
      final List<SpriteSource.Loader> loaders) {
      loadDefinitions(manager);
      Set<Identifier> spriteIds = new HashSet<>();
      for (Rule rule : rules) {
         for (Tile tile : rule.tiles) {
            if (!tile.textureFile.getPath().startsWith("textures/block/") && spriteIds.add(tile.spriteId)) {
               manager.getResource(tile.textureFile).ifPresent(resource ->
                  loaders.add(loaderAccess.loader(tile.spriteId, resource)));
            }
         }
      }
   }

   @FunctionalInterface
   public interface SpriteResourceLoaderAccess {
      SpriteSource.Loader loader(Identifier spriteId, Resource resource);
   }

   private static Rule parse(final Identifier location, final Properties properties, final ResourceManager manager) {
      String methodName = properties.getProperty("method", "").trim().toLowerCase(Locale.ROOT);
      Method method = Method.fromName(methodName);
      if (method == null) {
         LOGGER.warn("Unsupported connected-texture method '{}' in {}", methodName, location);
         return null;
      }

      Set<String> matchBlocks = split(properties.getProperty("matchBlocks", ""));
      Set<Identifier> matchTiles = new HashSet<>();
      for (String tile : split(properties.getProperty("matchTiles", ""))) {
         Identifier id = normalizeSpriteId(tile, "minecraft");
         if (id != null) {
            matchTiles.add(id);
         }
      }
      if (matchBlocks.isEmpty() && matchTiles.isEmpty()) {
         LOGGER.warn("Connected-texture definition {} has neither matchBlocks nor matchTiles", location);
         return null;
      }

      String conditionPack = null;
      String resourceCondition = properties.getProperty("resourceCondition");
      if (resourceCondition != null && !resourceCondition.isBlank()) {
         String conditionPath = resourceCondition.trim();
         int packSeparator = conditionPath.lastIndexOf('@');
         if (packSeparator >= 0) {
            conditionPack = conditionPath.substring(packSeparator + 1);
            conditionPath = conditionPath.substring(0, packSeparator);
         }
         Identifier conditionId = Identifier.tryParse(conditionPath.indexOf(':') < 0 ? "minecraft:" + conditionPath : conditionPath);
         if (conditionId == null) {
            LOGGER.warn("Invalid resourceCondition in {}", location);
            return null;
         }
         List<Resource> conditionResources = manager.getResourceStack(conditionId);
         if (conditionResources.isEmpty()) {
            return null;
         }
         if (conditionPack != null) {
            boolean matchingPack = false;
            for (Resource resource : conditionResources) {
               matchingPack |= resource.sourcePackId().contains(conditionPack);
            }
            if (!matchingPack) {
               return null;
            }
         }
      }

      Set<Direction> faces = parseFaces(properties.getProperty("faces", "all"));
      List<Tile> tiles = new ArrayList<>();
      for (String tileName : expandTileRanges(split(properties.getProperty("tiles", "")))) {
         List<Identifier> resolved = resolveTile(location, tileName, manager);
         if (resolved.isEmpty()) {
            LOGGER.warn("Connected-texture tile '{}' referenced by {} is missing", tileName, location);
            return null;
         }
         for (Identifier textureFile : resolved) {
            String filePath = textureFile.getPath();
            String path;
            if (filePath.startsWith("textures/")) {
               path = filePath.substring("textures/".length(), filePath.length() - ".png".length());
            } else if (filePath.startsWith(RESOURCE_DIR + "/")) {
               path = "ctm/" + filePath.substring((RESOURCE_DIR + "/").length(), filePath.length() - ".png".length());
            } else {
               path = "ctm/" + filePath.substring(0, filePath.length() - ".png".length());
            }
            Identifier spriteId = Identifier.fromNamespaceAndPath(textureFile.getNamespace(), path);
            tiles.add(new Tile(textureFile, spriteId));
         }
      }
      int expected = method.minimumTiles;
      if (tiles.size() < expected) {
         LOGGER.warn("Connected-texture definition {} needs at least {} tiles; found {}", location, expected, tiles.size());
         return null;
      }
      if (method == Method.REPEAT) {
         int width = parsePositive(properties.getProperty("width"), 1);
         int height = parsePositive(properties.getProperty("height"), 1);
         if ((long)width * height > tiles.size()) {
            LOGGER.warn("Invalid repeat dimensions in connected-texture definition {}", location);
            return null;
         }
         Connect connect = parseConnect(properties, location);
         if (connect == null) return null;
         return new Rule(location, conditionPack, method, matchBlocks, matchTiles, connect, faces, tiles, width, height,
            parseWeights(properties.getProperty("weights"), tiles.size()));
      }
      Connect connect = parseConnect(properties, location);
      if (connect == null) return null;
      return new Rule(location, conditionPack, method, matchBlocks, matchTiles, connect, faces, tiles, 1, 1,
         parseWeights(properties.getProperty("weights"), tiles.size()));
   }

   private static List<Identifier> resolveTile(final Identifier propertiesLocation, final String tileName, final ResourceManager manager) {
      String namespace = propertiesLocation.getNamespace();
      String parent = propertiesLocation.getPath().substring(0, propertiesLocation.getPath().lastIndexOf('/') + 1);
      List<Identifier> candidates = new ArrayList<>();
      if (tileName.matches("\\d+")) {
         candidates.add(Identifier.fromNamespaceAndPath(namespace, parent + tileName + ".png"));
         String file = propertiesLocation.getPath().substring(propertiesLocation.getPath().lastIndexOf('/') + 1);
         String stem = file.substring(0, file.length() - ".properties".length());
         candidates.add(Identifier.fromNamespaceAndPath("minecraft", "textures/block/ctm/" + stem + "/" + tileName + ".png"));
      } else {
         String value = tileName.replace('\\', '/');
         String tileNamespace = namespace;
         int colon = value.indexOf(':');
         if (colon >= 0) {
            tileNamespace = value.substring(0, colon);
            value = value.substring(colon + 1);
         }
         if (!value.endsWith(".png")) {
            value += ".png";
         }
         if (value.startsWith("textures/")) {
            candidates.add(Identifier.fromNamespaceAndPath(tileNamespace, value));
         } else {
            String texturePath = value.startsWith("block/") || value.startsWith("item/") ? "textures/" + value : parent + value;
            candidates.add(Identifier.fromNamespaceAndPath(tileNamespace, texturePath));
         }
      }
      for (Identifier candidate : candidates) {
         if (manager.getResource(candidate).isPresent()) {
            return List.of(candidate);
         }
      }
      return List.of();
   }

   private static Identifier normalizeSpriteId(final String input, final String defaultNamespace) {
      String value = input;
      int colon = value.indexOf(':');
      String namespace = colon < 0 ? defaultNamespace : value.substring(0, colon);
      if (colon >= 0) {
         value = value.substring(colon + 1);
      }
      if (value.endsWith(".png")) {
         value = value.substring(0, value.length() - 4);
      }
      if (value.startsWith("textures/")) {
         value = value.substring("textures/".length());
      }
      if (value.startsWith("block/") || value.startsWith("item/")) {
         return Identifier.tryParse(namespace + ":" + value);
      }
      return Identifier.tryParse(namespace + ":block/" + value);
   }

   static Set<String> split(final String value) {
      Set<String> result = new java.util.LinkedHashSet<>();
      for (String token : value.trim().split("[,\\s]+")) {
         if (!token.isBlank()) {
            result.add(token);
         }
      }
      return result;
   }

   static Set<Direction> parseFaces(final String input) {
      Set<Direction> result = new HashSet<>();
      for (String face : split(input.toLowerCase(Locale.ROOT))) {
         switch (face) {
            case "all" -> result.addAll(Arrays.asList(Direction.values()));
            case "sides" -> {
               result.add(Direction.NORTH);
               result.add(Direction.SOUTH);
               result.add(Direction.EAST);
               result.add(Direction.WEST);
            }
            case "top" -> result.add(Direction.UP);
            case "bottom" -> result.add(Direction.DOWN);
            case "north" -> result.add(Direction.NORTH);
            case "south" -> result.add(Direction.SOUTH);
            case "east" -> result.add(Direction.EAST);
            case "west" -> result.add(Direction.WEST);
            default -> {
               return Set.of();
            }
         }
      }
      return result;
   }

   private static Connect parseConnect(final Properties properties, final Identifier location) {
      String value = properties.getProperty("connect", "block").trim().toLowerCase(Locale.ROOT);
      if ("block".equals(value)) {
         return new Connect(split(properties.getProperty("connectBlocks", "")));
      }
      if ("tile".equals(value)) {
         LOGGER.warn("Connected-texture connect=tile is not supported in {}", location);
         return null;
      }
      LOGGER.warn("Unsupported connected-texture connect method '{}' in {}", value, location);
      return null;
   }

   static Set<String> expandTileRanges(final Set<String> source) {
      Set<String> result = new java.util.LinkedHashSet<>();
      for (String value : source) {
         int dash = value.indexOf('-');
         if (dash > 0) {
            try {
               int first = Integer.parseInt(value.substring(0, dash));
               int last = Integer.parseInt(value.substring(dash + 1));
               if (first <= last && last - first < 256) {
                  for (int i = first; i <= last; ++i) {
                     result.add(Integer.toString(i));
                  }
                  continue;
               }
            } catch (NumberFormatException ignored) {
            }
         }
         result.add(value);
      }
      return result;
   }

   private static int parsePositive(final String value, final int fallback) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed > 0 ? parsed : fallback;
      } catch (NumberFormatException ignored) {
         return fallback;
      }
   }

   private static int[] parseWeights(final String value, final int count) {
      int[] result = new int[count];
      Arrays.fill(result, 1);
      if (value == null || value.isBlank()) {
         return result;
      }
      String[] values = value.trim().split("[,\\s]+");
      for (int i = 0; i < Math.min(values.length, count); i++) {
         try {
            result[i] = Math.max(0, Integer.parseInt(values[i]));
         } catch (NumberFormatException ignored) {
            result[i] = 1;
         }
      }
      return result;
   }

   public static void populate(final TextureAtlas atlas) {
      for (Rule rule : rules) {
         rule.resolve(atlas);
      }
      Map<Identifier, TextureAtlasSprite> emissive = new HashMap<>();
      for (Map.Entry<Identifier, TextureAtlasSprite> entry : atlas.spritesByName().entrySet()) {
         Identifier id = entry.getKey();
         Identifier base = emissiveBaseId(id);
         if (base != null) {
            emissive.put(base, entry.getValue());
         }
      }
      emissiveSprites = Map.copyOf(emissive);
   }

   static Identifier emissiveBaseId(final Identifier id) {
      String suffix = id.getPath().endsWith("_emissive") ? "_emissive" : id.getPath().endsWith("_e") ? "_e" : null;
      return suffix == null ? null : Identifier.fromNamespaceAndPath(id.getNamespace(),
         id.getPath().substring(0, id.getPath().length() - suffix.length()));
   }

   static int definitionCount() {
      return rules.size();
   }

   public static TextureAtlasSprite emissiveSprite(final TextureAtlasSprite sprite) {
      return emissiveSprites.get(sprite.contents().name());
   }

   static Cache createCache(final BlockAndTintGetter level, final BlockState state, final BlockPos pos) {
      return new Cache(level, state, pos);
   }

   static BakedQuad apply(final Cache cache, final BakedQuad quad) {
      TextureAtlasSprite destination = cache.destination(quad);
      return destination == null ? quad : remapToSprite(quad, destination);
   }

   public static BakedQuad remapToSprite(final BakedQuad quad, final TextureAtlasSprite destination) {
      TextureAtlasSprite source = quad.materialInfo().sprite();
      return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
         mapUv(quad.packedUV0(), source, destination), mapUv(quad.packedUV1(), source, destination),
         mapUv(quad.packedUV2(), source, destination), mapUv(quad.packedUV3(), source, destination),
         quad.direction(), new BakedQuad.MaterialInfo(destination, quad.materialInfo().layer(), quad.materialInfo().itemRenderType(),
            quad.materialInfo().tintIndex(), quad.materialInfo().shade(), quad.materialInfo().lightEmission()));
   }

   private static long mapUv(final long packed, final TextureAtlasSprite source, final TextureAtlasSprite destination) {
      float fu = (UVPair.unpackU(packed) - source.getU0()) / (source.getU1() - source.getU0());
      float fv = (UVPair.unpackV(packed) - source.getV0()) / (source.getV1() - source.getV0());
      return UVPair.pack(destination.getU0() + fu * (destination.getU1() - destination.getU0()),
         destination.getV0() + fv * (destination.getV1() - destination.getV0()));
   }

   static boolean matchesBlock(final Set<String> patterns, final Identifier blockId) {
      if (patterns.isEmpty()) {
         return true;
      }
      String id = blockId.toString();
      for (String pattern : patterns) {
         String normalized = pattern.indexOf(':') < 0 ? "minecraft:" + pattern : pattern;
         if (normalized.equals(id) || wildcardMatch(normalized, id)) {
            return true;
         }
      }
      return false;
   }

   private static boolean wildcardMatch(final String pattern, final String value) {
      int star = pattern.indexOf('*');
      return star >= 0 && value.startsWith(pattern.substring(0, star)) && value.endsWith(pattern.substring(star + 1));
   }

   private static boolean isConnected(final BlockAndTintGetter level, final BlockState currentState, final BlockPos pos,
      final BlockPos.MutableBlockPos scratch, final int dx, final int dy, final int dz, final Connect connect) {
      BlockState neighbor = level.getBlockState(scratch.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
      if (!connect.blocks.isEmpty()) {
         return matchesBlock(connect.blocks, BuiltInRegistries.BLOCK.getKey(neighbor.getBlock()));
      }
      return neighbor.getBlock() == currentState.getBlock();
   }

   private static int horizontalRightX(final Direction direction) {
      return switch (direction) {
         case NORTH -> -1;
         case SOUTH -> 1;
         case UP, DOWN -> 1;
         default -> 0;
      };
   }

   private static int horizontalRightZ(final Direction direction) {
      return switch (direction) {
         case EAST -> -1;
         case WEST -> 1;
         default -> 0;
      };
   }

   static BlockPos faceOffset(final Direction face, final int rightStep, final int upStep) {
      int upX = 0;
      int upY = 0;
      int upZ = 0;
      switch (face) {
         case UP -> upZ = -1;
         case DOWN -> upZ = 1;
         default -> upY = 1;
      }
      return new BlockPos(
         upX * upStep + horizontalRightX(face) * rightStep,
         upY * upStep,
         upZ * upStep + horizontalRightZ(face) * rightStep
      );
   }

   enum Method {
      CTM(47), HORIZONTAL(4), VERTICAL(4), TOP(1), FIXED(1), RANDOM(1), REPEAT(1);

      private final int minimumTiles;

      Method(final int minimumTiles) {
         this.minimumTiles = minimumTiles;
      }

      static Method fromName(final String value) {
         return switch (value) {
            case "ctm" -> CTM;
            case "horizontal" -> HORIZONTAL;
            case "vertical" -> VERTICAL;
            case "top" -> TOP;
            case "fixed" -> FIXED;
            case "random" -> RANDOM;
            case "repeat" -> REPEAT;
            default -> null;
         };
      }
   }

   private record Tile(Identifier textureFile, Identifier spriteId) {
   }

   private record SpriteFaceKey(Identifier sprite, Direction face) {
   }

   private record RuleFaceKey(Rule rule, Direction face) {
   }

   private record Connect(Set<String> blocks) {
   }

   static final class Cache {
      private final BlockAndTintGetter level;
      private final BlockState state;
      private final BlockPos pos;
      private final Identifier blockId;
      private final List<Rule> rules;
      private final Map<SpriteFaceKey, TextureAtlasSprite> destinations = new HashMap<>();
      private final Map<RuleFaceKey, Integer> tiles = new HashMap<>();

      private Cache(final BlockAndTintGetter level, final BlockState state, final BlockPos pos) {
         this.level = level;
         this.state = state;
         this.pos = pos.immutable();
         this.blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
         this.rules = ConnectedTextures.rules;
      }

      private TextureAtlasSprite destination(final BakedQuad quad) {
         Identifier spriteId = quad.materialInfo().sprite().contents().name();
         Direction face = quad.direction();
         SpriteFaceKey spriteFace = new SpriteFaceKey(spriteId, face);
         if (this.destinations.containsKey(spriteFace)) {
            return this.destinations.get(spriteFace);
         }
         for (Rule rule : this.rules) {
            if (!rule.matches(this.blockId, spriteId) || !rule.faces.contains(face)) {
               continue;
            }
            int tile = this.tiles.computeIfAbsent(new RuleFaceKey(rule, face),
               ignored -> rule.chooseTile(this.level, this.state, this.pos, face));
            if (tile >= 0 && tile < rule.resolvedTiles.length && rule.resolvedTiles[tile] != null) {
               TextureAtlasSprite destination = rule.resolvedTiles[tile];
               this.destinations.put(spriteFace, destination);
               return destination;
            }
         }
         this.destinations.put(spriteFace, null);
         return null;
      }
   }

   private static final class Rule {
      private final Identifier location;
      private final String conditionPack;
      private final Method method;
      private final Set<String> matchBlocks;
      private final Set<Identifier> matchTiles;
      private final Connect connect;
      private final Set<Direction> faces;
      private final List<Tile> tiles;
      private final int width;
      private final int height;
      private final int[] weights;
      private TextureAtlasSprite[] resolvedTiles;

      private Rule(final Identifier location, final String conditionPack, final Method method, final Set<String> matchBlocks,
         final Set<Identifier> matchTiles, final Connect connect,
         final Set<Direction> faces, final List<Tile> tiles, final int width, final int height, final int[] weights) {
         this.location = location;
         this.conditionPack = conditionPack;
         this.method = method;
         this.matchBlocks = Set.copyOf(matchBlocks);
         this.matchTiles = Set.copyOf(matchTiles);
         this.connect = connect;
         this.faces = Set.copyOf(faces);
         this.tiles = List.copyOf(tiles);
         this.width = width;
         this.height = height;
         this.weights = weights;
      }

      private void resolve(final TextureAtlas atlas) {
         this.resolvedTiles = new TextureAtlasSprite[this.tiles.size()];
         TextureAtlasSprite missing = atlas.missingSprite();
         for (int i = 0; i < this.tiles.size(); i++) {
            TextureAtlasSprite sprite = atlas.getSprite(this.tiles.get(i).spriteId);
            if (sprite != missing) {
               this.resolvedTiles[i] = sprite;
            } else {
               LOGGER.warn("Connected-texture sprite {} is missing", this.tiles.get(i).spriteId);
            }
         }
      }

      private boolean matches(final Identifier blockId, final Identifier spriteId) {
         return matchesBlock(this.matchBlocks, blockId) && (this.matchTiles.isEmpty() || this.matchTiles.contains(spriteId));
      }

      private int chooseTile(final BlockAndTintGetter level, final BlockState state, final BlockPos pos, final Direction face) {
         BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
         return switch (this.method) {
            case CTM -> CTM_LOOKUP[this.ctmMask(level, state, pos, face, scratch)];
            case HORIZONTAL -> face.getAxis().isVertical() ? -1 :
               horizontalTile(this.connectedInFace(level, state, pos, face, scratch, -1, 0),
                  this.connectedInFace(level, state, pos, face, scratch, 1, 0));
            case VERTICAL -> verticalTile(this.connectedInFace(level, state, pos, face, scratch, 0, 1),
               this.connectedInFace(level, state, pos, face, scratch, 0, -1));
            case TOP -> face.getAxis().isVertical() || !isConnected(level, state, pos, scratch, 0, 1, 0, this.connect) ? -1 : 0;
            case FIXED -> 0;
            case RANDOM -> this.randomTile(pos);
            case REPEAT -> Math.floorMod(blockCoord(pos, face, true), this.width)
               + Math.floorMod(blockCoord(pos, face, false), this.height) * this.width;
         };
      }

      private int ctmMask(final BlockAndTintGetter level, final BlockState state, final BlockPos pos, final Direction face,
         final BlockPos.MutableBlockPos scratch) {
         boolean[] edges = new boolean[8];
         for (int i = 0; i < 8; i += 2) {
            int[] offset = CTM_OFFSETS[i];
            edges[i] = this.connectedInFace(level, state, pos, face, scratch, offset[1], offset[0]);
         }
         int mask = 0;
         for (int i = 0; i < 8; i += 2) {
            if (edges[i]) {
               mask |= 1 << i;
            }
         }
         for (int i = 1; i < 8; i += 2) {
            int previous = (i + 7) % 8;
            int next = (i + 1) % 8;
            if (edges[previous] && edges[next]) {
               int[] offset = CTM_OFFSETS[i];
               if (this.connectedInFace(level, state, pos, face, scratch, offset[1], offset[0])) {
                  mask |= 1 << i;
               }
            }
         }
         return mask;
      }

      private boolean connectedInFace(final BlockAndTintGetter level, final BlockState state, final BlockPos pos,
         final Direction face, final BlockPos.MutableBlockPos scratch, final int rightStep, final int upStep) {
         BlockPos offset = faceOffset(face, rightStep, upStep);
         return isConnected(level, state, pos, scratch, offset.getX(), offset.getY(), offset.getZ(), this.connect);
      }

      private int randomTile(final BlockPos pos) {
         long seed = (long)(pos.getX() * 3129871) ^ (long)pos.getZ() * 116129781L ^ pos.getY();
         seed = seed * seed * 42317861L + seed * 11L;
         int total = 0;
         for (int weight : this.weights) {
            total += weight;
         }
         if (total <= 0) {
            return 0;
         }
         int pick = Mth.abs((int)(seed >> 16)) % total;
         for (int i = 0; i < this.weights.length; i++) {
            pick -= this.weights[i];
            if (pick < 0) {
               return i;
            }
         }
         return 0;
      }

      private static int blockCoord(final BlockPos pos, final Direction face, final boolean horizontal) {
         if (face.getAxis().isVertical()) {
            return horizontal ? pos.getX() : pos.getZ();
         }
         return horizontal ? (face.getAxis() == Direction.Axis.Z ? pos.getX() : pos.getZ()) : pos.getY();
      }
   }
}
