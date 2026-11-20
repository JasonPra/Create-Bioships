package org.azkerash.createbioships.compat.letitwalk;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.legs.SubLevelLegs;

/**
 * Tool-only joint picking. Compact mount blocks keep their separate general-settings interaction.
 */
public final class SocketJointActions {
  public record Target(
      ServerSubLevel owner, BlockPos cell, int joint, boolean compact, String key, Vec3 point) {}

  private SocketJointActions() {}

  public static Target target(ServerPlayer player, BlockPos cell, int joint) {
    var physical = CoreAssemblyRules.containing(player.serverLevel(), cell);
    if (physical == null) {
      return null;
    }
    var owner = SubLevelCreatureParts.root(physical);
    if (!SubLevelData.isLiving(owner)) {
      return null;
    }
    if (CompactLegSocketBlock.isSocket(player.level().getBlockState(cell))) {
      if (joint < 0 || joint > 2 || physical != owner) {
        return null;
      }
      var pose = SubLevelLegs.poses(owner).stream().filter(p -> p.cell().equals(cell)).findFirst();
      if (pose.isEmpty()) {
        return null;
      }
      return new Target(
          owner,
          cell,
          joint,
          true,
          SocketJointSettings.compact(cell, joint),
          owner.logicalPose().transformPosition(pose.get().collisionEndpoints().get(joint * 2)));
    }
    if (joint != 0 || !player.level().getBlockState(cell).is(ComponentRegistry.LIMB_SOCKET.get())) {
      return null;
    }
    return new Target(
        owner,
        cell,
        0,
        false,
        SocketJointSettings.modular(physical, cell),
        physical.logicalPose().transformPosition(Vec3.atCenterOf(cell)));
  }

  public static boolean configure(
      ServerPlayer player, BlockPos cell, int joint, int setting, int delta) {
    if (!LimbPositionerActions.holding(player) || player.isSpectator() || !player.mayBuild()) {
      return false;
    }
    var target = target(player, cell, joint);
    if (target == null || target.point.distanceToSqr(player.getEyePosition()) > 100) {
      return false;
    }
    var before = SocketJointSettings.get(target.owner, target.key);
    var after =
        switch (setting) {
          case 0 ->
              new SocketJointSettings(before.swivel() + Integer.signum(delta) * 15, before.bend());
          case 1 ->
              new SocketJointSettings(before.swivel(), before.bend() + Integer.signum(delta) * 5);
          case 2 -> SocketJointSettings.DEFAULT;
          default -> null;
        };
    if (after == null) {
      return false;
    }
    after.save(target.owner, target.key);
    show(player, target);
    return true;
  }

  public static void show(ServerPlayer player, Target target) {
    var limits = SocketJointSettings.get(target.owner, target.key);
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        player,
        new SocketJointPayload.State(
            target.cell, target.joint, target.compact, limits.swivel(), limits.bend()));
  }

  public static InteractionResult useOn(UseOnContext context) {
    if (context.getLevel().isClientSide) {
      boolean specific =
          context
                  .getLevel()
                  .getBlockState(context.getClickedPos())
                  .is(ComponentRegistry.LIMB_SOCKET.get())
              || context
                  .getLevel()
                  .getBlockState(context.getClickedPos())
                  .is(ComponentRegistry.COMPACT_LIMB.get());
      return specific ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
    if (!(context.getPlayer() instanceof ServerPlayer player)) {
      return InteractionResult.PASS;
    }
    if (context
        .getLevel()
        .getBlockState(context.getClickedPos())
        .is(ComponentRegistry.LIMB_SOCKET.get())) {
      var target = target(player, context.getClickedPos(), 0);
      if (target == null) {
        return InteractionResult.PASS;
      }
      show(player, target);
      return InteractionResult.CONSUME;
    }
    if (context
        .getLevel()
        .getBlockState(context.getClickedPos())
        .is(ComponentRegistry.COMPACT_LIMB.get())) {
      var picked = pick(player);
      if (picked != null) {
        show(player, picked);
        return InteractionResult.CONSUME;
      }
      return InteractionResult.FAIL;
    }
    return InteractionResult.PASS;
  }

  /** Bounded ray/sphere test against visible compact joints, with native world occlusion. */
  public static Target pick(ServerPlayer player) {
    var container = SubLevelContainer.getContainer(player.level());
    if (container == null) {
      return null;
    }
    var eye = player.getEyePosition();
    var view = player.getLookAngle();
    double nearest = 6;
    var hit =
        player
            .level()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    eye,
                    eye.add(view.scale(6)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player));
    if (hit.getType() != HitResult.Type.MISS) {
      var at = hit.getLocation();
      var physical = CoreAssemblyRules.containing(player.serverLevel(), hit.getBlockPos());
      if (physical != null) {
        at = physical.logicalPose().transformPosition(at);
      }
      nearest = Math.min(6, at.distanceTo(eye) + .25);
    }
    Target best = null;
    for (var raw : container.getAllSubLevels()) {
      if (!(raw instanceof ServerSubLevel owner)
          || !SubLevelData.isLiving(owner)
          || SubLevelCreatureParts.isPart(owner)) {
        continue;
      }
      var bounds = owner.boundingBox();
      if (eye.x < bounds.minX() - 10
          || eye.x > bounds.maxX() + 10
          || eye.y < bounds.minY() - 10
          || eye.y > bounds.maxY() + 10
          || eye.z < bounds.minZ() - 10
          || eye.z > bounds.maxZ() + 10) {
        continue;
      }
      for (var pose : SubLevelLegs.poses(owner)) {
        for (int i = 0; i < 3; i++) {
          var point = owner.logicalPose().transformPosition(pose.collisionEndpoints().get(i * 2));
          var to = point.subtract(eye);
          double t = to.dot(view);
          double radius = .25;
          double perpendicular = to.lengthSqr() - t * t;
          if (t < 0 || perpendicular > radius * radius) {
            continue;
          }
          double distance =
              Math.max(0, t - Math.sqrt(Math.max(0, radius * radius - perpendicular)));
          if (distance > nearest) {
            continue;
          }
          nearest = distance;
          best =
              new Target(
                  owner, pose.cell(), i, true, SocketJointSettings.compact(pose.cell(), i), point);
        }
      }
    }
    return best;
  }
}
