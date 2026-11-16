package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wreck tissue is construction stock, never a meal. Fractions persist, preventing reload farming.
 */
public final class BioshipScavenging {
  private int fleshRemainder,
      boneRemainder,
      fiberRemainder,
      pendingFlesh,
      pendingBone,
      pendingFiber,
      pendingFreeTubes;

  static boolean frameMaterial(BlockState state) {
    return state.is(Create_bioships.CARAPACE.get())
        || state.is(Create_bioships.ARMOR_II.get())
        || state.is(Create_bioships.PERCH.get())
        || state.is(Create_bioships.DOOR.get())
        || state.getBlock() instanceof BlueprintPanelBlock
        || state.getBlock() instanceof AccessPanelBlock;
  }

  static boolean fiberMaterial(BlockState state) {
    return state.is(Create_bioships.AMOEBA_LEG.get())
        || state.is(Create_bioships.TENTACLE.get())
        || state.is(Create_bioships.INTESTINE.get())
        || state.getBlock() instanceof LivingThrottleBlock
        || state.getBlock() instanceof NerveControlBlock
        || BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .toString()
            .equals("simulated:steering_wheel");
  }

  static boolean keepComponent(LivingCoreBlockEntity core, BlockState state, BlockPos exclude) {
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    var others =
        BioshipIntegration.blockEntities(core.ship()).stream()
            .filter(e -> !e.getBlockPos().equals(exclude))
            .toList();
    if (state.is(Create_bioships.AMOEBA_LEG.get())) return BioshipLegGrowth.missing(core) > 0;
    if (state.is(Create_bioships.FEEDING_MOUTH.get())
        || id.equals("biomancy:bio_forge")
        || id.equals("biomancy:maw_hopper"))
      return others.stream().filter(e -> BioshipIntegration.isMaw(e.getBlockState())).count()
          < DigestiveSystem.requiredPairs(core);
    if (state.is(Create_bioships.STOMACH.get()))
      return others.stream().filter(e -> e instanceof StomachBlockEntity).count()
          < DigestiveSystem.requiredPairs(core);
    if (id.equals("biomancy:decomposer") || id.equals("biomancy:digester"))
      return others.stream().filter(e -> e instanceof StomachBlockEntity).count()
              < BioshipDigestiveGrowth.target(core)
          && others.stream().noneMatch(e -> e.getBlockState().is(state.getBlock()));
    if (id.equals("biomancy:storage_sac"))
      return others.stream().filter(EngineRoom::storage).count() < 8;
    if (id.equals("biomancy:modular_larynx"))
      return others.stream().filter(e -> e.getBlockState().is(state.getBlock())).count()
          < BioshipLarynx.missingCount(core);
    return false;
  }

  static boolean component(BlockState state) {
    return state.hasBlockEntity() && !state.is(Create_bioships.CORE.get());
  }

  static boolean emptyComponent(LivingCoreBlockEntity core, BlockPos pos) {
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null && BioshipStores.total(entity) > 0) return false;
    if (entity instanceof net.minecraft.world.Container c && !c.isEmpty()) return false;
    var inventory =
        core.getLevel()
            .getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
    if (inventory != null)
      for (int slot = 0; slot < inventory.getSlots(); slot++)
        if (!inventory.getStackInSlot(slot).isEmpty()) return false;
    return true;
  }

  static boolean needed(LivingCoreBlockEntity core, BlockState state) {
    if (state.isAir() || state.is(Create_bioships.CORE.get())) return false;
    if (state.is(Create_bioships.INTESTINE.get()) && !state.getValue(IntestineBlock.PAID)) {
      var storage = DigestiveSystem.stomach(core);
      return storage == null ? core.scavenging().pendingFreeTubes < 16 : storage.freeTubeStock < 16;
    }
    if (keepComponent(core, state, null)) return true;
    var stomach = DigestiveSystem.stomach(core);

    boolean flesh =
        core.hungry()
            || AirshipPrototype.available() && core.missingBiomass() > 0
            || (stomach == null
                ? core.adaptation().material(MaterialCost.FLESH) < 128
                : stomach.material(MaterialCost.FLESH) < 128);
    boolean bone =
        frameMaterial(state)
            && (stomach == null
                ? core.scavenging().pendingBone < 8
                : stomach.material("biomancy:bone_fragments") < 32);
    boolean fiber =
        fiberMaterial(state)
            && (stomach == null
                ? core.scavenging().pendingFiber < 12
                : stomach.material("biomancy:elastic_fibers") < 32);
    return flesh || bone || fiber;
  }

  record Source(BlockPos pos, Vec3 point, BlockState state) {}

  static java.util.List<Source> visibleSources(LivingCoreBlockEntity core) {
    var committed = core.assimilation().remaining(core);
    if (!committed.isEmpty()) return committed;
    var container = SubLevelContainer.getContainer(core.getLevel());
    if (container == null) return java.util.List.of();
    var eye = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var sources = new ArrayList<Source>();
    for (var body : container.getAllSubLevels())
      if (body instanceof ServerSubLevel wreck && !wreck.isRemoved() && BioshipDecay.dead(wreck)) {
        if (wreck
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(wreck.getPlot().getCenterBlock()))
                .distanceToSqr(eye)
            > 40 * 40) continue;
        for (var pos : BioshipDecay.blocks(wreck)) {
          var state = core.getLevel().getBlockState(pos);
          if (!needed(core, state)) continue;
          var point = wreck.logicalPose().transformPosition(Vec3.atCenterOf(pos));
          if (point.distanceToSqr(eye) <= 32 * 32 && visible(core, wreck, pos, eye, point))
            sources.add(new Source(pos, point, state));
        }
      }
    return sources;
  }

  static boolean collect(LivingCoreBlockEntity core, ServerSubLevel own) {
    core.scavenging().deliver(core);
    var decision = core.brain().choose(core);
    return decision.action() == BioshipBrain.Action.SCAVENGE
        && core.assimilation().start(core, decision);
  }

  static boolean physicallyReachable(
      LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 point) {
    var from = BioshipReach.anchor(core, point);
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(point) * 5));
    for (int i = 1; i < steps; i++) {
      var local =
          BlockPos.containing(
              wreck.logicalPose().transformPositionInverse(from.lerp(point, i / (double) steps)));
      if (!local.equals(source)
          && !core.getLevel()
              .getBlockState(local)
              .getCollisionShape(core.getLevel(), local)
              .isEmpty()) return false;
    }
    return true;
  }

  private static boolean visible(
      LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 eye, Vec3 point) {
    var physical = BlockPos.containing(point);
    var level = core.getLevel();
    if (!BioshipVision.openToNature(level, physical)) return false;
    for (var view : core.vision().views(core)) {
      if (!core.vision().inView(core, view, point)) continue;
      var from = view.origin();
      if (!BioshipVision.ray(core, from, point)) continue;
      boolean clear = true;
      int steps = Math.max(1, (int) Math.ceil(from.distanceTo(point) * 5));
      for (int i = 1; i < steps; i++) {
        var sample =
            BlockPos.containing(
                wreck.logicalPose().transformPositionInverse(from.lerp(point, i / (double) steps)));
        if (!sample.equals(source)
            && !BioshipVision.clearMaterial(
                level.getBlockState(sample),
                wreck
                    .logicalPose()
                    .transformPositionInverse(point)
                    .subtract(wreck.logicalPose().transformPositionInverse(from)))
            && !level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) {
          clear = false;
          break;
        }
      }
      if (clear) return true;
    }
    return false;
  }

  void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost) {
    finish(core, target, restoreHost, false);
  }

  void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost, boolean forced) {
    var state = core.getLevel().getBlockState(target);
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    boolean essential =
        keepComponent(core, state, target)
            && (BioshipIntegration.isMaw(state)
                || state.is(Create_bioships.STOMACH.get())
                || id.equals("biomancy:bio_forge")
                || id.equals("biomancy:decomposer")
                || id.equals("biomancy:digester"));
    if (keepComponent(core, state, target) && (!forced || essential)) {
      BioshipIntegration.finishAssimilation(core, target);
      return;
    }
    var hostState = BioshipTissue.host(core.getLevel().getBlockEntity(target));
    recycle(state);
    var storage =
        BioshipIntegration.blockEntities(core.ship()).stream()
            .filter(e -> e instanceof StomachBlockEntity && !e.getBlockPos().equals(target))
            .map(e -> (StomachBlockEntity) e)
            .findFirst()
            .orElse(null);
    if (storage != null
        && core.getLevel().getBlockEntity(target)
            instanceof net.minecraft.world.Container inventory)
      for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
        ItemStack content = inventory.removeItemNoUpdate(slot);
        if (!content.isEmpty()) storage.inheritInput(content);
      }
    if (storage != null) {
      var be = core.getLevel().getBlockEntity(target);
      if (be != null) {
        if (be instanceof StomachBlockEntity donor) donor.transferTo(storage);
        var materials = be.getPersistentData().getCompound(BioshipStores.MATERIALS);
        for (var key : materials.getAllKeys())
          storage.retain(
              new ItemStack(
                  BuiltInRegistries.ITEM.get(ResourceLocation.parse(key)), materials.getInt(key)));
        if (!(be instanceof net.minecraft.world.Container)) {
          var inventory =
              core.getLevel()
                  .getCapability(
                      net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                      target,
                      null);
          if (inventory != null)
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
              var content = inventory.extractItem(slot, Integer.MAX_VALUE, false);
              if (!content.isEmpty()) storage.inheritInput(content);
            }
        }
      }
    }
    core.getLevel().removeBlockEntity(target);
    core.getLevel()
        .setBlock(
            target,
            restoreHost
                ? (hostState.isAir() ? BioshipTissue.body().defaultBlockState() : hostState)
                : Blocks.AIR.defaultBlockState(),
            3);
    deliver(core);
    core.setChanged();
  }

  static boolean droppedBlock(ItemStack stack) {
    if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)
        || item.getBlock() == Create_bioships.CORE.get()) return false;
    String namespace = BuiltInRegistries.BLOCK.getKey(item.getBlock()).getNamespace();
    return namespace.equals("biomancy") || namespace.equals(Create_bioships.MOD_ID);
  }

  boolean consumeDroppedBlock(LivingCoreBlockEntity core, ItemStack stack) {
    if (!droppedBlock(stack)) return false;
    var stomach = DigestiveSystem.stomach(core);
    if (!core.hasMouth() || stomach == null || !stomach.connected(core)) return false;
    var state =
        ((net.minecraft.world.item.BlockItem) stack.getItem()).getBlock().defaultBlockState();
    if (component(state) && (!BioshipNutrition.critical(core) || keepComponent(core, state, null)))
      return core.assimilation().acceptItem(core, stack);
    if (state.is(Create_bioships.INTESTINE.get()))
      state = state.setValue(IntestineBlock.PAID, true);
    recycle(state);
    deliver(core);
    core.setChanged();
    return true;
  }

  void recycleToCore(LivingCoreBlockEntity core, BlockState state) {
    int flesh = pendingFlesh, bone = pendingBone, fiber = pendingFiber;
    recycle(state);
    core.adaptation().creditRecovery(MaterialCost.FLESH, pendingFlesh - flesh);
    core.adaptation().creditRecovery(MaterialCost.BONE, pendingBone - bone);
    core.adaptation().creditRecovery(MaterialCost.FIBER, pendingFiber - fiber);
    pendingFlesh = flesh;
    pendingBone = bone;
    pendingFiber = fiber;
    core.setChanged();
  }

  void recycle(BlockState state) {
    if (state.is(Create_bioships.INTESTINE.get()) && !state.getValue(IntestineBlock.PAID)) {
      pendingFreeTubes++;
      return;
    }
    // Half the bulk flesh and only a quarter of structural/muscle products survive reclamation.
    if (++fleshRemainder >= 2) {
      pendingFlesh++;
      fleshRemainder = 0;
    }
    if (frameMaterial(state) && ++boneRemainder >= 4) {
      pendingBone++;
      boneRemainder = 0;
    }
    if (fiberMaterial(state) && ++fiberRemainder >= 4) {
      pendingFiber++;
      fiberRemainder = 0;
    }
  }

  void deliver(LivingCoreBlockEntity core) {
    int growth =
        !AirshipPrototype.available() || BioshipNutrition.critical(core)
            ? 0
            : core.incorporateBiomass(pendingFlesh);
    pendingFlesh -= growth;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null) {
      pendingFlesh = BioshipStorage.deposit(core, MaterialCost.FLESH, pendingFlesh);
      pendingBone = BioshipStorage.deposit(core, MaterialCost.BONE, pendingBone);
      pendingFiber = BioshipStorage.deposit(core, MaterialCost.FIBER, pendingFiber);
      return;
    }
    stomach.freeTubeStock += pendingFreeTubes;
    pendingFreeTubes = 0;
    stomach.setChanged();
    stomach.retainFlesh(pendingFlesh);
    pendingFlesh = 0;
    if (pendingBone > 0) {
      stomach.retain(
          new ItemStack(
              BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:bone_fragments")),
              pendingBone));
      pendingBone = 0;
    }
    if (pendingFiber > 0) {
      stomach.retain(
          new ItemStack(
              BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:elastic_fibers")),
              pendingFiber));
      pendingFiber = 0;
    }
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("FreeTubes", pendingFreeTubes);
    t.putInt("FleshRemainder", fleshRemainder);
    t.putInt("BoneRemainder", boneRemainder);
    t.putInt("FiberRemainder", fiberRemainder);
    t.putInt("Flesh", pendingFlesh);
    t.putInt("Bone", pendingBone);
    t.putInt("Fiber", pendingFiber);
    return t;
  }

  void load(CompoundTag t) {
    pendingFreeTubes = Math.max(0, t.getInt("FreeTubes"));
    fleshRemainder = Math.clamp(t.getInt("FleshRemainder"), 0, 1);
    boneRemainder = Math.clamp(t.getInt("BoneRemainder"), 0, 3);
    fiberRemainder = Math.clamp(t.getInt("FiberRemainder"), 0, 3);
    pendingFlesh = Math.max(0, t.getInt("Flesh"));
    pendingBone = Math.max(0, t.getInt("Bone"));
    pendingFiber = Math.max(0, t.getInt("Fiber"));
  }
}
