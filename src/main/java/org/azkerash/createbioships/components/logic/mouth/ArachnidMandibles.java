package org.azkerash.createbioships.components.logic.mouth;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.behavior.*;

/**
 * Scheduled, collision-checked prey restraint. Victims receive bounded velocity, never
 * teleportation.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class ArachnidMandibles {
  public static final int WINDUP_TICKS = 6, HOLD_TICKS = 60, COOLDOWN_TICKS = 40;
  private static final int BITE_INTERVAL_TICKS = 20;
  private static final double REACH_BLOCKS = 2.25, LEASH_BLOCKS = 4, HOLD_OFFSET_BLOCKS = 1.25;
  private static final double MAX_PREY_WIDTH = 1.5, MAX_PREY_HEIGHT = 2.5, AIM_DOT = .75;
  private static final double PULL_GAIN = .18, MAX_PULL_PER_TICK = .2, MAX_RELATIVE_SPEED = .35;
  private static final float BITE_DAMAGE = 4;

  private record Claim(UUID body, BlockPos mouth, long expires) {}

  // Claims are transient, weakly keyed by the actual mob, and intentionally do not survive loading.
  private static final Map<LivingEntity, Claim> CLAIMS = new WeakHashMap<>();

  public static final class State {
    private UUID prey;
    private long closeAt, releaseAt, biteAt, readyAt;
    private boolean holding;

    public long getReadyAt() {
      return readyAt;
    }

    public void loadCooldown(long value) {
      readyAt = value;
    }

    public UUID getPrey() {
      return prey;
    }

    public boolean isHolding() {
      return holding;
    }

    public boolean isBusy() {
      return prey != null;
    }
  }

  private record Frame(
      ServerSubLevel physical, ServerSubLevel root, Vec3 origin, Vec3 forward, Vec3 anchor) {}

  private ArachnidMandibles() {}

  private static Frame frame(MouthBlockEntity mouth) {
    if (!(mouth.getLevel() instanceof ServerLevel level)
        || !MouthBlock.isArachnid(mouth.getBlockState())
        || !mouth.active()) {
      return null;
    }
    var physical = CoreAssemblyRules.containing(level, mouth.getBlockPos());
    if (physical == null) {
      return null;
    }
    var face = mouth.getBlockState().getValue(MouthBlock.FACING);
    var normal =
        physical
            .logicalPose()
            .orientation()
            .transform(new org.joml.Vector3d(face.getStepX(), face.getStepY(), face.getStepZ()));
    var center = physical.logicalPose().transformPosition(Vec3.atCenterOf(mouth.getBlockPos()));
    var forward = new Vec3(normal.x, normal.y, normal.z);
    return new Frame(
        physical,
        SubLevelCreatureParts.root(physical),
        center.add(forward.scale(.65)),
        forward,
        center.add(forward.scale(HOLD_OFFSET_BLOCKS)));
  }

  private static boolean eligible(Frame frame, LivingEntity prey) {
    return prey != null
        && prey.isAlive()
        && !prey.isPassenger()
        && !(prey instanceof org.azkerash.createbioships.combat.SubLevelCombatTarget)
        && prey.getBbWidth() <= MAX_PREY_WIDTH
        && prey.getBbHeight() <= MAX_PREY_HEIGHT
        && HuntingBehavior.isPrey(
            frame.root, prey, List.of(BuiltInRegistries.ENTITY_TYPE.getKey(prey.getType())));
  }

  private static boolean clear(MouthBlockEntity mouth, Vec3 from, Vec3 to, LivingEntity prey) {
    return mouth
            .getLevel()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    from,
                    to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    prey))
            .getType()
        == HitResult.Type.MISS;
  }

  public static boolean begin(MouthBlockEntity mouth, State state, LivingEntity prey) {
    var f = frame(mouth);
    if (f == null || !eligible(f, prey)) {
      return false;
    }
    if (state.isBusy()) {
      return state.prey.equals(prey.getUUID());
    }
    long now = mouth.getLevel().getGameTime();
    if (now < state.readyAt || mouth.getBlockState().getValue(MouthBlock.SPITTING)) {
      return false;
    }
    var delta = prey.getBoundingBox().getCenter().subtract(f.origin);
    if (delta.length() > REACH_BLOCKS
        || delta.normalize().dot(f.forward) < AIM_DOT
        || !clear(mouth, f.origin, prey.getBoundingBox().getCenter(), prey)) {
      return false;
    }
    var claim = CLAIMS.get(prey);
    if (claim != null && claim.expires > now) {
      return false;
    }
    CLAIMS.put(
        prey,
        new Claim(
            f.root.getUniqueId(),
            mouth.getBlockPos().immutable(),
            now + WINDUP_TICKS + HOLD_TICKS));
    state.prey = prey.getUUID();
    state.closeAt = now + WINDUP_TICKS;
    state.releaseAt = state.closeAt + HOLD_TICKS;
    state.readyAt = state.releaseAt + COOLDOWN_TICKS;
    state.biteAt = state.closeAt;
    mouth
        .getLevel()
        .setBlock(
            mouth.getBlockPos(),
            mouth.getBlockState().setValue(MouthBlock.CHEW, 1),
            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    mouth.getLevel().scheduleTick(mouth.getBlockPos(), mouth.getBlockState().getBlock(), 1);
    mouth.setChanged();
    return true;
  }

  public static boolean animate(MouthBlockEntity mouth, State state) {
    if (!state.isBusy() || !(mouth.getLevel() instanceof ServerLevel level)) {
      return false;
    }
    var f = frame(mouth);
    var entity = level.getEntity(state.prey);
    var prey = entity instanceof LivingEntity living ? living : null;
    long now = level.getGameTime();
    if (f == null
        || !eligible(f, prey)
        || now >= state.releaseAt
        || !HuntingBehavior.ID.equals(BrainBehaviors.active(f.root))
        || !HuntingBehavior.isPrey(f.root, prey, HuntingBehavior.preyTypes(f.root))
        || SubLevelData.hunger(f.root) >= SubLevelData.maximumHunger(f.root)
        || prey.getBoundingBox().getCenter().distanceTo(f.origin)
            > (state.holding ? LEASH_BLOCKS : REACH_BLOCKS)
        || !clear(mouth, f.origin, prey.getBoundingBox().getCenter(), prey)) {
      release(mouth, state);
      return false;
    }
    if (now >= state.closeAt) {
      state.holding = true;
      if (!mouth.getBlockState().getValue(MouthBlock.GRABBING)) {
        level.setBlock(
            mouth.getBlockPos(),
            mouth.getBlockState().setValue(MouthBlock.GRABBING, true).setValue(MouthBlock.CHEW, 0),
            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
      }
      var error = f.anchor.subtract(prey.getBoundingBox().getCenter());
      if (!clear(mouth, prey.getBoundingBox().getCenter(), f.anchor, prey)) {
        release(mouth, state);
        return false;
      }
      var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(f.root);
      var velocity =
          handle == null || !handle.isValid()
              ? new org.joml.Vector3d()
              : handle.getLinearVelocity(new org.joml.Vector3d()).mul(1d / 20);
      var carrier = new Vec3(velocity.x, velocity.y, velocity.z);
      var resistanceAttribute =
          prey.getAttribute(
              net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
      double resistance =
          resistanceAttribute == null ? 0 : Math.clamp(resistanceAttribute.getValue(), 0, 1);
      var pull = error.scale(PULL_GAIN * (1 - resistance));
      if (pull.length() > MAX_PULL_PER_TICK) {
        pull = pull.normalize().scale(MAX_PULL_PER_TICK);
      }
      var relative = prey.getDeltaMovement().subtract(carrier).add(pull);
      if (relative.length() > MAX_RELATIVE_SPEED) {
        relative = relative.normalize().scale(MAX_RELATIVE_SPEED);
      }
      prey.setDeltaMovement(carrier.add(relative));
      prey.hurtMarked = true;
      if (now >= state.biteAt) {
        var attacker = org.azkerash.createbioships.combat.CombatAdapter.target(f.root);
        if (attacker != null) {
          prey.hurt(level.damageSources().mobAttack(attacker), BITE_DAMAGE);
        }
        state.biteAt = now + BITE_INTERVAL_TICKS;
      }
      if (!prey.isAlive()) {
        release(mouth, state);
        return false;
      }
    }
    level.scheduleTick(mouth.getBlockPos(), mouth.getBlockState().getBlock(), 1);
    return true;
  }

  public static void release(MouthBlockEntity mouth, State state) {
    if (mouth.getLevel() == null || mouth.getLevel().isClientSide) {
      return;
    }
    var physical =
        CoreAssemblyRules.containing((ServerLevel) mouth.getLevel(), mouth.getBlockPos());
    var root = physical == null ? null : SubLevelCreatureParts.root(physical);
    CLAIMS
        .entrySet()
        .removeIf(
            e ->
                e.getValue().mouth.equals(mouth.getBlockPos())
                    && (root == null || e.getValue().body.equals(root.getUniqueId())));
    if (state.isBusy()) {
      state.readyAt = mouth.getLevel().getGameTime() + COOLDOWN_TICKS;
    }
    state.prey = null;
    state.holding = false;
    state.closeAt = state.releaseAt = state.biteAt = 0;
    if (mouth.getBlockState().hasProperty(MouthBlock.GRABBING)
        && mouth.getBlockState().getValue(MouthBlock.GRABBING)) {
      mouth
          .getLevel()
          .setBlock(
              mouth.getBlockPos(),
              mouth
                  .getBlockState()
                  .setValue(MouthBlock.GRABBING, false)
                  .setValue(MouthBlock.CHEW, 0),
              net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    mouth.setChanged();
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CLAIMS.clear();
  }
}
