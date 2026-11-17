package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;
import org.Azkerash.create_bioships.disabled.wip.AirshipPrototype;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Connected surface eyes extend sight; they do not reveal buried world machines. */
public final class BioshipVision {
  static final MaterialCost EYE_COST = new MaterialCost(2, 1, 0);
  private List<BlockPos> eyes = List.of();
  private long scanned = Long.MIN_VALUE;

  private void scan(LivingCoreBlockEntity core) {
    if (scanned == core.getLevel().getGameTime()) return;
    scanned = core.getLevel().getGameTime();
    eyes =
        BioshipDecay.blocks(core.ship()).stream()
            .filter(p -> core.getLevel().getBlockState(p).is(Create_bioships.SURFACE_EYE.get()))
            .filter(
                p -> {
                  var state = core.getLevel().getBlockState(p);
                  return !core.getLevel()
                      .getBlockState(
                          p.relative(state.getValue(SurfaceEyeBlock.FACING).getOpposite()))
                      .isAir();
                })
            .toList();
    // Existing saved eyes predate the window-mount flags; refresh them when the ship sees them.
    if (!core.getLevel().isClientSide)
      for (var eye : eyes) {
        var state = core.getLevel().getBlockState(eye);
        var mounted = SurfaceEyeBlock.mounted(state, core.getLevel(), eye);
        if (!state.equals(mounted)) core.getLevel().setBlock(eye, mounted, 2);
      }
  }

  public record View(Vec3 origin, Vec3 forward, double range) {
    public boolean contains(Vec3 point) {
      Vec3 delta = point.subtract(origin);
      if (delta.lengthSqr() > range * range) return false;
      return delta.lengthSqr() < .000001 || delta.normalize().dot(forward.normalize()) >= .5 - 1e-9;
    }
  }

  public int maximumDistance(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    int chunks = level.getServer().getPlayerList().getViewDistance();
    var player =
        core.creator() == null ? null : level.getServer().getPlayerList().getPlayer(core.creator());
    if (player == null) {
      var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      player =
          level.getServer().getPlayerList().getPlayers().stream()
              .filter(p -> p.serverLevel() == level)
              .min(java.util.Comparator.comparingDouble(p -> p.position().distanceToSqr(root)))
              .orElse(null);
    }
    return distanceLimit(chunks, player);
  }

  static int distanceLimit(int serverChunks, net.minecraft.server.level.ServerPlayer observer) {
    if (observer != null && observer.requestedViewDistance() > 0)
      serverChunks = Math.min(serverChunks, observer.requestedViewDistance());
    return Math.max(1, serverChunks) * 16;
  }

  public boolean inView(LivingCoreBlockEntity core, View view, Vec3 point) {
    if (!view.contains(point)) return false;
    var pos = BlockPos.containing(point);
    var level = (ServerLevel) core.getLevel();
    if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
    // Sample the visible surface too: a solid block's interior is dark even in daylight.
    var surface =
        BlockPos.containing(point.add(view.origin().subtract(point).normalize().scale(.75)));
    int light = level.getMaxLocalRawBrightness(pos);
    if (level.getChunkSource().hasChunk(surface.getX() >> 4, surface.getZ() >> 4))
      light = Math.max(light, level.getMaxLocalRawBrightness(surface));
    double range = Math.max(4, view.range() * (.2 + .8 * light / 15.0));
    return view.origin().distanceToSqr(point) <= range * range;
  }

  static boolean clearMaterial(
      net.minecraft.world.level.block.state.BlockState state, Vec3 direction) {
    if (state.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
    var block = state.getBlock();
    if (block instanceof OneWayWindowBlock)
      return direction.dot(
              Vec3.atLowerCornerOf(state.getValue(OneWayWindowBlock.FACING).getNormal()))
          > 0;
    return exposedTube(state)
        || block instanceof net.minecraft.world.level.block.TransparentBlock
        || block == net.minecraft.world.level.block.Blocks.GLASS_PANE
        || block instanceof net.minecraft.world.level.block.StainedGlassPaneBlock
        || block instanceof net.minecraft.world.level.block.BushBlock
        || block instanceof net.minecraft.world.level.block.GrowingPlantBlock
        || block instanceof net.minecraft.world.level.block.SugarCaneBlock
        || block instanceof net.minecraft.world.level.block.CactusBlock
        || block instanceof net.minecraft.world.level.block.BambooStalkBlock
        || block instanceof net.minecraft.world.level.block.BambooSaplingBlock
        || block instanceof net.minecraft.world.level.block.VineBlock
        || block instanceof net.minecraft.world.level.block.WaterlilyBlock;
  }

  public void invalidate() {
    scanned = Long.MIN_VALUE;
  }

  private record Sighting(
      net.minecraft.world.level.block.state.BlockState state, long seen, boolean full) {}

  private final Map<BlockPos, Sighting> remembered = new HashMap<>();
  private final TargetMemory<java.util.UUID> entityFreshness = new TargetMemory<>();
  private net.minecraft.world.entity.LivingEntity trackedEntity;
  private Vec3 trackedPoint;

  void rememberEntity(net.minecraft.world.entity.LivingEntity entity) {
    if (trackedEntity != entity) entityFreshness.clear();
    trackedEntity = entity;
    trackedPoint = entity.getBoundingBox().getCenter();
    entityFreshness.observe(entity.getUUID());
  }

  net.minecraft.world.entity.LivingEntity rememberedEntity(LivingCoreBlockEntity core) {
    if (trackedEntity == null
        || !trackedEntity.isAlive()
        || trackedEntity.level() != core.getLevel()
        || !entityFreshness.retains(trackedEntity.getUUID())) {
      trackedEntity = null;
      trackedPoint = null;
      entityFreshness.clear();
    }
    return trackedEntity;
  }

  Vec3 knownPosition(net.minecraft.world.entity.LivingEntity entity) {
    return trackedEntity == entity && trackedPoint != null
        ? trackedPoint
        : entity.getBoundingBox().getCenter();
  }

  Vec3 focus(LivingCoreBlockEntity core) {
    var selected = core.brain().lastDecision();
    if (selected == null) return null;
    if (selected.entity() != null)
      return rememberedEntity(core) == selected.entity() ? knownPosition(selected.entity()) : null;
    if (selected.action() == BioshipBrain.Action.ASSIMILATE)
      return selected.block() != null && sourceFreshness.retains(selected.block())
          ? selected.point()
          : null;
    return selected.point();
  }

  private final TargetMemory<BlockPos> sourceFreshness = new TargetMemory<>();

  public long unseenNanos(BlockPos pos) {
    return sourceFreshness.age(pos);
  }

  public void forget(LivingCoreBlockEntity core, BlockPos pos) {
    remembered.remove(pos);
    sourceFreshness.forget(pos);
    if (pos.equals(core.supplyTarget)) core.supplyTarget = null;
  }

  java.util.Set<BlockPos> rememberedSources() {
    return java.util.Set.copyOf(remembered.keySet());
  }

  public net.minecraft.world.level.block.state.BlockState rememberedState(BlockPos pos) {
    var seen = remembered.get(pos);
    return seen == null ? null : seen.state;
  }

  boolean rememberedFull(BlockPos pos) {
    return remembered.containsKey(pos) && remembered.get(pos).full;
  }

  net.minecraft.world.level.block.state.BlockState sourceState(
      LivingCoreBlockEntity core, BlockPos source) {
    long now = core.getLevel().getGameTime();
    if (visible(core, Vec3.atCenterOf(source))) {
      var state = core.getLevel().getBlockState(source);
      if (!BioshipPriorities.supply(state) || !openToNature(core.getLevel(), source)) {
        forget(core, source);
        return null;
      }
      var inventory =
          core.getLevel()
              .getCapability(
                  net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, source, null);
      boolean full =
          core.getLevel().getBlockEntity(source) instanceof net.minecraft.world.Container c
              && !c.isEmpty();
      if (inventory != null)
        for (int slot = 0; slot < inventory.getSlots(); slot++)
          full |= !inventory.getStackInSlot(slot).isEmpty();
      remembered.put(source.immutable(), new Sighting(state, now, full));
      sourceFreshness.observe(source.immutable());
      if (remembered.size() > 128)
        remembered.entrySet().stream()
            .filter(e -> !e.getKey().equals(core.supplyTarget))
            .min(java.util.Comparator.comparingLong(e -> e.getValue().seen))
            .ifPresent(e -> forget(core, e.getKey()));
      return state;
    }
    var seen = remembered.get(source);
    if (seen != null && sourceFreshness.retains(source)) return seen.state;
    forget(core, source);
    return null;
  }

  private int surveyed;

  public List<View> views(LivingCoreBlockEntity core) {
    scan(core);
    List<View> result = new ArrayList<>();
    if (!core.getBlockState().getValue(LivingCoreBlock.CONCEALED)) {
      for (var face : Direction.values())
        result.add(
            new View(
                BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO),
                core.ship().logicalPose().transformNormal(Vec3.atLowerCornerOf(face.getNormal())),
                maximumDistance(core)));
    }
    for (var eye : eyes) {
      var state = core.getLevel().getBlockState(eye);
      var face = state.getValue(SurfaceEyeBlock.FACING);
      Vec3 point = Vec3.atCenterOf(eye).relative(face, -.22);
      if (state.getValue(SurfaceEyeBlock.WINDOW_INTERNAL)) {
        var host = eye.relative(face.getOpposite());
        face = core.getLevel().getBlockState(host).getValue(OneWayWindowBlock.FACING);
        point = Vec3.atCenterOf(host).relative(face, .6);
      }
      result.add(
          new View(
              core.ship().logicalPose().transformPosition(point),
              core.ship().logicalPose().transformNormal(Vec3.atLowerCornerOf(face.getNormal())),
              maximumDistance(core)));
    }
    return result;
  }

  boolean knows(LivingCoreBlockEntity core, BlockPos source) {
    return sourceState(core, source) != null;
  }

  boolean surveyed(LivingCoreBlockEntity core) {
    return !core.hatchling() || surveyed == 63;
  }

  void look(LivingCoreBlockEntity core, Direction face) {
    if (core.getBlockState().getValue(LivingCoreBlock.FACING) != face)
      core.getLevel()
          .setBlock(
              core.getBlockPos(), core.getBlockState().setValue(LivingCoreBlock.FACING, face), 2);
    surveyed |= 1 << face.ordinal();
  }

  private void turn(LivingCoreBlockEntity core) {
    if (!core.getBlockState().getValue(LivingCoreBlock.CONCEALED)) surveyed = 63;
    var focus = focus(core);
    if (core.hatchling() && focus != null) {
      var delta =
          core.ship()
              .logicalPose()
              .transformPositionInverse(focus)
              .subtract(Vec3.atCenterOf(core.getBlockPos()));
      look(core, Direction.getNearest(delta.x, delta.y, delta.z));
    }
    for (var pos : java.util.List.copyOf(remembered.keySet()))
      if (pos.equals(core.supplyTarget)
          || core.getLevel().getGameTime() % 20 == 0
          || !sourceFreshness.retains(pos)) sourceState(core, pos);
  }

  public static boolean exposedTube(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(Create_bioships.INTESTINE.get()) && !state.getValue(IntestineBlock.ENCASED);
  }

  public static boolean ray(LivingCoreBlockEntity core, Vec3 from, Vec3 to) {
    return rayAtCore(core, from, to, core.getBlockPos());
  }

  static boolean rayAtCore(LivingCoreBlockEntity core, Vec3 from, Vec3 to, BlockPos origin) {
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 5));
    var targetCell = BlockPos.containing(to);
    for (int i = 1; i < steps; i++) {
      var worldCell = BlockPos.containing(from.lerp(to, i / (double) steps));
      if (core.getLevel() instanceof ServerLevel server
          && !server.getChunkSource().hasChunk(worldCell.getX() >> 4, worldCell.getZ() >> 4))
        return false;
      if ((!worldCell.equals(targetCell)
              || core.getLevel().getBlockState(worldCell).is(net.minecraft.tags.BlockTags.LEAVES))
          && (!clearMaterial(core.getLevel().getBlockState(worldCell), to.subtract(from))
                  && !core.getLevel()
                      .getBlockState(worldCell)
                      .getCollisionShape(core.getLevel(), worldCell)
                      .isEmpty()
              || core.getLevel().getFluidState(worldCell).is(net.minecraft.tags.FluidTags.LAVA)))
        return false;
      var sample =
          BlockPos.containing(
              core.ship()
                  .logicalPose()
                  .transformPositionInverse(from.lerp(to, i / (double) steps)));
      var state = core.getLevel().getBlockState(sample);
      if (sample.equals(origin) || state.is(Create_bioships.SURFACE_EYE.get())) continue;
      if (sample.equals(core.getBlockPos()) && !origin.equals(sample))
        state = core.getLevel().getBlockState(origin);
      var localDirection =
          core.ship()
              .logicalPose()
              .transformPositionInverse(to)
              .subtract(core.ship().logicalPose().transformPositionInverse(from));
      if (!clearMaterial(state, localDirection)
          && !state.getCollisionShape(core.getLevel(), sample).isEmpty()) return false;
    }
    return true;
  }

  public boolean visible(LivingCoreBlockEntity core, Vec3 point) {
    if (core.ship() == null) return false;
    for (var view : views(core))
      if (inView(core, view, point) && ray(core, view.origin(), point)) return true;
    return false;
  }

  public static boolean openToNature(net.minecraft.world.level.Level level, BlockPos source) {
    int top =
        level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            source.getX(),
            source.getZ());
    for (int y = source.getY() + 1; y < top; y++) {
      var pos = new BlockPos(source.getX(), y, source.getZ());
      var state = level.getBlockState(pos);
      if (state.is(net.minecraft.tags.BlockTags.LEAVES) || clearMaterial(state, new Vec3(0, 1, 0)))
        continue;
      if (!state.getCollisionShape(level, pos).isEmpty()) return false;
    }
    return true;
  }

  boolean exposed(LivingCoreBlockEntity core, BlockPos source) {
    return openToNature(core.getLevel(), source) && visible(core, Vec3.atCenterOf(source));
  }

  void observe(LivingCoreBlockEntity core) {
    turn(core);
  }

  boolean externalEyesPreferred(LivingCoreBlockEntity core) {
    scan(core);
    if (!eyes.isEmpty()) return true;
    var stomach = DigestiveSystem.stomach(core);
    return !core.hungry()
        && core.hasMouth()
        && stomach != null
        && stomach.connected(core)
        && EYE_COST.funded(stomach)
        && EmergencyReserve.after(core, EYE_COST);
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() % 10 == 0 && externalEyesPreferred(core) && eyes.isEmpty()) {
      growEyes(core);
      return;
    }
    if (core.getLevel().getGameTime() % 10 == 0 && externalEyesPreferred(core) && trackFocus(core))
      return;
    if (core.getLevel().getGameTime() % 80 == 0 && !reabsorb(core)) growEyes(core);
  }

  net.minecraft.nbt.CompoundTag save() {
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putInt("Surveyed", surveyed);
    var list = new net.minecraft.nbt.ListTag();
    remembered.forEach(
        (pos, memory) -> {
          var entry = new net.minecraft.nbt.CompoundTag();
          entry.putLong("Pos", pos.asLong());
          entry.putLong("Seen", memory.seen);
          entry.putLong("ObservedMillis", sourceFreshness.observedMillis(pos));
          entry.putBoolean("Full", memory.full);
          entry.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(memory.state));
          list.add(entry);
        });
    tag.put("Sources", list);
    return tag;
  }

  void load(
      net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    surveyed = tag.getInt("Surveyed") & 63;
    remembered.clear();
    sourceFreshness.clear();
    var entries = tag.getList("Sources", net.minecraft.nbt.Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(128, entries.size()); i++) {
      var entry = entries.getCompound(i);
      var state =
          net.minecraft.nbt.NbtUtils.readBlockState(
              registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
              entry.getCompound("State"));
      if (BioshipPriorities.supply(state)) {
        var pos = BlockPos.of(entry.getLong("Pos"));
        sourceFreshness.restore(
            pos,
            entry.contains("ObservedMillis")
                ? entry.getLong("ObservedMillis")
                : System.currentTimeMillis());
        remembered.put(
            BlockPos.of(entry.getLong("Pos")),
            new Sighting(state, entry.getLong("Seen"), entry.getBoolean("Full")));
      }
    }
    scanned = Long.MIN_VALUE;
  }

  boolean reabsorb(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null
        || core.body().dead()
        || core.blueprint().busy()
        || ComponentTransport.busy(core.amoebaComponents())
        || core.coreMotion().active()) return false;
    var all = views(core);
    int first = core.getBlockState().getValue(LivingCoreBlock.CONCEALED) ? 0 : 6;
    if (eyes.size() < 2) return false;
    for (int index = 0; index < eyes.size(); index++) {
      var pos = eyes.get(index);
      var state = core.getLevel().getBlockState(pos);
      if (!state.getValue(SurfaceEyeBlock.AUTOMATIC)) continue;
      var view = all.get(first + index);
      var targets = new ArrayList<Vec3>();
      for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
          for (int z = -1; z <= 1; z++)
            if (x != 0 || y != 0 || z != 0)
              targets.add(view.origin().add(new Vec3(x, y, z).normalize().scale(8)));
      remembered.keySet().forEach(p -> targets.add(Vec3.atCenterOf(p)));
      var focus = focus(core);
      if (focus != null) targets.add(focus);
      boolean needed = false;
      for (var point : targets)
        if (inView(core, view, point) && ray(core, view.origin(), point)) {
          boolean covered = false;
          for (int other = first; other < all.size(); other++)
            if (other != first + index
                && inView(core, all.get(other), point)
                && ray(core, all.get(other).origin(), point)) {
              covered = true;
              break;
            }
          if (!covered) {
            needed = true;
            break;
          }
        }
      if (needed) continue;
      core.getLevel().removeBlock(pos, false);
      stomach.retainFlesh(1);
      stomach.retain(
          new net.minecraft.world.item.ItemStack(
              net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                  net.minecraft.resources.ResourceLocation.parse("biomancy:elastic_fibers"))));
      scanned = Long.MIN_VALUE;
      core.setChanged();
      return true;
    }
    return false;
  }

  boolean trackFocus(LivingCoreBlockEntity core) {
    var focus = focus(core);
    if (focus == null
        || visible(core, focus)
        || core.body().dead()
        || core.blueprint().busy()
        || ComponentTransport.busy(core.amoebaComponents())
        || core.coreMotion().active()) return false;
    scan(core);
    var level = core.getLevel();
    record Mount(BlockPos pos, Direction direction, double distance) {}
    var candidates = new ArrayList<Mount>();
    for (var host : BioshipDecay.blocks(core.ship())) {
      var tissue = level.getBlockState(host);
      if (!BioshipTissue.isTissue(tissue)
          && !tissue.is(Create_bioships.CARAPACE.get())
          && !tissue.is(Create_bioships.ONE_WAY_WINDOW.get())) continue;
      for (var direction : Direction.values()) {
        if (tissue.is(Create_bioships.ONE_WAY_WINDOW.get())
            && tissue.getValue(OneWayWindowBlock.FACING) != direction) continue;
        var pos = host.relative(direction);
        if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) continue;
        var state = level.getBlockState(pos);
        if (!state.isAir()
            && !(state.is(Create_bioships.SURFACE_EYE.get())
                && state.getValue(SurfaceEyeBlock.AUTOMATIC))) continue;
        var origin =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(pos).relative(direction, -.22));
        var forward =
            core.ship().logicalPose().transformNormal(Vec3.atLowerCornerOf(direction.getNormal()));
        var view = new View(origin, forward, maximumDistance(core));
        if (inView(core, view, focus) && ray(core, origin, focus))
          candidates.add(new Mount(pos.immutable(), direction, origin.distanceToSqr(focus)));
      }
    }
    candidates.sort(Comparator.comparingDouble(Mount::distance));
    for (var mount : candidates) {
      var targetState = level.getBlockState(mount.pos);
      if (targetState.is(Create_bioships.SURFACE_EYE.get())) {
        level.setBlock(mount.pos, targetState.setValue(SurfaceEyeBlock.FACING, mount.direction), 3);
        invalidate();
        return true;
      }
      for (var eye : eyes) {
        var original = level.getBlockState(eye);
        if (!original.getValue(SurfaceEyeBlock.AUTOMATIC)) continue;
        level.setBlock(eye, original.setValue(SurfaceEyeBlock.FACING, mount.direction), 2);
        if (ComponentTransport.begin(core.amoebaComponents(), eye, mount.pos, false)) return true;
        level.setBlock(eye, original, 2);
      }
      var stomach = DigestiveSystem.stomach(core);
      if (stomach == null
          || !stomach.connected(core)
          || core.hungry()
          || !EmergencyReserve.after(core, new MaterialCost(2, 1, 0))
          || eyes.size() >= 8
          || stomach.material("biomancy:flesh_bits") < 2
          || stomach.material("biomancy:elastic_fibers") < 1) continue;
      var state =
          Create_bioships.SURFACE_EYE
              .get()
              .defaultBlockState()
              .setValue(SurfaceEyeBlock.FACING, mount.direction)
              .setValue(SurfaceEyeBlock.AUTOMATIC, true);
      level.setBlock(mount.pos, SurfaceEyeBlock.mounted(state, level, mount.pos), 3);
      stomach.spend("biomancy:flesh_bits", 2);
      stomach.spend("biomancy:elastic_fibers", 1);
      invalidate();
      core.setChanged();
      return true;
    }
    return false;
  }

  void growEyes(LivingCoreBlockEntity core) {
    if (core.body().dead()
        || core.blueprint().busy()
        || ComponentTransport.busy(core.amoebaComponents())
        || core.hungry()
        || !core.hasMouth()) return;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null || !stomach.connected(core)) return;
    scan(core);
    var objective = BioshipPriorities.bestWorld(core);
    if (!eyes.isEmpty() && objective != null && objective.rank() <= 2) return;
    var level = (ServerLevel) core.getLevel();
    var cells = BioshipDecay.blocks(core.ship());
    for (var direction : Direction.values()) {
      BlockPos existing =
          eyes.stream()
              .filter(p -> level.getBlockState(p).getValue(SurfaceEyeBlock.FACING) == direction)
              .findFirst()
              .orElse(null);
      Vec3 forward =
          core.ship().logicalPose().transformNormal(Vec3.atLowerCornerOf(direction.getNormal()));
      Vec3 root =
          existing == null
              ? BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)
              : core.ship()
                  .logicalPose()
                  .transformPosition(Vec3.atCenterOf(existing).relative(direction, -.22));
      if (!eyes.isEmpty()
          && (existing != null || visible(core, root.add(forward.scale(8))))
          && ray(core, root, root.add(forward.scale(8)))) continue;
      if (existing == null
          && (!EmergencyReserve.after(core, new MaterialCost(2, 1, 0))
              || eyes.size() >= 6
              || stomach.material("biomancy:flesh_bits") < 2
              || stomach.material("biomancy:elastic_fibers") < 1)) continue;
      var candidates = new ArrayList<>(cells);
      candidates.sort(
          Comparator.<BlockPos>comparingInt(
                  p ->
                      -(p.getX() * direction.getStepX()
                          + p.getY() * direction.getStepY()
                          + p.getZ() * direction.getStepZ()))
              .thenComparingDouble(p -> p.distSqr(core.getBlockPos())));
      for (var host : candidates) {
        var tissue = level.getBlockState(host);
        if (!BioshipTissue.isTissue(tissue)
            && !tissue.is(Create_bioships.CARAPACE.get())
            && !tissue.is(Create_bioships.CORE.get())
            && !tissue.is(Create_bioships.ONE_WAY_WINDOW.get())) continue;
        if (tissue.is(Create_bioships.ONE_WAY_WINDOW.get())
            && tissue.getValue(OneWayWindowBlock.FACING) != direction) continue;
        var target = host.relative(direction);
        if (!level.getBlockState(target).isAir()
            || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target)))
          continue;
        if (AirshipPrototype.available()
            && !core.isMature()
            && core.plannedBody().stream()
                .anyMatch(p -> p.offset().equals(target.subtract(core.getBlockPos())))) continue;
        Vec3 point =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(target).relative(direction, -.22));
        if (!ray(core, point, point.add(forward.scale(8)))) continue;
        if (existing != null) {
          if (ComponentTransport.begin(core.amoebaComponents(), existing, target, false)) return;
        } else {
          level.setBlock(
              target,
              Create_bioships.SURFACE_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(SurfaceEyeBlock.FACING, direction)
                  .setValue(SurfaceEyeBlock.AUTOMATIC, true),
              3);
          stomach.spend("biomancy:flesh_bits", 2);
          stomach.spend("biomancy:elastic_fibers", 1);
          scanned = Long.MIN_VALUE;
          core.setChanged();
          return;
        }
      }
    }
  }
}
