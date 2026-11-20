package org.azkerash.createbioships.compat.bigcannons;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.combat.SubLevelCombatTarget;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.joml.Vector3f;
import rbasamoyai.createbigcannons.munitions.config.components.*;
import rbasamoyai.createbigcannons.munitions.fragment_burst.*;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst.SubProjectile;

/** CBC ballistics/collision and RPL burst synchronization, with biological acid impact effects. */
public final class AcidBurstEntity extends CBCProjectileBurst {
  public static final int DROPLETS = 6;
  private static final int LIFETIME_TICKS = 30, POISON_TICKS = 80;
  private static final float IMPACT_DAMAGE = 4;
  private static final double GRAVITY_PER_TICK = -.025, DRAG = .01, DROPLET_SIZE = .18;
  private static final ProjectileBurstProperties PROPERTIES =
      new ProjectileBurstProperties(
          new BallisticPropertiesComponent(GRAVITY_PER_TICK, DRAG, false, 0, 0, 0, 0),
          new EntityDamagePropertiesComponent(IMPACT_DAMAGE, true, false, false, 0),
          LIFETIME_TICKS);
  private static final DustParticleOptions ACID =
      new DustParticleOptions(new Vector3f(.45f, .85f, .08f), 1.2f);
  private UUID sourceBody;
  private final Set<UUID> struck = new HashSet<>();

  public AcidBurstEntity(EntityType<? extends AcidBurstEntity> type, Level level) {
    super(type, level);
  }

  public void setSourceBody(UUID id) {
    sourceBody = id;
  }

  @Override
  protected ProjectileBurstProperties getProperties() {
    return PROPERTIES;
  }

  @Override
  public double getSubProjectileWidth() {
    return DROPLET_SIZE;
  }

  @Override
  public double getSubProjectileHeight() {
    return DROPLET_SIZE;
  }

  @Override
  public boolean canHitEntity(Entity entity) {
    if (!super.canHitEntity(entity) || !(entity instanceof LivingEntity)) {
      return false;
    }
    if (entity instanceof SubLevelCombatTarget target && target.body() != null) {
      return !SubLevelCreatureParts.root(target.body()).getUniqueId().equals(sourceBody);
    }
    return getOwner() == null || !getOwner().getUUID().equals(entity.getUUID());
  }

  @Override
  protected void onSubProjectileHitEntity(EntityHitResult hit, SubProjectile projectile) {
    if (level().isClientSide || !(hit.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    UUID id =
        victim instanceof SubLevelCombatTarget target && target.body() != null
            ? SubLevelCreatureParts.root(target.body()).getUniqueId()
            : victim.getUUID();
    // Several droplets (or several proxy hitboxes) must not multiply one shot's damage.
    if (struck.size() >= DROPLETS || !struck.add(id)) {
      return;
    }
    if (victim.hurt(level().damageSources().indirectMagic(this, getOwner()), IMPACT_DAMAGE)) {
      victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), this);
    }
  }

  @Override
  protected void onSubProjectileHit(HitResult hit, SubProjectile projectile) {
    super.onSubProjectileHit(hit, projectile);
    if (level() instanceof ServerLevel server) {
      var point = hit.getLocation();
      server.sendParticles(ACID, point.x, point.y, point.z, 8, .15, .15, .15, .02);
    }
  }

  @Override
  public void tick() {
    if (getOwner() == null && sourceBody != null && level() instanceof ServerLevel server) {
      var container = SubLevelContainer.getContainer(server);
      var source = container == null ? null : container.getSubLevel(sourceBody);
      if (source instanceof ServerSubLevel body && !body.isRemoved()) {
        setOwner(org.azkerash.createbioships.combat.CombatAdapter.target(body));
      }
    }
    super.tick();
    if (level().isClientSide) {
      for (var droplet : getSubProjectiles()) {
        var offset = droplet.displacement();
        level()
            .addParticle(ACID, getX() + offset[0], getY() + offset[1], getZ() + offset[2], 0, 0, 0);
      }
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (sourceBody != null) {
      tag.putUUID("AcidSource", sourceBody);
    }
    var hits = new ListTag();
    struck.forEach(id -> hits.add(StringTag.valueOf(id.toString())));
    tag.put("AcidVictims", hits);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    sourceBody = tag.hasUUID("AcidSource") ? tag.getUUID("AcidSource") : null;
    struck.clear();
    for (var entry : tag.getList("AcidVictims", Tag.TAG_STRING)) {
      if (struck.size() >= DROPLETS) {
        break;
      }
      try {
        struck.add(UUID.fromString(entry.getAsString()));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }
}
