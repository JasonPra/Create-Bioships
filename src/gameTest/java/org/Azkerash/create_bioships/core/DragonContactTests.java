package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class DragonContactTests {
  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "dragon_tissue_contact")
  public static void dragon_parts_require_tissue_contact_and_do_not_multiply_damage(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(880, 80, 128);
    level.setChunkForced(55, 8, true);
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.east(2), Blocks.STONE.defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.east(), origin.east(2)),
                  new BoundingBox3i(origin, origin.east(2)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          helper.runAfterDelay(
              5,
              () -> {
                body.forceUpdateGlobalBounds();
                var target = CombatAdapter.target(body);
                var dragon = EntityType.ENDER_DRAGON.create(level);
                dragon.setNoAi(true);
                var far =
                    body.logicalPose().transformPosition(Vec3.atCenterOf(core).add(30, 20, 30));
                dragon.setPos(far);
                level.addFreshEntity(dragon);
                dragon.setTarget(target);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                dragon
                    .getPhaseManager()
                    .getPhase(EnderDragonPhase.CHARGING_PLAYER)
                    .setTarget(target.position());
                helper.assertTrue(
                    dragon.getPhaseManager().getCurrentPhase().getPhase()
                        == EnderDragonPhase.CHARGING_PLAYER,
                    "Optional boss integration refused creature charge");
                for (var part : dragon.getSubEntities()) part.setPos(far);
                double before = SubLevelHealth.status(body).current();
                var event = new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(dragon);
                CombatAdapter.dragonContact(event);
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == before,
                    "Distant dragon dealt damage without contact");
                dragon.head.setPos(
                    body.logicalPose().transformPosition(Vec3.atCenterOf(core.east(2))));
                target.invulnerableTime = 0;
                CombatAdapter.dragonContact(event);
                helper.assertTrue(
                    Math.abs(SubLevelHealth.status(body).current() - (before - 10)) < .001,
                    "Native head contact did not route 10 damage to tissue");
                CombatAdapter.dragonContact(event);
                helper.assertTrue(
                    Math.abs(SubLevelHealth.status(body).current() - (before - 10)) < .001,
                    "Repeated contact bypassed native damage cooldown");
                dragon.discard();
                helper.runAfterDelay(10, helper::succeed);
              });
        });
  }
}
