package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Feeding appendages belong to mouths; walking legs are an independent support system. */
public final class BioshipGraspers {
  private static final java.util.List<
          java.util.function.Function<LivingCoreBlockEntity, java.util.List<Slot>>>
      providers = new java.util.ArrayList<>();

  /** Extension point for future explicit grasper components; providers do not select goals. */
  public static void registerProvider(
      java.util.function.Function<LivingCoreBlockEntity, java.util.List<Slot>> provider) {
    providers.add(java.util.Objects.requireNonNull(provider));
  }

  public record Slot(String key, BlockPos mouth, int ordinal, Vec3 root, boolean swimming) {
    public Slot(String key, BlockPos mouth, int ordinal, Vec3 root) {
      this(key, mouth, ordinal, root, false);
    }
  }

  public static List<Slot> slots(LivingCoreBlockEntity core) {
    var level = core.getLevel();
    if (level == null) return List.of();
    var container = SubLevelContainer.getContainer(level);
    var plot = container == null ? null : container.getPlot(new ChunkPos(core.getBlockPos()));
    if (plot == null) return List.of();
    var mouths = new ArrayList<FeedingMouthBlockEntity>();
    for (var chunk : plot.getSubLevel().getPlot().getLoadedChunks())
      for (var entity : chunk.getChunk().getBlockEntities().values())
        if (entity instanceof FeedingMouthBlockEntity mouth) mouths.add(mouth);
    mouths.sort(Comparator.comparing(FeedingMouthBlockEntity::getBlockPos));
    var slots = new ArrayList<Slot>();
    for (var mouth : mouths) {
      int tier = mouth.voiced() ? 2 : 1;
      var normal =
          Vec3.atLowerCornerOf(
              mouth.getBlockState().getValue(FeedingMouthBlock.FACING).getNormal());
      var side = Math.abs(normal.y) > .5 ? new Vec3(1, 0, 0) : new Vec3(-normal.z, 0, normal.x);
      for (int i = 0; i < tier && slots.size() < 32; i++) {
        var root =
            Vec3.atCenterOf(mouth.getBlockPos())
                .add(normal.scale(.48))
                .add(side.scale(tier == 1 ? 0 : i == 0 ? -.23 : .23))
                .add(0, -.2, 0);
        slots.add(
            new Slot(
                mouth.getBlockPos().asLong() + ":" + i,
                mouth.getBlockPos(),
                i,
                root,
                mouth.swimmingGrasper(i)));
      }
    }
    for (var provider : providers)
      for (var slot : provider.apply(core)) if (slots.size() < 32) slots.add(slot);
    // An amoeboid core must still acquire its first digestive organ. This is not a walking leg.
    if (slots.isEmpty())
      slots.add(new Slot("core", core.getBlockPos(), 0, Vec3.atCenterOf(core.getBlockPos())));
    return List.copyOf(slots);
  }

  static Slot available(LivingCoreBlockEntity core, Vec3 target, Collection<String> busy) {
    return slots(core).stream()
        .filter(s -> !busy.contains(s.key()))
        .min(
            Comparator.comparingDouble(
                s -> core.ship().logicalPose().transformPosition(s.root()).distanceToSqr(target)))
        .orElse(null);
  }

  public static boolean swimming(LivingCoreBlockEntity core, String key) {
    return slots(core).stream().anyMatch(s -> s.key().equals(key) && s.swimming());
  }

  static double attackReach(LivingCoreBlockEntity core, Vec3 target) {
    var slot = available(core, target, java.util.List.of());
    return core.aquatic().wet() && slot != null && slot.swimming() ? 5.5 : 4;
  }

  public static Vec3 root(LivingCoreBlockEntity core, String key) {
    var slots = slots(core);
    return slots.stream()
        .filter(s -> s.key().equals(key))
        .findFirst()
        .orElse(slots.get(0))
        .root()
        .subtract(Vec3.atLowerCornerOf(core.getBlockPos()));
  }
}
