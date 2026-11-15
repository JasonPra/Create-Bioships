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

@GameTestHolder(Create_bioships.MOD_ID)
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
          level.getBlockEntity(decomposerPos) instanceof DecomposerBlockEntity,
          "A decomposer formed a stomach without a digester");
      var digesterPos = decomposerPos.north();
      level.setBlock(digesterPos, block("biomancy:digester").defaultBlockState(), 3);
      var digester =
          (com.github.elenterius.biomancy.block.digester.DigesterBlockEntity)
              level.getBlockEntity(digesterPos);
      digester.getOutputInventory().setStackInSlot(0, new ItemStack(item("biomancy:nutrients"), 3));
      digester.getInputInventory().setStackInSlot(0, new ItemStack(Items.BEEF, 4));
      digester.getStateData().fuelHandler.setFuelAmount(9);
      BioshipIntegration.finishAssimilation(core, digesterPos);
      helper.assertTrue(
          level.getBlockState(digesterPos).isAir(),
          "Digester was not incorporated into the combined stomach");
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
      helper.assertTrue(
          stomach.material("biomancy:nutrients") == 3
              && stomach.queuedFood() == 4
              && stomach.nutrients() == 9,
          "Digester contents were not preserved");
      var extraDigester = extraDecomposer.east();
      level.setBlock(extraDigester, block("biomancy:digester").defaultBlockState(), 3);
      BioshipIntegration.finishAssimilation(core, extraDigester);
      helper.assertTrue(
          level.getBlockEntity(extraDigester)
              instanceof com.github.elenterius.biomancy.block.digester.DigesterBlockEntity,
          "A second digester was consumed");
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
                      Create_bioships.MOD_ID, "bio_forging/" + name));
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
              .is(item(Create_bioships.MOD_ID + ":" + name)),
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

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void tubes_only_connect_to_digestive_neighbors(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var p = core.getBlockPos().above();
    level.setBlock(p, Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(p.east(), Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(p.west(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(p.north(), Create_bioships.FLESH.get().defaultBlockState(), 3);
    var state = level.getBlockState(p);
    helper.assertTrue(
        state.getValue(IntestineBlock.CONNECTIONS.get(Direction.EAST)), "Missing tube connection");
    helper.assertTrue(
        state.getValue(IntestineBlock.CONNECTIONS.get(Direction.WEST)), "Missing mouth connection");
    helper.assertTrue(
        !state.getValue(IntestineBlock.CONNECTIONS.get(Direction.NORTH))
            && !state.getValue(IntestineBlock.CONNECTIONS.get(Direction.UP)),
        "Tube branches into air or ordinary flesh");
    level.setBlock(p, state.setValue(IntestineBlock.ENCASED, true), 3);
    level.removeBlock(p.west(), false);
    helper.assertTrue(
        !level.getBlockState(p).getValue(IntestineBlock.CONNECTIONS.get(Direction.WEST)),
        "Broken neighbor remained connected");
    helper.assertTrue(
        level.getBlockState(p).getValue(IntestineBlock.ENCASED)
            && level.getBlockState(p).getValue(IntestineBlock.CONNECTIONS.get(Direction.EAST)),
        "Encasing lost the working connection");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 200)
  public static void hidden_health_memory_and_biomass_repair(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    var owner = player(helper, "TissueOwner");
    core.claimCreator(owner.getUUID());
    stomach.retainFlesh(8);
    var p = core.getBlockPos().east();
    level.setBlock(p, Create_bioships.FLESH.get().defaultBlockState(), 3);
    core.body().observe(core);
    double healthy = core.body().health();
    int trust = core.saveWithFullMetadata(level.registryAccess()).getInt("Trust");
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
        new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
            level, p, level.getBlockState(p), owner));
    level.removeBlock(p, false);
    helper.assertTrue(core.body().health() < healthy, "Removing tissue did not injure the ship");
    helper.assertTrue(
        core.saveWithFullMetadata(level.registryAccess()).getInt("Trust") < trust,
        "Creator damage did not lower trust");
    var saved = core.saveWithFullMetadata(level.registryAccess());
    core.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(
        core.body().memory.containsKey(p.subtract(core.getBlockPos())),
        "Hole was forgotten on reload");
    helper.runAfterDelay(
        60,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      level.getBlockState(p).is(Create_bioships.FLESH.get()),
                      "Tissue did not regrow");
                  helper.assertTrue(
                      stomach.material("biomancy:flesh_bits") == 7,
                      "Repair did not spend exactly one flesh bit");
                  helper.assertTrue(
                      core.body().health() == healthy, "Repair did not restore health");
                  var guest = player(helper, "GuestBreaks");
                  int prior = core.saveWithFullMetadata(level.registryAccess()).getInt("Trust");
                  core.body().damage(core, p, guest);
                  helper.assertTrue(
                      prior == core.saveWithFullMetadata(level.registryAccess()).getInt("Trust"),
                      "Stranger damage blamed the owner");
                  var shrinking = core.body().save();
                  shrinking.putInt("Capacity", 2000);
                  shrinking.putDouble("Health", 2000);
                  core.body().load(shrinking, level.registryAccess());
                  core.body().observe(core);
                  helper.assertTrue(
                      core.body().health() <= core.body().capacity(),
                      "Shrinking the remembered body left health above its capacity");
                  helper.assertTrue(
                      core.goggleTooltip().stream()
                          .noneMatch(
                              line ->
                                  line.getString()
                                      .toLowerCase(java.util.Locale.ROOT)
                                      .contains("health")),
                      "Internal health leaked into the goggles tooltip");
                  level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                  core.body().observe(core);
                  var unchanged = BioshipBody.actual(core);
                  core.body().reconcile(core, unchanged);
                  helper.assertTrue(
                      core.body()
                          .memory
                          .get(p.subtract(core.getBlockPos()))
                          .is(Create_bioships.FLESH.get()),
                      "An outside obstruction replaced the remembered hull schematic");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 2600)
  public static void blueprint_draft_landing_rebuild_and_inventory(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    var owner = player(helper, "Architect");
    core.claimCreator(owner.getUUID());
    helper.runAfterDelay(
        1000,
        () -> {
          helper.assertTrue(core.isMature(), "Ship failed to mature");
          core.body().observe(core);
          var stomach = DigestiveSystem.stomach(core);
          stomach.retainFlesh(1024);
          for (int n = 0; n < 8; n++)
            core.acceptAmalgam(new ItemStack(item("aeronautics:levitite_blend_bucket")));
          var draft = new BioshipBlueprint.Draft(core.blueprint().rooms());
          // Add a room sharing the engine-room wall, and a membrane connecting their interiors.
          draft.edit(2 * 2500 + (5 + 24) * 49 + (7 + 24));
          draft.edit(6 * 2500 + (5 + 24) * 49 + (4 + 24));
          int quoted = core.blueprint().cost(core, draft);
          var plan = core.blueprint().proposal(core, draft);
          helper.assertTrue(
              core.blueprint().validate(core, draft, plan) == null,
              "Valid joined room was rejected: " + core.blueprint().validate(core, draft, plan));
          helper.assertTrue(quoted > 0, "New room has no biomass cost");
          helper.assertTrue(
              level.getBlockState(core.getBlockPos().offset(7, 1, 5)).isAir(),
              "Draft changed the live hull");
          var disconnected = new BioshipBlueprint.Draft(core.blueprint().rooms());
          disconnected.edit(2 * 2500 + (20 + 24) * 49 + (20 + 24));
          helper.assertTrue(
              core.blueprint()
                      .validate(core, disconnected, core.blueprint().proposal(core, disconnected))
                  != null,
              "Disconnected room was accepted");
          var discarded = new BioshipBlueprint.Draft(core.blueprint().rooms());
          helper.assertTrue(
              core.blueprint().cost(core, discarded) == 0,
              "Discarding draft did not restore the current plan");
          helper.runAfterDelay(
              30,
              () -> {
                int stock = stomach.material("biomancy:flesh_bits");
                helper.assertTrue(
                    core.blueprint().confirm(core, draft, core.blueprint().revision()),
                    "Confirmation failed: " + core.blueprint().status());
                helper.assertTrue(
                    stomach.material("biomancy:flesh_bits") == stock - quoted,
                    "Quoted biomass was not charged once");
                var tag = core.saveWithFullMetadata(level.registryAccess());
                core.loadAdditional(tag, level.registryAccess());
                helper.assertTrue(core.blueprint().busy(), "Approved job did not persist");
              });
        });
    helper.runAfterDelay(
        2500,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      !core.blueprint().busy(),
                      "Job stalled: " + core.blueprint().phase() + " " + core.blueprint().status());
                  helper.assertTrue(
                      core.blueprint().rooms().size() == 3, "New room was not committed");
                  helper.assertTrue(
                      level
                          .getBlockState(core.getBlockPos().offset(7, 1, 5))
                          .is(Create_bioships.FLESH.get()),
                      "Room floor missing");
                  helper.assertTrue(
                      level
                          .getBlockState(core.getBlockPos().offset(4, 2, 5))
                          .is(block("biomancy:biometric_membrane")),
                      "Requested door missing");
                  helper.assertTrue(
                      DigestiveSystem.stomach(core).material("biomancy:flesh_bits") > 0,
                      "Stored stomach contents were lost");
                  helper.assertTrue(
                      !core.blueprint().locked(), "Membranes stayed locked after construction");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 3500, batch = "room_surgery")
  public static void moving_engine_room_evacuates_and_preserves_stomach(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    var owner = player(helper, "RoomMover");
    core.claimCreator(owner.getUUID());
    var occupant = new net.minecraft.world.entity.animal.Cow(EntityType.COW, level);
    boolean[] sawArm = {false};
    double[] health = {0};
    int[] stock = {0};
    int[] cost = {0};
    helper.runAfterDelay(
        1000,
        () -> {
          helper.assertTrue(core.isMature(), "Ship did not grow before relocation");
          core.body().observe(core);
          var stomach = DigestiveSystem.stomach(core);
          stomach.retainFlesh(2048);
          stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 17));
          for (int n = 0; n < 8; n++)
            core.acceptAmalgam(new ItemStack(item("aeronautics:levitite_blend_bucket")));
        });
    helper.runAfterDelay(
        1040,
        () -> {
          var d = new BioshipBlueprint.Draft(core.blueprint().rooms());
          d.selected = 1;
          d.edit(2500 + (5 + 24) * 49 + (2 + 24));
          var error = core.blueprint().validate(core, d, core.blueprint().proposal(core, d));
          helper.assertTrue(error == null, "Room move was rejected: " + error);
          stock[0] = DigestiveSystem.stomach(core).material("biomancy:flesh_bits");
          cost[0] = core.blueprint().cost(core, d);
          health[0] = core.body().health();
          var point = BioshipBlueprint.physical(core, new BlockPos(0, 2, 2));
          occupant.moveTo(point.x, point.y - .5, point.z, 0, 0);
          level.addFreshEntity(occupant);
          helper.assertTrue(
              core.blueprint().confirm(core, d, core.blueprint().revision()),
              "Move confirmation failed: " + core.blueprint().status());
          var tag = core.saveWithFullMetadata(level.registryAccess());
          tag.putInt("Satiety", 1200);
          tag.putInt("Trust", 100);
          core.loadAdditional(tag, level.registryAccess());
        });
    helper.runAfterDelay(
        1070,
        () -> {
          sawArm[0] = core.blueprint().carried() != null && core.blueprint().locked();
        });
    helper.runAfterDelay(
        1090,
        () -> {
          sawArm[0] |= core.blueprint().carried() != null && core.blueprint().locked();
        });
    helper.runAfterDelay(
        1150,
        () -> {
          if (core.blueprint().busy()) {
            var tag = core.saveWithFullMetadata(level.registryAccess());
            core.loadAdditional(tag, level.registryAccess());
          }
        });
    helper.runAfterDelay(
        3350,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      sawArm[0], "Passenger was not carried by the evacuation tentacle");
                  helper.assertTrue(
                      !core.blueprint().busy(),
                      "Room relocation stalled: "
                          + core.blueprint().phase()
                          + " / "
                          + core.blueprint().status());
                  helper.assertTrue(
                      occupant.isAlive() && !core.blueprint().aboard(core, occupant),
                      "Passenger was not put safely outside");
                  helper.assertTrue(
                      core.blueprint().rooms().get(1).x() == 2,
                      "Engine room location was not saved");
                  var stomach = DigestiveSystem.stomach(core);
                  helper.assertTrue(stomach != null, "Moving the room lost its stomach");
                  helper.assertTrue(
                      stomach.material("biomancy:bone_fragments") == 17,
                      "Moving the room lost or duplicated private products");
                  helper.assertTrue(
                      stomach.material("biomancy:flesh_bits") == stock[0] - cost[0],
                      "Relocation charged twice or lost reserves");
                  helper.assertTrue(
                      core.body().health() >= health[0],
                      "Approved room surgery injured the bioship");
                  helper.assertTrue(
                      stomach.getBlockPos().equals(core.layoutPos(new BlockPos(-2, 2, 3))),
                      "Stomach did not move with its room");
                  helper.assertTrue(
                      !BioshipWrenchItem.movable(level, core.layoutPos(new BlockPos(-3, 2, 3))),
                      "Wrench can remove hull tissue");
                  occupant.discard();
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 2100, batch = "hatchling_walk")
  public static void blocked_hatchling_walks_to_clear_ground_without_flight(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    Vec3 start = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var obstacle = BlockPos.containing(start).south();
    for (int y = 0; y < 4; y++)
      level.setBlock(obstacle.above(y), Blocks.OBSIDIAN.defaultBlockState(), 3);
    digestive(helper, core, true);
    boolean[] walking = {false};
    helper.runAfterDelay(80, () -> walking[0] = core.crawl().walking());
    helper.runAfterDelay(
        2000,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  Vec3 end = BioshipBlueprint.physical(core, BlockPos.ZERO);

                  helper.assertTrue(
                      end.subtract(start).horizontalDistanceSqr() > 9,
                      "Walking arms did not move: start="
                          + start
                          + " end="
                          + end
                          + " crawl="
                          + core.crawl().save());
                  helper.assertTrue(
                      level.getBlockState(obstacle).is(Blocks.OBSIDIAN),
                      "Walking or growth ate the obstruction");
                  helper.assertTrue(
                      core.isMature(),
                      "Hatchling did not finish growing after finding space: " + diagnose(core));
                  helper.assertTrue(
                      !core.canFly() && end.y < start.y + 1.5,
                      "Walking supplied levitite-free flight");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "combined_stomach")
  public static void stomach_combines_separated_decomposer_and_digester(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var d = core.getBlockPos().west();
    var g = core.getBlockPos().east();
    level.setBlock(d, block("biomancy:decomposer").defaultBlockState(), 3);
    BioshipIntegration.finishAssimilation(core, d);
    helper.assertTrue(DigestiveSystem.stomach(core) == null, "Decomposer alone formed a stomach");
    level.setBlock(g, block("biomancy:digester").defaultBlockState(), 3);
    var digester =
        (com.github.elenterius.biomancy.block.digester.DigesterBlockEntity) level.getBlockEntity(g);
    digester.getOutputInventory().setStackInSlot(0, new ItemStack(item("biomancy:nutrients"), 7));
    BioshipIntegration.finishAssimilation(core, g);
    helper.assertTrue(
        OrganMotion.busy(core), "Separated organs did not move together through tissue");
    helper.runAfterDelay(
        100,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var stomach = DigestiveSystem.stomach(core);
                  helper.assertTrue(
                      stomach != null && stomach.material("biomancy:nutrients") == 7,
                      "Fusion lost the digester output");
                  helper.assertTrue(
                      BioshipIntegration.blockEntities(ship).stream()
                          .noneMatch(
                              e ->
                                  e
                                      instanceof
                                      com.github.elenterius.biomancy.block.digester
                                          .DigesterBlockEntity),
                      "Digester remained after fusion");
                  helper.assertTrue(
                      core.body().health() == core.body().capacity(),
                      "Organ fusion injured the ship");
                }));
  }

  private static void emptySatiety(LivingCoreBlockEntity core) {
    var t = core.saveWithFullMetadata(core.getLevel().registryAccess());
    t.putInt("Satiety", 0);
    t.putInt("MetabolismTicks", 0);
    core.loadAdditional(t, core.getLevel().registryAccess());
  }

  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "starvation_reserves")
  public static void emergency_reserves_delay_permanent_starvation_death(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    stomach.addNutrients(1);
    stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 2));
    emptySatiety(core);
    int initialTrust = core.saveWithFullMetadata(level.registryAccess()).getInt("Trust");
    helper.runAfterDelay(
        25,
        () -> {
          helper.assertTrue(
              stomach.nutrients() == 0 && stomach.material("biomancy:elastic_fibers") == 2,
              "Nutrients were not consumed before fibers");
          helper.assertTrue(
              core.body().health() == 100, "Reserve-backed starvation damaged health");
          emptySatiety(core);
        });
    helper.runAfterDelay(
        50,
        () -> {
          helper.assertTrue(
              stomach.material("biomancy:elastic_fibers") == 1,
              "Starving ship did not burn a stored fiber");
          helper.assertTrue(
              core.saveWithFullMetadata(level.registryAccess()).getInt("Trust") <= initialTrust,
              "Burning reserves granted trust");
          stomach.spend("biomancy:elastic_fibers", 1);
          emptySatiety(core);
        });
    helper.runAfterDelay(
        75,
        () -> {
          helper.assertTrue(
              core.body().health() < 100 && !core.body().dead(),
              "Empty reserves did not cause gradual starvation damage");
          var t = core.body().save();
          t.putDouble("Health", 1);
          core.body().load(t, level.registryAccess());
          emptySatiety(core);
        });
    helper.runAfterDelay(
        105,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      core.body().dead() && !core.canFly() && BioshipDecay.dead(ship),
                      "Exhausted ship did not die");
                  var saved = core.saveWithFullMetadata(level.registryAccess());
                  core.loadAdditional(saved, level.registryAccess());
                  stomach.retainFlesh(100);
                  helper.assertTrue(
                      core.body().dead() && core.body().health() == 0,
                      "Death was undone by reload or biomass");
                  helper.assertTrue(
                      !core.feedAtMouth(player(helper, "CorpseFeeder"), new ItemStack(Items.BEEF)),
                      "A dead ship accepted feeding");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 1650, batch = "wreck_fall_rot")
  public static void dead_flying_ship_falls_and_fragments_decay(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    double[] altitude = {0};
    int[] before = {0};
    ServerSubLevel[] fragment = {null};
    helper.runAfterDelay(1000, () -> enableFlight(helper, core));
    helper.runAfterDelay(
        1150,
        () -> {
          altitude[0] = BioshipBlueprint.physical(core, BlockPos.ZERO).y;
          helper.assertTrue(altitude[0] > 84, "Flight fixture did not rise");
          before[0] = BioshipDecay.blocks(ship).size();
          core.body().die(core);
          var decayTag = ship.getUserDataTag().copy();
          decayTag.putLong("BioshipDecayAfter", level.getGameTime());
          ship.setUserDataTag(decayTag);
          var source = core.layoutPos(new BlockPos(0, 1, 8));
          if (!level.getBlockState(source).isAir()) {
            fragment[0] =
                SubLevelAssemblyHelper.assembleBlocks(
                    level, source, List.of(source), new BoundingBox3i(source, source));
            fragment[0].setSplitFrom(ship, ship.logicalPose());
            SubLevelContainer.getContainer(level)
                .addForceLoadTicket(
                    fragment[0],
                    SubLevelLoadingTicketType.COMMAND_FORCED,
                    net.minecraft.util.Unit.INSTANCE);
            helper.assertTrue(BioshipDecay.dead(fragment[0]), "Split wreck did not inherit death");
          }
        });
    helper.runAfterDelay(
        1550,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      BioshipBlueprint.physical(core, BlockPos.ZERO).y < altitude[0] - 2,
                      "Dead ship retained levitation");
                  helper.assertTrue(
                      BioshipDecay.blocks(ship).size() < before[0] - 1,
                      "Wreck did not rot over time");
                  if (fragment[0] != null) {
                    helper.assertTrue(
                        fragment[0].isRemoved() || BioshipDecay.dead(fragment[0]),
                        "Coreless fragment lost its shared decay state");
                    if (!fragment[0].isRemoved())
                      SubLevelContainer.getContainer(level)
                          .removeSubLevel(fragment[0], SubLevelRemovalReason.REMOVED);
                  }
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 260, batch = "wreck_scavenge")
  public static void scavenging_recovers_needed_organs_and_tissue_at_a_loss(GameTestHelper helper) {
    var live = assemble(helper);
    var core = core(live);
    var level = helper.getLevel();
    var point = BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO)).east(5);
    level.setBlock(point, Create_bioships.CORE.get().defaultBlockState(), 3);
    var wreck =
        SubLevelAssemblyHelper.assembleBlocks(
            level, point, List.of(point), new BoundingBox3i(point, point));
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            wreck, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    var dead = core(wreck);
    var mouth = dead.getBlockPos().west();
    level.setBlock(mouth, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    ((FeedingMouthBlockEntity) level.getBlockEntity(mouth))
        .setItem(0, new ItemStack(Items.BARRIER, 11));
    helper.assertTrue(
        ((FeedingMouthBlockEntity) level.getBlockEntity(mouth)).getItem(0).getCount() == 11,
        "Maw inventory setup failed");
    var stomachPos = dead.getBlockPos().above();
    level.setBlock(stomachPos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    helper.assertTrue(
        !OrganMotion.begin(core, mouth, core.getBlockPos().north(), true),
        "Living neighbor was eligible for scavenging");
    dead.body().die(dead);
    helper.assertTrue(
        !BioshipScavenging.needed(core, dead.getBlockState())
            && !OrganMotion.begin(core, dead.getBlockPos(), core.getBlockPos().north(), true),
        "A dead bioship core was eligible for consumption");
    ItemStack[] recoveredStock = {ItemStack.EMPTY};
    helper.runAfterDelay(
        80,
        () -> {
          var recovered = (FeedingMouthBlockEntity) level.getBlockEntity(core.mouthBlock());
          helper.assertTrue(
              recovered.getItem(0).is(Items.BARRIER) && recovered.getItem(0).getCount() == 11,
              "Mouth inventory was not preserved across corpse recovery");
          recoveredStock[0] = recovered.removeItem(0, 11);
        });
    helper.runAfterDelay(
        170,
        () -> {
          try {
            helper.assertTrue(
                core.hasMouth() && DigestiveSystem.stomach(core) != null,
                "Nearby wreck organs were not pulled aboard");
            helper.assertTrue(
                recoveredStock[0].is(Items.BARRIER) && recoveredStock[0].getCount() == 11,
                "Recovered inventory could not be salvaged before digestion");
            helper.assertTrue(
                BioshipScavenging.needed(
                    core, Create_bioships.FEEDING_MOUTH.get().defaultBlockState()),
                "Duplicate dead organs cannot be reclaimed as construction stock");
            var port = core.getBlockPos().above();
            int satiety = core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety");
            for (int n = 0; n < 4; n++) {
              level.setBlock(port, Create_bioships.TENTACLE.get().defaultBlockState(), 3);
              core.scavenging().finish(core, port, false);
            }
            for (int n = 0; n < 4; n++) {
              level.setBlock(port, Create_bioships.CARAPACE.get().defaultBlockState(), 3);
              core.scavenging().finish(core, port, false);
            }
            var stomach = DigestiveSystem.stomach(core);
            helper.assertTrue(
                core.missingBiomass() == 60
                    && stomach.material("biomancy:elastic_fibers") == 1
                    && stomach.material("biomancy:bone_fragments") == 1,
                "Reclaimed tissue did not use the specified lossy yield");
            helper.assertTrue(
                core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") == satiety,
                "Wreck tissue counted as food");
            level.setBlock(port, Create_bioships.FLESH.get().defaultBlockState(), 3);
            core.scavenging().finish(core, port, false);
            var save = core.saveWithFullMetadata(level.registryAccess());
            core.loadAdditional(save, level.registryAccess());
            level.setBlock(port, Create_bioships.FLESH.get().defaultBlockState(), 3);
            core.scavenging().finish(core, port, false);
            helper.assertTrue(
                core.missingBiomass() == 59, "Fractional salvage yield did not survive reload");
            var originalMouth = core.mouthBlock();
            level.setBlock(port, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
            core.scavenging().finish(core, port, false);
            helper.assertTrue(
                level.getBlockState(port).isAir() && core.mouthBlock().equals(originalMouth),
                "Reclaimed duplicate organ was kept as a second mouth");
            helper.succeed();
          } finally {
            cleanup(helper, wreck);
            cleanup(helper, live);
          }
        });
  }

  private static class InteractionPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
    final List<net.minecraft.network.chat.Component> messages = new ArrayList<>();

    InteractionPlayer(net.minecraft.server.level.ServerLevel level, String name) {
      super(
          level,
          new com.mojang.authlib.GameProfile(
              UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
              name));
    }

    @Override
    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) {
      return openMenu(provider, b -> {});
    }

    @Override
    public java.util.OptionalInt openMenu(
        net.minecraft.world.MenuProvider provider,
        java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer) {
      containerMenu = Objects.requireNonNull(provider.createMenu(1, getInventory(), this));
      return java.util.OptionalInt.of(1);
    }

    @Override
    public void displayClientMessage(
        net.minecraft.network.chat.Component message, boolean overlay) {
      messages.add(message);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "empty_mouth_click")
  public static void empty_hand_opens_mouth_without_eating_or_overflow(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var sac = core.getBlockPos().east();
    level.setBlock(sac, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    var user = new InteractionPlayer(level, "MouthUser");
    var mouth = core.mouthBlock();
    Vec3 point = BioshipBlueprint.physical(core, mouth.subtract(core.getBlockPos()));
    user.setPos(point.x, point.y, point.z - 2);
    int[] sounds = {0};
    java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition> sound =
        event -> {
          if (event.getSound() != null
              && event.getSound().value() == net.minecraft.sounds.SoundEvents.GENERIC_EAT)
            sounds[0]++;
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.NORMAL,
        false,
        net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition.class,
        sound);
    try {
      helper.assertTrue(
          !core.feedAtMouth(user, ItemStack.EMPTY), "Empty hand counted as direct feeding");
      helper.assertTrue(
          !DigestiveSystem.offerFood(core, ItemStack.EMPTY, false),
          "Empty item counted as sac overflow");
      var result =
          user.gameMode.useItemOn(
              user,
              level,
              ItemStack.EMPTY,
              net.minecraft.world.InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(mouth), Direction.NORTH, mouth, false));
      helper.assertTrue(
          result.consumesAction()
              && user.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,
          "Empty-hand click did not reach the mouth inventory");
      helper.assertTrue(
          ((net.minecraft.world.inventory.ChestMenu) user.containerMenu).getContainer()
              == level.getBlockEntity(mouth),
          "Wrong mouth inventory opened");
      helper.assertTrue(
          user.containerMenu.stillValid(user),
          "Mouth inventory immediately failed physical reach validation");
      helper.assertTrue(
          sounds[0] == 0 && stomach.queuedFood() == 0,
          "Empty-hand inspection made the ship chew or enqueue food");
      for (boolean active : List.of(false, true))
        for (boolean blink : List.of(false, true))
          helper.assertTrue(
              Create_bioships.CORE
                      .get()
                      .defaultBlockState()
                      .setValue(LivingCoreBlock.ACTIVE, active)
                      .setValue(LivingCoreBlock.BLINK, blink)
                      .getLightEmission(level, core.getBlockPos())
                  == 0,
              "The eye emits block light");
      user.messages.clear();
      core.interact(user, ItemStack.EMPTY);
      helper.assertTrue(user.messages.size() == 1, "Missing goggles reminder was not given");
      user.setItemSlot(
          EquipmentSlot.HEAD, Create_bioships.BIOSHIP_GOGGLES.get().getDefaultInstance());
      core.interact(user, ItemStack.EMPTY);
      helper.assertTrue(user.messages.size() == 1, "Wearing goggles still produced the reminder");
      helper.succeed();
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(sound);
      user.closeContainer();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "sideways_tissue")
  public static void organs_use_existing_walls_and_never_grow_early_filler(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    // This position belongs to the future hull but is only an exposed machine in the eye stage.
    var source = core.getBlockPos().east();
    var target = core.getBlockPos().north();
    level.setBlock(source, block("biomancy:digester").defaultBlockState(), 3);
    helper.assertTrue(
        OrganMotion.begin(core, source, target, false), "Bare component did not start moving");
    helper.assertTrue(
        level.getBlockState(source).isAir() && core.organMove().usesArm(),
        "Future hull geometry created early flesh or an imaginary tissue route");
    helper.runAfterDelay(
        45,
        () -> {
          helper.assertTrue(
              level.getBlockState(source).isAir(), "The eye-stage move left a hull filler behind");
          // Move an organ through a vertical wall, well above the floor, with six-direction
          // connectivity.
          var wallSource = core.getBlockPos().offset(1, 2, 0);
          var wallTarget = core.getBlockPos().offset(1, 5, 0);
          for (int y = 0; y <= 5; y++)
            level.setBlock(
                core.getBlockPos().offset(1, y, 0),
                Create_bioships.FLESH.get().defaultBlockState(),
                3);
          level.setBlock(wallSource, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
          var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(wallSource);
          mouth.getPersistentData().putBoolean("BioshipHostFlesh", true);
          mouth.setItem(0, new ItemStack(Items.BEEF, 5));
          helper.assertTrue(
              OrganMotion.begin(core, wallSource, wallTarget, false),
              "Wall-embedded organ did not move");
          var move = core.organMove();
          helper.assertTrue(
              !move.usesArm()
                  && move.tissuePath().stream()
                      .allMatch(
                          p ->
                              p.getX() == wallSource.getX()
                                  && p.getZ() == wallSource.getZ()
                                  && p.getY() >= wallSource.getY()),
              "Move ignored the existing side wall or sank into the floor");
          var saved = core.saveWithFullMetadata(level.registryAccess());
          core.loadAdditional(saved, level.registryAccess());
          helper.assertTrue(
              core.organMove().tissuePath().equals(move.tissuePath()),
              "Wall route did not survive reload");
        });
    helper.runAfterDelay(
        100,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var old = core.getBlockPos().offset(1, 2, 0);
                  var next = core.getBlockPos().offset(1, 5, 0);
                  helper.assertTrue(
                      level.getBlockState(old).is(Create_bioships.FLESH.get()),
                      "Moving an embedded organ did not restore its real host tissue");
                  helper.assertTrue(
                      level.getBlockEntity(next) instanceof FeedingMouthBlockEntity
                          && ((FeedingMouthBlockEntity) level.getBlockEntity(next))
                                  .getItem(0)
                                  .getCount()
                              == 5,
                      "Wall travel lost the mouth or its inventory");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 450, batch = "sac_trust")
  public static void connected_sac_meals_reward_trust_after_digestion(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    var tag = core.saveWithFullMetadata(level.registryAccess());
    tag.putInt("Trust", 20);
    tag.putInt("Satiety", 1200);
    core.loadAdditional(tag, level.registryAccess());
    var connected = core.getBlockPos().east();
    level.setBlock(connected, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, connected, null);
    Objects.requireNonNull(inventory).insertItem(0, new ItemStack(Items.BEEF), false);
    // A storage sac in ordinary world space is not part of this ship and must remain untouched.
    var loose = BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO)).south(16);
    level.setBlock(loose, block("biomancy:storage_sac").defaultBlockState(), 3);
    var detached = level.getCapability(Capabilities.ItemHandler.BLOCK, loose, null);
    Objects.requireNonNull(detached).insertItem(0, new ItemStack(Items.BEEF, 3), false);
    int before = core.saveWithFullMetadata(level.registryAccess()).getInt("Trust");
    DigestiveSystem.pullStoredFood(core, stomach);
    helper.assertTrue(
        core.saveWithFullMetadata(level.registryAccess()).getInt("Trust") == before,
        "Inventory transfer granted trust before eating");
    helper.runAfterDelay(
        350,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      core.saveWithFullMetadata(level.registryAccess()).getInt("Trust")
                          == before + 1,
                      "Digested provisioned food did not reward exactly one trust point");
                  helper.assertTrue(
                      detached.getStackInSlot(0).getCount() == 3,
                      "Food in an unconnected world sac was taken");
                  helper.assertTrue(
                      stomach.queuedFood() == 0 && inventory.getStackInSlot(0).isEmpty(),
                      "The provisioned meal did not finish digestion");
                  level.removeBlock(loose, false);
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 1500, batch = "resumed_growth")
  public static void existing_hull_parts_and_replaceable_grass_do_not_stall_growth(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    var parts = DinghyGrowth.body();
    int index = -1;
    for (int n = 0; n < parts.size(); n++)
      if (parts.get(n).state().is(Create_bioships.FLESH.get())
          && level.getBlockState(core.getBlockPos().offset(parts.get(n).offset())).isAir()) {
        index = n;
        break;
      }
    helper.assertTrue(index >= 0, "No flesh cell available for regression fixture");
    var cell = parts.get(index);
    level.setBlock(core.getBlockPos().offset(cell.offset()), cell.state(), 3);
    var saved = core.saveWithFullMetadata(level.registryAccess());
    saved.putInt("Stage", 1);
    saved.putBoolean("FramePaid", true);
    saved.putInt("GrowthIndex", index);
    core.loadAdditional(saved, level.registryAccess());
    var physical = BlockPos.containing(BioshipBlueprint.physical(core, new BlockPos(2, 0, -2)));
    level.setBlock(physical, Blocks.SHORT_GRASS.defaultBlockState(), 3);
    helper.assertTrue(
        BioshipIntegration.growthSpace(level, physical), "Replaceable grass blocks construction");
    var water = physical.east();
    level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
    helper.assertTrue(
        !BioshipIntegration.growthSpace(level, water),
        "Growth clearance treats water as replaceable air");
    level.removeBlock(water, false);
    final int start = index;
    helper.runAfterDelay(
        30,
        () ->
            helper.assertTrue(
                core.saveWithFullMetadata(level.registryAccess()).getInt("GrowthIndex") > start,
                "A correctly placed hull cell stalled the growth cursor"));
    helper.runAfterDelay(
        1300,
        () ->
            finish(
                helper,
                ship,
                () ->
                    helper.assertTrue(
                        core.isMature(),
                        "Resumed body failed to complete after encountering existing hull blocks: "
                            + diagnose(core))));
  }

  private static net.minecraft.world.phys.shapes.VoxelShape sableMembraneShape(
      ServerSubLevel ship, BlockPos pos, net.minecraft.world.entity.Entity entity) {
    try {
      var method =
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
      method.setAccessible(true);
      return (net.minecraft.world.phys.shapes.VoxelShape)
          method.invoke(
              null,
              entity,
              new org.joml.Vector3d(),
              ship.logicalPose(),
              ship.getLevel().getBlockState(pos),
              new dev.ryanhcode.sable.util.LevelAccelerator(ship.getLevel()),
              pos,
              null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot query the native Sable entity collision path", e);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 1300, batch = "interior_collision")
  public static void cockpit_clearance_eye_level_panel_and_real_membrane_collision(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    var owner = new InteractionPlayer(level, "InteriorOwner");
    var guest = new InteractionPlayer(level, "UninvitedGuest");
    core.claimCreator(owner.getUUID());
    helper.runAfterDelay(
        1100,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(core.isMature(), "Expanded cockpit did not finish growing");
                  helper.assertTrue(
                      level
                          .getBlockState(core.getBlockPos().offset(DinghyGrowth.PERCH))
                          .is(Create_bioships.PERCH.get()),
                      "Captain seat did not move forward");
                  for (int z = -1; z >= -2; z--)
                    for (int y = 2; y <= 3; y++)
                      helper.assertTrue(
                          level.getBlockState(core.getBlockPos().offset(0, y, z)).isAir(),
                          "Entrance aisle is blocked by the captain seat or a console");
                  var panel = core.layoutPos(DinghyGrowth.BLUEPRINT_PANEL);
                  var state = level.getBlockState(panel);
                  helper.assertTrue(
                      state.is(Create_bioships.BLUEPRINT_PANEL.get())
                          && state.getValue(BlueprintPanelBlock.FACING) == Direction.SOUTH,
                      "Blueprint panel is not mounted at eye level toward the engine room");
                  var hit =
                      new BlockHitResult(Vec3.atCenterOf(panel), Direction.SOUTH, panel, false);
                  state.useWithoutItem(level, owner, hit);
                  helper.assertTrue(
                      owner.containerMenu instanceof BlueprintMenu,
                      "Interior panel face did not open its editor");
                  owner.closeContainer();
                  state.useWithoutItem(
                      level,
                      owner,
                      new BlockHitResult(Vec3.atCenterOf(panel), Direction.NORTH, panel, false));
                  helper.assertTrue(
                      !(owner.containerMenu instanceof BlueprintMenu),
                      "Panel opened from its fleshy backing");
                  helper.assertTrue(
                      state.getCollisionShape(level, panel).bounds().getYsize() == 1,
                      "Wall panel has no solid backing");
                  BioshipAccess.tick(core);
                  var door = core.layoutPos(DinghyGrowth.DOOR);
                  helper.assertTrue(
                      sableMembraneShape(ship, door, owner).isEmpty(),
                      "Sable still collides with the owner despite membrane permissions");
                  helper.assertTrue(
                      !sableMembraneShape(ship, door, guest).isEmpty(),
                      "Uninvited player bypassed Sable membrane collision");
                  var point =
                      ship.logicalPose()
                          .transformPosition(Vec3.atLowerCornerOf(door).add(.5, 0, .5));
                  var outward = ship.logicalPose().transformNormal(new Vec3(0, 0, 1));
                  Vec3 start = point.add(outward.scale(1.5));
                  // Sable deliberately delegates ServerPlayer movement to the client. Exercise the
                  // same
                  // native collider with living entities here, and check player filtering above.
                  var allowed = new net.minecraft.world.entity.animal.Cow(EntityType.COW, level);
                  var denied = new net.minecraft.world.entity.animal.Cow(EntityType.COW, level);
                  allowed.setCustomName(net.minecraft.network.chat.Component.literal("CrewCow"));
                  core.access().animals.put(allowed.getUUID(), "CrewCow");
                  BioshipAccess.tick(core);
                  allowed.setPos(start.x, start.y + .02, start.z);
                  denied.setPos(start.x, start.y + .02, start.z);
                  allowed.move(MoverType.SELF, outward.scale(-3));
                  denied.move(MoverType.SELF, outward.scale(-3));
                  helper.assertTrue(
                      allowed.position().subtract(start).dot(outward) < -2.5,
                      "Authorized creature could not actually cross the membrane");
                  helper.assertTrue(
                      denied.position().subtract(start).dot(outward) > -1.5,
                      "The uninvited creature crossed the membrane");
                  var gate = (ShipMembraneAccess) level.getBlockEntity(door);
                  var access = core.access().save(core.creator());
                  access.putBoolean("Locked", true);
                  gate.bioships$access(access);
                  helper.assertTrue(
                      !sableMembraneShape(ship, door, owner).isEmpty(),
                      "Construction lock did not stop the owner");
                }));
  }

  @GameTest(template = "test_empty", batch = "window_legacy_layout")
  public static void window_edges_join_and_old_layout_controls_keep_their_positions(
      GameTestHelper helper) {
    var window = Create_bioships.ONE_WAY_WINDOW.get();
    var north = window.defaultBlockState();
    helper.assertTrue(
        window.skipRendering(north, north, Direction.UP)
            && window.skipRendering(north, north, Direction.EAST),
        "Aligned membrane windows do not join at shared edges");
    helper.assertTrue(
        !window.skipRendering(north, north, Direction.NORTH)
            && !window.skipRendering(
                north, north.setValue(OneWayWindowBlock.FACING, Direction.EAST), Direction.UP),
        "Windows hide an exposed or differently oriented face");
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var tag = core.saveWithFullMetadata(level.registryAccess());
    tag.putInt("BodyLayoutVersion", 5);
    tag.putInt("Stage", 1);
    tag.putInt("GrowthIndex", 20);
    core.loadAdditional(tag, level.registryAccess());
    helper.assertTrue(
        core.layoutPos(DinghyGrowth.HELM).equals(core.getBlockPos().offset(0, 2, -4))
            && core.layoutPos(DinghyGrowth.THROTTLE).equals(core.getBlockPos().offset(-1, 2, -2)),
        "Old ships' helm or throttle positions changed");
    helper.assertTrue(
        core.saveWithFullMetadata(level.registryAccess()).getInt("Stage") == 1
            && core.saveWithFullMetadata(level.registryAccess()).getInt("GrowthIndex") == 20,
        "An older in-progress ship lost construction progress");
    helper.assertTrue(
        DinghyGrowth.body(6).size() > DinghyGrowth.body(5).size(),
        "The new cockpit was not lengthened");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "exterior_wreck_recovery")
  public static void visible_wreck_windows_and_under_hull_tentacles_can_be_reclaimed(
      GameTestHelper helper) {
    var live = assemble(helper);
    var core = core(live);
    var level = helper.getLevel();
    var origin = BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO)).east(6);
    level.setBlock(origin, Create_bioships.CORE.get().defaultBlockState(), 3);
    var wreck =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            wreck, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    var dead = core(wreck);
    var window = dead.getBlockPos().west();
    var limb = dead.getBlockPos().north();
    level.setBlock(window, Create_bioships.ONE_WAY_WINDOW.get().defaultBlockState(), 3);
    level.setBlock(window.above(), Create_bioships.FLESH.get().defaultBlockState(), 3);
    level.setBlock(limb, Create_bioships.TENTACLE.get().defaultBlockState(), 3);
    level.setBlock(limb.above(), Create_bioships.FLESH.get().defaultBlockState(), 3);
    helper.assertTrue(
        BioshipScavenging.needed(core, level.getBlockState(window))
            && BioshipScavenging.needed(core, level.getBlockState(limb)),
        "Window or tentacle was excluded from salvage materials");
    helper.assertTrue(
        BioshipScavenging.needed(core, Create_bioships.BLUEPRINT_PANEL.get().defaultBlockState())
            && BioshipScavenging.needed(core, Create_bioships.THROTTLE.get().defaultBlockState()),
        "Bioship controls cannot be broken down for needed reserves");
    dead.body().die(dead);
    var corpseTag = wreck.getUserDataTag().copy();
    corpseTag.putLong("BioshipDecayAfter", level.getGameTime() + 500);
    wreck.setUserDataTag(corpseTag);
    helper.runAfterDelay(
        350,
        () -> {
          try {
            helper.assertTrue(
                core.scavenging().save().getInt("FiberRemainder") == 1,
                "Visible under-hull tentacle was never reclaimed");
            helper.assertTrue(
                core.missingBiomass() <= 62,
                "Exposed window and hull tissue were not reclaimed before decay");
            helper.assertTrue(
                level.getBlockState(window).isAir() && level.getBlockState(limb).isAir(),
                "Reachable exterior wreck components remain stuck below their roof");
            helper.succeed();
          } finally {
            cleanup(helper, wreck);
            cleanup(helper, live);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "early_supply_walking")
  public static void eye_walks_to_visible_components_and_requires_organs_for_drops(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    Vec3 here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var component = BlockPos.containing(here).east(10);
    level.setBlock(component, block("biomancy:bio_forge").defaultBlockState(), 3);
    var goal = core.gatheringGoal();
    helper.assertTrue(
        goal != null && goal.distanceToSqr(Vec3.atCenterOf(component)) < 1,
        "Eye cannot approach a visible distant component before growth");
    helper.runAfterDelay(
        20,
        () -> {
          core.crawl().tick(core, false);
          helper.assertTrue(
              core.crawl().walking() && core.crawl().gathering(),
              "Early supply gathering did not sprout walking legs");
          level.removeBlock(component, false);
          var drop =
              new net.minecraft.world.entity.item.ItemEntity(
                  level,
                  here.x + 1,
                  here.y,
                  here.z,
                  new ItemStack(item("biomancy:elastic_fibers"), 3));
          level.addFreshEntity(drop);
          core.gatheringGoal();
          helper.assertTrue(
              drop.getItem().getCount() == 3 && core.crawl().reach() == null,
              "Eye collected material without digestive organs");
          var stomach = digestive(helper, core, false);
          core.gatheringGoal();
          helper.assertTrue(
              drop.getItem().getCount() == 2
                  && stomach.material("biomancy:elastic_fibers") == 1
                  && core.crawl().reach() != null,
              "Connected mouth and stomach did not collect fibers with an arm");
          helper.assertTrue(
              LivingCoreBlockEntity.droppedSupply(new ItemStack(item("biomancy:bone_fragments")))
                  && LivingCoreBlockEntity.droppedSupply(new ItemStack(Items.BEEF)),
              "Food or bone growth stock was excluded");
          drop.setPos(here.x + 6, here.y, here.z);
          var meal =
              new net.minecraft.world.entity.item.ItemEntity(
                  level, here.x + 1, here.y, here.z, new ItemStack(Items.BEEF));
          level.addFreshEntity(meal);
          core.gatheringGoal();
          helper.assertTrue(
              !meal.isAlive()
                  && stomach.queuedFood() == 1
                  && stomach.material("minecraft:beef") == 0,
              "Dropped meat was stored as construction material instead of entering digestion");
          helper.assertTrue(
              !LivingCoreBlockEntity.droppedSupply(new ItemStack(Create_bioships.CORE.get()))
                  && LivingCoreBlockEntity.droppedSupply(
                      new ItemStack(Create_bioships.ONE_WAY_WINDOW.get()))
                  && LivingCoreBlockEntity.droppedSupply(
                      new ItemStack(block("biomancy:bio_forge"))),
              "Dropped block filter rejected supported blocks or allowed a bioship core");
          int missing = core.missingBiomass();
          var blocks =
              new net.minecraft.world.entity.item.ItemEntity(
                  level,
                  here.x + 1,
                  here.y,
                  here.z,
                  new ItemStack(Create_bioships.ONE_WAY_WINDOW.get(), 2));
          level.addFreshEntity(blocks);
          core.gatheringGoal();
          core.gatheringGoal();
          helper.assertTrue(
              !blocks.isAlive() && core.missingBiomass() == missing - 1,
              "Dropped bioship blocks were not reclaimed at reduced yield");
          var protectedCore =
              new net.minecraft.world.entity.item.ItemEntity(
                  level, here.x + 1, here.y, here.z, new ItemStack(Create_bioships.CORE.get()));
          level.addFreshEntity(protectedCore);
          core.gatheringGoal();
          helper.assertTrue(
              protectedCore.isAlive() && protectedCore.getItem().getCount() == 1,
              "Dropped core was consumed");
          protectedCore.discard();
          var savedMouth = core.mouthBlock();
          level.removeBlock(stomach.getBlockPos(), false);
          core.gatheringGoal();
          helper.assertTrue(drop.getItem().getCount() == 2, "Mouth alone collected loose supplies");
          level.setBlock(
              stomach.getBlockPos(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
          DigestiveSystem.connect(core, ship);
          level.removeBlock(savedMouth, false);
          core.gatheringGoal();
          helper.assertTrue(
              drop.getItem().getCount() == 2, "Stomach alone collected loose supplies");
          helper.assertTrue(
              !LivingCoreBlockEntity.droppedSupply(new ItemStack(Items.DIAMOND)),
              "Unrelated dropped items qualify as food");
          drop.discard();
          finish(helper, ship, () -> {});
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 450, batch = "all_attachment_decay")
  public static void fragments_detached_while_alive_inherit_death_and_all_materials_rot(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    BioshipIntegration.bind(ship);
    var p = core.getBlockPos().east();
    level.setBlock(p, Create_bioships.ONE_WAY_WINDOW.get().defaultBlockState(), 3);
    var fragment =
        SubLevelAssemblyHelper.assembleBlocks(level, p, List.of(p), new BoundingBox3i(p, p));
    fragment.setSplitFrom(ship, ship.logicalPose());
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            fragment, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    helper.assertTrue(!BioshipDecay.dead(fragment), "Live split fragment was killed prematurely");
    level.setBlock(core.getBlockPos().north(), Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        BioshipScavenging.needed(core, Blocks.STONE.defaultBlockState()),
        "Non-mod attached wreck blocks cannot be reclaimed");
    level.setBlock(
        core.getBlockPos().south(), Create_bioships.TENTACLE.get().defaultBlockState(), 3);
    level.setBlock(
        core.getBlockPos().west(), Create_bioships.BLUEPRINT_PANEL.get().defaultBlockState(), 3);
    core.body().die(core);
    helper.assertTrue(BioshipDecay.dead(fragment), "Fragment detached before death escaped decay");
    var resumed = fragment.getUserDataTag().copy();
    resumed.remove("BioshipDead");
    resumed.remove("BioshipDecayAfter");
    resumed.remove("BioshipCorpseRoot");
    fragment.setUserDataTag(resumed);
    var savedDeaths =
        BioshipDecay.Deaths.get(level)
            .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
    var restored = BioshipDecay.Deaths.load(savedDeaths, level.registryAccess());
    helper.assertTrue(
        restored
                .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())
                .getLong(BioshipDecay.lineage(ship).toString())
            > 0,
        "Death lineage did not survive saved-data reload");
    helper.runAfterDelay(
        80,
        () ->
            helper.assertTrue(
                BioshipDecay.dead(fragment), "A resumed fragment escaped its saved lineage death"));
    helper.runAfterDelay(
        400,
        () -> {
          try {
            for (var wreck : List.of(ship, fragment)) {
              if (wreck.isRemoved()) continue;
              var tag = wreck.getUserDataTag().copy();
              tag.putLong("BioshipDecayAfter", 0);
              wreck.setUserDataTag(tag);
            }
            for (int pulse = 0; pulse < 10; pulse++) BioshipDecay.decay(level);
            helper.assertTrue(
                fragment.isRemoved() || BioshipDecay.blocks(fragment).isEmpty(),
                "Detached window did not rot");
            helper.assertTrue(
                ship.isRemoved() || BioshipDecay.blocks(ship).isEmpty(),
                "Attached foreign block did not rot");
            helper.succeed();
          } finally {
            cleanup(helper, fragment);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "slow_decay_order")
  public static void decay_waits_then_preserves_organs_and_special_blocks_across_fragments(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    for (var entry : Create_bioships.BLOCKS.getEntries()) {
      var block = entry.get();
      if (block == Create_bioships.CORE.get() || block == Create_bioships.DOOR.get()) continue;
      helper.assertTrue(
          block.asItem() != Items.AIR, "Missing recoverable block item: " + entry.getId());
      var drops =
          block
              .defaultBlockState()
              .getDrops(
                  new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                      .withParameter(
                          net.minecraft.world.level.storage.loot.parameters.LootContextParams
                              .ORIGIN,
                          BioshipBlueprint.physical(core, BlockPos.ZERO))
                      .withParameter(
                          net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                          ItemStack.EMPTY));
      helper.assertTrue(
          drops.size() == 1
              && drops.getFirst().is(block.asItem())
              && drops.getFirst().getCount() == 1,
          "Incorrect recoverable block drop: " + entry.getId());
    }
    var hull = core.getBlockPos().north();
    var organ = core.getBlockPos().west();
    var window = core.getBlockPos().east();
    level.setBlock(hull, Create_bioships.FLESH.get().defaultBlockState(), 3);
    level.setBlock(organ, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    level.setBlock(window, Create_bioships.ONE_WAY_WINDOW.get().defaultBlockState(), 3);
    BioshipIntegration.bind(ship);
    var fragment =
        SubLevelAssemblyHelper.assembleBlocks(
            level, window, List.of(window), new BoundingBox3i(window, window));
    fragment.setSplitFrom(ship, ship.logicalPose());
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            fragment, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    var detachedWindow = fragment.getPlot().getCenterBlock();
    core.body().die(core);
    helper.assertTrue(
        ship.getUserDataTag().getLong("BioshipDecayAfter") == level.getGameTime() + 1200,
        "New wreck grace period is not one minute");
    BioshipDecay.decay(level);
    helper.assertTrue(
        level.getBlockState(hull).is(Create_bioships.FLESH.get()),
        "Hull decayed during grace period");
    for (var wreck : List.of(ship, fragment)) {
      var tag = wreck.getUserDataTag().copy();
      tag.putLong("BioshipDecayAfter", 0);
      wreck.setUserDataTag(tag);
    }
    BioshipDecay.decay(level);
    helper.assertTrue(level.getBlockState(hull).isAir(), "Ordinary hull did not rot first");
    helper.assertTrue(
        level.getBlockState(organ).is(Create_bioships.STOMACH.get())
            && level.getBlockState(detachedWindow).is(Create_bioships.ONE_WAY_WINDOW.get()),
        "An organ or detached special block rotted before the hull");
    BioshipDecay.decay(level);
    BioshipDecay.decay(level);
    helper.assertTrue(
        level.getBlockState(organ).isAir() && level.getBlockState(detachedWindow).isAir(),
        "Special components never decayed");
    helper.assertTrue(
        level.getBlockState(core.getBlockPos()).is(Create_bioships.CORE.get()),
        "Core did not survive the organ phase");
    BioshipDecay.decay(level);
    helper.assertTrue(level.getBlockState(core.getBlockPos()).isAir(), "Core did not rot last");
    cleanup(helper, fragment);
    finish(helper, ship, () -> {});
  }
}
