package org.azkerash.createbioships.development.testing.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;
import org.azkerash.createbioships.development.testing.DevelopmentToolkitItem;

@EventBusSubscriber(modid = "create_bioships")
public final class CreatureStatsSync {
  private static final int SNAPSHOT_INTERVAL_TICKS = 10;
  private static final int VIEW_RADIUS_BLOCKS = 64;

  private static final Map<ServerPlayer, Set<UUID>> VIEWERS = new WeakHashMap<>();

  private CreatureStatsSync() {}

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    var r = e.registrar("1");
    r.playToServer(
        CreatureStatsRequest.TYPE,
        CreatureStatsRequest.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    if (p.enabled() && DevelopmentToolkitItem.allowed(player)) {
                      VIEWERS.put(player, new HashSet<>());
                    } else {
                      VIEWERS.remove(player);
                    }
                  }
                }));
    r.playToClient(
        CreatureStatsPayload.TYPE,
        CreatureStatsPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () ->
                    org.azkerash.createbioships.client.development.CreatureStatsOverlay.receive(
                        p)));
  }

  private static String text(String fmt, Object... args) {
    return String.format(Locale.ROOT, fmt, args);
  }

  private static CreatureStatsPayload.Metric row(String text) {
    return new CreatureStatsPayload.Metric(text, -1, 0xffc4e4e8);
  }

  private static CreatureStatsPayload.Metric bar(String text, double value, double max, int color) {
    return new CreatureStatsPayload.Metric(
        text, max <= 0 ? 0 : Math.clamp(value / max, 0, 1), color);
  }

  public static CreatureStatsPayload snapshot(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var hp = SubLevelHealth.status(body);
    var food = SubLevelData.hungerState(body);
    var metabolism = SubLevelHunger.metabolism(body);
    var flags = SubLevelLocomotion.get(body);
    var motion = BrainMotorControls.current(body);
    var rows = new ArrayList<CreatureStatsPayload.Metric>();
    rows.add(
        row(
            hp.dead()
                ? "DEAD - metabolism stopped"
                : food.paused() ? "METABOLISM PAUSED" : "LIVING - metabolism active"));
    rows.add(
        bar(
            text("Health %.1f / %.1f", hp.current(), hp.maximum()),
            hp.current(),
            hp.maximum(),
            0xffd56c76));
    var armor = SubLevelArmor.status(body);
    rows.add(
        bar(
            text("Armor %.1f / %.1f (armored hits only)", armor.current(), armor.maximum()),
            armor.current(),
            armor.maximum(),
            0xffaa79bb));
    rows.add(
        bar(
            text("Hunger %d / %d", food.value(), SubLevelData.maximumHunger(body)),
            food.value(),
            SubLevelData.maximumHunger(body),
            0xffdda750));
    rows.add(
        row(
            text(
                "Mass %.2f Sable units (%d bodies)",
                metabolism.mass(), SubLevelCreatureParts.members(body).size())));
    rows.add(
        row(
            text(
                "Drain %.2f hunger/min = max(1, mass / %.0f)",
                metabolism.hungerPerMinute(), SubLevelData.HUNGER_REFERENCE_MASS)));
    rows.add(
        bar(
            text(
                "Next hunger drain: %.1fs",
                (SubLevelHunger.DRAIN_INTERVAL - food.drainTicks()) / metabolism.multiplier() / 20),
            food.drainTicks(),
            SubLevelHunger.DRAIN_INTERVAL,
            0xff86c5cc));
    rows.add(
        row(
            text(
                "Healing needs %d hunger (90%%): %s",
                SubLevelHunger.minimumHungerToHeal(body),
                food.paused()
                    ? "paused"
                    : hp.current() >= hp.maximum()
                        ? "full health"
                        : SubLevelHunger.canRegenerate(body) ? "eligible" : "blocked")));
    rows.add(
        row(
            hp.dead()
                ? "Starvation inactive: dead"
                : food.paused()
                    ? "Starvation paused"
                    : food.value() > 0
                        ? "Starvation inactive: hunger above zero"
                        : text(
                            "Starvation: 1 HP / 4s; next in %.1fs",
                            (SubLevelHunger.STARVATION_INTERVAL - food.starvationTicks()) / 20d)));
    rows.add(
        bar(
            text("Trust %.1f / 100", SubLevelData.trust(body)),
            SubLevelData.trust(body),
            100,
            0xffb68ed6));
    rows.add(row("Stomach: 1 paste -> 4 hunger (+Efficiency) / 1.6s"));
    rows.add(
        row(
            "Appendages: legs="
                + flags.hasLegs()
                + " fins="
                + flags.hasFins()
                + " climbers="
                + flags.hasWallClimbers()));
    rows.add(
        row(
            text(
                "Motion: %s %.2f m/s; yaw %.2f",
                motion.mode(), motion.velocity().length(), motion.yawRate())));
    rows.add(
        row(
            "Visible targets: "
                + org.azkerash.createbioships.core.vision.SubLevelVision.detected(body).size()));
    var center =
        body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    var bounds = SubLevelLocomotion.bounds(body);
    return new CreatureStatsPayload(
        body.getUniqueId(), new Vec3(center.x, bounds.maxY + 1, center.z), rows);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    if (e.getServer().getTickCount() % SNAPSHOT_INTERVAL_TICKS != 0) {
      return;
    }
    VIEWERS
        .keySet()
        .removeIf(
            p ->
                !e.getServer().getPlayerList().getPlayers().contains(p)
                    || !DevelopmentToolkitItem.allowed(p));
    for (var player : VIEWERS.keySet()) {
      var c = SubLevelContainer.getContainer(player.serverLevel());
      if (c == null) {
        continue;
      }
      var roots = new ArrayList<ServerSubLevel>();
      for (var entry : c.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel b
            && !b.isRemoved()
            && !SubLevelCreatureParts.isPart(b)
            && CoreAssemblyRules.isCreature(b)
            && org.azkerash.createbioships.core.access.SubLevelPermissions.canInspect(
                b, player.getUUID())
            && org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.position(b)
                    .distanceToSqr(player.position())
                < VIEW_RADIUS_BLOCKS * VIEW_RADIUS_BLOCKS) {
          roots.add(b);
        }
      }
      roots.sort(
          Comparator.comparingDouble(
              b ->
                  org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.position(b)
                      .distanceToSqr(player.position())));
      var present = new HashSet<UUID>();
      for (var b : roots.subList(0, Math.min(4, roots.size()))) {
        present.add(b.getUniqueId());
        PacketDistributor.sendToPlayer(player, snapshot(b));
      }
      for (var id : VIEWERS.get(player)) {
        if (!present.contains(id)) {
          PacketDistributor.sendToPlayer(
              player, new CreatureStatsPayload(id, Vec3.ZERO, List.of()));
        }
      }
      VIEWERS.put(player, present);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
    VIEWERS.clear();
  }
}
