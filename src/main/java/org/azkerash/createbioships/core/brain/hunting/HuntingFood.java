package org.azkerash.createbioships.core.brain.hunting;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Comparator;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;

/** Meat enters real mouth inventory; existing intestinal transport and digestion own eating. */
public final class HuntingFood {
  private static final double SEARCH_RANGE_BLOCKS = 12, PICKUP_REACH_BLOCKS = 2.5;

  private HuntingFood() {}

  private static boolean isMeat(net.minecraft.world.item.ItemStack stack) {
    return stack.is(net.minecraft.tags.ItemTags.MEAT)
        || stack.is(net.minecraft.tags.ItemTags.FISHES);
  }

  public static ItemEntity nearest(ServerSubLevel body) {
    var position = SubLevelPathfinder.position(body);
    return body
        .getLevel()
        .getEntitiesOfClass(
            ItemEntity.class,
            AABB.ofSize(
                position,
                SEARCH_RANGE_BLOCKS * 2,
                SEARCH_RANGE_BLOCKS * 2,
                SEARCH_RANGE_BLOCKS * 2),
            item -> item.isAlive() && !item.hasPickUpDelay() && isMeat(item.getItem()))
        .stream()
        .min(Comparator.comparingDouble(item -> item.distanceToSqr(position)))
        .orElse(null);
  }

  public static boolean collect(ServerSubLevel body, ItemEntity item) {
    if (!item.isAlive() || item.hasPickUpDelay() || !isMeat(item.getItem())) {
      return false;
    }
    for (var member : SubLevelCreatureParts.members(body)) {
      for (var cell : SubLevelHealth.blocks(member)) {
        if (!(body.getLevel().getBlockEntity(cell) instanceof MouthBlockEntity mouth)) {
          continue;
        }
        var face = mouth.getBlockState().getValue(MouthBlock.FACING);
        var origin =
            member.logicalPose().transformPosition(Vec3.atCenterOf(cell).relative(face, .6));
        var destination = item.getBoundingBox().getCenter();
        if (origin.distanceToSqr(destination) > PICKUP_REACH_BLOCKS * PICKUP_REACH_BLOCKS) {
          continue;
        }
        // Do not pull food through terrain, enclosure walls or other Sable structures.
        if (body.getLevel()
                .clip(
                    new net.minecraft.world.level.ClipContext(
                        origin,
                        destination,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        item))
                .getType()
            != HitResult.Type.MISS) {
          continue;
        }
        var stack = item.getItem();
        var remainder = mouth.feed(stack, false);
        if (remainder.getCount() == stack.getCount()) {
          continue;
        }
        if (remainder.isEmpty()) {
          item.discard();
        } else {
          item.setItem(remainder);
        }
        return true;
      }
    }
    return false;
  }
}
