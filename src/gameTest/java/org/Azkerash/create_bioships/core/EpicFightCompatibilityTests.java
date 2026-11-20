package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class EpicFightCompatibilityTests {
  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "optional_epicfight")
  public static void optional_weapon_colliders_use_tissue_and_weak_points(GameTestHelper helper) {
    if (!ModList.get().isLoaded("epicfight")) {
      System.out.println("EPICFIGHT_ABSENT_OK optional hooks require no Epic Fight classes");
      helper.succeed();
      return;
    }
    var level = helper.getLevel();
    var origin = new BlockPos(272, 80, 128);
    level.setChunkForced(17, 8, true);
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        level.setBlock(origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), 3);
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
          level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(
              origin.east(),
              ComponentRegistry.LAND_EYE
                  .get()
                  .defaultBlockState()
                  .setValue(facing, Direction.NORTH),
              3);
          level.setBlock(origin.east().south(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.west(2), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.south(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.south().west(), Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(origin.south().west(2), Blocks.STONE.defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(
                      origin,
                      origin.east(),
                      origin.east().south(),
                      origin.west(2),
                      origin.south(),
                      origin.south().west(),
                      origin.south().west(2)),
                  new BoundingBox3i(origin.west(2), origin.east().south()));
          helper.assertTrue(body != null, "Epic Fight fixture assembly failed");
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          var attacker = EntityType.ZOMBIE.create(level);
          attacker.setPos(
              body.logicalPose().transformPosition(Vec3.atCenterOf(core).add(0, 0, -3)));
          attacker.setNoAi(true);
          level.addFreshEntity(attacker);
          helper.runAfterDelay(
              5,
              () -> {
                try {
                  body.forceUpdateGlobalBounds();
                  var hits =
                      collide(
                          attacker, body.logicalPose().transformPosition(Vec3.atCenterOf(core)));
                  var critical = CombatAdapter.coreTarget(body);
                  helper.assertTrue(
                      hits.contains(critical),
                      "Epic Fight collider missed core hurtbox: hits="
                          + hits
                          + " bounds="
                          + body.boundingBox());
                  var hole =
                      collide(
                          attacker,
                          body.logicalPose().transformPosition(Vec3.atCenterOf(core.west())));
                  helper.assertTrue(
                      hole.stream().noneMatch(e -> e instanceof SubLevelCombatTarget),
                      "Epic Fight hit an empty gap in the grid");
                  var ordinary =
                      collide(
                          attacker,
                          body.logicalPose().transformPosition(Vec3.atCenterOf(core.west(2))));
                  helper.assertTrue(
                      ordinary.contains(CombatAdapter.target(body)),
                      "Epic Fight missed ordinary tissue: hits="
                          + ordinary
                          + " states="
                          + SubLevelHealth.blocks(body).stream()
                              .map(p -> p + ":" + level.getBlockState(p))
                              .toList());
                  var eyeBox =
                      level
                          .getBlockState(core.east())
                          .getShape(level, core.east())
                          .bounds()
                          .move(core.east());
                  var eyeHits =
                      collide(attacker, body.logicalPose().transformPosition(eyeBox.getCenter()));
                  helper.assertTrue(
                      eyeHits.contains(CombatAdapter.eyeTarget(body, core.east())),
                      "Epic Fight missed eye weak point");
                  var tissue = body.logicalPose().transformPosition(Vec3.atCenterOf(core.west(2)));
                  var adapter = CombatAdapter.target(body);
                  adapter.focusForAttack(tissue);
                  attacker.setPos(tissue.add(0, -attacker.getEyeHeight(), -4));
                  helper.assertTrue(
                      org.Azkerash.create_bioships.compat.epicfight.EpicFightBlockCollisions
                          .visibleContact(adapter, attacker),
                      "Swept contact rejected the struck creature's own tissue");
                  var wall = BlockPos.containing(tissue.add(0, 0, -2));
                  level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                  helper.assertTrue(
                      !org.Azkerash.create_bioships.compat.epicfight.EpicFightBlockCollisions
                          .visibleContact(adapter, attacker),
                      "Swept contact bypassed a world obstacle");
                  level.removeBlock(wall, false);
                  var lookup = java.lang.invoke.MethodHandles.publicLookup();
                  var patchType =
                      Class.forName(
                          "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
                  var basePatch =
                      Class.forName("yesman.epicfight.world.capabilities.entitypatch.EntityPatch");
                  var caps =
                      Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
                  try {
                    var patch =
                        lookup
                            .findStatic(
                                caps,
                                "getEntityPatch",
                                java.lang.invoke.MethodType.methodType(
                                    basePatch, Entity.class, Class.class))
                            .invoke(attacker, patchType);
                    helper.assertTrue(patch != null, "Zombie has no native Epic Fight patch");
                    boolean rejected =
                        (boolean)
                            lookup
                                .findVirtual(
                                    patchType,
                                    "isTargetInvulnerable",
                                    java.lang.invoke.MethodType.methodType(
                                        boolean.class, Entity.class))
                                .invoke(patch, critical);
                    helper.assertTrue(
                        !rejected, "Epic Fight rejects the dedicated hurtbox as unpickable");
                  } catch (Throwable e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Epic Fight target eligibility failed", e);
                  }
                  var damageType =
                      Class.forName("yesman.epicfight.world.damagesource.EpicFightDamageSource");
                  var source =
                      (net.minecraft.world.damagesource.DamageSource)
                          damageType
                              .getConstructor(net.minecraft.world.damagesource.DamageSource.class)
                              .newInstance(level.damageSources().mobAttack(attacker));
                  double before = SubLevelHealth.status(body).current();
                  critical.invulnerableTime = 0;
                  critical.hurt(source, 2);
                  helper.assertTrue(
                      SubLevelHealth.status(body).current() == before - 3,
                      "Epic Fight core damage failed to use shared 1.5x weak-point rule");
                  SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test_corpse");
                  var corpseHits = collide(attacker, tissue);
                  helper.assertTrue(
                      corpseHits.stream().noneMatch(e -> e instanceof SubLevelCombatTarget),
                      "Corpse collider retained a living mob target");
                  helper.assertTrue(
                      SubLevelWounds.state(body)
                          .active(core.west(2), Direction.NORTH, level.getGameTime()),
                      "Epic Fight collider failed to wound corpse tissue");
                  attacker.discard();
                  System.out.println(
                      "EPICFIGHT_PRESENT_OK core=true eye=true ordinary=true emptyGap=false"
                          + " critical=1.5x");
                  helper.succeed();
                } catch (ReflectiveOperationException e) {
                  throw new IllegalStateException("Epic Fight API incompatible", e);
                }
              });
        });
  }

  @SuppressWarnings("unchecked")
  private static List<Entity> collide(Entity attacker, Vec3 point)
      throws ReflectiveOperationException {
    var type = Class.forName("yesman.epicfight.api.collider.OBBCollider");
    var matrixType = Class.forName("yesman.epicfight.api.utils.math.OpenMatrix4f");
    try {
      var lookup = java.lang.invoke.MethodHandles.publicLookup();
      var constructor =
          lookup.findConstructor(
              type,
              java.lang.invoke.MethodType.methodType(
                  void.class,
                  double.class,
                  double.class,
                  double.class,
                  double.class,
                  double.class,
                  double.class));
      var collider = constructor.invokeWithArguments(.12, .12, .12, 0d, 0d, 0d);
      var matrix =
          lookup
              .findStatic(
                  matrixType,
                  "createTranslation",
                  java.lang.invoke.MethodType.methodType(
                      matrixType, float.class, float.class, float.class))
              .invokeWithArguments((float) -point.x, (float) point.y, (float) -point.z);
      lookup
          .findVirtual(
              type, "transform", java.lang.invoke.MethodType.methodType(void.class, matrixType))
          .invoke(collider, matrix);
      var base = Class.forName("yesman.epicfight.api.collider.Collider");
      return (List<Entity>)
          lookup
              .findVirtual(
                  base,
                  "getCollideEntities",
                  java.lang.invoke.MethodType.methodType(List.class, Entity.class))
              .invoke(collider, attacker);
    } catch (Throwable e) {
      e.printStackTrace();
      throw new IllegalStateException("Native Epic Fight collider failed", e);
    }
  }
}
