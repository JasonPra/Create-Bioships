package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/**
 * Server-owned blueprint and resumable landing/evacuation/build job. Coordinates are core-relative.
 */
public final class BioshipBlueprint {
  public record Room(int id, String name, int x, int z, int width, int length) {
    public boolean contains(BlockPos p) {
      return Math.abs(p.getX() - x) <= width / 2 && Math.abs(p.getZ() - z) <= length / 2;
    }

    Room at(int x, int z) {
      return new Room(id, name, x, z, width, length);
    }

    CompoundTag save() {
      var t = new CompoundTag();
      t.putInt("Id", id);
      t.putString("Name", name);
      t.putInt("X", x);
      t.putInt("Z", z);
      t.putInt("W", width);
      t.putInt("L", length);
      return t;
    }

    static Room load(CompoundTag t) {
      return new Room(
          t.getInt("Id"),
          t.getString("Name"),
          t.getInt("X"),
          t.getInt("Z"),
          Math.clamp(t.getInt("W"), 3, 11),
          Math.clamp(t.getInt("L"), 3, 21));
    }
  }

  public static class Draft {
    public final List<Room> rooms = new ArrayList<>();
    public final Map<BlockPos, Integer> edits =
        new LinkedHashMap<>(); // 0 remove wall, 1 wall, 2 membrane
    public int selected;

    public Draft(List<Room> rooms) {
      this.rooms.addAll(rooms);
    }

    public void edit(int action) {
      if (action == 3 && selected < rooms.size() && rooms.get(selected).id >= 2) {
        var r = rooms.get(selected);
        rooms.set(selected, new Room(r.id, r.name, r.x, r.z, r.length, r.width));
        return;
      }
      if (action >= 100 && action < 116) {
        selected = action - 100;
        return;
      }
      int op = action / 2500, cell = action % 2500, x = cell % 49 - 24, z = cell / 49 - 24;
      if (op == 1 && selected < rooms.size()) rooms.set(selected, rooms.get(selected).at(x, z));
      if ((op == 2 || op == 3) && rooms.size() < 12) {
        int id = rooms.stream().mapToInt(Room::id).max().orElse(1) + 1;
        rooms.add(
            new Room(
                id,
                op == 2 ? "Room " + id : "Corridor " + id,
                x,
                z,
                op == 2 ? 7 : 3,
                op == 2 ? 7 : 5));
        selected = rooms.size() - 1;
      }
      if (op >= 4 && op <= 6) edits.put(new BlockPos(x, 2, z), op - 4);
    }
  }

  private List<Room> rooms =
      new ArrayList<>(
          List.of(new Room(0, "Cockpit", 0, -6, 9, 11), new Room(1, "Engine room", 0, 5, 9, 11)));
  private List<Room> pendingRooms = new ArrayList<>();
  private final Map<BlockPos, BlockState> target = new LinkedHashMap<>();
  private final Map<BlockPos, CompoundTag> cargo = new LinkedHashMap<>();
  private final Map<BlockPos, BlockPos> sources = new HashMap<>();
  private String phase = "idle", status = "Ready";
  private int revision, search, workTicks, carryTicks;
  private Vec3 landing, searchGoal, carryStart, carryEnd;
  private UUID carried;
  private int paid;
  private int baseCockpitZ = -6;
  private boolean demolitionDone;

  void engineWidth(int width) {
    rooms.replaceAll(
        r -> r.id == 1 ? new Room(r.id, r.name, r.x, r.z, Math.max(r.width, width), r.length) : r);
    revision++;
  }

  public List<Room> rooms() {
    return List.copyOf(rooms);
  }

  public String phase() {
    return phase;
  }

  public String status() {
    return status;
  }

  public int revision() {
    return revision;
  }

  public boolean busy() {
    return AirshipPrototype.available() && !phase.equals("idle");
  }

  public boolean locked() {
    return AirshipPrototype.available() && (phase.equals("building") || phase.equals("evacuating"));
  }

  public UUID carried() {
    return carried;
  }

  public int carryTicks() {
    return carryTicks;
  }

  public Vec3 carryEnd() {
    return carryEnd;
  }

  public Vec3 carryStart() {
    return carryStart;
  }

  public BlockPos offset(BlockPos original) {
    if (original.equals(BlockPos.ZERO)) return original;
    int id = original.getZ() < 0 ? 0 : 1;
    var room = rooms.stream().filter(r -> r.id == id).findFirst().orElse(null);
    return room == null
        ? original
        : original.offset(room.x, 0, room.z - (id == 0 ? baseCockpitZ : 5));
  }

  public BlockPos engineLocal(BlockPos local) {
    var r = rooms.stream().filter(a -> a.id == 1).findFirst().orElse(rooms.getFirst());
    return local.offset(-r.x, 0, 5 - r.z);
  }

  public int available(LivingCoreBlockEntity core) {
    var s = DigestiveSystem.stomach(core);
    return s == null ? 0 : s.material("biomancy:flesh_bits");
  }

  private static BlockPos structureLocal(LivingCoreBlockEntity core, BlockPos local) {
    return local.offset(core.getBlockPos().subtract(core.structuralOrigin()));
  }

  private static BlockPos coreLocal(LivingCoreBlockEntity core, BlockPos local) {
    return local.offset(core.structuralOrigin().subtract(core.getBlockPos()));
  }

  public Map<BlockPos, BlockState> proposal(LivingCoreBlockEntity core, Draft draft) {
    var plan = new LinkedHashMap<BlockPos, BlockState>();
    sources.clear();
    var owners =
        BioshipRoomOwnership.assign(
            core.body().memory.keySet(),
            rooms,
            core.getBlockPos().subtract(core.structuralOrigin()));
    for (var e : core.body().memory.entrySet()) {
      var p = e.getKey();
      var q = p;
      if (!p.equals(BlockPos.ZERO) && !p.equals(coreLocal(core, BlockPos.ZERO)))
        if (owners.containsKey(p)) {
          var old = owners.get(p);
          var next = draft.rooms.stream().filter(r -> r.id == old.id).findFirst().orElse(old);
          q = p.offset(next.x - old.x, 0, next.z - old.z);
        }
      plan.put(q, e.getValue());
      sources.put(q, p);
    }
    // An internally sheltered core can occupy a floor cell. Moving room tissue must not
    // overwrite that protected anchor, even when a translated floor lands on its position.
    var anchor = core.body().memory.get(BlockPos.ZERO);
    if (anchor != null) {
      plan.put(BlockPos.ZERO, anchor);
      sources.put(BlockPos.ZERO, BlockPos.ZERO);
    }
    for (var room : draft.rooms)
      if (rooms.stream().noneMatch(r -> r.id == room.id)) {
        for (int x = -room.width / 2; x <= room.width / 2; x++)
          for (int z = -room.length / 2; z <= room.length / 2; z++) {
            // Round the corners, leaving a usable floor and continuous curved shell.
            if (Math.abs(x) == room.width / 2 && Math.abs(z) == room.length / 2 && room.width > 3)
              continue;
            int px = room.x + x, pz = room.z + z;
            boolean edge =
                Math.abs(x) == room.width / 2
                    || Math.abs(z) == room.length / 2
                    || room.width > 3
                        && Math.abs(x) + Math.abs(z) == room.width / 2 + room.length / 2 - 1;
            for (int y = 1; y <= 5; y++)
              if (y == 1 || y == 5 || edge)
                plan.putIfAbsent(
                    coreLocal(core, new BlockPos(px, y, pz)),
                    BioshipTissue.body().defaultBlockState());
          }
      }
    var membrane =
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("biomancy:biometric_membrane"))
            .defaultBlockState();
    draft.edits.forEach(
        (p, kind) -> {
          for (int y = 2; y <= 3; y++) {
            var q = coreLocal(core, new BlockPos(p.getX(), y, p.getZ()));
            if (kind == 0) plan.remove(q);
            else plan.put(q, kind == 1 ? BioshipTissue.body().defaultBlockState() : membrane);
          }
        });
    return plan;
  }

  public int cost(LivingCoreBlockEntity core, Draft draft) {
    var plan = proposal(core, draft);
    int n = 0;
    for (var e : plan.entrySet()) if (!e.getValue().equals(core.body().memory.get(e.getKey()))) n++;
    return n;
  }

  public String validate(LivingCoreBlockEntity core, Draft d, Map<BlockPos, BlockState> plan) {
    if (d.rooms.size() > 12 || plan.size() > 10000) return "Blueprint is too large";
    for (var r : d.rooms)
      if (Math.abs(r.x) + r.width / 2 > 24 || Math.abs(r.z) + r.length / 2 > 24)
        return "Keep rooms within the 49 x 49 blueprint";
    for (var r : d.rooms)
      for (var other : d.rooms)
        if (r.id < other.id
            && Math.abs(r.x - other.x) < r.width / 2 + other.width / 2
            && Math.abs(r.z - other.z) < r.length / 2 + other.length / 2)
          return "Room interiors overlap";
    for (var edit : d.edits.keySet()) {
      var old = plan.get(coreLocal(core, edit));
      if (old != null && !editable(old))
        return "Move organs with the wrench; this panel edits tissue and doors";
      if (d.rooms.stream().noneMatch(r -> r.contains(edit)))
        return "Walls and doors must belong to a room";
      if (edit.getX() == 0 && edit.getZ() == 0 && d.edits.get(edit) == 1)
        return "Keep the core's access clear";
    }
    // All floor areas must be joined. A moved room needs a corridor back to the ship.
    Set<BlockPos> floors = new HashSet<>();
    plan.forEach(
        (p, s) -> {
          var local = structureLocal(core, p);
          // Exterior storage and its tubes can share floor height without being room floors.
          boolean floor =
              BioshipTissue.isTissue(s)
                  || s.is(Create_bioships.CARAPACE.get())
                  || s.is(Create_bioships.INTESTINE.get()) && s.getValue(IntestineBlock.ENCASED);
          if (local.getY() == 1 && floor && d.rooms.stream().anyMatch(r -> r.contains(local)))
            floors.add(p);
        });
    if (floors.isEmpty()) return "A ship needs floors";
    Set<BlockPos> seen = new HashSet<>();
    var todo = new ArrayDeque<BlockPos>();
    todo.add(floors.iterator().next());
    while (!todo.isEmpty()) {
      var p = todo.remove();
      if (!floors.contains(p) || !seen.add(p)) continue;
      for (var dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST))
        todo.add(p.relative(dir));
    }
    if (seen.size() != floors.size()) return "Connect every room with a corridor";
    Set<BlockPos> joined = new HashSet<>();
    var blocks = new ArrayDeque<BlockPos>();
    blocks.add(BlockPos.ZERO);
    while (!blocks.isEmpty()) {
      var p = blocks.remove();
      if (!plan.containsKey(p) || !joined.add(p)) continue;
      for (var dir : Direction.values()) blocks.add(p.relative(dir));
    }
    if (joined.size() != plan.size()) return "Keep all tissue connected to the core";
    // Preserve every machine and structural anchor; wall editing cannot silently erase inventory.
    for (var e : core.body().memory.entrySet())
      if (!editable(e.getValue())) {
        BlockPos dest = null;
        for (var s : sources.entrySet())
          if (s.getValue().equals(e.getKey())) {
            dest = s.getKey();
            break;
          }
        if (dest == null || !e.getValue().equals(plan.get(dest)))
          return "A room or wall would overwrite a component";
      }
    return null;
  }

  private static boolean editable(BlockState s) {
    return BioshipTissue.isTissue(s)
        || s.is(Create_bioships.CARAPACE.get())
        || s.is(Create_bioships.ONE_WAY_WINDOW.get())
        || s.is(Create_bioships.MEMBRANE.get())
        || s.is(Create_bioships.DOOR.get())
        || BuiltInRegistries.BLOCK
            .getKey(s.getBlock())
            .toString()
            .equals("biomancy:biometric_membrane");
  }

  public boolean confirm(LivingCoreBlockEntity core, Draft d, int baseRevision) {
    if (!AirshipPrototype.available()
        || core.body().dead()
        || busy()
        || core.rescue().active()
        || revision != baseRevision
        || !core.isMature()
        || ComponentTransport.busy(core)) {
      status = "Ship is busy or blueprint changed; reopen the panel";
      return false;
    }
    core.body().observe(core);
    var plan = proposal(core, d);
    var error = validate(core, d, plan);
    if (error != null) {
      status = error;
      return false;
    }
    int needed = 0;
    for (var e : plan.entrySet())
      if (!e.getValue().equals(core.body().memory.get(e.getKey()))) needed++;
    if (needed == 0 && plan.equals(core.body().memory)) {
      status = "No blueprint changes";
      return false;
    }
    if (!core.canFly()) {
      status = "Flight organs and levitite are required to find a landing site";
      return false;
    }
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null || !stomach.spend("biomancy:flesh_bits", needed)) {
      status = "Not enough stored biomass";
      return false;
    }
    demolitionDone = false;
    paid = needed;
    target.clear();
    target.putAll(plan);
    pendingRooms = new ArrayList<>(d.rooms);
    cargo.clear();
    landing = null;
    search = 0;
    workTicks = 0;
    phase = "searching";
    status = "Finding a clear landing site";
    revision++;
    core.setChanged();
    return true;
  }

  public void tick(LivingCoreBlockEntity core) {
    if (!busy() || core.ship() == null) return;
    var level = (ServerLevel) core.getLevel();
    workTicks++;
    if (phase.equals("searching") && level.getGameTime() % 20 == 0) {
      Vec3 origin = physical(core, BlockPos.ZERO);
      int ring = (search + 7) / 8, dir = search % 8;
      double angle = dir * Math.PI / 4;
      int dx = (int) Math.round(Math.cos(angle) * ring * 8),
          dz = (int) Math.round(Math.sin(angle) * ring * 8);
      search = (search + 1) % 72;
      Vec3 candidate = new Vec3(Math.floor(origin.x) + dx + .5, 0, Math.floor(origin.z) + dz + .5);
      landing = site(core, candidate);
      if (landing != null) {
        phase = "landing";
        status = "Travelling to the landing site";
        core.setChanged();
      } else {
        searchGoal = candidate.add(Math.cos(angle) * 16, origin.y, Math.sin(angle) * 16);
        status = "Searching nearby terrain for a dry, level landing site";
      }
    }
    if (phase.equals("landing")) {
      if (landing == null || site(core, landing) == null) {
        landing = null;
        phase = "searching";
        return;
      }
      Vec3 here = physical(core, BlockPos.ZERO);
      double horizontal = Math.hypot(here.x - landing.x, here.z - landing.z);
      if (horizontal < .65 && Math.abs(here.y - landing.y) < .4) {
        phase = "evacuating";
        status = "Tentacles are clearing the ship";
        core.setChanged();
      }
    }
    if (phase.equals("evacuating") || phase.equals("building")) {
      if (landing == null || site(core, landing) == null) {
        status = "Landing area changed; waiting for clear ground";
        return;
      }
      Vec3 here = physical(core, BlockPos.ZERO);
      if (here.distanceToSqr(landing) > 1) {
        status = "Waiting for the hull to settle on the landing site";
        return;
      }
      if (!evacuate(core)) return;
      if (!phase.equals("building")) {
        // Capture every moving machine once, before any block is cleared; no cloned inventories.
        for (var e : target.entrySet()) {
          var src = sources.getOrDefault(e.getKey(), e.getKey());
          var p = core.getBlockPos().offset(src);
          if (!src.equals(e.getKey())
              && level.getBlockEntity(p) != null
              && level.getBlockState(p).is(e.getValue().getBlock()))
            cargo.put(
                e.getKey(), level.getBlockEntity(p).saveWithFullMetadata(level.registryAccess()));
        }
        phase = "building";
        status = "Reconfiguring living tissue";
        core.setChanged();
      }
      build(core);
    }
  }

  /** A level, dry foundation and clear headroom for BOTH old and proposed footprints. */
  Vec3 site(LivingCoreBlockEntity core, Vec3 candidate) {
    var level = (ServerLevel) core.getLevel();
    Set<BlockPos> footprint = new HashSet<>();
    for (var p : target.keySet()) footprint.add(new BlockPos(p.getX(), 0, p.getZ()));
    for (var p : core.body().memory.keySet()) footprint.add(new BlockPos(p.getX(), 0, p.getZ()));
    int y = Integer.MIN_VALUE;
    for (var p : footprint) {
      Vec3 d = core.ship().logicalPose().transformNormal(new Vec3(p.getX(), 0, p.getZ()));
      var ground =
          new BlockPos((int) Math.floor(candidate.x + d.x), 0, (int) Math.floor(candidate.z + d.z));
      if (!level.getChunkSource().hasChunk(ground.getX() >> 4, ground.getZ() >> 4)
          || !level.getWorldBorder().isWithinBounds(ground)) return null;
      int height =
          level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.getX(), ground.getZ());
      var below = new BlockPos(ground.getX(), height - 1, ground.getZ());
      if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
          || !level.getFluidState(below).isEmpty()) return null;
      if (y == Integer.MIN_VALUE) y = height;
      else if (height != y) return null;
      for (int h = 0; h < 9; h++)
        if (!BioshipIntegration.growthSpace(
            level, new BlockPos(ground.getX(), height + h, ground.getZ()))) return null;
    }
    if (y <= level.getMinBuildHeight() || y + 10 >= level.getMaxBuildHeight()) return null;
    return new Vec3(
        candidate.x,
        y + .5 + core.getBlockPos().getY() - core.structuralOrigin().getY(),
        candidate.z);
  }

  public Vec3 flight(LivingCoreBlockEntity core) {
    if (phase.equals("idle")) return null;
    if (phase.equals("evacuating") || phase.equals("building"))
      return landing == null
          ? Vec3.ZERO
          : landing.subtract(physical(core, BlockPos.ZERO)).scale(1.3);
    Vec3 goal = landing != null ? landing : searchGoal;
    if (goal == null) return Vec3.ZERO;
    Vec3 here = physical(core, BlockPos.ZERO);
    double horizontal = Math.hypot(goal.x - here.x, goal.z - here.z);
    var level = (ServerLevel) core.getLevel();
    int terrain =
        level.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (int) Math.floor(here.x),
            (int) Math.floor(here.z));
    double altitude =
        landing == null || horizontal > 2
            ? Math.min(level.getMaxBuildHeight() - 10, Math.max(goal.y + 10, terrain + 12))
            : goal.y;
    Vec3 flat = new Vec3(goal.x - here.x, 0, goal.z - here.z);
    if (flat.length() > 1) flat = flat.normalize();
    double vertical = Math.clamp((altitude - here.y) * .8, -1, 1);
    if (here.y < altitude - 2 && horizontal > 2) flat = Vec3.ZERO;
    Vec3 motion = flat.add(0, vertical, 0);
    if (!worldClear(level, here, here.add(motion.scale(5))) && horizontal > 2)
      motion = new Vec3(0, 1, 0);
    return motion;
  }

  static boolean worldClear(net.minecraft.world.level.Level level, Vec3 from, Vec3 to) {
    // Ordinary world cells deliberately exclude the owner's Sable collision geometry.
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 5));
    for (int i = 0; i <= steps; i++) {
      var p = BlockPos.containing(from.lerp(to, i / (double) steps));
      if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
          || !level.getFluidState(p).isEmpty()) return false;
    }
    return true;
  }

  static Vec3 physical(LivingCoreBlockEntity core, BlockPos relative) {
    return core.ship()
        .logicalPose()
        .transformPosition(Vec3.atCenterOf(core.getBlockPos().offset(relative)));
  }

  boolean aboard(LivingCoreBlockEntity core, Entity entity) {
    Vec3 p =
        core.ship()
            .logicalPose()
            .transformPositionInverse(entity.position())
            .subtract(Vec3.atLowerCornerOf(core.structuralOrigin()));
    if (p.y < -.5 || p.y > 10) return false;
    return rooms.stream()
            .anyMatch(
                r ->
                    Math.abs(p.x - r.x) <= r.width / 2.0 + 1
                        && Math.abs(p.z - r.z) <= r.length / 2.0 + 1)
        || pendingRooms.stream()
            .anyMatch(
                r ->
                    Math.abs(p.x - r.x) <= r.width / 2.0 + 1
                        && Math.abs(p.z - r.z) <= r.length / 2.0 + 1);
  }

  private Vec3 exit(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    Vec3 center = physical(core, BlockPos.ZERO);
    for (int radius = 8; radius <= 38; radius += 2)
      for (int i = 0; i < 16; i++) {
        double a = i * Math.PI / 8;
        int x = (int) Math.floor(center.x + Math.cos(a) * radius),
            z = (int) Math.floor(center.z + Math.sin(a) * radius);
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var floor = new BlockPos(x, y - 1, z);
        Vec3 point = new Vec3(x + .5, y + .05, z + .5);
        Vec3 local =
            core.ship()
                .logicalPose()
                .transformPositionInverse(point)
                .subtract(Vec3.atLowerCornerOf(core.structuralOrigin()));
        if (rooms.stream().anyMatch(r -> r.contains(BlockPos.containing(local)))
            || pendingRooms.stream().anyMatch(r -> r.contains(BlockPos.containing(local))))
          continue;
        if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
            && level.getFluidState(floor).isEmpty()
            && level.noCollision(new AABB(x + .1, y, z + .1, x + .9, y + 2, z + .9))) return point;
      }
    return null;
  }

  private boolean evacuate(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    if (carried != null) {
      var entity = level.getEntity(carried);
      if (entity != null) {
        double t = Math.clamp(++carryTicks / 60.0, 0, 1);
        Vec3 point = carryStart.lerp(carryEnd, t).add(0, Math.sin(Math.PI * t) * 3, 0);
        Vec3 local = core.ship().logicalPose().transformPositionInverse(point);
        for (var cell :
            BlockPos.betweenClosed(
                BlockPos.containing(local.add(-.45, 0, -.45)),
                BlockPos.containing(local.add(.45, 1.85, .45)))) {
          var state = level.getBlockState(cell);
          if (editable(state) && core.body().memory.containsKey(cell.subtract(core.getBlockPos())))
            clear(core, cell.subtract(core.getBlockPos()));
        }
        if (!level.noCollision(
            new AABB(
                point.x - .3, point.y, point.z - .3, point.x + .3, point.y + 1.8, point.z + .3))) {
          status = "Evacuation path is obstructed";
          carryTicks--;
          return false;
        }
        entity.stopRiding();
        entity.teleportTo(point.x, point.y, point.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        if (carryTicks < 60) {
          sync(core);
          return false;
        }
      }
      carried = null;
      carryStart = null;
      carryEnd = null;
      carryTicks = 0;
      sync(core);
      return false;
    }
    Vec3 center = physical(core, BlockPos.ZERO);
    for (var e :
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center, center).inflate(48),
            entity ->
                !(entity instanceof BioshipTargetEntity)
                    && !entity.isSpectator()
                    && aboard(core, entity))) {
      var destination = exit(core);
      if (destination == null) {
        status = "Waiting for a safe place to put passengers";
        return false;
      }
      if (e instanceof ServerPlayer player) player.closeContainer();
      carried = e.getUUID();
      carryStart = e.position();
      carryEnd = destination;
      carryTicks = 0;
      sync(core);
      return false;
    }
    return true;
  }

  private void sync(LivingCoreBlockEntity core) {
    core.setChanged();
    if (core.getLevel().getGameTime() % 4 == 0 || carryTicks == 0)
      core.getLevel()
          .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }

  private void clear(LivingCoreBlockEntity core, BlockPos local) {
    var p = core.getBlockPos().offset(local);
    core.getLevel().removeBlockEntity(p);
    core.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 18);
  }

  private static boolean matches(BlockState live, BlockState wanted) {
    if (wanted == null) return false;
    if (wanted.is(Create_bioships.CORE.get())) return live.is(wanted.getBlock());
    if (wanted.is(Create_bioships.INTESTINE.get()))
      return live.is(wanted.getBlock())
          && live.getValue(IntestineBlock.ENCASED) == wanted.getValue(IntestineBlock.ENCASED);
    return live.equals(wanted);
  }

  private void build(LivingCoreBlockEntity core) {
    var level = core.getLevel();
    // Clear only cells remembered as ours; a new foreign obstruction pauses the job.
    if (!demolitionDone) {
      for (var e : core.body().memory.entrySet()) {
        if (e.getKey().equals(BlockPos.ZERO) || matches(e.getValue(), target.get(e.getKey())))
          continue;
        var p = core.getBlockPos().offset(e.getKey());
        if (level.getBlockState(p).isAir()) continue;
        if (!level.getBlockState(p).is(e.getValue().getBlock())) {
          status =
              "Changed component at "
                  + e.getKey()
                  + ": "
                  + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock())
                  + " (expected "
                  + BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock())
                  + ")";
          return;
        }
        if (level.getBlockEntity(p) != null)
          for (var destination : sources.entrySet())
            if (destination.getValue().equals(e.getKey())
                && !destination.getKey().equals(e.getKey()))
              cargo.put(
                  destination.getKey(),
                  level.getBlockEntity(p).saveWithFullMetadata(level.registryAccess()));
        clear(core, e.getKey());
        core.setChanged();
        return;
      }
      demolitionDone = true;
      core.setChanged();
    }
    var ordered = new ArrayList<>(target.entrySet());
    ordered.sort(
        Comparator.comparingInt(
            e ->
                BioshipTissue.isTissue(e.getValue())
                        || e.getValue().is(Create_bioships.CARAPACE.get())
                    ? e.getKey().getY()
                    : 20 + e.getKey().getY()));
    for (var e : ordered) {
      var p = core.getBlockPos().offset(e.getKey());
      var state = level.getBlockState(p);
      if (matches(state, e.getValue())) continue;
      if (!state.isAir()) {
        status = "A foreign block obstructs reconstruction";
        return;
      }
      Vec3 physical = physical(core, e.getKey());
      if (!BioshipIntegration.growthSpace(level, BlockPos.containing(physical))) {
        status = "Terrain obstructs reconstruction";
        return;
      }
      var chunk = new net.minecraft.world.level.ChunkPos(p);
      var plot = core.ship().getPlot();
      if (!plot.contains(chunk)) {
        status = "Blueprint exceeds this ship's plot";
        return;
      }
      if (plot.getChunkHolder(plot.toLocal(chunk)) == null) plot.newEmptyChunk(chunk);
      if (!e.getValue().canSurvive(level, p)) continue;
      BioshipIntegration.clearGrowthPlant(level, BlockPos.containing(physical));
      level.setBlock(p, e.getValue(), 3);
      var saved = cargo.remove(e.getKey());
      if (saved != null && level.getBlockEntity(p) instanceof BlockEntity be) {
        saved.putInt("x", p.getX());
        saved.putInt("y", p.getY());
        saved.putInt("z", p.getZ());
        be.loadWithComponents(saved, level.registryAccess());
        be.setChanged();
      }
      core.setChanged();
      return;
    }
    for (var e : target.entrySet())
      if (!matches(level.getBlockState(core.getBlockPos().offset(e.getKey())), e.getValue())) {
        status = "Waiting for structural support";
        return;
      }
    if (core.preferredMouth() != null)
      for (var e : sources.entrySet())
        if (e.getValue().equals(core.preferredMouth())) {
          core.preferredMouth(e.getKey());
          break;
        }
    core.body().memory.clear();
    core.body().memory.putAll(BioshipBody.actual(core));
    rooms = new ArrayList<>(pendingRooms);
    target.clear();
    sources.clear();
    cargo.clear();
    phase = "idle";
    status = "Blueprint complete";
    BioshipHealthChanges.changed(core);
    paid = 0;
    revision++;
    core.setChanged();
    BioshipAccess.tick(core);
  }

  int burnBufferedReserve() {
    for (var saved : cargo.values())
      if (saved.getString("id").equals("create_bioships:stomach")) {
        int nutrients = saved.getInt("NutrientReserve");
        if (nutrients > 0) {
          saved.putInt("NutrientReserve", nutrients - 1);
          return 20;
        }
        int energy = StomachBlockEntity.burnStored(saved.getCompound("TissueReserves"));
        if (energy > 0) return energy;
      }
    return 0;
  }

  CompoundTag save() {
    var t = new CompoundTag();
    var r = new ListTag();
    rooms.forEach(a -> r.add(a.save()));
    t.put("Rooms", r);
    var pr = new ListTag();
    pendingRooms.forEach(a -> pr.add(a.save()));
    t.put("PendingRooms", pr);
    t.putInt("BaseCockpitZ", baseCockpitZ);
    t.putString("Phase", phase);
    t.putString("Status", status);
    t.putInt("Revision", revision);
    t.putInt("Search", search);
    t.putInt("Paid", paid);
    t.putBoolean("DemolitionDone", demolitionDone);
    t.put("Target", BioshipBody.saveCells(target));
    var c = new ListTag();
    cargo.forEach(
        (p, v) -> {
          var e = new CompoundTag();
          e.putLong("P", p.asLong());
          e.put("BE", v.copy());
          c.add(e);
        });
    t.put("Cargo", c);
    var s = new ListTag();
    sources.forEach(
        (p, v) -> {
          var e = new CompoundTag();
          e.putLong("P", p.asLong());
          e.putLong("Source", v.asLong());
          s.add(e);
        });
    t.put("Sources", s);
    if (searchGoal != null) t.put("SearchGoal", vector(searchGoal));
    if (landing != null) t.put("Landing", vector(landing));
    if (carried != null) {
      t.putUUID("Carried", carried);
      t.put("CarryStart", vector(carryStart));
      t.put("CarryEnd", vector(carryEnd));
      t.putInt("CarryTicks", carryTicks);
    }
    return t;
  }

  void load(CompoundTag t, HolderLookup.Provider registries) {
    baseCockpitZ =
        t.contains("BaseCockpitZ") ? t.getInt("BaseCockpitZ") : t.contains("Rooms") ? -5 : -6;
    if (t.contains("Rooms")) {
      rooms.clear();
      var r = t.getList("Rooms", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(12, r.size()); i++) rooms.add(Room.load(r.getCompound(i)));
    }
    if (rooms.isEmpty()) rooms.add(new Room(1, "Engine room", 0, 5, 9, 11));
    pendingRooms.clear();
    var pr = t.getList("PendingRooms", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(12, pr.size()); i++)
      pendingRooms.add(Room.load(pr.getCompound(i)));
    phase = t.contains("Phase") ? t.getString("Phase") : "idle";
    status = t.contains("Status") ? t.getString("Status") : "Ready";
    revision = t.getInt("Revision");
    search = t.getInt("Search");
    paid = t.getInt("Paid");
    demolitionDone = t.getBoolean("DemolitionDone");
    target.clear();
    target.putAll(BioshipBody.loadCells(t.getList("Target", Tag.TAG_COMPOUND), registries));
    cargo.clear();
    var c = t.getList("Cargo", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(c.size(), 10000); i++) {
      var e = c.getCompound(i);
      cargo.put(BlockPos.of(e.getLong("P")), e.getCompound("BE").copy());
    }
    sources.clear();
    var s = t.getList("Sources", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(s.size(), 10000); i++) {
      var e = s.getCompound(i);
      sources.put(BlockPos.of(e.getLong("P")), BlockPos.of(e.getLong("Source")));
    }
    searchGoal = t.contains("SearchGoal") ? readVector(t.getCompound("SearchGoal")) : null;
    landing = t.contains("Landing") ? readVector(t.getCompound("Landing")) : null;
    carried = t.hasUUID("Carried") ? t.getUUID("Carried") : null;
    carryStart = t.contains("CarryStart") ? readVector(t.getCompound("CarryStart")) : null;
    carryEnd = t.contains("CarryEnd") ? readVector(t.getCompound("CarryEnd")) : null;
    carryTicks = t.getInt("CarryTicks");
  }

  private static CompoundTag vector(Vec3 v) {
    var t = new CompoundTag();
    t.putDouble("X", v.x);
    t.putDouble("Y", v.y);
    t.putDouble("Z", v.z);
    return t;
  }

  private static Vec3 readVector(CompoundTag t) {
    return new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
  }
}
