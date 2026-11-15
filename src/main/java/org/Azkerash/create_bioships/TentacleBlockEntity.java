package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** One flagellum: swimming muscles, bounded grabbing, and mouth-gated feeding. */
public class TentacleBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
  private static final TagKey<EntityType<?>> PREY =
      TagKey.create(
          Registries.ENTITY_TYPE,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "edible_entities"));
  private UUID target;
  private final TentacleStrike strike = new TentacleStrike();

  public TentacleStrike strike() {
    return strike;
  }

  private int clientTarget = -1, heldTicks, cooldown;
  private long lastTick = Long.MIN_VALUE;

  public TentacleBlockEntity(BlockPos pos, BlockState state) {
    super(Create_bioships.TENTACLE_ENTITY.get(), pos, state);
  }

  public boolean swimming() {
    return target == null;
  }

  public int targetEntityId() {
    return clientTarget;
  }

  public UUID targetId() {
    return target;
  }

  private ServerSubLevel ship() {
    if (!(level instanceof ServerLevel server)) return null;
    var container = SubLevelContainer.getContainer(server);
    if (container == null || !container.inBounds(worldPosition)) return null;
    var plot = container.getPlot(new ChunkPos(worldPosition));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel ship ? ship : null;
  }

  public LivingCoreBlockEntity owner(ServerSubLevel ship) {
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.isMature())
        for (BlockPos offset : DinghyGrowth.FLAGELLA)
          if (core.layoutPos(offset).equals(worldPosition)) return core;
    return null;
  }

  static boolean attackable(LivingEntity entity) {
    return !(entity instanceof BioshipTargetEntity)
        && entity.isAlive()
        && !entity.isSpectator()
        && !entity.isInvulnerable()
        && !(entity instanceof Player player && player.isCreative());
  }

  boolean valid(LivingEntity entity, LivingCoreBlockEntity core) {
    if (entity instanceof net.minecraft.world.entity.monster.Creeper
        && core.autonomy().creeperPanic()) return false;
    if (core.brain().attackTarget(core) != entity) return false;
    if (!attackable(entity) || entity.isPassenger() && !core.aggressive()) return false;
    if (core.aggressive()) return true;
    if (core.access().allows(core, entity)) return false;
    if (entity instanceof TamableAnimal tame && tame.isTame()) return false;
    return entity instanceof Monster || core.wantsToHunt() && edible(entity);
  }

  static boolean edible(LivingEntity entity) {
    if (entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton) return false;
    return entity instanceof Player
        || entity instanceof net.minecraft.world.entity.npc.Villager
        || entity.getType().is(PREY);
  }

  private boolean claimed(ServerSubLevel ship, UUID entity) {
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof TentacleBlockEntity other
          && other != this
          && entity.equals(other.target)) return true;
    return false;
  }

  private void syncTarget() {
    setChanged();
    if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  private void release() {
    strike.clear();
    if (target != null) {
      target = null;
      clientTarget = -1;
      heldTicks = 0;
      cooldown = 40;
      syncTarget();
    }
  }

  @Override
  public void setRemoved() {
    release();
    super.setRemoved();
  }

  @Override
  public void sable$tick(ServerSubLevel ship) {
    serverTick();
  }

  public void serverTick() {
    if (!(level instanceof ServerLevel server) || lastTick == level.getGameTime()) return;
    lastTick = level.getGameTime();
    ServerSubLevel ship = ship();
    LivingCoreBlockEntity core = ship == null ? null : owner(ship);
    if (core == null || core.body().dead() || core.blueprint().busy()) {
      release();
      return;
    }
    if (cooldown > 0) cooldown--;
    Vec3 root =
        ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)).add(0, -0.7, 0);
    LivingEntity prey =
        target == null
            ? null
            : server.getEntity(target) instanceof LivingEntity living ? living : null;
    if (target != null
        && (prey == null || !valid(prey, core) || prey.position().distanceToSqr(root) > 100)) {
      release();
      return;
    }
    if (target == null && cooldown == 0 && level.getGameTime() % 10 == 0) {
      var selected = core.brain().attackTarget(core);
      if (selected != null
          && valid(selected, core)
          && !claimed(ship, selected.getUUID())
          && selected.position().distanceToSqr(root) < 64
          && visible(root, selected.getBoundingBox().getCenter(), selected, core, ship))
        prey = selected;
      if (prey != null) {
        target = prey.getUUID();
        clientTarget = prey.getId();
        heldTicks = 0;
        syncTarget();
      }
    }
    if (prey == null) return;
    if (!visible(root, prey.getBoundingBox().getCenter(), prey, core, ship)) {
      release();
      return;
    }
    if (prey instanceof Player
        || prey instanceof net.minecraft.world.entity.monster.Enemy
        || BioshipDefense.threatening(core, prey)) {
      if (!strike.active()) {
        strike.begin(prey, level.getGameTime());
        syncTarget();
      }
      strike.tick(core, root, prey, 8, 6);
      if (strike.finished(level.getGameTime())) release();
      return;
    }
    heldTicks++;
    Vec3 destination = core.hasMouth() ? core.mouthPosition(ship) : root.add(0, -1, 0);
    Vec3 center = prey.getBoundingBox().getCenter();
    Vec3 pull = destination.subtract(center);
    double distance = pull.length();
    Vec3 velocity =
        distance > 0.01 ? pull.scale(Math.min(0.35, distance * 0.15) / distance) : Vec3.ZERO;
    prey.setDeltaMovement(
        prey.getDeltaMovement().scale(0.3).add(velocity).add(0, prey.isNoGravity() ? 0 : 0.08, 0));
    prey.hurtMarked = true;
    prey.fallDistance = 0;
    if (heldTicks % 20 == 0) {
      var stomach = DigestiveSystem.stomach(core);
      boolean bite =
          core.hasMouth() && stomach != null && stomach.connected(core) && distance < 1.8;
      boolean edible = edible(prey);
      // Capturing food must not kill it before it reaches a connected mouth.
      if (edible && !bite) {
        if (heldTicks >= 160) release();
        return;
      }
      boolean hurt =
          BioshipFeedingEvents.bite(
              prey, server.damageSources().generic(), bite ? 6 : 2, bite && edible);
      if (hurt && !prey.isAlive() && bite && edible) core.eatPrey(prey);
      if (!prey.isAlive()) {
        release();
        return;
      }
    }
    if (heldTicks >= 160) release();
  }

  boolean visible(
      Vec3 from, Vec3 to, Entity target, LivingCoreBlockEntity core, ServerSubLevel ship) {
    var hit =
        level.clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
    if (hit.getType() != HitResult.Type.MISS
        && BioshipVision.exposedTube(level.getBlockState(hit.getBlockPos())))
      return BioshipVision.ray(core, from, to);
    if (hit.getType() == HitResult.Type.MISS) return true;
    // The mouth itself must not make a captured creature disappear from the limb's sight.
    return core.hasMouth()
        && to.distanceToSqr(core.mouthPosition(ship)) < 3.24
        && hit.getBlockPos().equals(core.mouthBlock());
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = new CompoundTag();
    tag.putInt("TargetEntity", clientTarget);
    tag.put("Strike", strike.save());
    return tag;
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    strike.load(tag.getCompound("Strike"));
    clientTarget = tag.contains("TargetEntity") ? tag.getInt("TargetEntity") : -1;
    target = null;
    heldTicks = 0;
    cooldown = 0;
  }
}
