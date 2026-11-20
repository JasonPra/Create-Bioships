package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.eye.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class WeakPointWoundTests {
  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "sensory_weak_points")
  public static void core_and_eye_share_critical_damage_and_keep_projectiles(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(160, 80, 128);
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
      }
    }
    level.setChunkForced(10, 8, true);
    helper.runAfterDelay(
        10,
        () -> {
          var eyeState =
              ComponentRegistry.LAND_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(
                      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                      Direction.NORTH);
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(origin.east(), eyeState, 3);
          level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.east().south(), Blocks.STONE.defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  java.util.List.of(origin, origin.east(), origin.west(), origin.east().south()),
                  new BoundingBox3i(origin.west(), origin.east().south()));
          helper.assertTrue(body != null, "Assembly failed");
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          var source = level.damageSources().generic();
          var normal = CombatAdapter.target(body);
          normal.hurt(source, 2);
          helper.assertTrue(SubLevelHealth.status(body).current() == 38, "Ordinary damage changed");
          var critical = CombatAdapter.coreTarget(body);
          critical.hurt(source, 2);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == 35, "Core weak point is not 1.5x");
          var eye = CombatAdapter.eyeTarget(body, core.east());
          eye.hurt(source, 2);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == 32, "Eye weak point is not 1.5x");
          helper.assertTrue(
              CombatAdapter.hitTarget(body, core.east(), Direction.SOUTH) == normal,
              "Eye backing incorrectly receives critical damage");
          // Wait for the native collider and falling body pose to settle before ray casting.
          helper.runAfterDelay(
              25,
              () -> {
                var owner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
                arrow.setPos(
                    body.logicalPose().transformPosition(Vec3.atCenterOf(core).add(0, 0, -3)));
                arrow.setDeltaMovement(0, 0, 2);
                level.addFreshEntity(arrow);
                for (int i = 0; i < 3 && !ProjectileAttachment.attached(arrow); i++) {
                  arrow.tick();
                }
                helper.assertTrue(
                    !arrow.isRemoved() && ProjectileAttachment.attached(arrow),
                    "Arrow not embedded; removed="
                        + arrow.isRemoved()
                        + " pos="
                        + arrow.position()
                        + " core="
                        + body.logicalPose().transformPosition(Vec3.atCenterOf(core)));
                var point = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                helper.assertTrue(
                    arrow.position().distanceToSqr(point) < 2,
                    "Embedded arrow teleported into logical plot space");
                var saved = arrow.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                arrow.discard();
                var restored = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
                restored.load(saved);
                level.addFreshEntity(restored);
                helper.assertTrue(
                    ProjectileAttachment.attached(restored),
                    "Saved arrow lost its grid attachment on reload");
                var wounds = SubLevelWounds.state(body);
                var now = level.getGameTime();
                var reloadedWounds =
                    org.Azkerash.create_bioships.core.SurfaceWoundState.load(wounds.save(now));
                helper.assertTrue(
                    reloadedWounds.active(core, Direction.NORTH, now),
                    "Saved wound lost the struck face");
                helper.assertTrue(
                    !reloadedWounds.active(core, Direction.NORTH, now + 1200),
                    "Wound did not expire");
                var trident = new ThrownTrident(level, owner, new ItemStack(Items.TRIDENT));
                trident.setPos(point.add(0, 0, 3));
                trident.setDeltaMovement(0, 0, -1);
                critical.invulnerableTime = 0;
                normal.invulnerableTime = 0;
                level.addFreshEntity(trident);
                var hit =
                    new BlockHitResult(
                        Vec3.atCenterOf(core).add(0, 0, .5), Direction.SOUTH, core, false);
                SubLevelImpactAdapter.projectile(
                    new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(trident, hit));
                helper.assertTrue(
                    !trident.isRemoved() && ProjectileAttachment.attached(trident),
                    "Trident did not embed");
                helper.assertTrue(
                    trident.getPickupItemStackOrigin().is(Items.TRIDENT),
                    "Embedding lost original trident item");
                helper.succeed();
              });
        });
  }
}
