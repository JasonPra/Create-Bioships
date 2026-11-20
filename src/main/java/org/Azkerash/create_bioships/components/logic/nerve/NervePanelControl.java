package org.Azkerash.create_bioships.components.logic.nerve;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

/** Server-validated panel interactions; a remote/stale screen never grants editing rights. */
public final class NervePanelControl {
  public static final int CANDIDATE_LIMIT = 128;
  private static final int PANEL_REACH_BLOCKS = 8;
  private static final int MOB_SEARCH_RADIUS_BLOCKS = 32;
  private static final int CONSTRUCT_SEARCH_RADIUS_BLOCKS = 64;
  private static final int UUID_LABEL_CHARS = 8;

  private NervePanelControl() {}

  public static boolean usable(ServerPlayer player, ServerSubLevel body, BlockPos panel) {
    return !body.isRemoved()
        && player.level() == body.getLevel()
        && SubLevelData.isLiving(body)
        && CoreAssemblyRules.containing(body.getLevel(), panel) == body
        && body.getLevel().getBlockState(panel).is(ComponentRegistry.NERVE_PANEL.get())
        && player
                .getEyePosition()
                .distanceToSqr(body.logicalPose().transformPosition(Vec3.atCenterOf(panel)))
            <= PANEL_REACH_BLOCKS * PANEL_REACH_BLOCKS;
  }

  public static void open(ServerPlayer player, ServerSubLevel body, BlockPos panel) {
    if (!usable(player, body, panel)) {
      return;
    }
    var access = SubLevelPermissions.get(body);
    boolean editable = player.getUUID().equals(access.owner());
    var candidates = new ArrayList<SubLevelPermissions.Entry>();
    if (editable) {
      for (var other : player.getServer().getPlayerList().getPlayers()) {
        if (!access.permits(other.getUUID()) && candidates.size() < CANDIDATE_LIMIT) {
          candidates.add(
              new SubLevelPermissions.Entry(
                  other.getUUID(), other.getGameProfile().getName(), true));
        }
      }
      var center = body.logicalPose().transformPosition(Vec3.atCenterOf(panel));
      var mobs =
          body.getLevel()
              .getEntitiesOfClass(
                  Mob.class,
                  new AABB(center, center).inflate(MOB_SEARCH_RADIUS_BLOCKS),
                  e -> e.isAlive() && e.hasCustomName());
      mobs.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
      for (var mob : mobs) {
        if (!access.permits(mob.getUUID()) && candidates.size() < CANDIDATE_LIMIT) {
          candidates.add(
              new SubLevelPermissions.Entry(mob.getUUID(), mob.getName().getString(), false));
        }
      }
      var container =
          dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(body.getLevel());
      if (container != null) {
        for (var other : container.getAllSubLevels()) {
          if (other == body
              || other.isRemoved()
              || SubLevelCreatureParts.isPart(other)
              || candidates.size() >= CANDIDATE_LIMIT) {
            continue;
          }
          var named = org.Azkerash.create_bioships.core.naming.SubLevelNames.name(other);
          if (!named.isBlank()
              && !access.permits(other.getUniqueId())
              && nearConstruct(center, other)) {
            candidates.add(new SubLevelPermissions.Entry(other.getUniqueId(), named, false, true));
          }
        }
      }
      // Allowed players may still be selected as the next owner.
      for (var other : player.getServer().getPlayerList().getPlayers()) {
        if (!other.getUUID().equals(access.owner())
            && candidates.stream().noneMatch(e -> e.id().equals(other.getUUID()))
            && candidates.size() < CANDIDATE_LIMIT) {
          candidates.add(
              new SubLevelPermissions.Entry(
                  other.getUUID(), other.getGameProfile().getName(), true));
        }
      }
    }
    String ownerName = "Unassigned";
    if (access.owner() != null) {
      var online = player.getServer().getPlayerList().getPlayer(access.owner());
      var cache = player.getServer().getProfileCache();
      ownerName =
          editable
              ? player.getGameProfile().getName()
              : online != null
                  ? online.getGameProfile().getName()
                  : cache != null
                      ? cache
                          .get(access.owner())
                          .map(com.mojang.authlib.GameProfile::getName)
                          .orElse(access.owner().toString().substring(0, UUID_LABEL_CHARS))
                      : access.owner().toString().substring(0, UUID_LABEL_CHARS);
    }
    PacketDistributor.sendToPlayer(
        player,
        new NervePanelPayload(
            body.getUniqueId(),
            panel,
            ownerName,
            editable,
            access.allowed(),
            List.copyOf(candidates)));
  }

  public static boolean handle(ServerPlayer player, NervePanelAction action) {
    var body = CoreAssemblyRules.containing(player.serverLevel(), action.panel());
    if (body == null
        || !body.getUniqueId().equals(action.grid())
        || !usable(player, body, action.panel())) {
      return false;
    }
    if (!player.getUUID().equals(SubLevelPermissions.get(body).owner())) {
      return false;
    }
    boolean changed = false;
    switch (action.action()) {
      case NervePanelAction.INVITE -> {
        var target = player.getServer().getPlayerList().getPlayer(action.target());
        net.minecraft.world.entity.Entity entity =
            target == null ? body.getLevel().getEntity(action.target()) : target;
        var center = body.logicalPose().transformPosition(Vec3.atCenterOf(action.panel()));
        if (entity instanceof ServerPlayer
            || entity instanceof Mob
                && entity.isAlive()
                && entity.hasCustomName()
                && entity.position().distanceToSqr(center)
                    <= MOB_SEARCH_RADIUS_BLOCKS * MOB_SEARCH_RADIUS_BLOCKS) {
          changed = SubLevelPermissions.add(body, player.getUUID(), entity);
        } else {
          var container =
              dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(body.getLevel());
          var other = container == null ? null : container.getSubLevel(action.target());
          if (other instanceof ServerSubLevel craft && nearConstruct(center, craft)) {
            changed = SubLevelPermissions.addConstruct(body, player.getUUID(), craft);
          }
        }
      }
      case NervePanelAction.REVOKE -> {
        changed = SubLevelPermissions.remove(body, player.getUUID(), action.target());
      }
      case NervePanelAction.TRANSFER -> {
        if (player.getServer().getPlayerList().getPlayer(action.target()) != null) {
          changed = SubLevelPermissions.transfer(body, player.getUUID(), action.target());
        }
      }
      default -> {
        // Refresh and unknown actions only return the current authorized view.
      }
    }
    open(player, body, action.panel());
    return changed;
  }

  private static boolean nearConstruct(Vec3 center, ServerSubLevel other) {
    var bounds = other.boundingBox().toMojang();
    var closest =
        new Vec3(
            Math.clamp(center.x, bounds.minX, bounds.maxX),
            Math.clamp(center.y, bounds.minY, bounds.maxY),
            Math.clamp(center.z, bounds.minZ, bounds.maxZ));
    return closest.distanceToSqr(center)
        <= CONSTRUCT_SEARCH_RADIUS_BLOCKS * CONSTRUCT_SEARCH_RADIUS_BLOCKS;
  }
}
