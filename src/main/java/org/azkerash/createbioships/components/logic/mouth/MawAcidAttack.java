package org.azkerash.createbioships.components.logic.mouth;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.compat.bigcannons.AcidBurstEntity;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelInteractionInterceptor;

/**
 * Server-only firing transaction, reusable by player controls, redstone and future behavior cards.
 */
public final class MawAcidAttack {
  public static final int WINDUP_TICKS = 20, COOLDOWN_TICKS = 40, ACID_COST = 50;
  private static final double MUZZLE_OFFSET = .8, SPEED_BLOCKS_PER_TICK = 1.6, SPREAD = .035;

  public enum Result {
    READY("Opening mouth…"),
    FIRED("Acid spray released."),
    INACTIVE("The Eldritch Maw must belong to a living creature."),
    FORBIDDEN("You do not have permission to fire this creature's maw."),
    EMPTY("Not enough stomach acid (50 required)."),
    BLOCKED("The mouth's opening is obstructed."),
    BUSY("The mouth is recovering or charging."),
    FAILED("The acid spray could not be spawned.");
    private final String message;

    Result(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  private MawAcidAttack() {}

  private static ServerSubLevel body(ServerLevel level, BlockPos pos) {
    var physical = CoreAssemblyRules.containing(level, pos);
    return physical == null ? null : SubLevelCreatureParts.root(physical);
  }

  private static List<StomachBlockEntity> stomachs(ServerSubLevel root) {
    var result = new ArrayList<StomachBlockEntity>();
    for (var member : SubLevelCreatureParts.members(root)) {
      for (var cell : SubLevelHealth.blocks(member)) {
        if (member.getLevel().getBlockEntity(cell) instanceof StomachBlockEntity stomach) {
          result.add(stomach);
        }
      }
    }
    result.sort(Comparator.comparingLong(stomach -> stomach.getBlockPos().asLong()));
    return result;
  }

  public static Result validate(ServerLevel level, BlockPos pos, UUID actor) {
    var root = body(level, pos);
    if (!MouthBlock.isArmored(level.getBlockState(pos))
        || root == null
        || !SubLevelData.isLiving(root)
        || SubLevelHealth.status(root).dead()) {
      return Result.INACTIVE;
    }
    if (actor != null && !SubLevelInteractionInterceptor.canModify(actor, root)) {
      return Result.FORBIDDEN;
    }
    if (stomachs(root).stream().mapToInt(StomachBlockEntity::acid).sum() < ACID_COST) {
      return Result.EMPTY;
    }
    var state = level.getBlockState(pos);
    var face = state.getValue(MouthBlock.FACING);
    // This is a plot-local check: never launch from inside a neighbouring piece of the same hull.
    var next = pos.relative(face);
    if (!level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
      return Result.BLOCKED;
    }
    return Result.READY;
  }

  public static Result release(ServerLevel level, BlockPos pos, UUID actor) {
    var ready = validate(level, pos, actor);
    if (ready != Result.READY) {
      return ready;
    }
    var root = body(level, pos);
    var physical = CoreAssemblyRules.containing(level, pos);
    var face = level.getBlockState(pos).getValue(MouthBlock.FACING);
    var muzzle =
        physical
            .logicalPose()
            .transformPosition(Vec3.atCenterOf(pos).relative(face, MUZZLE_OFFSET));
    var normal =
        physical
            .logicalPose()
            .orientation()
            .transform(new org.joml.Vector3d(face.getStepX(), face.getStepY(), face.getStepZ()));
    var direction = new Vec3(normal.x, normal.y, normal.z).normalize();
    var burst = CreateBioships.ACID_BURST.get().create(level);
    if (burst == null) {
      return Result.FAILED;
    }
    burst.setPos(muzzle);
    burst.setSourceBody(root.getUniqueId());
    burst.setOwner(org.azkerash.createbioships.combat.CombatAdapter.target(root));
    var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(physical);
    var inherited =
        handle == null
            ? new org.joml.Vector3d()
            : handle.getLinearVelocity(new org.joml.Vector3d()).div(20);
    for (int i = 0; i < AcidBurstEntity.DROPLETS; i++) {
      var shot =
          direction
              .add(
                  (level.random.nextDouble() - .5) * SPREAD,
                  (level.random.nextDouble() - .5) * SPREAD,
                  (level.random.nextDouble() - .5) * SPREAD)
              .normalize()
              .scale(SPEED_BLOCKS_PER_TICK)
              .add(inherited.x, inherited.y, inherited.z);
      burst.addSubProjectile(0, 0, 0, shot.x, shot.y, shot.z);
    }
    if (!level.addFreshEntity(burst)) {
      return Result.FAILED;
    }
    // Commit ammunition only after the server accepts the CBC projectile entity.
    int remaining = ACID_COST;
    for (var stomach : stomachs(root)) {
      int consumed = Math.min(remaining, stomach.acid());
      stomach.state().setAcid(stomach.acid() - consumed);
      stomach.setChanged();
      remaining -= consumed;
      if (remaining == 0) {
        break;
      }
    }
    level.playSound(
        null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS, 1, .65f);
    return Result.FIRED;
  }
}
