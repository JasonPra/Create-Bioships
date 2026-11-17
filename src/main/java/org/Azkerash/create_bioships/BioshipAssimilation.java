package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.merging.ComponentMerging;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Conserved cargo is swallowed first; fitting kept organs is a separate, subsequent job. */
public final class BioshipAssimilation {
  private final List<ComponentTransport.Move> arms = new ArrayList<>();
  private final Map<UUID, String> grasperBindings = new HashMap<>();

  Collection<String> busyGraspers() {
    return List.copyOf(grasperBindings.values());
  }

  public String grasper(UUID cargo) {
    return grasperBindings.getOrDefault(cargo, "core");
  }

  private final List<CompoundTag> stock = new ArrayList<>();
  private UUID wreck;
  private boolean settling, deploymentBlocked, resumeWreck, feedingJob;

  boolean feeding() {
    return feedingJob;
  }

  private long retry;
  private CompoundTag repair = new CompoundTag();

  public boolean repairing() {
    return !repair.isEmpty();
  }

  boolean reservesRepairCell(BlockPos pos) {
    if (!repairing()) return false;
    for (var entry : repair.getList("Parts", 10)) {
      var part = (CompoundTag) entry;
      if (BlockPos.of(part.getLong("Source")).equals(pos)
          || BlockPos.of(part.getLong("Target")).equals(pos)) return true;
      var state = part.getCompound("State");
      if (state.getString("Name").equals("create_bioships:feeding_mouth")) {
        var face = Direction.byName(state.getCompound("Properties").getString("facing"));
        if (face != null && BlockPos.of(part.getLong("Target")).relative(face).equals(pos))
          return true;
      }
    }
    return false;
  }

  public List<ComponentTransport.Move> arms() {
    return List.copyOf(arms);
  }

  public boolean active() {
    return !arms.isEmpty();
  }

  boolean committed() {
    return wreck != null && !settling;
  }

  boolean pending() {
    return !stock.isEmpty() || wreck != null || active();
  }

  boolean finishing() {
    return repairing() || !deploymentBlocked && !stock.isEmpty() && (wreck == null || settling);
  }

  public int stored() {
    return stock.stream().mapToInt(t -> Math.max(1, t.getInt("Count"))).sum();
  }

  int health(LivingCoreBlockEntity core) {
    long total = 0;
    for (var tag : stock)
      total +=
          (long)
                  BioshipHealthPool.part(
                      state(core, tag), tag.getCompound("Entity"), !tag.getBoolean("HostReleased"))
              * Math.max(1, tag.getInt("Count"));
    return (int) Math.min(Integer.MAX_VALUE, total);
  }

  double load(LivingCoreBlockEntity core) {
    double mass = 0;
    for (var tag : stock)
      mass +=
          core.crawl().blockLoad(core, core.getBlockPos(), state(core, tag))
              * Math.max(1, tag.getInt("Count"));
    return mass;
  }

  private static BlockState state(LivingCoreBlockEntity core, CompoundTag tag) {
    return NbtUtils.readBlockState(
        core.getLevel()
            .registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
        tag.getCompound("State"));
  }

  boolean acceptItem(LivingCoreBlockEntity core, net.minecraft.world.item.ItemStack stack) {
    if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem block)
        || block.getBlock() == Create_bioships.CORE.get()) return false;
    var state = block.getBlock().defaultBlockState();
    if (!core.canFly()
        && core.crawl().carriedLoad(core) + core.crawl().blockLoad(core, core.getBlockPos(), state)
            > core.crawl().loadCapacity()) return false;
    var tag = new CompoundTag();
    tag.put("State", NbtUtils.writeBlockState(state));
    tag.putInt("Count", 1);
    var entity = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (entity != null) tag.put("Entity", entity.copyTag());
    stock.add(tag);
    BioshipHealthChanges.changed(core);
    core.setChanged();
    return true;
  }

  public void receive(LivingCoreBlockEntity core, ServerSubLevel cargo, BlockPos pos) {
    var state = core.getLevel().getBlockState(pos);
    var tag = new CompoundTag();
    tag.put("State", NbtUtils.writeBlockState(state));
    tag.putInt("Count", 1);
    tag.putBoolean(
        "HostReleased",
        core.organMove() != null
            && !core.organMove().external()
            && core.organMove().restoreFlesh());
    BioshipHealthChanges.changed(core);
    if (cargo.getUserDataTag() != null) {
      tag.putBoolean("Wreck", cargo.getUserDataTag().getBoolean("BioshipScavenged"));
      tag.putBoolean("Recycle", cargo.getUserDataTag().getBoolean("BioshipEmergencyRecycle"));
    }
    if (cargo.getUserDataTag() != null && cargo.getUserDataTag().contains("RepairSlot"))
      tag.putInt("RepairSlot", cargo.getUserDataTag().getInt("RepairSlot"));
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null)
      tag.put("Entity", entity.saveWithFullMetadata(core.getLevel().registryAccess()));
    if (entity == null && !tag.contains("RepairSlot"))
      for (var existing : stock)
        if (!existing.contains("Entity")
            && existing.getBoolean("Wreck") == tag.getBoolean("Wreck")
            && existing.getBoolean("Recycle") == tag.getBoolean("Recycle")
            && existing.getCompound("State").equals(tag.getCompound("State"))) {
          existing.putInt("Count", existing.getInt("Count") + 1);
          core.setChanged();
          return;
        }
    stock.add(tag);
    BioshipHealthChanges.changed(core);
    core.setChanged();
  }

  /** A committed wreck remains a source even after ordinary reserve targets are satisfied. */
  List<BioshipScavenging.Source> remaining(LivingCoreBlockEntity core) {
    if (wreck == null || settling) return List.of();
    var body = SubLevelContainer.getContainer(core.getLevel()).getSubLevel(wreck);
    if (!(body instanceof ServerSubLevel ship) || ship.isRemoved() || !BioshipDecay.dead(ship)) {
      settling = true;
      return List.of();
    }
    var result = new ArrayList<BioshipScavenging.Source>();
    for (var pos : BioshipDecay.blocks(ship)) {
      var state = core.getLevel().getBlockState(pos);
      if (state.isAir() || state.is(Create_bioships.CORE.get())) continue;
      var point = ship.logicalPose().transformPosition(Vec3.atCenterOf(pos));
      if (!core.crawl().reachableGoal(BlockPos.containing(point))) continue;
      result.add(new BioshipScavenging.Source(pos, point, state));
    }
    if (result.isEmpty()) {
      settling = true;
      resumeWreck = false;
    }
    return result;
  }

  private List<Vec3> cluster(LivingCoreBlockEntity core, Vec3 primary) {
    var points = new ArrayList<Vec3>();
    points.add(primary);
    for (var goal : core.brain().queuedGoals())
      if (points.size() < BioshipGraspers.slots(core).size()
          && goal.action() == BioshipBrain.Action.ASSIMILATE
          && goal.point() != null
          && goal.point().distanceToSqr(primary) > 0
          && goal.point().distanceToSqr(primary) <= 36) points.add(goal.point());
    return points;
  }

  Vec3 gatheringPoint(LivingCoreBlockEntity core, Vec3 primary) {
    var points = cluster(core, primary);
    var sum = Vec3.ZERO;
    for (var point : points) sum = sum.add(point);
    return sum.scale(1d / points.size());
  }

  boolean clusterReachable(LivingCoreBlockEntity core, Vec3 candidate) {
    var goal = core.brain().lastDecision();
    if (goal == null || goal.action() != BioshipBrain.Action.ASSIMILATE || goal.point() == null)
      return true;
    for (var target : cluster(core, goal.point()))
      if (!BioshipReach.at(core, candidate, target, true)) return false;
    return true;
  }

  boolean start(LivingCoreBlockEntity core, BioshipBrain.Decision chosen) {
    if (repairing()
        || active()
        || core.organMove() != null
        || core.coreMotion().active()
        || settling) return false;
    if (chosen.action() != BioshipBrain.Action.SCAVENGE
        && chosen.action() != BioshipBrain.Action.ASSIMILATE) return false;
    if (chosen.block() == null) return false;
    var sourceShip = BioshipIntegration.containing(core.getLevel(), chosen.block());
    if (chosen.point() == null
        || !BioshipReach.clear(core, chosen.point(), true, sourceShip == null ? 5 : 14))
      return false;
    if (sourceShip != null) {
      if (!BioshipDecay.dead(sourceShip) || sourceShip == core.ship()) return false;
      wreck = sourceShip.getUniqueId();
    }
    feedingJob = BioshipNutrition.foodRelated(core, chosen);
    var sources = new ArrayList<BioshipScavenging.Source>();
    sources.add(
        new BioshipScavenging.Source(
            chosen.block(), chosen.point(), core.getLevel().getBlockState(chosen.block())));
    if (wreck != null) sources.addAll(remaining(core));
    else
      for (var goal : core.brain().queuedGoals())
        if (goal.action() == BioshipBrain.Action.ASSIMILATE
            && goal.block() != null
            && goal.point() != null
            && goal.point().distanceToSqr(chosen.point()) <= 100)
          sources.add(
              new BioshipScavenging.Source(
                  goal.block(), goal.point(), core.getLevel().getBlockState(goal.block())));
    var seen = new HashSet<BlockPos>();
    double reserved = core.crawl().carriedLoad(core);
    for (var source : sources) {
      if (arms.size() >= BioshipGraspers.slots(core).size()) break;
      var grasper = BioshipGraspers.available(core, source.point(), grasperBindings.values());
      if (grasper == null) break;
      if (!seen.add(source.pos())
          || source.state().isAir()
          || source.state().is(Create_bioships.CORE.get())) continue;
      if (!BioshipReach.clear(core, source.point(), true, sourceShip == null ? 5 : 14)) continue;
      if (sourceShip == null && !BioshipIntegration.knownReachableSource(core, source.pos()))
        continue;
      if (sourceShip != null
          && !BioshipScavenging.physicallyReachable(core, sourceShip, source.pos(), source.point()))
        continue;
      double extra = core.crawl().blockLoad(core, source.pos(), source.state());
      if (!core.canFly() && reserved + extra > core.crawl().loadCapacity()) {
        settling = true;
        resumeWreck = !stock.isEmpty();
        break;
      }
      if (ComponentTransport.swallow(core.amoebaComponents(), source.pos())) {
        arms.add(core.organMove());
        grasperBindings.put(core.organMove().cargo(), grasper.key());
        core.organMove(null);
        reserved += extra;
      }
    }
    if (!arms.isEmpty()) {
      core.setChanged();
      return true;
    }
    return false;
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.ship() == null) return;
    int beforeArms = arms.size();
    for (var move : List.copyOf(arms)) {
      core.workingOrganMove(move);
      ComponentTransport.tick(core.amoebaComponents());
      arms.remove(move);
      if (core.organMove() != null) arms.add(core.organMove());
      else grasperBindings.remove(move.cargo());
      core.workingOrganMove(null);
    }
    if (beforeArms != arms.size()) core.organMove(null);
    if (active() || core.organMove() != null || core.coreMotion().active()) return;
    if (repairing()) {
      tickRepair(core);
      return;
    }
    if (wreck != null && !settling) {
      var remaining = remaining(core);
      if (!remaining.isEmpty()
          && core.crawl().carriedLoad(core) >= core.crawl().loadCapacity() - 1) {
        settling = true;
        resumeWreck = true;
      }
      if (!settling) return;
    }
    if (!stock.isEmpty() && (wreck == null || settling)) settle(core);
    if (stock.isEmpty() && settling) {
      if (!resumeWreck) wreck = null;
      settling = false;
      resumeWreck = false;
      core.setChanged();
    }
  }

  boolean beginRepair(LivingCoreBlockEntity core, List<BioshipAnatomy.RepairPart> plan) {
    if (plan.isEmpty()
        || active()
        || wreck != null && !settling
        || core.organMove() != null
        || repairing()) return false;
    var order = BioshipAnatomy.deploymentOrder(core, plan);
    if (order.length != plan.size()) return false;
    var entries = new ListTag();
    for (var part : plan) {
      var entry = new CompoundTag();
      entry.putLong("Source", part.source().asLong());
      entry.putLong("Target", part.target().asLong());
      entry.put("State", NbtUtils.writeBlockState(part.state()));
      entry.put(
          "OriginalState", NbtUtils.writeBlockState(core.getLevel().getBlockState(part.source())));
      entries.add(entry);
    }
    repair.put("Parts", entries);
    repair.putIntArray("DeployOrder", order);
    repair.putString("Phase", "recall");
    core.setChanged();
    return true;
  }

  private void tickRepair(LivingCoreBlockEntity core) {
    if (core.body().dead()) return;
    var parts = repair.getList("Parts", 10);
    int index = repair.getInt("Cursor");
    if (repair.getString("Phase").equals("recall")) {
      if (index >= parts.size()) {
        repair.putString("Phase", "deploy");
        repair.putInt("Cursor", 0);
        core.setChanged();
        return;
      }
      final int slot = index;
      if (stock.stream()
          .anyMatch(t -> t.contains("RepairSlot") && t.getInt("RepairSlot") == slot)) {
        repair.putInt("Cursor", index + 1);
        core.setChanged();
        return;
      }
      var source = BlockPos.of(parts.getCompound(index).getLong("Source"));
      var expected =
          NbtUtils.readBlockState(
              core.getLevel()
                  .registryAccess()
                  .lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
              parts.getCompound(index).getCompound("OriginalState"));
      if (core.getLevel().getBlockState(source).getBlock() != expected.getBlock())
        return; // Interrupted/missing cargo: never clone the saved state.
      if (ComponentTransport.storeComponent(core.amoebaComponents(), source)) {
        var cargo =
            SubLevelContainer.getContainer(core.getLevel()).getSubLevel(core.organMove().cargo());
        ((ServerSubLevel) cargo).getUserDataTag().putInt("RepairSlot", index);
        core.setChanged();
      }
      return;
    }
    if (index >= parts.size()) {
      repair = new CompoundTag();
      DigestiveSystem.connect(core, core.ship());
      BioshipAnatomy.invalidate(core);
      core.setChanged();
      return;
    }
    var order = repair.getIntArray("DeployOrder");
    final int slot = order.length == parts.size() ? order[index] : index;
    var part = parts.getCompound(slot);
    var saved =
        stock.stream()
            .filter(t -> t.contains("RepairSlot") && t.getInt("RepairSlot") == slot)
            .findFirst()
            .orElse(null);
    var target = BlockPos.of(part.getLong("Target"));
    var state =
        NbtUtils.readBlockState(
            core.getLevel()
                .registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
            part.getCompound("State"));
    if (saved == null) {
      if (core.getLevel().getBlockState(target).equals(state)) {
        repair.putInt("Cursor", index + 1);
        core.setChanged();
      }
      return;
    }
    if (ComponentTransport.deployStored(
        core.amoebaComponents(), target, state, saved.getCompound("Entity"))) {
      var cargo =
          (ServerSubLevel)
              SubLevelContainer.getContainer(core.getLevel()).getSubLevel(core.organMove().cargo());
      cargo.getUserDataTag().putInt("RepairSlot", slot);
      stock.remove(saved);
      BioshipHealthChanges.changed(core);
      core.setChanged();
    }
  }

  private void settle(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() < retry || core.body().dead()) return;
    retry = core.getLevel().getGameTime() + 10;
    deploymentBlocked = false;
    stock.sort(
        Comparator.comparingInt(
            t -> BioshipPriorities.rank(core, state(core, t), core.getBlockPos())));
    for (var tag : List.copyOf(stock)) {
      var state = state(core, tag);
      if (BioshipLarynx.isLarynx(state)
          && ComponentMerging.absorbLarynx(core.amoebaComponents(), tag.getCompound("Entity"))) {
        take(core, tag);
        core.setChanged();
        continue;
      }
      boolean keep =
          !tag.getBoolean("Wreck") && !tag.getBoolean("Recycle")
              || BioshipScavenging.keepComponent(core, state, null);
      boolean emergency =
          BioshipNutrition.critical(core)
              && core.hasMouth()
              && DigestiveSystem.stomach(core) != null;
      // Never discard a missing digestive organ, or contents that cannot yet be transferred.
      if (!keep && !tag.contains("Entity")) {
        core.scavenging().recycle(state);
        take(core, tag);
        core.scavenging().deliver(core);
        core.setChanged();
        continue;
      }
      BlockPos port = null;
      if (state.hasBlockEntity()
          && tag.contains("Entity")
          && tag.getCompound("Entity").getString("id").contains("storage_sac")
          && DigestiveSystem.stomach(core) != null)
        port = DigestiveSystem.storagePort(core, core.getBlockPos());
      else if (state.is(Create_bioships.FEEDING_MOUTH.get())
          || net.minecraft.core.registries.BuiltInRegistries.BLOCK
              .getKey(state.getBlock())
              .toString()
              .equals("biomancy:bio_forge")) port = HatchlingFront.port(core, null);
      if (port != null && !DigestiveSystem.permitsPlacement(core, port)) port = null;
      if (port == null) {
        var candidates = new HashSet<BlockPos>();
        for (var cell : BioshipDecay.blocks(core.ship()))
          for (var face : Direction.values()) candidates.add(cell.relative(face));
        var home = core.structuralOrigin();
        for (var p :
            candidates.stream()
                .sorted(
                    Comparator.<BlockPos>comparingDouble(
                        p -> Math.abs(p.getY() - home.getY()) * 8 + p.distSqr(home)))
                .toList()) {
          if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(p))
              || !core.getLevel().getBlockState(p).isAir()
              || !DigestiveSystem.permitsPlacement(core, p)) continue;
          var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p));
          if (BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(world))
              && BioshipSupport.safeAddition(core, p, state)) {
            port = p;
            break;
          }
        }
      }
      if (port == null) continue;
      var host = core.getLevel().getBlockState(port);
      if (!host.isAir() && !BioshipTissue.isTissue(host)) continue;
      if (!BioshipSupport.safeAddition(core, port, state)) continue;
      if (!DigestiveSystem.reserveOrganRoute(core, core.getBlockPos(), port)) continue;
      if (!core.getLevel().setBlock(port, state, 3)) continue;
      var entity = core.getLevel().getBlockEntity(port);
      if (entity != null && tag.contains("Entity")) {
        var data = tag.getCompound("Entity").copy();
        data.putInt("x", port.getX());
        data.putInt("y", port.getY());
        data.putInt("z", port.getZ());
        entity.loadWithComponents(data, core.getLevel().registryAccess());
        BioshipTissue.remember(entity, host);
        entity.setChanged();
      }
      take(core, tag);
      if (tag.getBoolean("Wreck") || tag.getBoolean("Recycle"))
        core.scavenging()
            .finish(
                core,
                port,
                BioshipTissue.isTissue(host),
                tag.getBoolean("Recycle") || emergency && !keep);
      else BioshipIntegration.finishAssimilation(core, port);
      DigestiveSystem.connect(core, core.ship());
      core.coreMotion().layoutChanged(core);
      core.setChanged();
      return;
    }
    deploymentBlocked = !stock.isEmpty();
  }

  private void take(LivingCoreBlockEntity core, CompoundTag tag) {
    BioshipHealthChanges.changed(core);
    int count = Math.max(1, tag.getInt("Count"));
    if (count == 1) stock.remove(tag);
    else tag.putInt("Count", count - 1);
  }

  void abandon(LivingCoreBlockEntity core) {
    for (var move : List.copyOf(arms)) {
      core.organMove(move);
      ComponentTransport.abandon(core.amoebaComponents());
    }
    arms.clear();
    var at = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    for (var tag : stock) {
      var item = state(core, tag).getBlock().asItem();
      int count = Math.max(1, tag.getInt("Count"));
      while (count > 0) {
        var stack = new net.minecraft.world.item.ItemStack(item, Math.min(count, 64));
        count -= Math.min(count, 64);
        if (tag.contains("Entity"))
          stack.set(
              net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
              net.minecraft.world.item.component.CustomData.of(tag.getCompound("Entity").copy()));
        if (!stack.isEmpty())
          core.getLevel()
              .addFreshEntity(
                  new net.minecraft.world.entity.item.ItemEntity(
                      core.getLevel(), at.x, at.y, at.z, stack));
      }
    }
    stock.clear();
    repair = new CompoundTag();
    wreck = null;
    settling = false;
    core.setChanged();
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    var cargo = new ListTag();
    for (var arm : arms) cargo.add(arm.save());
    tag.put("Arms", cargo);
    var bindings = new CompoundTag();
    grasperBindings.forEach((id, slot) -> bindings.putString(id.toString(), slot));
    tag.put("Graspers", bindings);
    var items = new ListTag();
    stock.forEach(t -> items.add(t.copy()));
    tag.put("Stock", items);
    if (wreck != null) tag.putUUID("Wreck", wreck);
    tag.putBoolean("Settling", settling);
    tag.putBoolean("Resume", resumeWreck);
    tag.putBoolean("Feeding", feedingJob);
    tag.put("LayoutRepair", repair.copy());
    return tag;
  }

  void load(CompoundTag tag) {
    arms.clear();
    grasperBindings.clear();
    stock.clear();
    var bindings = tag.getCompound("Graspers");
    for (var id : bindings.getAllKeys()) {
      try {
        grasperBindings.put(UUID.fromString(id), bindings.getString(id));
      } catch (IllegalArgumentException ignored) {
      }
    }
    var cargo = tag.getList("Arms", 10);
    for (int i = 0; i < Math.min(32, cargo.size()); i++) {
      var m = ComponentTransport.Move.load(cargo.getCompound(i));
      if (m != null) arms.add(m);
    }
    var items = tag.getList("Stock", 10);
    for (int i = 0; i < Math.min(12000, items.size()); i++) stock.add(items.getCompound(i).copy());
    wreck = tag.hasUUID("Wreck") ? tag.getUUID("Wreck") : null;
    settling = tag.getBoolean("Settling");
    resumeWreck = tag.getBoolean("Resume");
    feedingJob = tag.getBoolean("Feeding");
    repair = tag.getCompound("LayoutRepair").copy();
  }
}
