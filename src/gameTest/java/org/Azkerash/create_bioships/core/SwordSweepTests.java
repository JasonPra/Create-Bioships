package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class SwordSweepTests {
  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "native_sword_sweep")
  public static void sword_sweep_hits_each_grid_once_and_keeps_native_mob_damage(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(416, 80, 128);
    for (int x = (origin.getX() - 6) >> 4; x <= (origin.getX() + 6) >> 4; x++)
      for (int z = (origin.getZ() - 6) >> 4; z <= (origin.getZ() + 6) >> 4; z++)
        level.setChunkForced(x, z, true);
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(origin.north(3))
                        && level.isPositionEntityTicking(origin),
                    "Waiting for sweep chunks"))
        .thenExecute(
            () -> {
              level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
              level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
              level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
              var body =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level,
                      origin,
                      List.of(origin, origin.east(), origin.west()),
                      new BoundingBox3i(origin.west(), origin.east()));
              var core = body.getPlot().getCenterBlock();
              CoreAssemblyRules.mark(level, core);
              var otherPos = origin.south();
              level.setBlock(otherPos, ComponentRegistry.CORE.get().defaultBlockState(), 3);
              var other =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level, otherPos, List.of(otherPos), new BoundingBox3i(otherPos, otherPos));
              CoreAssemblyRules.mark(level, other.getPlot().getCenterBlock());
              helper.runAfterDelay(
                  10,
                  () -> {
                    var point = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                    var profile =
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "sweep-test");
                    var player =
                        new net.minecraft.server.level.ServerPlayer(
                            level.getServer(),
                            level,
                            profile,
                            net.minecraft.server.level.ClientInformation.createDefault());
                    player.connection =
                        new net.neoforged.neoforge.common.util.FakePlayer(level, profile)
                            .connection;
                    player.setPos(point.add(0, -.5, -2));
                    player.setItemInHand(
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.DIAMOND_SWORD));
                    for (int i = 0; i < 30; i++) player.doTick();
                    player.setPos(point.add(0, -.5, -2));
                    player.setDeltaMovement(Vec3.ZERO);
                    player.setOnGround(true);
                    player.setSprinting(false);
                    var victim = EntityType.COW.create(level);
                    victim.setNoAi(true);
                    victim.setPos(point.add(.75, -.5, -1.1));
                    level.addFreshEntity(victim);
                    var target = CombatAdapter.coreTarget(body);
                    target.focusForAttack(point.add(0, 0, -.55));
                    var primaryBefore = SubLevelHealth.status(body).current();
                    var secondaryBefore = SubLevelHealth.status(other).current();
                    player.attack(target);
                    var primaryDamage = primaryBefore - SubLevelHealth.status(body).current();
                    var secondaryDamage = secondaryBefore - SubLevelHealth.status(other).current();
                    helper.assertTrue(
                        Math.abs(primaryDamage - 10.5) < .001,
                        "Sword primary was double-counted or lost weapon damage: " + primaryDamage);
                    helper.assertTrue(
                        Math.abs(secondaryDamage - 1.5) < .001,
                        "Sweep missed adjacent core or hit its adapters twice: " + secondaryDamage);
                    helper.assertTrue(
                        Math.abs(victim.getHealth() - 9) < .001,
                        "Sweep changed native cow damage: "
                            + victim.getHealth()
                            + " cow="
                            + victim.position()
                            + " player="
                            + player.position()
                            + " target="
                            + target.getBoundingBox()
                            + " nearby="
                            + level
                                .getEntitiesOfClass(
                                    net.minecraft.world.entity.LivingEntity.class,
                                    target.getBoundingBox().inflate(1, .25, 1))
                                .stream()
                                .map(e -> e.getType().toShortString() + ":" + e.position())
                                .toList());
                    victim.discard();
                    player.discard();
                    helper.succeed();
                  });
            });
  }
}
