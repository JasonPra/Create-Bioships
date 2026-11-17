package org.Azkerash.create_bioships.amoeba.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.AmoebaLegBlock;
import org.Azkerash.create_bioships.BioshipAccess;
import org.Azkerash.create_bioships.BioshipContainerState;
import org.Azkerash.create_bioships.BioshipDecay;
import org.Azkerash.create_bioships.BioshipDigestiveGrowth;
import org.Azkerash.create_bioships.BioshipHealthChanges;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.Azkerash.create_bioships.BioshipLarynx;
import org.Azkerash.create_bioships.BioshipLegEvolution;
import org.Azkerash.create_bioships.BioshipStorage;
import org.Azkerash.create_bioships.BioshipStructure;
import org.Azkerash.create_bioships.BioshipSupport;
import org.Azkerash.create_bioships.BioshipTissue;
import org.Azkerash.create_bioships.DigestiveSystem;
import org.Azkerash.create_bioships.FeedingMouthBlock;
import org.Azkerash.create_bioships.FeedingMouthBlockEntity;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost;
import org.Azkerash.create_bioships.amoeba.merging.ComponentMerging;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.resources.ResourceAccount;
import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Bridges the current amoeba core's policies to component mechanics; owns no cargo. */
public final class AmoebaCoreAdapter implements AmoebaComponentHost {
  private final LivingCoreBlockEntity core;

  public AmoebaCoreAdapter(LivingCoreBlockEntity core) {
    this.core = core;
  }

  public Level level() {
    return core.getLevel();
  }

  public ServerSubLevel body() {
    return core.ship();
  }

  public BlockPos controllerPosition() {
    return core.getBlockPos();
  }

  public ResourceAccount resources() {
    return BioshipStorage.account(core);
  }

  public ComponentTransport.Move movement() {
    return core.organMove();
  }

  public void movement(ComponentTransport.Move move) {
    core.organMove(move);
  }

  public boolean alive() {
    return !core.body().dead();
  }

  public List<ComponentTransport.Move> absorptions() {
    return core.assimilation().arms();
  }

  public BlockState hostTissue(BlockEntity entity) {
    return BioshipTissue.host(entity);
  }

  public boolean absorptionActive() {
    return core.assimilation().active();
  }

  public Prepared prepare(Request request) {
    var source = request.source();
    var target = request.target();
    boolean external = request.external(),
        manual = request.manual(),
        consuming = request.consuming(),
        stash = request.stash();
    var mergeRecipe = request.mergeRecipe();
    boolean combining = mergeRecipe != null;
    var ship = core.ship();
    var level = ship.getLevel();
    if (!stash && !consuming && !DigestiveSystem.permitsPlacement(core, target)) return null;
    var host = level.getBlockState(target);
    if (!stash && !host.isAir() && !BioshipTissue.isTissue(host) && !combining) return null;
    if (combining
        && !ComponentMerging.matches(
            mergeRecipe,
            BioshipContainerState.entity(level, ship, source),
            BioshipContainerState.entity(level, ship, target))) return null;
    var state = level.getBlockState(source);
    if (state.isAir() || state.is(Create_bioships.CORE.get())) return null;
    if (!stash && !consuming && BioshipLegEvolution.leg(state)) {
      var mounts = new java.util.LinkedHashMap<>(BioshipStructure.cells(ship));
      if (!external) mounts.remove(source);
      var oriented = state.setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(mounts, target));
      if (!AmoebaLegBlock.mounted(mounts, target, oriented)) return null;
    }
    var sourceShip = BioshipIntegration.containing(level, source);
    boolean scavenged =
        external && sourceShip != null && sourceShip != ship && BioshipDecay.dead(sourceShip);
    if (external && sourceShip != null && sourceShip != ship && !scavenged) return null;
    Vec3 sourceWorld =
        sourceShip != null
            ? sourceShip.logicalPose().transformPosition(Vec3.atCenterOf(source))
            : Vec3.atCenterOf(source);
    Vec3 destination = ship.logicalPose().transformPosition(Vec3.atCenterOf(target));
    var destinationCell = BlockPos.containing(destination);
    boolean vacatedSource = external && sourceShip == null && destinationCell.equals(source);
    if (!stash
        && !combining
        && !vacatedSource
        && !BioshipIntegration.growthSpace(level, destinationCell)) return null;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    boolean mouth =
        state.is(Create_bioships.FEEDING_MOUTH.get())
            || id.equals("biomancy:bio_forge") && !core.hasMouth();
    boolean stomach =
        state.is(Create_bioships.STOMACH.get())
            || id.equals("biomancy:decomposer") && DigestiveSystem.stomach(core) == null;
    var entity = BioshipContainerState.entity(level, sourceShip, source);
    var sourceHost = BioshipTissue.host(entity);
    boolean restore = !sourceHost.isAir();
    if (!stash
        && !consuming
        && !external
        && !manual
        && !(combining
            ? BioshipSupport.safeMerge(
                core, source, target, restore, ComponentMerging.result(mergeRecipe))
            : BioshipSupport.safeMove(core, source, target, restore))) return null;
    List<BlockPos> tissuePath =
        external ? List.of() : ComponentTransport.findTissuePath(this, source, target);
    if (!external)
      for (var player : level.players())
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu
            && menu.getContainer() == entity) return null;
    if (!stash
        && !consuming
        && (!external || entity != null && NativeStorage.isStorage(entity))
        && !DigestiveSystem.reserveOrganRoute(core, source, target)) return null;
    BioshipIntegration.clearGrowthPlant(level, BlockPos.containing(destination));
    var capsule = BioshipContainerState.capture(level, entity);
    var data = new CompoundTag();
    data.putUUID("BioshipCargoOwner", ship.getUniqueId());
    data.putBoolean("BioshipBound", true);
    data.putBoolean("BioshipStash", stash);
    if (combining) ComponentMerging.tag(data, mergeRecipe);
    data.putBoolean("BioshipCoreReclamation", stash && consuming);
    data.putBoolean("BioshipScavenged", scavenged);
    data.putBoolean("ScavengeHostFlesh", BioshipTissue.isTissue(host));
    var selected = core.brain().lastDecision();
    data.putBoolean(
        "BioshipEmergencyRecycle",
        consuming
            || external
                && selected != null
                && selected.recycle()
                && source.equals(selected.block()));

    return new Prepared(
        sourceShip, sourceWorld, sourceHost, capsule, mouth, stomach, tissuePath, data);
  }

  public void departed(Request request, Prepared prepared) {
    if (!request.external()) {
      var local = request.source().subtract(core.getBlockPos());
      core.body().intendedCells().remove(local);
      if (!prepared.sourceHost().isAir())
        core.body().intendedCells().put(local, level().getBlockState(request.source()));
    }
  }

  public void forgetSource(BlockPos source) {
    core.vision().forget(core, source);
  }

  public boolean owns(BlockPos pos) {
    return BioshipIntegration.containing(level(), pos) == body();
  }

  public boolean travelTissue(BlockState state) {
    return BioshipTissue.isTissue(state)
        || state.is(Create_bioships.CARAPACE.get())
        || state.is(Create_bioships.INTESTINE.get())
        || state.is(Create_bioships.ONE_WAY_WINDOW.get());
  }

  public boolean leg(BlockState state) {
    return BioshipLegEvolution.leg(state);
  }

  public Direction legFacing(BlockPos target) {
    return AmoebaLegBlock.outward(core, target);
  }

  public Direction mouthFacing(BlockPos target) {
    return FeedingMouthBlock.outward(core, target);
  }

  public boolean validAttachment(BlockState state, BlockPos target) {
    if (!leg(state)) return true;
    var mounts = BioshipStructure.cells(body());
    var oriented = state.setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(mounts, target));
    return AmoebaLegBlock.mounted(mounts, target, oriented);
  }

  public BlockEntity component(ServerSubLevel owner, BlockPos pos) {
    return BioshipContainerState.entity(level(), owner, pos);
  }

  public List<BlockEntity> components() {
    return BioshipIntegration.blockEntities(body());
  }

  public CompoundTag capture(BlockEntity entity) {
    return BioshipContainerState.capture(level(), entity);
  }

  public void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule) {
    BioshipContainerState.restore(level(), owner, pos, capsule);
  }

  public void rememberHost(BlockEntity entity, BlockState host) {
    BioshipTissue.remember(entity, host);
  }

  public void changed() {
    BioshipHealthChanges.changed(core);
  }

  public void invalidateVision() {
    core.vision().invalidate();
  }

  public void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim) {
    if (reclaim) core.scavenging().recycleToCore(core, level().getBlockState(pos));
    else core.assimilation().receive(core, cargo, pos);
  }

  public void placed(
      ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh) {
    if (scavenged || recycle) core.scavenging().finish(core, move.target(), hostFlesh, recycle);
    else if (move.external()) BioshipIntegration.finishAssimilation(core, move.target());
    merged();
    BioshipAccess.tick(core);
  }

  public void merged() {
    DigestiveSystem.connect(core, body());
    core.coreMotion().layoutChanged(core);
  }

  public boolean needsStomach() {
    return DigestiveSystem.stomachs(core).size() < BioshipDigestiveGrowth.target(core);
  }

  public boolean digestionReady() {
    return BioshipLarynx.ready(core);
  }

  public List<FeedingMouthBlockEntity> mouths() {
    return BioshipLarynx.mouths(core);
  }

  public void stomachFormed() {
    core.adaptation().salvageStarterLegs();
    core.setChanged();
  }
}
