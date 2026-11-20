package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.mixin.EmbeddedArrowAccess;

/**
 * Keep original projectile entities/items attached to struck tissue, rather than replacing them.
 */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class ProjectileAttachment {
  public static final int LIFETIME = 1200;

  public record Attachment(UUID body, BlockPos cell, Vec3 point, Vec3 direction, long expires) {}

  private static final Map<Level, Map<Integer, Attachment>> ATTACHED =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final String DATA = "create_bioships:embedded_projectile";
  private static final ThreadLocal<Entity> IMPACT = new ThreadLocal<>();

  private ProjectileAttachment() {}

  public static boolean retain(Entity entity) {
    return IMPACT.get() == entity;
  }

  public static void begin(AbstractArrow arrow) {
    IMPACT.set(arrow);
  }

  public static void end() {
    IMPACT.remove();
  }

  public static void accept(Level level, int id, Attachment attachment) {
    ATTACHED
        .computeIfAbsent(level, l -> new java.util.concurrent.ConcurrentHashMap<>())
        .put(id, attachment);
  }

  public static boolean attached(Entity entity) {
    return ATTACHED.getOrDefault(entity.level(), Map.of()).containsKey(entity.getId());
  }

  public static void attach(
      AbstractArrow arrow, ServerSubLevel body, BlockHitResult hit, Vec3 incoming) {
    var point = hit.getLocation();
    if (!new AABB(hit.getBlockPos()).inflate(.01).contains(point)) {
      point = body.logicalPose().transformPositionInverse(point);
    }
    var data =
        new Attachment(
            body.getUniqueId(),
            hit.getBlockPos(),
            point,
            body.logicalPose().transformNormalInverse(incoming).normalize(),
            body.getLevel().getGameTime() + LIFETIME);
    arrow.setNoPhysics(true);
    arrow.setDeltaMovement(Vec3.ZERO);
    ((EmbeddedArrowAccess) arrow).core$inGround(true);
    arrow.getPersistentData().put(DATA, save(data));
    accept(arrow.level(), arrow.getId(), data);
    update(arrow, data);
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
        body.getLevel(), new ProjectileAttachmentPayload(arrow.getId(), data));
  }

  private static net.minecraft.nbt.CompoundTag save(Attachment data) {
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putUUID("body", data.body());
    tag.putLong("cell", data.cell().asLong());
    tag.putLong("expires", data.expires());
    tag.putDouble("x", data.point().x);
    tag.putDouble("y", data.point().y);
    tag.putDouble("z", data.point().z);
    tag.putDouble("dx", data.direction().x);
    tag.putDouble("dy", data.direction().y);
    tag.putDouble("dz", data.direction().z);
    return tag;
  }

  @SubscribeEvent
  public static void loaded(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
    if (!(event.getEntity() instanceof AbstractArrow arrow) || event.getLevel().isClientSide) {
      return;
    }
    var tag = arrow.getPersistentData().getCompound(DATA);
    if (!tag.hasUUID("body")) {
      return;
    }
    accept(
        event.getLevel(),
        arrow.getId(),
        new Attachment(
            tag.getUUID("body"),
            BlockPos.of(tag.getLong("cell")),
            new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
            new Vec3(tag.getDouble("dx"), tag.getDouble("dy"), tag.getDouble("dz")),
            tag.getLong("expires")));
  }

  @SubscribeEvent
  public static void tracking(
      net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
    if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
      return;
    }
    var target = event.getTarget();
    var data = ATTACHED.getOrDefault(target.level(), Map.of()).get(target.getId());
    if (data != null) {
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
          player, new ProjectileAttachmentPayload(target.getId(), data));
    }
  }

  @SubscribeEvent
  public static void removed(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
    var map = ATTACHED.get(event.getLevel());
    if (map != null) {
      map.remove(event.getEntity().getId());
    }
  }

  @SubscribeEvent
  public static void tick(EntityTickEvent.Pre event) {
    if (!(event.getEntity() instanceof AbstractArrow arrow)) {
      return;
    }
    var map = ATTACHED.get(arrow.level());
    if (map == null) {
      return;
    }
    var data = map.get(arrow.getId());
    if (data == null) {
      return;
    }
    if (arrow.isRemoved() || !update(arrow, data)) {
      map.remove(arrow.getId());
      if (!arrow.level().isClientSide && !arrow.isRemoved()) {
        if (arrow instanceof ThrownTrident && arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
          arrow.spawnAtLocation(arrow.getPickupItemStackOrigin().copy());
        }
        arrow.discard();
      }
      return;
    }
    event.setCanceled(true);
  }

  private static boolean update(AbstractArrow arrow, Attachment data) {
    var level = arrow.level();
    if (level.getGameTime() >= data.expires()) {
      return false;
    }
    var container = SubLevelContainer.getContainer(level);
    var body = container == null ? null : container.getSubLevel(data.body());
    if (body == null || body.isRemoved()) {
      return level.isClientSide;
    }
    if (level.getBlockState(data.cell()).isAir()) {
      return false;
    }
    var pose = body instanceof ClientSubLevel client ? client.renderPose() : body.logicalPose();
    var point = pose.transformPosition(data.point());
    var direction = pose.transformNormal(data.direction());
    arrow.setPos(point);
    arrow.setDeltaMovement(Vec3.ZERO);
    arrow.setNoPhysics(true);
    ((EmbeddedArrowAccess) arrow).core$inGround(true);
    arrow.setYRot((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
    arrow.setXRot(
        (float) Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
    arrow.yRotO = arrow.getYRot();
    arrow.xRotO = arrow.getXRot();
    return true;
  }
}
