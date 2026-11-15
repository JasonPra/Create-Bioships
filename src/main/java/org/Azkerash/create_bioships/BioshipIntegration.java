package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** World mutation happens in ordinary block ticks, outside Sable's actor iteration. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipIntegration {
  private static final String BOUND = "BioshipBound";
  private static final TagKey<net.minecraft.world.level.block.Block> MACHINES =
      TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "assimilable_machines"));
  private static final TagKey<net.minecraft.world.level.block.Block> FLESH =
      TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "construction_flesh"));
  private static final TagKey<Item> MATERIAL =
      TagKey.create(
          net.minecraft.core.registries.Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "construction_biomass"));

  public static TagKey<Item> constructionMaterial() {
    return MATERIAL;
  }

  public static void bind(ServerSubLevel ship) {
    var tag = ship.getUserDataTag();
    if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
    if (!tag.getBoolean(BOUND)) {
      tag.putBoolean(BOUND, true);
      ship.setUserDataTag(tag);
    }
  }

  public static boolean bound(SubLevel ship) {
    if (!(ship instanceof ServerSubLevel server)) return false;
    if (server.getUserDataTag() != null && server.getUserDataTag().getBoolean(BOUND)) return true;
    for (var actor : server.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity) return true;
    return false;
  }

  public static ServerSubLevel containing(Level level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) return null;
    var plot = container.getPlot(new net.minecraft.world.level.ChunkPos(pos));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel server ? server : null;
  }

  public static void awaken(ServerLevel level, BlockPos pos) {
    if (!level.getBlockState(pos).is(Create_bioships.CORE.get()) || containing(level, pos) != null)
      return;
    var ship =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, List.of(pos), new BoundingBox3i(pos, pos));
    if (ship != null) bind(ship);
  }

  public static boolean isMaw(BlockState state) {
    return state.is(Create_bioships.FEEDING_MOUTH.get())
        || BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .equals(ResourceLocation.fromNamespaceAndPath("biomancy", "maw_hopper"));
  }

  public static boolean isIntegratedMachine(Level level, BlockPos pos) {
    return BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(pos).getBlock())
            .getNamespace()
            .equals("biomancy")
        || level.getBlockState(pos).is(MACHINES)
        || level.getBlockState(pos).is(Create_bioships.INTESTINE.get());
  }

  public static List<net.minecraft.world.level.block.entity.BlockEntity> blockEntities(
      ServerSubLevel ship) {
    List<net.minecraft.world.level.block.entity.BlockEntity> result = new ArrayList<>();
    for (var chunk : ship.getPlot().getLoadedChunks())
      result.addAll(chunk.getChunk().getBlockEntities().values());
    return result;
  }

  public static int countBlocks(ServerSubLevel ship) {
    int count = 0;
    for (var chunk : ship.getPlot().getLoadedChunks())
      for (var section : chunk.getChunk().getSections()) {
        if (section.hasOnlyAir()) continue;
        for (int y = 0; y < 16; y++)
          for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) if (!section.getBlockState(x, y, z).isAir()) count++;
      }
    return count;
  }

  public static void assimilate(LivingCoreBlockEntity core, ServerSubLevel ship) {
    if (OrganMotion.busy(core)) return;
    ServerLevel level = ship.getLevel();
    // Bounded three-block neighborhood of core, then one-block neighborhood of each existing
    // organ/machine.
    Set<BlockPos> candidates = new LinkedHashSet<>();
    BlockPos physical =
        BlockPos.containing(
            ship.logicalPose().transformPosition(Vec3.atCenterOf(core.getBlockPos())));
    BlockPos.betweenClosed(physical.offset(-3, 0, -3), physical.offset(3, 3, 3))
        .forEach(p -> candidates.add(p.immutable()));
    for (var actor : blockEntities(ship)) {
      BlockPos point =
          BlockPos.containing(
              ship.logicalPose().transformPosition(Vec3.atCenterOf(actor.getBlockPos())));
      for (Direction direction : Direction.values()) candidates.add(point.relative(direction));
      convertFirstStomach(core, actor.getBlockPos());
      convertFirstBioForge(core, actor.getBlockPos());
    }
    if (BioshipScavenging.collect(core, ship)) return;
    int moved = 0;
    for (BlockPos source : candidates) {
      if (moved >= 16
          || !level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
          || containing(level, source) != null) continue;
      BlockState state = level.getBlockState(source);
      if (!exposedAndVisible(level, physical, source)) continue;
      if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("biomancy")
          || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
      BlockPos target =
          BlockPos.containing(ship.logicalPose().transformPositionInverse(Vec3.atCenterOf(source)));
      if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(target))
          || !level.isInWorldBounds(target)
          || !level.getBlockState(target).isAir()) continue;
      boolean attached = false;
      for (Direction direction : Direction.values())
        if (!level.getBlockState(target.relative(direction)).isAir()) attached = true;
      if (!attached) {
        target = null;
        for (Direction direction :
            new Direction[] {
              Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
            }) {
          BlockPos port = core.getBlockPos().relative(direction);
          if (level.getBlockState(port).isAir()) {
            target = port;
            break;
          }
        }
        if (target == null) continue;
      }
      if (OrganMotion.begin(core, source, target, true)) {
        moved++;
        break;
      }
    }
  }

  public static boolean exposedAndVisible(ServerLevel level, BlockPos eye, BlockPos source) {
    if (!level.canSeeSky(source.above())
        || !level.getBlockState(source.above()).getCollisionShape(level, source.above()).isEmpty())
      return false;
    Vec3 from = Vec3.atCenterOf(eye), to = Vec3.atCenterOf(source);
    int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 5));
    for (int i = 1; i < samples; i++) {
      BlockPos point = BlockPos.containing(from.lerp(to, (double) i / samples));
      if (point.equals(source)) continue;
      var state = level.getBlockState(point);
      if (!state.getCollisionShape(level, point).isEmpty()) return false;
    }
    return true;
  }

  public static void finishAssimilation(LivingCoreBlockEntity core, BlockPos target) {
    if (BuiltInRegistries.BLOCK
        .getKey(core.getLevel().getBlockState(target).getBlock())
        .toString()
        .equals("biomancy:modular_larynx")) core.adoptLarynx();
    if (core.getLevel().getBlockState(target).is(FLESH) && core.missingBiomass() > 0) {
      core.getLevel().removeBlock(target, false);
      core.incorporateBiomass(1);
      return;
    }
    convertFirstStomach(core, target);
    convertFirstBioForge(core, target);
  }

  private static void convertFirstStomach(LivingCoreBlockEntity core, BlockPos pos) {
    if (DigestiveSystem.stomach(core) != null || OrganMotion.busy(core)) return;
    com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity decomposer = null;
    com.github.elenterius.biomancy.block.digester.DigesterBlockEntity digester = null;
    for (var entity : blockEntities(core.ship())) {
      if (decomposer == null
          && entity
              instanceof com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity d)
        decomposer = d;
      if (digester == null
          && entity instanceof com.github.elenterius.biomancy.block.digester.DigesterBlockEntity d)
        digester = d;
    }
    if (decomposer == null || digester == null) return;
    var destination = decomposer.getBlockPos();
    var source = digester.getBlockPos();
    if (destination.distManhattan(source) != 1) {
      for (var direction : Direction.values()) {
        var adjacent = destination.relative(direction);
        var state = core.getLevel().getBlockState(adjacent);
        if ((state.isAir() || state.is(Create_bioships.FLESH.get()))
            && OrganMotion.begin(core, source, adjacent, false)) return;
      }
      return;
    }
    var queued = new ArrayList<net.minecraft.world.item.ItemStack>();
    var stores = new ArrayList<net.minecraft.world.item.ItemStack>();
    for (int i = 0; i < decomposer.getInputInventory().getSlots(); i++)
      queued.add(decomposer.getInputInventory().getStackInSlot(i).copy());
    for (int i = 0; i < digester.getInputInventory().getSlots(); i++)
      queued.add(digester.getInputInventory().getStackInSlot(i).copy());
    for (int i = 0; i < decomposer.getOutputInventory().getSlots(); i++)
      stores.add(decomposer.getOutputInventory().getStackInSlot(i).copy());
    for (int i = 0; i < digester.getOutputInventory().getSlots(); i++)
      stores.add(digester.getOutputInventory().getStackInSlot(i).copy());
    stores.add(decomposer.getStackInFuelSlot().copy());
    stores.add(digester.getStackInFuelSlot().copy());
    int nutrients =
        decomposer.getStateData().fuelHandler.getFuelAmount()
            + digester.getStateData().fuelHandler.getFuelAmount();
    boolean flesh = digester.getPersistentData().getBoolean("BioshipHostFlesh");
    core.getLevel().removeBlockEntity(source);
    core.getLevel()
        .setBlock(
            source,
            flesh
                ? Create_bioships.FLESH.get().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
            18);
    core.getLevel().removeBlockEntity(destination);
    core.getLevel().setBlock(destination, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    if (core.getLevel().getBlockEntity(destination) instanceof StomachBlockEntity stomach) {
      for (var stack : stores) stomach.retain(stack);
      for (var stack : queued) stomach.inheritInput(stack);
      stomach.addNutrients(nutrients);
    }
  }

  private static void convertFirstBioForge(LivingCoreBlockEntity core, BlockPos pos) {
    if (core.hasMouth()
        || !(core.getLevel().getBlockEntity(pos)
            instanceof com.github.elenterius.biomancy.block.bioforge.BioForgeBlockEntity forge))
      return;
    var stock = forge.getStackInFuelSlot().copy();
    int biomass = forge.getStateData().fuelHandler.getFuelAmount();
    forge.setStackInFuelSlot(net.minecraft.world.item.ItemStack.EMPTY);
    forge.getStateData().fuelHandler.setFuelAmount(0);
    core.getLevel().setBlock(pos, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    if (core.getLevel().getBlockEntity(pos) instanceof FeedingMouthBlockEntity mouth)
      mouth.inheritBioForge(stock, biomass);
  }

  public static void relocateMouth(LivingCoreBlockEntity core, ServerSubLevel ship) {
    BlockPos source = core.mouthBlock();
    if (source == null
        || !(ship.getLevel().getBlockEntity(source) instanceof FeedingMouthBlockEntity mouth))
      return;
    boolean hunting = core.huntingAllowed();
    List<BlockPos> destinations =
        hunting
            ? List.of(
                DinghyGrowth.MOUTH,
                new BlockPos(0, 0, 7),
                new BlockPos(1, 0, -5),
                new BlockPos(-1, 0, 5),
                new BlockPos(0, 2, -7))
            : List.of(
                new BlockPos(3, 2, 3),
                new BlockPos(-3, 2, 3),
                new BlockPos(3, 2, 2),
                new BlockPos(-3, 2, 2));
    if (!hunting && core.preferredMouth() != null) {
      destinations = new ArrayList<>(destinations);
      destinations.add(0, core.preferredMouth());
    }
    ServerLevel level = ship.getLevel();
    for (BlockPos offset : destinations) {
      BlockPos target = core.layoutPos(offset);
      if (target.equals(source)) return;
      if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(target))
          || !level.isInWorldBounds(target)) continue;
      var host = level.getBlockState(target);
      if (!host.isAir() && !host.is(Create_bioships.FLESH.get())) continue;
      Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(target));
      if (!level.getBlockState(BlockPos.containing(physical)).isAir()) continue;
      if (!hunting) {
        BlockPos inside = target.relative(offset.getX() > 0 ? Direction.WEST : Direction.EAST);
        if (!level.getBlockState(inside).isAir() || !level.getBlockState(inside.above()).isAir())
          continue;
      }
      OrganMotion.begin(core, source, target, false);
      return;
    }
  }

  @SubscribeEvent
  public static void routeBioshipWrench(PlayerInteractEvent.RightClickBlock event) {
    if (event.getItemStack().is(Create_bioships.BIOSHIP_WRENCH.get())) {
      event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
      event.setUseItem(net.neoforged.neoforge.common.util.TriState.TRUE);
    }
  }

  @SubscribeEvent
  public static void feedMaw(PlayerInteractEvent.RightClickBlock event) {
    if (event.getLevel().isClientSide || !isMaw(event.getLevel().getBlockState(event.getPos())))
      return;
    var ship = containing(event.getLevel(), event.getPos());
    if (ship == null) return;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.primary(ship)) {
        if (core.feedAtMouth(event.getEntity(), event.getItemStack())) {
          event.setCanceled(true);
          event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
        return;
      }
  }
}
