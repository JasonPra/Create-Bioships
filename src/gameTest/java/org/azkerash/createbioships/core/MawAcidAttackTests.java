package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.bigcannons.AcidBurstEntity;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MawAcidAttackTests {
  private record Rig(
      ServerSubLevel body, MouthBlockEntity maw, StomachBlockEntity stomach, UUID owner) {}

  private static Rig rig(GameTestHelper h) {
    var level = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 4, 2));
    for (int x = -1; x <= 16; x++) {
      for (int z = -2; z <= 2; z++) {
        level.setBlock(p.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    var cells = List.of(p, p.east(), p.east(2), p.east(3));
    level.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(p.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east(3),
        ComponentRegistry.ARMORED_MAW
            .get()
            .defaultBlockState()
            .setValue(MouthBlock.FACING, Direction.EAST),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(level, p, cells, new BoundingBox3i(p, p.east(3)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var owner = UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(body, owner);
    var stomach = (StomachBlockEntity) level.getBlockEntity(core.east());
    stomach.state().setAcid(StomachBlockEntity.MAX_ACID);
    return new Rig(body, (MouthBlockEntity) level.getBlockEntity(core.east(3)), stomach, owner);
  }

  private static List<AcidBurstEntity> bursts(GameTestHelper h, Rig rig) {
    var at = rig.body.logicalPose().transformPosition(Vec3.atCenterOf(rig.maw.getBlockPos()));
    return h.getLevel().getEntitiesOfClass(AcidBurstEntity.class, AABB.ofSize(at, 100, 100, 100));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "acid_maw_telegraph")
  public static void mouth_opens_before_cbc_burst_then_spends_acid_and_hits_a_target(
      GameTestHelper h) {
    var rig = rig(h);
    h.runAfterDelay(
        20,
        () -> {
          var mouth = rig.maw;
          h.assertTrue(
              mouth.requestAcidAttack(rig.owner) == MawAcidAttack.Result.READY,
              "Owned maw did not charge");
          h.assertTrue(
              mouth.getBlockState().getValue(MouthBlock.SPITTING),
              "Wide-open telegraph not visible immediately");
          h.assertTrue(bursts(h, rig).isEmpty(), "Shot fired before telegraph");
          h.runAfterDelay(
              MawAcidAttack.WINDUP_TICKS - 1,
              () -> {
                h.assertTrue(bursts(h, rig).isEmpty(), "Shot released before the full wind-up");
                h.assertTrue(
                    rig.stomach.acid() == StomachBlockEntity.MAX_ACID,
                    "Acid spent before discharge");
              });
          h.runAfterDelay(
              MawAcidAttack.WINDUP_TICKS + 3,
              () -> {
                var shots = bursts(h, rig);
                h.assertTrue(
                    shots.size() == 1,
                    "CBC burst was not created: "
                        + mouth.saveWithFullMetadata(h.getLevel().registryAccess())
                        + " time="
                        + h.getLevel().getGameTime());
                var burst = shots.getFirst();
                h.assertTrue(
                    burst.getSubProjectileCount() == AcidBurstEntity.DROPLETS,
                    "Wrong droplet budget");
                h.assertTrue(
                    rig.stomach.acid() >= StomachBlockEntity.MAX_ACID - MawAcidAttack.ACID_COST
                        && rig.stomach.acid()
                            <= StomachBlockEntity.MAX_ACID - MawAcidAttack.ACID_COST + 4,
                    "Acid cost was not committed once");
                h.assertTrue(
                    mouth.requestAcidAttack(rig.owner) == MawAcidAttack.Result.BUSY,
                    "Cooldown bypassed");
                h.assertTrue(
                    mouth.saveWithFullMetadata(h.getLevel().registryAccess()).getLong("AcidReadyAt")
                        > h.getLevel().getGameTime(),
                    "Cooldown not saved on the organ");
                var cow = EntityType.COW.create(h.getLevel());
                cow.setNoAi(true);
                cow.moveTo(burst.getX() + 8, burst.getY() - .7, burst.getZ(), 0, 0);
                h.getLevel().addFreshEntity(cow);
                h.runAfterDelay(
                    8,
                    () -> {
                      h.assertTrue(
                          cow.getHealth() < cow.getMaxHealth() && cow.hasEffect(MobEffects.POISON),
                          "Native CBC collision did not apply acid damage and poison");
                      h.assertTrue(
                          cow.getHealth() >= cow.getMaxHealth() - 5,
                          "Droplets multiplied impact damage");
                      cow.discard();
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "acid_maw_cancel")
  public static void permission_checks_and_missing_acid_cancel_without_invisible_shots(
      GameTestHelper h) {
    var rig = rig(h);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              rig.maw.requestAcidAttack(UUID.randomUUID()) == MawAcidAttack.Result.FORBIDDEN,
              "Stranger fired owned maw");
          h.assertTrue(
              rig.maw.requestAcidAttack(rig.owner) == MawAcidAttack.Result.READY,
              "Valid charge rejected");
          rig.stomach.state().setAcid(0);
          h.runAfterDelay(
              MawAcidAttack.WINDUP_TICKS + 5,
              () -> {
                h.assertTrue(bursts(h, rig).isEmpty(), "Lost acid supply still fired");
                h.assertTrue(
                    !rig.maw.getBlockState().getValue(MouthBlock.SPITTING),
                    "Cancelled attack left mouth open");
                h.succeed();
              });
        });
  }
}
