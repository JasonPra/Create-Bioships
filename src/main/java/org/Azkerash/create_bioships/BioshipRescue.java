package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.amoeba.ComponentTransport;

/** Carries allies through a temporary, conserved hull aperture, never through intact blocks. */
public final class BioshipRescue {
  private final Map<BlockPos, BlockState> retracted = new LinkedHashMap<>();
  private List<BlockPos> path = List.of();
  private UUID passenger;
  private int cursor, animation, stalled;
  private String phase = "idle";
  private Vec3 carried;
  private long nextAttempt;

  public boolean active() {
    return !phase.equals("idle");
  }

  public Vec3 carriedPosition() {
    return carried;
  }

  public boolean retracted(BlockPos pos) {
    return retracted.containsKey(pos);
  }

  public Map<BlockPos, BlockState> tissue() {
    return Collections.unmodifiableMap(retracted);
  }

  public double withdrawal() {
    return phase.equals("opening")
        ? Math.clamp(animation / 8d, 0, 1)
        : phase.equals("closing") ? 1 - Math.clamp(animation / 8d, 0, 1) : 1;
  }

  private static boolean hull(BlockState state) {
    return BioshipTissue.isTissue(state)
        || state.is(Create_bioships.CARAPACE.get())
        || state.getBlock() instanceof OneWayWindowBlock
        || state.is(Create_bioships.DOOR.get());
  }

  private Set<BlockPos> obstruction(LivingCoreBlockEntity core, Vec3 world, boolean allowHull) {
    var level = (ServerLevel) core.getLevel();
    var box =
        new AABB(world.x - .3, world.y, world.z - .3, world.x + .3, world.y + 1.79, world.z + .3);
    for (var p :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
      if (!level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)
          || !level.getWorldBorder().isWithinBounds(p)) return null;
      if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
          || !level.getFluidState(p).isEmpty()) return null;
    }
    Set<BlockPos> holes = new LinkedHashSet<>();
    Vec3 min = null, max = null;
    for (int x = 0; x < 2; x++)
      for (int y = 0; y < 2; y++)
        for (int z = 0; z < 2; z++) {
          var local =
              core.ship()
                  .logicalPose()
                  .transformPositionInverse(
                      new Vec3(
                          x == 0 ? box.minX : box.maxX,
                          y == 0 ? box.minY : box.maxY,
                          z == 0 ? box.minZ : box.maxZ));
          min =
              min == null
                  ? local
                  : new Vec3(
                      Math.min(min.x, local.x), Math.min(min.y, local.y), Math.min(min.z, local.z));
          max =
              max == null
                  ? local
                  : new Vec3(
                      Math.max(max.x, local.x), Math.max(max.y, local.y), Math.max(max.z, local.z));
        }
    for (var p : BlockPos.betweenClosed(BlockPos.containing(min), BlockPos.containing(max))) {
      var state = level.getBlockState(p);
      if (state.getCollisionShape(level, p).isEmpty()) continue;
      if (!allowHull || !hull(state) || state.hasBlockEntity() || p.equals(core.getBlockPos()))
        return null;
      holes.add(p.immutable());
    }
    return holes;
  }

  private List<BlockPos> route(LivingCoreBlockEntity core, Player player, BlockPos goal) {
    var local = core.ship().logicalPose().transformPositionInverse(player.position());
    var start = BlockPos.containing(local);
    record Node(BlockPos p, double cost, double estimate) {}
    var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::estimate));
    Map<BlockPos, Double> costs = new HashMap<>();
    Map<BlockPos, BlockPos> previous = new HashMap<>();
    open.add(new Node(start, 0, Math.sqrt(start.distSqr(goal))));
    costs.put(start, 0d);
    previous.put(start, start);
    while (!open.isEmpty() && costs.size() < 5000) {
      var node = open.remove();
      if (node.cost > costs.getOrDefault(node.p, Double.MAX_VALUE)) continue;
      if (node.p.equals(goal)) {
        var result = new ArrayList<BlockPos>();
        var p = goal;
        while (!p.equals(start)) {
          result.add(p);
          p = previous.get(p);
        }
        Collections.reverse(result);
        return result;
      }
      for (var d : Direction.values()) {
        var next = node.p.relative(d);
        if (next.distManhattan(core.structuralOrigin()) > 40) continue;
        var world =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atLowerCornerOf(next).add(.5, .06, .5));
        var holes = obstruction(core, world, true);
        if (holes == null) continue;
        double cost = node.cost + 1 + holes.size() * 5;
        if (cost >= costs.getOrDefault(next, Double.MAX_VALUE)) continue;
        costs.put(next, cost);
        previous.put(next, node.p);
        open.add(new Node(next, cost, cost + Math.sqrt(next.distSqr(goal))));
      }
    }
    return List.of();
  }

  boolean begin(LivingCoreBlockEntity core, Player player) {
    if (active()
        || ComponentTransport.busy(core.amoebaComponents())
        || core.blueprint().busy()
        || !core.isMature()
        || !core.access().allows(core, player)
        || core.blueprint().aboard(core, player)) return false;
    var level = (ServerLevel) core.getLevel();
    for (var offset :
        List.of(
            new BlockPos(0, 2, -2),
            new BlockPos(-1, 2, -2),
            new BlockPos(1, 2, -2),
            new BlockPos(0, 2, -1))) {
      var goal = core.structuralOrigin().offset(offset);
      var end =
          core.ship().logicalPose().transformPosition(Vec3.atLowerCornerOf(goal).add(.5, .06, .5));
      if (obstruction(core, end, false) == null
          || level.getBlockState(goal.below()).getCollisionShape(level, goal.below()).isEmpty())
        continue;
      var planned = route(core, player, goal);
      if (planned.isEmpty()) continue;
      var holes = new LinkedHashSet<BlockPos>();
      for (var p : planned) {
        var found =
            obstruction(
                core,
                core.ship()
                    .logicalPose()
                    .transformPosition(Vec3.atLowerCornerOf(p).add(.5, .06, .5)),
                true);
        if (found == null) return false;
        holes.addAll(found);
      }
      if (holes.size() > 24 || !connectedWithout(core, holes)) continue;
      for (var p : holes) retracted.put(p, level.getBlockState(p));
      for (var p : holes) level.removeBlock(p, false);
      stalled = 0;
      path = planned;
      passenger = player.getUUID();
      cursor = 0;
      animation = 0;
      phase = "opening";
      carried = player.getBoundingBox().getCenter();
      sync(core);
      return true;
    }
    return false;
  }

  void tick(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    if (!active()) {
      if (core.body().dead()
          || !core.isMature()
          || !core.canFly()
          || !core.autonomy().fleeing()
          || core.autonomy().trustedPilot(core)
          || core.blueprint().busy()
          || level.getGameTime() < nextAttempt) return;
      nextAttempt = level.getGameTime() + 20;
      var root = BioshipBlueprint.physical(core, BlockPos.ZERO);
      var allies =
          level.getEntitiesOfClass(
              Player.class,
              new AABB(root, root).inflate(16),
              p ->
                  p.isAlive()
                      && !p.isSpectator()
                      && p.onGround()
                      && core.access().allows(core, p)
                      && !core.blueprint().aboard(core, p));
      allies.sort(Comparator.comparingDouble(p -> p.distanceToSqr(root)));
      for (var player : allies) if (begin(core, player)) break;
      return;
    }
    if ((!AirshipPrototype.available() || core.body().dead()) && !phase.equals("closing")) {
      phase = "closing";
      animation = 0;
      passenger = null;
      carried = null;
    }
    if (phase.equals("opening")) {
      if (++animation >= 8) phase = "carrying";
      sync(core);
      return;
    }
    if (phase.equals("carrying")) {
      if (++stalled > 100) {
        phase = "closing";
        animation = 0;
        passenger = null;
        carried = null;
        sync(core);
        return;
      }
      var entity = passenger == null ? null : level.getEntity(passenger);
      if (!(entity instanceof Player player)
          || !player.isAlive()
          || !core.access().allows(core, player)) {
        phase = "closing";
        animation = 0;
        carried = null;
        sync(core);
        return;
      }
      var target =
          core.ship()
              .logicalPose()
              .transformPosition(Vec3.atLowerCornerOf(path.get(cursor)).add(.5, .06, .5));
      var delta = target.subtract(player.position());
      var next =
          player.position().add(delta.scale(Math.min(1, .4 / Math.max(.001, delta.length()))));
      // Sweep every small step: temporary hull openings do not authorize crossing world terrain.
      for (int i = 1; i <= 4; i++)
        if (obstruction(core, player.position().lerp(next, i / 4d), false) == null) {
          sync(core);
          return;
        }
      if (!level
          .getEntitiesOfClass(
              LivingEntity.class,
              new AABB(next.x - .3, next.y, next.z - .3, next.x + .3, next.y + 1.79, next.z + .3),
              e -> e != player && !(e instanceof BioshipTargetEntity))
          .isEmpty()) return;
      stalled = 0;
      player.stopRiding();
      player.teleportTo(next.x, next.y, next.z);
      player.setDeltaMovement(Vec3.ZERO);
      player.fallDistance = 0;
      carried = player.getBoundingBox().getCenter();
      if (next.distanceToSqr(target) < .02 && ++cursor >= path.size()) {
        phase = "closing";
        animation = 0;
        passenger = null;
        carried = null;
      }
      sync(core);
      return;
    }
    if (phase.equals("closing")) {
      if (++animation < 8) {
        sync(core);
        return;
      }
      for (var entry : new ArrayList<>(retracted.entrySet())) {
        var p = entry.getKey();
        if (level.getBlockState(p).equals(entry.getValue())) {
          retracted.remove(p);
          continue;
        }
        if (!level.getBlockState(p).isAir()) continue;
        var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p));
        if (!level
            .getBlockState(BlockPos.containing(world))
            .getCollisionShape(level, BlockPos.containing(world))
            .isEmpty()) continue;
        if (!level
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                    world.x - .5,
                    world.y - .5,
                    world.z - .5,
                    world.x + .5,
                    world.y + .5,
                    world.z + .5),
                e -> !(e instanceof BioshipTargetEntity))
            .isEmpty()) continue;
        level.setBlock(p, entry.getValue(), 3);
        retracted.remove(p);
      }
      if (retracted.isEmpty()) {
        phase = "idle";
        path = List.of();
        nextAttempt = level.getGameTime() + 40;
      }
      sync(core);
    }
  }

  private static boolean connectedWithout(LivingCoreBlockEntity core, Set<BlockPos> holes) {
    var remaining = new HashSet<>(BioshipDecay.blocks(core.ship()));
    remaining.removeAll(holes);
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    while (!queue.isEmpty()) {
      var point = queue.removeFirst();
      for (var direction : Direction.values()) {
        var next = point.relative(direction);
        if (remaining.contains(next) && seen.add(next)) queue.add(next);
      }
    }
    return seen.containsAll(remaining);
  }

  private void sync(LivingCoreBlockEntity core) {
    core.setChanged();
    core.getLevel()
        .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.putString("Phase", phase);
    tag.putInt("Cursor", cursor);
    tag.putInt("Stalled", stalled);
    tag.putInt("Animation", animation);
    tag.putLong("Next", nextAttempt);
    if (passenger != null) tag.putUUID("Passenger", passenger);
    if (carried != null) tag.put("Carried", VisionDebug.vector(carried));
    tag.putLongArray("Path", path.stream().mapToLong(BlockPos::asLong).toArray());
    tag.put("Tissue", BioshipBody.saveCells(retracted));
    return tag;
  }

  void load(CompoundTag tag, HolderLookup.Provider registries) {
    phase = tag.contains("Phase") ? tag.getString("Phase") : "idle";
    cursor = tag.getInt("Cursor");
    stalled = Math.clamp(tag.getInt("Stalled"), 0, 100);
    animation = tag.getInt("Animation");
    nextAttempt = tag.getLong("Next");
    passenger = tag.hasUUID("Passenger") ? tag.getUUID("Passenger") : null;
    carried = tag.contains("Carried") ? VisionDebug.vector(tag.getCompound("Carried")) : null;
    path = Arrays.stream(tag.getLongArray("Path")).limit(160).mapToObj(BlockPos::of).toList();
    cursor = Math.clamp(cursor, 0, Math.max(0, path.size() - 1));
    retracted.clear();
    retracted.putAll(BioshipBody.loadCells(tag.getList("Tissue", Tag.TAG_COMPOUND), registries));
    if (phase.equals("carrying") && path.isEmpty()) phase = "closing";
  }
}
