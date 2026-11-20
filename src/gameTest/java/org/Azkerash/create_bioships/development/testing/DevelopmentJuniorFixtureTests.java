package org.Azkerash.create_bioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DevelopmentJuniorFixtureTests {
  @GameTest(template = "test_empty", batch = "bundled_junior", timeoutTicks = 200)
  public static void fresh_developer_fixture_restores_authored_junior_and_is_idempotent(
      GameTestHelper h) {
    var l = h.getLevel();
    var base = NecromancerDenFixture.base(l);
    for (int x = -20; x <= 20; x++) {
      for (int z = -20; z <= 20; z++) {
        var p = base.offset(x, -1, z);
        l.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
        l.setBlock(p, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var state = CoreTestState.get(l);
          state.setJuniorSubject(null);
          state.setJuniorBaseline(new net.minecraft.nbt.CompoundTag());
          var template = DevelopmentJuniorFixture.template(l);
          var owner =
              template.getCompound("user").getCompound(SubLevelData.ACCESS).getUUID("owner");
          DevelopmentJuniorFixture.install(l);
          var id = state.getJuniorSubject();
          h.runAfterDelay(
              80,
              () -> {
                var b =
                    (dev.ryanhcode.sable.sublevel.ServerSubLevel)
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l)
                            .getSubLevel(id);
                h.assertTrue(
                    SubLevelCreatureParts.members(b).size() == 25,
                    "Bundled Junior lacks his compact limbs");
                h.assertTrue(
                    "Junior".equals(org.Azkerash.create_bioships.core.naming.SubLevelNames.name(b))
                        && owner.equals(SubLevelPermissions.get(b).owner()),
                    "Bundled Junior lost label or creator");
                h.assertTrue(
                    CreatureDecay.familyId(b).equals(b.getUniqueId()),
                    "Reference creature inherited another entity's death family");
                var air = SubLevelOxygen.status(b);
                h.assertTrue(
                    air.lungs() == 1
                        && air.supply() > air.demand()
                        && !SubLevelHealth.status(b).dead(),
                    "Bundled Junior life support is invalid");
                DevelopmentJuniorFixture.install(l);
                h.assertTrue(
                    id.equals(state.getJuniorSubject()),
                    "Fixture duplicated Junior on repeat installation");
                h.succeed();
              });
        });
  }
}
