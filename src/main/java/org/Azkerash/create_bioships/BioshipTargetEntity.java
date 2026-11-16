package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** A living attack surface for vanilla mob AI; health belongs to the block-based organism. */
public final class BioshipTargetEntity extends LivingEntity {
  private static final Map<
          ServerLevel,
          Map<
              UUID,
              EnumMap<
                  net.minecraft.core.Direction, java.lang.ref.WeakReference<BioshipTargetEntity>>>>
      LIVE = new WeakHashMap<>();

  private record SurfaceCells(long at, List<net.minecraft.core.BlockPos> cells) {}

  private static final Map<LivingCoreBlockEntity, SurfaceCells> SURFACE_CELLS = new WeakHashMap<>();

  static List<BioshipTargetEntity> surfaces(LivingCoreBlockEntity core) {
    ensure(core);
    var registry = LIVE.get((ServerLevel) core.getLevel());
    if (registry == null || core.ship() == null) return List.of();
    var surfaces = registry.get(core.ship().getUniqueId());
    return surfaces == null
        ? List.of()
        : surfaces.values().stream()
            .map(java.lang.ref.WeakReference::get)
            .filter(Objects::nonNull)
            .filter(e -> !e.isRemoved())
            .toList();
  }

  static BioshipTargetEntity attackSource(LivingCoreBlockEntity core, LivingEntity victim) {
    return surfaces(core).stream()
        .min(
            Comparator.comparingDouble(
                surface ->
                    surface
                        .getBoundingBox()
                        .getCenter()
                        .distanceToSqr(victim.getBoundingBox().getCenter())))
        .orElseGet(() -> ensure(core));
  }

  private UUID shipId;
  private net.minecraft.core.Direction face = net.minecraft.core.Direction.NORTH;
  private LivingCoreBlockEntity cachedCore;

  public BioshipTargetEntity(EntityType<? extends BioshipTargetEntity> type, Level level) {
    super(type, level);
    noPhysics = true;
    blocksBuilding = false;
    setNoGravity(true);
    setInvisible(true);
  }

  public static AttributeSupplier.Builder attributes() {
    return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20);
  }

  public LivingCoreBlockEntity core() {
    if (!(level() instanceof ServerLevel level) || shipId == null) return null;
    if (cachedCore != null
        && !cachedCore.isRemoved()
        && cachedCore.ship() != null
        && !cachedCore.ship().isRemoved()
        && shipId.equals(cachedCore.ship().getUniqueId())) return cachedCore;
    var container = SubLevelContainer.getContainer(level);
    var body = container == null ? null : container.getSubLevel(shipId);
    if (body instanceof ServerSubLevel ship && !ship.isRemoved())
      for (var actor : ship.getPlot().getBlockEntityActors())
        if (actor instanceof LivingCoreBlockEntity core) return cachedCore = core;
    return null;
  }

  boolean belongsTo(LivingCoreBlockEntity core) {
    return core.ship() != null && core.ship().getUniqueId().equals(shipId);
  }

  public static BioshipTargetEntity ensure(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    var ship = core.ship();
    if (ship == null || ship.isRemoved()) return null;
    var tag = ship.getUserDataTag() == null ? new CompoundTag() : ship.getUserDataTag().copy();
    var registry = LIVE.computeIfAbsent(level, ignored -> new HashMap<>());
    var live =
        registry.computeIfAbsent(
            ship.getUniqueId(), ignored -> new EnumMap<>(net.minecraft.core.Direction.class));
    var surfaces = tag.getCompound("AttackSurfaces");
    BioshipTargetEntity primary = null;
    boolean changed = false;
    for (var side : net.minecraft.core.Direction.values()) {
      var key = side.getName();
      var ref = live.get(side);
      net.minecraft.world.entity.Entity existing = ref == null ? null : ref.get();
      if (existing == null || existing.isRemoved())
        existing = surfaces.hasUUID(key) ? level.getEntity(surfaces.getUUID(key)) : null;
      if (core.body().dead()) {
        if (existing instanceof BioshipTargetEntity target) target.discard();
        continue;
      }
      if (existing instanceof BioshipTargetEntity target
          && !target.isRemoved()
          && target.belongsTo(core)) {
        target.cachedCore = core;
        live.put(side, new java.lang.ref.WeakReference<>(target));
        if (primary == null) primary = target;
      } else {
        var target = Create_bioships.BIOSHIP_TARGET.get().create(level);
        if (target == null) continue;
        target.shipId = ship.getUniqueId();
        target.cachedCore = core;
        target.face = side;
        target.positionOnBody(core);
        if (!level.addFreshEntity(target)) continue;
        live.put(side, new java.lang.ref.WeakReference<>(target));
        surfaces.putUUID(key, target.getUUID());
        changed = true;
        if (primary == null) primary = target;
      }
    }
    if (core.body().dead()) {
      registry.remove(ship.getUniqueId());
      if (tag.contains("AttackSurfaces")) {
        tag.remove("AttackSurfaces");
        ship.setUserDataTag(tag);
      }
      return null;
    }
    if (changed) {
      tag.put("AttackSurfaces", surfaces);
      ship.setUserDataTag(tag);
    }
    return primary;
  }

  @Override
  public void tick() {
    if (level().isClientSide) {
      super.tick();
      return;
    }
    var core = core();
    if (core == null || core.body().dead()) {
      discard();
      return;
    }
    // A hostile on the perimeter attacks the body there, rather than trying to walk through its
    // hull.
    if (tickCount % 10 == 0) positionOnBody(core);
    super.tick();
    setDeltaMovement(Vec3.ZERO);
    setNoGravity(true);
    setInvisible(true);
    setAirSupply(300);
  }

  private void positionOnBody(LivingCoreBlockEntity core) {
    var direction = Vec3.atLowerCornerOf(face.getNormal());
    net.minecraft.core.BlockPos outer = core.getBlockPos();
    double best = -Double.MAX_VALUE;
    var cells = SURFACE_CELLS.get(core);
    if (cells == null || core.getLevel().getGameTime() - cells.at >= 10) {
      cells = new SurfaceCells(core.getLevel().getGameTime(), BioshipDecay.blocks(core.ship()));
      SURFACE_CELLS.put(core, cells);
    }
    for (var block : cells.cells) {
      double projection =
          Vec3.atCenterOf(block).subtract(Vec3.atCenterOf(core.getBlockPos())).dot(direction);
      double score = projection * 1000 - block.distSqr(core.getBlockPos());
      if (score > best) {
        best = score;
        outer = block;
      }
    }
    Vec3 anchor =
        core.ship().logicalPose().transformPosition(Vec3.atCenterOf(outer).relative(face, .8));
    setPos(anchor.x, anchor.y - .5, anchor.z);
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    var core = core();
    if (core == null || core.body().dead() || !Float.isFinite(amount) || amount <= 0) return false;
    if (!(source.getEntity() instanceof LivingEntity)
        && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
    invulnerableTime = 20;
    boolean accepted = core.body().combatDamage(core, source, amount);
    if (core.body().dead()) discard();
    return accepted;
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public void push(Entity entity) {}

  @Override
  public boolean isPickable() {
    return false;
  }

  @Override
  public boolean canBeHitByProjectile() {
    return isAlive();
  }

  @Override
  public void remove(RemovalReason reason) {
    super.remove(reason);
    if (!(level() instanceof ServerLevel level) || shipId == null) return;
    var registry = LIVE.get(level);
    var surfaces = registry == null ? null : registry.get(shipId);
    if (surfaces == null) return;
    var reference = surfaces.get(face);
    if (reference != null && reference.get() == this) surfaces.remove(face);
    if (surfaces.isEmpty()) registry.remove(shipId);
  }

  @Override
  public double getVisibilityPercent(Entity observer) {
    return 1;
  }

  @Override
  public Iterable<ItemStack> getArmorSlots() {
    return List.of();
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

  @Override
  public HumanoidArm getMainArm() {
    return HumanoidArm.RIGHT;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (shipId != null) tag.putUUID("Ship", shipId);
    tag.putString("Face", face.getName());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
    var loaded = net.minecraft.core.Direction.byName(tag.getString("Face"));
    face = loaded == null ? net.minecraft.core.Direction.NORTH : loaded;
  }
}
