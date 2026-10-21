package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class WorldOcclusionHelperTest {
   @Test
   public void occludedWhenSolidBlockBlocksCameraRay() {
      assertTrue(WorldOcclusionHelper.isRayOccluded(0.0D, 0.0D, 0.0D, 10.0D, 0.0D, 0.0D, blockPos -> blockPos.equals(new BlockPos(5, 0, 0))));
   }

   @Test
   public void visibleWhenRayHasClearLineOfSight() {
      assertFalse(WorldOcclusionHelper.isRayOccluded(0.0D, 0.0D, 0.0D, 10.0D, 0.0D, 0.0D, blockPos -> false));
   }

   @Test
   public void worldOcclusionRequiresEveryBoundsSampleToBeBlocked() {
      AABB bounds = new AABB(10.0D, 0.0D, 0.0D, 11.0D, 3.0D, 3.0D);
      assertFalse(WorldOcclusionHelper.isOccludedByWorld(new Vec3(0.0D, 0.5D, 0.5D), bounds,
         pos -> pos.getX() == 5 && pos.getY() == 0 && pos.getZ() == 0));
   }

   @Test
   public void rayTraversalChecksVoxelsRatherThanRepeatedSubBlockSamples() {
      AtomicInteger blockChecks = new AtomicInteger();
      assertTrue(WorldOcclusionHelper.isRayOccluded(0.0D, 0.0D, 0.0D, 10.0D, 0.0D, 0.0D, pos -> {
         blockChecks.incrementAndGet();
         return pos.getX() == 5 && pos.getY() == 0 && pos.getZ() == 0;
      }));
      assertTrue(blockChecks.get() <= 6);
   }
}
