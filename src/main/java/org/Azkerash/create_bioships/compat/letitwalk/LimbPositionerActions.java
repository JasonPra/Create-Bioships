package org.Azkerash.create_bioships.compat.letitwalk;

import dev.letitwalk.walker.BuiltLegAssembler;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.logic.legs.ModularJointEntity;
import org.Azkerash.create_bioships.equipment.LimbPositionerItem;

/** Server-authoritative native rest-pose changes, restricted to our own limb components. */
public final class LimbPositionerActions {
  public static final int RESET = 4;

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
            > 100) return false;
    if (!(player.level().getBlockEntity(pos) instanceof ModularJointEntity joint)) return false;
    int before = joint.restOffset();
    joint.setRestOffset(before + Math.clamp(degrees, -15, 15));
    if (before == joint.restOffset()) return false;
    BuiltLegAssembler.markEdited();
    return true;
  }

  public static boolean configure(ServerPlayer player, BlockPos pos, int setting, int delta) {
    if (!holding(player)
        || player.isSpectator()
        || !player.mayBuild()
        || Sable.HELPER.distanceSquaredWithSubLevels(
                player.level(), player.position(), Vec3.atCenterOf(pos))
            > 100) return false;
    var level = player.serverLevel();
    var state = level.getBlockState(pos);
    boolean compact =
        state.is(org.Azkerash.create_bioships.components.ComponentRegistry.CRAB_LEG.get());
    if (!compact && !(level.getBlockEntity(pos) instanceof ModularJointEntity)) return false;
    if ((setting == 2 || setting == 3)
        && org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(level, pos) == null)
      return false;
    var tuning = LimbTuning.get(level, pos);
    int direction = Integer.signum(delta);
    if (setting == RESET) {
      LimbTuning.clear(level, pos);
      if (compact)
        level.setBlock(
            pos,
            state.setValue(
                org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT, 1),
            3);
      else {
        ((ModularJointEntity) level.getBlockEntity(pos)).setRestOffset(0);
        BuiltLegAssembler.markEdited();
      }
    } else if (setting == 0 && compact)
      level.setBlock(
          pos,
          state.setValue(
              org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT,
              Math.clamp(
                  state.getValue(
                          org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT)
                      + direction,
                  1,
                  3)),
          3);
    else if (setting == 1) {
      if (compact)
        new LimbTuning(
                Math.clamp(tuning.restAngle() + Math.clamp(delta, -15, 15), -45, 45),
                tuning.stiffness(),
                tuning.damping())
            .save(level, pos);
      else adjust(player, pos, delta);
    } else if (setting == 2)
      new LimbTuning(tuning.restAngle(), tuning.stiffness() + direction * .25, tuning.damping())
          .save(level, pos);
    else if (setting == 3)
      new LimbTuning(tuning.restAngle(), tuning.stiffness(), tuning.damping() + direction * .25)
          .save(level, pos);
    else return false;
    show(player, pos);
    return true;
  }

  private static void show(ServerPlayer player, BlockPos pos) {
    var level = player.serverLevel();
    var state = level.getBlockState(pos);
    boolean compact =
        state.is(org.Azkerash.create_bioships.components.ComponentRegistry.CRAB_LEG.get());
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
            org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(level, pos) != null,
            compact
                ? state.getValue(
                    org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT)
                : 0,
            angle,
            tuning.stiffness(),
            tuning.damping()));
  }

  public static InteractionResult use(UseOnContext context) {
    var level = context.getLevel();
    var pos = context.getClickedPos();
    if (!(level.getBlockEntity(pos) instanceof ModularJointEntity)
        && !level
            .getBlockState(pos)
            .is(org.Azkerash.create_bioships.components.ComponentRegistry.CRAB_LEG.get()))
      return InteractionResult.PASS;
    if (context.getPlayer() instanceof ServerPlayer player) show(player, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
