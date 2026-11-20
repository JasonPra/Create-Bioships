package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MembranePermissionTests {
  @GameTest(template = "test_empty", batch = "membrane_permissions", timeoutTicks = 120)
  public static void uuid_permissions_native_collision_transfer_and_persistence(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    var membrane = com.github.elenterius.biomancy.init.ModBlocks.BIOMETRIC_MEMBRANE.get();
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(origin.east(), membrane.defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east()),
            new BoundingBox3i(origin, origin.east()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var owner =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Owner"));
    var guest =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Guest"));
    var intruder =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Intruder"));
    helper.assertTrue(
        SubLevelPermissions.assignIfUnowned(body, owner.getUUID())
            && !SubLevelPermissions.assignIfUnowned(body, intruder.getUUID()),
        "Owner could be overwritten");
    helper.assertTrue(
        SubLevelPermissions.canInspect(body, owner.getUUID())
            && !SubLevelPermissions.canInspect(body, guest.getUUID())
            && !SubLevelPermissions.canInspect(body, intruder.getUUID()),
        "Inspection disclosed data to an uninvited player");
    var pos = core.east();
    var state = level.getBlockState(pos);
    helper.assertTrue(
        state.getCollisionShape(level, pos, CollisionContext.of(owner)).isEmpty()
            && !state.getCollisionShape(level, pos, CollisionContext.of(guest)).isEmpty(),
        "Native membrane did not distinguish owner and denied guest");
    try {
      var movementShape =
          dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.class
              .getDeclaredMethod(
                  "getSubLevelEntityCollisionShape",
                  net.minecraft.world.entity.Entity.class,
                  org.joml.Vector3dc.class,
                  dev.ryanhcode.sable.companion.math.Pose3dc.class,
                  net.minecraft.world.level.block.state.BlockState.class,
                  dev.ryanhcode.sable.util.LevelAccelerator.class,
                  BlockPos.class,
                  dev.ryanhcode.sable.api.math.LevelReusedVectors.class);
      movementShape.setAccessible(true);
      var query = new dev.ryanhcode.sable.util.LevelAccelerator(level);
      var vectors = new dev.ryanhcode.sable.api.math.LevelReusedVectors();
      var center = new org.joml.Vector3d();
      var pass =
          (net.minecraft.world.phys.shapes.VoxelShape)
              movementShape.invoke(
                  null, owner, center, body.logicalPose(), state, query, pos, vectors);
      var deny =
          (net.minecraft.world.phys.shapes.VoxelShape)
              movementShape.invoke(
                  null, guest, center, body.logicalPose(), state, query, pos, vectors);
      helper.assertTrue(
          pass.isEmpty() && !deny.isEmpty(),
          "Sable movement bypassed entity-aware membrane permissions");
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
    helper.assertTrue(
        !SubLevelPermissions.add(body, intruder.getUUID(), guest)
            && SubLevelPermissions.add(body, owner.getUUID(), guest),
        "Permission edit authority broken");
    helper.assertTrue(
        SubLevelPermissions.canInspect(body, guest.getUUID()),
        "Invited player cannot inspect creature");
    var mob = EntityType.COW.create(level);
    var duplicate = EntityType.COW.create(level);
    mob.setCustomName(Component.literal("Bessie"));
    duplicate.setCustomName(Component.literal("Bessie"));
    helper.assertTrue(
        SubLevelPermissions.add(body, owner.getUUID(), mob)
            && state.getCollisionShape(level, pos, CollisionContext.of(mob)).isEmpty()
            && !state.getCollisionShape(level, pos, CollisionContext.of(duplicate)).isEmpty(),
        "Named mob authorization used name instead of UUID");
    mob.setCustomName(Component.literal("Renamed"));
    helper.assertTrue(
        SubLevelPermissions.get(body).permits(mob.getUUID()), "Renaming lost identity permission");
    SubLevelPermissions.forget(body);
    helper.assertTrue(
        SubLevelPermissions.get(body).owner().equals(owner.getUUID())
            && SubLevelPermissions.get(body).permits(guest.getUUID()),
        "Grid permission NBT did not survive reload");
    helper.assertTrue(
        !SubLevelPermissions.transfer(body, intruder.getUUID(), guest.getUUID())
            && SubLevelPermissions.transfer(body, owner.getUUID(), guest.getUUID()),
        "Owner transfer authority broken");
    helper.assertTrue(
        !SubLevelPermissions.get(body).permits(owner.getUUID())
            && SubLevelPermissions.get(body).permits(guest.getUUID())
            && SubLevelPermissions.get(body).permits(mob.getUUID()),
        "Transfer did not preserve whitelist and replace owner");
    helper.assertTrue(
        !SubLevelPermissions.remove(body, owner.getUUID(), mob.getUUID())
            && SubLevelPermissions.remove(body, guest.getUUID(), mob.getUUID()),
        "Former owner could edit after transfer");
    helper.assertTrue(
        !SubLevelPermissions.canInspect(body, owner.getUUID())
            && SubLevelPermissions.canInspect(body, guest.getUUID()),
        "Inspection did not follow ownership transfer");
    helper.assertTrue(
        SubLevelPermissions.add(body, guest.getUUID(), intruder)
            && SubLevelPermissions.canInspect(body, intruder.getUUID())
            && SubLevelPermissions.remove(body, guest.getUUID(), intruder.getUUID())
            && !SubLevelPermissions.canInspect(body, intruder.getUUID()),
        "Revoked invitation still permits inspection");
    SubLevelHealth.damage(body, 10000, "test");
    helper.assertTrue(
        state.getCollisionShape(level, pos, CollisionContext.of(guest)).isEmpty(),
        "Corpse lost ownership permissions");
    var world = origin.south(3);
    level.setBlock(world, membrane.defaultBlockState(), Block.UPDATE_ALL);
    var nativeMembrane =
        (com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlockEntity)
            level.getBlockEntity(world);
    nativeMembrane.setEntityFilter(intruder);
    helper.assertTrue(
        membrane
                .defaultBlockState()
                .getCollisionShape(level, world, CollisionContext.of(intruder))
                .isEmpty()
            && !membrane
                .defaultBlockState()
                .getCollisionShape(level, world, CollisionContext.of(guest))
                .isEmpty(),
        "World membrane native filtering was replaced");
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "membrane_placer", timeoutTicks = 120)
  public static void player_core_placement_records_owner(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1776, 85, 160);
    level.setChunkForced(111, 10, true);
    var owner = UUID.randomUUID();
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(
              origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          CoreAssemblyRules.playerPlaced(level, origin, owner);
          helper.runAfterDelay(
              10,
              () -> {
                var container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                boolean found = false;
                for (var entry : container.getAllSubLevels()) {
                  if (entry instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body
                      && owner.equals(SubLevelPermissions.get(body).owner())) {
                    found = true;
                  }
                }
                helper.assertTrue(
                    found, "Player-placed core did not save its placer as grid owner");
                helper.succeed();
              });
        });
  }
}
