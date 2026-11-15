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
      pendingFiber;

  private static boolean frameMaterial(BlockState state) {
    return state.is(Create_bioships.CARAPACE.get())
        || state.is(Create_bioships.PERCH.get())
        || state.is(Create_bioships.DOOR.get())
        || state.getBlock() instanceof BlueprintPanelBlock
        || state.getBlock() instanceof AccessPanelBlock;
  }

  private static boolean fiberMaterial(BlockState state) {
    return state.is(Create_bioships.TENTACLE.get())
        || state.is(Create_bioships.INTESTINE.get())
        || state.getBlock() instanceof LivingThrottleBlock
        || state.getBlock() instanceof NerveControlBlock
        || BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .toString()
            .equals("simulated:steering_wheel");
  }

  private static boolean keepComponent(
      LivingCoreBlockEntity core, BlockState state, BlockPos exclude) {
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    var others =
        BioshipIntegration.blockEntities(core.ship()).stream()
            .filter(e -> !e.getBlockPos().equals(exclude))
            .toList();
    if (state.is(Create_bioships.FEEDING_MOUTH.get())
        || id.equals("biomancy:bio_forge")
        || id.equals("biomancy:maw_hopper"))
      return others.stream().noneMatch(e -> BioshipIntegration.isMaw(e.getBlockState()));
    if (state.is(Create_bioships.STOMACH.get()))
      return others.stream().noneMatch(e -> e instanceof StomachBlockEntity);
    if (id.equals("biomancy:decomposer") || id.equals("biomancy:digester"))
      return others.stream()
          .noneMatch(
              e -> e instanceof StomachBlockEntity || e.getBlockState().is(state.getBlock()));
    if (id.equals("biomancy:storage_sac"))
      return others.stream().filter(EngineRoom::storage).count() < 8;
    if (id.equals("biomancy:modular_larynx"))
      return core.isMature()
          && others.stream().noneMatch(e -> e.getBlockState().is(state.getBlock()));
    return false;
  }

  static boolean needed(LivingCoreBlockEntity core, BlockState state) {
    if (state.isAir()) return false;
    if (keepComponent(core, state, null)) return true;
    var stomach = DigestiveSystem.stomach(core);

    boolean flesh =
        core.missingBiomass() > 0
            || stomach != null && stomach.material("biomancy:flesh_bits") < 128;
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

  static Vec3 approach(LivingCoreBlockEntity core) {
    var container = SubLevelContainer.getContainer(core.getLevel());
    if (container == null) return null;
    Vec3 eye = BioshipBlueprint.physical(core, BlockPos.ZERO), nearest = null;
    double distance = 32 * 32;
    for (var body : container.getAllSubLevels()) {
      if (!(body instanceof ServerSubLevel wreck) || wreck.isRemoved() || !BioshipDecay.dead(wreck))
        continue;
      if (wreck
              .logicalPose()
              .transformPosition(Vec3.atCenterOf(wreck.getPlot().getCenterBlock()))
              .distanceToSqr(eye)
          > 40 * 40) continue;
      for (var source : BioshipDecay.blocks(wreck)) {
        if (!needed(core, core.getLevel().getBlockState(source))) continue;
        Vec3 point = wreck.logicalPose().transformPosition(Vec3.atCenterOf(source));
        double next = point.distanceToSqr(eye);
        if (next > 9 * 9 && next < distance && visible(core, wreck, source, eye, point)) {
          nearest = point;
          distance = next;
        }
      }
    }
    return nearest;
  }

  static boolean collect(LivingCoreBlockEntity core, ServerSubLevel own) {
    core.scavenging().deliver(core);
    var level = own.getLevel();
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return false;
    Vec3 eye = BioshipBlueprint.physical(core, BlockPos.ZERO);
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel wreck
          && wreck != own
          && !wreck.isRemoved()
          && BioshipDecay.dead(wreck)) {
        Vec3 center =
            wreck
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(wreck.getPlot().getCenterBlock()));
        if (center.distanceToSqr(eye) > 32 * 32) continue;
        var cells = BioshipDecay.blocks(wreck);
        cells.sort(
            Comparator.comparingDouble(
                p -> wreck.logicalPose().transformPosition(Vec3.atCenterOf(p)).distanceToSqr(eye)));
        for (var source : cells) {
          var state = level.getBlockState(source);
          if (!needed(core, state)) continue;
          Vec3 point = wreck.logicalPose().transformPosition(Vec3.atCenterOf(source));
          if (point.distanceToSqr(eye) > 12 * 12 || !visible(core, wreck, source, eye, point))
            continue;
          for (var dir :
              List.of(
                  Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP)) {
            var target = core.getBlockPos().relative(dir);
            var host = level.getBlockState(target);
            if (!host.isAir() && !host.is(Create_bioships.FLESH.get())) continue;
            if (OrganMotion.begin(core, source, target, true)) return true;
          }
        }
      }
    return false;
  }

  private static boolean visible(
      LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 eye, Vec3 point) {
    var physical = BlockPos.containing(point);
    var level = core.getLevel();
    if (!level.canSeeSky(physical.above())
        || !level
            .getBlockState(physical.above())
            .getCollisionShape(level, physical.above())
            .isEmpty()
        || !BioshipBlueprint.worldClear(level, eye, point)) return false;
    int steps = Math.max(1, (int) Math.ceil(eye.distanceTo(point) * 5));
    for (int i = 1; i < steps; i++) {
      var sample =
          BlockPos.containing(
              wreck.logicalPose().transformPositionInverse(eye.lerp(point, i / (double) steps)));
      if (!sample.equals(source)
          && !level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) return false;
    }
    return true;
  }

  void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost) {
    var state = core.getLevel().getBlockState(target);
    if (keepComponent(core, state, target)) {
      BioshipIntegration.finishAssimilation(core, target);
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
    var storage = DigestiveSystem.stomach(core);
    if (storage != null
        && core.getLevel().getBlockEntity(target)
            instanceof net.minecraft.world.Container inventory)
      for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
        ItemStack content = inventory.removeItemNoUpdate(slot);
        if (!content.isEmpty()) storage.inheritInput(content);
      }
    core.getLevel().removeBlockEntity(target);
    core.getLevel()
        .setBlock(
            target,
            restoreHost
                ? Create_bioships.FLESH.get().defaultBlockState()
                : Blocks.AIR.defaultBlockState(),
            3);
    deliver(core);
    core.setChanged();
  }

  void deliver(LivingCoreBlockEntity core) {
    int growth = core.incorporateBiomass(pendingFlesh);
    pendingFlesh -= growth;
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null) return;
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
    t.putInt("FleshRemainder", fleshRemainder);
    t.putInt("BoneRemainder", boneRemainder);
    t.putInt("FiberRemainder", fiberRemainder);
    t.putInt("Flesh", pendingFlesh);
    t.putInt("Bone", pendingBone);
    t.putInt("Fiber", pendingFiber);
    return t;
  }

  void load(CompoundTag t) {
    fleshRemainder = Math.clamp(t.getInt("FleshRemainder"), 0, 1);
    boneRemainder = Math.clamp(t.getInt("BoneRemainder"), 0, 3);
    fiberRemainder = Math.clamp(t.getInt("FiberRemainder"), 0, 3);
    pendingFlesh = Math.max(0, t.getInt("Flesh"));
    pendingBone = Math.max(0, t.getInt("Bone"));
    pendingFiber = Math.max(0, t.getInt("Fiber"));
  }
}
