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
  private UUID bodyId, anchorThreat;
  protected net.minecraft.core.BlockPos impactCell;
  protected net.minecraft.core.Direction impactFace = net.minecraft.core.Direction.NORTH;
  private Vec3 anchor;
  private int anchorTick;

  public SubLevelCombatTarget(EntityType<? extends SubLevelCombatTarget> type, Level level) {
    super(type, level);
    noPhysics = true;
    setNoGravity(true);
    setInvisible(true);
  }

  public static AttributeSupplier.Builder attributes() {
    return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1024);
  }

  public void bind(ServerSubLevel body) {
    bodyId = body.getUniqueId();
    setHealth(getMaxHealth());
    position(body);
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
    if (threat != null
        && anchor != null
        && threat.getUUID().equals(anchorThreat)
        && tickCount - anchorTick < 40) {
      point = body.logicalPose().transformPosition(anchor);
    } else if (threat != null) {
      var cells = new java.util.HashSet<>(SubLevelHealth.blocks(body));
      double best = Double.POSITIVE_INFINITY;
      for (var cell : cells)
        for (var face : net.minecraft.core.Direction.values()) {
          if (cells.contains(cell.relative(face))) continue;
          var candidate =
              body.logicalPose().transformPosition(Vec3.atCenterOf(cell).relative(face, .55));
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
          double distance = candidate.distanceToSqr(threat.getEyePosition());
          if (distance < best) {
            best = distance;
            point = candidate;
            impactCell = cell;
            impactFace = face;
          }
        }
    }
    if (threat != null
        && (anchor == null
            || !threat.getUUID().equals(anchorThreat)
            || tickCount - anchorTick >= 40)) {
      anchor = body.logicalPose().transformPositionInverse(point);
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
    super.tick();
    setDeltaMovement(Vec3.ZERO);
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
      SubLevelHealth.damage(body, damage, source);
      CombatAdapter.hurtFlash(body);
      if (impactCell != null
          && (source.getDirectEntity() instanceof LivingEntity
              || source.getDirectEntity()
                  instanceof net.minecraft.world.entity.projectile.Projectile))
        SubLevelWounds.hit(body, impactCell, impactFace);
    }
    // This entity is an adapter, never a second health pool or loot-bearing creature.
    setHealth(getMaxHealth());
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
  public boolean isPushable() {
    return true;
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
