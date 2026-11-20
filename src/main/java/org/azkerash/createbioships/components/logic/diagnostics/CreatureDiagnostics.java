package org.azkerash.createbioships.components.logic.diagnostics;

import static org.azkerash.createbioships.components.logic.diagnostics.DebugPanelPayload.Row;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.behavior.*;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;
import org.azkerash.createbioships.core.pathfinding.*;
import org.azkerash.createbioships.core.vision.SubLevelVision;

/** Observations only: collecting diagnostics never replans, raycasts or changes joint settings. */
public final class CreatureDiagnostics {
  private record Motion(Vec3 position, long lastMoved) {}

  private static final Map<ServerSubLevel, Motion> MOTION = new WeakHashMap<>();
  private static final int STALL_TICKS = 40, MAX_COMPONENT_ROWS = 24, MAX_LEG_ROWS = 24;
  private static final double MOVEMENT_EPSILON_SQUARED = .0225, COMMAND_EPSILON_SQUARED = .0225;

  private CreatureDiagnostics() {}

  private static void add(
      List<Row> rows, String section, String title, String detail, String advice, int severity) {
    if (rows.size() < DebugPanelPayload.MAX_ROWS) {
      rows.add(new Row(section, title, detail, advice, severity));
    }
  }

  private static String number(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String vector(Vec3 value) {
    return "(" + number(value.x) + ", " + number(value.y) + ", " + number(value.z) + ")";
  }

  public static List<Row> inspect(ServerSubLevel subject) {
    var body = SubLevelCreatureParts.root(subject);
    long now = body.getLevel().getGameTime();
    var rows = new ArrayList<Row>();
    var health = SubLevelHealth.status(body);
    var armor = SubLevelArmor.status(body);
    var oxygen = SubLevelOxygen.status(body);
    var brain = SubLevelBrains.get(body);
    var behavior = BrainBehaviors.active(body);
    var flags = SubLevelLocomotion.get(body);
    var bounds = SubLevelLocomotion.bounds(body);
    add(
        rows,
        "Overview",
        health.dead() ? "Creature is dead" : "Living creature",
        "HP "
            + number(health.current())
            + "/"
            + number(health.maximum())
            + "; armor "
            + number(armor.current())
            + "/"
            + number(armor.maximum()),
        health.dead()
            ? "Movement and hunting stop on death. Inspect the health/life-support setup."
            : "",
        health.dead() ? 2 : 0);
    add(
        rows,
        "Overview",
        "Hunger and breathing",
        "Hunger "
            + SubLevelData.hunger(body)
            + "/"
            + SubLevelData.maximumHunger(body)
            + "; oxygen "
            + number(oxygen.current())
            + "/"
            + oxygen.maximum()
            + "; supply/demand "
            + number(oxygen.supply())
            + "/"
            + number(oxygen.demand()),
        oxygen.supply() < oxygen.demand()
            ? "Check exposed lungs/gills and the creature's current medium."
            : "",
        oxygen.supply() < oxygen.demand() ? 1 : 0);
    add(
        rows,
        "Overview",
        "Brain and locomotion",
        "Active brain slots: "
            + brain.capacity()
            + "; program: "
            + (behavior == null ? "none" : behavior)
            + "; legs="
            + flags.hasLegs()
            + ", flippers="
            + flags.hasFins()
            + ", climbers="
            + flags.hasWallClimbers(),
        brain.capacity() == 0
            ? "Connect a functioning brain before requesting movement."
            : behavior == null
                ? "Install a behavior card in active memory; the first executable card takes"
                    + " priority."
                : "",
        brain.capacity() == 0 || behavior == null ? 1 : 0);
    add(
        rows,
        "Collisions",
        "Full physical span",
        number(bounds.getXsize())
            + " x "
            + number(bounds.getYsize())
            + " x "
            + number(bounds.getZsize())
            + " blocks; generated compact collision bodies: "
            + SubLevelLegs.collisionShapes(body),
        "Leg collision and combat geometry remain enabled. Leave clearance for the entire"
            + " articulated span.",
        0);
    var request = BrainMotorControls.current(body);
    var position = SubLevelPathfinder.position(body);
    var previous = MOTION.get(body);
    boolean moved =
        previous == null || position.distanceToSqr(previous.position()) > MOVEMENT_EPSILON_SQUARED;
    boolean driving = request.velocity().lengthSqr() > COMMAND_EPSILON_SQUARED;
    long lastMoved = moved || !driving ? now : previous.lastMoved();
    MOTION.put(body, new Motion(position, lastMoved));
    var rejected = BrainMotorControls.lastRejection(body);
    if (rejected != null) {
      add(
          rows,
          "Collisions",
          "Confirmed: movement clearance rejected",
          "Requested velocity " + vector(rejected.movement()) + " at tick " + rejected.tick(),
          "Inspect the next waypoint and full leg/body clearance. A smaller rest-angle sweep, a"
              + " wider route, or more usable joint bend may help. The exact trapped joint is not"
              + " identified by this signal.",
          2);
    } else if (driving && now - lastMoved >= STALL_TICKS) {
      add(
          rows,
          "Advice",
          "Suspected: commanded movement without progress",
          "A movement request is active, but the body has not moved appreciably for "
              + (now - lastMoved)
              + " ticks.",
          "Inspect joint limits and footholds, then test the same stance on open ground. This"
              + " observation alone cannot distinguish a trapped joint from body/support problems.",
          1);
    } else {
      add(
          rows,
          "Advice",
          "No confirmed stall in this sample",
          "Command velocity "
              + vector(request.velocity())
              + "; yaw "
              + number(request.yawRate())
              + "; sample tick "
              + now,
          "Keep this panel open for motion history. No movement request can also mean waiting,"
              + " arrival, a card condition, or no route.",
          0);
    }
    var path = SubLevelPaths.get(body);
    add(
        rows,
        "Navigation",
        path == null ? "No current route" : "Active waypoint route",
        path == null
            ? "There is no non-expired route published by the behavior."
            : path.mode()
                + "; waypoint "
                + (path.next() + 1)
                + "/"
                + path.points().size()
                + "; revision "
                + path.revision()
                + "; goal "
                + vector(path.points().getLast()),
        path == null && behavior != null
            ? "Check target visibility, hunger conditions, locomotion, terrain support and passage"
                + " width. A missing route alone does not prove the legs are stuck."
            : "",
        path == null && driving ? 1 : 0);
    add(
        rows,
        "Navigation",
        "Cached visual targets",
        "Visible targets in the sight cache: " + SubLevelVision.detected(body).size(),
        "Check eye facing and unobstructed views. This panel reads the existing cache and casts no"
            + " extra vision rays.",
        0);
    if (HuntingBehavior.ID.equals(behavior)) {
      var types = HuntingBehavior.preyTypes(body);
      add(
          rows,
          "Navigation",
          "Hunting conditions",
          "Prey types: "
              + types.size()
              + "; hunger "
              + SubLevelData.hunger(body)
              + "/"
              + SubLevelData.maximumHunger(body),
          types.isEmpty()
              ? "Open the hunting card, add prey types, and save its whitelist."
              : "A new hunt starts at half hunger or less. An active meal continues until full. A"
                  + " mouth/weapon, visible prey and a usable route are also required.",
          types.isEmpty() ? 2 : 0);
    }
    add(
        rows,
        "Legs",
        "Physical support",
        "Compact ground contacts: "
            + SubLevelLegs.contacts(body)
            + "; segment bodies: "
            + SubLevelLegs.segments(body).size(),
        flags.hasLegs() && SubLevelLegs.contacts(body) < 3
            ? "For arachnid adhesion, retain at least three real supporting toes. Check reach and"
                + " solid support; water does not anchor a toe."
            : "",
        flags.hasLegs() && SubLevelLegs.contacts(body) == 0 ? 1 : 0);
    var components = new TreeMap<String, Integer>();
    int sockets = 0, shownLegs = 0;
    for (var member : SubLevelCreatureParts.members(body)) {
      for (var cell : SubLevelHealth.blocks(member)) {
        var state = body.getLevel().getBlockState(cell);
        components.merge(
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
        if (!(state.getBlock() instanceof CompactLegSocketBlock)) {
          continue;
        }
        sockets++;
        if (shownLegs++ >= MAX_LEG_ROWS) {
          continue;
        }
        var tuning = LimbTuning.get(body.getLevel(), cell);
        var limits = new ArrayList<String>();
        int locked = 0;
        for (int joint = 0; joint < 3; joint++) {
          var setting = SocketJointSettings.get(member, SocketJointSettings.compact(cell, joint));
          limits.add(
              "J" + (joint + 1) + " swivel=" + setting.swivel() + " bend=+/-" + setting.bend());
          if (setting.swivel() == 0 && setting.bend() == 0) {
            locked++;
          }
        }
        BlockPos relative = cell.subtract(member.getPlot().getCenterBlock());
        add(
            rows,
            "Legs",
            "Socket " + relative + " (" + state.getBlock().getName().getString() + ")",
            "Height "
                + state.getValue(CompactLegSocketBlock.HEIGHT)
                + "; rest "
                + tuning.restAngle()
                + " degrees; passive stiffness/damping "
                + number(tuning.stiffness())
                + "/"
                + number(tuning.damping())
                + ". "
                + String.join("; ", limits),
            locked > 0
                ? "Confirmed: "
                    + locked
                    + " joint(s) have zero swivel and zero bend. Use the Positioner on those joints"
                    + " or reset their limits. Check remaining clearance before increasing range."
                : "Rest angle sets the neutral direction; joint limits set available motion."
                    + " Passive tuning is not powered walking speed.",
            locked > 0 ? 2 : 0);
      }
    }
    if (sockets > MAX_LEG_ROWS) {
      add(
          rows,
          "Legs",
          "Additional sockets",
          (sockets - MAX_LEG_ROWS) + " further sockets omitted from this bounded snapshot.",
          "Component totals below still include the whole creature.",
          0);
    }
    int shown = 0;
    for (var entry : components.entrySet()) {
      if (shown++ >= MAX_COMPONENT_ROWS) {
        break;
      }
      add(rows, "Components", entry.getKey(), "Installed blocks: " + entry.getValue(), "", 0);
    }
    if (components.size() > MAX_COMPONENT_ROWS) {
      add(
          rows,
          "Components",
          "Additional component types",
          (components.size() - MAX_COMPONENT_ROWS) + " more types omitted.",
          "",
          0);
    }
    return List.copyOf(rows);
  }
}
