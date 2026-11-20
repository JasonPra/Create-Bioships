package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.walker.BuiltLegAssembler;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.components.logic.legs.ModularJointEntity;
import org.azkerash.createbioships.equipment.LimbPositionerItem;

/** Server-authoritative native rest-pose changes, restricted to our own limb components. */
public final class LimbPositionerActions {
  // These IDs are shared with the positioner's packet and screen rows.
  public static final int HEIGHT = 0;
  public static final int REST_ANGLE = 1;
  public static final int STIFFNESS = 2;
  public static final int DAMPING = 3;
  public static final int RESET = 4;
  private static final int REACH_BLOCKS = 10;
  private static final int MAX_ADJUSTMENT_DEGREES = 15;
  private static final int MAX_REST_ANGLE_DEGREES = 45;
  private static final double TUNING_STEP = .25;

  private LimbPositionerActions() {}

  public static boolean holding(net.minecraft.world.entity.player.Player player) {
    return player.getMainHandItem().getItem() instanceof LimbPositionerItem
        || player.getOffhandItem().getItem() instanceof LimbPositionerItem;
  }

  public static boolean adjust(ServerPlayer player, BlockPos pos, int degrees) {
    if (!holding(player)
        || player.isSpectator()
        || !player.mayBuild()
        || Sable.HELPER.distanceSquaredWithSubLevels(
                player.level(), player.position(), Vec3.atCenterOf(pos))
            > REACH_BLOCKS * REACH_BLOCKS) {
      return false;
    }
    if (!(player.level().getBlockEntity(pos) instanceof ModularJointEntity joint)) {
      return false;
    }
    int before = joint.restOffset();
    joint.setRestOffset(
        before + Math.clamp(degrees, -MAX_ADJUSTMENT_DEGREES, MAX_ADJUSTMENT_DEGREES));
    if (before == joint.restOffset()) {
      return false;
    }
    BuiltLegAssembler.markEdited();
    return true;
  }

  public static boolean configure(ServerPlayer player, BlockPos pos, int setting, int delta) {
    if (!holding(player)
        || player.isSpectator()
        || !player.mayBuild()
        || Sable.HELPER.distanceSquaredWithSubLevels(
                player.level(), player.position(), Vec3.atCenterOf(pos))
            > REACH_BLOCKS * REACH_BLOCKS) {
      return false;
    }
    var level = player.serverLevel();
    var state = level.getBlockState(pos);
    boolean compact = CompactLegSocketBlock.isSocket(state);
    if (!compact && !(level.getBlockEntity(pos) instanceof ModularJointEntity)) {
      return false;
    }
    if ((setting == STIFFNESS || setting == DAMPING)
        && org.azkerash.createbioships.core.CoreAssemblyRules.containing(level, pos) == null) {
      return false;
    }
    var tuning = LimbTuning.get(level, pos);
    int direction = Integer.signum(delta);
    switch (setting) {
      case RESET -> {
        LimbTuning.clear(level, pos);
        if (compact) {
          level.setBlock(
              pos,
              state.setValue(
                  org.azkerash.createbioships.components.logic.legs.CrabLegBlock.HEIGHT, 1),
              Block.UPDATE_ALL);
        } else {
          ((ModularJointEntity) level.getBlockEntity(pos)).setRestOffset(0);
          BuiltLegAssembler.markEdited();
        }
      }
      case HEIGHT -> {
        if (!compact) {
          return false;
        }
        level.setBlock(
            pos,
            state.setValue(
                org.azkerash.createbioships.components.logic.legs.CrabLegBlock.HEIGHT,
                Math.clamp(
                    state.getValue(
                            org.azkerash.createbioships.components.logic.legs.CrabLegBlock.HEIGHT)
                        + direction,
                    1,
                    3)),
            Block.UPDATE_ALL);
      }
      case REST_ANGLE -> {
        if (compact) {
          new LimbTuning(
                  Math.clamp(
                      tuning.restAngle()
                          + Math.clamp(delta, -MAX_ADJUSTMENT_DEGREES, MAX_ADJUSTMENT_DEGREES),
                      -MAX_REST_ANGLE_DEGREES,
                      MAX_REST_ANGLE_DEGREES),
                  tuning.stiffness(),
                  tuning.damping())
              .save(level, pos);
        } else {
          adjust(player, pos, delta);
        }
      }
      case STIFFNESS -> {
        new LimbTuning(
                tuning.restAngle(), tuning.stiffness() + direction * TUNING_STEP, tuning.damping())
            .save(level, pos);
      }
      case DAMPING -> {
        new LimbTuning(
                tuning.restAngle(), tuning.stiffness(), tuning.damping() + direction * TUNING_STEP)
            .save(level, pos);
      }
      default -> {
        return false;
      }
    }
    show(player, pos);
    return true;
  }

  private static void show(ServerPlayer player, BlockPos pos) {
    var level = player.serverLevel();
    var state = level.getBlockState(pos);
    boolean compact = CompactLegSocketBlock.isSocket(state);
    var tuning = LimbTuning.get(level, pos);
    int angle =
        compact
            ? tuning.restAngle()
            : ((ModularJointEntity) level.getBlockEntity(pos)).restOffset();
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        player,
        new LimbPositionerState(
            pos,
            compact,
            org.azkerash.createbioships.core.CoreAssemblyRules.containing(level, pos) != null,
            compact
                ? state.getValue(
                    org.azkerash.createbioships.components.logic.legs.CrabLegBlock.HEIGHT)
                : 0,
            angle,
            tuning.stiffness(),
            tuning.damping()));
  }

  public static InteractionResult use(UseOnContext context) {
    var level = context.getLevel();
    var pos = context.getClickedPos();
    if (!(level.getBlockEntity(pos) instanceof ModularJointEntity)
        && !CompactLegSocketBlock.isSocket(level.getBlockState(pos))) {
      return InteractionResult.PASS;
    }
    if (context.getPlayer() instanceof ServerPlayer player) {
      show(player, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
