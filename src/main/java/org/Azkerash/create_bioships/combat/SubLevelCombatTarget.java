package org.Azkerash.create_bioships.combat;

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
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.SubLevelHealth;

/** Vanilla attack calculations enter here; persistent health belongs exclusively to the grid. */
public class SubLevelCombatTarget extends LivingEntity {
  private static final net.minecraft.network.syncher.EntityDataAccessor<Float> POOL_HEALTH =
      net.minecraft.network.syncher.SynchedEntityData.defineId(
          SubLevelCombatTarget.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Float> POOL_MAXIMUM =
      net.minecraft.network.syncher.SynchedEntityData.defineId(
          SubLevelCombatTarget.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
  private UUID bodyId, anchorThreat, anchorBody;
  protected net.minecraft.core.BlockPos impactCell;
  protected net.minecraft.core.Direction impactFace = net.minecraft.core.Direction.NORTH;
  private Vec3 anchor;
  private int anchorTick;

  public SubLevelCombatTarget(EntityType<? extends SubLevelCombatTarget> type, Level level) {
    super(type, level);
    noPhysics = true;
    blocksBuilding = false;
    setNoGravity(true);
    setInvisible(true);
  }

  @Override
  protected void defineSynchedData(
      net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(POOL_HEALTH, 1024f);
    builder.define(POOL_MAXIMUM, 1024f);
  }

  private void syncPool(ServerSubLevel body) {
    var health = SubLevelHealth.status(body);
    entityData.set(POOL_HEALTH, (float) health.current());
    entityData.set(POOL_MAXIMUM, (float) health.maximum());
    setHealth((float) health.current());
  }

  @Override
  public float getHealth() {
    var body = body();
    return body == null
        ? entityData.get(POOL_HEALTH)
        : (float) SubLevelHealth.status(body).current();
  }

  @Override
  public double getAttributeValue(net.minecraft.core.Holder<Attribute> attribute) {
    if (attribute != Attributes.MAX_HEALTH) return super.getAttributeValue(attribute);
    var body = body();
    return body == null ? entityData.get(POOL_MAXIMUM) : SubLevelHealth.status(body).maximum();
  }

  @Override
  public void heal(float amount) {
    var body = body();
    if (body != null) {
      amount = net.neoforged.neoforge.event.EventHooks.onLivingHeal(this, amount);
      SubLevelHealth.heal(body, amount);
      syncPool(body);
    } else if (!level().isClientSide) super.heal(amount);
  }

  private SubLevelCombatTarget effectOwner() {
    var body = body();
    if (body == null) return this;
    var owner = org.Azkerash.create_bioships.core.SubLevelCreatureParts.root(body);
    return owner != body || this instanceof EyeCombatHurtbox ? CombatAdapter.target(owner) : this;
  }

  @Override
  public boolean addEffect(net.minecraft.world.effect.MobEffectInstance effect, Entity source) {
    var owner = effectOwner();
    if (owner == null) return false;
    if (owner != this)
      return owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect), source);
    boolean added = super.addEffect(effect, source);
    if (added && body() != null) CombatAdapter.saveEffects(body(), this);
    return added;
  }

  @Override
  public boolean hasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
    var owner = effectOwner();
    return owner != null && (owner == this ? super.hasEffect(effect) : owner.hasEffect(effect));
  }

  @Override
  public net.minecraft.world.effect.MobEffectInstance getEffect(
      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
    var owner = effectOwner();
    return owner == null ? null : owner == this ? super.getEffect(effect) : owner.getEffect(effect);
  }

  @Override
  public java.util.Collection<net.minecraft.world.effect.MobEffectInstance> getActiveEffects() {
    var owner = effectOwner();
    return owner == null
        ? List.of()
        : owner == this ? super.getActiveEffects() : owner.getActiveEffects();
  }

  @Override
  public boolean removeEffect(
      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
    var owner = effectOwner();
    if (owner == null) return false;
    if (owner != this) return owner.removeEffect(effect);
    boolean removed = super.removeEffect(effect);
    if (removed && body() != null) CombatAdapter.saveEffects(body(), this);
    return removed;
  }

  @Override
  public boolean removeAllEffects() {
    var owner = effectOwner();
    if (owner == null) return false;
    boolean changed = owner == this ? super.removeAllEffects() : owner.removeAllEffects();
    if (owner == this && body() != null) CombatAdapter.saveEffects(body(), this);
    return changed;
  }

  public static AttributeSupplier.Builder attributes() {
    return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1024);
  }

  public void bind(ServerSubLevel body) {
    bodyId = body.getUniqueId();
    setHealth(getMaxHealth());
    position(body);
    syncPool(body);
    if (!(this instanceof EyeCombatHurtbox)) CombatAdapter.restoreEffects(body, this);
  }

  public ServerSubLevel body() {
    if (!(level() instanceof ServerLevel server) || bodyId == null) return null;
    var container = SubLevelContainer.getContainer(server);
    var body = container == null ? null : container.getSubLevel(bodyId);
    return body instanceof ServerSubLevel serverBody && !body.isRemoved() ? serverBody : null;
  }

  protected void position(ServerSubLevel body) {
    Vec3 center =
        body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    var threat =
        level()
            .getEntitiesOfClass(
                Mob.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(48),
                HostileCombatIntegration::isHostile)
            .stream()
            .min(Comparator.comparingDouble(m -> m.position().distanceToSqr(center)))
            .orElse(null);
    Vec3 point = center;
    var members = org.Azkerash.create_bioships.core.SubLevelCreatureParts.members(body);
    var anchored =
        members.stream().filter(m -> m.getUniqueId().equals(anchorBody)).findFirst().orElse(body);
    ServerSubLevel contactBody = body;
    if (threat != null
        && anchor != null
        && threat.getUUID().equals(anchorThreat)
        && tickCount - anchorTick < 40) {
      point = anchored.logicalPose().transformPosition(anchor);
      contactBody = anchored;
    } else if (threat != null) {
      double best = Double.POSITIVE_INFINITY;
      for (var physical : members) {
        var cells = new java.util.HashSet<>(SubLevelHealth.blocks(physical));
        for (var cell : cells)
          for (var face : net.minecraft.core.Direction.values()) {
            if (cells.contains(cell.relative(face))) continue;
            var candidate =
                physical.logicalPose().transformPosition(Vec3.atCenterOf(cell).relative(face, .55));
            if (level()
                    .clip(
                        new net.minecraft.world.level.ClipContext(
                            threat.getEyePosition(),
                            candidate,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            threat))
                    .getType()
                != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            double distance = candidate.distanceToSqr(threat.position());
            if (distance < best) {
              best = distance;
              point = candidate;
              contactBody = physical;
              impactCell = cell;
              impactFace = face;
            }
          }
      }
    }
    if (threat != null
        && (anchor == null
            || !threat.getUUID().equals(anchorThreat)
            || tickCount - anchorTick >= 40)) {
      anchor = contactBody.logicalPose().transformPositionInverse(point);
      anchorBody = contactBody.getUniqueId();
      anchorThreat = threat.getUUID();
      anchorTick = tickCount;
    }
    setPos(point.x, point.y - getEyeHeight(), point.z);
  }

  @Override
  public void tick() {
    if (!level().isClientSide) {
      var body = body();
      if (body == null || SubLevelHealth.status(body).dead()) {
        discard();
        return;
      }
      position(body);
    }
    setDeltaMovement(Vec3.ZERO);
    super.tick();
    setDeltaMovement(Vec3.ZERO);
    var body = body();
    if (body != null) {
      syncPool(body);
      if (!(this instanceof EyeCombatHurtbox) && tickCount % 20 == 0)
        CombatAdapter.saveEffects(body, this);
    }
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    var body = body();
    if (body == null || SubLevelHealth.status(body).dead()) return false;
    return super.hurt(source, amount);
  }

  @Override
  protected void actuallyHurt(DamageSource source, float amount) {
    super.actuallyHurt(source, amount);
    double damage = damageContainers.peek().getNewDamage() * damageMultiplier();
    var body = body();
    if (body != null && damage > 0) {
      var physical =
          impactCell == null
              ? body
              : org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(
                  (ServerLevel) level(), impactCell);
      if (physical == null
          || org.Azkerash.create_bioships.core.SubLevelCreatureParts.root(physical)
              != org.Azkerash.create_bioships.core.SubLevelCreatureParts.root(body))
        physical = body;
      double remaining =
          org.Azkerash.create_bioships.core.SubLevelArmor.absorb(
              physical, impactCell, source, damage);
      SubLevelHealth.damage(body, remaining, source);
      CombatAdapter.hurtFlash(body);
      if (impactCell != null
          && (source.getDirectEntity() instanceof LivingEntity
              || source.getDirectEntity()
                  instanceof net.minecraft.world.entity.projectile.Projectile))
        SubLevelWounds.hit(physical, impactCell, impactFace);
    }
    // This entity is an adapter, never a second health pool or loot-bearing creature.
    if (body != null) syncPool(body);
  }

  protected double damageMultiplier() {
    return 1;
  }

  public void impact(net.minecraft.core.BlockPos cell, net.minecraft.core.Direction face) {
    impactCell = cell;
    impactFace = face;
  }

  public void focusForAttack(Vec3 eye) {
    setPos(eye.x, eye.y - getEyeHeight(), eye.z);
  }

  @Override
  public boolean shouldBeSaved() {
    return false;
  }

  @Override
  protected int decreaseAirSupply(int currentAir) {
    return getMaxAirSupply();
  }

  @Override
  public boolean isPickable() {
    return false;
  }

  @Override
  public boolean canBeHitByProjectile() {
    return false;
  }

  @Override
  public void knockback(double strength, double x, double z) {
    // This adapter follows tissue. Vanilla knockback must not detach its target point from it.
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public void push(Entity entity) {}

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
  public void setItemSlot(EquipmentSlot slot, ItemStack item) {}

  @Override
  public HumanoidArm getMainArm() {
    return HumanoidArm.RIGHT;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (bodyId != null) tag.putUUID("sublevel", bodyId);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    bodyId = tag.hasUUID("sublevel") ? tag.getUUID("sublevel") : null;
  }
}
