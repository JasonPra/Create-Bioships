package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-readable real simulation snapshots, without a GUI panel or behavior overrides. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipDiagnostics {
  static List<LivingCoreBlockEntity> cores(ServerLevel level) {
    var result = new ArrayList<LivingCoreBlockEntity>();
    for (var entry : SubLevelContainer.getContainer(level).getAllSubLevels())
      if (entry instanceof ServerSubLevel ship && !ship.isRemoved())
        for (var actor : BioshipIntegration.blockEntities(ship))
          if (actor instanceof LivingCoreBlockEntity core) result.add(core);
    result.sort(Comparator.comparing(c -> c.ship().getUniqueId()));
    return result;
  }

  /** Opt-in call-site evidence; never changes death, ownership or simulation decisions. */
  static void decayMarked(ServerSubLevel ship) {
    if (!Boolean.getBoolean("bioships.traceLifecycle")) return;
    var callsite =
        StackWalker.getInstance()
            .walk(frames -> frames.skip(1).limit(10).map(Object::toString).toList());
    System.out.println(
        "BIOSHIP_LIFECYCLE event=mark_dead tick="
            + ship.getLevel().getGameTime()
            + " ship="
            + ship.getUniqueId()
            + " lineage="
            + BioshipDecay.lineage(ship)
            + " liveCores="
            + BioshipIntegration.blockEntities(ship).stream()
                .filter(e -> e instanceof LivingCoreBlockEntity core && !core.body().dead())
                .count()
            + " callsite="
            + callsite);
  }

  static void removalCheck(
      LivingCoreBlockEntity core, ServerSubLevel owner, net.minecraft.core.BlockPos pos) {
    if (!Boolean.getBoolean("bioships.traceLifecycle")) return;
    var holder =
        owner
            .getPlot()
            .getChunkHolder(owner.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(pos)));
    System.out.println(
        "BIOSHIP_LIFECYCLE event=validate_removal tick="
            + owner.getLevel().getGameTime()
            + " ship="
            + owner.getUniqueId()
            + " core="
            + pos.toShortString()
            + " receipt="
            + core.hasTransferReceipt()
            + " transferred="
            + core.transferred()
            + " ownerRemoved="
            + owner.isRemoved()
            + " coreRemoved="
            + core.isRemoved()
            + " globalState="
            + owner.getLevel().getBlockState(pos)
            + " plotState="
            + (holder == null ? "unloaded" : holder.getChunk().getBlockState(pos))
            + " plotBEisSource="
            + (holder != null && holder.getChunk().getBlockEntities().get(pos) == core)
            + " globalBEisSource="
            + (owner.getLevel().getBlockEntity(pos) == core));
  }

  static void death(LivingCoreBlockEntity core, BioshipDeathCause cause, long at) {
    var ship = core.ship();
    var food = core.foodLevel();
    System.out.println(
        "BIOSHIP_DEATH cause="
            + cause
            + " tick="
            + at
            + " ship="
            + (ship == null ? "removed" : ship.getUniqueId())
            + " core="
            + core.getBlockPos().toShortString()
            + " food="
            + food
            + " air="
            + core.aquatic().air()
            + " gills="
            + (ship == null ? "unknown" : BioshipRespiration.has(core, true))
            + " activeGills="
            + (ship != null && BioshipRespiration.activeGills(core))
            + " lungs="
            + (ship == null ? "unknown" : BioshipRespiration.has(core, false)));
  }

  static CompoundTag snapshot(LivingCoreBlockEntity core) {
    var data = new CompoundTag();
    var cells = BioshipStructure.cells(core.ship());
    data.putUUID("Ship", core.ship().getUniqueId());
    data.put("Position", VisionDebug.vector(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)));
    data.putString("LogicalCore", core.getBlockPos().toShortString());
    data.putString("LogicalMouths", DigestiveSystem.mouths(core).toString());
    data.putBoolean("ChunkLease", core.chunkLoading.active());
    data.putInt("ViewChunks", core.chunkLoading.viewDistance());
    data.putInt("SimulationChunks", core.chunkLoading.simulationDistance());
    data.putLong("GameTime", core.getLevel().getGameTime());
    data.putFloat("RequestedTickRate", core.getLevel().getServer().tickRateManager().tickrate());
    data.putString("Behavior", core.brain().action().name());
    data.put("BodyWork", core.adaptation().behavior().debug());
    data.putString("Reason", core.brain().reason());
    data.put("Navigation", core.crawl().navigationDebug(core.getLevel().getGameTime()));
    data.putDouble("Health", core.body().health());
    data.putInt("MaxHealth", core.body().capacity());
    data.putInt("BlastAdaptationPoints", core.survival().blastPoints());
    data.putBoolean("BodyDead", core.body().dead());
    data.putString("DeathCause", core.body().deathCause().name());
    data.putLong("DeathAt", core.body().deathAt());
    data.putBoolean("ShipDead", BioshipDecay.dead(core.ship()));
    data.putString("Lineage", BioshipDecay.lineage(core.ship()).toString());
    data.putInt(
        "Food", core.saveWithFullMetadata(core.getLevel().registryAccess()).getInt("Satiety"));
    data.put("Water", core.aquatic().save());
    data.putInt("Blocks", cells.size());
    data.putInt("FaceConnectedComponents", BodyTopology.components(cells.keySet()));
    data.putInt(
        "StructuralComponents", BodyTopology.components(BodyTopology.structuralCells(cells)));
    data.putInt(
        "Height",
        cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0)
            - cells.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0)
            + 1);
    data.putInt("CoreElevation", core.getBlockPos().getY() - core.structuralOrigin().getY());
    data.putInt("CoreCoverage", core.coreMotion().coverage(core, core.getBlockPos()));
    data.put("MassCenter", VisionDebug.vector(BioshipSupport.layout(core).center()));
    data.putBoolean("StableSupport", BioshipSupport.layout(core).stable());
    data.putInt(
        "InvalidLegMounts",
        (int)
            cells.entrySet().stream()
                .filter(
                    e ->
                        BioshipLegEvolution.leg(e.getValue())
                            && !AmoebaLegBlock.mounted(cells, e.getKey(), e.getValue()))
                .count());
    data.putInt("Stomachs", DigestiveSystem.stomachs(core).size());
    data.putInt(
        "ConnectedStomachs",
        (int)
            DigestiveSystem.stomachs(core).stream()
                .filter(s -> DigestiveSystem.connected(core, s))
                .count());
    data.put("CoreMove", core.coreMotion().save());
    data.putBoolean("CreeperPanic", core.autonomy().creeperPanic());
    var hazards = new net.minecraft.nbt.ListTag();
    var center = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    for (var creeper :
        core.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.monster.Creeper.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(32),
                e -> e.isAlive())) {
      var hazard = BioshipCreeperHazard.estimate(creeper);
      var row = new CompoundTag();
      row.putUUID("Entity", creeper.getUUID());
      row.put("Position", VisionDebug.vector(creeper.position()));
      row.putDouble("Distance", creeper.position().distanceTo(center));
      row.putDouble("DamageReach", hazard.damageReach());
      row.putInt("FuseRemaining", hazard.fuseRemaining());
      row.putBoolean("Primed", hazard.fuseRemaining() != Integer.MAX_VALUE);
      hazards.add(row);
    }
    data.put("CreeperHazards", hazards);
    data.putString("OrganMove", String.valueOf(core.organMove()));
    data.putString(
        "Cells",
        cells.entrySet().stream()
            .map(e -> e.getKey().subtract(core.structuralOrigin()) + "=" + e.getValue())
            .toList()
            .toString());
    return data;
  }

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("bioship_debug")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.literal("list")
                        .executes(
                            ctx -> {
                              var list = cores(ctx.getSource().getLevel());
                              for (var core : list)
                                ctx.getSource()
                                    .sendSuccess(
                                        () ->
                                            Component.literal(
                                                core.ship().getUniqueId()
                                                    + " "
                                                    + BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)
                                                    + " "
                                                    + core.brain().action()),
                                        false);
                              return list.size();
                            }))
                .then(
                    Commands.literal("inspect")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .executes(
                                    ctx -> {
                                      String prefix =
                                          StringArgumentType.getString(ctx, "id")
                                              .toLowerCase(Locale.ROOT);
                                      var matches =
                                          cores(ctx.getSource().getLevel()).stream()
                                              .filter(
                                                  c ->
                                                      c.ship()
                                                          .getUniqueId()
                                                          .toString()
                                                          .startsWith(prefix))
                                              .toList();
                                      if (matches.size() != 1) {
                                        ctx.getSource()
                                            .sendFailure(
                                                Component.literal(
                                                    "Use a unique UUID or prefix from"
                                                        + " /bioship_debug list."));
                                        return 0;
                                      }
                                      var value = snapshot(matches.getFirst()).toString();
                                      ctx.getSource()
                                          .sendSuccess(() -> Component.literal(value), false);
                                      return 1;
                                    }))));
  }
}
