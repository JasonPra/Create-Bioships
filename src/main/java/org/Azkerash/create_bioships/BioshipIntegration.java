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
    if (!tag.getBoolean(BOUND) || !tag.hasUUID("BioshipLineage")) {
      tag.putBoolean(BOUND, true);
      if (!tag.hasUUID("BioshipLineage")) tag.putUUID("BioshipLineage", ship.getUniqueId());
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
    return BioshipStructure.count(ship);
  }

  static boolean knownReachableSource(LivingCoreBlockEntity core, BlockPos source) {
    var level = core.getLevel();
    if (level == null
        || !BioshipVision.openToNature(level, source)
        || !BioshipReach.clear(core, Vec3.atCenterOf(source), true)) return false;
    var known = core.vision().sourceState(core, source);
    var actual = level.getBlockState(source);
    // A clear tentacle approach can verify a remembered component by contact.
    if (known == null || !known.is(actual.getBlock()) || !BioshipPriorities.supply(actual)) {
      core.vision().forget(core, source);
      return false;
    }
    return true;
  }

  public static void assimilate(LivingCoreBlockEntity core, ServerSubLevel ship) {
    if (!BioshipBodyBehavior.blocker(core).isEmpty()) return;
    if (core.brain().allowsConfiguration(core)) BioshipConfiguration.execute(core);
    collect(core);
  }

  /** External collection follows the action tree; owned organ conversion follows the body tree. */
  static void collect(LivingCoreBlockEntity core) {
    if (!BioshipBodyBehavior.blocker(core).isEmpty()) return;
    core.assimilation().start(core, core.brain().choose(core));
  }

  public static boolean exposedAndVisible(ServerLevel level, BlockPos eye, BlockPos source) {
    if (!BioshipVision.openToNature(level, source)) return false;
    Vec3 from = Vec3.atCenterOf(eye), to = Vec3.atCenterOf(source);
    int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 5));
    for (int i = 1; i < samples; i++) {
      BlockPos point = BlockPos.containing(from.lerp(to, (double) i / samples));
      if (point.equals(source)) continue;
      var state = level.getBlockState(point);
      if (!BioshipVision.clearMaterial(state, to.subtract(from))
          && !state.getCollisionShape(level, point).isEmpty()) return false;
    }
    return true;
  }

  static boolean growthSpace(Level level, BlockPos pos) {
    var state = level.getBlockState(pos);
    return state.isAir() || state.canBeReplaced() && level.getFluidState(pos).isEmpty();
  }

  /** Organic growth can displace water, but never lava or solid terrain. */
  static boolean aquaticGrowthSpace(Level level, BlockPos pos) {
    return growthSpace(level, pos)
        || level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
            && level.getBlockState(pos).canBeReplaced();
  }

  static void clearGrowthPlant(Level level, BlockPos pos) {
    if (!level.getBlockState(pos).isAir() && growthSpace(level, pos))
      level.destroyBlock(pos, false);
  }

  public static void finishAssimilation(LivingCoreBlockEntity core, BlockPos target) {
    if (BuiltInRegistries.BLOCK
        .getKey(core.getLevel().getBlockState(target).getBlock())
        .toString()
        .equals("biomancy:modular_larynx")) core.adoptLarynx();
    if (AirshipPrototype.available()
        && core.getLevel().getBlockState(target).is(FLESH)
        && core.missingBiomass() > 0) {
      core.getLevel().removeBlock(target, false);
      core.incorporateBiomass(1);
      return;
    }
    ComponentMerging.tryAvailable(core);
    convertFirstBioForge(core, target);
  }

  static void convertFirstBioForge(LivingCoreBlockEntity core, BlockPos pos) {
    if (DigestiveSystem.mouths(core).size() >= BioshipDigestiveGrowth.target(core)
        || !(core.getLevel().getBlockEntity(pos)
            instanceof com.github.elenterius.biomancy.block.bioforge.BioForgeBlockEntity forge))
      return;
    var host = BioshipTissue.host(forge);
    var stock = forge.getStackInFuelSlot().copy();
    int biomass = forge.getStateData().fuelHandler.getFuelAmount();
    forge.setStackInFuelSlot(net.minecraft.world.item.ItemStack.EMPTY);
    forge.getStateData().fuelHandler.setFuelAmount(0);
    core.getLevel()
        .setBlock(
            pos,
            Create_bioships.FEEDING_MOUTH
                .get()
                .defaultBlockState()
                .setValue(FeedingMouthBlock.FACING, FeedingMouthBlock.outward(core, pos)),
            3);
    if (core.getLevel().getBlockEntity(pos) instanceof FeedingMouthBlockEntity mouth) {
      BioshipTissue.remember(mouth, host);
      mouth.embeddedInHull(!host.isAir());
      mouth.inheritBioForge(stock, biomass);
    }
  }

  static void orientMouth(LivingCoreBlockEntity core) {
    if (core.hatchling()) {
      HatchlingFront.arrangeMouth(core);
      return;
    }
    var pos = core.mouthBlock();
    if (pos == null || ComponentTransport.busy(core)) return;
    var state = core.getLevel().getBlockState(pos);
    if (!state.is(Create_bioships.FEEDING_MOUTH.get())) return;
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null && entity.getPersistentData().getBoolean("BioshipManualPlacement")) return;
    var facing = state.getValue(FeedingMouthBlock.FACING);
    if (!FeedingMouthBlock.openFace(core, pos, facing)
        || !core.isMature() && facing == Direction.DOWN)
      core.getLevel()
          .setBlock(
              pos,
              state.setValue(FeedingMouthBlock.FACING, FeedingMouthBlock.outward(core, pos)),
              3);
  }

  public static void relocateMouth(LivingCoreBlockEntity core, ServerSubLevel ship) {
    if (ComponentTransport.busy(core)) return;
    boolean hunting = core.huntingAllowed();
    var destinations = new ArrayList<BlockPos>();
    if (hunting)
      destinations.addAll(
          List.of(
              DinghyGrowth.MOUTH,
              new BlockPos(0, 0, 7),
              new BlockPos(1, 0, -5),
              new BlockPos(-1, 0, 5),
              new BlockPos(0, 2, -7)));
    int side = 3 + core.engineRoom().widening();
    for (int z : new int[] {3, 2, 4}) {
      destinations.add(new BlockPos(side, 2, z));
      destinations.add(new BlockPos(-side, 2, z));
    }
    if (!hunting && core.preferredMouth() != null) destinations.add(0, core.preferredMouth());
    var level = ship.getLevel();
    boolean hasHuntingMouth =
        hunting
            && destinations.subList(0, 5).stream()
                .anyMatch(offset -> isMaw(level.getBlockState(core.layoutPos(offset))));
    for (var source : DigestiveSystem.mouths(core)) {
      if (!(level.getBlockEntity(source) instanceof FeedingMouthBlockEntity mouth)
          || mouth.getPersistentData().getBoolean("BioshipManualPlacement")
          || (!(hunting && !hasHuntingMouth && source.equals(core.mouthBlock()))
              && destinations.stream().anyMatch(offset -> core.layoutPos(offset).equals(source))))
        continue;
      for (var offset : destinations) {
        var target = core.layoutPos(offset);
        if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(target))
            || !level.isInWorldBounds(target)) continue;
        var host = level.getBlockState(target);
        if (!host.isAir() && !BioshipTissue.isTissue(host)) continue;
        var physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(target));
        if (!BioshipIntegration.growthSpace(level, BlockPos.containing(physical))) continue;
        if (Math.abs(offset.getX()) == side) {
          var inside = target.relative(offset.getX() > 0 ? Direction.WEST : Direction.EAST);
          var tissue = level.getBlockState(inside);
          if ((!tissue.isAir() && !tissue.is(Create_bioships.INTESTINE.get()))
              || !level.getBlockState(inside.above()).isAir()) continue;
        }
        if (ComponentTransport.begin(core, source, target, false)) return;
      }
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
    if (event.getItemStack().isEmpty()
        || event.getItemStack().is(Create_bioships.BIOSHIP_WRENCH.get())
        || event.getLevel().isClientSide
        || !isMaw(event.getLevel().getBlockState(event.getPos()))) return;
    var mouthState = event.getLevel().getBlockState(event.getPos());
    if (mouthState.is(Create_bioships.FEEDING_MOUTH.get())
        && event.getHitVec().getDirection() != mouthState.getValue(FeedingMouthBlock.FACING))
      return;
    var ship = containing(event.getLevel(), event.getPos());
    if (ship == null) return;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.primary(ship)) {
        if (core.feedAtMouth(event.getPos(), event.getEntity(), event.getItemStack())) {
          event.setCanceled(true);
          event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
        return;
      }
  }
}
