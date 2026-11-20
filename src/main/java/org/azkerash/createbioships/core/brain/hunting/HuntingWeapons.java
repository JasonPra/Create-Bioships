package org.azkerash.createbioships.core.brain.hunting;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.core.*;

/** Weapon capability boundary: later cannons can register without changing hunting decisions. */
public final class HuntingWeapons {
  public interface Weapon {
    boolean installed(ServerSubLevel member, BlockPos cell);

    boolean attack(ServerSubLevel member, BlockPos cell, LivingEntity prey);
  }

  public record Mount(ServerSubLevel body, BlockPos cell, Weapon weapon) {}

  private static final List<Weapon> WEAPONS = new ArrayList<>();
  private static final double BITE_REACH_BLOCKS = 1.6, AIM_DOT = .85;
  private static final float BITE_DAMAGE = 4;
  private static final int BITE_COOLDOWN_TICKS = 20;
  private static final String BITE_READY = "create_bioships:hunt_bite_ready";

  static {
    register(
        new Weapon() {
          public boolean installed(ServerSubLevel member, BlockPos cell) {
            return member.getLevel().getBlockState(cell).getBlock() instanceof MouthBlock;
          }

          public boolean attack(ServerSubLevel member, BlockPos cell, LivingEntity prey) {
            var level = member.getLevel();
            var state = level.getBlockState(cell);
            if (MouthBlock.isArachnid(state)
                && level.getBlockEntity(cell) instanceof MouthBlockEntity mandibles
                && (mandibles.isHandlingPrey() || mandibles.tryGrabPrey(prey))) {
              return true;
            }
            var facing = state.getValue(MouthBlock.FACING);
            var origin =
                member.logicalPose().transformPosition(Vec3.atCenterOf(cell).relative(facing, .6));
            var axis =
                member
                    .logicalPose()
                    .orientation()
                    .transform(
                        new org.joml.Vector3d(
                            facing.getStepX(), facing.getStepY(), facing.getStepZ()));
            var forward = new Vec3(axis.x, axis.y, axis.z);
            var target = prey.getBoundingBox().getCenter();
            if (target.subtract(origin).normalize().dot(forward) < AIM_DOT) {
              return false;
            }
            if (!(level.getBlockEntity(cell) instanceof MouthBlockEntity mouth)) {
              return false;
            }
            // A bite must reach the actual victim bounds; a ray from the mouth prevents wall hits.
            var end = origin.add(forward.scale(BITE_REACH_BLOCKS));
            var hit = prey.getBoundingBox().inflate(.1).clip(origin, end);
            if (prey.getBoundingBox().contains(origin) || hit.isPresent()) {
              var point = hit.orElse(origin);
              if (level
                      .clip(
                          new net.minecraft.world.level.ClipContext(
                              origin,
                              point,
                              net.minecraft.world.level.ClipContext.Block.COLLIDER,
                              net.minecraft.world.level.ClipContext.Fluid.NONE,
                              prey))
                      .getType()
                  != net.minecraft.world.phys.HitResult.Type.MISS) {
                return false;
              }
              var root = SubLevelCreatureParts.root(member);
              long now = level.getGameTime();
              var data = root.getUserDataTag().copy();
              if (now < data.getLong(BITE_READY)) {
                return false;
              }
              data.putLong(BITE_READY, now + BITE_COOLDOWN_TICKS);
              root.setUserDataTag(data);
              mouth.snapAtPrey();
              var attacker = org.azkerash.createbioships.combat.CombatAdapter.target(root);
              if (attacker != null) {
                return prey.hurt(level.damageSources().mobAttack(attacker), BITE_DAMAGE);
              }
              return false;
            }
            return MouthBlock.isArmored(state)
                && mouth.requestAcidAttack(null) == MawAcidAttack.Result.READY;
          }
        });
  }

  private HuntingWeapons() {}

  public static void register(Weapon weapon) {
    WEAPONS.add(Objects.requireNonNull(weapon));
  }

  public static List<Mount> mounts(ServerSubLevel body) {
    var result = new ArrayList<Mount>();
    for (var member : SubLevelCreatureParts.members(body)) {
      for (var cell : SubLevelHealth.blocks(member)) {
        for (var weapon : WEAPONS) {
          if (weapon.installed(member, cell)) {
            result.add(new Mount(member, cell, weapon));
          }
        }
      }
    }
    return List.copyOf(result);
  }
}
