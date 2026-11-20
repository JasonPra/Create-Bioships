package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class JuniorBaselineTests {
  @GameTest(template = "test_empty", batch = "junior_baseline", timeoutTicks = 160)
  public static void baseline_restores_body_inventory_owner_and_developer_feeding_is_bounded(
      GameTestHelper h) {
    var l = h.getLevel();
    var at = new BlockPos(7800, 85, 128);
    for (int x = 486; x <= 488; x++) {
      for (int z = 7; z <= 9; z++) {
        l.setChunkForced(x, z, true);
      }
    }
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        l.setBlock(at.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
      }
    }
    l.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    l.setBlock(at.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    l.setBlock(at.west(), ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    var b =
        SubLevelAssemblyHelper.assembleBlocks(
            l, at, List.of(at, at.east(), at.west()), new BoundingBox3i(at.west(), at.east()));
    var core = b.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, core);
    DevelopmentLifeSupport.pause(b);
    b.setName("Baseline Junior");
    var owner = UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(b, owner);
    var stomach = (StomachBlockEntity) l.getBlockEntity(core.east());
    stomach.setItem(StomachBlockEntity.PASTE_SLOT, new ItemStack(StomachBlockEntity.paste(), 24));
    h.assertTrue(
        DevelopmentJuniorCare.check(b) == 0, "Caretaker fed an adequately stocked stomach");
    stomach.setItem(StomachBlockEntity.PASTE_SLOT, ItemStack.EMPTY);
    double trust = SubLevelData.trust(b);
    h.assertTrue(
        DevelopmentJuniorCare.check(b) == 64, "Caretaker failed to insert one paste stack");
    h.assertTrue(
        DevelopmentJuniorCare.check(b) == 0 && SubLevelData.trust(b) == trust,
        "Caretaker accumulated stacks or awarded feeding trust");
    var mouth = (MouthBlockEntity) l.getBlockEntity(core.west());
    mouth.clearContent();
    stomach.setItem(StomachBlockEntity.PASTE_SLOT, new ItemStack(StomachBlockEntity.paste(), 24));
    DevelopmentJuniorBaseline.capture(b);
    SubLevelHealth.damage(b, 5, "test_setup");
    l.removeBlock(core.east(), false);
    var restored = DevelopmentJuniorBaseline.reset(l);
    var newCore = restored.getPlot().getCenterBlock();
    h.assertTrue(
        b.isRemoved() && !restored.getUniqueId().equals(b.getUniqueId()),
        "Reset duplicated the old subject");
    h.assertTrue(
        owner.equals(SubLevelPermissions.get(restored).owner())
            && "Baseline Junior".equals(restored.getName()),
        "Reset lost identity label or creator ownership");
    h.assertTrue(
        l.getBlockState(newCore.east()).is(ComponentRegistry.STOMACH.get())
            && ((StomachBlockEntity) l.getBlockEntity(newCore.east()))
                    .getItem(StomachBlockEntity.PASTE_SLOT)
                    .getCount()
                == 24,
        "Reset lost stomach or saved inventory");
    h.assertTrue(
        SubLevelHealth.status(restored).current() == 30 && !SubLevelHealth.status(restored).dead(),
        "Reset retained broken health");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "junior_compact_reset", timeoutTicks = 260)
  public static void restored_compact_creature_initializes_native_segment_mass(GameTestHelper h) {
    var l = h.getLevel();
    var floor = new BlockPos(8500, 85, 128);
    for (int x = -12; x <= 12; x++) {
      for (int z = -12; z <= 12; z++) {
        l.setChunkForced((floor.getX() + x) >> 4, (floor.getZ() + z) >> 4, true);
        l.setBlock(floor.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var p = floor.above(2);
          var cells = new ArrayList<BlockPos>();
          for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
              var at = p.offset(x, 0, z);
              cells.add(at);
              l.setBlock(at, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
            }
          }
          l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          l.setBlock(p.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
          cells.add(p.above());
          for (var face : List.of(Direction.EAST, Direction.WEST)) {
            for (int z : new int[] {-1, 1}) {
              var at = p.relative(face, 2).offset(0, 0, z);
              cells.add(at);
              l.setBlock(
                  at,
                  ComponentRegistry.CRAB_LEG
                      .get()
                      .defaultBlockState()
                      .setValue(
                          net.minecraft.world.level.block.state.properties.BlockStateProperties
                              .HORIZONTAL_FACING,
                          face),
                  3);
            }
          }
          var b =
              SubLevelAssemblyHelper.assembleBlocks(
                  l, p, cells, new BoundingBox3i(p.offset(-2, 0, -1), p.offset(2, 1, 1)));
          CoreAssemblyRules.mark(l, b.getPlot().getCenterBlock());
          DevelopmentLifeSupport.pause(b);
          h.runAfterDelay(
              80,
              () -> {
                h.assertTrue(
                    SubLevelCreatureParts.members(b).size() == 13,
                    "Initial compact family incomplete");
                DevelopmentJuniorBaseline.capture(b);
                var restored = DevelopmentJuniorBaseline.reset(l);
                DevelopmentLifeSupport.pause(restored);
                h.runAfterDelay(
                    100,
                    () -> {
                      var members = SubLevelCreatureParts.members(restored);
                      h.assertTrue(
                          members.size() == 13 && !SubLevelHealth.status(restored).dead(),
                          "Reset did not rebuild living compact family");
                      for (var part : members) {
                        h.assertTrue(
                            part.getMassTracker().getCenterOfMass() != null
                                && part.getMassTracker().getMass() > 0,
                            "Restored segment lacks native mass");
                      }
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "test_empty", batch = "protected_roads", timeoutTicks = 20)
  public static void development_path_is_unbreakable_and_uses_the_native_path_shape(
      GameTestHelper h) {
    var path = DevelopmentTestBlocks.PATH.get();
    var p = h.absolutePos(new BlockPos(1, 2, 1));
    h.getLevel().setBlock(p, path.defaultBlockState(), 2);
    h.assertTrue(
        DevelopmentTestBlocks.protectedBlock(path.defaultBlockState())
            && path.defaultBlockState().getDestroySpeed(h.getLevel(), p) < 0
            && path.getExplosionResistance() >= 3600000,
        "Road material is destructible");
    h.assertTrue(
        path.defaultBlockState().getShape(h.getLevel(), p).bounds().maxY == 15 / 16d,
        "Protected road is not a dirt-path variation");
    h.succeed();
  }
}
