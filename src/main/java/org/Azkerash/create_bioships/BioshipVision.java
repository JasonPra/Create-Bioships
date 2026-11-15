package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Connected surface eyes extend sight; they do not reveal buried world machines. */
public final class BioshipVision {
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
      var root = BioshipBlueprint.physical(core, BlockPos.ZERO);
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

  boolean inView(LivingCoreBlockEntity core, View view, Vec3 point) {
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

  void invalidate() {
    scanned = Long.MIN_VALUE;
  }

  private final Map<BlockPos, Long> remembered = new HashMap<>();
  private int surveyed;

  public List<View> views(LivingCoreBlockEntity core) {
    scan(core);
    List<View> result = new ArrayList<>();
    if (!core.getBlockState().getValue(LivingCoreBlock.CONCEALED)) {
      for (var face : Direction.values())
        result.add(
            new View(
                BioshipBlueprint.physical(core, BlockPos.ZERO),
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
    if (exposed(core, source)) {
      remembered.put(source.immutable(), core.getLevel().getGameTime());
      return true;
    }
    return remembered.getOrDefault(source, Long.MIN_VALUE) > core.getLevel().getGameTime() - 200;
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
    remembered.entrySet().removeIf(e -> e.getValue() < core.getLevel().getGameTime() - 200);
  }

  static boolean exposedTube(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(Create_bioships.INTESTINE.get()) && !state.getValue(IntestineBlock.ENCASED);
  }

  static boolean ray(LivingCoreBlockEntity core, Vec3 from, Vec3 to) {
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

  static boolean openToNature(net.minecraft.world.level.Level level, BlockPos source) {
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

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() % 80 == 0 && !reabsorb(core)) growEyes(core);
  }

  net.minecraft.nbt.CompoundTag save() {
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putInt("Surveyed", surveyed);
    return tag;
  }

  void load(net.minecraft.nbt.CompoundTag tag) {
    surveyed = tag.getInt("Surveyed") & 63;
    remembered.clear();
    scanned = Long.MIN_VALUE;
  }

  boolean reabsorb(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null
        || core.body().dead()
        || core.blueprint().busy()
        || OrganMotion.busy(core)
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

  void growEyes(LivingCoreBlockEntity core) {
    if (core.body().dead()
        || core.blueprint().busy()
        || OrganMotion.busy(core)
        || core.hungry()
        || !core.hasMouth()) return;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null || !stomach.connected(core)) return;
    var objective = BioshipPriorities.bestWorld(core);
    if (objective != null && objective.rank() <= 2) return;
    scan(core);
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
              ? BioshipBlueprint.physical(core, BlockPos.ZERO)
              : core.ship()
                  .logicalPose()
                  .transformPosition(Vec3.atCenterOf(existing).relative(direction, -.22));
      if ((existing != null || visible(core, root.add(forward.scale(8))))
          && ray(core, root, root.add(forward.scale(8)))) continue;
      if (existing == null
          && (eyes.size() >= 6
              || stomach.material("biomancy:flesh_bits") < 2
              || stomach.material("biomancy:elastic_fibers") < (core.isMature() ? 1 : 13)))
        continue;
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
        if (!tissue.is(Create_bioships.FLESH.get())
            && !tissue.is(Create_bioships.CARAPACE.get())
            && !tissue.is(Create_bioships.CORE.get())
            && !tissue.is(Create_bioships.ONE_WAY_WINDOW.get())) continue;
        if (tissue.is(Create_bioships.ONE_WAY_WINDOW.get())
            && tissue.getValue(OneWayWindowBlock.FACING) != direction) continue;
        var target = host.relative(direction);
        if (!level.getBlockState(target).isAir()
            || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target)))
          continue;
        if (!core.isMature()
            && core.plannedBody().stream()
                .anyMatch(p -> p.offset().equals(target.subtract(core.getBlockPos())))) continue;
        Vec3 point =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(target).relative(direction, -.22));
        if (!ray(core, point, point.add(forward.scale(8)))) continue;
        if (existing != null) {
          if (OrganMotion.begin(core, existing, target, false)) return;
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
