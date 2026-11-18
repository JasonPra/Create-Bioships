package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CreatureDecayTests {
  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "corpse_decay")
  public static void shared_decay_waits_for_the_entire_ordinary_group(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(464, 80, 128);
    level.setChunkForced(29, 8, true);
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        10,
        () -> {
          // An ordinary inventory block and a newly registered future component surround the core.
          CreatureDecay.registerLastToRot(ResourceLocation.withDefaultNamespace("diamond_block"));
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.west(), Blocks.CHEST.defaultBlockState(), 3);
          level.setBlock(origin.east(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
          ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                  level.getBlockEntity(origin.west()))
              .setItem(0, new ItemStack(Items.APPLE, 5));
          var parent =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.west(), origin.east()),
                  new BoundingBox3i(origin.west(), origin.east()));
          var core = parent.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          var detached = origin.south(3);
          level.setBlock(detached, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
          level.setBlock(detached.east(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
          var child =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  detached,
                  List.of(detached, detached.east()),
                  new BoundingBox3i(detached, detached.east()));
          helper.runAfterDelay(
              10,
              () -> {
                long now = level.getGameTime();
                CreatureDecay.advance(level, now);
                helper.assertTrue(
                    !parent.getUserDataTag().hasUUID(CreatureDecay.FAMILY),
                    "Living creature entered decay");
                SubLevelHealth.damage(parent, SubLevelHealth.status(parent).maximum(), "test");
                // Exercise the native Sable ancestry callback, rather than calling inheritance
                // directly.
                child.setSplitFrom(parent, parent.logicalPose());
                helper.assertTrue(
                    CreatureDecay.familyId(parent).equals(CreatureDecay.familyId(child)),
                    "Detached protected components lost corpse-family ID");
                helper.assertTrue(SubLevelHealth.status(child).dead(), "Detached fragment revived");
                CreatureDecay.advance(level, now);
                var world = CreatureDecay.WorldState.get(level);
                var id = CreatureDecay.familyId(parent);
                var family = world.families.get(id);
                helper.assertTrue(
                    family.interval >= 20 && family.next > now, "Decay is not slow/scheduled");
                long first = family.next;
                CreatureDecay.advance(level, first - 1);
                helper.assertTrue(
                    level.getBlockState(core.west()).is(Blocks.CHEST),
                    "Decay happened before its deadline");
                CreatureDecay.advance(level, first);
                helper.assertTrue(
                    level.getBlockState(core.west()).isAir(), "Ordinary group did not rot first");
                helper.assertTrue(
                    level.getBlockState(core).is(ComponentRegistry.CORE.get())
                        && level.getBlockState(core.east()).is(Blocks.DIAMOND_BLOCK),
                    "Registered group rotted too early");
                helper.assertTrue(
                    SubLevelHealth.blocks(child).size() == 2,
                    "Detached protected components rotted before ordinary blocks");
                helper.assertTrue(
                    level
                        .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(10))
                        .isEmpty(),
                    "Rot spilled chest contents or block loot");
                // Persist a summary for an ordinary fragment which is not loaded in this world.
                var absent = UUID.randomUUID();
                family.members.put(
                    absent,
                    new HashMap<>(Map.of(ResourceLocation.withDefaultNamespace("stone"), 1)));
                var saved = world.save(new CompoundTag(), level.registryAccess());
                var restored = CreatureDecay.WorldState.load(saved);
                helper.assertTrue(
                    restored.families.get(id).hasOrdinary(CreatureDecay.lastToRotRegistry()),
                    "Save/load forgot unloaded ordinary remains");
                var remaining =
                    SubLevelHealth.blocks(parent).size() + SubLevelHealth.blocks(child).size();
                CreatureDecay.advance(level, family.next);
                helper.assertTrue(
                    SubLevelHealth.blocks(parent).size() + SubLevelHealth.blocks(child).size()
                        == remaining,
                    "Unloaded ordinary fragment did not block the final group");
                family.members.remove(absent);
                CreatureDecay.advance(level, family.next);
                int after =
                    (parent.isRemoved() ? 0 : SubLevelHealth.blocks(parent).size())
                        + (child.isRemoved() ? 0 : SubLevelHealth.blocks(child).size());
                helper.assertTrue(
                    after == remaining - 1 && after > 1,
                    "Final group did not begin while multiple registered components remain");
                for (int i = 0; i < 8 && world.families.containsKey(id); i++)
                  CreatureDecay.advance(level, family.next);
                helper.runAfterDelay(
                    10,
                    () -> {
                      var container = SubLevelContainer.getContainer(level);
                      helper.assertTrue(
                          container.getSubLevel(parent.getUniqueId()) == null
                              && container.getSubLevel(child.getUniqueId()) == null,
                          "Rot left an empty corpse grid");
                      helper.assertTrue(
                          !world.families.containsKey(id), "Deleted family retained stale members");
                      helper.assertTrue(
                          level
                              .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(10))
                              .isEmpty(),
                          "Final decay generated drops");
                      helper.succeed();
                    });
              });
        });
  }
}
