package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class CreatureDecayPersistenceTests {
  private static final String SAVE_ID = "create_bioships_decay";
  private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");
  private static final ResourceLocation CORE =
      ResourceLocation.parse("create_bioships:living_core");

  @GameTest(template = "test_empty", batch = "decay_layout_codec")
  public static void layouts_round_trip_without_live_sublevel_objects(GameTestHelper helper) {
    var bodyId = UUID.randomUUID();
    var core = new BlockPos(20_000_000, 80, -20_000_000);
    var layout = Map.of(core.asLong(), CORE, core.west().asLong(), STONE);
    var tag = fixture(bodyId, layout);
    var state = CreatureDecay.WorldState.load(tag);
    helper.assertTrue(
        state.getCells(bodyId).equals(layout), "UUID-owned cell layout was not loaded");
    var loaded =
        CreatureDecay.WorldState.load(
            state.save(new CompoundTag(), helper.getLevel().registryAccess()));
    helper.assertTrue(
        loaded.getCells(bodyId).equals(layout),
        "Packed positions or block IDs changed on save/load");
    helper.assertTrue(
        loaded.getFamilies().get(bodyId).getNext() == 1234
            && loaded.getFamilies().get(bodyId).getInterval() == 240,
        "Cell persistence changed the family schedule");
    try {
      loaded.getCells(bodyId).clear();
      throw new AssertionError("Layout getter exposed persistent state for external mutation");
    } catch (UnsupportedOperationException expected) {
      // Diagnostic access must not bypass SavedData dirty tracking.
    }
    var oldSave = tag.copy();
    oldSave.remove("cells");
    var legacy = CreatureDecay.WorldState.load(oldSave);
    helper.assertTrue(
        legacy.getCells(bodyId).isEmpty()
            && legacy.getFamilies().get(bodyId).hasOrdinary(CreatureDecay.lastToRotRegistry()),
        "Legacy summaries were discarded when cell data was absent");
    var malformed = tag.copy();
    malformed
        .getList("cells", Tag.TAG_COMPOUND)
        .getCompound(0)
        .putLongArray("positions", new long[] {core.asLong()});
    var rejected = CreatureDecay.WorldState.load(malformed);
    helper.assertTrue(
        rejected
                .save(new CompoundTag(), helper.getLevel().registryAccess())
                .getList("cells", Tag.TAG_COMPOUND)
                .isEmpty()
            && rejected.getFamilies().get(bodyId).hasOrdinary(CreatureDecay.lastToRotRegistry()),
        "Incomplete layout became an authoritative empty corpse");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 220, batch = "decay_restart")
  public static void saved_layout_survives_disk_reload_and_fresh_sable_instance(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(22008, 80, 136);
    level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        level.setBlock(
            origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(
              origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(origin.east(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.west(), origin.east()),
                  new BoundingBox3i(origin.west(), origin.east()));
          helper.assertTrue(body != null, "Could not assemble persistence subject");
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          helper.runAfterDelay(
              10,
              () -> {
                SubLevelHealth.damage(
                    body, SubLevelHealth.status(body).maximum(), "persistence_test");
                long now = level.getGameTime();
                CreatureDecay.advance(level, now);
                var id = body.getUniqueId();
                var initial = CreatureDecay.WorldState.get(level);
                level.setBlock(
                    core.east(), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                var layout = initial.getCells(id);
                helper.assertTrue(
                    ResourceLocation.withDefaultNamespace("cobblestone")
                        .equals(layout.get(core.east().asLong())),
                    "Block type change did not update the persistent layout");
                helper.assertTrue(layout.size() == 3, "Decay did not record all corpse cells");
                long deadline = initial.getFamilies().get(id).getNext();
                int interval = initial.getFamilies().get(id).getInterval();

                // Upgrade a real legacy summary in place: discovering its layout must not restart
                // time.
                var legacyTag = initial.save(new CompoundTag(), level.registryAccess());
                legacyTag.remove("cells");
                var upgraded = CreatureDecay.WorldState.load(legacyTag);
                level.getDataStorage().set(SAVE_ID, upgraded);
                CreatureDecay.advance(level, now);
                helper.assertTrue(
                    upgraded.getCells(id).equals(layout)
                        && upgraded.getFamilies().get(id).getNext() == deadline,
                    "Legacy discovery failed or restarted the existing deadline");
                var serialized = SubLevelSerializer.toData(body, List.of());
                var container = SubLevelContainer.getContainer(level);
                container.removeSubLevel(body, SubLevelRemovalReason.UNLOADED);
                helper.assertTrue(
                    upgraded.getCells(id).equals(layout), "Unloading deleted the UUID layout");
                Path folder;
                try {
                  var dataFolder =
                      level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
                  Files.createDirectories(dataFolder);
                  folder = Files.createTempDirectory(dataFolder, "decay-reload-");
                } catch (IOException error) {
                  throw new IllegalStateException(error);
                }
                var file = folder.resolve(SAVE_ID + ".dat");
                upgraded.save(file.toFile(), level.registryAccess());
                helper
                    .startSequence()
                    .thenWaitUntil(
                        () ->
                            helper.assertTrue(
                                Files.isRegularFile(file), "Waiting for Minecraft SavedData write"))
                    .thenIdle(5)
                    .thenExecute(
                        () -> {
                          var storage =
                              new DimensionDataStorage(
                                  folder.toFile(),
                                  DataFixers.getDataFixer(),
                                  level.registryAccess());
                          var restored =
                              storage.get(
                                  new SavedData.Factory<>(
                                      CreatureDecay.WorldState::new,
                                      (tag, registries) -> CreatureDecay.WorldState.load(tag)),
                                  SAVE_ID);
                          helper.assertTrue(
                              restored != null && restored != upgraded,
                              "SavedData did not reload from disk");
                          helper.assertTrue(
                              restored.getCells(id).equals(layout),
                              "Disk reload lost the unloaded layout");
                          helper.assertTrue(
                              restored.getFamilies().get(id).getNext() == deadline
                                  && restored.getFamilies().get(id).getInterval() == interval,
                              "Disk reload reset the decay schedule");
                          level.getDataStorage().set(SAVE_ID, restored);
                          var replacement = SubLevelSerializer.fullyLoad(level, serialized);
                          helper.assertTrue(
                              replacement != null
                                  && replacement != body
                                  && replacement.getUniqueId().equals(id),
                              "Sable did not create a fresh instance with the same UUID");
                          helper.assertTrue(
                              SubLevelHealth.status(replacement).dead(),
                              "Reload revived the corpse");
                          helper.assertTrue(
                              restored.getCells(id).equals(layout),
                              "Plot bootstrap overwrote the saved layout");
                          restored.setDirty(false);
                          CreatureDecay.changed(replacement, core, level.getBlockState(core));
                          helper.assertTrue(
                              !restored.isDirty(), "Unchanged block type dirtied decay state");
                          CreatureDecay.advance(level, deadline - 1);
                          helper.assertTrue(
                              restored.getCells(id).size() == 3, "Reload caused early decay");
                          CreatureDecay.advance(level, deadline);
                          helper.assertTrue(
                              restored.getCells(id).size() == 2
                                  && restored.isDirty()
                                  && level.getBlockState(core).is(ComponentRegistry.CORE.get()),
                              "Ordinary cells did not rot before the core after reload");
                          var family = restored.getFamilies().get(id);
                          CreatureDecay.advance(level, family.getNext());
                          helper.assertTrue(
                              restored.getCells(id).size() == 1
                                  && level.getBlockState(core).is(ComponentRegistry.CORE.get()),
                              "Reload lost the ordinary-before-protected ordering");
                          CreatureDecay.advance(level, family.getNext());
                          helper.assertTrue(
                              replacement.isRemoved()
                                  && !restored.getFamilies().containsKey(id)
                                  && restored.getCells(id).isEmpty(),
                              "Permanent deletion left persistent decay state");
                        })
                    .thenSucceed();
              });
        });
  }

  private static CompoundTag fixture(UUID id, Map<Long, ResourceLocation> layout) {
    var member = new CompoundTag();
    member.putUUID("id", id);
    var counts = new CompoundTag();
    layout
        .values()
        .forEach(block -> counts.putInt(block.toString(), counts.getInt(block.toString()) + 1));
    member.put("counts", counts);
    var members = new ListTag();
    members.add(member);
    var family = new CompoundTag();
    family.putUUID("id", id);
    family.putLong("next", 1234);
    family.putInt("interval", 240);
    family.put("members", members);
    var families = new ListTag();
    families.add(family);
    var row = new CompoundTag();
    row.putUUID("id", id);
    var positions = layout.keySet().stream().mapToLong(Long::longValue).toArray();
    row.putLongArray("positions", positions);
    var blocks = new ListTag();
    for (long pos : positions) {
      blocks.add(StringTag.valueOf(layout.get(pos).toString()));
    }
    row.put("blocks", blocks);
    var cells = new ListTag();
    cells.add(row);
    var tag = new CompoundTag();
    tag.put("families", families);
    tag.put("cells", cells);
    return tag;
  }
}
