package org.Azkerash.create_bioships;

import com.github.elenterius.biomancy.block.bioforge.BioForgeBlockEntity;
import com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity;
import com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public class BioshipGameTests {
  private static int fixture;

  private static BlockPos location() {
    return new BlockPos(8 + 128 * fixture++, 80, 8);
  }

  private static Block block(String id) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
  }

  private static net.minecraft.server.level.ServerPlayer player(
      GameTestHelper helper, String name) {
    return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
        helper.getLevel(),
        new com.mojang.authlib.GameProfile(
            UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
  }

  private static void ground(GameTestHelper helper, BlockPos pos) {
    helper.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
    for (var point : BlockPos.betweenClosed(pos.offset(-20, -1, -20), pos.offset(20, -1, 20)))
      helper.getLevel().setBlock(point, Blocks.STONE.defaultBlockState(), 3);
  }

  private static ServerSubLevel assemble(GameTestHelper helper) {
    var pos = location();
    ground(helper, pos);
    helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    var ship =
        SubLevelAssemblyHelper.assembleBlocks(
            helper.getLevel(), pos, List.of(pos), new BoundingBox3i(pos, pos));
    SubLevelContainer.getContainer(helper.getLevel())
        .addForceLoadTicket(
            ship, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    return ship;
  }

  private static LivingCoreBlockEntity core(ServerSubLevel ship) {
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core) return core;
    throw new IllegalStateException("Missing core");
  }

  private static void cleanup(GameTestHelper helper, ServerSubLevel ship) {
    var container = SubLevelContainer.getContainer(helper.getLevel());
    for (var child : List.copyOf(container.getAllSubLevels()))
      if (child instanceof ServerSubLevel cargo
          && cargo != ship
          && cargo.getUserDataTag() != null
          && cargo.getUserDataTag().hasUUID("BioshipCargoOwner")
          && cargo.getUserDataTag().getUUID("BioshipCargoOwner").equals(ship.getUniqueId()))
        container.removeSubLevel(cargo, SubLevelRemovalReason.REMOVED);
    if (!ship.isRemoved()) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
  }

  private static void finish(GameTestHelper helper, ServerSubLevel ship, Runnable assertions) {
    try {
      assertions.run();
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  private static StomachBlockEntity digestive(
      GameTestHelper helper, LivingCoreBlockEntity core, boolean frame) {
    var level = helper.getLevel();
    level.setBlock(
        core.getBlockPos().north(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(core.getBlockPos().west(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var stomach = (StomachBlockEntity) level.getBlockEntity(core.getBlockPos().west());
    DigestiveSystem.connect(core, core.ship());
    if (frame) {
      stomach.retainFlesh(64);
      stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 8));
      stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 12));
      stomach.continueGrowth(core);
      var saved = core.saveWithFullMetadata(level.registryAccess());
      saved.putInt("Satiety", 1200);
      saved.putInt("Trust", 100);
      core.loadAdditional(saved, level.registryAccess());
    }
    return stomach;
  }

  private static void enableFlight(GameTestHelper helper, LivingCoreBlockEntity core) {
    var mineral = new ItemStack(item("aeronautics:levitite_blend_bucket"), 8);
    for (int i = 0; i < 8; i++)
      helper.assertTrue(
          core.feedAtMouth(player(helper, "Captain"), mineral), "Amalgam was rejected");
    helper.assertTrue(mineral.isEmpty(), "Amalgam was duplicated");
    var pos = core.getBlockPos().offset(DinghyGrowth.VERTICAL_TRIM);
    helper
        .getLevel()
        .setBlock(
            pos,
            helper.getLevel().getBlockState(pos).setValue(LivingThrottleBlock.THROTTLE, 20),
            3);
  }

  private static void hunt(GameTestHelper helper, LivingCoreBlockEntity core, boolean enabled) {
    var pos = core.getBlockPos().offset(DinghyGrowth.HUNT);
    var state = helper.getLevel().getBlockState(pos);
    helper
        .getLevel()
        .setBlock(
            pos,
            state
                .setValue(NerveControlBlock.LATCHED, enabled)
                .setValue(NerveControlBlock.ACTIVE, enabled),
            3);
  }

  private static String diagnose(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    String detail =
        core.saveWithFullMetadata(core.getLevel().registryAccess())
            + " stomach="
            + (stomach == null
                ? "none"
                : "connected="
                    + stomach.connected(core)
                    + " bone="
                    + stomach.material("biomancy:bone_fragments")
                    + " fiber="
                    + stomach.material("biomancy:elastic_fibers"));
    for (var part : DinghyGrowth.body()) {
      var pos = core.getBlockPos().offset(part.offset());
      if (!core.getLevel().getBlockState(pos).isAir()
          && !BioshipIntegration.isIntegratedMachine(core.getLevel(), pos))
        return detail
            + " blocked logical="
            + part.offset()
            + " "
            + core.getLevel().getBlockState(pos);
      var physical =
          BlockPos.containing(core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos)));
      if (!core.getLevel().getBlockState(physical).isAir())
        return detail
            + " blocked physical="
            + physical
            + " "
            + core.getLevel().getBlockState(physical);
    }
    return detail;
  }

  @GameTest(template = "test_empty", batch = "core_dormant")
  public static void anEyeCannotEat(GameTestHelper helper) {
    var pos = helper.absolutePos(new BlockPos(3, 4, 3));
    helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    var core = (LivingCoreBlockEntity) helper.getLevel().getBlockEntity(pos);
    var stack = new ItemStack(Items.BEEF, 2);
    core.interact(player(helper, "Captain"), stack);
    helper.assertTrue(stack.getCount() == 2, "Eye consumed food");
    helper.assertTrue(!core.getBlockState().getValue(LivingCoreBlock.BLINK), "Dormant eye blinked");
    helper.getLevel().removeBlock(pos, false);
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "physical_controls")
  public static void signedControlsValidateRangeAndNeutral(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(3, 4, 3));
    var captain = player(helper, "Captain");
    captain.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1);
    for (var control :
        List.of(Create_bioships.THROTTLE.get(), Create_bioships.VERTICAL_TRIM.get())) {
      level.setBlock(pos, control.defaultBlockState(), 3);
      for (int setting : new int[] {0, 20, 10}) {
        helper.assertTrue(
            BioshipNetwork.setThrottle(captain, pos, setting), "Control setting rejected");
        helper.assertTrue(
            LivingThrottleBlock.signed(level.getBlockState(pos)) == (setting - 10) / 10.0,
            "Wrong signed control value");
      }
      helper.assertTrue(
          !BioshipNetwork.setThrottle(captain, pos, -1)
              && !BioshipNetwork.setThrottle(captain, pos, 21),
          "Invalid setting accepted");
    }
    captain.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
    helper.assertTrue(
        !BioshipNetwork.setThrottle(captain, pos, 20), "Distant control update accepted");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 1700, batch = "grown_flight")
  public static void ovalWorkshopGrowsGroundedThenFliesWithNativeHelm(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, true);
    helper
        .getLevel()
        .setBlock(core.getBlockPos().south(), block("biomancy:storage_sac").defaultBlockState(), 3);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            var level = helper.getLevel();
            helper.assertTrue(
                core.isMature(), "Oval body did not finish growing: " + diagnose(core));
            helper.assertTrue(!core.canFly(), "Biomass supplied flight without amalgam");
            helper.assertTrue(core.swimmingFlagella() == 6, "Flagella did not grow");
            helper.assertTrue(
                EngineRoom.inside(core, DigestiveSystem.stomach(core).getBlockPos()),
                "Stomach did not move to engine room");
            for (var organ : BioshipIntegration.blockEntities(ship))
              if (EngineRoom.storage(organ))
                helper.assertTrue(
                    EngineRoom.inside(core, organ.getBlockPos()),
                    "Storage sac stayed outside the engine room");
            helper.assertTrue(
                !DigestiveSystem.stomach(core).sacPaths.isEmpty(),
                "Engine storage has no floor tubes");
            helper.assertTrue(
                level
                    .getBlockState(core.getBlockPos().offset(DinghyGrowth.DOOR))
                    .is(block("biomancy:biometric_membrane")),
                "Rear membrane missing");
            helper.assertTrue(
                level
                    .getBlockState(core.getBlockPos().offset(0, 2, 0))
                    .is(block("biomancy:biometric_membrane")),
                "Workshop divider missing");
            helper.assertTrue(
                level
                    .getBlockState(core.getBlockPos().offset(3, 3, 2))
                    .is(Create_bioships.ONE_WAY_WINDOW.get()),
                "One-way window missing");
            helper.assertTrue(
                DinghyGrowth.radius(8) < DinghyGrowth.radius(0), "Hull has no oval taper");
            var helm =
                (SteeringWheelBlockEntity)
                    level.getBlockEntity(core.getBlockPos().offset(DinghyGrowth.HELM));
            helper.assertTrue(
                helm != null && helm.material.is(Blocks.CRIMSON_PLANKS),
                "Native helm lost its material");
            enableFlight(helper, core);
            var control = core.getBlockPos().offset(DinghyGrowth.THROTTLE);
            var captain = player(helper, "Captain");
            var physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(control));
            captain.setPos(physical.x, physical.y, physical.z + 1);
            helper.assertTrue(
                BioshipNetwork.setThrottle(captain, control, 20),
                "Control update rejected on moving ship");
            double start = ship.logicalPose().position().z();
            helper.runAfterDelay(
                60,
                () -> {
                  try {
                    helper.assertTrue(
                        core.canFly() && ship.logicalPose().position().z() < start - .1,
                        "Flagella did not propel the ship");
                    level.setBlock(
                        control,
                        level.getBlockState(control).setValue(LivingThrottleBlock.THROTTLE, 0),
                        3);
                    helper.runAfterDelay(
                        80,
                        () -> {
                          try {
                            helper.assertTrue(
                                ship.latestLinearVelocity.z() > .05, "Reverse thrust failed");
                            helm.targetAngleToUpdate = 90;
                            helm.updateTargetAngle(90);
                            var heading =
                                new org.joml.Quaterniond(ship.logicalPose().orientation());
                            helper.runAfterDelay(
                                100,
                                () ->
                                    finish(
                                        helper,
                                        ship,
                                        () -> {
                                          double angle =
                                              heading
                                                  .invert()
                                                  .mul(ship.logicalPose().orientation())
                                                  .angle();
                                          helper.assertTrue(
                                              Math.min(angle, Math.PI * 2 - angle) > .05,
                                              "Native helm did not steer");
                                          for (var root : DinghyGrowth.FLAGELLA)
                                            level.removeBlock(
                                                core.getBlockPos().offset(root), false);
                                          helper.assertTrue(
                                              core.swimmingFlagella() == 0,
                                              "Destroyed limbs still propel");
                                        }));
                          } catch (Throwable failure) {
                            cleanup(helper, ship);
                            throw failure;
                          }
                        });
                  } catch (Throwable failure) {
                    cleanup(helper, ship);
                    throw failure;
                  }
                });
          } catch (Throwable failure) {
            cleanup(helper, ship);
            throw failure;
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 1400, batch = "steady_lights")
  public static void cabinLanternsStaySteadyWhileTheEyeBlinks(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, true);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            helper.assertTrue(core.isMature(), "Cabin did not grow: " + diagnose(core));
            var level = helper.getLevel();
            var lamp = core.getBlockPos().offset(DinghyGrowth.CABIN_LIGHTS.getFirst());
            var initial = level.getBlockState(lamp);
            int brightness = initial.getLightEmission(level, lamp);
            helper.assertTrue(
                initial.is(DinghyGrowth.cabinLantern().getBlock()) && brightness > 0,
                "Not a native Bio-Lantern");
            final boolean[] eyes = {false, false};
            int until = Math.floorMod(-level.getGameTime() - core.getBlockPos().getZ(), 120);
            helper.runAfterDelay(
                until + 1,
                () ->
                    eyes[0] =
                        level.getBlockState(core.getBlockPos()).getValue(LivingCoreBlock.BLINK));
            helper.runAfterDelay(
                until + 30,
                () ->
                    eyes[1] =
                        !level.getBlockState(core.getBlockPos()).getValue(LivingCoreBlock.BLINK));
            helper.runAfterDelay(
                160,
                () ->
                    finish(
                        helper,
                        ship,
                        () -> {
                          helper.assertTrue(
                              level.getBlockState(lamp).equals(initial)
                                  && level.getBlockState(lamp).getLightEmission(level, lamp)
                                      == brightness,
                              "Cabin light blinked");
                          helper.assertTrue(eyes[0] && eyes[1], "Eye did not blink independently");
                        }));
          } catch (Throwable failure) {
            cleanup(helper, ship);
            throw failure;
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "growth_obstruction")
  public static void growthNeverOverwritesObstructions(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, true);
    var target = core.getBlockPos().offset(new BlockPos(0, 0, 1));
    helper.getLevel().setBlock(target, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
    helper.runAtTickTime(
        60,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      helper.getLevel().getBlockState(target).is(Blocks.DIAMOND_BLOCK),
                      "Growth overwrote a block");
                  helper.assertTrue(!core.isMature(), "Obstructed body matured");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "acid_stomach")
  public static void stomachNeedsAnIntestineAndUsesRegeneratingAcid(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var stomach = digestive(helper, core, false);
    var data = core.saveWithFullMetadata(helper.getLevel().registryAccess());
    data.putInt("Satiety", 100);
    core.loadAdditional(data, helper.getLevel().registryAccess());
    helper.assertTrue(stomach.connected(core), "No physical intestine was built");
    helper.assertTrue(!stomach.gutPath.isEmpty(), "Mouth and stomach have no tube blocks");
    helper.assertTrue(
        stomach.enqueue(new ItemStack(Items.BEEF), false),
        "Beef was not accepted by decomposition recipes");
    var path = stomach.gutPath.getFirst();
    helper.getLevel().setBlock(path, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
    helper.assertTrue(!stomach.connected(core), "Broken intestine still connects");
    StomachBlockEntity.tick(
        helper.getLevel(), stomach.getBlockPos(), stomach.getBlockState(), stomach);
    helper.assertTrue(stomach.queuedFood() == 1, "Disconnected stomach consumed food");
    helper.getLevel().setBlock(path, Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    helper.runAtTickTime(
        140,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
                  helper.assertTrue(
                      saved.getInt("Biomass") >= 3, "Native decomposition did not supply flesh");
                  helper.assertTrue(
                      saved.getInt("Satiety") > 100 && saved.getInt("Trust") == 61,
                      "Fresh digestion did not count as feeding");
                  helper.assertTrue(
                      stomach.acid() > 100 && stomach.acid() < StomachBlockEntity.MAX_ACID,
                      "Acid did not regenerate after use");
                  helper.assertTrue(
                      helper
                              .getLevel()
                              .getCapability(
                                  Capabilities.ItemHandler.BLOCK, stomach.getBlockPos(), null)
                          == null,
                      "Stomach exposes extractable products");
                  var menu = new StomachMenu(1, player(helper, "Captain").getInventory(), stomach);
                  helper.assertTrue(menu.slots.isEmpty(), "Stomach GUI exposes output slots");
                  var restored =
                      (StomachBlockEntity)
                          BlockEntity.loadStatic(
                              stomach.getBlockPos(),
                              stomach.getBlockState(),
                              stomach.saveWithFullMetadata(helper.getLevel().registryAccess()),
                              helper.getLevel().registryAccess());
                  helper.assertTrue(
                      restored != null && restored.acid() == stomach.acid(),
                      "Stomach acid did not persist");
                }));
  }

  @GameTest(template = "test_empty", batch = "storage_overflow")
  public static void fullStomachOverflowsIntoConnectedFoodSacs(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      var pos = core.getBlockPos().offset(-2, 0, 0);
      helper.getLevel().setBlock(pos, block("biomancy:storage_sac").defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(stomach.sacPaths.containsKey(pos), "Storage sac has no floor tube");
      for (int i = 0; i < 576; i++)
        helper.assertTrue(
            stomach.enqueue(new ItemStack(Items.BEEF), false), "Stomach capacity inconsistent");
      helper.assertTrue(
          DigestiveSystem.offerFood(core, new ItemStack(Items.BEEF), false),
          "Food did not overflow into storage");
      var inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
      int total = 0;
      for (int slot = 0; slot < inventory.getSlots(); slot++)
        total += inventory.getStackInSlot(slot).getCount();
      helper.assertTrue(
          total == 1 && stomach.queuedFood() == 576, "Overflow lost or duplicated food");
      DigestiveSystem.pullStoredFood(core, stomach);
      helper.assertTrue(stomach.queuedFood() == 576, "Full stomach pulled excess food");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", batch = "machine_conversion")
  public static void onlyOneForgeAndDecomposerBecomeOrgans(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      BlockPos forgePos = core.getBlockPos().north(), decomposerPos = core.getBlockPos().west();
      level.setBlock(forgePos, block("biomancy:bio_forge").defaultBlockState(), 3);
      var forge = (BioForgeBlockEntity) level.getBlockEntity(forgePos);
      forge.setStackInFuelSlot(new ItemStack(Items.BEEF, 64));
      forge.getStateData().fuelHandler.setFuelAmount(80);
      BioshipIntegration.finishAssimilation(core, forgePos);
      level.setBlock(decomposerPos, block("biomancy:decomposer").defaultBlockState(), 3);
      var decomposer = (DecomposerBlockEntity) level.getBlockEntity(decomposerPos);
      decomposer
          .getOutputInventory()
          .setStackInSlot(0, new ItemStack(item("biomancy:bone_fragments"), 8));
      decomposer
          .getOutputInventory()
          .setStackInSlot(1, new ItemStack(item("biomancy:elastic_fibers"), 12));
      BioshipIntegration.finishAssimilation(core, decomposerPos);
      helper.assertTrue(
          level.getBlockEntity(forgePos) instanceof FeedingMouthBlockEntity
              && level.getBlockEntity(decomposerPos) instanceof StomachBlockEntity,
          "First machines did not become unique organs");
      BlockPos extraForge = core.getBlockPos().east(), extraDecomposer = core.getBlockPos().south();
      level.setBlock(extraForge, block("biomancy:bio_forge").defaultBlockState(), 3);
      level.setBlock(extraDecomposer, block("biomancy:decomposer").defaultBlockState(), 3);
      BioshipIntegration.finishAssimilation(core, extraForge);
      BioshipIntegration.finishAssimilation(core, extraDecomposer);
      helper.assertTrue(
          level.getBlockEntity(extraForge) instanceof BioForgeBlockEntity
              && level.getBlockEntity(extraDecomposer) instanceof DecomposerBlockEntity,
          "Additional crafting machines were converted");
      var stomach = DigestiveSystem.stomach(core);
      helper.assertTrue(
          stomach.material("biomancy:bone_fragments") == 8
              && stomach.material("biomancy:elastic_fibers") == 12,
          "Existing decomposition products were lost");
      DigestiveSystem.connect(core, ship);
      helper.runAtTickTime(
          40,
          () ->
              finish(
                  helper,
                  ship,
                  () -> {
                    int total =
                        core.saveWithFullMetadata(level.registryAccess()).getInt("Biomass")
                            + stomach.material("biomancy:flesh_bits");
                    helper.assertTrue(
                        total == 80, "Stored Bio-Forge biomass was not transferred: " + total);
                    helper.assertTrue(
                        core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") <= 600,
                        "Inherited tissue supplied nutrition");
                  }));
    } catch (Throwable failure) {
      cleanup(helper, ship);
      throw failure;
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "tentacle_assimilation")
  public static void externalBlocksArePulledAsRealSableCargo(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var world =
        BlockPos.containing(
            ship.logicalPose().transformPosition(Vec3.atCenterOf(core.getBlockPos())));
    var source = world.east(2);
    helper.getLevel().setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
    var inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, source, null);
    inventory.insertItem(0, new ItemStack(Items.BEEF, 32), false);
    inventory.insertItem(1, new ItemStack(Items.DIAMOND, 7), false);
    BioshipIntegration.assimilate(core, ship);
    helper.assertTrue(
        core.organMove() != null && core.organMove().external(), "No tentacle pull was started");
    var cargo =
        SubLevelContainer.getContainer(helper.getLevel()).getSubLevel(core.organMove().cargo());
    helper.assertTrue(
        cargo != null && helper.getLevel().getBlockState(source).isAir(),
        "Source did not become moving Sable cargo");
    helper.runAtTickTime(
        80,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(core.organMove() == null, "Tentacle pull did not complete");
                  helper.assertTrue(
                      helper
                          .getLevel()
                          .getEntitiesOfClass(
                              net.minecraft.world.entity.item.ItemEntity.class,
                              new AABB(Vec3.atCenterOf(source), Vec3.atCenterOf(source)).inflate(4))
                          .isEmpty(),
                      "Tentacle assembly duplicated inventory into dropped items");
                  BlockPos stored = null;
                  for (var entity : BioshipIntegration.blockEntities(ship))
                    if (EngineRoom.storage(entity)) stored = entity.getBlockPos();
                  helper.assertTrue(stored != null, "Pulled sac did not join the construct");
                  var moved =
                      helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, stored, null);
                  helper.assertTrue(
                      moved.getStackInSlot(0).getCount() == 32
                          && moved.getStackInSlot(1).getCount() == 7,
                      "Tentacle pull lost or duplicated inventory");
                }));
  }

  @GameTest(template = "test_empty", batch = "visibility")
  public static void assimilationCannotReachThroughWallsOrRoofs(GameTestHelper helper) {
    var pos = location();
    ground(helper, pos);
    var target = pos.east(3);
    helper.getLevel().setBlock(target, block("biomancy:bio_lab").defaultBlockState(), 3);
    helper.assertTrue(
        BioshipIntegration.exposedAndVisible(helper.getLevel(), pos, target),
        "Exposed Biomancy block is not eligible");
    helper.getLevel().setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !BioshipIntegration.exposedAndVisible(helper.getLevel(), pos, target),
        "Assimilation reached through a wall");
    helper.getLevel().removeBlock(pos.east(), false);
    helper.getLevel().setBlock(target.above(), Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !BioshipIntegration.exposedAndVisible(helper.getLevel(), pos, target),
        "Assimilation reached a roofed or underground block");
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "biometric_access")
  public static void biometricAccessIsOwnerControlledAndTracksAnimalIdentity(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      net.minecraft.server.level.ServerPlayer owner = player(helper, "Captain"),
          guest = player(helper, "Guest");
      core.claimCreator(owner.getUUID());
      var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
      saved.putInt("Stage", 2);
      core.loadAdditional(saved, helper.getLevel().registryAccess());
      BlockPos panel = core.getBlockPos().offset(DinghyGrowth.ACCESS_PANEL),
          membrane = core.getBlockPos().offset(DinghyGrowth.DOOR);
      helper.getLevel().setBlock(panel, Create_bioships.ACCESS_PANEL.get().defaultBlockState(), 3);
      helper
          .getLevel()
          .setBlock(membrane, block("biomancy:biometric_membrane").defaultBlockState(), 3);
      var physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(panel));
      owner.setPos(physical.x, physical.y, physical.z);
      guest.setPos(physical.x, physical.y, physical.z);
      var animal = EntityType.COW.create(helper.getLevel());
      animal.setCustomName(net.minecraft.network.chat.Component.literal("Daisy"));
      helper.getLevel().addFreshEntity(animal);
      var entries =
          List.of(
              new AccessMenu.Choice(guest.getUUID(), "Guest", false),
              new AccessMenu.Choice(animal.getUUID(), "Daisy", true));
      var menu = new AccessMenu(1, owner.getInventory(), core, panel, entries);
      helper.assertTrue(!menu.clickMenuButton(guest, 0), "Non-owner modified permissions");
      helper.assertTrue(
          menu.clickMenuButton(owner, 0) && menu.clickMenuButton(owner, 1),
          "Owner could not authorize guests");
      var gate = (BiometricMembraneBlockEntity) helper.getLevel().getBlockEntity(membrane);
      helper.assertTrue(
          gate.shouldIgnoreEntityCollisionAt(
                  gate.getBlockState(), helper.getLevel(), membrane, owner)
              && gate.shouldIgnoreEntityCollisionAt(
                  gate.getBlockState(), helper.getLevel(), membrane, guest)
              && gate.shouldIgnoreEntityCollisionAt(
                  gate.getBlockState(), helper.getLevel(), membrane, animal),
          "Authorized identities cannot cross membrane");
      var copy = EntityType.COW.create(helper.getLevel());
      copy.setCustomName(animal.getName());
      helper.assertTrue(
          !gate.shouldIgnoreEntityCollisionAt(
              gate.getBlockState(), helper.getLevel(), membrane, copy),
          "Copied animal name bypassed identity check");
      var restored = new BiometricMembraneBlockEntity(membrane, gate.getBlockState());
      restored.loadWithComponents(
          gate.getUpdateTag(helper.getLevel().registryAccess()),
          helper.getLevel().registryAccess());
      helper.assertTrue(
          restored.shouldIgnoreEntityCollisionAt(
              restored.getBlockState(), helper.getLevel(), membrane, guest),
          "Synced membrane lost whitelist");
      helper.assertTrue(
          menu.clickMenuButton(owner, 0)
              && !gate.shouldIgnoreEntityCollisionAt(
                  gate.getBlockState(), helper.getLevel(), membrane, guest),
          "Removed guest retained access");
      animal.discard();
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", batch = "recipes_and_tools")
  public static void bioForgeRecipesAndDedicatedEquipmentAreRegistered(GameTestHelper helper) {
    for (String name : List.of("living_core", "bioship_wrench", "bioship_goggles")) {
      var recipe =
          helper
              .getLevel()
              .getRecipeManager()
              .byKey(
                  ResourceLocation.fromNamespaceAndPath(
                      Create_bioships.MODID, "bio_forging/" + name));
      helper.assertTrue(
          recipe.isPresent()
              && recipe.get().value()
                  instanceof com.github.elenterius.biomancy.crafting.recipe.BioForgingRecipe,
          "Missing native Bio-Forge recipe: " + name);
      helper.assertTrue(
          recipe
              .get()
              .value()
              .getResultItem(helper.getLevel().registryAccess())
              .is(item(Create_bioships.MODID + ":" + name)),
          "Wrong Bio-Forge output");
    }
    var captain = player(helper, "Captain");
    captain.setItemSlot(
        EquipmentSlot.HEAD, Create_bioships.BIOSHIP_GOGGLES.get().getDefaultInstance());
    helper.assertTrue(
        com.simibubi.create.content.equipment.goggles.GogglesItem.isWearingGoggles(captain),
        "Symbiotic goggles do not support the native helm");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "organ_orientation")
  public static void bioshipWrenchRotatesAndMovesOwnedOrgans(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, false);
    var captain = player(helper, "Captain");
    core.claimCreator(captain.getUUID());
    captain.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    captain.setShiftKeyDown(true);
    for (var pos : List.of(core.mouthBlock(), DigestiveSystem.stomach(core).getBlockPos())) {
      var original =
          helper
              .getLevel()
              .getBlockState(pos)
              .getValue(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
      for (int n = 0; n < 6; n++) {
        var context =
            new net.minecraft.world.item.context.UseOnContext(
                captain,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        Create_bioships.BIOSHIP_WRENCH.get().useOn(context);
      }
      helper.assertTrue(
          helper
                  .getLevel()
                  .getBlockState(pos)
                  .getValue(
                      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
              == original,
          "Organ cannot rotate through six directions");
    }
    captain.setShiftKeyDown(false);
    BlockPos source = core.mouthBlock(), target = core.getBlockPos().offset(3, 2, 3);
    helper.getLevel().setBlock(target, Create_bioships.FLESH.get().defaultBlockState(), 3);
    for (var pos : List.of(source, target))
      Create_bioships.BIOSHIP_WRENCH
          .get()
          .useOn(
              new net.minecraft.world.item.context.UseOnContext(
                  captain,
                  net.minecraft.world.InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
    helper.assertTrue(
        core.organMove() != null && !core.organMove().external(),
        "Wrench did not start internal organ movement");
    helper.runAtTickTime(
        80,
        () ->
            finish(
                helper,
                ship,
                () ->
                    helper.assertTrue(
                        helper
                            .getLevel()
                            .getBlockState(target)
                            .is(Create_bioships.FEEDING_MOUTH.get()),
                        "Owned mouth did not reach requested location")));
  }

  @GameTest(template = "test_empty", timeoutTicks = 1600, batch = "hunting_digestion")
  public static void huntingPreyMustBeDigestedAndSkeletonsAreNotFood(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, true);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            helper.assertTrue(core.isMature(), "Body did not mature: " + diagnose(core));
            enableFlight(helper, core);
            hunt(helper, core, true);
            helper.runAfterDelay(
                100,
                () -> {
                  try {
                    helper.assertTrue(
                        core.mouthBlock() != null && !EngineRoom.inside(core, core.mouthBlock()),
                        "Hunting mouth stayed inside");
                    var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
                    saved.putInt("Satiety", 100);
                    core.loadAdditional(saved, helper.getLevel().registryAccess());
                    var chicken = EntityType.CHICKEN.create(helper.getLevel());
                    chicken.setNoAi(true);
                    chicken.setNoGravity(true);
                    var meal = core.mouthPosition(ship).add(0, -.3, -.3);
                    chicken.setPos(meal.x, meal.y, meal.z);
                    helper.getLevel().addFreshEntity(chicken);
                    helper.runAfterDelay(
                        240,
                        () ->
                            finish(
                                helper,
                                ship,
                                () -> {
                                  helper.assertTrue(
                                      !chicken.isAlive(),
                                      "Tentacles did not capture and consume prey");
                                  helper.assertTrue(
                                      DigestiveSystem.stomach(core).meals() > 0
                                          && DigestiveSystem.stomach(core)
                                                  .material("biomancy:flesh_bits")
                                              > 0,
                                      "Prey did not pass through digestion");
                                  var skeleton = EntityType.SKELETON.create(helper.getLevel());
                                  helper.assertTrue(
                                      !TentacleBlockEntity.edible(skeleton),
                                      "Skeletons count as food");
                                  chicken.discard();
                                }));
                  } catch (Throwable failure) {
                    cleanup(helper, ship);
                    throw failure;
                  }
                });
          } catch (Throwable failure) {
            cleanup(helper, ship);
            throw failure;
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "automatic_placement")
  public static void playerPlacementCreatesProtectedSableObject(GameTestHelper helper) {
    var pos = location();
    ground(helper, pos);
    var captain = player(helper, "Captain");
    helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    Create_bioships.CORE
        .get()
        .setPlacedBy(
            helper.getLevel(),
            pos,
            helper.getLevel().getBlockState(pos),
            captain,
            Create_bioships.CORE_ITEM.get().getDefaultInstance());
    ServerSubLevel ship = null;
    for (var candidate : SubLevelContainer.getContainer(helper.getLevel()).getAllSubLevels())
      if (candidate instanceof ServerSubLevel server) {
        for (var actor : server.getPlot().getBlockEntityActors())
          if (actor instanceof LivingCoreBlockEntity core
              && captain.getUUID().equals(core.creator())) ship = server;
      }
    helper.assertTrue(ship != null, "Placement did not automatically assemble");
    try {
      var core = core(ship);
      helper.assertTrue(!core.canFly(), "New eye supplied flight");
      boolean blocked = false;
      try {
        dev.simulated_team.simulated.content.blocks.physics_assembler.assembly_preventer
            .DisassemblyPrevention.checkSubLevelForPrimary(helper.getLevel(), core.getBlockPos());
      } catch (com.simibubi.create.content.contraptions.AssemblyException expected) {
        blocked = true;
      }
      helper.assertTrue(blocked, "Living structure can be disassembled");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 2200, batch = "later_larynx")
  public static void continuedDigestionDevelopsOneModularLarynx(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    digestive(helper, core, true);
    helper.runAtTickTime(
        1000,
        () -> {
          helper.assertTrue(core.isMature(), "Initial body did not mature");
          var stomach = DigestiveSystem.stomach(core);
          stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 2));
          stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 4));
          for (int meal = 0; meal < 8; meal++)
            helper.assertTrue(
                stomach.enqueue(new ItemStack(Items.BEEF), false), "Continued food was rejected");
        });
    helper.runAtTickTime(
        2050,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var stomach = DigestiveSystem.stomach(core);
                  helper.assertTrue(stomach.meals() >= 8, "Post-growth meals did not register");
                  helper.assertTrue(
                      helper
                          .getLevel()
                          .getBlockState(core.getBlockPos().offset(DinghyGrowth.LARYNX))
                          .is(block("biomancy:modular_larynx")),
                      "Native modular larynx did not develop");
                  helper.assertTrue(
                      stomach.material("biomancy:bone_fragments") == 0
                          && stomach.material("biomancy:elastic_fibers") == 0,
                      "Larynx did not use structural materials exactly once");
                }));
  }
}
