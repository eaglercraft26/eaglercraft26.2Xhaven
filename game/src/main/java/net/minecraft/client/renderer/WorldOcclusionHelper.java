package net.minecraft.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

public final class WorldOcclusionHelper {
   private WorldOcclusionHelper() {
   }

   public static boolean isOccludedByWorld(
      final Vec3 cameraPos,
      final AABB targetBounds,
      final java.util.function.Predicate<BlockPos> solidBlockAt
   ) {
      return isOccludedByWorldInternal(cameraPos, targetBounds, solidBlockAt);
   }

   private static boolean isOccludedByWorldInternal(
      final Vec3 cameraPos,
      final AABB targetBounds,
      final java.util.function.Predicate<BlockPos> solidBlockAt
   ) {
      double centerX = (targetBounds.minX + targetBounds.maxX) * 0.5D;
      double centerY = (targetBounds.minY + targetBounds.maxY) * 0.5D;
      double centerZ = (targetBounds.minZ + targetBounds.maxZ) * 0.5D;
      double[] samplesX = {centerX, targetBounds.minX, targetBounds.maxX};
      double[] samplesY = {centerY, targetBounds.minY, targetBounds.maxY};
      double[] samplesZ = {centerZ, targetBounds.minZ, targetBounds.maxZ};
      Map<BlockPos, Boolean> blockingBlocks = new HashMap<>();

      for (double sampleX : samplesX) {
            for (double sampleY : samplesY) {
            for (double sampleZ : samplesZ) {
                  if (!isRayOccluded(cameraPos.x(), cameraPos.y(), cameraPos.z(), sampleX, sampleY, sampleZ,
                     pos -> cachedSolidBlock(pos, solidBlockAt, blockingBlocks))) {
                     return false;
                  }
            }
         }
      }
      return true;
   }

   public static boolean isRayOccluded(
      final double cameraX,
      final double cameraY,
      final double cameraZ,
      final double targetX,
      final double targetY,
      final double targetZ,
      final java.util.function.Predicate<BlockPos> solidBlockAt
   ) {
      double dx = targetX - cameraX;
      double dy = targetY - cameraY;
      double dz = targetZ - cameraZ;
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (distance < 0.5D) {
         return false;
      }

      int stepX = Double.compare(dx, 0.0D);
      int stepY = Double.compare(dy, 0.0D);
      int stepZ = Double.compare(dz, 0.0D);
      int blockX = Mth.floor(cameraX);
      int blockY = Mth.floor(cameraY);
      int blockZ = Mth.floor(cameraZ);
      double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
      double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
      double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
      double nextX = firstBoundaryTime(cameraX, blockX, dx, stepX);
      double nextY = firstBoundaryTime(cameraY, blockY, dy, stepY);
      double nextZ = firstBoundaryTime(cameraZ, blockZ, dz, stepZ);
      double enteredAt = 0.0D;
      int maxSteps = Math.abs(Mth.floor(targetX) - blockX) + Math.abs(Mth.floor(targetY) - blockY)
         + Math.abs(Mth.floor(targetZ) - blockZ) + 4;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int i = 0; i < maxSteps; ++i) {
         double next = Math.min(1.0D, Math.min(nextX, Math.min(nextY, nextZ)));
         if (enteredAt > 1.0E-9D && next - enteredAt > 1.0E-9D && (enteredAt + next) * 0.5D < 1.0D - 1.0E-9D
            && solidBlockAt.test(pos.set(blockX, blockY, blockZ))) {
            return true;
         }
         if (next >= 1.0D) {
            break;
         }
         double tolerance = Math.max(1.0D, Math.abs(next)) * 1.0E-12D;
         if (nextX <= next + tolerance) {
            blockX += stepX;
            nextX += deltaX;
         }
         if (nextY <= next + tolerance) {
            blockY += stepY;
            nextY += deltaY;
         }
         if (nextZ <= next + tolerance) {
            blockZ += stepZ;
            nextZ += deltaZ;
         }
         enteredAt = next;
      }
      return false;
   }

   private static double firstBoundaryTime(final double coordinate, final int block, final double delta, final int step) {
      if (step == 0) {
         return Double.POSITIVE_INFINITY;
      }
      double boundary = step > 0 ? block + 1.0D : block;
      return (boundary - coordinate) / delta;
   }

   private static boolean cachedSolidBlock(final BlockPos pos, final java.util.function.Predicate<BlockPos> solidBlockAt,
      final Map<BlockPos, Boolean> cache) {
      Boolean cached = cache.get(pos);
      if (cached != null) {
         return cached;
      }
      boolean solid = solidBlockAt.test(pos);
      cache.put(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), solid);
      return solid;
   }

   public static boolean isSolidBlocking(final BlockState state) {
      return state != null && !state.isAir() && state.isSolidRender() && state.canOcclude() && state.getFluidState().isEmpty();
   }
}
