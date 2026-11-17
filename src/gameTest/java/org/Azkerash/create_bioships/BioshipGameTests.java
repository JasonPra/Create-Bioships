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
import org.Azkerash.create_bioships.amoeba.merging.ComponentMerging;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;
import org.Azkerash.create_bioships.creature.resources.MaterialLedger;
import org.Azkerash.create_bioships.development.AquaticLab;
import org.Azkerash.create_bioships.development.BioshipDiagnostics;
import org.Azkerash.create_bioships.development.EcosystemLab;
import org.Azkerash.create_bioships.development.EndHabitatLab;
import org.Azkerash.create_bioships.development.LabPopulation;
import org.Azkerash.create_bioships.development.LabStarters;
import org.Azkerash.create_bioships.development.NavigationLab;
import org.Azkerash.create_bioships.development.OceanLabTerrain;
import org.Azkerash.create_bioships.disabled.wip.AccessMenu;
import org.Azkerash.create_bioships.disabled.wip.AirshipPrototype;
import org.Azkerash.create_bioships.disabled.wip.BioshipBlueprint;
import org.Azkerash.create_bioships.disabled.wip.BioshipEngineRoomPlanner;
import org.Azkerash.create_bioships.disabled.wip.BlueprintMenu;
import org.Azkerash.create_bioships.disabled.wip.BlueprintPanelBlock;
import org.Azkerash.create_bioships.disabled.wip.DinghyGrowth;
import org.Azkerash.create_bioships.disabled.wip.EngineRoom;
import org.Azkerash.create_bioships.disabled.wip.EngineRoomLayout;
import org.Azkerash.create_bioships.disabled.wip.EngineRoomSizing;
import org.Azkerash.create_bioships.disabled.wip.LivingThrottleBlock;
import org.Azkerash.create_bioships.disabled.wip.NerveControlBlock;
import org.Azkerash.create_bioships.disabled.wip.TentacleBlockEntity;
import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.network.BioshipNetwork;
import org.Azkerash.create_bioships.network.NavigationDebug;
import org.Azkerash.create_bioships.network.VisionDebug;
import org.Azkerash.create_bioships.registry.Create_bioships;

@GameTestHolder(Create_bioships.MOD_ID)
@PrefixGameTestTemplate(false)
public class BioshipGameTests {
  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "end_fixture_geometry")
  public static void developer_end_habitat_has_remote_islands_biome_and_void_gap(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var destination = EndHabitatLab.prepare(level);
    helper.assertTrue(destination.getX() >= 4096, "End habitat is too close to other stations");
    helper.assertTrue(
        level
            .getBiome(EndHabitatLab.CENTER)
            .is(net.minecraft.world.level.biome.Biomes.END_HIGHLANDS),
        "End habitat has the wrong biome");
    helper.assertTrue(
        level.getBlockState(EndHabitatLab.CENTER).is(Blocks.END_STONE),
        "Central End island is absent");
    helper.assertTrue(
        level.getBlockState(EndHabitatLab.CENTER.offset(55, 0, 0)).isAir(),
        "Floating islands have no air gap");
    helper.assertTrue(
        level.getBlockState(EndHabitatLab.CENTER.offset(64, 0, 0)).is(Blocks.GLASS),
        "End enclosure has an open wall");
    helper.assertTrue(
        level.getBlockState(EndHabitatLab.CENTER.above(40)).is(Blocks.TINTED_GLASS),
        "End habitat lacks daylight shade");
    helper.assertTrue(
        !EndHabitatLab.inside(EndHabitatLab.CENTER.getX() + 65, 112, 0),
        "Escape destination counted as contained");
    helper.assertTrue(
        EndHabitatLab.prepare(level).equals(destination),
        "Repeat visit rebuilt the habitat at a different location");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "amoeba_host_contract")
  public static void amoeba_transport_uses_host_movement_state_and_preserves_its_save_format(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var delegate = core.amoebaComponents();
      var state = new java.util.concurrent.atomic.AtomicReference<ComponentTransport.Move>();
      var host =
          (org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost)
              java.lang.reflect.Proxy.newProxyInstance(
                  org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost.class
                      .getClassLoader(),
                  new Class<?>[] {
                    org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost.class
                  },
                  (proxy, method, args) -> {
                    if (method.getName().equals("movement")) {
                      if (args == null || args.length == 0) return state.get();
                      state.set((ComponentTransport.Move) args[0]);
                      return null;
                    }
                    try {
                      return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                      throw e.getCause();
                    }
                  });
      var level = helper.getLevel();
      var destination = core.getBlockPos().west();
      var source = destination.north();
      level.setBlock(destination, block("biomancy:decomposer").defaultBlockState(), 3);
      level.setBlock(source, block("biomancy:digester").defaultBlockState(), 3);
      var digester =
          (com.github.elenterius.biomancy.block.digester.DigesterBlockEntity)
              level.getBlockEntity(source);
      digester.getOutputInventory().setStackInSlot(0, new ItemStack(item("biomancy:nutrients"), 7));
      helper.assertTrue(
          !ComponentTransport.merge(host, source, destination, "missing_recipe")
              && level.getBlockEntity(source) == digester,
          "Invalid recipe detached or consumed its source");
      helper.assertTrue(
          ComponentTransport.merge(host, source, destination, "stomach"),
          "Alternate host could not start fusion");
      helper.assertTrue(
          core.organMove() == null && state.get() != null,
          "Transport used concrete-core movement state");
      var move = state.get();
      helper.assertTrue(
          move.equals(ComponentTransport.Move.load(move.save())),
          "Movement serialization changed during extraction");
      // Complete this host's elapsed transaction without letting another controller tick it.
      state.set(elapsedTransport(move));
      ComponentTransport.tick(host);
      var organ = host.component(ship, destination);
      helper.assertTrue(
          state.get() == null
              && core.organMove() == null
              && organ instanceof StomachBlockEntity stomach
              && stomach.material("biomancy:nutrients") == 7,
          "Alternate host did not commit fusion and preserve inventory");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  private static ComponentTransport.Move elapsedTransport(ComponentTransport.Move move) {
    return new ComponentTransport.Move(
        move.cargo(),
        move.cargoBlock(),
        move.source(),
        move.target(),
        move.start(),
        move.started() - ComponentMerging.DURATION_TICKS,
        move.external(),
        move.restoreFlesh(),
        move.mouth(),
        move.stomach(),
        move.manual(),
        move.ownerForced(),
        move.tissuePath());
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "shared_stomach_connection")
  public static void adjacent_stomachs_share_one_anchor_but_not_tubes_diagonals_or_broken_links(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var anchor = digestive(helper, core, false);
      var middle = anchor.getBlockPos().west();
      var end = middle.west();
      level.setBlock(middle, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      level.setBlock(end, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      var child = (StomachBlockEntity) level.getBlockEntity(end);
      helper.assertTrue(
          anchor.connected(core)
              && child.connected(core)
              && child.connectedMouth == null
              && child.gutPath.isEmpty(),
          "Connected stomach required its own mouth or intestine route");
      helper.assertTrue(
          DigestiveSystem.functional(core)
              && BioshipDigestiveGrowth.mouthTarget(core) == 1
              && BioshipDigestiveGrowth.plan(core) == null,
          "Shared group demanded redundant mouths");
      var diagonal = anchor.getBlockPos().east().south();
      level.setBlock(diagonal, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      var tube = anchor.getBlockPos().south();
      var separated = tube.south();
      level.setBlock(separated, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      level.setBlock(
          tube,
          Create_bioships.INTESTINE.get().defaultBlockState().setValue(IntestineBlock.MANUAL, true),
          3);
      helper.assertTrue(
          !((StomachBlockEntity) level.getBlockEntity(diagonal)).connected(core)
              && !((StomachBlockEntity) level.getBlockEntity(separated)).connected(core),
          "Diagonal or intestine-only stomach contact inherited an anchor");
      level.removeBlock(middle, false);
      helper.assertTrue(
          !child.connected(core), "Broken stomach bridge retained its old group connection");
      helper.assertTrue(!anchor.gutPath.isEmpty(), "Fixture had no external intestine connection");
      level.removeBlock(anchor.gutPath.getFirst(), false);
      helper.assertTrue(!anchor.connected(core), "Broken intake connection remained functional");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "many_mouths_one_stomach")
  public static void multiple_mouths_feed_one_stomach_without_growing_redundant_stomachs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var stomach = digestive(helper, core, false);
      var extra = stomach.getBlockPos().west();
      level.setBlock(
          extra,
          Create_bioships.FEEDING_MOUTH
              .get()
              .defaultBlockState()
              .setValue(FeedingMouthBlock.FACING, Direction.WEST),
          3);
      helper.assertTrue(
          DigestiveSystem.forMouth(core, extra) == stomach,
          "A directly connected extra mouth required an exclusive stomach assignment");
      helper.assertTrue(
          DigestiveSystem.offerFood(core, extra, new ItemStack(Items.BEEF), false)
              && stomach.queuedFood() == 1,
          "Additional intake did not deliver exactly one food item");
      helper.assertTrue(
          BioshipDigestiveGrowth.target(core) == 1 && BioshipDigestiveGrowth.plan(core) == null,
          "Additional mouth demanded another stomach");
      level.removeBlock(extra, false);
      helper.assertTrue(
          DigestiveSystem.forMouth(core, extra) == null,
          "Removed mouth retained a cached intake route");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "shared_stomach_food")
  public static void stomach_group_preserves_full_capacity_food_provenance_and_active_meals(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var first = digestive(helper, core, false);
      var next = first.getBlockPos().west();
      level.setBlock(next, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      var second = (StomachBlockEntity) level.getBlockEntity(next);
      for (int i = 0; i < 576; i++)
        helper.assertTrue(
            first.enqueue(new ItemStack(Items.BEEF), false),
            "Ordinary stomach lost its original nine-stack food capacity");
      helper.assertTrue(!first.enqueue(new ItemStack(Items.BEEF), false), "Full queue overfilled");
      helper.assertTrue(
          DigestiveSystem.offerFood(
                  core, core.getBlockPos().north(), new ItemStack(Items.CHICKEN), false)
              && second.queuedFood() == 1,
          "Full anchor blocked unused peer inventory capacity");
      int total = first.queuedFood() + second.queuedFood();
      helper.assertTrue(
          first.moveMealTo(second) && total == first.queuedFood() + second.queuedFood(),
          "Meal sharing lost or duplicated food");
      helper.assertTrue(
          first.queuedNutrition() + second.queuedNutrition() == total,
          "Fresh food became inherited during transfer");
      var old = next.west();
      level.setBlock(old, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      var inherited = (StomachBlockEntity) level.getBlockEntity(old);
      for (int i = 0; i < 8; i++) inherited.enqueue(new ItemStack(Items.PORKCHOP), true);
      helper.assertTrue(
          inherited.moveMealTo(second)
              && inherited.queuedNutrition() == 0
              && first.queuedNutrition() + second.queuedNutrition() == total,
          "Inherited food was credited as fresh after sharing");
      // Starting digestion must not make the reserved portion transferable.
      StomachBlockEntity.tick(level, old, level.getBlockState(old), inherited);
      int progress = inherited.menuData().get(1);
      int before = inherited.queuedFood() + second.queuedFood();
      inherited.moveMealTo(second);
      helper.assertTrue(
          inherited.menuData().get(1) == progress
              && inherited.queuedFood() > 0
              && before == inherited.queuedFood() + second.queuedFood(),
          "Sharing reset active digestion or removed its reserved ingredients");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "stomach_adjacency_layout")
  public static void isolated_stomach_moves_beside_network_without_losing_food(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var first = digestive(helper, core, false);
      var source = core.getBlockPos().east();
      level.setBlock(source, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      var second = (StomachBlockEntity) level.getBlockEntity(source);
      second.enqueue(new ItemStack(Items.BEEF), false);
      helper.assertTrue(
          org.Azkerash.create_bioships.amoeba.digestion.DigestiveLayoutPlanner.arrange(core),
          "Isolated stomach was not queued to join an existing network");
      var move = core.organMove();
      helper.assertTrue(
          move.source().equals(source),
          "Planner selected the network anchor instead of the isolated stomach");
      core.organMove(elapsedTransport(move));
      ComponentTransport.tick(core.amoebaComponents());
      var moved = (StomachBlockEntity) level.getBlockEntity(move.target());
      helper.assertTrue(
          moved != null
              && moved.queuedFood() == 1
              && moved.connected(core)
              && StomachConnections.members(core, first).size() == 2,
          "Relocation lost food or did not join the functioning stomach group");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "stomach_cube_formation")
  public static void existing_stomachs_are_arranged_into_a_solid_cube_and_breakage_unforms_it(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      digestive(helper, core, false);
      var origin = core.getBlockPos().west(3);
      var missing = origin.offset(0, 2, 2);
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) {
            var p = origin.offset(x, y, z);
            if (!p.equals(missing))
              level.setBlock(p, Create_bioships.STOMACH.get().defaultBlockState(), 3);
          }
      var source = core.getBlockPos().east();
      level.setBlock(source, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      var organ = (StomachBlockEntity) level.getBlockEntity(source);
      organ.enqueue(new ItemStack(Items.BEEF), false);
      helper.assertTrue(
          StomachMultiblock.formations(core).isEmpty(), "Incomplete cube formed early");
      helper.assertTrue(
          org.Azkerash.create_bioships.amoeba.digestion.DigestiveLayoutPlanner.arrange(core),
          "Available stomach was not queued to complete a solid cube");
      var move = core.organMove();
      helper.assertTrue(
          move.target().equals(missing), "Planner abandoned the nearly complete footprint");
      core.organMove(elapsedTransport(move));
      ComponentTransport.tick(core.amoebaComponents());
      var cubes = StomachMultiblock.formations(core);
      helper.assertTrue(
          cubes.size() == 1 && cubes.getFirst().members().size() == 27,
          "Arranged stomachs did not form a solid 27-block structure");
      helper.assertTrue(
          ((StomachBlockEntity) level.getBlockEntity(missing)).queuedFood() == 1,
          "Formation lost the moved stomach's food");
      level.removeBlock(origin.offset(1, 1, 1), false);
      helper.assertTrue(
          StomachMultiblock.formations(core).isEmpty(),
          "Missing center did not dissolve formation");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "whole_stomach_transport")
  public static void formed_stomach_moves_as_one_cargo_and_preserves_every_queue_and_progress(
      GameTestHelper helper) {
    wholeStomachMove(helper, false);
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "whole_stomach_rollback")
  public static void blocked_whole_stomach_move_restores_every_member_to_its_source(
      GameTestHelper helper) {
    wholeStomachMove(helper, true);
  }

  private static void wholeStomachMove(GameTestHelper helper, boolean obstruct) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var anchor = digestive(helper, core, false);
      var origin = core.getBlockPos().west(3);
      level.setBlock(core.getBlockPos().above(), BioshipTissue.body().defaultBlockState(), 3);
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) {
            var p = origin.offset(x, y, z);
            if (!p.equals(anchor.getBlockPos()))
              level.setBlock(p, Create_bioships.STOMACH.get().defaultBlockState(), 3);
            var organ = (StomachBlockEntity) level.getBlockEntity(p);
            organ.enqueue(new ItemStack(Items.BEEF), false);
            var data = organ.saveWithFullMetadata(level.registryAccess());
            data.putInt("Progress", 7);
            data.putInt("ActiveSlot", 0);
            data.putInt("Duration", 100);
            organ.loadWithComponents(data, level.registryAccess());
          }
      anchor.creditLocal("biomancy:flesh_bits", 64);
      anchor.creditLocal("biomancy:elastic_fibers", 64);
      helper.assertTrue(
          ComponentTransport.begin(core.amoebaComponents(), origin, origin.above(), false),
          "Whole stomach could not start a validated coordinated move");
      var move = core.organMove();
      var cargo = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
      helper.assertTrue(
          DigestiveSystem.stomachs(core).isEmpty()
              && BioshipIntegration.blockEntities(cargo).stream()
                      .filter(e -> e instanceof StomachBlockEntity)
                      .count()
                  == 27,
          "Structure was moved one member at a time or copied into both bodies");
      var blocked = origin.offset(0, 3, 0);
      if (obstruct) level.setBlock(blocked, Blocks.OBSIDIAN.defaultBlockState(), 3);
      core.organMove(elapsedTransport(move));
      ComponentTransport.tick(core.amoebaComponents());
      var expected = obstruct ? origin : origin.above();
      helper.assertTrue(
          core.organMove() == null && StomachMultiblock.formations(core).size() == 1,
          "Group commit or rollback left a partial structure");
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) {
            var organ = (StomachBlockEntity) level.getBlockEntity(expected.offset(x, y, z));
            helper.assertTrue(
                organ != null && organ.queuedFood() == 1 && organ.menuData().get(1) == 7,
                "Group commit lost a member's queue or digestion progress");
          }
      helper.assertTrue(
          DigestiveSystem.stomachs(core).stream().allMatch(s -> s.connected(core)),
          "Relocated structure could not restore its digestive connection");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "stomach_ports")
  public static void formed_stomach_exposes_one_intake_and_panel_and_resets_roles_on_breakage(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      digestive(helper, core, false);
      var origin = core.getBlockPos().west(3);
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) {
            var p = origin.offset(x, y, z);
            if (!level.getBlockState(p).is(Create_bioships.STOMACH.get()))
              level.setBlock(p, Create_bioships.STOMACH.get().defaultBlockState(), 3);
          }
      StomachPorts.refresh(core);
      var organs = DigestiveSystem.stomachs(core);
      helper.assertTrue(
          organs.stream()
                      .filter(
                          s ->
                              s.getBlockState().getValue(StomachBlock.ROLE)
                                  == StomachBlock.Role.INTAKE)
                      .count()
                  == 1
              && organs.stream()
                      .filter(
                          s ->
                              s.getBlockState().getValue(StomachBlock.ROLE)
                                  == StomachBlock.Role.PANEL)
                      .count()
                  == 1,
          "Formation did not expose exactly one intake and one player panel");
      for (var organ : organs) {
        var state = organ.getBlockState();
        if (state.getValue(StomachBlock.ROLE) == StomachBlock.Role.MEMBER) continue;
        var outside = organ.getBlockPos().relative(state.getValue(StomachBlock.FACING));
        helper.assertTrue(
            !StomachMultiblock.containing(core, organ.getBlockPos())
                .contains(StomachMultiblock.cell(outside)),
            "A port faced inward into another stomach member");
      }
      level.removeBlock(origin.offset(1, 1, 1), false);
      StomachPorts.refresh(core);
      helper.assertTrue(
          DigestiveSystem.stomachs(core).stream()
              .allMatch(
                  s -> s.getBlockState().getValue(StomachBlock.ROLE) == StomachBlock.Role.NORMAL),
          "Dismantled structure retained stale intake/panel roles");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "large_stomach_capacity_ui")
  public static void large_stomach_keeps_all_243_slots_and_pages_without_duplicate_shared_resources(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var anchor = digestive(helper, core, false);
      var origin = core.getBlockPos().west(3);
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) {
            var p = origin.offset(x, y, z);
            if (!level.getBlockState(p).is(Create_bioships.STOMACH.get()))
              level.setBlock(p, Create_bioships.STOMACH.get().defaultBlockState(), 3);
            var organ = (StomachBlockEntity) level.getBlockEntity(p);
            for (int slot = 0; slot < 9; slot++)
              helper.assertTrue(
                  organ.receiveMeal(new ItemStack(Items.BEEF, 64), false),
                  "A member lost queue capacity when forming");
          }
      anchor.creditLocal("biomancy:flesh_bits", 10);
      var view = new StomachInventoryView(anchor);
      var data = view.data();
      java.util.function.IntUnaryOperator value =
          i -> data.get(i * 2) & 65535 | (data.get(i * 2 + 1) & 65535) << 16;
      helper.assertTrue(
          value.applyAsInt(6) == 15552
              && value.applyAsInt(9) == 15552
              && value.applyAsInt(10) == 27
              && value.applyAsInt(11) == 1
              && view.pages() == 9,
          "Aggregate inventory did not retain 27 ordinary stomachs of capacity");
      helper.assertTrue(value.applyAsInt(3) == 10, "Shared products were counted once per stomach");
      for (int page = 0; page < 9; page++) {
        for (int slot = 0; slot < 27; slot++)
          helper.assertTrue(
              view.getItem(slot).getCount() == 64,
              "An aggregate inventory page did not expose a real member queue");
        if (page < 8) helper.assertTrue(view.turn(1), "Could not reach every inventory page");
      }
      helper.assertTrue(
          !view.turn(1) && view.removeItem(0, 64).isEmpty(),
          "Read-only view allowed extraction or exceeded its page bounds");
      var copy = view.getItem(0);
      copy.setCount(1);
      helper.assertTrue(
          view.getItem(0).getCount() == 64, "Display copies mutated authoritative food");
      StomachBlockEntity.tick(level, anchor.getBlockPos(), anchor.getBlockState(), anchor);
      int expected =
          org.Azkerash.create_bioships.creature.digestion.StomachFormation.workUnits(
              level.getGameTime(), true);
      helper.assertTrue(
          anchor.menuData().get(1) == expected, "Formed stomach did not apply its work-rate bonus");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

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

  private static void survey(LivingCoreBlockEntity core) {
    for (var face : Direction.values()) {
      core.vision().look(core, face);
      BioshipPriorities.bestWorld(core);
    }
    var best = BioshipPriorities.bestWorld(core);
    if (best != null) {
      var delta =
          core.ship()
              .logicalPose()
              .transformPositionInverse(Vec3.atCenterOf(best.pos()))
              .subtract(Vec3.atCenterOf(core.getBlockPos()));
      core.vision().look(core, Direction.getNearest(delta.x, delta.y, delta.z));
    }
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

  private static long paidIntestinalMaterial(LivingCoreBlockEntity core) {
    long physical =
        BioshipDecay.blocks(core.ship()).stream()
            .filter(
                p -> {
                  var state = core.getLevel().getBlockState(p);
                  return state.is(Create_bioships.INTESTINE.get())
                      && state.getValue(IntestineBlock.PAID);
                })
            .count();
    return physical + DigestiveSystem.stomachs(core).stream().mapToInt(s -> s.paidTubeStock).sum();
  }

  private static StomachBlockEntity digestive(
      GameTestHelper helper, LivingCoreBlockEntity core, boolean frame) {
    var level = helper.getLevel();
    level.setBlock(
        core.getBlockPos().north(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(core.getBlockPos().west(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var stomach = (StomachBlockEntity) level.getBlockEntity(core.getBlockPos().west());
    DigestiveSystem.connect(core, core.ship());
    stomach.paidTubeStock =
        32; // Fixture supplies prepaid material for later organ/storage routing.
    if (frame) {
      stomach.retainFlesh(140); // Hull, leg upgrades, sensory growth and emergency reserve.
      stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 20));
      stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 54));
      stomach.continueGrowth(core);
      var saved = core.saveWithFullMetadata(level.registryAccess());
      saved.putInt("Satiety", 1200);
      saved.putInt("Trust", 100);
      core.loadAdditional(saved, level.registryAccess());
    }
    return stomach;
  }

  private static void afterIndexed(
      GameTestHelper helper,
      List<? extends net.minecraft.world.entity.Entity> entities,
      Runnable action) {
    if (entities.stream()
        .allMatch(
            e ->
                helper
                    .getLevel()
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.Entity.class, e.getBoundingBox().inflate(.1))
                    .contains(e))) {
      action.run();
    } else helper.runAfterDelay(1, () -> afterIndexed(helper, entities, action));
  }

  private static void afterAttackSurfaceIndexed(
      GameTestHelper helper,
      LivingCoreBlockEntity core,
      java.util.function.Consumer<BioshipTargetEntity> action) {
    var current =
        BioshipTargetEntity.surfaces(core).stream()
            .filter(
                e ->
                    helper
                        .getLevel()
                        .getEntitiesOfClass(
                            BioshipTargetEntity.class, e.getBoundingBox().inflate(.1))
                        .contains(e))
            .max(java.util.Comparator.comparingDouble(e -> e.position().y))
            .orElse(null);
    if (current != null) action.accept(current);
    else helper.runAfterDelay(1, () -> afterAttackSurfaceIndexed(helper, core, action));
  }

  private static void enableFlight(GameTestHelper helper, LivingCoreBlockEntity core) {
    var mineral = new ItemStack(item("aeronautics:levitite_blend_bucket"), 8);
    for (int i = 0; i < 8; i++)
      helper.assertTrue(
          core.feedAtMouth(player(helper, "Captain"), mineral), "Amalgam was rejected");
    helper.assertTrue(mineral.isEmpty(), "Amalgam was duplicated");
    var pos = core.layoutPos(DinghyGrowth.VERTICAL_TRIM);
    helper
        .getLevel()
        .setBlock(
            pos,
            helper.getLevel().getBlockState(pos).setValue(LivingThrottleBlock.THROTTLE, 20),
            3);
  }

  private static void hunt(GameTestHelper helper, LivingCoreBlockEntity core, boolean enabled) {
    var pos = core.layoutPos(DinghyGrowth.HUNT);
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
      var pos = core.layoutPos(part.offset());
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

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "remembered_target_debug")
  public static void debug_names_the_remembered_machine_until_it_is_visually_rechecked(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var origin =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var source = origin.east(4);
    var machine = block("biomancy:bio_forge");
    level.setBlock(source, machine.defaultBlockState(), 3);
    helper.assertTrue(
        core.vision().sourceState(core, source) != null,
        "Visible fixture machine was not observed");
    var wall = new ArrayList<BlockPos>();
    for (var p : BlockPos.betweenClosed(origin.offset(2, 0, -2), origin.offset(2, 2, 2))) {
      wall.add(p.immutable());
      level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    }
    level.removeBlock(source, false);
    core.supplyTarget = source;
    try {
      helper.assertTrue(
          !core.vision().visible(core, Vec3.atCenterOf(source)),
          "Fixture did not hide the changed target");
      helper.assertTrue(
          core.vision().sourceState(core, source) != null,
          "Pursuit forgot a target without seeing its disappearance");
      var decision = core.brain().choose(core);
      helper.assertTrue(source.equals(decision.block()), "Remembered target was not selected");
      var before = core.saveWithFullMetadata(level.registryAccess());
      var debug = NavigationDebug.inspect(core);
      helper.assertTrue(
          debug.getString("TargetLabel").equals(machine.getName().getString()),
          "Debugger mislabeled the remembered machine as air: " + debug);
      // NBT DoubleTag equality treats the unchanged NaN idle stance as unequal to itself.
      helper.assertTrue(
          before.toString().equals(core.saveWithFullMetadata(level.registryAccess()).toString()),
          "Debugger modified target memory");
      wall.forEach(p -> level.removeBlock(p, false));
      helper.assertTrue(
          core.vision().sourceState(core, source) == null,
          "Seeing the empty location did not invalidate the target");
      helper.assertTrue(
          !source.equals(core.brain().choose(core).block()),
          "Confirmed-gone machine remained selected");
      helper.succeed();
    } finally {
      wall.forEach(p -> level.removeBlock(p, false));
      level.removeBlock(source, false);
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "off_center_goal_reach")
  public static void route_cannot_finish_at_a_snapped_position_when_real_grasp_is_blocked(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var initial = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var origin = BlockPos.containing(initial);
    // A legal starting stance between block centers, as produced by ordinary physics movement.
    ship.logicalPose().position().add(-.49, .35, -.49);
    var actual = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var snapped = new Vec3(origin.getX() + .5, origin.getY() + .85, origin.getZ() + .5);
    var goal = new Vec3(origin.getX() + 3.5, origin.getY() + .325, origin.getZ() + 1.9);
    var corner = origin.east(2);
    level.setBlock(corner, Blocks.GLASS.defaultBlockState(), 3);
    var state = core.crawl().save();
    state.putBoolean("Gathering", true);
    core.crawl().load(state);
    try {
      helper.assertTrue(
          BioshipReach.at(core, snapped, goal, true),
          "Fixture's hypothetical tile center cannot reach the item");
      helper.assertTrue(
          !BioshipReach.clear(core, goal, true),
          "Fixture's actual grasp is not blocked by the glass corner");
      var route = new GroundRoute();
      helper.assertTrue(route.plan(core, goal), "No route around the glass corner was found");
      helper.assertTrue(
          !route.points().isEmpty(),
          "Route falsely completed with zero waypoints by testing a hypothetical grid center"
              + " instead of the actual body: "
              + actual);
      helper.assertTrue(
          BioshipReach.at(core, route.points().getLast(), goal, true),
          "Final waypoint does not provide a real grasp");
      var reachable = actual.add(0, 0, 2);
      helper.assertTrue(
          BioshipReach.clear(core, reachable, true),
          "Nearby control target is not physically reachable");
      helper.assertTrue(
          route.plan(core, reachable)
              && route.points().isEmpty()
              && route.debug(level.getGameTime()).getString("Status").equals("in_action_range"),
          "A genuinely reachable target should need no movement and report action range");
      helper.succeed();
    } finally {
      level.removeBlock(corner, false);
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "path_debug_snapshot")
  public static void path_debug_exposes_actual_route_priorities_and_round_trips_without_replanning(
      GameTestHelper helper) {
    var ship = assemble(helper);
    helper.getLevel().setDayTime(1000);
    helper.runAfterDelay(
        40,
        () -> {
          var core = core(ship);
          var level = helper.getLevel();
          var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
          var source = BlockPos.containing(root).east(12);
          level.setBlock(source, block("biomancy:bio_forge").defaultBlockState(), 3);
          survey(core);
          core.brain().choose(core);
          var goal = Vec3.atCenterOf(source);
          var crawl = core.crawl().save();
          crawl.putString("Mode", "forage");
          crawl.putBoolean("Walking", true);
          crawl.putBoolean("Gathering", true);
          crawl.putBoolean("Goal", true);
          crawl.putDouble("X", goal.x);
          crawl.putDouble("Y", goal.y);
          crawl.putDouble("Z", goal.z);
          core.crawl().load(crawl);
          core.crawl().velocity(core);
          var before = core.saveWithFullMetadata(level.registryAccess());
          var route = core.crawl().path();
          var data = NavigationDebug.inspect(core);
          helper.assertTrue(
              data.hasUUID("Ship") && data.getUUID("Ship").equals(ship.getUniqueId()),
              "Path snapshot selected another body");
          helper.assertTrue(
              !route.isEmpty()
                  && data.getList("Route", net.minecraft.nbt.Tag.TAG_COMPOUND).size()
                      == route.size(),
              "Debug did not expose the actual remaining route");
          helper.assertTrue(
              data.getString("Action").equals("ASSIMILATE")
                  && data.contains("Target")
                  && data.contains("Goal"),
              "Behavior target and movement goal were omitted");
          helper.assertTrue(
              !data.getList("Priorities", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
              "Actual priority ranking was omitted");
          helper.assertTrue(
              before.equals(core.saveWithFullMetadata(level.registryAccess()))
                  && route.equals(core.crawl().path()),
              "Observing debug state changed the behavior or navigation");
          var buffer =
              new net.minecraft.network.RegistryFriendlyByteBuf(
                  io.netty.buffer.Unpooled.buffer(),
                  level.registryAccess(),
                  net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
          try {
            BioshipNetwork.PathSnapshot.CODEC.encode(buffer, new BioshipNetwork.PathSnapshot(data));
            helper.assertTrue(
                BioshipNetwork.PathSnapshot.CODEC.decode(buffer).data().equals(data),
                "Path snapshot lost route or priorities in transit");
          } finally {
            buffer.release();
          }
          var observer = player(helper, "PathDebugObserver");
          observer.setPos(root.x, root.y, root.z);
          observer.getPersistentData().remove("PathRequested");
          helper.assertTrue(
              NavigationDebug.request(observer).hasUUID("Ship"),
              "Developer path debug incorrectly requires goggles");
          helper.assertTrue(
              NavigationDebug.request(observer) == null, "Path requests were not rate-limited");
          level.removeBlock(source, false);
          finish(helper, ship, () -> {});
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "path_debug_blocked")
  public static void path_debug_reports_unreachable_target_inside_sealed_glass(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var origin = BlockPos.containing(root);
    var walls = new ArrayList<BlockPos>();
    for (var p : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 4, 2)))
      if (Math.abs(p.getX() - origin.getX()) == 2
          || Math.abs(p.getZ() - origin.getZ()) == 2
          || p.getY() == origin.getY() + 4) {
        walls.add(p.immutable());
        level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
      }
    var goal = root.add(12, 0, 0);
    var crawl = core.crawl().save();
    crawl.putString("Mode", "forage");
    crawl.putBoolean("Walking", true);
    crawl.putBoolean("Gathering", true);
    crawl.putBoolean("Goal", true);
    crawl.putDouble("X", goal.x);
    crawl.putDouble("Y", goal.y);
    crawl.putDouble("Z", goal.z);
    core.crawl().load(crawl);
    core.crawl().velocity(core);
    var data = NavigationDebug.inspect(core);
    helper.assertTrue(
        data.getBoolean("Failed") && data.getString("Status").equals("no_route"),
        "An unreachable route was presented as idle or following: " + data);
    helper.assertTrue(
        data.getList("Route", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
        "Debugger fabricated a path through sealed glass");
    walls.forEach(p -> level.removeBlock(p, false));
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 240, batch = "natural_ship_chunk_loading")
  public static void bioship_loads_view_range_and_ticks_simulation_range(GameTestHelper helper) {
    var level = helper.getLevel();
    var players = level.getServer().getPlayerList();
    int previousView = players.getViewDistance(),
        previousSimulation = players.getSimulationDistance();
    players.setViewDistance(3);
    players.setSimulationDistance(2);
    var ship = assemble(helper);
    var core = core(ship);
    core.chunkLoading.update(core);
    var root =
        new ChunkPos(
            BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)));
    var near = new ChunkPos(root.x + 2, root.z);
    var far = new ChunkPos(root.x + 3, root.z);
    helper.succeedWhen(
        () -> {
          var chunks = level.getChunkSource();
          helper.assertTrue(
              chunks.getChunkNow(far.x, far.z) != null,
              "The normal bioship loader did not load its view-distance edge");
          helper.assertTrue(
              chunks.chunkMap.getDistanceManager().inEntityTickingRange(near.toLong()),
              "The normal bioship loader did not simulate its inner range");
          helper.assertTrue(
              !chunks.chunkMap.getDistanceManager().inEntityTickingRange(far.toLong()),
              "View-only chunks are being simulated beyond the simulation-distance limit");
          helper.assertTrue(
              core.chunkLoading.viewDistance() == 3 && core.chunkLoading.simulationDistance() == 2,
              "Bioship ignored the server distance limits");
          core.body().die(core);
          helper.assertTrue(!core.chunkLoading.active(), "Death retained bioship loading tickets");
          cleanup(helper, ship);
          players.setViewDistance(previousView);
          players.setSimulationDistance(previousSimulation);
        });
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

  @DeferredAirshipTest(template = "test_empty", batch = "physical_controls")
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1700, batch = "grown_flight")
  public static void ovalWorkshopGrowsGroundedThenFliesWithNativeHelm(GameTestHelper helper) {
    var ship = assemble(helper);
    digestive(helper, core(ship), true);
    helper
        .getLevel()
        .setBlock(
            core(ship).getBlockPos().south(), block("biomancy:storage_sac").defaultBlockState(), 3);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            var level = helper.getLevel();
            helper.assertTrue(
                core(ship).isMature(), "Oval body did not finish growing: " + diagnose(core(ship)));
            helper.assertTrue(!core(ship).canFly(), "Biomass supplied flight without amalgam");
            helper.assertTrue(core(ship).swimmingFlagella() == 6, "Flagella did not grow");
            helper.assertTrue(
                EngineRoom.inside(core(ship), DigestiveSystem.stomach(core(ship)).getBlockPos()),
                "Stomach did not move to engine room");
            for (var organ : BioshipIntegration.blockEntities(ship))
              if (NativeStorage.isStorage(organ))
                helper.assertTrue(
                    EngineRoom.inside(core(ship), organ.getBlockPos()),
                    "Storage sac stayed outside the engine room");
            helper.assertTrue(
                !DigestiveSystem.stomach(core(ship)).sacPaths.isEmpty(),
                "Engine storage has no floor tubes");
            helper.assertTrue(
                level
                    .getBlockState(core(ship).structuralOrigin().offset(DinghyGrowth.DOOR))
                    .is(block("biomancy:biometric_membrane")),
                "Rear membrane missing");
            helper.assertTrue(
                level
                    .getBlockState(core(ship).structuralOrigin().offset(0, 2, 0))
                    .is(block("biomancy:biometric_membrane")),
                "Workshop divider missing");
            helper.assertTrue(
                level
                    .getBlockState(core(ship).structuralOrigin().offset(3, 3, 2))
                    .is(Create_bioships.ONE_WAY_WINDOW.get()),
                "One-way window missing");
            helper.assertTrue(
                DinghyGrowth.radius(8) < DinghyGrowth.radius(0), "Hull has no oval taper");
            var helm =
                (SteeringWheelBlockEntity)
                    level.getBlockEntity(core(ship).structuralOrigin().offset(DinghyGrowth.HELM));
            helper.assertTrue(
                helm != null && helm.material.is(Blocks.CRIMSON_PLANKS),
                "Native helm lost its material");
            enableFlight(helper, core(ship));
            var control = core(ship).structuralOrigin().offset(DinghyGrowth.THROTTLE);
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
                        core(ship).canFly() && ship.logicalPose().position().z() < start - .1,
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
                                                core(ship).structuralOrigin().offset(root), false);
                                          helper.assertTrue(
                                              core(ship).swimmingFlagella() == 0,
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1400, batch = "steady_lights")
  public static void cabinLanternsStaySteadyWhileTheEyeBlinks(GameTestHelper helper) {
    var ship = assemble(helper);
    digestive(helper, core(ship), true);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            helper.assertTrue(core(ship).isMature(), "Cabin did not grow: " + diagnose(core(ship)));
            var level = helper.getLevel();
            var lamp = core(ship).structuralOrigin().offset(DinghyGrowth.CABIN_LIGHTS.getFirst());
            var initial = level.getBlockState(lamp);
            int brightness = initial.getLightEmission(level, lamp);
            helper.assertTrue(
                initial.is(DinghyGrowth.cabinLantern().getBlock()) && brightness > 0,
                "Not a native Bio-Lantern");
            final boolean[] eyes = {false, false};
            int until = Math.floorMod(-level.getGameTime() - core(ship).getBlockPos().getZ(), 120);
            helper.runAfterDelay(
                until + 1,
                () ->
                    eyes[0] =
                        level
                            .getBlockState(core(ship).getBlockPos())
                            .getValue(LivingCoreBlock.BLINK));
            helper.runAfterDelay(
                until + 30,
                () ->
                    eyes[1] =
                        !level
                            .getBlockState(core(ship).getBlockPos())
                            .getValue(LivingCoreBlock.BLINK));
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 100, batch = "growth_obstruction")
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
                      saved.getInt("Biomass") + saved.getInt("GoggleFlesh") >= 3,
                      "Native decomposition did not supply flesh");
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
          ComponentTransport.busy(core.amoebaComponents()) && !core.organMove().usesArm(),
          "Adjacent stomach parts skipped the amoeba merge transaction");
      // Sample inventory at the actual fusion boundary, before normal growth can spend it.
      core.organMove(elapsedTransport(core.organMove()));
      ComponentTransport.tick(core.amoebaComponents());
      {
        helper.assertTrue(
            level.getBlockEntity(forgePos) instanceof FeedingMouthBlockEntity
                && level.getBlockEntity(decomposerPos) instanceof StomachBlockEntity,
            "First machines did not become unique organs");
        BlockPos extraForge = core.getBlockPos().east(),
            extraDecomposer = core.getBlockPos().south();
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
            stomach.material("biomancy:bone_fragments") == 12
                && stomach.material("biomancy:elastic_fibers") == 20,
            "Existing decomposition products were lost");
        helper.assertTrue(
            stomach.material("biomancy:nutrients") == 3
                && stomach.queuedFood() == 4
                && stomach.nutrients() == 9,
            "Digester contents were not preserved: products="
                + stomach.material("biomancy:nutrients")
                + " queued="
                + stomach.queuedFood()
                + " fuel="
                + stomach.nutrients());
        var extraDigester = extraDecomposer.east();
        level.setBlock(extraDigester, block("biomancy:digester").defaultBlockState(), 3);
        BioshipIntegration.finishAssimilation(core, extraDigester);
        helper.assertTrue(
            level.getBlockEntity(extraDigester)
                instanceof com.github.elenterius.biomancy.block.digester.DigesterBlockEntity,
            "A second digester was consumed");
        DigestiveSystem.connect(core, ship);
        helper.runAfterDelay(
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
                          total
                                  + 4
                                      * BioshipStructure.cells(core.ship()).values().stream()
                                          .filter(BioshipLegEvolution::leg)
                                          .count()
                                  + BioshipRespiration.LUNG_COST.flesh()
                                      * BioshipStructure.cells(core.ship()).values().stream()
                                          .filter(state -> state.is(Create_bioships.LUNGS.get()))
                                          .count()
                                  + 2
                                      * BioshipStructure.cells(core.ship()).values().stream()
                                          .filter(
                                              state -> state.is(Create_bioships.SURFACE_EYE.get()))
                                          .count()
                              == 96,
                          "Stored Bio-Forge biomass or one-time leg salvage was lost: " + total);
                      helper.assertTrue(
                          core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety")
                              <= 600,
                          "Inherited tissue supplied nutrition");
                    }));
      }
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
    survey(core);
    BioshipIntegration.assimilate(core, ship);
    helper.assertTrue(
        !core.assimilation().arms().isEmpty() && core.assimilation().arms().getFirst().external(),
        "No tentacle pull was started");
    var cargo =
        SubLevelContainer.getContainer(helper.getLevel())
            .getSubLevel(core.assimilation().arms().getFirst().cargo());
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
                    if (NativeStorage.isStorage(entity)) stored = entity.getBlockPos();
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

  @DeferredAirshipTest(template = "test_empty", batch = "biometric_access")
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
    helper.getLevel().setBlock(target, BioshipTissue.body().defaultBlockState(), 3);
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1600, batch = "hunting_digestion")
  public static void huntingPreyMustBeDigestedAndSkeletonsAreNotFood(GameTestHelper helper) {
    var ship = assemble(helper);
    digestive(helper, core(ship), true);
    helper.runAtTickTime(
        1000,
        () -> {
          try {
            helper.assertTrue(
                core(ship).isMature(), "Body did not mature: " + diagnose(core(ship)));
            enableFlight(helper, core(ship));
            hunt(helper, core(ship), true);
            helper.runAfterDelay(
                100,
                () -> {
                  try {
                    helper.assertTrue(
                        core(ship).mouthBlock() != null
                            && !EngineRoom.inside(core(ship), core(ship).mouthBlock()),
                        "Hunting mouth stayed inside");
                    var saved = core(ship).saveWithFullMetadata(helper.getLevel().registryAccess());
                    saved.putInt("Satiety", 100);
                    core(ship).loadAdditional(saved, helper.getLevel().registryAccess());
                    var chicken = EntityType.CHICKEN.create(helper.getLevel());
                    chicken.setNoAi(true);
                    chicken.setNoGravity(true);
                    var meal = core(ship).mouthPosition(ship).add(0, -.3, -.3);
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
                                      "Tentacles did not capture and consume prey: alive="
                                          + chicken.isAlive()
                                          + " removed="
                                          + chicken.getRemovalReason()
                                          + " indexed="
                                          + helper
                                              .getLevel()
                                              .getEntitiesOfClass(
                                                  net.minecraft.world.entity.animal.Chicken.class,
                                                  chicken.getBoundingBox().inflate(12))
                                              .size()
                                          + " hungry="
                                          + core(ship).hungry()
                                          + " wants="
                                          + core(ship).wantsToHunt()
                                          + " stomach="
                                          + DigestiveSystem.stomach(core(ship))
                                              .saveWithFullMetadata(
                                                  helper.getLevel().registryAccess()));
                                  helper.assertTrue(
                                      DigestiveSystem.stomachs(core(ship)).stream()
                                                  .mapToInt(StomachBlockEntity::meals)
                                                  .sum()
                                              > 0
                                          && DigestiveSystem.stomach(core(ship))
                                                  .material("biomancy:flesh_bits")
                                              > 0,
                                      "Prey did not pass through digestion: "
                                          + DigestiveSystem.stomach(core(ship))
                                              .saveWithFullMetadata(
                                                  helper.getLevel().registryAccess()));
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 2200, batch = "later_larynx")
  public static void continuedDigestionDevelopsOneModularLarynx(GameTestHelper helper) {
    var ship = assemble(helper);
    digestive(helper, core(ship), true);
    helper.runAtTickTime(
        1000,
        () -> {
          helper.assertTrue(core(ship).isMature(), "Initial body did not mature");
          var stomach = DigestiveSystem.stomach(core(ship));
          stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 8));
          stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 26));
          stomach.retainFlesh(48); // Additional digestive pair, sensory eyes, larynx and reserve.
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
                  var stomach = DigestiveSystem.stomach(core(ship));
                  helper.assertTrue(
                      DigestiveSystem.stomachs(core(ship)).stream()
                              .mapToInt(StomachBlockEntity::meals)
                              .sum()
                          >= 8,
                      "Post-growth meals did not register");
                  helper.assertTrue(
                      helper
                          .getLevel()
                          .getBlockState(core(ship).structuralOrigin().offset(DinghyGrowth.LARYNX))
                          .is(block("biomancy:modular_larynx")),
                      "Native modular larynx did not develop: stock="
                          + BioshipStorage.snapshot(core(ship))
                          + " pairs="
                          + DigestiveSystem.stomachs(core(ship)).size()
                          + " plan="
                          + BioshipDigestiveGrowth.plan(core(ship)));
                  int extraMouths = DigestiveSystem.mouths(core(ship)).size() - 1;
                  int extraStomachs = DigestiveSystem.stomachs(core(ship)).size() - 1;
                  int tiers = core(ship).crawl().legTier();
                  long eyes =
                      BioshipDecay.blocks(ship).stream()
                          .filter(
                              p ->
                                  helper
                                      .getLevel()
                                      .getBlockState(p)
                                      .is(Create_bioships.SURFACE_EYE.get()))
                          .count();
                  long paidTubes =
                      BioshipDecay.blocks(ship).stream()
                          .filter(
                              p -> {
                                var state = helper.getLevel().getBlockState(p);
                                return state.is(Create_bioships.INTESTINE.get())
                                    && state.getValue(IntestineBlock.PAID);
                              })
                          .count();
                  int tubeCredit =
                      DigestiveSystem.stomachs(core(ship)).stream()
                          .mapToInt(organ -> organ.paidTubeStock)
                          .sum();
                  int expectedBone =
                      20 + 8 - 8 - 2 * tiers - 2 * extraMouths - 4 * extraStomachs - 2;
                  // Each additional stomach price contains four fiber units of paid tube seed.
                  int expectedFiber =
                      54 + 26 + 32 - 12 - 4 * tiers - 4 * extraMouths - 4 * extraStomachs - 4;
                  helper.assertTrue(
                      stomach.material(MaterialCost.BONE) == expectedBone
                          && stomach.material(MaterialCost.FIBER) + eyes + paidTubes + tubeCredit
                              == expectedFiber,
                      "Larynx/digestion/tube material conservation failed: stock="
                          + BioshipStorage.snapshot(core(ship))
                          + " eyes="
                          + eyes
                          + " tubes="
                          + paidTubes
                          + " credit="
                          + tubeCredit
                          + " expectedBone="
                          + expectedBone
                          + " expectedFiber="
                          + expectedFiber);
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
    level.setBlock(p.north(), BioshipTissue.body().defaultBlockState(), 3);
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
    stomach.retainFlesh(12);
    stomach.credit(MaterialCost.FIBER, 4);
    var p = core.getBlockPos().east();
    level.setBlock(p, BioshipTissue.body().defaultBlockState(), 3);
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
                      BioshipTissue.isTissue(level.getBlockState(p)), "Tissue did not regrow");
                  helper.assertTrue(
                      stomach.material("biomancy:flesh_bits") == 4
                          && stomach.material(MaterialCost.FIBER) == 0,
                      "Packed tissue repair did not pay its dense-tissue material cost");
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
                          .anyMatch(
                              line ->
                                  line.getString()
                                      .toLowerCase(java.util.Locale.ROOT)
                                      .contains("health")),
                      "Goggles did not show the requested health readout");
                  level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                  core.body().observe(core);
                  var unchanged = BioshipBody.actual(core);
                  core.body().reconcile(core, unchanged);
                  helper.assertTrue(
                      BioshipTissue.isTissue(
                          core.body().memory.get(p.subtract(core.getBlockPos()))),
                      "An outside obstruction replaced the remembered hull schematic");
                }));
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 2600)
  public static void blueprint_draft_landing_rebuild_and_inventory(GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), true);
    var owner = player(helper, "Architect");
    core(ship).claimCreator(owner.getUUID());
    helper.runAfterDelay(
        1000,
        () -> {
          helper.assertTrue(core(ship).isMature(), "Ship failed to mature");
          core(ship).body().observe(core(ship));
          var stomach = DigestiveSystem.stomach(core(ship));
          stomach.retainFlesh(1024);
          for (int n = 0; n < 8; n++)
            core(ship).acceptAmalgam(new ItemStack(item("aeronautics:levitite_blend_bucket")));
          var draft = new BioshipBlueprint.Draft(core(ship).blueprint().rooms());
          // Add a room sharing the engine-room wall, and a membrane connecting their interiors.
          draft.edit(2 * 2500 + (5 + 24) * 49 + (7 + 24));
          draft.edit(6 * 2500 + (5 + 24) * 49 + (4 + 24));
          int quoted = core(ship).blueprint().cost(core(ship), draft);
          var plan = core(ship).blueprint().proposal(core(ship), draft);
          helper.assertTrue(
              core(ship).blueprint().validate(core(ship), draft, plan) == null,
              "Valid joined room was rejected: "
                  + core(ship).blueprint().validate(core(ship), draft, plan));
          helper.assertTrue(quoted > 0, "New room has no biomass cost");
          helper.assertTrue(
              level.getBlockState(core(ship).structuralOrigin().offset(7, 1, 5)).isAir(),
              "Draft changed the live hull");
          var disconnected = new BioshipBlueprint.Draft(core(ship).blueprint().rooms());
          disconnected.edit(2 * 2500 + (20 + 24) * 49 + (20 + 24));
          helper.assertTrue(
              core(ship)
                      .blueprint()
                      .validate(
                          core(ship),
                          disconnected,
                          core(ship).blueprint().proposal(core(ship), disconnected))
                  != null,
              "Disconnected room was accepted");
          var discarded = new BioshipBlueprint.Draft(core(ship).blueprint().rooms());
          helper.assertTrue(
              core(ship).blueprint().cost(core(ship), discarded) == 0,
              "Discarding draft did not restore the current plan");
          helper.runAfterDelay(
              30,
              () -> {
                int stock = stomach.material("biomancy:flesh_bits");
                helper.assertTrue(
                    core(ship)
                        .blueprint()
                        .confirm(core(ship), draft, core(ship).blueprint().revision()),
                    "Confirmation failed: " + core(ship).blueprint().status());
                helper.assertTrue(
                    stomach.material("biomancy:flesh_bits") == stock - quoted,
                    "Quoted biomass was not charged once");
                var tag = core(ship).saveWithFullMetadata(level.registryAccess());
                core(ship).loadAdditional(tag, level.registryAccess());
                helper.assertTrue(core(ship).blueprint().busy(), "Approved job did not persist");
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
                      !core(ship).blueprint().busy(),
                      "Job stalled: "
                          + core(ship).blueprint().phase()
                          + " "
                          + core(ship).blueprint().status());
                  helper.assertTrue(
                      core(ship).blueprint().rooms().size() == 3, "New room was not committed");
                  helper.assertTrue(
                      BioshipTissue.isTissue(
                          level.getBlockState(core(ship).structuralOrigin().offset(7, 1, 5))),
                      "Room floor missing");
                  helper.assertTrue(
                      level
                          .getBlockState(core(ship).structuralOrigin().offset(4, 2, 5))
                          .is(block("biomancy:biometric_membrane")),
                      "Requested door missing");
                  helper.assertTrue(
                      DigestiveSystem.stomach(core(ship)).material("biomancy:flesh_bits") > 0,
                      "Stored stomach contents were lost");
                  helper.assertTrue(
                      !core(ship).blueprint().locked(),
                      "Membranes stayed locked after construction");
                }));
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 3500, batch = "room_surgery")
  public static void moving_engine_room_evacuates_and_preserves_stomach(GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), true);
    var owner = player(helper, "RoomMover");
    core(ship).claimCreator(owner.getUUID());
    var occupant = new net.minecraft.world.entity.animal.Cow(EntityType.COW, level);
    boolean[] sawArm = {false};
    double[] health = {0};
    int[] capacity = {0};
    int[] stock = {0};
    int[] cost = {0};
    int[] sacs = {0};
    long[] tubeStock = {0};
    helper.runAfterDelay(
        1000,
        () -> {
          helper.assertTrue(core(ship).isMature(), "Ship did not grow before relocation");
          core(ship).body().observe(core(ship));
          var stomach = DigestiveSystem.stomach(core(ship));
          stomach.retainFlesh(2048);
          stomach.retain(new ItemStack(Items.BARRIER, 17));
          for (int n = 0; n < 8; n++)
            core(ship).acceptAmalgam(new ItemStack(item("aeronautics:levitite_blend_bucket")));
        });
    helper.runAfterDelay(
        1040,
        () -> {
          var d = new BioshipBlueprint.Draft(core(ship).blueprint().rooms());
          d.selected = 1;
          d.edit(2500 + (5 + 24) * 49 + (2 + 24));
          var error =
              core(ship)
                  .blueprint()
                  .validate(core(ship), d, core(ship).blueprint().proposal(core(ship), d));
          helper.assertTrue(error == null, "Room move was rejected: " + error);
          stock[0] = DigestiveSystem.stomach(core(ship)).material("biomancy:flesh_bits");
          cost[0] = core(ship).blueprint().cost(core(ship), d);
          sacs[0] = BioshipStorage.sacks(core(ship)).size();
          tubeStock[0] = paidIntestinalMaterial(core(ship));
          health[0] = core(ship).body().health();
          capacity[0] = core(ship).body().capacity();
          var point =
              BodyCoordinates.physical(core(ship).amoebaComponents(), new BlockPos(0, 2, 2));
          occupant.moveTo(point.x, point.y - .5, point.z, 0, 0);
          level.addFreshEntity(occupant);
          helper.assertTrue(
              core(ship).blueprint().confirm(core(ship), d, core(ship).blueprint().revision()),
              "Move confirmation failed: " + core(ship).blueprint().status());
          var tag = core(ship).saveWithFullMetadata(level.registryAccess());
          tag.putInt("Satiety", 1200);
          tag.putInt("Trust", 100);
          core(ship).loadAdditional(tag, level.registryAccess());
        });
    helper.runAfterDelay(
        1070,
        () -> {
          sawArm[0] = core(ship).blueprint().carried() != null && core(ship).blueprint().locked();
        });
    helper.runAfterDelay(
        1090,
        () -> {
          sawArm[0] |= core(ship).blueprint().carried() != null && core(ship).blueprint().locked();
        });
    helper.runAfterDelay(
        1150,
        () -> {
          if (core(ship).blueprint().busy()) {
            var tag = core(ship).saveWithFullMetadata(level.registryAccess());
            core(ship).loadAdditional(tag, level.registryAccess());
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
                      !core(ship).blueprint().busy(),
                      "Room relocation stalled: "
                          + core(ship).blueprint().phase()
                          + " / "
                          + core(ship).blueprint().status());
                  helper.assertTrue(
                      occupant.isAlive() && !core(ship).blueprint().aboard(core(ship), occupant),
                      "Passenger was not put safely outside");
                  helper.assertTrue(
                      core(ship).blueprint().rooms().get(1).x() == 2,
                      "Engine room location was not saved");
                  var stomach = DigestiveSystem.stomach(core(ship));
                  helper.assertTrue(stomach != null, "Moving the room lost its stomach");
                  helper.assertTrue(
                      stomach.material("minecraft:barrier") == 17,
                      "Moving the room lost or duplicated private products");
                  helper.assertTrue(
                      stomach.material("biomancy:flesh_bits") + paidIntestinalMaterial(core(ship))
                          == stock[0]
                              + tubeStock[0]
                              - cost[0]
                              - 8 * (BioshipStorage.sacks(core(ship)).size() - sacs[0]),
                      "Relocation reserve mismatch: bank="
                          + BioshipStorage.snapshot(core(ship))
                          + " sacs="
                          + BioshipStorage.sacks(core(ship)).size()
                          + " before="
                          + stock[0]
                          + " cost="
                          + cost[0]
                          + " after="
                          + stomach.material("biomancy:flesh_bits")
                          + " fiber="
                          + stomach.material("biomancy:elastic_fibers")
                          + " health="
                          + core(ship).body().health()
                          + "/"
                          + health[0]
                          + " tubes="
                          + stomach.paidTubeStock
                          + "/"
                          + stomach.freeTubeStock);
                  helper.assertTrue(
                      core(ship).body().capacity() - core(ship).body().health()
                          <= capacity[0] - health[0],
                      "Approved room surgery injured the bioship");
                  helper.assertTrue(
                      stomach.getBlockPos().equals(core(ship).layoutPos(new BlockPos(-2, 2, 3))),
                      "Stomach did not move with its room");
                  helper.assertTrue(
                      !BioshipWrenchItem.movable(
                          level, core(ship).layoutPos(new BlockPos(-3, 2, 3))),
                      "Wrench can remove hull tissue");
                  occupant.discard();
                }));
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 2100, batch = "hatchling_walk")
  public static void blocked_hatchling_walks_to_clear_ground_without_flight(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    Vec3 start = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
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
                  Vec3 end = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);

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
                      "Walking supplied levitite-free flight: start="
                          + start
                          + " end="
                          + end
                          + " stance="
                          + core.crawl().save());
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "merge_below_core")
  public static void stomach_merge_below_core_needs_no_top_staging_cell(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var destination = core.getBlockPos().below();
    var source = core.getBlockPos().east();
    level.setBlock(destination, block("biomancy:decomposer").defaultBlockState(), 3);
    level.setBlock(source, block("biomancy:digester").defaultBlockState(), 3);
    var digester =
        (com.github.elenterius.biomancy.block.digester.DigesterBlockEntity)
            level.getBlockEntity(source);
    digester.getOutputInventory().setStackInSlot(0, new ItemStack(item("biomancy:nutrients"), 7));
    ComponentMerging.tryAvailable(core.amoebaComponents());
    helper.assertTrue(
        ComponentTransport.busy(core.amoebaComponents()),
        "Core above decomposer prevented the merge from starting");
    helper.runAfterDelay(
        37,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var organ = BioshipContainerState.entity(level, ship, destination);
                  helper.assertTrue(
                      organ instanceof StomachBlockEntity stomach
                          && stomach.material("biomancy:nutrients") == 7,
                      "Under-core merge failed or lost resources");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "merge_faces")
  public static void component_merge_accepts_every_face_and_preserves_inputs(
      GameTestHelper helper) {
    var ships = new ArrayList<ServerSubLevel>();
    var targets = new ArrayList<BlockPos>();
    for (var face : Direction.values()) {
      var ship = assemble(helper);
      ships.add(ship);
      var core = core(ship);
      var level = helper.getLevel();
      var destination = core.getBlockPos().offset(2, 1, 0);
      targets.add(destination);
      level.setBlock(core.getBlockPos().above(), BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(
          core.getBlockPos().above().east(), BioshipTissue.body().defaultBlockState(), 3);
      var source = destination.relative(face);
      boolean encased = BioshipTissue.isTissue(level.getBlockState(source));
      level.setBlock(destination, block("biomancy:decomposer").defaultBlockState(), 3);
      level.setBlock(source, block("biomancy:digester").defaultBlockState(), 3);
      var digester =
          (com.github.elenterius.biomancy.block.digester.DigesterBlockEntity)
              level.getBlockEntity(source);
      if (encased) BioshipTissue.remember(digester, BioshipTissue.body().defaultBlockState());
      digester.getOutputInventory().setStackInSlot(0, new ItemStack(item("biomancy:nutrients"), 7));
      helper.assertTrue(
          ComponentTransport.merge(core.amoebaComponents(), source, destination, "stomach"),
          "Merge rejected face " + face);
    }
    helper.runAfterDelay(
        37,
        () -> {
          try {
            for (int i = 0; i < ships.size(); i++) {
              var organ =
                  BioshipContainerState.entity(helper.getLevel(), ships.get(i), targets.get(i));
              helper.assertTrue(
                  organ instanceof StomachBlockEntity stomach
                      && stomach.material("biomancy:nutrients") == 7,
                  "Merge lost destination or resources for " + Direction.values()[i]);
            }
            helper.succeed();
          } finally {
            for (var ship : ships) cleanup(helper, ship);
          }
        });
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
        ComponentTransport.busy(core.amoebaComponents()),
        "Separated organs did not move together through tissue");
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
                      "Fusion lost the digester output: "
                          + (stomach == null ? "missing" : stomach.material("biomancy:nutrients"))
                          + " core="
                          + core(ship).getBlockPos()
                          + " move="
                          + core(ship).organMove());
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
    level.setBlock(core.getBlockPos().west(2), BioshipTissue.body().defaultBlockState(), 3);
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
              core.body().health() == core.body().capacity(),
              "Reserve-backed starvation damaged health");
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
              core.body().health() < core.body().capacity() && !core.body().dead(),
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1650, batch = "wreck_fall_rot")
  public static void dead_flying_ship_falls_and_fragments_decay(GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), true);
    double[] altitude = {0};
    int[] before = {0};
    ServerSubLevel[] fragment = {null};
    helper.runAfterDelay(1000, () -> enableFlight(helper, core(ship)));
    helper.runAfterDelay(
        1150,
        () -> {
          altitude[0] = BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO).y;
          helper.assertTrue(
              altitude[0] > 84,
              "Flight fixture did not rise: y="
                  + altitude[0]
                  + " stage="
                  + core(ship).isMature()
                  + " canFly="
                  + core(ship).canFly()
                  + " crawl="
                  + core(ship).crawl().save()
                  + " trim="
                  + level.getBlockState(core(ship).layoutPos(DinghyGrowth.VERTICAL_TRIM))
                  + " flagella="
                  + core(ship).swimmingFlagella());
          before[0] = BioshipDecay.blocks(ship).size();
          core(ship).body().die(core(ship));
          var decayTag = ship.getUserDataTag().copy();
          decayTag.putLong("BioshipDecayAfter", level.getGameTime());
          ship.setUserDataTag(decayTag);
          var source = core(ship).layoutPos(new BlockPos(0, 1, 8));
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
                      BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO).y
                          < altitude[0] - 2,
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

  @GameTest(template = "test_empty", timeoutTicks = 420, batch = "wreck_scavenge")
  public static void scavenging_recovers_needed_organs_and_tissue_at_a_loss(GameTestHelper helper) {
    var live = assemble(helper);
    var core = core(live);
    var level = helper.getLevel();
    var point =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(5);
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
        !ComponentTransport.begin(core.amoebaComponents(), mouth, core.getBlockPos().north(), true),
        "Living neighbor was eligible for scavenging");
    dead.body().die(dead);
    helper.assertTrue(
        !BioshipScavenging.needed(core, dead.getBlockState())
            && !ComponentTransport.begin(
                core.amoebaComponents(), dead.getBlockPos(), core.getBlockPos().north(), true),
        "A dead bioship core was eligible for consumption");
    ItemStack[] recoveredStock = {ItemStack.EMPTY};
    // Batched organs can connect sooner; inspect the mouth immediately when it grows out,
    // before ordinary recipe-driven transfer to the newly recovered stomach can run.
    for (int tick = 1; tick <= 160; tick++)
      helper.runAfterDelay(
          tick,
          () -> {
            var current = core(live);
            var pos = current.mouthBlock();
            if (!recoveredStock[0].isEmpty() || pos == null) return;
            if (level.getBlockEntity(pos) instanceof FeedingMouthBlockEntity recovered) {
              helper.assertTrue(
                  recovered.getItem(0).is(Items.BARRIER) && recovered.getItem(0).getCount() == 11,
                  "Recovered mouth cargo lost its original inventory");
              recoveredStock[0] = recovered.removeItem(0, 11);
            }
          });
    helper.runAfterDelay(
        280,
        () -> {
          var current = core(live);
          try {
            helper.assertTrue(
                current.hasMouth() && DigestiveSystem.stomach(current) != null,
                "Nearby wreck organs were not pulled aboard");
            helper.assertTrue(
                recoveredStock[0].is(Items.BARRIER) && recoveredStock[0].getCount() == 11,
                "Recovered inventory could not be salvaged before digestion");
            helper.assertTrue(
                BioshipScavenging.needed(
                    current, Create_bioships.FEEDING_MOUTH.get().defaultBlockState()),
                "Duplicate dead organs cannot be reclaimed as construction stock");
            var port = current.getBlockPos().above();
            int satiety = current.saveWithFullMetadata(level.registryAccess()).getInt("Satiety");
            int fleshBefore = BioshipStorage.amount(current, MaterialCost.FLESH);
            int fiberBefore = BioshipStorage.amount(current, MaterialCost.FIBER);
            int boneBefore = BioshipStorage.amount(current, MaterialCost.BONE);
            for (int n = 0; n < 4; n++) {
              level.setBlock(port, Create_bioships.TENTACLE.get().defaultBlockState(), 3);
              current.scavenging().finish(current, port, false);
            }
            for (int n = 0; n < 4; n++) {
              level.setBlock(port, Create_bioships.CARAPACE.get().defaultBlockState(), 3);
              current.scavenging().finish(current, port, false);
            }
            var stomach = DigestiveSystem.stomach(current);
            helper.assertTrue(
                BioshipStorage.amount(current, MaterialCost.FLESH) == fleshBefore + 4
                    && stomach.material("biomancy:elastic_fibers") == fiberBefore + 1
                    && stomach.material("biomancy:bone_fragments") == boneBefore + 1,
                "Reclaimed tissue did not use the specified lossy yield: before="
                    + fleshBefore
                    + "/"
                    + fiberBefore
                    + "/"
                    + boneBefore
                    + " after="
                    + BioshipStorage.snapshot(current));
            helper.assertTrue(
                current.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") == satiety,
                "Wreck tissue counted as food");
            level.setBlock(port, BioshipTissue.body().defaultBlockState(), 3);
            current.scavenging().finish(current, port, false);
            var save = current.saveWithFullMetadata(level.registryAccess());
            current.loadAdditional(save, level.registryAccess());
            level.setBlock(port, BioshipTissue.body().defaultBlockState(), 3);
            current.scavenging().finish(current, port, false);
            helper.assertTrue(
                BioshipStorage.amount(current, MaterialCost.FLESH) == fleshBefore + 5,
                "Fractional salvage yield did not survive reload");
            var originalMouth = current.mouthBlock();
            level.setBlock(port, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
            current.scavenging().finish(current, port, false);
            helper.assertTrue(
                level.getBlockState(port).isAir() && current.mouthBlock().equals(originalMouth),
                "Reclaimed duplicate organ was kept as a second mouth");
            helper.succeed();
          } finally {
            cleanup(helper, wreck);
            cleanup(helper, live);
          }
        });
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 100, batch = "trusted_pilot")
  public static void trusted_pilot_commands_override_injury_but_absence_does_not(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var captain = new InteractionPlayer(level, "TrustedInjuredCaptain");
    captain.setGameMode(GameType.SURVIVAL);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    captain.setPos(here.x + 2, here.y, here.z);
    level.addNewPlayer(captain);
    core.claimCreator(captain.getUUID());
    try {
      var saved = core.saveWithFullMetadata(level.registryAccess());
      saved.putInt("Trust", 79);
      core.loadAdditional(saved, level.registryAccess());
      var injury = core.body().save();
      injury.putDouble("Health", 10);
      core.body().load(injury, level.registryAccess());
      var panic = new net.minecraft.nbt.CompoundTag();
      panic.put("Danger", VisionDebug.vector(here.add(3, 0, 0)));
      panic.putLong("PanicUntil", level.getGameTime() + 100);
      core.autonomy().load(panic);
      core.autonomy().command(core, captain);
      core.autonomy().assess(core);
      helper.assertTrue(core.autonomy().fleeing(), "Low-health threat did not trigger retreat");
      helper.assertTrue(
          !core.autonomy().trustedPilot(core), "Insufficient trust suppressed retreat");
      saved = core.saveWithFullMetadata(level.registryAccess());
      saved.putInt("Trust", 80);
      core.loadAdditional(saved, level.registryAccess());
      helper.assertTrue(
          core.autonomy().trustedPilot(core), "Low health rejected the trusted pilot");
      helper.assertTrue(core.body().health() == 10, "Pilot authority healed the injury");
      helper.assertTrue(
          core.autonomy().groundEscape(core) == null, "Retreat overrode trusted control");
      captain.discard();
      helper.assertTrue(!core.autonomy().trustedPilot(core), "Absent captain suppressed retreat");
      helper.succeed();
    } finally {
      captain.discard();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "swipe_dodge")
  public static void player_can_dodge_locked_swipe_without_being_grabbed(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var target = new VulnerablePlayer(level, "SwipeDodger");
    target.setGameMode(GameType.SURVIVAL);
    target.setNoGravity(true);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var position = here.add(3, -.5, 0);
    target.setPos(position.x, position.y, position.z);
    level.addNewPlayer(target);
    core.claimCreator(target.getUUID());
    var root = BioshipReach.anchor(core, target.getBoundingBox().getCenter());
    helper.assertTrue(
        TentacleStrike.tipCollision(
            new net.minecraft.world.phys.AABB(2, -.1, -.1, 2.2, .1, .1),
            new Vec3(1, 0, 0),
            new Vec3(3, 0, 0)),
        "Fast tip skipped a collision between ticks");
    helper.assertTrue(
        !TentacleStrike.tipCollision(
            new net.minecraft.world.phys.AABB(.5, -.1, -.1, .7, .1, .1),
            new Vec3(3, 0, -.5),
            new Vec3(3, 0, .5)),
        "Shaft contact incorrectly counted as tip contact");
    var strike = new TentacleStrike();
    strike.begin(target, level.getGameTime());
    helper.runAfterDelay(
        6,
        () -> {
          helper.assertTrue(
              !strike.tick(core, root, target, 4, 6) && target.getHealth() == 20,
              "Swipe damaged a player during its wind-up");
          target.setPos(position.x, position.y, position.z + 4);
        });
    helper.runAfterDelay(
        15,
        () -> {
          helper.assertTrue(
              !strike.tick(core, root, target, 4, 6) && target.getHealth() == 20,
              "Swipe followed a player who dodged its locked aim");
          helper.assertTrue(
              target.getDeltaMovement().lengthSqr() < .0001, "Combat swipe dragged the player");
          target.setPos(position.x, position.y, position.z);
          strike.begin(target, level.getGameTime());
        });
    helper.runAfterDelay(
        30,
        () -> {
          try {
            helper.assertTrue(
                strike.tick(core, root, target, 4, 6), "Stationary target escaped the swipe");
            helper.assertTrue(target.getHealth() == 14, "Swipe dealt the wrong damage");
            helper.assertTrue(!strike.tick(core, root, target, 4, 6), "Same swipe damaged twice");
            helper.succeed();
          } finally {
            target.discard();
            cleanup(helper, ship);
          }
        });
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 100, batch = "rescue_aperture")
  public static void rescue_opens_conserved_hull_and_restores_it_after_carrying(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var data = core.saveWithFullMetadata(level.registryAccess());
    data.putInt("Stage", 2);
    core.loadAdditional(data, level.registryAccess());
    var origin = core.structuralOrigin();
    for (var offset : BlockPos.betweenClosed(new BlockPos(-2, 1, -3), new BlockPos(2, 5, 3))) {
      if (offset.getY() == 1
          || offset.getY() == 5
          || Math.abs(offset.getX()) == 2
          || Math.abs(offset.getZ()) == 3)
        level.setBlock(origin.offset(offset), BioshipTissue.body().defaultBlockState(), 3);
    }
    core.body().observe(core);
    var initial = BioshipBody.actual(core);
    double health = core.body().health();
    var ally =
        new InteractionPlayer(level, "RescuedCaptain") {
          @Override
          public void teleportTo(double x, double y, double z) {
            setPos(x, y, z);
          }
        };
    core.claimCreator(ally.getUUID());
    var start =
        ship.logicalPose().transformPosition(Vec3.atLowerCornerOf(origin).add(.5, 0, -14.5));
    ally.setPos(start.x, start.y, start.z);
    level.addNewPlayer(ally);
    try {
      helper.assertTrue(core.rescue().begin(core, ally), "Rescue could not find an aperture route");
      helper.assertTrue(
          !core.rescue().tissue().isEmpty(), "Closed hull was traversed without opening it");
      for (var p : core.rescue().tissue().keySet())
        helper.assertTrue(level.getBlockState(p).isAir(), "Aperture retained solid hull");
      core.body().observe(core);
      helper.assertTrue(
          core.body().health() == health, "Opening an authorized aperture injured the ship");
      var saved = core.saveWithFullMetadata(level.registryAccess());
      core.loadAdditional(saved, level.registryAccess());
      for (int i = 0; i < 300 && core.rescue().active(); i++) core.rescue().tick(core);
      helper.assertTrue(
          !core.rescue().active(),
          "Rescue failed to finish and seal its aperture: "
              + core.rescue().save()
              + " player="
              + ship.logicalPose().transformPositionInverse(ally.position()));
      helper.assertTrue(core.blueprint().aboard(core, ally), "Ally was not brought inside");
      helper.assertTrue(
          BioshipBody.actual(core).equals(initial), "Rescue lost or duplicated hull tissue");
      helper.assertTrue(core.body().health() == health, "Rescue injured the ship");
      helper.succeed();
    } finally {
      ally.discard();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "front_mouth_stability")
  public static void hatchling_mouth_moves_to_front_once_and_keeps_inventory(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var origin = core.structuralOrigin();
    var source = origin.east();
    level.setBlock(source, block("biomancy:bio_forge").defaultBlockState(), 3);
    BioshipIntegration.finishAssimilation(core, source);
    ((FeedingMouthBlockEntity) level.getBlockEntity(source))
        .setItem(0, new ItemStack(Items.BEEF, 5));
    BioshipIntegration.orientMouth(core);
    helper.runAfterDelay(
        70,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var front = origin.north();
                  helper.assertTrue(
                      front.equals(core.mouthBlock()),
                      "Mouth drifted beyond the actual front face");
                  helper.assertTrue(
                      level.getBlockState(front).getValue(FeedingMouthBlock.FACING)
                          == Direction.NORTH,
                      "Front mouth teeth point inside the body");
                  helper.assertTrue(
                      ((FeedingMouthBlockEntity) level.getBlockEntity(front)).getItem(0).getCount()
                          == 5,
                      "Front placement lost mouth inventory");
                  helper.assertTrue(
                      !ComponentTransport.busy(core.amoebaComponents()),
                      "Front mouth continually repositions itself");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "source_memory")
  public static void hidden_last_seen_source_survives_reload_until_visually_rechecked(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var source = here.east(3);
    var state = block("biomancy:bio_forge").defaultBlockState();
    level.setBlock(source, state, 3);
    survey(core);
    try {
      helper.assertTrue(
          state.equals(core.vision().sourceState(core, source)),
          "Visible source was not remembered");
      var wall = new ArrayList<BlockPos>();
      for (var p : BlockPos.betweenClosed(here.offset(1, -1, -2), here.offset(1, 3, 2))) {
        wall.add(p.immutable());
        level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
      }
      helper.assertTrue(
          !core.vision().visible(core, Vec3.atCenterOf(source)),
          "Memory fixture did not block sight");
      level.removeBlock(source, false);
      core.supplyTarget = source;
      var saved = core.saveWithFullMetadata(level.registryAccess());
      core.loadAdditional(saved, level.registryAccess());
      helper.assertTrue(
          state.equals(core.vision().sourceState(core, source)),
          "Hidden source was forgotten or read omnisciently");
      for (var p : wall) level.removeBlock(p, false);
      helper.assertTrue(
          core.vision().sourceState(core, source) == null,
          "Visible missing source was never forgotten");
      helper.succeed();
    } finally {
      level.removeBlock(source, false);
      cleanup(helper, ship);
    }
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
    Vec3 point =
        BodyCoordinates.physical(core.amoebaComponents(), mouth.subtract(core.getBlockPos()));
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
        ComponentTransport.begin(core.amoebaComponents(), source, target, false),
        "Bare component did not start moving");
    helper.assertTrue(
        level.getBlockState(source).isAir() && !core.organMove().usesArm(),
        "Internal merge created filler or used an external carrying arm");
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
                core.getBlockPos().offset(1, y, 0), BioshipTissue.body().defaultBlockState(), 3);
          level.setBlock(wallSource, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
          var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(wallSource);
          BioshipTissue.remember(mouth, BioshipTissue.body().defaultBlockState());
          mouth.getPersistentData().putBoolean("BioshipManualPlacement", true);
          mouth.setItem(0, new ItemStack(Items.BEEF, 5));
          helper.assertTrue(
              ComponentTransport.begin(core.amoebaComponents(), wallSource, wallTarget, false),
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
                      BioshipTissue.isTissue(level.getBlockState(old)),
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

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "storage_attachment")
  public static void disconnected_sac_relocates_to_existing_gut_without_free_tubes(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    stomach.paidTubeStock = 0;
    for (int y = 0; y < 3; y++)
      level.setBlock(
          core.getBlockPos().offset(1, y, 0), BioshipTissue.body().defaultBlockState(), 3);
    var source = core.getBlockPos().offset(1, 3, 0);
    level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
    var inventory =
        Objects.requireNonNull(level.getCapability(Capabilities.ItemHandler.BLOCK, source, null));
    inventory.insertItem(0, new ItemStack(Items.BEEF, 3), false);
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        !stomach.sacPaths.containsKey(source),
        "High disconnected sac unexpectedly has a tube route");
    int tubes =
        (int)
            BioshipDecay.blocks(ship).stream()
                .filter(p -> level.getBlockState(p).is(Create_bioships.INTESTINE.get()))
                .count();
    DigestiveSystem.arrangeStorage(core);
    helper.assertTrue(
        ComponentTransport.busy(core.amoebaComponents()), "Disconnected sac was not repositioned");
    helper.runAfterDelay(
        90,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = DigestiveSystem.stomach(core);
                  helper.assertTrue(
                      current.sacPaths.size() == 1,
                      "Relocated sac is not connected to the stomach");
                  helper.assertTrue(
                      !current.sacPaths.containsKey(source),
                      "Inaccessible sac stayed at its old position");
                  helper.assertTrue(
                      current.queuedFood() > 0, "Connected sac did not transfer food");
                  int remaining = current.queuedFood();
                  for (var pos : current.sacPaths.keySet()) {
                    var store =
                        Objects.requireNonNull(
                            level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
                    for (int slot = 0; slot < store.getSlots(); slot++)
                      remaining += store.getStackInSlot(slot).getCount();
                  }
                  helper.assertTrue(
                      remaining == 3, "Moving and connecting a sack lost or duplicated food");
                  helper.assertTrue(
                      BioshipDecay.blocks(ship).stream()
                              .filter(
                                  p -> level.getBlockState(p).is(Create_bioships.INTESTINE.get()))
                              .count()
                          <= tubes,
                      "Relocation created unpurchased intestinal tissue");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "assimilated_storage_attachment")
  public static void assimilated_food_sac_arrives_at_a_working_digestive_port(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    stomach.paidTubeStock = 0;
    var source =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .south(3);
    level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
    Objects.requireNonNull(level.getCapability(Capabilities.ItemHandler.BLOCK, source, null))
        .insertItem(0, new ItemStack(Items.BEEF, 4), false);
    survey(core);
    BioshipIntegration.assimilate(core, ship);
    helper.assertTrue(
        ComponentTransport.busy(core.amoebaComponents()),
        "Visible food storage was not assimilated");
    helper.runAfterDelay(
        90,
        () -> {
          try {
            var current = DigestiveSystem.stomach(core);
            helper.assertTrue(
                current.sacPaths.size() == 1 && current.queuedFood() > 0,
                "Assimilated storage was attached without a working tube connection");
            int remaining = current.queuedFood();
            for (var pos : current.sacPaths.keySet()) {
              var store =
                  Objects.requireNonNull(
                      level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
              for (int slot = 0; slot < store.getSlots(); slot++)
                remaining += store.getStackInSlot(slot).getCount();
            }
            helper.assertTrue(remaining == 4, "Assimilation lost or duplicated stored food");
            helper.succeed();
          } finally {
            level.removeBlock(source, false);
            cleanup(helper, ship);
          }
        });
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
    var loose =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .south(16);
    level.setBlock(loose, block("biomancy:storage_sac").defaultBlockState(), 3);
    level.setBlock(loose.above(2), Blocks.STONE.defaultBlockState(), 3);
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
                      DigestiveSystem.stomach(core).queuedFood() == 0
                          && BioshipIntegration.blockEntities(ship).stream()
                              .filter(NativeStorage::isStorage)
                              .allMatch(
                                  entity -> {
                                    var store =
                                        level.getCapability(
                                            Capabilities.ItemHandler.BLOCK,
                                            entity.getBlockPos(),
                                            null);
                                    if (store == null) return true;
                                    for (int slot = 0; slot < store.getSlots(); slot++)
                                      if (!store.getStackInSlot(slot).isEmpty()) return false;
                                    return true;
                                  }),
                      "The provisioned meal did not finish digestion: "
                          + DigestiveSystem.stomach(core)
                              .saveWithFullMetadata(level.registryAccess()));
                  level.removeBlock(loose, false);
                  level.removeBlock(loose.above(2), false);
                }));
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1500, batch = "resumed_growth")
  public static void existing_hull_parts_and_replaceable_grass_do_not_stall_growth(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, true);
    var parts = DinghyGrowth.body();
    int index = -1;
    for (int n = 0; n < parts.size(); n++)
      if (BioshipTissue.isTissue(parts.get(n).state())
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
    var physical =
        BlockPos.containing(
            BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(2, 0, -2)));
    level.setBlock(physical, Blocks.SHORT_GRASS.defaultBlockState(), 3);
    helper.assertTrue(
        BioshipIntegration.growthSpace(level, physical), "Replaceable grass blocks construction");
    var water = physical.east();
    level.setBlock(water, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4), 3);
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

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1300, batch = "interior_collision")
  public static void cockpit_clearance_eye_level_panel_and_real_membrane_collision(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), true);
    var owner = new InteractionPlayer(level, "InteriorOwner");
    var guest = new InteractionPlayer(level, "UninvitedGuest");
    core(ship).claimCreator(owner.getUUID());
    helper.runAfterDelay(
        1100,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      core(ship).isMature(), "Expanded cockpit did not finish growing");
                  helper.assertTrue(
                      level
                          .getBlockState(core(ship).structuralOrigin().offset(DinghyGrowth.PERCH))
                          .is(Create_bioships.PERCH.get()),
                      "Captain seat did not move forward");
                  for (int z = -1; z >= -2; z--)
                    for (int y = 2; y <= 3; y++)
                      helper.assertTrue(
                          level
                              .getBlockState(core(ship).structuralOrigin().offset(0, y, z))
                              .isAir(),
                          "Entrance aisle is blocked by the captain seat or a console");
                  var panel = core(ship).layoutPos(DinghyGrowth.BLUEPRINT_PANEL);
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
                  BioshipAccess.tick(core(ship));
                  var door = core(ship).layoutPos(DinghyGrowth.DOOR);
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
                  core(ship).access().animals.put(allowed.getUUID(), "CrewCow");
                  BioshipAccess.tick(core(ship));
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
                  var access = core(ship).access().save(core(ship).creator());
                  access.putBoolean("Locked", true);
                  gate.bioships$access(access);
                  helper.assertTrue(
                      !sableMembraneShape(ship, door, owner).isEmpty(),
                      "Construction lock did not stop the owner");
                }));
  }

  @DeferredAirshipTest(template = "test_empty", batch = "window_legacy_layout")
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
    var origin =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(6);
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
    level.setBlock(window.above(), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(limb, Create_bioships.TENTACLE.get().defaultBlockState(), 3);
    level.setBlock(limb.above(), BioshipTissue.body().defaultBlockState(), 3);
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
                BioshipStorage.amount(core, MaterialCost.FLESH) >= 2,
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
    helper.getLevel().setDayTime(1000);
    helper.runAfterDelay(
        40,
        () -> {
          var core = core(ship);
          var level = helper.getLevel();
          Vec3 here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
          var component = BlockPos.containing(here).east(10);
          level.setBlock(component, block("biomancy:bio_forge").defaultBlockState(), 3);
          survey(core);
          var goal = core.gatheringGoal();
          helper.assertTrue(
              goal != null && goal.distanceToSqr(Vec3.atCenterOf(component)) < 1,
              "Eye cannot approach a visible distant component before growth");
          // Server chunk/entity registration is asynchronous. Register inert placeholders before
          // testing same-tick arm interactions; enable each supply once the query index is ready.
          var pendingDrops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
          for (var inert :
              List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT)) {
            var entity =
                new net.minecraft.world.entity.item.ItemEntity(
                    level, here.x + 1, here.y, here.z, new ItemStack(inert));
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(level.addFreshEntity(entity), "Supply entity was rejected");
            pendingDrops.add(entity);
          }
          helper.runAfterDelay(
              20,
              () -> {
                core.vision().look(core, Direction.EAST);
                core.crawl().tick(core, false);
                helper.assertTrue(
                    core.crawl().walking() && core.crawl().gathering(),
                    "Early supply gathering did not sprout walking legs");
                level.removeBlock(component, false);
                afterIndexed(
                    helper,
                    pendingDrops,
                    () -> {
                      core.vision().look(core, Direction.EAST);
                      var drop = pendingDrops.get(0);
                      var near =
                          ship.logicalPose()
                              .transformPosition(Vec3.atCenterOf(core.getBlockPos()).add(1, 0, 0));
                      drop.setPos(near.x, near.y, near.z);
                      drop.setItem(new ItemStack(item("biomancy:elastic_fibers"), 3));
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
                          LivingCoreBlockEntity.droppedSupply(
                                  new ItemStack(item("biomancy:bone_fragments")))
                              && LivingCoreBlockEntity.droppedSupply(new ItemStack(Items.BEEF)),
                          "Food or bone growth stock was excluded");
                      var now = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
                      var far =
                          ship.logicalPose()
                              .transformPosition(
                                  Vec3.atCenterOf(core.getBlockPos()).add(4.5, 0, 0));
                      drop.setPos(far.x, far.y, far.z);
                      drop.setDeltaMovement(Vec3.ZERO);
                      core.gatheringGoal();
                      helper.assertTrue(
                          drop.getDeltaMovement().dot(far.subtract(now)) < 0
                              && core.crawl().reach() != null,
                          "Tentacle cannot grab supplies beyond the old three-block reach");
                      var beyond =
                          ship.logicalPose()
                              .transformPosition(Vec3.atCenterOf(core.getBlockPos()).add(6, 0, 0));
                      drop.setPos(beyond.x, beyond.y, beyond.z);
                      var meal = pendingDrops.get(1);
                      meal.setPos(near.x, near.y, near.z);
                      meal.setItem(new ItemStack(Items.BEEF));
                      core.gatheringGoal();
                      helper.assertTrue(
                          !meal.isAlive()
                              && stomach.queuedFood() == 1
                              && stomach.material("minecraft:beef") == 0,
                          "Dropped meat was stored as construction material instead of entering"
                              + " digestion");
                      helper.assertTrue(
                          !LivingCoreBlockEntity.droppedSupply(
                                  new ItemStack(Create_bioships.CORE.get()))
                              && LivingCoreBlockEntity.droppedSupply(
                                  new ItemStack(Create_bioships.ONE_WAY_WINDOW.get()))
                              && LivingCoreBlockEntity.droppedSupply(
                                  new ItemStack(block("biomancy:bio_forge"))),
                          "Dropped block filter rejected supported blocks or allowed a bioship"
                              + " core");
                      int flesh = BioshipStorage.amount(core, MaterialCost.FLESH);
                      var blocks = pendingDrops.get(2);
                      blocks.setPos(near.x, near.y, near.z);
                      blocks.setItem(new ItemStack(Create_bioships.ONE_WAY_WINDOW.get(), 2));
                      core.gatheringGoal();
                      core.gatheringGoal();
                      helper.assertTrue(
                          !blocks.isAlive()
                              && BioshipStorage.amount(core, MaterialCost.FLESH) == flesh + 1,
                          "Dropped bioship blocks were not reclaimed at reduced yield");
                      var protectedCore = pendingDrops.get(3);
                      protectedCore.setPos(near.x, near.y, near.z);
                      protectedCore.setItem(new ItemStack(Create_bioships.CORE.get()));
                      core.gatheringGoal();
                      helper.assertTrue(
                          protectedCore.isAlive() && protectedCore.getItem().getCount() == 1,
                          "Dropped core was consumed");
                      protectedCore.discard();
                      var savedMouth = core.mouthBlock();
                      level.removeBlock(stomach.getBlockPos(), false);
                      core.gatheringGoal();
                      helper.assertTrue(
                          drop.getItem().getCount() == 2, "Mouth alone collected loose supplies");
                      level.setBlock(
                          stomach.getBlockPos(),
                          Create_bioships.STOMACH.get().defaultBlockState(),
                          3);
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
              });
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
          (block == Create_bioships.INTESTINE.get()
                  ? block.defaultBlockState().setValue(IntestineBlock.PAID, true)
                  : block.defaultBlockState())
              .getDrops(
                  new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                      .withParameter(
                          net.minecraft.world.level.storage.loot.parameters.LootContextParams
                              .ORIGIN,
                          BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
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
    level.setBlock(hull, BioshipTissue.body().defaultBlockState(), 3);
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
        BioshipTissue.isTissue(level.getBlockState(hull)), "Hull decayed during grace period");
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

  private static final class VulnerablePlayer extends InteractionPlayer {
    VulnerablePlayer(net.minecraft.server.level.ServerLevel level, String name) {
      super(level, name);
      try {
        var field =
            net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        field.setAccessible(true);
        field.setInt(this, 0);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
      return getAbilities().invulnerable || isSpectator();
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "aggressive_hatchling")
  public static void
      aggressive_small_arms_attack_owner_and_creatures_but_respect_sight_and_creative(
          GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var owner = new VulnerablePlayer(level, "ScuttleCaptain");
    owner.setGameMode(GameType.SURVIVAL);
    owner.setHealth(20);
    owner.setNoGravity(true);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    owner.setPos(here.x + 2, here.y, here.z);
    level.addNewPlayer(owner);
    var creative = new VulnerablePlayer(level, "CreativeWitness");
    creative.setGameMode(GameType.CREATIVE);
    creative.setPos(here.x + 1, here.y, here.z);
    level.addNewPlayer(creative);
    var cow = EntityType.COW.create(level);
    cow.setNoAi(true);
    cow.setNoGravity(true);
    cow.setPos(here.x + 3, here.y, here.z);
    level.addFreshEntity(cow);
    var tag = core.saveWithFullMetadata(level.registryAccess());
    tag.putInt("Trust", -20);
    tag.putInt("Satiety", 1200);
    tag.putUUID("Captain", owner.getUUID());
    core.loadAdditional(tag, level.registryAccess());
    helper
        .startSequence()
        .thenWaitUntil(
            () -> {
              var entities =
                  level.getEntitiesOfClass(
                      net.minecraft.world.entity.LivingEntity.class,
                      new AABB(here, here).inflate(8));
              helper.assertTrue(
                  entities.contains(owner) && entities.contains(cow),
                  "Waiting for player and creature fixture chunks to finish loading");
            })
        .thenExecute(
            () -> {
              core.vision().look(core, Direction.EAST);
              helper.assertTrue(
                  core.aggressive() && BioshipDefense.target(core) == owner,
                  "Aggressive hatchling did not target its survival owner: aggressive="
                      + core.aggressive()
                      + " attackable="
                      + TentacleBlockEntity.attackable(owner)
                      + " visible="
                      + core.vision().visible(core, owner.getBoundingBox().getCenter())
                      + " target="
                      + BioshipDefense.target(core)
                      + " entities="
                      + level
                          .getEntitiesOfClass(
                              net.minecraft.world.entity.LivingEntity.class,
                              new AABB(here, here).inflate(8))
                          .size());
              helper.assertTrue(
                  !TentacleBlockEntity.attackable(creative),
                  "Creative player was an attack target");
              helper.runAfterDelay(
                  60,
                  () -> {
                    helper.assertTrue(
                        owner.getHealth() < 20,
                        "Aggressive hatchling never damaged the survival player");
                    level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                    var wall =
                        BlockPos.containing(
                                BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
                            .east();
                    for (int y = 0; y < 4; y++)
                      level.setBlock(wall.above(y), Blocks.OBSIDIAN.defaultBlockState(), 3);
                    helper.runAfterDelay(
                        30,
                        () -> {
                          helper.assertTrue(
                              cow.getHealth() == cow.getMaxHealth(),
                              "Small arm attacked through a world wall");
                          for (int y = 0; y < 4; y++) level.removeBlock(wall.above(y), false);
                          helper.runAfterDelay(
                              40,
                              () -> {
                                try {
                                  helper.assertTrue(
                                      cow.getHealth() < cow.getMaxHealth(),
                                      "Aggression did not attack visible creatures");
                                  helper.assertTrue(
                                      creative.getHealth() == 20, "Creative player was damaged");
                                  helper.succeed();
                                } finally {
                                  cow.discard();
                                  level.removePlayerImmediately(
                                      creative, Entity.RemovalReason.DISCARDED);
                                  cleanup(helper, ship);
                                }
                              });
                        });
                  });
            });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "needs_priorities")
  public static void missing_organs_outrank_near_sacks_then_full_storage_comes_first(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    level.setDayTime(1000);
    // Priority is tested in daylight; darkness intentionally limits distant target detection.
    // Let the new fixture chunks receive sky lighting before testing distant source priority.
    helper.runAfterDelay(
        40,
        () -> {
          var here =
              BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
          var sack = here.south(2);
          var forge = here.north(10);
          var decomposer = here.west(11);
          var digester = here.east(12);
          level.setBlock(sack, block("biomancy:storage_sac").defaultBlockState(), 3);
          var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, sack, null);
          inventory.insertItem(0, new ItemStack(Items.BEEF, 32), false);
          level.setBlock(forge, block("biomancy:bio_forge").defaultBlockState(), 3);
          level.setBlock(decomposer, block("biomancy:decomposer").defaultBlockState(), 3);
          level.setBlock(digester, block("biomancy:digester").defaultBlockState(), 3);
          survey(core);
          var best = BioshipPriorities.bestWorld(core);
          helper.assertTrue(
              best != null && best.rank() == 0 && best.pos().equals(forge),
              "Nearby food sack displaced a missing mouth goal: range="
                  + core.vision().maximumDistance(core)
                  + " sky="
                  + level.getMaxLocalRawBrightness(forge.above())
                  + " best="
                  + best
                  + " forge="
                  + core.vision().exposed(core, forge)
                  + " decomposer="
                  + core.vision().exposed(core, decomposer)
                  + " digester="
                  + core.vision().exposed(core, digester));
          survey(core);
          BioshipIntegration.assimilate(core, ship);
          helper.assertTrue(
              core.organMove() == null,
              "Grabbed low-priority sack instead of approaching missing organs");
          helper.assertTrue(
              core.gatheringGoal().distanceToSqr(Vec3.atCenterOf(forge)) < 1,
              "Travel goal ignored missing mouth");
          level.setBlock(
              core.getBlockPos().north(),
              Create_bioships.FEEDING_MOUTH.get().defaultBlockState(),
              3);
          best = BioshipPriorities.bestWorld(core);
          helper.assertTrue(
              best != null
                  && best.rank() == 0
                  && (best.pos().equals(decomposer) || best.pos().equals(digester)),
              "Missing stomach lost priority after mouth formation");
          level.setBlock(
              core.getBlockPos().west(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
          DigestiveSystem.connect(core, ship);
          best = BioshipPriorities.bestWorld(core);
          helper.assertTrue(
              best != null && best.pos().equals(sack),
              "Completed digestive organs did not switch priority to food storage");
          var first = best.pos();
          level.setBlock(here.south(3), block("biomancy:storage_sac").defaultBlockState(), 3);
          helper.assertTrue(
              BioshipPriorities.bestWorld(core).pos().equals(first),
              "Equal priority target selection oscillated");
          for (var pos : List.of(sack, forge, decomposer, digester, here.south(3)))
            level.removeBlock(pos, false);
          finish(helper, ship, () -> {});
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "surface_eyes")
  public static void sensory_eyes_spend_reserves_extend_sight_and_relocate_through_tissue(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var tag = core.saveWithFullMetadata(level.registryAccess());
    tag.putInt("Stage", 2);
    tag.putInt("Satiety", 1200);
    core.loadAdditional(tag, level.registryAccess());
    var host = core.getBlockPos().north(2);
    level.setBlock(host, BioshipTissue.body().defaultBlockState(), 3);
    var target = BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(0, 0, -8));
    helper.assertTrue(
        !core.vision().visible(core, target), "Body tissue did not block the original eye");
    core.vision().growEyes(core);
    helper.assertTrue(
        BioshipDecay.blocks(ship).stream()
            .noneMatch(p -> level.getBlockState(p).is(Create_bioships.SURFACE_EYE.get())),
        "Eye grew without construction resources");
    stomach.retainFlesh(10 + EmergencyReserve.TARGET.flesh());
    stomach.retain(
        new ItemStack(item("biomancy:elastic_fibers"), 10 + EmergencyReserve.TARGET.fiber()));
    stomach.retain(new ItemStack(item(MaterialCost.BONE), EmergencyReserve.TARGET.bone()));
    var eye = host.north();
    for (int attempt = 0;
        attempt < 6 && !level.getBlockState(eye).is(Create_bioships.SURFACE_EYE.get());
        attempt++) core.vision().growEyes(core);
    long eyeCount =
        BioshipDecay.blocks(ship).stream()
            .filter(p -> level.getBlockState(p).is(Create_bioships.SURFACE_EYE.get()))
            .count();
    helper.assertTrue(
        level.getBlockState(eye).is(Create_bioships.SURFACE_EYE.get())
            && level.getBlockState(eye).getValue(SurfaceEyeBlock.FACING) == Direction.NORTH,
        "No outward sensory eye grew on blocked hull side: connected="
            + stomach.connected(core)
            + " mouth="
            + core.hasMouth()
            + " mature="
            + core.isMature()
            + " eyes="
            + BioshipDecay.blocks(ship).stream()
                .filter(p -> level.getBlockState(p).is(Create_bioships.SURFACE_EYE.get()))
                .toList()
            + " flesh="
            + stomach.material("biomancy:flesh_bits")
            + " fiber="
            + stomach.material("biomancy:elastic_fibers"));
    helper.assertTrue(
        stomach.material("biomancy:flesh_bits")
                == 10 + EmergencyReserve.TARGET.flesh() - 2 * eyeCount
            && stomach.material("biomancy:elastic_fibers")
                == 10 + EmergencyReserve.TARGET.fiber() - eyeCount,
        "Sensory eye did not spend flesh and fibers");
    helper.assertTrue(
        core.vision().visible(core, target), "Auxiliary eye did not extend line of sight");
    for (int x = 1; x <= 3; x++)
      level.setBlock(host.east(x), BioshipTissue.body().defaultBlockState(), 3);
    var wall =
        BlockPos.containing(
            BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(0, 0, -6)));
    for (int y = 0; y < 3; y++)
      level.setBlock(wall.above(y), Blocks.OBSIDIAN.defaultBlockState(), 3);
    core.vision().growEyes(core);
    helper.assertTrue(
        core.organMove() != null
            && core.organMove().source().equals(eye)
            && !core.organMove().external(),
        "Blocked sensory eye was not moved through its own connected tissue");
    helper.assertTrue(
        stomach.material("biomancy:flesh_bits")
            == 10 + EmergencyReserve.TARGET.flesh() - 2 * eyeCount,
        "Relocating an existing eye charged its construction cost again");
    for (int y = 0; y < 3; y++) level.removeBlock(wall.above(y), false);
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "mouth_face_and_grid")
  public static void mouth_faces_outward_only_accepts_front_clicks_and_cores_cannot_join_grids(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var mouth = core.getBlockPos().east();
    level.setBlock(mouth, block("biomancy:bio_forge").defaultBlockState(), 3);
    BioshipIntegration.finishAssimilation(core, mouth);
    helper.assertTrue(
        level.getBlockState(mouth).getValue(FeedingMouthBlock.FACING) == Direction.EAST,
        "New mouth faces the core or ground instead of outward");
    helper.assertTrue(
        level.getCapability(Capabilities.ItemHandler.BLOCK, mouth, Direction.NORTH) == null
            && level.getCapability(Capabilities.ItemHandler.BLOCK, mouth, Direction.EAST) != null,
        "Automated item feeding can access a side without teeth");
    var stomachPos = core.getBlockPos().west();
    level.setBlock(stomachPos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    var user = new InteractionPlayer(level, "MouthFaceTester");
    var physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(mouth));
    user.setPos(physical.x + 1, physical.y, physical.z);
    user.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 2));
    user.gameMode.useItemOn(
        user,
        level,
        user.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(mouth), Direction.NORTH, mouth, false));
    helper.assertTrue(
        user.getMainHandItem().getCount() == 2
            && ((StomachBlockEntity) level.getBlockEntity(stomachPos)).queuedFood() == 0,
        "Side click fed the mouth");
    user.gameMode.useItemOn(
        user,
        level,
        user.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(mouth), Direction.EAST, mouth, false));
    helper.assertTrue(user.getMainHandItem().getCount() == 1, "Toothed front face refused food");
    user.closeContainer();
    user.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new ItemStack(Create_bioships.CORE_ITEM.get(), 2));
    var placement =
        new net.minecraft.world.item.context.BlockPlaceContext(
            user,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            user.getMainHandItem(),
            new BlockHitResult(
                Vec3.atCenterOf(core.getBlockPos()), Direction.UP, core.getBlockPos(), false));
    helper.assertTrue(
        Create_bioships.CORE.get().getStateForPlacement(placement) == null,
        "Core can be placed on an existing Sable grid");
    var result =
        Create_bioships.CORE_ITEM
            .get()
            .useOn(
                new net.minecraft.world.item.context.UseOnContext(
                    user,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(core.getBlockPos()),
                        Direction.UP,
                        core.getBlockPos(),
                        false)));
    helper.assertTrue(
        !result.consumesAction() && user.getMainHandItem().getCount() == 2,
        "Rejected grid placement consumed a core item");
    var plain =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(12);
    level.setBlock(plain, Blocks.STONE.defaultBlockState(), 3);
    var grid =
        SubLevelAssemblyHelper.assembleBlocks(
            level, plain, List.of(plain), new BoundingBox3i(plain, plain));
    var blockPos = grid.getPlot().getCenterBlock();
    var plainContext =
        new net.minecraft.world.item.context.BlockPlaceContext(
            user,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            user.getMainHandItem(),
            new BlockHitResult(Vec3.atCenterOf(blockPos), Direction.UP, blockPos, false));
    helper.assertTrue(
        Create_bioships.CORE.get().getStateForPlacement(plainContext) == null,
        "Core placement was allowed on a non-bioship Sable grid");
    cleanup(helper, grid);
    finish(helper, ship, () -> {});
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1400, batch = "core_scouting")
  public static void core_slides_on_own_body_preserves_organs_and_returns_inside_for_growth(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var original = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, original, false);
    stomach.retain(new ItemStack(Items.BARRIER, 5));
    stomach.retainFlesh(6);
    stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 6));
    var home = original.getBlockPos();
    var mouth = original.mouthBlock();
    var stomachPos = stomach.getBlockPos();
    var sentinel =
        BuiltInRegistries.ITEM.stream()
            .filter(
                item ->
                    item != Items.AIR
                        && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
            .map(item -> new ItemStack(item, 7))
            .filter(
                stack ->
                    !stomach.canEnqueue(stack, false)
                        && !stack.is(BioshipIntegration.constructionMaterial())
                        && !stack.is(
                            net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM,
                                ResourceLocation.parse("create_bioships:levitite_amalgam"))))
            .findFirst()
            .orElseThrow();
    ((FeedingMouthBlockEntity) level.getBlockEntity(mouth)).setItem(0, sentinel.copy());
    level.setBlock(
        home.above(),
        Create_bioships.INTESTINE
            .get()
            .defaultBlockState()
            .setValue(IntestineBlock.MANUAL, true)
            .setValue(IntestineBlock.PAID, true),
        3);
    level.setBlock(
        home.above(2),
        Create_bioships.INTESTINE
            .get()
            .defaultBlockState()
            .setValue(IntestineBlock.MANUAL, true)
            .setValue(IntestineBlock.PAID, true),
        3);
    var wall =
        BlockPos.containing(BodyCoordinates.physical(original.amoebaComponents(), BlockPos.ZERO))
            .north(3);
    for (int x = -2; x <= 2; x++)
      for (int y = 0; y < 2; y++)
        level.setBlock(wall.offset(x, y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
    var tag = original.saveWithFullMetadata(level.registryAccess());
    UUID captain =
        UUID.nameUUIDFromBytes(
            "scouting-captain".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    tag.putUUID("Captain", captain);
    tag.putInt("Trust", 75);
    original.loadAdditional(tag, level.registryAccess());
    int before = original.coreMotion().coverage(original, home);
    double health = original.body().health();
    original.coreMotion().scoutNow(original);
    helper.assertTrue(
        original.coreMotion().active(), "Blocked hatchling did not begin relocating its core");
    helper.assertTrue(
        original.getBlockState().getValue(LivingCoreBlock.RELOCATING),
        "Core relocation has no travel render state");
    var saved = original.saveWithFullMetadata(level.registryAccess());
    original.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(original.coreMotion().active(), "Core travel did not survive save/load");
    helper.runAfterDelay(
        100,
        () -> {
          var moved = core(ship);
          helper.assertTrue(
              !moved.getBlockPos().equals(home) && moved.getBlockPos().getY() > home.getY(),
              "Core did not move higher on its own tissue");
          helper.assertTrue(
              moved.coreMotion().coverage(moved, moved.getBlockPos()) > before,
              "Relocating the core did not improve panoramic sight");
          helper.assertTrue(
              level.getBlockEntity(stomachPos) instanceof StomachBlockEntity retained
                  && retained.material("minecraft:barrier") == 5
                  && level.getBlockEntity(mouth) instanceof FeedingMouthBlockEntity
                  && ((FeedingMouthBlockEntity) level.getBlockEntity(mouth)).getItem(0).getCount()
                      == 7
                  && ((FeedingMouthBlockEntity) level.getBlockEntity(mouth))
                      .getItem(0)
                      .is(sentinel.getItem()),
              "Core scouting moved or lost digestive organs/inventories: stomach="
                  + level.getBlockEntity(stomachPos)
                  + " stomachState="
                  + level.getBlockState(stomachPos)
                  + " mouth="
                  + level.getBlockEntity(mouth)
                  + " mouthState="
                  + level.getBlockState(mouth)
                  + " items="
                  + (level.getBlockEntity(mouth) instanceof FeedingMouthBlockEntity m
                      ? m.getItem(0)
                      : "missing"));
          helper.assertTrue(
              moved.body().health() == health
                  && captain.equals(moved.creator())
                  && moved.saveWithFullMetadata(level.registryAccess()).getInt("Trust") == 75,
              "Authorized core motion injured the organism or cost trust");
          for (int x = -2; x <= 2; x++)
            for (int y = 0; y < 2; y++) level.removeBlock(wall.offset(x, y, 0), false);
          var currentStomach = DigestiveSystem.stomach(moved);
          currentStomach.retainFlesh(140);
          currentStomach.retain(new ItemStack(item("biomancy:bone_fragments"), 20));
          currentStomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 54));
        });
    helper.runAfterDelay(
        1250,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var grown = core(ship);
                  helper.assertTrue(
                      grown.isMature(),
                      "Scout did not return and grow after the site became suitable: "
                          + diagnose(grown));
                  helper.assertTrue(
                      grown.coreMotion().home(grown).equals(home)
                          && CoreMotion.protectedInside(grown, grown.getBlockPos()),
                      "Growth lost its body anchor or left the core exposed");
                  helper.assertTrue(
                      grown.getBlockState().getValue(LivingCoreBlock.CONCEALED),
                      "Growing hull did not conceal its internal core");
                  helper.assertTrue(
                      !grown.getBlockState().getValue(LivingCoreBlock.RELOCATING),
                      "Core remained in a traveling render state");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "ground_route")
  public static void scuttle_routes_around_terrain_and_checks_attached_body_clearance(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    level.setBlock(core.getBlockPos().north(), BioshipTissue.body().defaultBlockState(), 3);
    var wall = BlockPos.containing(here).east(3);
    for (int z = -3; z <= 3; z++)
      for (int y = 0; y < 5; y++)
        level.setBlock(wall.offset(0, y, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
    var route = new GroundRoute();
    helper.assertTrue(
        route.plan(core, here.add(8, 0, 0)),
        "No route was found around a reachable terrain obstruction");
    helper.assertTrue(
        route.points().stream().anyMatch(p -> Math.abs(p.z - here.z) >= 4),
        "Route runs directly through terrain rather than around it");
    for (var point : route.points()) {
      helper.assertTrue(
          level.getBlockState(BlockPos.containing(point)).isAir(),
          "Route puts the core inside a solid block");
      var attached = BlockPos.containing(point.add(0, 0, -1));
      helper.assertTrue(
          level.getBlockState(attached).isAir(), "Route clips an attached body block into terrain");
    }
    for (int z = -3; z <= 3; z++)
      for (int y = 0; y < 5; y++) level.removeBlock(wall.offset(0, y, z), false);
    for (var pos :
        BlockPos.betweenClosed(
            BlockPos.containing(here).offset(4, 0, -4), BlockPos.containing(here).offset(10, 0, 4)))
      level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        route.plan(core, here.add(8, 1, 0))
            && route.points().stream().anyMatch(p -> p.y > here.y + .5),
        "Route cannot climb a safe one-block terrace");
    for (var pos :
        BlockPos.betweenClosed(
            BlockPos.containing(here).offset(4, 0, -4), BlockPos.containing(here).offset(10, 0, 4)))
      level.removeBlock(pos, false);
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "window_eye_visibility")
  public static void window_eyes_are_hidden_from_inside_and_keep_outward_vision(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var window = core.getBlockPos().east(2);
    level.setBlock(
        window,
        Create_bioships.ONE_WAY_WINDOW
            .get()
            .defaultBlockState()
            .setValue(OneWayWindowBlock.FACING, Direction.EAST),
        3);
    var outside = window.east();
    var inside = window.west();
    level.setBlock(
        outside,
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.FACING, Direction.EAST),
        3);
    helper.assertTrue(
        level.getBlockState(outside).getValue(SurfaceEyeBlock.WINDOW_MOUNTED)
            && !level.getBlockState(outside).getValue(SurfaceEyeBlock.WINDOW_INTERNAL),
        "Exterior window eye does not use its one-sided model");
    level.setBlock(
        inside,
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.FACING, Direction.WEST),
        3);
    helper.assertTrue(
        level.getBlockState(inside).getValue(SurfaceEyeBlock.WINDOW_INTERNAL),
        "An eye attached to the inner window face remains visible inside");
    helper.assertTrue(
        level.getBlockState(inside).getShape(level, inside).isEmpty(),
        "Concealed window eye still has a visible selection outline");
    try (var stream =
        BioshipGameTests.class
            .getClassLoader()
            .getResourceAsStream("assets/create_bioships/models/block/surface_eye_window.json")) {
      var model =
          com.google.gson.JsonParser.parseReader(
                  new java.io.InputStreamReader(
                      Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      var faces =
          model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
      helper.assertTrue(
          faces.size() == 1 && faces.has("north"),
          "Window eye model has back or edge faces visible from the cabin");
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
    level.removeBlock(outside, false);
    level.setBlock(
        inside,
        level
            .getBlockState(inside)
            .setValue(SurfaceEyeBlock.WINDOW_MOUNTED, false)
            .setValue(SurfaceEyeBlock.WINDOW_INTERNAL, false),
        2);
    helper.assertTrue(
        core.vision()
            .visible(
                core, BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(8, 0, 0))),
        "Concealed inner window eye no longer sees outward");
    helper.assertTrue(
        level.getBlockState(inside).getValue(SurfaceEyeBlock.WINDOW_INTERNAL),
        "A saved eye did not refresh its concealed window mount");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "tube_sight")
  public static void exposed_intestines_do_not_block_sight_but_encased_tissue_and_walls_do(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    core.vision().look(core, Direction.EAST);
    var pipe = core.getBlockPos().east();
    var target = BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(4, 0, 0));
    level.setBlock(pipe, Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    helper.assertTrue(
        core.vision().visible(core, target), "Exposed intestine on the ship blocks sight");
    var worldPipe =
        BlockPos.containing(
            BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(2, 0, 0)));
    level.setBlock(worldPipe, Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    helper.assertTrue(
        core.vision().visible(core, target), "Exposed world-space intestine blocks sight");
    var limb =
        new TentacleBlockEntity(
            core.getBlockPos(), Create_bioships.TENTACLE.get().defaultBlockState());
    limb.setLevel(level);
    var prey = EntityType.COW.create(level);
    helper.assertTrue(
        limb.visible(
            BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO),
            target,
            prey,
            core,
            ship),
        "Tentacle targeting is blocked by an exposed tube");
    level.setBlock(
        worldPipe, level.getBlockState(worldPipe).setValue(IntestineBlock.ENCASED, true), 3);
    helper.assertTrue(
        !limb.visible(
            BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO),
            target,
            prey,
            core,
            ship),
        "Tentacle targeting sees through a flesh-encased tube");
    level.setBlock(
        worldPipe, level.getBlockState(worldPipe).setValue(IntestineBlock.ENCASED, false), 3);

    var wallBehind =
        BlockPos.containing(
            BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(3, 0, 0)));
    level.setBlock(wallBehind, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !limb.visible(
            BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO),
            target,
            prey,
            core,
            ship),
        "Ignoring a tube also ignored a solid wall behind it");
    level.removeBlock(wallBehind, false);
    level.setBlock(pipe, level.getBlockState(pipe).setValue(IntestineBlock.ENCASED, true), 3);
    helper.assertTrue(
        !core.vision().visible(core, target),
        "Encased intestine reveals targets through its flesh casing");
    level.setBlock(pipe, level.getBlockState(pipe).setValue(IntestineBlock.ENCASED, false), 3);
    level.setBlock(worldPipe, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !core.vision().visible(core, target), "Ignoring tubes also ignored solid world obstacles");
    level.removeBlock(worldPipe, false);
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "directional_cones")
  public static void core_and_surface_eyes_use_forward_cones_and_respect_occlusion(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    core.vision().look(core, Direction.EAST);
    helper.assertTrue(
        core.vision().visible(core, root.add(5, 0, 0)), "Core cannot see directly forward");
    helper.assertTrue(core.vision().views(core).size() == 6, "Core is missing face eyes");
    for (var face : Direction.values())
      if (face != Direction.DOWN)
        helper.assertTrue(
            core.vision().visible(core, root.relative(face, 5)),
            "Core cannot see through face " + face);
    var eastView =
        core.vision().views(core).stream()
            .filter(v -> v.forward().x > .9)
            .findFirst()
            .orElseThrow();
    helper.assertTrue(
        !eastView.contains(root.add(-5, 0, 0)) && !eastView.contains(root.add(0, 0, 5)),
        "Individual core eye sees outside its cone");
    helper.assertTrue(
        eastView.contains(root.add(2.5, 0, Math.sqrt(3) * 2.5)), "Cone boundary is excluded");
    helper.assertTrue(!eastView.contains(root.add(2.4, 0, 4.4)), "Individual cone is too wide");
    var host = core.getBlockPos().north(2);
    level.setBlock(host, BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(
        host.north(),
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.FACING, Direction.NORTH),
        3);
    level.setBlock(
        core.getBlockPos(), core.getBlockState().setValue(LivingCoreBlock.CONCEALED, true), 2);
    helper.assertTrue(
        core.vision().visible(core, root.add(0, 0, -8)), "Surface eye cannot see ahead");
    helper.assertTrue(
        !core.vision().visible(core, root.add(8, 0, -3.2)),
        "Surface eye sees sideways through clear space");
    var wall = BlockPos.containing(root.add(0, 0, -6));
    level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !core.vision().visible(core, root.add(0, 0, -8)), "A cone sees through a world wall");
    level.removeBlock(wall, false);
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "tube_conservation")
  public static void starter_tubes_are_free_additions_are_paid_and_retracted_tissue_is_reused(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    stomach.paidTubeStock = 0;
    helper.assertTrue(
        stomach.connected(core) && stomach.tubeBootstrapped,
        "Fresh stomach did not receive its initial free connection");
    var starter = new HashSet<>(stomach.gutPath);
    helper.assertTrue(!starter.isEmpty(), "Fixture has no initial tubes");
    for (var p : starter) {
      var state = level.getBlockState(p);
      helper.assertTrue(!state.getValue(IntestineBlock.PAID), "Starter tube was charged");
      var drops =
          state.getDrops(
              new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                  .withParameter(
                      net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                      ship.logicalPose().transformPosition(Vec3.atCenterOf(p)))
                  .withParameter(
                      net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                      ItemStack.EMPTY));
      helper.assertTrue(drops.isEmpty(), "Free starter tubes can be farmed for item drops");
    }
    var sac = core.getBlockPos().east(4);
    level.setBlock(sac, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        !stomach.sacPaths.containsKey(sac), "Unfunded additional intestines grew for free");
    stomach.retainFlesh(32);
    stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 32));
    DigestiveSystem.connect(core, ship);
    int cost = 32 - stomach.material("biomancy:elastic_fibers");
    helper.assertTrue(
        cost > 0
            && stomach.material("biomancy:flesh_bits") == 32 - cost
            && stomach.sacPaths.containsKey(sac),
        "New tube material did not consume matching flesh and fiber");
    level.removeBlock(sac, false);
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        stomach.paidTubeStock > 0,
        "Obsolete paid intestines were not reabsorbed into reusable tissue");
    int flesh = stomach.material("biomancy:flesh_bits"),
        fiber = stomach.material("biomancy:elastic_fibers");
    var saved = stomach.saveWithFullMetadata(level.registryAccess());
    stomach.loadWithComponents(saved, level.registryAccess());
    var second = core.getBlockPos().east(3);
    level.setBlock(second, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        stomach.sacPaths.containsKey(second)
            && stomach.material("biomancy:flesh_bits") == flesh
            && stomach.material("biomancy:elastic_fibers") == fiber,
        "Rerouting charged for material already available to reuse");
    var unused = core.getBlockPos().south(3);
    level.setBlock(
        unused,
        Create_bioships.INTESTINE
            .get()
            .defaultBlockState()
            .setValue(IntestineBlock.MANUAL, true)
            .setValue(IntestineBlock.PAID, true),
        3);
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        level.getBlockState(unused).is(Create_bioships.INTESTINE.get()),
        "Manual intestine was reabsorbed without permission");
    var free = core.getBlockPos().south();
    level.setBlock(free, Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    int missing = core.missingBiomass();
    core.scavenging().finish(core, free, false);
    helper.assertTrue(
        core.missingBiomass() == missing
            && stomach.material("biomancy:flesh_bits") == flesh
            && stomach.material("biomancy:elastic_fibers") == fiber,
        "Recycling starter tissue generated free growth material");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "eye_reabsorption")
  public static void redundant_automatic_eyes_are_reabsorbed_without_removing_needed_organs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    for (int x = 0; x < 2; x++) {
      var host = core.getBlockPos().offset(x, 0, -3);
      level.setBlock(host, BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(
          host.north(),
          Create_bioships.SURFACE_EYE
              .get()
              .defaultBlockState()
              .setValue(SurfaceEyeBlock.FACING, Direction.NORTH)
              .setValue(SurfaceEyeBlock.AUTOMATIC, true),
          3);
    }
    helper.assertTrue(
        core.vision().reabsorb(core), "Duplicate automatic sensory eyes were not reclaimed");
    helper.assertTrue(
        stomach.material("biomancy:flesh_bits") == 1
            && stomach.material("biomancy:elastic_fibers") == 1,
        "Eye refund exceeded or missed the partial growth refund");
    helper.assertTrue(!core.vision().reabsorb(core), "Last useful eye was also reabsorbed");
    helper.assertTrue(
        core.hasMouth() && DigestiveSystem.stomach(core) == stomach,
        "Reabsorption removed essential digestive organs");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "plant_glass_sight")
  public static void cones_see_through_plants_and_glass_but_not_leaves_or_stone(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    core.vision().look(core, Direction.EAST);
    var obstacle =
        BlockPos.containing(
            BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(2, 0, 0)));
    var target = BodyCoordinates.physical(core.amoebaComponents(), new BlockPos(5, 0, 0));
    for (var block :
        List.of(
            Blocks.SHORT_GRASS,
            Blocks.TALL_GRASS,
            Blocks.DANDELION,
            Blocks.SUGAR_CANE,
            Blocks.CACTUS,
            Blocks.BAMBOO,
            Blocks.GLASS,
            Blocks.GLASS_PANE,
            Blocks.RED_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS_PANE,
            Blocks.TINTED_GLASS)) {
      level.setBlock(obstacle, block.defaultBlockState(), 2);
      helper.assertTrue(
          core.vision().visible(core, target),
          "Plant or glass blocks sight: " + BuiltInRegistries.BLOCK.getKey(block));
    }
    for (var block :
        List.of(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, Blocks.STONE, Blocks.GRASS_BLOCK)) {
      level.setBlock(obstacle, block.defaultBlockState(), 2);
      helper.assertTrue(
          !core.vision().visible(core, target),
          "Opaque foliage or terrain does not block sight: "
              + BuiltInRegistries.BLOCK.getKey(block));
    }
    level.removeBlock(obstacle, false);
    finish(helper, ship, () -> {});
  }

  private static final class ViewPlayer extends InteractionPlayer {
    int chunks = 2;

    ViewPlayer(net.minecraft.server.level.ServerLevel level) {
      super(level, "VisionClient");
    }

    @Override
    public int requestedViewDistance() {
      return chunks;
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "sight_light_distance")
  public static void sight_range_matches_client_and_server_limits_and_changes_with_light(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var list = level.getServer().getPlayerList();
    int previous = list.getViewDistance();
    list.setViewDistance(3);
    var client = new ViewPlayer(level);
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    client.setPos(root.x, root.y, root.z);
    level.addNewPlayer(client);
    helper.assertTrue(
        BioshipVision.distanceLimit(list.getViewDistance(), client) == 32,
        "Client render distance was not used");
    helper.assertTrue(
        core.vision().maximumDistance(core) == 48,
        "Unconnected fake player shortened actual sight range");
    client.chunks = 8;
    helper.assertTrue(
        BioshipVision.distanceLimit(list.getViewDistance(), client) == 48,
        "Eye range exceeded the server view distance");
    list.setViewDistance(2);
    core.vision().look(core, Direction.EAST);
    var center = BlockPos.containing(root).offset(10, 1, 0);
    for (var p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2))) {
      boolean shell =
          p.getX() == center.getX() - 2
              || p.getX() == center.getX() + 2
              || p.getZ() == center.getZ() - 2
              || p.getZ() == center.getZ() + 2
              || p.getY() == center.getY() - 1
              || p.getY() == center.getY() + 3;
      if (shell)
        level.setBlock(
            p,
            p.getX() == center.getX() - 2
                ? Blocks.TINTED_GLASS.defaultBlockState()
                : Blocks.STONE.defaultBlockState(),
            3);
    }
    level.setBlock(center, Blocks.STONE.defaultBlockState(), 3);
    var point = Vec3.atCenterOf(center);
    helper.runAfterDelay(
        40,
        () -> {
          core.vision().look(core, Direction.EAST);
          helper.assertTrue(
              !core.vision().visible(core, point),
              "Dark target is visible at the full daylight range");
          level.setBlock(center.below().west(), Blocks.SEA_LANTERN.defaultBlockState(), 3);
          helper.succeedWhen(
              () -> {
                core.vision().look(core, Direction.EAST);
                var view = core.vision().views(core).getFirst();
                var surface =
                    BlockPos.containing(
                        point.add(view.origin().subtract(point).normalize().scale(.75)));
                helper.assertTrue(
                    core.vision().visible(core, point),
                    "Lighting a solid target did not restore sight: light="
                        + level.getMaxLocalRawBrightness(surface)
                        + " range="
                        + view.range()
                        + " cone="
                        + view.contains(point)
                        + " ray="
                        + BioshipVision.ray(core, view.origin(), point));
                for (var p :
                    BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 3, 2)))
                  level.removeBlock(p, false);
                list.setViewDistance(previous);
                level.removePlayerImmediately(client, Entity.RemovalReason.DISCARDED);
                cleanup(helper, ship);
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 450, batch = "wall_scuttle")
  public static void hatchling_legs_climb_a_real_wall_and_fall_when_the_grip_is_removed(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var wall = BlockPos.containing(root).east();
    var resource = BlockPos.containing(root).above(8);
    for (var p : BlockPos.betweenClosed(wall.offset(0, 0, -2), wall.offset(0, 10, 2)))
      level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(resource, block("biomancy:bio_forge").defaultBlockState(), 3);
    var route = new GroundRoute();
    helper.assertTrue(
        route.plan(core, root.add(0, 7, 0))
            && route.points().stream().anyMatch(p -> p.y > root.y + 4),
        "No vertical wall route was found");
    double[] height = {root.y};
    // Isolate climbing/support from the independent idle-wandering behavior after collection.
    for (int tick = 1; tick < 300; tick++)
      helper.runAfterDelay(tick, () -> core(ship).autonomy().activity(core(ship)));
    helper.runAfterDelay(
        300,
        () -> {
          var current = core(ship);
          height[0] = BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO).y;
          helper.assertTrue(
              height[0] > root.y + 3 && current.hasMouth(),
              "Hatchling did not climb to grab the visible high component: height="
                  + height[0]
                  + " mouth="
                  + current.hasMouth()
                  + " brain="
                  + current.brain().action()
                  + " crawl="
                  + current.crawl().save());
          helper.assertTrue(!current.canFly(), "Wall climbing granted flight");
          for (var p : BlockPos.betweenClosed(wall.offset(0, 0, -2), wall.offset(0, 10, 2)))
            level.removeBlock(p, false);
        });
    helper.runAfterDelay(
        400,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO).y
                          < height[0] - 1,
                      "Hatchling hovered after its wall was removed");
                  level.removeBlock(resource, false);
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "canopy_debug")
  public static void canopy_allows_side_sight_but_leaves_and_base_roofs_still_protect(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var machine = BlockPos.containing(root).east(4);
    level.setBlock(machine, block("biomancy:bio_forge").defaultBlockState(), 3);
    level.setBlock(machine.above(2), Blocks.OAK_LEAVES.defaultBlockState(), 3);
    helper.assertTrue(
        core.vision().exposed(core, machine), "Natural canopy hides an exposed machine");
    var target = Vec3.atCenterOf(machine);
    var data = VisionDebug.inspect(core, target);
    var views = data.getList("Views", net.minecraft.nbt.Tag.TAG_COMPOUND);
    helper.assertTrue(
        views.size() == 6
            && views.stream()
                .anyMatch(
                    t -> ((net.minecraft.nbt.CompoundTag) t).getString("Reason").equals("visible")),
        "Server debug disagrees with visible canopy target");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(),
            level.registryAccess(),
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
    try {
      BioshipNetwork.SightSnapshot.CODEC.encode(buffer, new BioshipNetwork.SightSnapshot(data));
      var decoded = BioshipNetwork.SightSnapshot.CODEC.decode(buffer);
      helper.assertTrue(
          decoded.data().equals(data), "Debug snapshot lost server geometry or reasons in transit");
    } finally {
      buffer.release();
    }
    var leaf = BlockPos.containing(root).east(2);
    level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 3);
    helper.assertTrue(!core.vision().visible(core, target), "Side sight penetrates leaves");
    helper.assertTrue(
        VisionDebug.inspect(core, target)
            .getList("Views", net.minecraft.nbt.Tag.TAG_COMPOUND)
            .stream()
            .anyMatch(
                t -> ((net.minecraft.nbt.CompoundTag) t).getString("Reason").equals("blocked")),
        "Debug did not report occlusion");
    level.removeBlock(leaf, false);
    level.setBlock(machine.above(2), Blocks.OAK_PLANKS.defaultBlockState(), 3);
    helper.assertTrue(
        core.vision().visible(core, target) && !core.vision().exposed(core, machine),
        "Base roof protection disappeared");
    helper.assertTrue(
        VisionDebug.inspect(core, target)
            .getList("Views", net.minecraft.nbt.Tag.TAG_COMPOUND)
            .stream()
            .anyMatch(t -> ((net.minecraft.nbt.CompoundTag) t).getString("Reason").equals("roof")),
        "Debug did not distinguish a roof from ray occlusion");
    level.removeBlock(machine.above(2), false);
    level.removeBlock(machine, false);
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "upward_scouting")
  public static void upward_occlusion_triggers_scouting_even_with_clear_horizontal_sight(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    for (int y = 1; y <= 5; y++)
      level.setBlock(core.getBlockPos().above(y), BioshipTissue.body().defaultBlockState(), 3);
    var wall = BlockPos.containing(root).north(5).above(2);
    for (var p : BlockPos.betweenClosed(wall.offset(-4, 0, 0), wall.offset(4, 1, 0)))
      level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        core.coreMotion().coverage(core, core.getBlockPos()) < 48,
        "Scouting ignores upward obstruction");
    core.coreMotion().scoutNow(core);
    helper.assertTrue(
        core.coreMotion().active() && core.coreMotion().target().getY() > core.getBlockPos().getY(),
        "Core did not choose connected higher tissue for upward sight");
    for (var p : BlockPos.betweenClosed(wall.offset(-4, 0, 0), wall.offset(4, 1, 0)))
      level.removeBlock(p, false);
    finish(helper, ship, () -> {});
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 260, batch = "hostile_all_stages")
  public static void zombies_attack_each_stage_and_even_content_juveniles_retaliate(
      GameTestHelper helper) {
    var ships = new java.util.ArrayList<ServerSubLevel>();
    var zombies = new java.util.ArrayList<net.minecraft.world.entity.monster.Zombie>();
    helper.getLevel().setDayTime(18000);
    for (int stage = 0; stage <= 2; stage++) {
      var ship = assemble(helper);
      ships.add(ship);
      var core = core(ship);
      var data = core.saveWithFullMetadata(helper.getLevel().registryAccess());
      data.putInt("Stage", stage);
      data.putInt("Trust", 80);
      data.putInt("Satiety", 1200);
      core.loadAdditional(data, helper.getLevel().registryAccess());
      BioshipTargetEntity.ensure(core);
      var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
      zombie.setPos(root.x + 2.5, root.y - .5, root.z);
      zombie.setItemSlot(
          net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
      helper.getLevel().addFreshEntity(zombie);
      zombies.add(zombie);
    }
    helper.runAfterDelay(
        180,
        () -> {
          try {
            for (int stage = 0; stage < ships.size(); stage++) {
              var core = core(ships.get(stage));
              var zombie = zombies.get(stage);
              helper.assertTrue(
                  core.body().health() < core.body().capacity(),
                  "Zombie did not damage stage " + stage);
              helper.assertTrue(
                  zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(),
                  "Content stage " + stage + " did not retaliate");
              helper.assertTrue(
                  !core.aggressive(), "Defense required or changed trust into aggression");
            }
            helper.succeed();
          } finally {
            zombies.forEach(net.minecraft.world.entity.Entity::discard);
            ships.forEach(ship -> cleanup(helper, ship));
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "native_ranged_creeper")
  public static void skeletons_and_creepers_acquire_real_bioship_attack_targets(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    level.setDayTime(18000);
    var proxy = BioshipTargetEntity.ensure(core);
    helper.assertTrue(
        BioshipTargetEntity.ensure(core) == proxy,
        "Same-tick attack-surface setup created duplicates");
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var skeleton = net.minecraft.world.entity.EntityType.SKELETON.create(level);
    skeleton.setPos(root.x + 9, root.y - .5, root.z);
    skeleton.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    skeleton.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    var creeper = net.minecraft.world.entity.EntityType.CREEPER.create(level);
    creeper.setPos(root.x - 7, root.y - .5, root.z);
    level.setChunkForced(
        skeleton.blockPosition().getX() >> 4, skeleton.blockPosition().getZ() >> 4, true);
    level.addFreshEntity(skeleton);
    level.addFreshEntity(creeper);
    boolean[] acquired = {false, false};
    for (int tick = 1; tick <= 100; tick++)
      helper.runAfterDelay(
          tick,
          () -> {
            acquired[0] |=
                skeleton.getTarget() instanceof BioshipTargetEntity target
                    && target.belongsTo(core);
            acquired[1] |=
                creeper.getTarget() instanceof BioshipTargetEntity target && target.belongsTo(core);
          });
    helper.runAfterDelay(
        105,
        () -> {
          try {
            helper.assertTrue(
                acquired[0],
                "Skeleton did not acquire the ship with native target AI: alive="
                    + skeleton.isAlive()
                    + " target="
                    + skeleton.getTarget()
                    + " at="
                    + skeleton.position()
                    + " proxy="
                    + proxy.position()
                    + " visible="
                    + skeleton.getSensing().hasLineOfSight(proxy)
                    + " canAttack="
                    + skeleton.canAttack(proxy)
                    + " surfaces="
                    + level
                        .getEntitiesOfClass(
                            BioshipTargetEntity.class, skeleton.getBoundingBox().inflate(20))
                        .size()
                    + " goal="
                    + new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                            skeleton, BioshipTargetEntity.class, 0, true, false, e -> true)
                        .canUse());
            helper.assertTrue(
                acquired[1],
                "Creeper did not acquire the ship with native target AI: target="
                    + creeper.getTarget()
                    + " at="
                    + creeper.position()
                    + " proxy="
                    + proxy.position()
                    + " alive="
                    + creeper.isAlive()
                    + " visible="
                    + creeper.getSensing().hasLineOfSight(proxy)
                    + " canAttack="
                    + creeper.canAttack(proxy));
            helper.assertTrue(
                !TentacleBlockEntity.attackable(proxy),
                "Ship considers its own attack surface prey");
            helper.succeed();
          } finally {
            skeleton.discard();
            creeper.discard();
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "creeper_core_reflex")
  public static void creeper_reflex_moves_existing_core_tissue_to_a_safer_cell(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    for (int x = 1; x <= 4; x++)
      level.setBlock(core.getBlockPos().east(x), BioshipTissue.body().defaultBlockState(), 3);
    var proxy = BioshipTargetEntity.ensure(core);
    helper.assertTrue(
        BioshipTargetEntity.ensure(core) == proxy,
        "Same-tick attack-surface setup created duplicates");
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var creeper = net.minecraft.world.entity.EntityType.CREEPER.create(level);
    creeper.setNoAi(true);
    creeper.setPos(root.x - 3, root.y - .5, root.z);
    creeper.setTarget(proxy);
    level.addFreshEntity(creeper);
    int before = BioshipIntegration.countBlocks(ship);
    double safety = CoreMotion.blastSafety(core, core.getBlockPos(), creeper);
    var original = core.getBlockPos();
    core.coreMotion().evade(core, creeper);
    helper.assertTrue(
        core.coreMotion().active() && core.coreMotion().target().getX() > original.getX(),
        "Core did not retreat from creeper along connected flesh");
    helper.assertTrue(
        core.coreMotion().duration() == 4, "Emergency relocation is not faster than scouting");
    helper.runAfterDelay(
        15,
        () -> {
          try {
            var moved = core(ship);
            helper.assertTrue(
                CoreMotion.blastSafety(moved, moved.getBlockPos(), creeper) > safety,
                "Core moved into more blast exposure");
            helper.assertTrue(
                BioshipIntegration.countBlocks(ship) == before,
                "Evasion created free tissue or discarded body blocks");
            moved.body().die(moved);
            BioshipTargetEntity.ensure(moved);
            helper.assertTrue(proxy.isRemoved(), "Dead ship retains a living AI attack target");
            helper.succeed();
          } finally {
            creeper.discard();
            cleanup(helper, ship);
          }
        });
  }

  private static void courseFloor(GameTestHelper helper, BlockPos start) {
    for (var p : BlockPos.betweenClosed(start.offset(-8, -1, -12), start.offset(48, -1, 12))) {
      helper.getLevel().getChunk(p.getX() >> 4, p.getZ() >> 4);
      helper.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
      helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 300, batch = "behavior_tree_wreck_priority")
  public static void needs_tree_prefers_wreck_over_hunting_and_interrupts_for_escape(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, false);
    helper
        .getLevel()
        .setBlock(core.getBlockPos().west(2), BioshipTissue.body().defaultBlockState(), 3);
    var data = core.saveWithFullMetadata(level.registryAccess());
    data.putInt("Satiety", 40);
    core.loadAdditional(data, level.registryAccess());
    level.setDayTime(1000);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var prey = EntityType.CHICKEN.create(level);
    prey.setNoAi(true);
    prey.setNoGravity(true);
    prey.setPos(here.x + 10, here.y, here.z);
    level.addFreshEntity(prey);
    var lamp = prey.blockPosition().below();
    level.setBlock(lamp, Blocks.GLOWSTONE.defaultBlockState(), 3);
    helper.runAfterDelay(
        25,
        () ->
            afterIndexed(
                helper,
                List.of(prey),
                () -> {
                  ServerSubLevel wreck = null;
                  var loose =
                      BlockPos.containing(
                              BodyCoordinates.physical(
                                  core(ship).amoebaComponents(), BlockPos.ZERO))
                          .south(4);
                  try {
                    var current = core(ship);
                    var root = BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO);
                    var source = BlockPos.containing(root).east(6).south(2);
                    level.setBlock(source, Create_bioships.CARAPACE.get().defaultBlockState(), 3);
                    wreck =
                        SubLevelAssemblyHelper.assembleBlocks(
                            level, source, List.of(source), new BoundingBox3i(source, source));
                    SubLevelContainer.getContainer(level)
                        .addForceLoadTicket(
                            wreck,
                            SubLevelLoadingTicketType.COMMAND_FORCED,
                            net.minecraft.util.Unit.INSTANCE);
                    BioshipDecay.mark(wreck);
                    level.setBlock(loose, block("biomancy:storage_sac").defaultBlockState(), 3);
                    helper.assertTrue(
                        BioshipDefense.prey(current) != null,
                        "Competing live prey was not visible/indexed: alive="
                            + prey.isAlive()
                            + " indexed="
                            + (level.getEntity(prey.getUUID()) == prey)
                            + " at="
                            + prey.position()
                            + " root="
                            + root
                            + " wantsHunt="
                            + current.wantsToHunt()
                            + " visible="
                            + current.vision().visible(current, prey.getBoundingBox().getCenter())
                            + " stomachConnected="
                            + DigestiveSystem.stomach(current).connected(current)
                            + " brain="
                            + current.brain().choose(current));
                    var decision = current.brain().choose(current);
                    helper.assertTrue(
                        decision.action() == BioshipBrain.Action.SCAVENGE,
                        "Needful wreck lost to live prey or optional storage: " + decision);
                    var body = current.body().save();
                    body.putDouble("Health", 10);
                    current.body().load(body, level.registryAccess());
                    var danger = new net.minecraft.nbt.CompoundTag();
                    danger.put("Danger", VisionDebug.vector(root.add(3, 0, 0)));
                    danger.putLong("PanicUntil", level.getGameTime() + 100);
                    current.autonomy().load(danger);
                    helper.assertTrue(
                        current.brain().choose(current).action() == BioshipBrain.Action.FLEE,
                        "Escape failed to interrupt reclamation");
                    helper.assertTrue(
                        !BioshipScavenging.collect(current, ship),
                        "Wreck collector ignored the selected escape action");
                    body.putDouble("Health", 100);
                    current.body().load(body, level.registryAccess());
                    current.autonomy().load(new net.minecraft.nbt.CompoundTag());
                    helper.assertTrue(
                        current.brain().choose(current).action() == BioshipBrain.Action.SCAVENGE,
                        "Safe creature did not resume the highest need");
                    helper.assertTrue(
                        BioshipScavenging.collect(current, ship),
                        "Selected wreck was not executed by the collector");
                    helper.succeed();
                  } finally {
                    prey.discard();
                    level.setBlock(lamp, Blocks.STONE.defaultBlockState(), 3);
                    level.removeBlock(loose, false);
                    cleanup(helper, ship);
                    if (wreck != null && !wreck.isRemoved()) cleanup(helper, wreck);
                  }
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "underside_occlusion")
  public static void shaped_buoyancy_tissue_does_not_cull_exposed_neighboring_hull(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var hull = core.getBlockPos().east();
    var organ = hull.below();
    var state = Create_bioships.BUOYANCY_ORGAN.get().defaultBlockState();
    level.setBlock(hull, BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(organ, state, 3);
    helper.assertTrue(
        !state.canOcclude(), "Partial buoyancy model incorrectly culls neighboring hull faces");
    helper.assertTrue(
        Block.shouldRenderFace(level.getBlockState(hull), level, hull, Direction.DOWN, organ),
        "Exposed underside hull face disappears beside the shaped organ");
    helper.assertTrue(
        state.getShape(level, organ).bounds().getXsize() < 1,
        "Buoyancy collision still fills invisible corners");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "growth_hunting")
  public static void full_hatchling_hunts_for_growth_with_functional_organs(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, false).paidTubeStock = 0;
    var data = core.saveWithFullMetadata(level.registryAccess());
    data.putInt("Satiety", 1200);
    core.loadAdditional(data, level.registryAccess());
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    level.setDayTime(18000);
    var chicken = EntityType.CHICKEN.create(level);
    chicken.setNoAi(true);
    chicken.setNoGravity(true);
    chicken.setPos(here.x, here.y, here.z - 3);
    level.addFreshEntity(chicken);
    helper.assertTrue(core.wantsToHunt(), "Full hatchling did not seek missing growth materials");
    helper.runAfterDelay(
        700,
        () -> {
          try {
            var current = core(ship);
            var stomach = DigestiveSystem.stomach(current);
            helper.assertTrue(!chicken.isAlive(), "Full hatchling did not capture prey for growth");
            helper.assertTrue(
                stomach.material("biomancy:bone_fragments") > 0
                    && stomach.material("biomancy:elastic_fibers") > 0
                    && (current.missingBiomass() < 64 || stomach.material(MaterialCost.FLESH) > 0),
                "Hunted prey did not provide growth flesh, bone and fibers");
            helper.assertTrue(
                current.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") > 240,
                "Hunting only began after hunger");
            helper.succeed();
          } finally {
            chicken.discard();
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "mouth_configuration")
  public static void front_obstruction_is_rearranged_without_inward_teeth(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var origin = core.getBlockPos();
    level.setBlock(origin.south(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(origin.north(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(origin.south());
    mouth.setItem(0, new ItemStack(Items.STRUCTURE_VOID, 7));
    DigestiveSystem.connect(core, ship);
    helper.assertTrue(
        !DigestiveSystem.stomach(core).canEnqueue(new ItemStack(Items.STRUCTURE_VOID), false),
        "Inventory sentinel has a decomposition recipe");
    BioshipIntegration.orientMouth(core);
    helper.assertTrue(
        level.getBlockState(origin.south()).getValue(FeedingMouthBlock.FACING) == Direction.SOUTH,
        "Mouth teeth faced through the core while awaiting relocation");
    helper.runAfterDelay(
        145,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  var pos = current.mouthBlock();
                  helper.assertTrue(
                      pos.equals(origin.north()),
                      "Mouth did not take the cleared front position: "
                          + pos
                          + " core="
                          + current.getBlockPos()
                          + " motion="
                          + current.coreMotion().save());
                  helper.assertTrue(
                      level.getBlockState(pos).getValue(FeedingMouthBlock.FACING) == Direction.NORTH
                          && FeedingMouthBlock.openFace(current, pos, Direction.NORTH),
                      "Relocated mouth has an inaccessible tooth face");
                  helper.assertTrue(
                      ((FeedingMouthBlockEntity) level.getBlockEntity(pos)).getItem(0).getCount()
                          == 7,
                      "Mouth reconfiguration lost its inventory: mouth="
                          + ((FeedingMouthBlockEntity) level.getBlockEntity(pos))
                              .saveWithFullMetadata(level.registryAccess())
                          + " stomach="
                          + DigestiveSystem.stomach(current)
                              .saveWithFullMetadata(level.registryAccess()));
                  helper.assertTrue(
                      DigestiveSystem.stomach(current).connected(current),
                      "Reconfiguration disconnected digestion");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "stationary_gait")
  public static void blocked_idle_hatchling_does_not_keep_walking_in_place(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, false);
    var origin =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var glass = new ArrayList<BlockPos>();
    for (var offset : BlockPos.betweenClosed(new BlockPos(-3, 0, -3), new BlockPos(3, 4, 3)))
      if (Math.abs(offset.getX()) == 3 || Math.abs(offset.getZ()) == 3 || offset.getY() == 4) {
        var p = origin.offset(offset);
        glass.add(p);
        level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
      }
    var target = origin.east(5);
    level.setBlock(target, block("biomancy:bio_forge").defaultBlockState(), 3);
    helper.runAfterDelay(
        120,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                !current.crawl().animating(),
                "Stationary blocked creature animated walking forever");
            helper.assertTrue(
                !current.crawl().bracing() || BioshipSupport.layout(current).stable(),
                "Stationary body was supported without a stable footprint");
            helper.succeed();
          } finally {
            glass.forEach(p -> level.removeBlock(p, false));
            level.removeBlock(target, false);
            cleanup(helper, ship);
          }
        });
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 220, batch = "hull_leg_budget")
  public static void hull_growth_waits_for_its_combined_leg_and_frame_budget(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 8));
    stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 12));
    helper.runAfterDelay(
        100,
        () -> {
          helper.assertTrue(
              core.hatchling() && core.crawl().legTier() == 0,
              "Hull expanded without a leg budget");
          helper.assertTrue(
              stomach.material("biomancy:bone_fragments") == 8
                  && stomach.material("biomancy:elastic_fibers") == 12,
              "Incomplete combined budget was partially spent");
          stomach.retainFlesh(16 + EmergencyReserve.TARGET.flesh());
          stomach.retain(
              new ItemStack(item("biomancy:bone_fragments"), 4 + EmergencyReserve.TARGET.bone()));
          stomach.retain(
              new ItemStack(item("biomancy:elastic_fibers"), 8 + EmergencyReserve.TARGET.fiber()));
        });
    helper.runAfterDelay(
        180,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      !core.hatchling() && core.crawl().legTier() >= core.crawl().hullTier(core),
                      "Hull growth did not first strengthen the supporting legs");
                  helper.assertTrue(!core.canFly(), "Preparing strong legs granted flight");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 300, batch = "single_core_creeper_flight")
  public static void healthy_single_core_flees_creeper_instead_of_fighting(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var creeper = EntityType.CREEPER.create(level);
    creeper.setNoAi(true);
    creeper.setPersistenceRequired();
    creeper.setPos(here.x + 3, here.y - .5, here.z);
    helper.runAfterDelay(
        20,
        () -> {
          helper.assertTrue(level.addFreshEntity(creeper), "Creeper fixture was rejected");
          creeper.setTarget(BioshipTargetEntity.ensure(core));
          afterIndexed(
              helper,
              List.of(creeper),
              () -> {
                helper.runAfterDelay(
                    20,
                    () -> {
                      helper.assertTrue(
                          core.autonomy().creeperPanic() && core.autonomy().fleeing(),
                          "Healthy single core chose to fight a creeper: "
                              + core.autonomy().save()
                              + " visible="
                              + core.vision().visible(core, creeper.getBoundingBox().getCenter())
                              + " nearby="
                              + level
                                  .getEntitiesOfClass(
                                      net.minecraft.world.entity.monster.Creeper.class,
                                      creeper.getBoundingBox().inflate(12))
                                  .size()
                              + " alive="
                              + creeper.isAlive()
                              + " removed="
                              + creeper.getRemovalReason()
                              + " at="
                              + creeper.position()
                              + " entity="
                              + level.getEntity(creeper.getUUID()));
                      helper.assertTrue(
                          !core.coreMotion().canAvoidBlast(core, creeper),
                          "A single core claimed nonexistent escape tissue");
                    });
                helper.runAfterDelay(
                    70,
                    () -> {
                      try {
                        helper.assertTrue(
                            BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO)
                                    .distanceToSqr(creeper.position())
                                > 36,
                            "Single-block creature did not retreat from blast range");
                        helper.assertTrue(
                            creeper.getHealth() == creeper.getMaxHealth(),
                            "Single core attacked the creeper while fleeing");
                        helper.assertTrue(
                            core.body().health() == 100 && !core.canFly(),
                            "Creeper retreat changed health or granted flight");
                        helper.succeed();
                      } finally {
                        creeper.discard();
                        cleanup(helper, ship);
                      }
                    });
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "permanent_legs")
  public static void leg_upgrades_cost_material_and_preserve_reach_and_strength(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    helper.assertTrue(
        core.crawl().extraLift() == 1 && !core.crawl().enhance(core),
        "Base legs exceed one free block or upgrade without materials");
    stomach.retainFlesh(8);
    stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 4));
    stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 2));
    double capacity = core.crawl().loadCapacity();
    helper.assertTrue(core.crawl().enhance(core), "Paid leg enhancement was rejected");
    helper.assertTrue(
        stomach.material("biomancy:flesh_bits") == 0
            && stomach.material("biomancy:elastic_fibers") == 0
            && stomach.material("biomancy:bone_fragments") == 0,
        "Leg enhancement did not charge the exact materials");
    var saved = core.saveWithFullMetadata(level.registryAccess());
    core.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(
        core.crawl().legTier() == 1
            && core.crawl().extraLift() == 2
            && core.crawl().legThickness() > 1
            && core.crawl().loadCapacity() > capacity,
        "Permanent leg enhancement was lost on reload");
    helper.assertTrue(!core.canFly(), "Longer legs granted flight");
    finish(helper, ship, () -> {});
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 1600, batch = "heavy_ground_walking")
  public static void grown_hull_walks_before_flight_after_strengthening_its_legs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), true);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO));
    courseFloor(helper, start);
    Vec3[] before = {Vec3.ZERO};
    boolean[] walked = {false};
    helper.runAfterDelay(
        1000,
        () -> {
          var current = core(ship);
          helper.assertTrue(
              current.isMature() && !current.canFly(),
              "Fixture did not build a grounded mature hull");
          var stomach = DigestiveSystem.stomach(current);
          stomach.retainFlesh(40);
          stomach.retain(new ItemStack(item("biomancy:elastic_fibers"), 20));
          stomach.retain(new ItemStack(item("biomancy:bone_fragments"), 10));
          hunt(helper, current, true);
          before[0] = BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO);
        });
    for (int tick = 1020; tick < 1500; tick += 2)
      helper.runAfterDelay(tick, () -> walked[0] |= core(ship).crawl().walking());
    helper.runAfterDelay(
        1500,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.crawl().legTier() > 0
                          && current.crawl().carriedLoad(current) <= current.crawl().loadCapacity(),
                      "Heavy hull did not grow adequate leg strength");
                  helper.assertTrue(
                      walked[0]
                          && BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO)
                                  .distanceToSqr(before[0])
                              > 1,
                      "Built hull could not walk before acquiring flight: "
                          + current.crawl().save());
                  helper.assertTrue(
                      !current.canFly(), "Ground walking gave the mature hull flight");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 130, batch = "fed_passive_healing")
  public static void fed_ship_passively_heals_using_stored_biomass(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    core.body().observe(core);
    var body = core.body().save();
    body.putDouble("Health", 50);
    core.body().load(body, level.registryAccess());
    stomach.retainFlesh(2);
    helper.runAfterDelay(
        100,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      core.body().health() == 58,
                      "Passive healing did not restore four health per flesh bit");
                  helper.assertTrue(
                      stomach.material("biomancy:flesh_bits") == 0,
                      "Passive healing did not consume its biomass");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 1100, batch = "wide_body_terrain")
  public static void attached_organs_clear_a_side_ledge_and_tree_during_foraging(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    int previous = level.getServer().getPlayerList().getViewDistance();
    level.getServer().getPlayerList().setViewDistance(4);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    courseFloor(helper, start);
    level.setDayTime(1000);
    digestive(helper, core, false);
    level.removeBlock(core.getBlockPos().north(), false);
    level.setBlock(
        core.getBlockPos().south(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level
        .getBlockEntity(core.getBlockPos().south())
        .getPersistentData()
        .putBoolean("BioshipManualPlacement", true);
    DigestiveSystem.connect(core, ship);
    level.setBlock(core.getBlockPos().east(), block("biomancy:bio_lab").defaultBlockState(), 3);
    var terrain = new LinkedHashSet<BlockPos>();
    for (var p : BlockPos.betweenClosed(start.offset(1, 0, -10), start.offset(4, 0, -4))) {
      terrain.add(p.immutable());
      level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
    }
    for (var p : BlockPos.betweenClosed(start.offset(-2, 0, -6), start.offset(-2, 4, -6))) {
      terrain.add(p.immutable());
      level.setBlock(p, Blocks.ACACIA_LOG.defaultBlockState(), 3);
    }
    var target = start.north(18);
    var light = target.east();
    terrain.add(light);
    level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
    int[] collected = {-1};
    helper.runAfterDelay(
        40,
        () -> {
          level.setBlock(target, block("biomancy:bio_forge").defaultBlockState(), 3);
          helper.assertTrue(
              core.vision().exposed(core, target),
              "Terrain fixture obscures the target: "
                  + VisionDebug.inspect(core, Vec3.atCenterOf(target)));
        });
    for (int tick = 42; tick < 1000; tick += 2) {
      int observed = tick;
      helper.runAfterDelay(
          tick,
          () -> {
            if (collected[0] < 0
                && BioshipIntegration.blockEntities(ship).stream()
                    .anyMatch(e -> e instanceof BioForgeBlockEntity)) collected[0] = observed;
          });
    }
    helper.runAfterDelay(
        1000,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                collected[0] >= 0,
                "Wide hatchling snagged beside a ledge/tree: at="
                    + BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO)
                        .subtract(Vec3.atLowerCornerOf(start))
                    + " crawl="
                    + current.crawl().save()
                    + " route="
                    + current.crawl().path());
            helper.assertTrue(
                terrain.stream().allMatch(p -> !level.getBlockState(p).isAir()),
                "Navigation destroyed terrain instead of clearing it");
            helper.assertTrue(level.getBlockState(target).isAir(), "Target was never collected");
            helper.succeed();
          } finally {
            terrain.forEach(p -> level.removeBlock(p, false));
            level.removeBlock(target, false);
            level.getServer().getPlayerList().setViewDistance(previous);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 2200, batch = "elevated_glass_maze")
  public static void wide_hatchling_navigates_enclosed_glass_maze_with_steps_and_tall_grass(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    int previous = level.getServer().getPlayerList().getViewDistance();
    level.getServer().getPlayerList().setViewDistance(4);
    level.setDayTime(1000);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var course = NavigationLab.course(start, true);
    NavigationLab.place(level, course);
    digestive(helper, core, false).paidTubeStock = 0;
    level.setBlock(core.getBlockPos().south(), block("biomancy:bio_lab").defaultBlockState(), 3);
    int[] completed = {-1};
    boolean[] gates = {false, false, false};
    double[] peak = {0};
    helper.runAfterDelay(
        40,
        () -> {
          level.setBlock(course.prize(), block("biomancy:bio_forge").defaultBlockState(), 3);
          helper.assertTrue(
              core.vision().exposed(core, course.prize()),
              "Elevated prize is not visible from the other end: "
                  + VisionDebug.inspect(core, Vec3.atCenterOf(course.prize())));
          helper.assertTrue(!course.grass().isEmpty(), "Elevation maze has no tall grass");
          helper.assertTrue(
              BioshipPriorities.bestWorld(core) != null, "Visible prize was not selected");
        });
    for (int tick = 42; tick < 2100; tick += 2) {
      int observed = tick;
      helper.runAfterDelay(
          tick,
          () -> {
            if (completed[0] >= 0) return;
            var current = core(ship);
            var point = BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO);
            var local = point.subtract(Vec3.atLowerCornerOf(start));
            peak[0] = Math.max(peak[0], local.y);
            if (local.x > 6 && local.z > 6) gates[0] = true;
            if (gates[0] && local.x > 12 && local.z < -5) gates[1] = true;
            if (gates[1] && local.x > 18 && local.z > 6) gates[2] = true;
            helper.assertTrue(
                local.x > -1.5 && local.x < 23 && Math.abs(local.z) < 10 && local.y < 7,
                "Hatchling bypassed the enclosed elevation maze: " + local);
            if (BioshipIntegration.blockEntities(ship).stream()
                .anyMatch(e -> e instanceof BioForgeBlockEntity)) completed[0] = observed;
          });
    }
    helper.runAfterDelay(
        2100,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                completed[0] >= 0,
                "Wide hatchling stuck in stepped maze: at="
                    + BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO)
                        .subtract(Vec3.atLowerCornerOf(start))
                    + " rotation="
                    + ship.logicalPose().orientation()
                    + " coreOffset="
                    + current.coreHeightOffset()
                    + " cells="
                    + BioshipDecay.blocks(ship).stream()
                        .map(p -> p.subtract(current.getBlockPos()))
                        .toList()
                    + " crawl="
                    + current.crawl().save()
                    + " route="
                    + current.crawl().path());
            helper.assertTrue(
                gates[0] && gates[1] && gates[2],
                "Elevation maze openings were bypassed: " + Arrays.toString(gates));
            helper.assertTrue(peak[0] > 3.4, "Hatchling did not climb the maze's raised floors");
            helper.assertTrue(
                course.glass().stream().allMatch(p -> level.getBlockState(p).is(Blocks.GLASS)),
                "Maze glass was destroyed");
            helper.assertTrue(
                level.getBlockState(course.prize()).isAir(), "Prize was not assimilated");
            com.mojang.logging.LogUtils.getLogger()
                .info(
                    "Elevation maze passed at tick {}, peak core height {}", completed[0], peak[0]);
            helper.succeed();
          } finally {
            course.blocks().keySet().forEach(p -> level.removeBlock(p, false));
            level.removeBlock(course.prize(), false);
            level.getServer().getPlayerList().setViewDistance(previous);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "navigation_lab_resources")
  public static void navigation_world_type_is_packaged_and_trader_party_is_prey(
      GameTestHelper helper) {
    var registry =
        helper
            .getLevel()
            .registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET);
    var id = ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "navigation_lab");
    helper.assertTrue(registry.get(id) != null, "Packaged navigation world type is missing");
    var normal =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.WORLD_PRESET,
            ResourceLocation.withDefaultNamespace("normal"));
    helper.assertTrue(
        registry.getTag(normal).orElseThrow().stream()
            .anyMatch(
                holder ->
                    holder.is(
                        net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.WORLD_PRESET, id))),
        "Navigation lab is not selectable in the world type list");
    var trader = EntityType.WANDERING_TRADER.create(helper.getLevel());
    var llama = EntityType.TRADER_LLAMA.create(helper.getLevel());
    helper.assertTrue(
        TentacleBlockEntity.edible(trader) && TentacleBlockEntity.edible(llama),
        "Trader party is excluded from prey");
    helper.assertTrue(
        !TentacleBlockEntity.edible(EntityType.SKELETON.create(helper.getLevel())),
        "Skeleton became edible");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 1800, batch = "glass_maze")
  public static void hatchling_navigates_visible_component_through_roofed_glass_maze(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    int previous = level.getServer().getPlayerList().getViewDistance();
    level.getServer().getPlayerList().setViewDistance(4);
    level.setDayTime(1000);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    courseFloor(helper, start);
    var course = NavigationLab.course(start, false);
    NavigationLab.place(level, course);
    var glass = course.glass();
    var component = course.prize();
    boolean[] gates = {false, false, false};
    int[] completed = {-1};
    double[] travelled = {0};
    Vec3[] last = {BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)};
    helper.runAfterDelay(
        40,
        () -> {
          level.setBlock(component, block("biomancy:bio_forge").defaultBlockState(), 3);
          helper.assertTrue(
              core.vision().exposed(core, component),
              "Maze target is not clearly visible through the glass");
          var objective = BioshipPriorities.bestWorld(core);
          helper.assertTrue(
              objective != null && objective.pos().equals(component),
              "Visible required organ did not become the maze target");
          helper.assertTrue(
              !BioshipReach.clear(core, Vec3.atCenterOf(component), true),
              "Maze allows a straight tentacle shortcut");
        });
    for (int tick = 42; tick < 1700; tick += 2) {
      int observedTick = tick;
      helper.runAfterDelay(
          tick,
          () -> {
            if (completed[0] >= 0) return;
            var current = core(ship);
            var point = BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO);
            travelled[0] += point.distanceTo(last[0]);
            last[0] = point;
            var local = point.subtract(Vec3.atLowerCornerOf(start));
            if (local.x > 5 && local.z > 5) gates[0] = true;
            if (gates[0] && local.x > 10 && local.z < -4) gates[1] = true;
            if (gates[1] && local.x > 15 && local.z > 5) gates[2] = true;
            helper.assertTrue(
                local.x > -1.5 && local.x < 20 && Math.abs(local.z) < 8 && local.y < 3.6,
                "Hatchling bypassed the maze's closed boundary: " + local);
            for (var cell :
                BlockPos.betweenClosed(
                    BlockPos.containing(point.add(-.4, -.4, -.4)),
                    BlockPos.containing(point.add(.4, .4, .4))))
              helper.assertTrue(!glass.contains(cell), "Core crossed solid maze glass at " + local);
            if (current.hasMouth()) completed[0] = observedTick;
          });
    }
    helper.runAfterDelay(
        1700,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                completed[0] >= 0 && current.hasMouth(),
                "Visible organ was not reached through the maze: at="
                    + BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO)
                        .subtract(Vec3.atLowerCornerOf(start))
                    + " crawl="
                    + current.crawl().save()
                    + " path="
                    + current.crawl().path());
            helper.assertTrue(
                gates[0] && gates[1] && gates[2],
                "Hatchling did not traverse all three alternating maze openings: "
                    + Arrays.toString(gates));
            helper.assertTrue(travelled[0] > 35, "Maze was bypassed rather than navigated");
            helper.assertTrue(
                glass.stream().allMatch(p -> level.getBlockState(p).is(Blocks.GLASS)),
                "Navigation broke or assimilated maze walls");
            helper.assertTrue(
                level.getBlockState(component).isAir(), "Original component remains outside");
            com.mojang.logging.LogUtils.getLogger()
                .info(
                    "Glass maze passed: organ assimilated at tick {}, travelled {} blocks, all"
                        + " three gates crossed",
                    completed[0],
                    travelled[0]);
            helper.succeed();
          } finally {
            glass.forEach(p -> level.removeBlock(p, false));
            level.removeBlock(component, false);
            level.getServer().getPlayerList().setViewDistance(previous);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 850, batch = "glass_obstacle_course")
  public static void visible_needed_organs_beyond_sixteen_blocks_are_reached_around_glass(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    int previous = level.getServer().getPlayerList().getViewDistance();
    level.getServer().getPlayerList().setViewDistance(4);
    level.setDayTime(1000);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    courseFloor(helper, start);
    var wall = start.east(8);
    for (var p : BlockPos.betweenClosed(wall.offset(0, 0, -4), wall.offset(0, 3, 4)))
      level.setBlock(p, Blocks.WHITE_STAINED_GLASS.defaultBlockState(), 3);
    var machine = start.east(27);
    helper.runAfterDelay(
        40,
        () -> {
          level.setBlock(machine, block("biomancy:bio_forge").defaultBlockState(), 3);
          helper.assertTrue(
              core.vision().exposed(core, machine),
              "Visible distant organ is excluded by perception");
          var goal = BioshipPriorities.bestWorld(core);
          helper.assertTrue(
              goal != null && goal.pos().equals(machine),
              "Clear sight beyond 16 blocks did not become a needed goal");
        });
    double[] furthest = {start.getX()};
    for (int tick = 1; tick < 750; tick += 2)
      helper.runAfterDelay(
          tick,
          () ->
              furthest[0] =
                  Math.max(
                      furthest[0],
                      BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO).x));
    helper.runAfterDelay(
        750,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                current.hasMouth(),
                "Ship did not navigate around the glass and assimilate its needed mouth: at="
                    + BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO)
                    + " "
                    + current.crawl().save()
                    + " path="
                    + current.crawl().path().stream().limit(8).toList());
            helper.assertTrue(
                furthest[0] > start.getX() + 18,
                "Component was stolen through glass without approaching");
            helper.assertTrue(
                level.getBlockState(wall).is(Blocks.WHITE_STAINED_GLASS),
                "Navigation destroyed the barrier");
            helper.succeed();
          } finally {
            for (var p : BlockPos.betweenClosed(wall.offset(0, 0, -4), wall.offset(0, 3, 4)))
              level.removeBlock(p, false);
            level.removeBlock(machine, false);
            level.getServer().getPlayerList().setViewDistance(previous);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "tall_grass_pickup_course")
  public static void floor_food_in_tall_grass_is_grabbed_and_digested_after_approach(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    int previous = level.getServer().getPlayerList().getViewDistance();
    level.getServer().getPlayerList().setViewDistance(4);
    level.setDayTime(1000);
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    courseFloor(helper, start);
    var grass = start.east(21);
    level.setBlock(grass.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
    level.setBlock(grass, Blocks.TALL_GRASS.defaultBlockState(), 3);
    level.setBlock(
        grass.above(),
        Blocks.TALL_GRASS
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.DoublePlantBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
        3);
    var meal =
        new net.minecraft.world.entity.item.ItemEntity(
            level,
            grass.getX() + .5,
            grass.getY() + .15,
            grass.getZ() + .5,
            new ItemStack(Items.BEEF));
    helper.assertTrue(
        level.getBlockState(grass).is(Blocks.TALL_GRASS),
        "Tall grass was missing from the pickup course");
    meal.setDeltaMovement(Vec3.ZERO);
    level.addFreshEntity(meal);
    double[] furthest = {start.getX()};
    for (int tick = 1; tick < 650; tick += 2)
      helper.runAfterDelay(
          tick,
          () -> {
            furthest[0] =
                Math.max(
                    furthest[0],
                    BodyCoordinates.physical(core(ship).amoebaComponents(), BlockPos.ZERO).x);
          });
    helper.runAfterDelay(
        650,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                !meal.isAlive() || meal.getItem().isEmpty(),
                "Arm left edible food stranded in tall grass: " + current.crawl().save());
            helper.assertTrue(
                furthest[0] > start.getX() + 12, "Food was consumed without approaching");
            helper.assertTrue(
                current.saveWithFullMetadata(level.registryAccess()).getInt("Trust") > 60,
                "Collected floor meat did not count as feeding after digestion");
            helper.succeed();
          } finally {
            meal.discard();
            level.removeBlock(grass.above(), false);
            level.removeBlock(grass, false);
            level.getServer().getPlayerList().setViewDistance(previous);
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "shared_attack_health")
  public static void attack_surfaces_share_one_health_pool_and_do_not_multiply_blast_damage(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var surfaces = BioshipTargetEntity.surfaces(core);
    helper.assertTrue(
        surfaces.size() == 6 && BioshipTargetEntity.surfaces(core).equals(surfaces),
        "Attack surfaces duplicated within a tick");
    var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
    helper.assertTrue(
        !surfaces.get(0).isPickable() && surfaces.get(0).canBeHitByProjectile(),
        "Attack surfaces intercept player selection or reject projectiles");
    var source = level.damageSources().mobAttack(zombie);
    helper.assertTrue(surfaces.get(0).hurt(source, 3), "First body strike was rejected");
    helper.assertTrue(
        !surfaces.get(1).hurt(source, 3), "A second surface bypassed the shared damage cooldown");
    helper.assertTrue(
        surfaces.get(2).hurt(source, 6) && core.body().health() == 94,
        "Multiple surfaces multiplied a single damage window");
    var saved = core.saveWithFullMetadata(level.registryAccess());
    core.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(
        !surfaces.get(3).hurt(source, 6), "Core save/load reset shared damage protection");
    core.body().die(core);
    BioshipTargetEntity.ensure(core);
    helper.assertTrue(
        surfaces.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
        "Death left attackable ghost surfaces");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "source_memory")
  public static void expired_hidden_source_releases_selection_without_forcing_a_behavior(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var source = here.east(3);
    level.setBlock(source, block("biomancy:bio_forge").defaultBlockState(), 3);
    try {
      survey(core);
      helper.assertTrue(
          core.vision().sourceState(core, source) != null, "Source was never observed");
      for (var p : BlockPos.betweenClosed(here.offset(1, -1, -2), here.offset(1, 3, 2)))
        level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
      var memory = core.vision().save();
      var entries = memory.getList("Sources", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int index = 0; index < entries.size(); index++)
        entries.getCompound(index).putLong("ObservedMillis", System.currentTimeMillis() - 3000);
      core.vision().load(memory, level.registryAccess());
      core.supplyTarget = source;
      helper.assertTrue(
          core.vision().sourceState(core, source) == null && core.supplyTarget == null,
          "Expired hidden source remained selected");
      helper.assertTrue(
          !core.vision().rememberedSources().contains(source), "Expired snapshot survived");
      helper.succeed();
    } finally {
      level.removeBlock(source, false);
      cleanup(helper, ship);
    }
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 100, batch = "core_scouting")
  public static void grown_core_stays_behind_hull_instead_of_scouting_outside(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var home = core.getBlockPos();
    try {
      for (var p : BlockPos.betweenClosed(home.offset(-2, -2, -2), home.offset(2, 2, 2))) {
        if (Math.abs(p.getX() - home.getX()) == 2
            || Math.abs(p.getY() - home.getY()) == 2
            || Math.abs(p.getZ() - home.getZ()) == 2)
          level.setBlock(p, BioshipTissue.body().defaultBlockState(), 3);
      }
      level.setBlock(home.above(), BioshipTissue.body().defaultBlockState(), 3);
      var tag = core.saveWithFullMetadata(level.registryAccess());
      tag.putInt("Stage", 2);
      core.loadAdditional(tag, level.registryAccess());
      helper.assertTrue(
          CoreMotion.protectedInside(core, home), "Interior core was not recognized as sheltered");
      helper.assertTrue(
          !CoreMotion.protectedInside(core, home.above(2)),
          "Exterior roof was accepted as protected interior");
      core.coreMotion().layoutChanged(core);
      core.coreMotion().tick(core);
      helper.assertTrue(!core.coreMotion().active(), "Protected grown core moved outside to scout");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @DeferredAirshipTest(template = "test_empty", timeoutTicks = 100, batch = "source_memory")
  public static void grown_ship_repositions_existing_eye_for_remembered_target(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var home = core.getBlockPos();
    var source =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(4);
    level.setBlock(source, block("biomancy:bio_forge").defaultBlockState(), 3);
    survey(core);
    var tag = core.saveWithFullMetadata(level.registryAccess());
    tag.putInt("Stage", 2);
    core.loadAdditional(tag, level.registryAccess());
    core.brain().choose(core);
    level.setBlock(home, core.getBlockState().setValue(LivingCoreBlock.CONCEALED, true), 3);
    level.setBlock(home.east(), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(
        home.above(),
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.AUTOMATIC, true)
            .setValue(SurfaceEyeBlock.FACING, Direction.UP),
        3);
    core.vision().invalidate();
    try {
      helper.assertTrue(core.vision().focus(core) != null, "Remembered target was not selected");
      helper.assertTrue(
          !core.vision().visible(core, Vec3.atCenterOf(source)), "Eye was already facing target");
      helper.assertTrue(core.vision().trackFocus(core), "No existing eye was moved to face target");
    } catch (Throwable failure) {
      level.removeBlock(source, false);
      cleanup(helper, ship);
      throw failure;
    }
    helper.runAfterDelay(
        50,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      core(ship).getBlockPos().equals(home),
                      "Grown ship moved its core for external sight");
                  helper.assertTrue(
                      core(ship).vision().visible(core(ship), Vec3.atCenterOf(source)),
                      "Repositioned eye cannot see target");
                  long eyes =
                      BioshipDecay.blocks(ship).stream()
                          .filter(p -> level.getBlockState(p).is(Create_bioships.SURFACE_EYE.get()))
                          .count();
                  helper.assertTrue(eyes == 1, "Eye tracking duplicated a free eye");
                  level.removeBlock(source, false);
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "source_memory")
  public static void fresh_remembered_component_can_be_grasped_behind_own_tissue_but_not_glass(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var home = core.getBlockPos();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var source = here.east(3);
    level.setBlock(source, block("biomancy:bio_forge").defaultBlockState(), 3);
    try {
      survey(core);
      helper.assertTrue(
          core.vision().sourceState(core, source) != null, "Component was not observed");
      level.setBlock(home.east(), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          !core.vision().visible(core, Vec3.atCenterOf(source)), "Own tissue did not occlude eye");
      helper.assertTrue(
          BioshipIntegration.knownReachableSource(core, source),
          "Known reachable component required a fresh visual recheck");
      for (var p : BlockPos.betweenClosed(here.offset(2, -1, -2), here.offset(2, 3, 2)))
        level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
      helper.assertTrue(
          !BioshipIntegration.knownReachableSource(core, source),
          "Remembered grasp passed through solid glass");
      helper.succeed();
    } finally {
      level.removeBlock(source, false);
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 240, batch = "component_perch")
  public static void core_perches_above_existing_organs_without_free_filler_or_lost_component_data(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    var original = core(ship);
    var home = original.getBlockPos();
    level.setBlock(
        home.north(),
        Create_bioships.FEEDING_MOUTH
            .get()
            .defaultBlockState()
            .setValue(FeedingMouthBlock.FACING, Direction.NORTH),
        3);
    level.setBlock(home.west(), block("biomancy:decomposer").defaultBlockState(), 3);
    var organ = level.getBlockEntity(home.west());
    organ.getPersistentData().putString("PerchSentinel", "preserved");
    // A low real obstruction makes elevation useful; height alone is not a benefit.
    var physical =
        BlockPos.containing(BodyCoordinates.physical(original.amoebaComponents(), BlockPos.ZERO));
    for (int x = -4; x <= 4; x++)
      for (int z = -4; z <= 4; z++)
        if (Math.abs(x) == 4 || Math.abs(z) == 4)
          level.setBlock(physical.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
    int before = original.coreMotion().coverage(original, home);
    original.coreMotion().scoutNow(original);
    helper.runAfterDelay(
        160,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.getBlockPos().getY() > home.getY(),
                      "Core did not perch above its organs: " + current.coreMotion().save());
                  helper.assertTrue(
                      current.coreMotion().coverage(current, current.getBlockPos()) > before,
                      "Higher perch made surrounding sight worse");
                  var cells = BioshipDecay.blocks(ship);
                  helper.assertTrue(
                      cells.size() == 3 && level.getBlockState(home).isAir(),
                      "Perching created free tissue or lost a component");
                  helper.assertTrue(
                      cells.stream()
                          .anyMatch(
                              p ->
                                  level.getBlockEntity(p) != null
                                      && level
                                          .getBlockEntity(p)
                                          .getPersistentData()
                                          .getString("PerchSentinel")
                                          .equals("preserved")),
                      "Organ relocation lost its block entity data");
                  helper.assertTrue(
                      current.coreMotion().home(current).equals(home),
                      "Perching moved the structural anchor");
                  helper.assertTrue(
                      current.body().health() == current.body().capacity(),
                      "Perching damaged the creature");
                  helper.assertTrue(
                      !BioshipDecay.dead(ship), "Authorized core relocation marked its ship dead");
                  var support = BioshipSupport.layout(current);
                  helper.assertTrue(support.stable(), "Perch has no stable reachable foot layout");
                  for (var leg : support.legs()) {
                    var attachment =
                        BlockPos.containing(
                            Vec3.atLowerCornerOf(current.structuralOrigin())
                                .add(leg.root())
                                .add(0, .02, 0));
                    helper.assertTrue(
                        !level.getBlockState(attachment).isAir(),
                        "Leg attaches to the empty original core position");
                  }
                  helper.assertTrue(
                      BioshipSupport.containsProjection(
                          support.center(),
                          support.legs().stream()
                              .map(BioshipSupport.Leg::foot)
                              .toArray(Vec3[]::new)),
                      "Feet do not surround the mass distribution");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "terrain_stance")
  public static void higher_front_footings_pitch_body_up_instead_of_shortening_front_legs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= -1; z++)
        level.setBlock(here.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
    var crawl = core.crawl().save();
    crawl.putBoolean("Walking", true);
    core.crawl().load(crawl);
    try {
      core.crawl().tick(core, false);
      var up = core.crawl().stanceUp();
      helper.assertTrue(
          core.crawl().bracing() && up.z > .2 && up.y < .99,
          "Higher front feet failed to lift and pitch the front: " + up);
      helper.assertTrue(
          core.crawl().wallFacing() == null, "One-block step was misclassified as wall climbing");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "legacy_wreck_recovery")
  public static void legacy_coreless_hull_recovers_but_living_and_generic_constructs_do_not(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var origin = core.getBlockPos();
    try {
      for (int x = 1; x <= 6; x++)
        for (int z = 0; z <= 1; z++)
          level.setBlock(
              origin.offset(x, 0, z),
              (x < 4 ? BioshipTissue.body() : Create_bioships.CARAPACE.get()).defaultBlockState(),
              3);
      ship.setUserDataTag(null);
      helper.assertTrue(
          !BioshipDecay.recoverLegacyHull(ship), "Generic tissue construct was declared dead");
      level.setBlock(origin.east(7), Create_bioships.TENTACLE.get().defaultBlockState(), 3);
      helper.assertTrue(
          !BioshipDecay.recoverLegacyHull(ship), "Living construct was declared a wreck");
      level.removeBlock(origin, false);
      ship.setUserDataTag(null);
      helper.assertTrue(
          BioshipDecay.recoverLegacyHull(ship) && BioshipDecay.dead(ship),
          "Unmarked coreless bioship hull remained ineligible");
      helper.assertTrue(
          ship.getUserDataTag().getLong("BioshipDecayAfter")
              >= level.getGameTime() + BioshipDecay.GRACE_TICKS,
          "Recovered wreck skipped its decay grace period");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "physical_balance")
  public static void
      reconfiguration_rejects_unreachable_overhang_and_foot_polygon_checks_mass_projection(
          GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    try {
      helper.assertTrue(
          !BioshipSupport.safeMove(
              core, stomach.getBlockPos(), core.getBlockPos().offset(12, 8, 0), false),
          "Automatic reconfiguration accepted an unreachable top-heavy overhang: "
              + BioshipSupport.movedLayout(
                  core, stomach.getBlockPos(), core.getBlockPos().offset(12, 8, 0), false));
      Vec3[] feet = {
        new Vec3(-1, 0, -1), new Vec3(1, 0, -1), new Vec3(-1, 0, 1), new Vec3(1, 0, 1)
      };
      helper.assertTrue(
          BioshipSupport.containsProjection(Vec3.ZERO, feet), "Stable center of mass was rejected");
      helper.assertTrue(
          !BioshipSupport.containsProjection(new Vec3(2, 4, 0), feet),
          "Mass outside planted feet was held up artificially");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "core_removal_death")
  public static void player_and_non_player_core_removal_mark_the_whole_construct_dead(
      GameTestHelper helper) {
    var ships = new ArrayList<ServerSubLevel>();
    var level = helper.getLevel();
    for (int mode = 0; mode < 4; mode++) {
      var ship = assemble(helper);
      ships.add(ship);
      var core = core(ship);
      var pos = core.getBlockPos();
      level.setBlock(pos.east(), BioshipTissue.body().defaultBlockState(), 3);
      BioshipIntegration.bind(ship);
      if (mode == 0)
        helper.assertTrue(
            player(helper, "CoreBreaker").gameMode.destroyBlock(pos),
            "Player could not remove core");
      else if (mode == 1) level.destroyBlock(pos, false);
      else if (mode == 2) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      else {
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      }
      if (mode < 3)
        helper.assertTrue(
            BioshipDecay.dead(ship), "Core removal path " + mode + " left a live wreck");
    }
    helper.runAfterDelay(
        3,
        () -> {
          try {
            for (var ship : ships) {
              helper.assertTrue(
                  BioshipDecay.dead(ship), "Entity-first core removal lost death registration");
              helper.assertTrue(
                  ship.getUserDataTag().getLong("BioshipDecayAfter") > level.getGameTime(),
                  "Core removal skipped slow decay grace");
            }
            helper.succeed();
          } finally {
            for (var ship : ships) cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "core_removal_fragment")
  public static void untagged_fragment_inherits_core_removal_after_parent_disappears(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    BioshipIntegration.bind(ship);
    var source = core.getBlockPos().east();
    level.setBlock(source, Create_bioships.ONE_WAY_WINDOW.get().defaultBlockState(), 3);
    var fragment =
        SubLevelAssemblyHelper.assembleBlocks(
            level, source, List.of(source), new BoundingBox3i(source, source));
    fragment.setUserDataTag(null);
    fragment.setSplitFrom(ship, ship.logicalPose());
    var container = SubLevelContainer.getContainer(level);
    container.addForceLoadTicket(
        fragment, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    level.destroyBlock(core.getBlockPos(), false);
    helper.assertTrue(BioshipDecay.dead(ship), "Removed core did not leave a death record");
    long deadline = ship.getUserDataTag().getLong("BioshipDecayAfter");
    if (!ship.isRemoved()) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    helper.runAfterDelay(
        50,
        () -> {
          try {
            helper.assertTrue(
                BioshipDecay.dead(fragment), "Small untagged fragment lost its dead parent");
            helper.assertTrue(
                fragment.getUserDataTag().getLong("BioshipDecayAfter") == deadline,
                "Fragment reset its parent's decay schedule");
            helper.succeed();
          } finally {
            cleanup(helper, fragment);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "nearby_sac_pickup")
  public static void adjacent_storage_source_is_vacated_before_attachment_clearance(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var source =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east();
    level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
    Objects.requireNonNull(level.getCapability(Capabilities.ItemHandler.BLOCK, source, null))
        .insertItem(0, new ItemStack(Items.BEEF, 3), false);
    survey(core);
    BioshipIntegration.assimilate(core, ship);
    helper.assertTrue(
        ComponentTransport.busy(core.amoebaComponents()),
        "Adjacent source blocked its own assimilation destination");
    helper.runAfterDelay(
        70,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      level.getBlockState(source).isAir(), "World storage source was not removed");
                  int food = 0;
                  for (var be : BioshipIntegration.blockEntities(ship))
                    if (NativeStorage.isStorage(be)) {
                      var inv =
                          level.getCapability(
                              Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
                      for (int slot = 0; inv != null && slot < inv.getSlots(); slot++)
                        if (inv.getStackInSlot(slot).is(Items.BEEF))
                          food += inv.getStackInSlot(slot).getCount();
                    }
                  helper.assertTrue(food == 3, "Nearby storage pickup lost or duplicated food");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "extended_body_pickup")
  public static void selected_supply_in_appendage_range_is_not_limited_by_core_search_box(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var home = core.getBlockPos();
    for (int x = 1; x <= 4; x++)
      level.setBlock(home.east(x), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(
        home.east(5),
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.FACING, Direction.EAST),
        3);
    var source =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(8);
    level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
    survey(core);
    helper.assertTrue(
        BioshipReach.clear(core, Vec3.atCenterOf(source), true),
        "Fixture is outside appendage reach");
    BioshipIntegration.assimilate(core, ship);
    helper.assertTrue(
        ComponentTransport.busy(core.amoebaComponents()),
        "Reachable selected supply was discarded by the core-centered search box");
    helper.runAfterDelay(
        70,
        () ->
            finish(
                helper,
                ship,
                () ->
                    helper.assertTrue(
                        level.getBlockState(source).isAir(), "Selected supply was not collected")));
  }

  @GameTest(template = "test_empty", timeoutTicks = 480, batch = "batch_assimilation")
  public static void bootstrap_batch_uses_one_slot_and_keeps_component_inventories(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var sources = List.of(here.east(2), here.west(2), here.north(2), here.south(2));
    for (var source : sources) {
      level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
      Objects.requireNonNull(level.getCapability(Capabilities.ItemHandler.BLOCK, source, null))
          .insertItem(0, new ItemStack(Items.BEEF, 2), false);
    }
    survey(core);
    var decision = core.brain().choose(core);
    helper.assertTrue(core.assimilation().start(core, decision), "Batch did not start");
    helper.assertTrue(
        core.assimilation().arms().size() == 1,
        "Available appendages were not used concurrently: " + core.assimilation().arms().size());
    helper.assertTrue(
        BioshipIntegration.blockEntities(ship).stream().noneMatch(NativeStorage::isStorage),
        "Pickup required prematurely fitting organs");
    helper.assertTrue(
        sources.stream().filter(p -> level.getBlockState(p).isAir()).count() == 1,
        "Bare core used more than its single bootstrap slot");
    var restored = new BioshipAssimilation();
    restored.load(core.assimilation().save());
    helper.assertTrue(restored.arms().size() == 1, "Reload lost active batch arms");
    helper.runAfterDelay(
        360,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  int food = 0;
                  for (var be : BioshipIntegration.blockEntities(ship))
                    if (NativeStorage.isStorage(be)) {
                      var inv =
                          level.getCapability(
                              Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
                      for (int i = 0; inv != null && i < inv.getSlots(); i++)
                        if (inv.getStackInSlot(i).is(Items.BEEF))
                          food += inv.getStackInSlot(i).getCount();
                    }
                  helper.assertTrue(
                      food == 8, "Batch lost or duplicated native container contents: " + food);
                  helper.assertTrue(
                      core(ship).assimilation().stored() == 0,
                      "Reachable retained components never grew back out: "
                          + core(ship).assimilation().save()
                          + " body="
                          + BioshipDecay.blocks(ship));
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 520, batch = "whole_wreck_batch")
  public static void committed_wreck_job_keeps_collecting_until_all_noncore_blocks_are_gone(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var start =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
            .east(4);
    var cells =
        List.of(
            start,
            start.above(),
            start.south(),
            start.south().above(),
            start.south(2),
            start.south(2).above());
    for (var pos : cells) level.setBlock(pos, BioshipTissue.body().defaultBlockState(), 3);
    var wreck =
        SubLevelAssemblyHelper.assembleBlocks(
            level, start, cells, new BoundingBox3i(start, start.offset(0, 1, 2)));
    BioshipDecay.mark(wreck);
    var source = wreck.getPlot().getCenterBlock();
    source = BioshipDecay.blocks(wreck).getFirst();
    var point = wreck.logicalPose().transformPosition(Vec3.atCenterOf(source));
    var decision =
        new BioshipBrain.Decision(
            BioshipBrain.Action.SCAVENGE,
            point,
            source,
            null,
            null,
            new BehaviorPriority.Offer("test-wreck", BehaviorPriority.Band.NEED, 950, 0, 0),
            "Recover the whole wreck");
    helper.assertTrue(core.assimilation().start(core, decision), "Wreck batch did not start");
    helper.runAfterDelay(
        420,
        () -> {
          try {
            helper.assertTrue(
                wreck.isRemoved() || BioshipDecay.blocks(wreck).isEmpty(),
                "Committed scavenging abandoned reachable wreck tissue");
            helper.assertTrue(
                core.assimilation().stored() == 0, "Completed wreck stock was never processed");
            helper.succeed();
          } finally {
            cleanup(helper, ship);
            cleanup(helper, wreck);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "core_emergency_reserve")
  public static void emergency_core_stock_is_transferred_once_and_regrows_lost_digestive_organs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    stomach.retainFlesh(180);
    stomach.retain(new ItemStack(item(BioshipAdaptation.FIBER), 64));
    stomach.retain(new ItemStack(item(BioshipAdaptation.BONE), 48));
    int before = stomach.material(BioshipAdaptation.FLESH);
    core.adaptation().maintain(core);
    helper.assertTrue(
        core.adaptation().material(BioshipAdaptation.FLESH) >= EmergencyReserve.TARGET.flesh()
            && stomach.material(BioshipAdaptation.FLESH) == before,
        "Core buffer duplicated construction stock");
    int held = core.adaptation().material(BioshipAdaptation.FLESH),
        heldBone = core.adaptation().material(BioshipAdaptation.BONE);
    var saved = core.saveWithFullMetadata(level.registryAccess());
    core.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(
        core.adaptation().material(BioshipAdaptation.FLESH) == held, "Reload lost core stock");
    var mouth = core.mouthBlock();
    level.removeBlock(mouth, false);
    level.removeBlock(stomach.getBlockPos(), false);
    helper.runAfterDelay(
        120,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.hasMouth() && DigestiveSystem.stomach(current) != null,
                      "Stored core reserves did not regrow lost essentials");
                  helper.assertTrue(
                      current.adaptation().material(BioshipAdaptation.BONE) == heldBone - 6,
                      "Regrowth did not pay both organ costs exactly once");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "pooled_material_storage")
  public static void connected_sacs_store_and_spend_materials_without_duplication(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    stomach.retainFlesh(240);
    stomach.retain(new ItemStack(item(BioshipAdaptation.FIBER), 64));
    var pos = DigestiveSystem.storagePort(core, core.getBlockPos());
    helper.assertTrue(pos != null, "No connected storage port");
    level.setBlock(pos, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    int before = stomach.material(BioshipAdaptation.FLESH);
    BioshipStores.balance(stomach);
    var sac = level.getBlockEntity(pos);
    helper.assertTrue(BioshipStores.total(sac) > 0, "Overflow was not moved into the sac");
    helper.assertTrue(
        stomach.material(BioshipAdaptation.FLESH) == before,
        "Moving stock duplicated or lost material");
    helper.assertTrue(
        stomach.spend(BioshipAdaptation.FLESH, 20)
            && stomach.material(BioshipAdaptation.FLESH) == before - 20,
        "Shared stock was not debited exactly once");
    int held = BioshipStores.stored(sac, BioshipAdaptation.FLESH);
    level.removeBlock(pos, false);
    helper.assertTrue(
        stomach.material(BioshipAdaptation.FLESH) == before - 20 - held,
        "Disconnected storage remained spendable");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 250, batch = "food_threshold_queue")
  public static void food_threshold_reorders_component_goals_and_removes_consumed_targets(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), false);
    helper
        .getLevel()
        .setBlock(core(ship).getBlockPos().west(2), BioshipTissue.body().defaultBlockState(), 3);
    helper.runAfterDelay(
        100,
        () -> {
          var core = core(ship);
          // Finish startup reconfiguration before inspecting the ordinary weighted branch.
          if (core.coreMotion().active()) core.coreMotion().cancel(core);
          var here =
              BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
          var source = here.south(3);
          var food =
              new net.minecraft.world.entity.item.ItemEntity(
                  level,
                  here.getX() + 2.5,
                  here.getY() + .2,
                  here.getZ() + .5,
                  new ItemStack(Items.BEEF));
          level.addFreshEntity(food);
          afterIndexed(
              helper,
              List.of(food),
              () -> {
                try {
                  var current = core(ship);
                  if (current.coreMotion().active()) current.coreMotion().cancel(current);
                  level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
                  var tag = current.saveWithFullMetadata(level.registryAccess());
                  tag.putInt("Satiety", 40);
                  current.loadAdditional(tag, level.registryAccess());
                  survey(current);
                  helper.assertTrue(
                      current.brain().choose(current).action() == BioshipBrain.Action.COLLECT_ITEM,
                      "Below 50 food, optional components displaced nutrition: "
                          + current.brain().choose(current));
                  tag = current.saveWithFullMetadata(level.registryAccess());
                  tag.putInt("Satiety", 51);
                  current.loadAdditional(tag, level.registryAccess());
                  helper.assertTrue(
                      current.brain().choose(current).action() == BioshipBrain.Action.ASSIMILATE,
                      "Above 50 food, components lost to routine feeding: "
                          + current.brain().choose(current));
                  helper.assertTrue(
                      current.brain().queuedGoals().size() > 1,
                      "Competing reachable goals were not queued");
                  level.removeBlock(source, false);
                  current.vision().forget(current, source);
                  current.brain().choose(current);
                  helper.assertTrue(
                      current.brain().queuedGoals().stream()
                          .noneMatch(g -> source.equals(g.block())),
                      "Removed target left a stale queued goal");
                  helper.succeed();
                } finally {
                  food.discard();
                  level.removeBlock(source, false);
                  cleanup(helper, ship);
                }
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "adaptation_mass_budget")
  public static void overloaded_adaptation_proposes_shedding_and_funded_mass_requires_stronger_legs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var added = new ArrayList<BlockPos>();
    for (var offset : BlockPos.betweenClosed(new BlockPos(-3, 0, -3), new BlockPos(3, 0, 3))) {
      var pos = core.getBlockPos().offset(offset);
      if (!level.getBlockState(pos).isAir()) continue;
      level.setBlock(pos, BioshipTissue.body().defaultBlockState(), 3);
      added.add(pos.immutable());
      if (core.crawl().carriedLoad(core) > core.crawl().loadCapacity()) break;
    }
    var saved = core.adaptation().save();
    saved.putLongArray("Armor", added.stream().mapToLong(BlockPos::asLong).toArray());
    core.adaptation().load(saved);
    var proposals = core.adaptation().proposals(core);
    helper.assertTrue(
        proposals.stream().anyMatch(p -> p.action() == BioshipBrain.Action.RECLAIM_COMPONENT),
        "Overloaded unfunded body had no safe shedding proposal");
    helper.assertTrue(
        proposals.stream()
            .noneMatch(
                p ->
                    p.action() == BioshipBrain.Action.FORTIFY
                        || p.action() == BioshipBrain.Action.GROW_STORAGE),
        "Overloaded body proposed more unsupported mass");
    stomach.retainFlesh(200);
    stomach.retain(new ItemStack(item(BioshipAdaptation.FIBER), 64));
    stomach.retain(new ItemStack(item(BioshipAdaptation.BONE), 64));
    helper.assertTrue(core.crawl().canEnhance(core), "Fixture did not fund stronger legs");
    helper.assertTrue(
        core.adaptation().proposals(core).stream()
            .noneMatch(p -> p.action() == BioshipBrain.Action.RECLAIM_COMPONENT),
        "Fundable leg enhancement was replaced by destructive shedding");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 250, batch = "nutrition_interrupts")
  public static void
      pressing_reserve_food_interrupts_unrelated_cargo_but_existing_food_work_continues(
          GameTestHelper helper) {
    var ship = assemble(helper);
    var level = helper.getLevel();
    digestive(helper, core(ship), false);
    level.setBlock(core(ship).getBlockPos().west(2), BioshipTissue.body().defaultBlockState(), 3);
    helper.runAfterDelay(
        100,
        () -> {
          var current = core(ship);
          if (current.coreMotion().active()) current.coreMotion().cancel(current);
          var here =
              BlockPos.containing(
                  BodyCoordinates.physical(current.amoebaComponents(), BlockPos.ZERO));
          var food =
              new net.minecraft.world.entity.item.ItemEntity(
                  level,
                  here.getX() + .5,
                  here.getY() + .2,
                  here.getZ() + 2.5,
                  new ItemStack(item("biomancy:nutrients"), 4));
          level.addFreshEntity(food);
          afterIndexed(
              helper,
              List.of(food),
              () -> {
                var core = core(ship);
                if (core.coreMotion().active()) core.coreMotion().cancel(core);
                var source =
                    BlockPos.containing(
                            BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO))
                        .east(2);
                level.setBlock(source, block("biomancy:storage_sac").defaultBlockState(), 3);
                try {
                  var target = DigestiveSystem.storagePort(core, source);
                  helper.assertTrue(
                      target != null
                          && ComponentTransport.begin(
                              core.amoebaComponents(), source, target, true),
                      "Could not start unrelated component transport");
                  var moving = core.organMove();
                  var saved = core.saveWithFullMetadata(level.registryAccess());
                  saved.putInt("Satiety", 40);
                  core.loadAdditional(saved, level.registryAccess());
                  helper.assertTrue(
                      core.brain().choose(core).action() == BioshipBrain.Action.COLLECT_ITEM,
                      "Urgent non-meat fuel did not interrupt unrelated transport: "
                          + core.brain().choose(core));
                  helper.assertTrue(
                      core.organMove() != null && core.organMove().cargo().equals(moving.cargo()),
                      "Food interruption discarded carried parts");
                  saved = core.saveWithFullMetadata(level.registryAccess());
                  saved.putInt("Satiety", 80);
                  core.loadAdditional(saved, level.registryAccess());
                  helper.assertTrue(
                      core.brain().choose(core).action() == BioshipBrain.Action.MOVE_COMPONENT,
                      "Ordinary fuel interrupted a healthy committed task");
                  var inventory =
                      level.getCapability(
                          Capabilities.ItemHandler.BLOCK, moving.cargoBlock(), null);
                  helper.assertTrue(inventory != null, "Moving sac lost its native inventory");
                  inventory.insertItem(0, new ItemStack(Items.BEEF, 5), false);
                  saved = core.saveWithFullMetadata(level.registryAccess());
                  saved.putInt("Satiety", 40);
                  core.loadAdditional(saved, level.registryAccess());
                  helper.assertTrue(
                      core.brain().choose(core).action() == BioshipBrain.Action.MOVE_COMPONENT,
                      "Ongoing food acquisition was interrupted by another food source");
                  helper.succeed();
                } finally {
                  food.discard();
                  level.removeBlock(source, false);
                  cleanup(helper, ship);
                }
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "private_stomach_salvage")
  public static void recycling_duplicate_stomach_preserves_private_products_and_inputs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var pos = core.getBlockPos().south();
    level.setBlock(pos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var donor = (StomachBlockEntity) level.getBlockEntity(pos);
    donor.retainFlesh(17);
    donor.retain(new ItemStack(item(BioshipAdaptation.BONE), 4));
    donor.inheritInput(new ItemStack(Items.BARRIER, 3));
    donor.freeTubeStock = 5;
    donor.paidTubeStock = 7;
    int tubes = stomach.freeTubeStock + stomach.paidTubeStock;
    try {
      core.scavenging().finish(core, pos, false, true);
      helper.assertTrue(level.getBlockState(pos).isAir(), "Duplicate stomach was not reclaimed");
      helper.assertTrue(
          stomach.material(BioshipAdaptation.FLESH) == 17
              && stomach.material(BioshipAdaptation.BONE) == 4,
          "Reclamation lost or duplicated private products: flesh="
              + stomach.material(BioshipAdaptation.FLESH)
              + " bone="
              + stomach.material(BioshipAdaptation.BONE));
      helper.assertTrue(stomach.queuedFood() == 3, "Reclamation lost queued native inputs");
      helper.assertTrue(
          stomach.freeTubeStock + stomach.paidTubeStock == tubes + 12,
          "Stored tube credits were not conserved");
      helper.assertTrue(
          donor.localMaterial(BioshipAdaptation.FLESH) == 0 && donor.queuedFood() == 0,
          "Reclaimed organ retained duplicate spendable stock");
      finish(helper, ship, () -> {});
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "reclaimed_organ_resources")
  public static void sacrificed_parts_become_resources_before_digestive_organ_regrowth(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    level.setBlock(
        core.getBlockPos().north(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    var state = core.adaptation().save();
    var stock = new net.minecraft.nbt.CompoundTag();
    stock.putInt(MaterialCost.FLESH, 15);
    stock.putInt(MaterialCost.FIBER, 8);
    stock.putInt(MaterialCost.BONE, 4);
    state.put("Reserve", stock);
    core.adaptation().load(state);
    var first = core.getBlockPos().east();
    var second = core.getBlockPos().west();
    level.setBlock(first, BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(second, BioshipTissue.body().defaultBlockState(), 3);
    helper.assertTrue(
        core.adaptation().proposals(core).stream()
            .anyMatch(p -> p.action() == BioshipBrain.Action.RECLAIM_COMPONENT),
        "Missing organs could not propose raw-material reclamation without digestion");
    core.scavenging().recycleToCore(core, BioshipTissue.body().defaultBlockState());
    level.removeBlock(first, false);
    helper.assertTrue(
        core.adaptation().material(MaterialCost.FLESH) == 15,
        "A single tissue block bypassed the half-yield resource loss");
    var fraction = core.scavenging().save();
    core.scavenging().load(fraction);
    helper.runAfterDelay(
        260,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.hasMouth() && DigestiveSystem.stomach(current) != null,
                      "Reclaimed resources did not fund both organs");
                  helper.assertTrue(
                      current.adaptation().material(MaterialCost.BONE) == 0
                          && current.adaptation().material(MaterialCost.FIBER) <= 4,
                      "Organs bypassed their structural resource costs");
                  helper.assertTrue(
                      level.getBlockState(first).isAir() || level.getBlockState(second).isAir(),
                      "No donor was actually consumed");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "native_recovery_precedence")
  public static void present_native_prerequisites_prevent_resource_fabrication(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var saved = core.adaptation().save();
    var stock = new net.minecraft.nbt.CompoundTag();
    stock.putInt(MaterialCost.FLESH, 100);
    stock.putInt(MaterialCost.FIBER, 100);
    stock.putInt(MaterialCost.BONE, 100);
    saved.put("Reserve", stock);
    core.adaptation().load(saved);
    level.setBlock(core.getBlockPos().north(), block("biomancy:bio_forge").defaultBlockState(), 3);
    level.setBlock(core.getBlockPos().west(), block("biomancy:decomposer").defaultBlockState(), 3);
    level.setBlock(core.getBlockPos().south(), block("biomancy:digester").defaultBlockState(), 3);
    helper.assertTrue(
        core.adaptation().proposals(core).stream()
            .noneMatch(
                p ->
                    p.action() == BioshipBrain.Action.REGROW_MOUTH
                        || p.action() == BioshipBrain.Action.REGROW_STOMACH
                        || p.action() == BioshipBrain.Action.RECLAIM_COMPONENT),
        "Attached organ precursors were ignored in favor of fabrication");
    level.removeBlock(core.getBlockPos().north(), false);
    level.removeBlock(core.getBlockPos().west(), false);
    level.removeBlock(core.getBlockPos().south(), false);
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var parts = List.of(here.north(2), here.west(2), here.south(2));
    level.setBlock(parts.get(0), block("biomancy:bio_forge").defaultBlockState(), 3);
    level.setBlock(parts.get(1), block("biomancy:decomposer").defaultBlockState(), 3);
    level.setBlock(parts.get(2), block("biomancy:digester").defaultBlockState(), 3);
    try {
      survey(core);
      var options = BioshipRecovery.options(core);
      helper.assertTrue(
          options.mouth() && options.stomach(),
          "Visible nearby native organ precursors were ignored");
      helper.succeed();
    } finally {
      parts.forEach(p -> level.removeBlock(p, false));
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 300, batch = "grounded_self_righting")
  public static void upside_down_hatchling_rights_itself_with_reachable_ground(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var pipeline =
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline();
    pipeline.teleport(
        ship,
        new org.joml.Vector3d(ship.logicalPose().position()).add(0, 1, 0),
        new org.joml.Quaterniond().rotateZ(Math.PI));
    pipeline.resetVelocity(ship);
    helper.runAfterDelay(
        220,
        () ->
            finish(
                helper,
                ship,
                () ->
                    helper.assertTrue(
                        ship.logicalPose().transformNormal(new Vec3(0, 1, 0)).y > .8,
                        "Grounded inverted hatchling could not right itself: "
                            + ship.logicalPose().orientation())));
  }

  @GameTest(template = "test_empty", timeoutTicks = 450, batch = "swim_and_drown")
  public static void trapped_submerged_core_swims_without_flight_and_takes_drowning_damage(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var pool = new ArrayList<BlockPos>();
    for (var p : BlockPos.betweenClosed(here.offset(-4, 0, -4), here.offset(4, 4, 4))) {
      var pos = p.immutable();
      pool.add(pos);
      boolean wall =
          Math.abs(p.getX() - here.getX()) == 4
              || Math.abs(p.getZ() - here.getZ()) == 4
              || p.getY() == here.getY() + 4;
      level.setBlock(pos, (wall ? Blocks.GLASS : Blocks.WATER).defaultBlockState(), 3);
    }
    helper.runAfterDelay(
        40,
        () -> {
          var current = core(ship);
          helper.assertTrue(
              current.aquatic().swimming() && !current.canFly(),
              "Submerged tentacle strokes did not enable water-only propulsion");
        });
    helper.runAfterDelay(
        380,
        () -> {
          try {
            var current = core(ship);
            helper.assertTrue(
                current.aquatic().air() == 0 && current.body().health() < current.body().capacity(),
                "A trapped bioship could breathe underwater");
            helper.succeed();
          } finally {
            pool.forEach(p -> level.removeBlock(p, false));
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "mouth_breathing")
  public static void one_exposed_mouth_breathes_for_the_whole_submerged_ship(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var first = core.getBlockPos().east();
    var second = core.getBlockPos().west();
    level.setBlock(first, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(second, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    var firstWorld =
        BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(first)));
    var secondWorld =
        BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(second)));
    var water = new ArrayList<BlockPos>();
    for (var p :
        BlockPos.betweenClosed(
            new BlockPos(
                Math.min(firstWorld.getX(), secondWorld.getX()) - 1,
                firstWorld.getY() - 1,
                firstWorld.getZ() - 1),
            new BlockPos(
                Math.max(firstWorld.getX(), secondWorld.getX()) + 1,
                firstWorld.getY() + 1,
                firstWorld.getZ() + 1))) {
      water.add(p.immutable());
      level.setBlock(p, Blocks.WATER.defaultBlockState(), 18);
    }
    try {
      helper.assertTrue(
          !BioshipAquatic.canBreathe(core), "Submerged mouths breathed through the core");
      for (var p :
          BlockPos.betweenClosed(secondWorld.offset(-1, -1, -1), secondWorld.offset(1, 1, 1)))
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
      helper.assertTrue(BioshipAquatic.canBreathe(core), "One exposed mouth did not supply air");
      water.forEach(p -> level.setBlock(p, Blocks.WATER.defaultBlockState(), 18));
      for (var p :
          BlockPos.betweenClosed(firstWorld.offset(-1, -1, -1), firstWorld.offset(1, 1, 1)))
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
      helper.assertTrue(
          BioshipAquatic.canBreathe(core), "Breathing checked only the preferred mouth");
      helper.succeed();
    } finally {
      water.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 18));
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "shared_digestive_intakes")
  public static void additional_mouth_wiring_costs_materials_and_preserves_existing_intakes(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var stomach = digestive(helper, core, false);
      var original = core.getBlockPos().north();
      var extra = stomach.getBlockPos().west(4);
      stomach.freeTubeStock = 0;
      stomach.paidTubeStock = 0;
      level.setBlock(
          extra,
          Create_bioships.FEEDING_MOUTH
              .get()
              .defaultBlockState()
              .setValue(FeedingMouthBlock.FACING, Direction.WEST),
          3);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          DigestiveSystem.forMouth(core, extra) == null,
          "Extra intake grew free intestines despite exhausted resources");
      stomach.creditLocal("biomancy:flesh_bits", 32);
      stomach.creditLocal("biomancy:elastic_fibers", 32);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          DigestiveSystem.forMouth(core, extra) == stomach
              && DigestiveSystem.forMouth(core, original) == stomach,
          "Additional mouth failed to join the existing stomach network");
      helper.assertTrue(
          stomach.localMaterial("biomancy:flesh_bits") < 32
              && stomach.localMaterial("biomancy:elastic_fibers") < 32,
          "Extra intake did not pay for its intestine route");
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          DigestiveSystem.offerFood(core, extra, new ItemStack(Items.BEEF), false)
              && DigestiveSystem.offerFood(core, original, new ItemStack(Items.CHICKEN), false)
              && stomach.queuedFood() == 2,
          "Route maintenance lost an intake or duplicated food");
      var cell =
          new org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell(
              extra.getX(), extra.getY(), extra.getZ());
      var tube = DigestiveIntakes.routes(core).route(cell).tubes().getFirst();
      level.removeBlock(new BlockPos(tube.x(), tube.y(), tube.z()), false);
      helper.assertTrue(
          DigestiveSystem.forMouth(core, extra) == null
              && DigestiveSystem.forMouth(core, original) == stomach,
          "Breaking an intake tube retained stale connectivity or disabled the other intake");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 420, batch = "producer_energy_exemption")
  public static void lone_core_and_digestive_producers_do_not_consume_energy(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    int food = core.foodLevel();
    helper.runAfterDelay(
        120,
        () ->
            helper.assertTrue(
                core(ship).foodLevel() == food && BioshipEnergy.demand(core(ship)) == 0,
                "A lone core required fuel"));
    helper.runAfterDelay(
        140,
        () -> {
          var current = core(ship);
          var level = helper.getLevel();
          var pos = current.getBlockPos();
          level.setBlock(pos.north(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
          level.setBlock(pos.west(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
          DigestiveSystem.connect(current, ship);
        });
    helper.runAfterDelay(
        240,
        () -> {
          var current = core(ship);
          helper.assertTrue(
              current.foodLevel() == food && BioshipEnergy.demand(current) == 0,
              "Mouth, stomach or intestines added upkeep");
          helper
              .getLevel()
              .setBlock(current.getBlockPos().south(), BioshipTissue.body().defaultBlockState(), 3);
        });
    helper.runAfterDelay(
        320,
        () ->
            finish(
                helper,
                ship,
                () ->
                    helper.assertTrue(
                        core(ship).foodLevel() < food && BioshipEnergy.demand(core(ship)) > 0,
                        "Structural tissue did not activate energy consumption")));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "core_storage_reserve")
  public static void shared_storage_refills_core_reserves_and_spends_native_sack_resources(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var port = DigestiveSystem.storagePort(core, core.getBlockPos());
    level.setBlock(port, block("biomancy:storage_sac").defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, port, null);
    inventory.insertItem(0, new ItemStack(item(MaterialCost.FIBER), 40), false);
    inventory.insertItem(1, new ItemStack(item(MaterialCost.BONE), 20), false);
    stomach.retainFlesh(100);
    BioshipStorage.flush(core);
    try {
      helper.assertTrue(
          core.adaptation().material(MaterialCost.FIBER) == 16
              && core.adaptation().material(MaterialCost.BONE) == 8,
          "Core emergency targets were not replenished from a native sack");
      int before = stomach.material(MaterialCost.FIBER);
      helper.assertTrue(
          stomach.spend(MaterialCost.FIBER, 8)
              && stomach.material(MaterialCost.FIBER) == before - 8,
          "Sack materials were not globally spendable");
      helper.assertTrue(
          core.adaptation().material(MaterialCost.FIBER) == 16,
          "Ordinary spending drained the core before the sack");
      core.adaptation().ledger().spend(MaterialCost.FIBER, 4);
      BioshipStorage.flush(core);
      helper.assertTrue(
          core.adaptation().material(MaterialCost.FIBER) == 16,
          "Borrowed emergency stock was not refilled promptly");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 1200, batch = "parallel_digestive_output")
  public static void parallel_stomachs_keep_paste_and_send_native_products_to_the_core(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var first = digestive(helper, core, false);
    var mouth = core.getBlockPos().south();
    var pos = core.getBlockPos().east();
    level.setBlock(
        mouth,
        Create_bioships.FEEDING_MOUTH
            .get()
            .defaultBlockState()
            .setValue(FeedingMouthBlock.FACING, Direction.SOUTH),
        3);
    level.setBlock(pos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    // This six-block fixture needs land respiration while the recipes run.
    level.setBlock(core.getBlockPos().above(), Create_bioships.LUNGS.get().defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    var second = (StomachBlockEntity) level.getBlockEntity(pos);
    var saved = core.saveWithFullMetadata(level.registryAccess());
    saved.putInt("Satiety", BioshipNeeds.MAX_SATIETY);
    core.loadAdditional(saved, level.registryAccess());
    helper.assertTrue(
        first.enqueue(new ItemStack(Items.BEEF), false)
            && second.enqueue(new ItemStack(Items.CHICKEN), false),
        "Parallel food was rejected");
    helper
        .startSequence()
        .thenWaitUntil(
            () -> {
              var organs = DigestiveSystem.stomachs(core(ship));
              helper.assertTrue(
                  organs.size() == 2 && organs.stream().allMatch(s -> s.queuedFood() == 0),
                  "Waiting for both independent stomach recipes to finish");
            })
        .thenExecute(
            () -> {
              try {
                var current = core(ship);
                var organs = DigestiveSystem.stomachs(current);
                helper.assertTrue(
                    organs.stream().allMatch(s -> s.localMaterial("biomancy:nutrient_paste") == 1),
                    "Paste was not retained independently in both stomachs");
                helper.assertTrue(
                    current.adaptation().material(MaterialCost.FLESH) > 0
                        && organs.stream().allMatch(s -> s.localMaterial(MaterialCost.FLESH) == 0),
                    "Native products were not sent into core storage");
                int trust = current.saveWithFullMetadata(level.registryAccess()).getInt("Trust");
                int paste = organs.get(0).burnPaste();
                helper.assertTrue(
                    paste == 20
                        && organs.get(0).localMaterial("biomancy:nutrient_paste") == 0
                        && organs.get(1).localMaterial("biomancy:nutrient_paste") == 1
                        && current.saveWithFullMetadata(level.registryAccess()).getInt("Trust")
                            == trust,
                    "Energy conversion duplicated paste or rewarded trust");
              } finally {
                cleanup(helper, ship);
              }
            })
        .thenSucceed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 700, batch = "automatic_storage_investment")
  public static void overflow_grows_a_paid_connected_sac_and_preserves_core_emergency_stock(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var stomach = digestive(helper, core, false);
    core.incorporateBiomass(64);
    stomach.retainFlesh(400);
    stomach.retain(new ItemStack(item(MaterialCost.FIBER), 64));
    stomach.retain(new ItemStack(item(MaterialCost.BONE), 20));
    helper.runAfterDelay(
        500,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  var sacs = BioshipStorage.sacks(current);
                  helper.assertTrue(
                      !sacs.isEmpty()
                          && sacs.stream()
                              .allMatch(
                                  sac ->
                                      DigestiveSystem.stomachs(current).stream()
                                          .anyMatch(
                                              owner ->
                                                  owner.sacPaths.containsKey(sac.getBlockPos()))),
                      "Overflow did not grow physically connected storage");
                  helper.assertTrue(
                      !EmergencyReserve.missing(current),
                      "Storage investment did not replenish emergency core stock");
                  helper.assertTrue(
                      BioshipStorage.amount(current, MaterialCost.BONE)
                          == 20
                              - sacs.size()
                              - BioshipStructure.cells(current.ship()).values().stream()
                                  .filter(state -> state.is(Create_bioships.AMOEBA_LEG.get()))
                                  .count()
                              - 2 * current.crawl().legTier()
                              - BioshipLarynx.mouths(current).stream()
                                      .filter(FeedingMouthBlockEntity::voiced)
                                      .count()
                                  * BioshipLarynx.COST.bone(),
                      "Storage or leg growth created or lost bone resources");
                  helper.assertTrue(
                      current.hatchling(), "An unaffordable hull bypassed its combined budget");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "engine_room_geometry")
  public static void wider_engine_shells_remain_connected_and_keep_the_central_aisle(
      GameTestHelper helper) {
    for (int columns = 1; columns <= 4; columns++) {
      var cells = new java.util.HashSet<BlockPos>();
      for (var part : EngineRoomLayout.body(DinghyGrowth.LAYOUT_VERSION, columns))
        cells.add(part.offset());
      cells.add(BlockPos.ZERO);
      var seen = new java.util.HashSet<BlockPos>();
      var queue = new java.util.ArrayDeque<BlockPos>();
      queue.add(BlockPos.ZERO);
      while (!queue.isEmpty()) {
        var point = queue.removeFirst();
        if (!cells.contains(point) || !seen.add(point)) continue;
        for (var direction : Direction.values()) queue.addLast(point.relative(direction));
      }
      helper.assertTrue(
          seen.equals(cells),
          "Widening detached shell cells at columns="
              + columns
              + ": "
              + cells.stream().filter(p -> !seen.contains(p)).toList());
      for (int z = 1; z <= 5; z++)
        helper.assertTrue(
            !cells.contains(new BlockPos(0, 2, z)), "Widening filled the central aisle");
    }
    helper.succeed();
  }

  @DeferredAirshipTest(
      template = "test_empty",
      timeoutTicks = 1300,
      batch = "engine_room_investment")
  public static void engine_expansion_budgets_organs_legs_and_reserves_before_spending(
      GameTestHelper helper) {
    var ship = assemble(helper);
    digestive(helper, core(ship), true);
    helper.runAfterDelay(
        1000,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  var level = helper.getLevel();
                  helper.assertTrue(current.isMature(), "Engine fixture did not mature");
                  helper.assertTrue(
                      !ComponentTransport.busy(current.amoebaComponents()),
                      "Engine fixture is still moving an organ");
                  for (int z : new int[] {3, 2, 4})
                    for (int side : new int[] {-1, 1}) {
                      if (DigestiveSystem.mouths(current).size() >= 3) break;
                      var port = current.layoutPos(new BlockPos(3 * side, 2, z));
                      if (BioshipTissue.isTissue(level.getBlockState(port)))
                        level.setBlock(
                            port,
                            Create_bioships.FEEDING_MOUTH
                                .get()
                                .defaultBlockState()
                                .setValue(
                                    FeedingMouthBlock.FACING,
                                    side < 0 ? Direction.WEST : Direction.EAST),
                            3);
                    }
                  helper.assertTrue(
                      DigestiveSystem.mouths(current).size() >= 3,
                      "Additional mouths were not present");
                  var quote = current.engineRoom().quote(current);
                  helper.assertTrue(
                      quote != null && quote.columns() > 1,
                      "More mouths did not request a larger engine room: "
                          + BioshipEngineRoomPlanner.reason(current));
                  var account = BioshipStorage.account(current);
                  // Fund room and legs, but deliberately omit the missing stomachs.
                  var partial =
                      new MaterialCost(
                          quote.cost().flesh()
                              + quote.legUpgrades() * 8
                              + EmergencyReserve.TARGET.flesh(),
                          quote.cost().fiber()
                              + quote.legUpgrades() * 4
                              + EmergencyReserve.TARGET.fiber(),
                          quote.cost().bone()
                              + quote.legUpgrades() * 2
                              + EmergencyReserve.TARGET.bone());
                  for (var entry :
                      java.util.Map.of(
                              MaterialCost.FLESH,
                              partial.flesh(),
                              MaterialCost.FIBER,
                              partial.fiber(),
                              MaterialCost.BONE,
                              partial.bone())
                          .entrySet()) {
                    int delta = entry.getValue() - account.material(entry.getKey());
                    if (delta > 0) account.credit(entry.getKey(), delta);
                    else account.spend(entry.getKey(), -delta);
                  }
                  var before = BioshipStorage.snapshot(current);
                  helper.assertTrue(
                      !current.engineRoom().expand(current)
                          && BioshipStorage.snapshot(current).equals(before),
                      "An incomplete investment partially spent resources");
                  var complete = quote.completeCost(current);
                  for (var entry :
                      java.util.Map.of(
                              MaterialCost.FLESH,
                              complete.flesh() + EmergencyReserve.TARGET.flesh(),
                              MaterialCost.FIBER,
                              complete.fiber() + EmergencyReserve.TARGET.fiber(),
                              MaterialCost.BONE,
                              complete.bone() + EmergencyReserve.TARGET.bone())
                          .entrySet()) {
                    int gap = entry.getValue() - account.material(entry.getKey());
                    if (gap > 0) account.credit(entry.getKey(), gap);
                  }
                  for (int tier = 0; tier < quote.legUpgrades(); tier++)
                    helper.assertTrue(
                        current.crawl().enhance(current), "Funded leg strengthening failed");
                  quote = current.engineRoom().quote(current);
                  helper.assertTrue(
                      quote != null && quote.legUpgrades() == 0,
                      "Engine still exceeds paid leg capacity");
                  var blocked =
                      quote.changes().entrySet().stream()
                          .filter(
                              e -> !e.getValue().isAir() && level.getBlockState(e.getKey()).isAir())
                          .findFirst()
                          .orElseThrow()
                          .getKey();
                  var physical =
                      BlockPos.containing(
                          ship.logicalPose().transformPosition(Vec3.atCenterOf(blocked)));
                  var original = level.getBlockState(physical);
                  level.setBlock(physical, Blocks.OBSIDIAN.defaultBlockState(), 3);
                  helper.assertTrue(
                      current.engineRoom().quote(current) == null,
                      "Engine expansion ignored terrain clearance");
                  level.setBlock(physical, original, 3);
                  var funded = BioshipStorage.snapshot(current);
                  helper.assertTrue(
                      current.engineRoom().expand(current),
                      "A complete supported investment was rejected");
                  helper.assertTrue(
                      current.engineRoom().capacity() >= 3
                          && EngineRoom.stomachBays(current).size()
                              == current.engineRoom().capacity(),
                      "Room size and organ bays disagree");
                  helper.assertTrue(
                      account.material(MaterialCost.FLESH)
                              == funded.getOrDefault(MaterialCost.FLESH, 0) - quote.cost().flesh()
                          && account.material(MaterialCost.FIBER)
                              == funded.getOrDefault(MaterialCost.FIBER, 0) - quote.cost().fiber()
                          && account.material(MaterialCost.BONE)
                              == funded.getOrDefault(MaterialCost.BONE, 0) - quote.cost().bone(),
                      "Expansion did not pay exactly its quoted cost");
                  helper.assertTrue(
                      EmergencyReserve.after(current, new MaterialCost(0, 0, 0)),
                      "Expansion exhausted the emergency reserve");
                  int width =
                      current.blueprint().rooms().stream()
                          .filter(r -> r.id() == 1)
                          .findFirst()
                          .orElseThrow()
                          .width();
                  int columns = current.engineRoom().columns();
                  var saved = current.saveWithFullMetadata(level.registryAccess());
                  current.loadAdditional(saved, level.registryAccess());
                  helper.assertTrue(
                      current.engineRoom().columns() == columns
                          && width == EngineRoomSizing.width(columns),
                      "Expanded room capacity did not survive save/load");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 650, batch = "dormant_airship_prototype")
  public static void stocked_hatchling_and_loaded_legacy_stage_cannot_start_airship_progression(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var stomach = digestive(helper, core, false);
    stomach.retainFlesh(140);
    stomach.retain(new ItemStack(item(MaterialCost.FIBER), 54));
    stomach.retain(new ItemStack(item(MaterialCost.BONE), 20));
    var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
    saved.putInt("Stage", 2);
    saved.putInt("Levitite", 8);
    saved.putInt("Biomass", 0);
    core.loadAdditional(saved, helper.getLevel().registryAccess());
    helper.assertTrue(
        !AirshipPrototype.available() && core.hatchling() && !core.isMature() && !core.canFly(),
        "Legacy stage data activated the dormant prototype");
    helper.assertTrue(
        !core.acceptAmalgam(new ItemStack(item("aeronautics:levitite_blend_bucket"))),
        "Dormant flight progression consumed amalgam");
    helper.runAfterDelay(
        500,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.hatchling()
                          && !current.canFly()
                          && !current.buildingHull()
                          && !current.growthStockReady(),
                      "A funded creature started staged construction");
                  helper.assertTrue(
                      BioshipStructure.cells(ship).values().stream()
                          .noneMatch(state -> AirshipPrototype.component(state.getBlock())),
                      "A cockpit or flight component was built automatically");
                  helper.assertTrue(
                      current
                                  .saveWithFullMetadata(helper.getLevel().registryAccess())
                                  .getInt("Biomass")
                              == 0
                          && BioshipStorage.amount(current, MaterialCost.FLESH) > 64,
                      "Construction materials were consumed by inactive staging");
                  helper.assertTrue(
                      current.brain().choose(current).action() != BioshipBrain.Action.GROW
                          && current.brain().choose(current).action()
                              != BioshipBrain.Action.GROW_SITE,
                      "Inactive progression remained in the goal queue");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "dormant_airship_controls")
  public static void preserved_controls_are_registered_but_inert(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var user = new InteractionPlayer(level, "DormantControls");
    try {
      var pos = core.getBlockPos().east();
      for (var block :
          java.util.List.of(
              Create_bioships.THROTTLE.get(),
              Create_bioships.VERTICAL_TRIM.get(),
              Create_bioships.BLUEPRINT_PANEL.get(),
              Create_bioships.ACCESS_PANEL.get(),
              block("create_bioships:nerve_hunt"),
              block("create_bioships:living_helm"))) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        var before = level.getBlockState(pos);
        user.gameMode.useItemOn(
            user,
            level,
            ItemStack.EMPTY,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        helper.assertTrue(
            level.getBlockState(pos).equals(before) && user.containerMenu == user.inventoryMenu,
            "An unimplemented control changed state or opened its UI: " + block);
      }
      level.setBlock(pos, Create_bioships.THROTTLE.get().defaultBlockState(), 3);
      helper.assertTrue(
          !BioshipNetwork.setThrottle(user, pos, 20),
          "A packet activated preserved flight controls");
      helper.assertTrue(
          !core.blueprint()
              .confirm(
                  core,
                  new BioshipBlueprint.Draft(core.blueprint().rooms()),
                  core.blueprint().revision()),
          "The dormant blueprint accepted a reconstruction");
      helper.succeed();
    } finally {
      user.closeContainer();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 240, batch = "active_hatchling_hostility")
  public static void content_hatchling_is_targeted_by_a_zombie_and_retaliates(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    level.setDayTime(18000);
    var data = core.saveWithFullMetadata(level.registryAccess());
    data.putInt("Trust", 80);
    data.putInt("Satiety", 1200);
    core.loadAdditional(data, level.registryAccess());
    BioshipTargetEntity.ensure(core);
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
    zombie.setPos(root.x + 1.2, root.y - .5, root.z);
    zombie.setItemSlot(
        net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    level.addFreshEntity(zombie);
    double[] lowest = {core.body().health()};
    boolean[] swiped = {false};
    for (int tick = 2; tick < 180; tick += 2)
      helper.runAfterDelay(
          tick,
          () -> {
            var current = core(ship);
            lowest[0] = Math.min(lowest[0], current.body().health());
            swiped[0] |= current.defense().save().getCompound("Strike").contains("Aim");
          });
    helper.runAfterDelay(
        180,
        () -> {
          try {
            helper.assertTrue(
                lowest[0] < core(ship).body().capacity(),
                "Zombie did not damage the active hatchling: minimum="
                    + lowest[0]
                    + " body="
                    + core(ship).body().save()
                    + " zombieHealth="
                    + zombie.getHealth()
                    + " target="
                    + zombie.getTarget());
            helper.assertTrue(
                swiped[0] || zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(),
                "Content hatchling did not attempt a telegraphed defensive swipe");
            helper.assertTrue(!core.aggressive(), "Retaliation required or caused aggression");
            helper.succeed();
          } finally {
            zombie.discard();
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "voice_priority")
  public static void voice_requires_connected_digestion_and_preserves_emergency_stock(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      for (var entry : EmergencyReserve.targets().entrySet())
        core.adaptation().creditRecovery(entry.getKey(), entry.getValue() * 3);
      helper.assertTrue(
          BioshipLarynx.proposal(core) == null, "Voice outranked missing digestive organs");
      var stomach = digestive(helper, core, false);
      helper.assertTrue(BioshipLarynx.ready(core), "Fixture has no working digestion");
      var plan = BioshipLarynx.proposal(core);
      helper.assertTrue(
          plan != null && plan.action() == BioshipBrain.Action.GROW_LARYNX,
          "Hatchling voice upgrade unavailable");
      var before = BioshipStorage.snapshot(core);
      helper.assertTrue(BioshipLarynx.develop(core, plan.pos()), "Funded voice failed");
      helper.assertTrue(
          !BioshipLarynx.missing(core) && EmergencyReserve.after(core, new MaterialCost(0, 0, 0)),
          "Voice lost reserve or failed to combine");
      helper.assertTrue(
          BioshipStorage.amount(core, MaterialCost.FLESH)
                  == before.getOrDefault(MaterialCost.FLESH, 0) - 8
              && BioshipStorage.amount(core, MaterialCost.FIBER)
                  == before.getOrDefault(MaterialCost.FIBER, 0) - 4
              && BioshipStorage.amount(core, MaterialCost.BONE)
                  == before.getOrDefault(MaterialCost.BONE, 0) - 2,
          "Voice cost was lost or duplicated");
      var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(plan.pos());
      helper.assertTrue(
          mouth.voiced() && !BioshipLarynx.develop(core, plan.pos()),
          "Duplicate voice investment accepted");
      var saved = mouth.saveWithFullMetadata(level.registryAccess());
      mouth.loadWithComponents(saved, level.registryAccess());
      helper.assertTrue(mouth.voiced(), "Mouth lost larynx on reload");
      var tube = stomach.gutPath.iterator().next();
      level.removeBlock(tube, false);
      helper.assertTrue(
          !BioshipLarynx.ready(core), "Broken intestine still permitted voice growth");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "voice_native")
  public static void each_mouth_absorbs_one_native_larynx_capsule(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      digestive(helper, core, false);
      var second = core.getBlockPos().south();
      level.setBlock(
          second,
          Create_bioships.FEEDING_MOUTH
              .get()
              .defaultBlockState()
              .setValue(FeedingMouthBlock.FACING, Direction.SOUTH),
          3);
      level.setBlock(
          core.getBlockPos().east(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          BioshipLarynx.missingCount(core) == 2, "Mouths shared a larynx requirement");
      var nativeData = new net.minecraft.nbt.CompoundTag();
      nativeData.putString("SoundEvent", "biomancy:entity.flesh_blob.growl");
      nativeData.putString("PreservedEssence", "test");
      helper.assertTrue(
          ComponentMerging.absorbLarynx(core.amoebaComponents(), nativeData)
              && BioshipLarynx.missingCount(core) == 1,
          "One larynx upgraded more than one mouth");
      helper.assertTrue(
          ComponentMerging.absorbLarynx(core.amoebaComponents(), nativeData)
              && !BioshipLarynx.missing(core),
          "Second mouth could not develop a voice");
      helper.assertTrue(
          !ComponentMerging.absorbLarynx(core.amoebaComponents(), nativeData),
          "Extra larynx was silently consumed");
      for (var mouth : BioshipLarynx.mouths(core))
        helper.assertTrue(
            mouth.larynxCapsule().getString("PreservedEssence").equals("test"),
            "Larynx capsule lost native data");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "body_feedback")
  public static void poison_feedback_ends_on_recovery_or_death_and_damage_flashes(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      helper.assertTrue(
          !BioshipPoisonFeedback.active(core), "Healthy core emitted poison particles");
      core.digestMeal(new ItemStack(Items.ROTTEN_FLESH));
      helper.assertTrue(BioshipPoisonFeedback.active(core), "Poison particles missing");
      long next = core.trustFeedback.nextTick();
      helper.assertTrue(
          next == level.getGameTime() + 60 && !core.trustFeedback.emit(core, true),
          "Trust burst ignored shared cooldown");
      var data = core.saveWithFullMetadata(level.registryAccess());
      data.putInt("PoisonSeconds", 0);
      core.loadAdditional(data, level.registryAccess());
      helper.assertTrue(
          !BioshipPoisonFeedback.active(core) && core.trustFeedback.nextTick() == next,
          "Recovery or transfer broke feedback state");
      core.body().starve(core, 1);
      helper.assertTrue(core.body().flashing(core), "Damage failed to start red flash");
      var body = core.body().save();
      core.body().load(body, level.registryAccess());
      helper.assertTrue(core.body().flashing(core), "Damage flash lost during synchronization");
      core.digestMeal(new ItemStack(Items.ROTTEN_FLESH));
      core.body().die(core);
      helper.assertTrue(!BioshipPoisonFeedback.active(core), "Corpse continued poison particles");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "native_tissue")
  public static void packed_flesh_and_legacy_armor_hosts_are_conserved(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      helper.assertTrue(
          BioshipTissue.body() == block("biomancy:packed_flesh"),
          "Ordinary body is not native Packed Flesh");
      helper.assertTrue(
          Create_bioships.FLESH.get() == block("create_bioships:living_hull")
              && Create_bioships.ARMOR_II.get() == block("create_bioships:living_hull_tier_2"),
          "Preserved armor registry lost");
      var pos = core.getBlockPos().north();
      level.setBlock(pos, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
      var mouth = level.getBlockEntity(pos);
      for (var host : List.of(BioshipTissue.body(), Create_bioships.FLESH.get())) {
        BioshipTissue.remember(mouth, host.defaultBlockState());
        helper.assertTrue(
            BioshipTissue.host(mouth).is(host), "Host material changed during embedding");
      }
      mouth.getPersistentData().remove("BioshipHostState");
      helper.assertTrue(
          BioshipTissue.host(mouth).is(Create_bioships.FLESH.get()),
          "Legacy armor host was converted");
      helper.assertTrue(
          !BioshipTissue.isTissue(Create_bioships.ARMOR_II.get().defaultBlockState()),
          "Future armor became ordinary replaceable flesh");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "native_voice_capsule")
  public static void native_larynx_is_consumed_once_and_jaws_follow_swallow_and_hurt(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    digestive(helper, core, false);
    var pos = core.getBlockPos().south();
    helper.assertTrue(
        level.getBlockState(pos).isAir(), "Native voice fixture would overwrite an intestine");
    level.setBlock(pos, block("biomancy:modular_larynx").defaultBlockState(), 3);
    var nativeOrgan = level.getBlockEntity(pos);
    BioshipTissue.remember(nativeOrgan, BioshipTissue.body().defaultBlockState());
    var capsule = nativeOrgan.saveWithoutMetadata(level.registryAccess());
    var plan = BioshipLarynx.proposal(core);
    helper.assertTrue(
        plan != null && plan.flesh() == 0 && plan.fiber() == 0 && plan.bone() == 0,
        "Available native larynx charged a second growth cost or lacked working digestion: "
            + plan);
    helper.assertTrue(
        BioshipLarynx.develop(core, plan.pos()) && ComponentTransport.busy(core.amoebaComponents()),
        "Native larynx did not enter conserved body transport");
    helper.runAfterDelay(
        70,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  var mouth =
                      BioshipLarynx.mouths(current).stream()
                          .filter(FeedingMouthBlockEntity::voiced)
                          .findFirst()
                          .orElse(null);
                  helper.assertTrue(
                      mouth != null
                          && !level.getBlockState(pos).is(block("biomancy:modular_larynx")),
                      "Larynx did not sink and combine with mouth");
                  helper.assertTrue(
                      mouth
                          .larynxCapsule()
                          .getString(
                              com.github.elenterius.biomancy.block.modularlarynx
                                  .ModularLarynxBlockEntity.SOUND_EVENT_TAG)
                          .equals(
                              capsule.getString(
                                  com.github.elenterius.biomancy.block.modularlarynx
                                      .ModularLarynxBlockEntity.SOUND_EVENT_TAG)),
                      "Native sound configuration was lost");
                  helper.assertTrue(
                      DigestiveSystem.offerFood(
                          current, mouth.getBlockPos(), new ItemStack(Items.BEEF), false),
                      "Working mouth rejected meal");
                  helper.assertTrue(
                      mouth.jawShift(.5f) < FeedingMouthBlockEntity.REST_JAW_SHIFT,
                      "Mouth did not articulate to swallow accepted food");
                  current.body().starve(current, 1);
                  helper.assertTrue(
                      current.body().flashing(current)
                          && mouth.jawShift(.5f) < FeedingMouthBlockEntity.REST_JAW_SHIFT,
                      "Hurt voice did not articulate the upgraded mouth");
                  var saved = mouth.saveWithFullMetadata(level.registryAccess());
                  mouth.loadWithComponents(saved, level.registryAccess());
                  helper.assertTrue(
                      mouth.jawShift(.5f) < FeedingMouthBlockEntity.REST_JAW_SHIFT
                          && mouth.voiced(),
                      "Animation or voice lost on sync/transfer");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "meat_drop_policy")
  public static void new_meat_drops_preserve_normal_loot_and_exclude_undead_and_golems(
      GameTestHelper helper) {
    var level = helper.getLevel();
    for (EntityType<?> type :
        List.of(
            EntityType.ENDERMAN,
            EntityType.VILLAGER,
            EntityType.WITCH,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.IRON_GOLEM,
            EntityType.ZOMBIE,
            EntityType.COW)) {
      var mob = (LivingEntity) type.create(level);
      var drops = new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
      if (type == EntityType.COW)
        drops.add(
            new net.minecraft.world.entity.item.ItemEntity(
                level, 0, 80, 0, new ItemStack(Items.BEEF, 3)));
      drops.add(
          new net.minecraft.world.entity.item.ItemEntity(
              level, 0, 80, 0, new ItemStack(Items.STICK)));
      var event =
          new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(
              mob, level.damageSources().generic(), drops, false);
      BioshipMeatDrops.drops(event);
      BioshipMeatDrops.drops(event);
      int meats =
          drops.stream()
              .filter(d -> LivingCoreBlockEntity.isMeat(d.getItem()))
              .mapToInt(d -> d.getItem().getCount())
              .sum();
      helper.assertTrue(
          meats
              == (type == EntityType.COW
                  ? 3
                  : type == EntityType.ZOMBIE || type == EntityType.IRON_GOLEM ? 0 : 1),
          "Missing or duplicate meat for " + type + ": " + meats);
      helper.assertTrue(
          drops.stream().anyMatch(d -> d.getItem().is(Items.STICK)), "Native loot was suppressed");
      helper.assertTrue(
          BioshipPrey.eligible(mob) == (type != EntityType.ZOMBIE && type != EntityType.IRON_GOLEM),
          "Prey policy disagrees with drop policy");
      for (var drop : drops)
        if (LivingCoreBlockEntity.isMeat(drop.getItem()))
          helper.assertTrue(
              level
                  .getRecipeManager()
                  .getRecipeFor(
                      DecomposerBlockEntity.RECIPE_TYPE.get(),
                      new net.minecraft.world.item.crafting.SingleRecipeInput(drop.getItem()),
                      level)
                  .isPresent(),
              "Meat cannot be decomposed");
    }
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "known_kill_loot")
  public static void known_kill_drops_survive_lost_sight_and_reload_without_free_meals(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      digestive(helper, core, false);
      var prey = EntityType.VILLAGER.create(level);
      var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      prey.setPos(here.x + core.vision().maximumDistance(core) + 32, here.y, here.z);
      var drops = new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
      var proxy = BioshipTargetEntity.ensure(core);
      int before = DigestiveSystem.foodCount(core);
      BioshipMeatDrops.drops(
          new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(
              prey, level.damageSources().mobAttack(proxy), drops, false));
      helper.assertTrue(
          drops.size() == 1 && DigestiveSystem.foodCount(core) == before,
          "Kill manufactured a meal instead of a world drop");
      helper.assertTrue(
          !core.vision().visible(core, drops.getFirst().position()),
          "Fixture drop was within visual range");
      var saved = core.saveWithFullMetadata(level.registryAccess());
      core.loadAdditional(saved, level.registryAccess());
      var decision = core.brain().choose(core);
      helper.assertTrue(
          decision.action() == BioshipBrain.Action.COLLECT_ITEM
              && core.loot().knows(decision.point()),
          "Known distant kill loot was forgotten or lost to hunting: " + decision);
      helper.assertTrue(
          drops
              .getFirst()
              .getItem()
              .getOrDefault(
                  net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                  net.minecraft.world.item.component.CustomData.EMPTY)
              .copyTag()
              .getBoolean("BioshipHunted"),
          "Autonomous kill loot would reward player trust");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "mouth_grasper_slots")
  public static void mouths_own_grasper_slots_independently_of_the_four_legs(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    try {
      helper.assertTrue(
          BioshipGraspers.slots(core).size() == 1
              && BioshipGraspers.slots(core).getFirst().key().equals("core"),
          "Bare core cannot bootstrap");
      digestive(helper, core, false);
      helper.assertTrue(
          BioshipGraspers.slots(core).size() == 1
              && !BioshipGraspers.slots(core).getFirst().key().equals("core"),
          "Level-one mouth has wrong feeding capability");
      level.setBlock(
          core.getBlockPos().south(),
          Create_bioships.FEEDING_MOUTH
              .get()
              .defaultBlockState()
              .setValue(FeedingMouthBlock.FACING, Direction.SOUTH),
          3);
      level.setBlock(
          core.getBlockPos().east(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(BioshipGraspers.slots(core).size() == 2, "Second mouth did not own a slot");
      ComponentMerging.absorbLarynx(core.amoebaComponents(), new net.minecraft.nbt.CompoundTag());
      helper.assertTrue(
          BioshipGraspers.slots(core).size() == 3, "Level-two mouth did not gain its second slot");
      ComponentMerging.absorbLarynx(core.amoebaComponents(), new net.minecraft.nbt.CompoundTag());
      var slots = BioshipGraspers.slots(core);
      helper.assertTrue(
          slots.size() == 4
              && slots.stream().map(BioshipGraspers.Slot::key).distinct().count() == 4,
          "Slots overlap across mouths");
      helper.assertTrue(
          BioshipSupport.layout(core).legs().size() == 4,
          "Grasper growth changed the walking legs");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "surface_contacts")
  public static void wall_and_ceiling_grips_require_reachable_real_surface_contacts(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var wall = BlockPos.containing(here).east();
    var cells = new ArrayList<BlockPos>();
    try {
      for (var p : BlockPos.betweenClosed(wall.offset(0, -1, -1), wall.offset(0, 3, 1))) {
        var cell = p.immutable();
        cells.add(cell);
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
      }
      var grip = GroundRoute.grip(core, here);
      helper.assertTrue(
          grip != null && grip.direction() == Direction.EAST,
          "Legs did not recognize a solid wall");
      for (var cell : cells) level.removeBlock(cell, false);
      cells.clear();
      var roof = BlockPos.containing(here).above(2);
      for (var p : BlockPos.betweenClosed(roof.offset(-1, 0, -1), roof.offset(1, 0, 1))) {
        var cell = p.immutable();
        cells.add(cell);
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
      }
      grip = GroundRoute.grip(core, here);
      helper.assertTrue(
          grip != null && grip.direction() == Direction.UP,
          "Legs did not recognize a reachable ceiling");
      for (var cell : cells) level.removeBlock(cell, false);
      cells.clear();
      helper.assertTrue(GroundRoute.grip(core, here) == null, "Air generated an adhesive contact");
      helper.succeed();
    } finally {
      for (var cell : cells) level.removeBlock(cell, false);
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "direct_diagonal_route")
  public static void open_diagonal_corridors_skip_search_and_blocked_corners_do_not_get_cut(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var obstacles = List.of(BlockPos.containing(here).east(), BlockPos.containing(here).south());
    try {
      var direct = new GroundRoute();
      helper.assertTrue(direct.plan(core, here.add(8, 0, 8)), "Open route failed");
      helper.assertTrue(
          direct.debug(level.getGameTime()).getString("Status").equals("direct_route")
              && direct.points().size() == 1,
          "Open diagonal unnecessarily searched or zigzagged: "
              + direct.debug(level.getGameTime()));
      for (var cell : obstacles) level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
      var corner = new GroundRoute();
      helper.assertTrue(corner.plan(core, here.add(4, 0, 4)), "Corner detour failed");
      var first = corner.points().getFirst();
      helper.assertTrue(
          !(first.x > here.x && first.z > here.z && first.y < here.y + .6),
          "Diagonal cut through the blocked corner");
      helper.succeed();
    } finally {
      for (var cell : obstacles) level.removeBlock(cell, false);
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "packed_tissue_budget")
  public static void dense_tissue_growth_cannot_multiply_native_decomposition_resources(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var recipe =
        level
            .getRecipeManager()
            .getRecipeFor(
                DecomposerBlockEntity.RECIPE_TYPE.get(),
                new net.minecraft.world.item.crafting.SingleRecipeInput(
                    new ItemStack(BioshipTissue.body())),
                level)
            .orElseThrow()
            .value();
    int maximumFlesh =
        recipe.getOutputs().stream()
            .filter(o -> o.getItem() == item(MaterialCost.FLESH))
            .mapToInt(o -> o.getCountRange().getMaxCount())
            .sum();
    int maximumProducts =
        recipe.getOutputs().stream().mapToInt(o -> o.getCountRange().getMaxCount()).sum();
    var price = BioshipTissue.GROWTH_COST;
    helper.assertTrue(
        price.flesh() > maximumFlesh
            && price.flesh() + price.fiber() + price.bone() > maximumProducts,
        "Dense tissue can generate free resources through native decomposition");
    var stock = new MaterialLedger();
    stock.credit(MaterialCost.FLESH, price.flesh());
    helper.assertTrue(
        !price.pay(stock) && stock.material(MaterialCost.FLESH) == price.flesh(),
        "Partial dense-tissue funding spent resources");
    stock.credit(MaterialCost.FIBER, price.fiber());
    helper.assertTrue(price.pay(stock), "Fully funded tissue was rejected");
    helper.assertTrue(stock.snapshot().isEmpty(), "Tissue payment did not conserve stock");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "mouth_food_binding")
  public static void floor_food_enters_the_stomach_of_its_graspers_mouth(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var first = digestive(helper, core, false);
    var rear = core.getBlockPos().south();
    level.setBlock(
        rear,
        Create_bioships.FEEDING_MOUTH
            .get()
            .defaultBlockState()
            .setValue(FeedingMouthBlock.FACING, Direction.SOUTH),
        3);
    level.setBlock(core.getBlockPos().east(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    DigestiveSystem.connect(core, ship);
    helper.runAfterDelay(
        1,
        () -> {
          var current = core(ship);
          var point = ship.logicalPose().transformPosition(Vec3.atCenterOf(rear).add(0, 0, 1));
          var drop =
              new net.minecraft.world.entity.item.ItemEntity(
                  level, point.x, point.y, point.z, new ItemStack(Items.BEEF));
          drop.setNoGravity(true);
          level.addFreshEntity(drop);
          try {
            var intake = DigestiveSystem.forMouth(current, rear);
            helper.assertTrue(intake != null && intake != first, "Fixture mouths shared a stomach");
            var result =
                BioshipFoodCollection.collect(current, drop.position().add(0, .2, 0), drop);
            helper.assertTrue(
                intake.queuedFood() == 1 && first.queuedFood() == 0,
                "Food grasper delivered to a different mouth's stomach: first="
                    + first.queuedFood()
                    + " selected="
                    + intake.queuedFood()
                    + " result="
                    + result
                    + " connected="
                    + intake.connected(current));
            helper.assertTrue(
                ((FeedingMouthBlockEntity) level.getBlockEntity(rear)).jawShift(.5f)
                    < FeedingMouthBlockEntity.REST_JAW_SHIFT,
                "Receiving mouth did not swallow");
            helper.succeed();
          } finally {
            drop.discard();
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "procedural_feet")
  public static void planted_leg_tips_use_collision_contacts_and_step_arcs(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      var floor = BlockPos.containing(here).below();
      for (var cell : BlockPos.betweenClosed(floor.offset(-4, 0, -4), floor.offset(4, 0, 4)))
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
      var anchors = new BioshipLegAnchors();
      anchors.tick(core);
      for (int leg = 0; leg < 4; leg++) {
        var foot = anchors.foot(leg, level.getGameTime());
        helper.assertTrue(
            foot != null && Math.abs(foot.y - (floor.getY() + 1)) < .001,
            "Leg tip did not anchor to the actual floor surface: leg="
                + leg
                + " foot="
                + foot
                + " floor="
                + floor
                + " here="
                + here
                + " layout="
                + BioshipSupport.layout(core));
        helper.assertTrue(
            anchors.contactBox(leg).contains(foot), "Tip contact escaped its collision box");
        anchors.tick(core);
        helper.assertTrue(
            anchors.foot(leg, level.getGameTime() + 1).distanceToSqr(foot) < .0001,
            "Stationary planted foot slid");
      }
      var saved = anchors.save();
      var step = saved.getCompound("Leg0");
      var start = anchors.foot(0, level.getGameTime());
      step.put("From", VisionDebug.vector(start));
      step.put("To", VisionDebug.vector(start.add(1, 0, 0)));
      step.put("Up", VisionDebug.vector(new Vec3(0, 1, 0)));
      step.putLong("Started", level.getGameTime());
      anchors.load(saved);
      var mid = anchors.foot(0, level.getGameTime() + 2.5);
      helper.assertTrue(
          Math.abs(mid.x - start.x - .5) < .001 && mid.y > start.y + .29,
          "Procedural step did not lift between planted contacts");
      helper.assertTrue(
          anchors.foot(0, level.getGameTime() + 5).distanceToSqr(start.add(1, 0, 0)) < .0001,
          "Procedural step missed its destination");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "amoeba_leg_growth")
  public static void amoeba_legs_are_paid_face_aware_and_beat_voice_growth(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      core.adaptation().salvageStarterLegs();
      var initial = BioshipStorage.account(core);
      helper.assertTrue(
          initial.material(MaterialCost.FLESH) == 16
              && initial.material(MaterialCost.FIBER) == 8
              && initial.material(MaterialCost.BONE) == 4,
          "Starter salvage did not fund exactly four legs");
      var saved = core.adaptation().save();
      core.adaptation().load(saved);
      core.adaptation().salvageStarterLegs();
      helper.assertTrue(
          initial.material(MaterialCost.FLESH) == 16, "Starter salvage duplicated after reload");
      for (int i = 0; i < 4; i++) {
        var plan = BioshipLegGrowth.proposal(core);
        helper.assertTrue(
            plan != null && plan.urgency() > 970, "Mobility did not precede larynx growth");
        helper.assertTrue(
            BioshipLegGrowth.develop(core, plan.pos(), plan.state()), "Paid leg failed to grow");
      }
      helper.assertTrue(BioshipLegGrowth.missing(core) == 0, "Four leg sockets were not created");
      helper.assertTrue(
          initial.material(MaterialCost.FLESH) == 0
              && initial.material(MaterialCost.FIBER) == 0
              && initial.material(MaterialCost.BONE) == 0,
          "Leg growth did not deduct its resources exactly");
      stomach.retainFlesh(400);
      stomach.credit(MaterialCost.FIBER, 64);
      stomach.credit(MaterialCost.BONE, 8);
      helper.assertTrue(
          core.adaptation().proposals(core).stream()
              .noneMatch(p -> p.action() == BioshipBrain.Action.GROW_STORAGE),
          "Overflow storage consumed the emergency reserve");
      for (var face : Direction.values()) {
        var leg = new BioshipSupport.Leg(Vec3.ZERO, new Vec3(1, -1, 1), face);
        var start = leg.curve(leg.foot(), new Vec3(0, 1, 0), .001);
        helper.assertTrue(
            start.dot(Vec3.atLowerCornerOf(face.getNormal())) > 0,
            "Leg bent into its attachment face: " + face);
      }
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "digestive_obstruction_repair")
  public static void repairs_all_stomachs_without_overwriting_an_obstructing_component(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var first = digestive(helper, core, false);
      var mouth = core.getBlockPos().south();
      var pos = core.getBlockPos().east();
      level.setBlock(
          mouth,
          Create_bioships.FEEDING_MOUTH
              .get()
              .defaultBlockState()
              .setValue(FeedingMouthBlock.FACING, Direction.SOUTH),
          3);
      level.setBlock(pos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      var second = (StomachBlockEntity) level.getBlockEntity(pos);
      var blocked =
          first.gutPath.stream().filter(p -> !second.gutPath.contains(p)).findFirst().orElseThrow();
      level.setBlock(blocked, block("biomancy:storage_sac").defaultBlockState(), 3);
      var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, blocked, null);
      net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
          inventory, new ItemStack(Items.DIAMOND, 7), false);
      first.retainFlesh(100);
      first.credit(MaterialCost.FIBER, 100);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          first.connected(core) && second.connected(core), "Repair did not restore every stomach");
      helper.assertTrue(
          level.getBlockState(blocked).is(block("biomancy:storage_sac"))
              && inventory.getStackInSlot(0).getCount() == 7,
          "Repair overwrote an obstructing component or its inventory");
      helper.assertTrue(
          !DigestiveSystem.permitsPlacement(core, first.gutPath.getFirst()),
          "Automatic placement could occupy a reserved intestine");
      int before = BioshipStorage.amount(core, MaterialCost.FIBER);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          BioshipStorage.amount(core, MaterialCost.FIBER) == before,
          "Stable digestive repair charged twice");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "digestive_repair_cost")
  public static void damaged_guts_reuse_stock_before_paying_for_new_intestines(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      var level = helper.getLevel();
      for (var tube : List.copyOf(stomach.gutPath)) level.removeBlock(tube, false);
      stomach.freeTubeStock = 0;
      stomach.paidTubeStock = 0;
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(!stomach.connected(core), "Unfunded repair created free intestines");
      int quote = BioshipDigestiveRepair.purchases(core);
      helper.assertTrue(quote > 0, "Missing intestine materials were absent from the repair quote");
      stomach.paidTubeStock = quote;
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          stomach.connected(core) && stomach.paidTubeStock == 0,
          "Prepaid intestine stock was not reused exactly");
      for (var tube : List.copyOf(stomach.gutPath)) level.removeBlock(tube, false);
      stomach.retainFlesh(quote);
      stomach.credit(MaterialCost.FIBER, quote);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          stomach.connected(core)
              && stomach.material(MaterialCost.FLESH) == 0
              && stomach.material(MaterialCost.FIBER) == 0,
          "New intestines did not pay matching flesh and fiber");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "configure_liveness")
  public static void infeasible_cosmetic_configuration_does_not_block_other_behaviors(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      digestive(helper, core, false);
      var mouth = core.mouthBlock();
      level.setBlock(mouth.north(), BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(
          mouth, level.getBlockState(mouth).setValue(FeedingMouthBlock.FACING, Direction.WEST), 3);
      helper.assertTrue(
          !mouth.equals(HatchlingFront.port(core, mouth)),
          "Fixture did not propose an alternate front port");
      helper.assertTrue(
          !HatchlingFront.canArrange(core),
          "Fixture unexpectedly allowed the disconnected cosmetic move");
      helper.assertTrue(
          core.brain().choose(core).action() != BioshipBrain.Action.CONFIGURE,
          "Unexecutable configuration monopolized the goal queue");
      var state = core.brain().save();
      state.putString("Action", "CONFIGURE");
      state.putString("Reason", "Snapshot configuration");
      core.brain().load(state);
      helper.assertTrue(
          core.brain().action() == BioshipBrain.Action.CONFIGURE,
          "Goggles mislabeled the synchronized behavior as IDLE");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "ecosystem_sun_protection")
  public static void ecosystem_undead_receive_a_non_dropping_sun_helmet(GameTestHelper helper) {
    var zombie = EntityType.ZOMBIE.create(helper.getLevel());
    EcosystemLab.sunProtection(zombie);
    helper.assertTrue(
        zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),
        "Test undead has no sunlight protection");
    var data = zombie.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    helper.assertTrue(
        data.getList("ArmorDropChances", net.minecraft.nbt.Tag.TAG_FLOAT).getFloat(3) == 0,
        "Test helmet became renewable loot");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "affordable_surface_eyes")
  public static void affordable_surface_eyes_replace_core_sight_scouting(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      stomach.retainFlesh(64);
      stomach.credit(MaterialCost.FIBER, 64);
      stomach.credit(MaterialCost.BONE, 8);
      helper
          .getLevel()
          .setBlock(core.getBlockPos().above(), BioshipTissue.body().defaultBlockState(), 3);
      DigestiveSystem.connect(core, ship);
      helper.assertTrue(
          core.vision().externalEyesPreferred(core),
          "Funded eyes were not preferred: connected="
              + stomach.connected(core)
              + " materials="
              + BioshipStorage.snapshot(core));
      core.coreMotion().tick(core);
      helper.assertTrue(
          !core.coreMotion().active(), "Core climbed for sight despite affordable eyes");
      core.vision().growEyes(core);
      helper.assertTrue(
          BioshipStructure.cells(ship).values().stream()
              .anyMatch(state -> state.is(Create_bioships.SURFACE_EYE.get())),
          "Core deferred sight scouting but did not actually grow a funded eye");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  private static long lungInvestment(LivingCoreBlockEntity core, boolean flesh) {
    long count =
        BioshipStructure.cells(core.ship()).values().stream()
            .filter(state -> state.is(Create_bioships.LUNGS.get()))
            .count();
    return count
        * (flesh ? BioshipRespiration.LUNG_COST.flesh() : BioshipRespiration.LUNG_COST.fiber());
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "protected_core_entry")
  public static void external_eyes_bury_core_reclaim_one_flesh_and_do_not_refund_twice(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var origin = core.getBlockPos();
    var level = helper.getLevel();
    for (var p : BlockPos.betweenClosed(origin.offset(1, 0, -1), origin.offset(3, 2, 1)))
      level.setBlock(p, BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(
        origin.east(4).above(),
        Create_bioships.SURFACE_EYE
            .get()
            .defaultBlockState()
            .setValue(SurfaceEyeBlock.FACING, Direction.EAST),
        3);
    helper.assertTrue(
        core.vision().externalEyesPreferred(core), "Existing eyes did not protect core priority");
    helper.assertTrue(
        core.coreMotion().tick(core) && origin.east(2).above().equals(core.coreMotion().target()),
        "Core did not select an existing sheltered flesh cell");
    helper.runAfterDelay(
        13,
        () -> {
          var current = core(ship);
          helper.assertTrue(
              current.getBlockPos().equals(origin.east(2).above())
                  && level.getBlockState(origin).isAir()
                  && CoreMotion.protectedInside(current, current.getBlockPos()),
              "Initial burial failed: position="
                  + current.getBlockPos()
                  + " source="
                  + level.getBlockState(origin)
                  + " protected="
                  + CoreMotion.protectedInside(current, current.getBlockPos())
                  + " move="
                  + current.coreMotion().save());
          helper.assertTrue(
              BioshipStorage.amount(current, MaterialCost.FLESH) + lungInvestment(current, true)
                      == BioshipTissue.GROWTH_COST.flesh()
                  && BioshipStorage.amount(current, MaterialCost.FIBER)
                          + lungInvestment(current, false)
                      == BioshipTissue.GROWTH_COST.fiber(),
              "Replaced flesh material balance="
                  + BioshipStorage.snapshot(current)
                  + " work="
                  + current.adaptation().behavior().debug());
          var tag = current.saveWithFullMetadata(level.registryAccess());
          current.loadAdditional(tag, level.registryAccess());
          for (int i = 0; i < 10; i++) current.coreMotion().tick(current);
          finish(
              helper,
              ship,
              () ->
                  helper.assertTrue(
                      current.coreMotion().save().getBoolean("Sheltered")
                          && !current.coreMotion().save().getBoolean("ReclaimEntry")
                          && BioshipStorage.amount(current, MaterialCost.FLESH)
                                  + lungInvestment(current, true)
                              == BioshipTissue.GROWTH_COST.flesh()
                          && BioshipStorage.amount(current, MaterialCost.FIBER)
                                  + lungInvestment(current, false)
                              == BioshipTissue.GROWTH_COST.fiber(),
                      "Reload/repeated shelter evaluation duplicated or lost the committed"
                          + " refund"));
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "water_respiration_priority")
  public static void water_habitat_prefers_exposed_gills_even_with_a_mouth_above_water(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var stomach = digestive(helper, core, false);
      stomach.retainFlesh(64);
      stomach.credit(MaterialCost.FIBER, 64);
      stomach.credit(MaterialCost.BONE, 8);
      level.setBlock(core.getBlockPos().east(), BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(core.getBlockPos().south(), BioshipTissue.body().defaultBlockState(), 3);
      var world =
          BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
      for (var p : BlockPos.betweenClosed(world.offset(-4, -3, -4), world.offset(4, -1, 4)))
        level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
      var water = core.aquatic().save();
      water.putBoolean("WaterContact", true);
      water.putInt("GillSamples", 100);
      water.putInt("GillUse", 900);
      water.putDouble("GillUsePrecise", 900);
      core.aquatic().load(water);
      helper.assertTrue(
          BioshipRespiration.airAccess(core) && BioshipRespiration.missingLungs(core),
          "Fixture does not offer competing land respiration");
      var plan = BioshipRespiration.proposal(core);
      helper.assertTrue(
          plan != null
              && plan.state().is(Create_bioships.GILLS.get())
              && BioshipRespiration.exposedGill(core, plan.pos(), plan.state()),
          "Water resident prioritized lungs over exposed gills");
      helper.assertTrue(
          BioshipRespiration.grow(core, plan.pos(), plan.state()), "Funded gills did not grow");
      helper.assertTrue(
          BioshipRespiration.plan(core) == null,
          "Aquatic resident immediately added lungs after gills");
      for (var p : BlockPos.betweenClosed(world.offset(-4, -3, -4), world.offset(4, -1, 4)))
        level.removeBlock(p, false);
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "socket_anchor_identity")
  public static void socket_contact_identity_survives_filtered_and_reordered_layouts(
      GameTestHelper helper) {
    var origin = new BlockPos(100, 80, 200);
    var westRoot = new Vec3(-.02, .5, .5);
    var eastRoot = new Vec3(1.02, .5, .5);
    var west = new BioshipSupport.Leg(westRoot, westRoot.add(0, -1, 0), Direction.WEST, false);
    var east = new BioshipSupport.Leg(eastRoot, eastRoot.add(0, -1, 0), Direction.EAST, false);
    var layout = new BioshipSupport.Layout(Vec3.ZERO, List.of(east, west), .15);
    helper.assertTrue(
        BioshipAnatomy.anchorIndex(origin, origin.west(), Direction.WEST, layout) == 1
            && BioshipAnatomy.anchorIndex(origin, origin.east(), Direction.EAST, layout) == 0,
        "Socket contact followed cell order instead of its actual root");
    helper.assertTrue(
        BioshipAnatomy.anchorIndex(origin, origin.north(), Direction.NORTH, layout) == -1
            && BioshipAnatomy.anchorIndex(origin, origin.west(), Direction.EAST, layout) == -1,
        "A missing or differently facing socket borrowed another contact");
    var bootstrap =
        new BioshipSupport.Layout(
            Vec3.ZERO,
            List.of(new BioshipSupport.Leg(eastRoot, eastRoot, Direction.EAST, true)),
            .15);
    helper.assertTrue(
        BioshipAnatomy.anchorIndex(origin, origin.east(), Direction.EAST, bootstrap) == -1,
        "A bootstrap foot was mistaken for a mounted socket");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "compact_chamber_progress")
  public static void compaction_does_not_shuffle_cover_for_an_immovable_organ(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var origin = core.structuralOrigin();
      var level = helper.getLevel();
      // A filled central chamber and a distant lung connected by a horizontal tissue bridge.
      for (var pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 0, 2)))
        if (!pos.equals(core.getBlockPos()))
          level.setBlock(pos, BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(origin.east(3), BioshipTissue.body().defaultBlockState(), 3);
      level.setBlock(origin.east(4), Create_bioships.LUNGS.get().defaultBlockState(), 3);
      var cells = BioshipStructure.cells(ship);
      var plan = BioshipAnatomy.compact(core, cells);
      helper.assertTrue(plan != null, "A movable organ had no compaction plan");
      if (BioshipTissue.isTissue(cells.get(plan.source()))) {
        helper.assertTrue(
            plan.target().distManhattan(origin) > 2,
            "Cover moved back into the chamber being cleared");
      }
      // Removing the lung now severs a real branch. Clearing unrelated cover cannot fix that.
      level.setBlock(origin.east(5), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          BioshipAnatomy.compact(core, BioshipStructure.cells(ship)) == null,
          "An immovable bridging organ caused pointless cover reshuffling");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "anatomy_balance_protection")
  public static void buried_legs_queue_relocation_before_protection_and_armor(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var stomach = digestive(helper, core, false);
      core.adaptation().salvageStarterLegs();
      for (int n = 0; n < 4; n++) {
        var plan = BioshipLegGrowth.proposal(core);
        helper.assertTrue(
            plan != null && BioshipLegGrowth.develop(core, plan.pos(), plan.state()),
            "Fixture could not grow four actual legs");
      }
      stomach.retainFlesh(200);
      stomach.credit(MaterialCost.FIBER, 100);
      stomach.credit(MaterialCost.BONE, 100);
      var covering = BioshipAnatomy.protection(core);
      helper.assertTrue(
          covering != null && covering.state().is(BioshipTissue.body()),
          "Unprotected organs skipped flesh covering and proceeded to armor");
      var leg =
          BioshipStructure.cells(ship).entrySet().stream()
              .filter(e -> e.getValue().is(Create_bioships.AMOEBA_LEG.get()))
              .filter(
                  e ->
                      level
                          .getBlockState(
                              e.getKey().relative(e.getValue().getValue(AmoebaLegBlock.FACING)))
                          .isAir())
              .findFirst()
              .orElseThrow();
      var blocked = leg.getKey().relative(leg.getValue().getValue(AmoebaLegBlock.FACING));
      level.setBlock(blocked, BioshipTissue.body().defaultBlockState(), 3);
      BioshipAnatomy.invalidate(core);
      var relocation = BioshipAnatomy.proposal(core);
      helper.assertTrue(
          relocation != null && relocation.action() == BioshipBrain.Action.REPOSITION_ANATOMY,
          "A leg buried by new flesh did not enter the anatomy repair queue");
      helper.assertTrue(
          BioshipAnatomy.protection(core) == null,
          "More armor was proposed before correcting the blocked leg");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "leg_transport_mass")
  public static void detached_amoeba_leg_keeps_physics_mass_in_water(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = location();
    ground(helper, pos);
    level.setBlock(pos, Create_bioships.AMOEBA_LEG.get().defaultBlockState(), 3);
    helper.assertTrue(
        !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(),
        "Leg socket has no physical root volume");
    var cargo =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, List.of(pos), new BoundingBox3i(pos, pos));
    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
    helper.runAfterDelay(
        10,
        () -> {
          try {
            helper.assertTrue(
                cargo.getMassTracker().getMass() > 0,
                "Detached leg became a massless physics body");
            helper.succeed();
          } finally {
            level.removeBlock(pos, false);
            cleanup(helper, cargo);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "paid_leg_specialization")
  public static void paddle_legs_evolve_from_sustained_need_and_pay_both_directions(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      core.adaptation().salvageStarterLegs();
      for (int i = 0; i < 4; i++) {
        var quote = BioshipLegGrowth.proposal(core);
        helper.assertTrue(
            quote != null && BioshipLegGrowth.develop(core, quote.pos(), quote.state()),
            "Fixture did not grow legs");
      }
      stomach.retainFlesh(100);
      stomach.credit(MaterialCost.FIBER, 100);
      stomach.credit(MaterialCost.BONE, 32);
      core.aquatic().legUsage().restore(100, 900);
      var quote = BioshipLegEvolution.proposal(core);
      helper.assertTrue(
          quote != null && quote.state().is(Create_bioships.SWIMMING_LEG.get()),
          "Sustained water use did not propose a paddle");
      var face = core.getLevel().getBlockState(quote.pos()).getValue(AmoebaLegBlock.FACING);
      helper.assertTrue(
          BioshipLegEvolution.evolve(core, quote.pos(), quote.state()),
          "Funded paddle evolution failed");
      helper.assertTrue(
          BioshipStorage.amount(core, MaterialCost.FLESH) == 96
              && BioshipStorage.amount(core, MaterialCost.FIBER) == 96,
          "Paddle evolution did not pay its price");
      helper.assertTrue(
          BioshipLegGrowth.missing(core) == 0 && BioshipLegEvolution.swimmingFraction(core) == .25,
          "Paddle stopped counting as a support leg");
      helper.assertTrue(
          core.getLevel().getBlockState(quote.pos()).getValue(AmoebaLegBlock.FACING) == face,
          "Evolution changed the attachment face");
      helper.assertTrue(
          BioshipLegEvolution.proposal(core) == null,
          "Legs evolved repeatedly without the cooldown");
      core.aquatic().legEvolved(0);
      core.aquatic().legUsage().restore(100, 500);
      helper.assertTrue(
          BioshipLegEvolution.proposal(core) == null, "Mixed use caused unnecessary switching");
      core.aquatic().legUsage().restore(200, 50);
      quote = BioshipLegEvolution.proposal(core);
      helper.assertTrue(
          quote != null
              && quote.state().is(Create_bioships.AMOEBA_LEG.get())
              && BioshipLegEvolution.evolve(core, quote.pos(), quote.state()),
          "Sustained land use did not restore a walking leg");
      helper.assertTrue(
          BioshipStorage.amount(core, MaterialCost.FLESH) == 94
              && BioshipStorage.amount(core, MaterialCost.FIBER) == 94,
          "Returning to land refunded resources or failed to pay");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "respiratory_organs")
  public static void large_bodies_need_lungs_and_only_exposed_wet_gills_supply_water_breathing(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      helper.assertTrue(BioshipAquatic.canBreathe(core), "Bare core lost passive breathing");
      for (var dir : Direction.Plane.HORIZONTAL)
        level.setBlock(
            core.getBlockPos().relative(dir), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          BioshipRespiration.bodySize(core) == 5 && !BioshipAquatic.canBreathe(core),
          "A body larger than four blocks breathed on land without lungs");
      var lung = core.getBlockPos().above();
      level.setBlock(lung, Create_bioships.LUNGS.get().defaultBlockState(), 3);
      helper.assertTrue(BioshipAquatic.canBreathe(core), "Lungs did not enable land breathing");
      var gill = core.getBlockPos().east(2);
      var state =
          Create_bioships.GILLS
              .get()
              .defaultBlockState()
              .setValue(RespiratoryOrganBlock.FACING, Direction.EAST);
      level.setBlock(gill, state, 3);
      var world =
          BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
      for (var pos : BlockPos.betweenClosed(world.offset(-4, -2, -4), world.offset(4, 2, 4)))
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
      helper.assertTrue(
          BioshipRespiration.has(core, false) && BioshipRespiration.has(core, true),
          "The body could not retain both respiratory organs");
      helper.assertTrue(
          BioshipAquatic.canBreathe(core), "Exposed submerged gills did not supply air");
      helper.assertTrue(
          !DigestiveSystem.permitsPlacement(core, gill.east()),
          "Automatic growth could bury the gill opening");
      level.setBlock(gill.east(), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          !BioshipAquatic.canBreathe(core), "Covered gills continued breathing underwater");
      level.removeBlock(gill.east(), false);
      helper.assertTrue(
          BioshipAquatic.canBreathe(core), "Uncovering gills did not restore water breathing");
      for (var pos : BlockPos.betweenClosed(world.offset(-4, -2, -4), world.offset(4, 2, 4)))
        level.removeBlock(pos, false);
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "gill_usage_and_cost")
  public static void gills_require_sustained_need_and_paid_exposed_growth(GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      var level = helper.getLevel();
      stomach.retainFlesh(64);
      stomach.credit(MaterialCost.FIBER, 64);
      // Discretionary adaptation must leave the normal emergency bone reserve intact.
      stomach.credit(MaterialCost.BONE, EmergencyReserve.TARGET.bone());
      core.aquatic().gillUsage().restore(10, 900);
      helper.assertTrue(!BioshipRespiration.wantsGills(core), "A brief dive caused gill growth");
      var world =
          BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
      for (var pos : BlockPos.betweenClosed(world.offset(-4, -2, -4), world.offset(4, 2, 4)))
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
      core.aquatic().gillUsage().restore(100, 900);
      var quote = BioshipRespiration.proposal(core);
      helper.assertTrue(
          quote != null && quote.state().is(Create_bioships.GILLS.get()),
          "Frequent submersion did not propose exposed gills");
      helper.assertTrue(
          BioshipRespiration.grow(core, quote.pos(), quote.state()), "Paid gills did not grow");
      helper.assertTrue(
          stomach.material(MaterialCost.FLESH) == 58 && stomach.material(MaterialCost.FIBER) == 58,
          "Gill growth did not pay the resource cost");
      helper.assertTrue(
          BioshipRespiration.activeGills(core),
          "New gills were placed where they could not operate");
      for (var pos : BlockPos.betweenClosed(world.offset(-4, -2, -4), world.offset(4, 2, 4)))
        level.removeBlock(pos, false);
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "hunting_grasper_evolution")
  public static void swimming_hunting_graspers_share_costs_priorities_and_preserve_mouth_state(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = digestive(helper, core, false);
      var level = helper.getLevel();
      stomach.retainFlesh(100);
      stomach.credit(MaterialCost.FIBER, 100);
      stomach.credit(MaterialCost.BONE, 32);
      core.aquatic().legUsage().restore(100, 900);
      var quote = BioshipGrasperEvolution.proposal(core);
      helper.assertTrue(
          quote != null
              && quote.urgency() == 1030
              && quote.flesh() == BioshipLegEvolution.SWIM_COST.flesh()
              && quote.fiber() == BioshipLegEvolution.SWIM_COST.fiber(),
          "Hunting arms used different specialization rules");
      helper.assertTrue(
          BioshipGrasperEvolution.evolve(core, quote.pos()),
          "Funded swimming grasper did not evolve");
      var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(quote.pos());
      helper.assertTrue(
          mouth.swimmingGrasper(0)
              && BioshipGraspers.slots(core).stream().anyMatch(BioshipGraspers.Slot::swimming),
          "Adapted mouth did not expose its swimming grasper");
      var saved = mouth.saveWithFullMetadata(level.registryAccess());
      mouth.loadAdditional(saved, level.registryAccess());
      helper.assertTrue(
          mouth.swimmingGrasper(0)
              && stomach.material(MaterialCost.FLESH) == 96
              && stomach.material(MaterialCost.FIBER) == 96,
          "Grasper adaptation lost state or its cost");
      helper.assertTrue(
          BioshipGrasperEvolution.proposal(core) == null,
          "Hunting arm specialization ignored its cooldown");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 600, batch = "aquatic_fixture_geometry")
  public static void aquatic_test_zone_has_real_water_air_containment_and_capped_replenishment(
      GameTestHelper helper) {
    var level = helper.getLevel();
    AquaticLab.build(level, new BlockPos(1000000, 80, 1000000));
    var center = AquaticLab.center(level);
    helper.assertTrue(
        AquaticLab.RADIUS * 2 + 1 > 2 * 41 && AquaticLab.FISH_CAP >= 80,
        "Ocean expansion is not more than twice the old width or fish budget was not scaled");
    int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, grass = 0, kelp = 0, coral = 0;
    for (int x = -AquaticLab.RADIUS + 2; x < AquaticLab.RADIUS - 1; x++)
      for (int z = -AquaticLab.RADIUS + 2; z < AquaticLab.RADIUS - 1; z++) {
        int floor = OceanLabTerrain.floor(x, z);
        low = Math.min(low, floor);
        high = Math.max(high, floor);
        var root = level.getBlockState(center.offset(x, floor + 1, z));
        if (root.is(Blocks.SEAGRASS)) grass++;
        if (root.is(Blocks.KELP) || root.is(Blocks.KELP_PLANT)) kelp++;
        if (root.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS)) coral++;
      }
    helper.assertTrue(
        high - low >= 5 && grass > 100 && kelp > 50 && coral > 10,
        "Expanded ocean omitted relief/vegetation/reef: range="
            + (high - low)
            + " grass="
            + grass
            + " kelp="
            + kelp
            + " coral="
            + coral);
    helper.assertTrue(
        level.getBlockState(center.offset(-16, -AquaticLab.DEPTH + 13, 12)).is(Blocks.STONE)
            && level
                .getFluidState(center.offset(-16, -AquaticLab.DEPTH + 10, 12))
                .is(net.minecraft.tags.FluidTags.WATER),
        "Seabed arch lacks its submerged opening or roof");
    helper.assertTrue(
        level.getBlockState(center.above(AquaticLab.AIR_HEIGHT)).is(Blocks.GLASS),
        "Swimming test has no containment roof");
    helper.assertTrue(
        level.getFluidState(center.below(3)).is(net.minecraft.tags.FluidTags.WATER)
            && level.getFluidState(center.above()).isEmpty(),
        "Swimming test omitted either water or breathable air");
    helper.assertTrue(
        level.getBlockState(center.east(AquaticLab.RADIUS)).is(Blocks.GLASS),
        "Swimming test has an open containment wall");
    for (var side : Direction.Plane.HORIZONTAL) {
      var wall = center.relative(side, AquaticLab.ISOLATION_RADIUS);
      var apron = center.relative(side, AquaticLab.RADIUS + 2).below();
      helper.assertTrue(
          level.getBlockState(wall).is(Blocks.STONE_BRICKS)
              && level.getBlockState(wall.above(AquaticLab.AIR_HEIGHT + 1)).is(Blocks.STONE_BRICKS),
          "Aquatic isolation wall has an open sight line");
      helper.assertTrue(
          level.getBlockState(apron).is(Blocks.STONE_SLAB)
              && level.getBlockState(apron).getValue(SlabBlock.TYPE)
                  == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM,
          "Aquatic apron is not spawn-proof bottom slabs");
    }
    for (int dx : new int[] {-12, 12})
      for (int dz : new int[] {-12, 12})
        helper.assertTrue(
            !level.getBlockState(center.offset(dx, -AquaticLab.DEPTH + 1, dz)).is(Blocks.SPAWNER),
            "Aquatic lab still depends on ordinary fish spawners");
    var state = EcosystemLab.State.get(level);
    LabStarters.core(level, state, center.offset(-8, -2, 0));
    helper
        .startSequence()
        .thenWaitUntil(
            () -> {
              // The normal controller replenishes only once chunks actually simulate entities.
              AquaticLab.replenish(level);
              helper.assertTrue(
                  AquaticLab.fishCount(level) >= 8,
                  "Waiting for creature-owned chunks and fish replenishment to enter simulation");
            })
        .thenExecute(
            () -> {
              for (var fish :
                  level.getEntitiesOfClass(
                      net.minecraft.world.entity.animal.AbstractFish.class,
                      new AABB(center).inflate(32))) fish.discard();
              helper.runAfterDelay(1, () -> LabPopulation.replenish(level, state));
              helper.runAfterDelay(
                  3,
                  () -> {
                    var counts = LabPopulation.counts(level, state.center());
                    helper.assertTrue(
                        AquaticLab.fishCount(level) == AquaticLab.FISH_CAP,
                        "A full refill did not replenish fish immediately: "
                            + AquaticLab.fishCount(level)
                            + "/"
                            + AquaticLab.FISH_CAP);
                    helper.assertTrue(
                        counts.prey() > 0
                            && counts.prey() <= EcosystemLab.PREY_CAP
                            && counts.hostiles() > 0
                            && counts.hostiles() <= EcosystemLab.HOSTILE_CAP,
                        "Full refill omitted land populations or exceeded caps: " + counts);
                  });
              for (int i = 0; i < 30; i++)
                helper.runAfterDelay(i + 4, () -> AquaticLab.replenish(level));
              helper.runAfterDelay(
                  35,
                  () -> {
                    helper.assertTrue(
                        AquaticLab.fishCount(level) == AquaticLab.FISH_CAP,
                        "Fish replenishment count="
                            + AquaticLab.fishCount(level)
                            + ", expected="
                            + AquaticLab.FISH_CAP);
                    var before = AquaticLab.center(level);
                    LabStarters.clear(level, state);
                    AquaticLab.reset(level, new BlockPos(1000000, 80, 1000000));
                    helper.assertTrue(
                        AquaticLab.center(level).equals(before),
                        "Reset moved the water enclosure upward");
                    helper.succeed();
                  });
            });
  }

  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "ecosystem_runtime_fixtures")
  public static void ecosystem_runtime_seeds_cores_and_reset_replaces_them_without_a_probe(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var center = new BlockPos(2000000, 80, 2000000);
    EcosystemLab.prepare(level, center);
    var state = EcosystemLab.State.get(level);
    helper.assertTrue(
        state.starters().size() == 12,
        "Preset did not create three cores and nine native starter components");
    helper.assertTrue(
        level.getBlockEntity(LabPopulation.controller(state.center()))
            instanceof net.minecraft.world.level.block.entity.CommandBlockEntity,
        "Preset did not install a command-block population controller");
    helper.runAfterDelay(
        10,
        () -> {
          helper.assertTrue(
              LabPopulation.counts(level, state.center()).cores() == 3,
              "Preset starters were missing without the optional observer");
          LabStarters.clear(level, state);
          helper.assertTrue(
              LabPopulation.counts(level, state.center()).cores() == 0,
              "Reset cleanup left old bioship cores behind");
          LabStarters.seed(level, state);
          helper.runAfterDelay(
              10,
              () -> {
                helper.assertTrue(
                    state.starters().size() == 12
                        && LabPopulation.counts(level, state.center()).cores() == 3,
                    "Reset duplicated or omitted starter creatures");
                LabStarters.clear(level, state);
                helper.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "solid_leg_mounts")
  public static void legs_require_full_face_support_and_reject_edges_pipes_and_orphaning_moves(
      GameTestHelper helper) {
    var body =
        Map.of(BlockPos.ZERO, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    for (var face : Direction.values()) {
      var leg =
          Create_bioships.AMOEBA_LEG
              .get()
              .defaultBlockState()
              .setValue(AmoebaLegBlock.FACING, face);
      var pos = BlockPos.ZERO.relative(face);
      helper.assertTrue(AmoebaLegBlock.mounted(body, pos, leg), "Full face rejected: " + face);
      for (var forbidden : List.of(Create_bioships.CORE.get(), Create_bioships.LUNGS.get())) {
        helper.assertTrue(
            !AmoebaLegBlock.mounted(Map.of(BlockPos.ZERO, forbidden.defaultBlockState()), pos, leg),
            "Leg mounted on protected organ: " + forbidden);
      }
      helper.assertTrue(
          !AmoebaLegBlock.canHostLeg(
              Create_bioships.FEEDING_MOUTH
                  .get()
                  .defaultBlockState()
                  .setValue(FeedingMouthBlock.FACING, face),
              BlockPos.ZERO,
              face),
          "Leg covered mouth opening");
      helper.assertTrue(
          !AmoebaLegBlock.canHostLeg(
              Create_bioships.GILLS
                  .get()
                  .defaultBlockState()
                  .setValue(RespiratoryOrganBlock.FACING, face),
              BlockPos.ZERO,
              face),
          "Leg covered gill intake");
      var diagonal =
          pos.relative(face.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP);
      helper.assertTrue(
          !AmoebaLegBlock.mounted(body, diagonal, leg), "Diagonal mount accepted: " + face);
      helper.assertTrue(
          !AmoebaLegBlock.mounted(
              Map.of(BlockPos.ZERO, Create_bioships.INTESTINE.get().defaultBlockState()), pos, leg),
          "Thin intestine accepted as full support");
    }
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var host = core.getBlockPos().east();
      helper
          .getLevel()
          .setBlock(host, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
      var pos = host.east();
      var leg =
          Create_bioships.AMOEBA_LEG
              .get()
              .defaultBlockState()
              .setValue(AmoebaLegBlock.FACING, Direction.EAST);
      helper.getLevel().setBlock(pos, leg, 3);
      helper.assertTrue(
          !ComponentTransport.begin(core.amoebaComponents(), pos, pos.north(), false),
          "Relocation accepted a corner-only leg mount");
      helper.assertTrue(
          !BioshipSupport.safeMove(core, host, host.above(), false),
          "Body relocation orphaned an existing leg mount");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "transitional_leg_faces")
  public static void transitional_legs_use_solid_exposed_faces_instead_of_thin_pipe_roots(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    helper
        .getLevel()
        .setBlock(
            core.getBlockPos().east(), Create_bioships.INTESTINE.get().defaultBlockState(), 3);
    helper.runAfterDelay(
        1,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  var cells = BioshipStructure.cells(ship);
                  var origin = Vec3.atLowerCornerOf(current.structuralOrigin());
                  for (var leg : BioshipSupport.layout(current).legs()) {
                    var inward = Vec3.atLowerCornerOf(leg.face().getNormal()).scale(-.04);
                    var host = BlockPos.containing(leg.root().add(origin).add(inward));
                    helper.assertTrue(
                        AmoebaLegBlock.supportsFace(cells.get(host), host, leg.face()),
                        "Transitional tentacle root does not lie on a full exposed body face: "
                            + host);
                    helper.assertTrue(
                        !cells.containsKey(host.relative(leg.face())),
                        "Tentacle root is buried inside another component");
                  }
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "repair_legacy_leg_mount")
  public static void anatomy_repositions_a_corner_only_leg_before_digestion_exists(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    helper
        .getLevel()
        .setBlock(core.getBlockPos().south(), BioshipTissue.body().defaultBlockState(), 3);
    var bad = core.getBlockPos().east().north();
    helper
        .getLevel()
        .setBlock(
            bad,
            Create_bioships.AMOEBA_LEG
                .get()
                .defaultBlockState()
                .setValue(AmoebaLegBlock.FACING, Direction.EAST),
            3);
    BioshipAnatomy.invalidate(core);
    var proposal = BioshipAnatomy.proposal(core);
    helper.assertTrue(
        proposal != null && proposal.pos().equals(bad),
        "Legacy diagonal leg was not queued for a full-face relocation");
    helper.assertTrue(BioshipAnatomy.execute(core, bad), "Full-face repair could not begin");
    helper.runAfterDelay(
        45,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var cells = BioshipStructure.cells(ship);
                  var legs =
                      cells.entrySet().stream()
                          .filter(e -> BioshipLegEvolution.leg(e.getValue()))
                          .toList();
                  helper.assertTrue(
                      legs.size() == 1
                          && legs.stream()
                              .allMatch(
                                  e -> AmoebaLegBlock.mounted(cells, e.getKey(), e.getValue())),
                      "Repair duplicated, lost or left a leg on a non-solid mounting face");
                }));
  }

  @GameTest(
      template = "test_empty",
      timeoutTicks = 100,
      batch = "leg_mount_changed_during_transport")
  public static void moving_leg_keeps_its_cargo_if_the_supporting_face_disappears(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var host = core.getBlockPos().west();
    var source = core.getBlockPos().east();
    var target = host.north();
    level.setBlock(host, BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(
        source,
        Create_bioships.AMOEBA_LEG
            .get()
            .defaultBlockState()
            .setValue(AmoebaLegBlock.FACING, Direction.EAST),
        3);
    helper.assertTrue(
        ComponentTransport.begin(core.amoebaComponents(), source, target, false),
        "Valid supported leg move did not begin");
    var move = core.organMove();
    level.removeBlock(host, false);
    helper.runAfterDelay(
        45,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      level.getBlockState(target).isAir(),
                      "Transport committed a leg after its host was removed");
                  var cargo = SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
                  helper.assertTrue(
                      cargo instanceof ServerSubLevel
                          && BioshipLegEvolution.leg(level.getBlockState(move.cargoBlock())),
                      "Canceled mounting lost the real leg cargo");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "face_connected_anatomy")
  public static void body_topology_distinguishes_edges_bridges_and_conserved_repair(
      GameTestHelper helper) {
    helper.assertTrue(
        BodyTopology.components(Set.of(BlockPos.ZERO, new BlockPos(1, 1, 0))) == 2,
        "Corner contact counted as face-connected anatomy");
    helper.assertTrue(
        BodyTopology.components(Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0))) == 1,
        "Shared full cell face was disconnected");
    var body = Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
    helper.assertTrue(
        !BodyTopology.canRemove(body, new BlockPos(1, 0, 0))
            && !BodyTopology.canMove(body, new BlockPos(1, 0, 0), new BlockPos(1, 1, 0), false),
        "In-transit removal disconnected a structural bridge");
    helper.assertTrue(
        BodyTopology.canMove(body, new BlockPos(1, 0, 0), new BlockPos(1, 1, 0), true),
        "Retained support was ignored");
    var socketBridge =
        Map.of(
            BlockPos.ZERO,
            Create_bioships.CORE.get().defaultBlockState(),
            new BlockPos(1, 0, 0),
            Create_bioships.AMOEBA_LEG.get().defaultBlockState(),
            new BlockPos(2, 0, 0),
            BioshipTissue.body().defaultBlockState());
    helper.assertTrue(
        BodyTopology.components(BodyTopology.structuralCells(socketBridge)) == 2,
        "Leg socket counted as a structural bridge between body sections");
    var broken = Set.of(BlockPos.ZERO, new BlockPos(1, 1, 0));
    helper.assertTrue(
        BodyTopology.canMove(broken, new BlockPos(1, 1, 0), new BlockPos(1, 0, 0), false),
        "Repair could not reconnect an existing disconnected component");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "structural_bridge_transport")
  public static void moving_an_organ_cannot_temporarily_remove_a_body_bridge(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var source = core.getBlockPos().east();
      var target = core.getBlockPos().south();
      helper.getLevel().setBlock(source, Create_bioships.STOMACH.get().defaultBlockState(), 3);
      helper.getLevel().setBlock(source.east(), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          !BioshipSupport.safeMove(core, source, target, false)
              && !ComponentTransport.begin(core.amoebaComponents(), source, target, false),
          "A supporting stomach could leave body sections disconnected during transport");
      helper.assertTrue(
          helper.getLevel().getBlockState(source).is(Create_bioships.STOMACH.get())
              && core.organMove() == null,
          "Rejected bridge move changed the body");
      helper.assertTrue(
          BioshipSupport.safeMove(core, source, target, true),
          "A move retaining real source tissue was rejected");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "rejoin_legacy_split")
  public static void a_disconnected_core_rejoins_existing_anatomy_without_climbing_higher(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var destination = core.getBlockPos().east();
    helper.getLevel().setBlock(destination.above(), BioshipTissue.body().defaultBlockState(), 3);
    helper.assertTrue(
        core.coreMotion().tick(core) && destination.equals(core.coreMotion().target()),
        "Disconnected core did not select a nearby reconnection");
    helper.runAfterDelay(
        20,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      BodyTopology.components(
                                  BodyTopology.structuralCells(BioshipStructure.cells(ship)))
                              == 1
                          && current.getBlockPos().equals(destination),
                      "Core failed to reconnect: actual="
                          + current.getBlockPos()
                          + " expected="
                          + destination
                          + " sections="
                          + BodyTopology.components(
                              BodyTopology.structuralCells(BioshipStructure.cells(ship))));
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "no_gratuitous_perch")
  public static void an_equally_occluded_higher_perch_does_not_trigger_reconfiguration(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    level.setBlock(core.getBlockPos().east(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    for (var pos : BlockPos.betweenClosed(root.offset(-4, 0, -4), root.offset(4, 8, 4)))
      if (Math.abs(pos.getX() - root.getX()) == 4
          || Math.abs(pos.getZ() - root.getZ()) == 4
          || pos.getY() == root.getY() + 8)
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
    helper.runAfterDelay(
        2,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(
                      current.coreMotion().coverage(current, current.getBlockPos()) == 0,
                      "Occluded fixture unexpectedly sees through its walls");
                  current.coreMotion().layoutChanged(current);
                  current.coreMotion().tick(current);
                  helper.assertTrue(
                      !current.coreMotion().active() && current.organMove() == null,
                      "An equally occluded higher perch caused pointless body rearrangement");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "parallel_body_combat")
  public static void defensive_action_and_owned_digestive_conversion_run_independently(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var threat = EntityType.ZOMBIE.create(level);
    threat.setNoAi(true);
    threat.setNoGravity(true);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    threat.setPos(here.x + 8, here.y, here.z);
    level.addFreshEntity(threat);
    afterIndexed(
        helper,
        List.of(threat),
        () -> {
          try {
            var root = core.getBlockPos();
            level.setBlock(root.north(), block("biomancy:bio_forge").defaultBlockState(), 3);
            level.setBlock(root.west(), block("biomancy:decomposer").defaultBlockState(), 3);
            level.setBlock(root.west().north(), block("biomancy:digester").defaultBlockState(), 3);
            core.defense().remember(core, threat);
            helper.assertTrue(
                core.brain().choose(core).action() == BioshipBrain.Action.DEFEND,
                "Fixture has no defensive action");
            var body = core.adaptation().behavior().choose(core);
            helper.assertTrue(
                body != null && body.action() == BioshipBrain.Action.CONFIGURE,
                "Defense suppressed owned digestive conversion: "
                    + core.adaptation().behavior().debug().getString("Reason"));
            BioshipConfiguration.execute(core);
            helper.assertTrue(
                ComponentTransport.busy(core.amoebaComponents()) && !core.organMove().usesArm(),
                "Defense suppressed the timed digestive merge");
            helper.assertTrue(
                core.brain().choose(core).action() == BioshipBrain.Action.DEFEND,
                "Body work replaced the defensive action");
            var debug = NavigationDebug.inspect(core);
            helper.assertTrue(
                debug.getString("Action").equals("DEFEND")
                    && debug.getCompound("BodyWork").getString("Action").equals("CONFIGURE"),
                "Debug snapshot does not expose both independent trees");
            helper.runAfterDelay(
                50,
                () -> {
                  try {
                    helper.assertTrue(
                        DigestiveSystem.functional(core(ship)),
                        "Owned components failed to finish connected digestion during defense");
                    helper.succeed();
                  } finally {
                    threat.discard();
                    cleanup(helper, ship);
                  }
                });
          } catch (Throwable failure) {
            threat.discard();
            cleanup(helper, ship);
            throw failure;
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "parallel_body_escape")
  public static void emergency_mouth_regrowth_continues_during_escape_and_pays_once(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    core.adaptation().creditRecovery(MaterialCost.FLESH, 8);
    core.adaptation().creditRecovery(MaterialCost.FIBER, 4);
    core.adaptation().creditRecovery(MaterialCost.BONE, 2);
    var injury = core.body().save();
    injury.putDouble("Health", 20);
    core.body().load(injury, helper.getLevel().registryAccess());
    var panic = new net.minecraft.nbt.CompoundTag();
    panic.put(
        "Danger",
        VisionDebug.vector(
            BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO).add(3, 0, 0)));
    panic.putLong("PanicUntil", helper.getLevel().getGameTime() + 100);
    core.autonomy().load(panic);
    helper.assertTrue(
        core.brain().choose(core).action() == BioshipBrain.Action.FLEE,
        "Fixture has no escape action");
    helper.runAfterDelay(
        25,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var current = core(ship);
                  helper.assertTrue(current.hasMouth(), "Escape prevented paid essential regrowth");
                  helper.assertTrue(
                      current.adaptation().material(MaterialCost.FLESH) == 0
                          && current.adaptation().material(MaterialCost.FIBER) == 0
                          && current.adaptation().material(MaterialCost.BONE) == 0,
                      "Essential regrowth did not debit its resources exactly once");
                  helper.assertTrue(
                      DigestiveSystem.mouths(current).size() == 1,
                      "Concurrent action/body work duplicated a mouth");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "body_transaction_lock")
  public static void carried_organs_lock_body_replanning_without_spending_or_duplicate_regrowth(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var level = helper.getLevel();
      var source = core.getBlockPos().north();
      level.setBlock(source, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
      core.adaptation().creditRecovery(MaterialCost.FLESH, 100);
      core.adaptation().creditRecovery(MaterialCost.FIBER, 100);
      core.adaptation().creditRecovery(MaterialCost.BONE, 100);
      helper.assertTrue(
          ComponentTransport.begin(
              core.amoebaComponents(), source, core.getBlockPos().east(), false),
          "Could not start conserved mouth movement");
      var before = core.adaptation().save().getCompound("Reserve");
      helper.assertTrue(
          core.adaptation().behavior().choose(core) == null
              && core.adaptation().proposals(core).isEmpty(),
          "Body tree proposed regrowth while the original organ was carried");
      helper.assertTrue(
          !core.brain().allowsConfiguration(core),
          "Configuration bypassed the component transaction lock");
      BioshipIntegration.assimilate(core, ship);
      helper.assertTrue(
          core.organMove() != null
              && core.adaptation().save().getCompound("Reserve").equals(before),
          "Concurrent integration lost its transaction or spent reserved stock");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "core_transfer_death_receipt")
  public static void sable_core_transfer_never_tombstones_a_living_replacement(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var original = core(ship);
    var level = helper.getLevel();
    var from = original.getBlockPos();
    var to = from.above();
    level.setBlock(from.east(), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(to.east(), BioshipTissue.body().defaultBlockState(), 3);
    BioshipIntegration.bind(ship);
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(
            from, to, 0, net.minecraft.world.level.block.Rotation.NONE, level),
        List.of(from));
    helper.runAfterDelay(
        2,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      level.getBlockEntity(to) instanceof LivingCoreBlockEntity,
                      "Sable did not create the replacement core");
                  var replacement = (LivingCoreBlockEntity) level.getBlockEntity(to);
                  helper.assertTrue(
                      !replacement.body().dead() && !BioshipDecay.dead(ship),
                      "Successful core transfer marked the living organism for decay");
                  var deaths =
                      BioshipDecay.Deaths.get(level)
                          .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
                  helper.assertTrue(
                      !deaths.contains(BioshipDecay.lineage(ship).toString())
                          && !deaths.contains(ship.getUniqueId().toString()),
                      "Successful transfer wrote an irreversible death tombstone");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "failed_core_transfer_death")
  public static void incomplete_core_transfer_still_marks_the_construct_dead(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var original = core(ship);
    var level = helper.getLevel();
    var from = original.getBlockPos();
    level.setBlock(from.east(), BioshipTissue.body().defaultBlockState(), 3);
    BioshipIntegration.bind(ship);
    original.recordTransfer(level, from.above());
    level.removeBlock(from, false);
    helper.runAfterDelay(
        2,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      BioshipDecay.dead(ship),
                      "Incomplete transfer suppressed real core-removal death");
                }));
  }

  @GameTest(
      template = "test_empty",
      timeoutTicks = 100,
      batch = "core_transfer_chunk_task_boundary")
  public static void polled_server_tasks_cannot_tombstone_an_in_progress_core_handoff(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var original = core(ship);
    var level = helper.getLevel();
    var from = original.getBlockPos();
    var to = from.above();
    level.setBlock(from.east(), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(to.east(), BioshipTissue.body().defaultBlockState(), 3);
    BioshipIntegration.bind(ship);
    var saved = original.saveWithFullMetadata(level.registryAccess());
    original.recordTransfer(level, to);
    level.removeBlock(from, false);
    // Chunk loading may poll server tasks before the destination BE has been installed.
    for (int n = 0; n < 16; n++) if (!level.getServer().pollTask()) break;
    helper.assertTrue(
        !BioshipDecay.dead(ship), "An in-progress handoff was treated as permanent core removal");
    level.setBlock(to, Create_bioships.CORE.get().defaultBlockState(), 3);
    var replacement = (LivingCoreBlockEntity) level.getBlockEntity(to);
    replacement.loadAdditional(saved, level.registryAccess());
    helper.runAfterDelay(
        2,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  helper.assertTrue(
                      !replacement.body().dead() && !BioshipDecay.dead(ship),
                      "Completed handoff did not survive end-of-tick validation");
                  var deaths =
                      BioshipDecay.Deaths.get(level)
                          .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
                  helper.assertTrue(
                      !deaths.contains(BioshipDecay.lineage(ship).toString()),
                      "Nested task polling left a persistent false death record");
                }));
  }

  private static void deathFixture(
      GameTestHelper helper, LivingCoreBlockEntity core, boolean hungry) {
    core.body().observe(core);
    var data = core.saveWithFullMetadata(helper.getLevel().registryAccess());
    data.putInt("Satiety", hungry ? 0 : 600);
    core.loadAdditional(data, helper.getLevel().registryAccess());
    var injury = core.body().save();
    injury.putDouble("Health", 1);
    core.body().load(injury, helper.getLevel().registryAccess());
    var air = core.aquatic().save();
    air.putInt("Air", 0);
    core.aquatic().load(air);
  }

  private static void verifyDeath(
      GameTestHelper helper, LivingCoreBlockEntity core, BioshipDeathCause expected) {
    helper.assertTrue(
        core.body().dead() && core.body().deathCause() == expected,
        "Wrong fatal cause: expected " + expected + ", got " + core.body().deathCause());
    helper.assertTrue(
        core.ship().getUserDataTag().getString("BioshipDeathCause").equals(expected.name()),
        "Wreck grid did not retain the cause of death");
    var saved = core.body().save();
    var loaded = new BioshipBody();
    loaded.load(saved, helper.getLevel().registryAccess());
    helper.assertTrue(
        loaded.deathCause() == expected && loaded.deathAt() == core.body().deathAt(),
        "Cause or timestamp did not survive body reload");
    core.body().die(core, BioshipDeathCause.UNKNOWN);
    helper.assertTrue(
        core.body().deathCause() == expected,
        "A later death notification overwrote the actual fatal event");
    var ledger =
        BioshipDecay.Deaths.get(helper.getLevel())
            .save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
    var restored =
        BioshipDecay.Deaths.load(ledger, helper.getLevel().registryAccess())
            .save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
    helper.assertTrue(
        restored
            .getCompound("Causes")
            .getString(BioshipDecay.lineage(core.ship()).toString())
            .equals(expected.name()),
        "Death registry reload lost the fatal cause");
    var legacy = saved.copy();
    legacy.remove("DeathCause");
    legacy.remove("DeathAt");
    loaded.load(legacy, helper.getLevel().registryAccess());
    helper.assertTrue(
        loaded.deathCause() == BioshipDeathCause.UNKNOWN && loaded.deathAt() == -1,
        "Legacy deaths invented a cause or timestamp");
  }

  private static void deepWater(GameTestHelper helper, LivingCoreBlockEntity core) {
    var world =
        BlockPos.containing(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    for (var pos : BlockPos.betweenClosed(world.offset(-8, 0, -8), world.offset(8, 12, 8)))
      helper.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "fatal_starvation_cause")
  public static void metabolic_starvation_records_a_persistent_fatal_cause(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    helper
        .getLevel()
        .setBlock(core.getBlockPos().east(), BioshipTissue.body().defaultBlockState(), 3);
    deathFixture(helper, core, true);
    helper.runAfterDelay(
        25,
        () ->
            finish(
                helper, ship, () -> verifyDeath(helper, core(ship), BioshipDeathCause.STARVATION)));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "fatal_asphyxiation_cause")
  public static void missing_lungs_on_land_records_asphyxiation_not_drowning(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    for (var face : Direction.Plane.HORIZONTAL)
      helper
          .getLevel()
          .setBlock(core.getBlockPos().relative(face), BioshipTissue.body().defaultBlockState(), 3);
    deathFixture(helper, core, false);
    helper.assertTrue(
        BioshipRespiration.airAccess(core) && !BioshipRespiration.canBreathe(core),
        "Fixture is not an air-exposed body requiring lungs");
    helper.runAfterDelay(
        25,
        () ->
            finish(
                helper,
                ship,
                () -> verifyDeath(helper, core(ship), BioshipDeathCause.ASPHYXIATION)));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "fatal_drowning_cause")
  public static void exhausted_air_underwater_records_drowning_not_starvation(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    deepWater(helper, core);
    deathFixture(helper, core, false);
    helper.assertTrue(
        !BioshipRespiration.airAccess(core) && !BioshipRespiration.activeGills(core),
        "Drowning fixture can breathe");
    helper.runAfterDelay(
        25,
        () ->
            finish(
                helper, ship, () -> verifyDeath(helper, core(ship), BioshipDeathCause.DROWNING)));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "gilled_starvation_cause")
  public static void active_gills_do_not_mislabel_underwater_starvation_as_drowning(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    helper
        .getLevel()
        .setBlock(
            core.getBlockPos().east(),
            Create_bioships.GILLS
                .get()
                .defaultBlockState()
                .setValue(RespiratoryOrganBlock.FACING, Direction.NORTH),
            3);
    deepWater(helper, core);
    deathFixture(helper, core, true);
    helper.assertTrue(
        BioshipRespiration.activeGills(core) && BioshipRespiration.canBreathe(core),
        "Gilled fixture cannot breathe underwater");
    helper.runAfterDelay(
        25,
        () ->
            finish(
                helper, ship, () -> verifyDeath(helper, core(ship), BioshipDeathCause.STARVATION)));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "retired_sable_owner_not_death")
  public static void retired_physics_owner_with_an_intact_core_does_not_write_a_death_record(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    BioshipIntegration.bind(ship);
    BioshipCoreLifecycle.removed(core);
    ship.markRemoved();
    core.setRemoved();
    helper.runAfterDelay(
        2,
        () -> {
          try {
            helper.assertTrue(
                !BioshipDecay.dead(ship) && !core.body().dead(),
                "Retiring the physics body was mistaken for destruction of its core");
            var deaths =
                BioshipDecay.Deaths.get(helper.getLevel())
                    .save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
            helper.assertTrue(
                !deaths.contains(ship.getUniqueId().toString()),
                "Retired owner poisoned its replacement's persistent lineage");
            helper.succeed();
          } finally {
            cleanup(helper, ship);
          }
        });
  }

  private static StomachBlockEntity crowdedMounts(
      GameTestHelper helper, LivingCoreBlockEntity core, boolean tube) {
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    if (!tube) {
      var mouth = core.mouthBlock();
      level.removeBlock(mouth, false);
      level.setBlock(
          stomach.getBlockPos().north(),
          Create_bioships.FEEDING_MOUTH.get().defaultBlockState(),
          3);
      DigestiveSystem.connect(core, core.ship());
    }
    var sites = BioshipLegGrowth.sites(core, BioshipStructure.cells(core.ship()));
    for (var site : sites)
      level.setBlock(site.getKey(), Create_bioships.LUNGS.get().defaultBlockState(), 3);
    helper.assertTrue(
        BioshipLegGrowth.sites(core, BioshipStructure.cells(core.ship())).isEmpty(),
        "Crowded fixture still has a legal mount");
    helper.assertTrue(
        DigestiveSystem.functional(core), "Crowded fixture has no connected digestion");
    core.adaptation().creditRecovery(MaterialCost.FLESH, 300);
    core.adaptation().creditRecovery(MaterialCost.FIBER, 200);
    core.adaptation().creditRecovery(MaterialCost.BONE, 100);
    return stomach;
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "paid_mount_encasement")
  public static void missing_leg_faces_encase_a_paid_intestine_without_breaking_digestion(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      var stomach = crowdedMounts(helper, core, true);
      var plan = BioshipMountGrowth.plan(core);
      helper.assertTrue(
          plan != null
              && plan.state().is(Create_bioships.INTESTINE.get())
              && plan.state().getValue(IntestineBlock.ENCASED),
          "Did not prefer encasing an existing intestine for a mounting face");
      var original = helper.getLevel().getBlockState(plan.pos());
      var account = BioshipStorage.account(core);
      int flesh = account.material(MaterialCost.FLESH),
          fiber = account.material(MaterialCost.FIBER);
      int paidStock = stomach.paidTubeStock, freeStock = stomach.freeTubeStock;
      helper.assertTrue(
          BioshipMountGrowth.grow(core, plan.pos(), plan.state()), "Paid tube encasement failed");
      var actual = helper.getLevel().getBlockState(plan.pos());
      helper.assertTrue(
          actual.is(Create_bioships.INTESTINE.get())
              && actual.getValue(IntestineBlock.ENCASED)
              && actual.getValue(IntestineBlock.PAID).equals(original.getValue(IntestineBlock.PAID))
              && actual
                  .getValue(IntestineBlock.MANUAL)
                  .equals(original.getValue(IntestineBlock.MANUAL))
              && DigestiveSystem.functional(core),
          "Encasing lost tube ownership or digestive connectivity");
      helper.assertTrue(
          account.material(MaterialCost.FLESH) == flesh - 8
              && account.material(MaterialCost.FIBER) == fiber - 4
              && stomach.paidTubeStock == paidStock
              && stomach.freeTubeStock == freeStock,
          "Encasing refunded tube materials or failed to pay for tissue");
      var leg = BioshipLegGrowth.proposal(core);
      helper.assertTrue(
          leg != null && BioshipLegGrowth.develop(core, leg.pos(), leg.state()),
          "New support did not enable paid leg growth");
      helper.assertTrue(
          AmoebaLegBlock.mounted(BioshipStructure.cells(ship), leg.pos(), leg.state()),
          "New leg has no eligible full supporting face");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "paid_mount_flesh")
  public static void crowded_organs_grow_compact_flesh_hosts_and_preserve_openings(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      crowdedMounts(helper, core, false);
      var mouth = core.mouthBlock();
      var facing = helper.getLevel().getBlockState(mouth).getValue(FeedingMouthBlock.FACING);
      var plan = BioshipMountGrowth.plan(core);
      helper.assertTrue(
          plan != null && plan.state().is(BioshipTissue.body()),
          "No paid flesh support was proposed when all mount faces were occupied");
      helper.assertTrue(
          !plan.pos().equals(mouth.relative(facing))
              && !BioshipRespiration.blocksOpening(core, plan.pos()),
          "Support covers an intake");
      var account = BioshipStorage.account(core);
      int flesh = account.material(MaterialCost.FLESH);
      helper.assertTrue(
          BioshipMountGrowth.grow(core, plan.pos(), plan.state()), "Paid flesh support failed");
      helper.assertTrue(
          account.material(MaterialCost.FLESH) == flesh - 8
              && BioshipLegGrowth.proposal(core) != null
              && DigestiveSystem.functional(core),
          "New flesh was free, blocked digestion, or provided no legal leg site");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "mount_budget_revalidation")
  public static void support_growth_keeps_future_leg_budget_and_rejects_stale_or_hungry_work(
      GameTestHelper helper) {
    var ship = assemble(helper);
    try {
      var core = core(ship);
      crowdedMounts(helper, core, true);
      var plan = BioshipMountGrowth.plan(core);
      helper.assertTrue(
          plan != null && plan.completeCost().flesh() >= 24,
          "Quote omitted future support/leg construction");
      var account = BioshipStorage.account(core);
      int before = account.material(MaterialCost.FLESH);
      account.spend(MaterialCost.FLESH, before - 8);
      var source = helper.getLevel().getBlockState(plan.pos());
      helper.assertTrue(
          !BioshipMountGrowth.grow(core, plan.pos(), plan.state())
              && account.material(MaterialCost.FLESH) == 8
              && helper.getLevel().getBlockState(plan.pos()).equals(source),
          "A tissue-only budget consumed the remaining-leg/emergency reserve");
      int investment = EmergencyReserve.TARGET.flesh() + plan.completeCost().flesh();
      account.credit(MaterialCost.FLESH, investment - 1 - 8);
      helper.assertTrue(
          core.brain().materialNeeds(core).flesh() > 0,
          "Foraging forgot the extra mounting investment before it was affordable");
      helper.assertTrue(
          core.adaptation().proposals(core).stream()
              .noneMatch(p -> p.action() == BioshipBrain.Action.GROW_LARYNX),
          "A lower-priority vocal upgrade spent the pending mobility budget");
      account.credit(MaterialCost.FLESH, before - (investment - 1));
      var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
      saved.putInt("Satiety", 49);
      core.loadAdditional(saved, helper.getLevel().registryAccess());
      helper.assertTrue(
          BioshipMountGrowth.proposal(core) == null,
          "Pressing food need did not defer support investment");
      helper.succeed();
    } finally {
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "creeper_full_blast_envelope")
  public static void creeper_core_escape_requires_full_damage_clearance_and_usable_tissue(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var creeper = EntityType.CREEPER.create(helper.getLevel());
    try {
      var core = core(ship);
      var root = core.getBlockPos();
      var world = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      creeper.setPos(world.x - 3, world.y - .5, world.z);
      for (int x = 1; x <= 2; x++)
        helper.getLevel().setBlock(root.east(x), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          !core.coreMotion().canAvoidBlast(core, creeper),
          "A body wholly within the damage envelope was considered safe to fight");
      for (int x = 3; x <= 6; x++)
        helper.getLevel().setBlock(root.east(x), BioshipTissue.body().defaultBlockState(), 3);
      helper.assertTrue(
          core.coreMotion().canAvoidBlast(core, creeper),
          "A connected route beyond full blast clearance was rejected");
      helper
          .getLevel()
          .setBlock(
              root.east().north(),
              Create_bioships.AMOEBA_LEG
                  .get()
                  .defaultBlockState()
                  .setValue(AmoebaLegBlock.FACING, Direction.NORTH),
              3);
      helper.assertTrue(
          !core.coreMotion().canAvoidBlast(core, creeper),
          "Escape claimed tissue the core cannot occupy without orphaning a leg");
      helper.succeed();
    } finally {
      creeper.discard();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "native_creeper_fuse_budget")
  public static void creeper_safety_reads_native_power_charge_and_remaining_fuse(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var creeper = EntityType.CREEPER.create(helper.getLevel());
    try {
      var core = core(ship);
      var world = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
      creeper.setPos(world.x - 3, world.y - .5, world.z);
      for (int x = 1; x <= 6; x++)
        helper
            .getLevel()
            .setBlock(core.getBlockPos().east(x), BioshipTissue.body().defaultBlockState(), 3);
      var config = new net.minecraft.nbt.CompoundTag();
      config.putByte("ExplosionRadius", (byte) 3);
      config.putShort("Fuse", (short) 3);
      config.putBoolean("ignited", true);
      creeper.readAdditionalSaveData(config);
      helper.assertTrue(
          BioshipCreeperHazard.estimate(creeper).damageReach() == 6
              && !core.coreMotion().canAvoidBlast(core, creeper),
          "Core escape ignored the native fuse budget");
      config.putShort("Fuse", (short) 40);
      creeper.readAdditionalSaveData(config);
      helper.assertTrue(
          core.coreMotion().canAvoidBlast(core, creeper),
          "A sufficiently long primed fuse did not allow a reachable escape");
      config.putByte("ExplosionRadius", (byte) 5);
      config.putBoolean("powered", true);
      creeper.readAdditionalSaveData(config);
      helper.assertTrue(
          BioshipCreeperHazard.estimate(creeper).damageReach() == 20
              && !core.coreMotion().canAvoidBlast(core, creeper),
          "Charged/custom explosion settings were replaced by a fixed small radius");
      helper.succeed();
    } finally {
      creeper.discard();
      cleanup(helper, ship);
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "creeper_interrupts_core_scout")
  public static void unsafe_core_scouting_does_not_hold_up_the_selected_ground_escape(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    helper
        .getLevel()
        .setBlock(core.getBlockPos().east(), BioshipTissue.body().defaultBlockState(), 3);
    var world = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var creeper = EntityType.CREEPER.create(helper.getLevel());
    creeper.setNoAi(true);
    creeper.setNoGravity(true);
    creeper.setPos(world.x - 3, world.y - .5, world.z);
    helper.getLevel().addFreshEntity(creeper);
    afterIndexed(
        helper,
        List.of(creeper),
        () -> {
          try {
            core.autonomy().load(core.autonomy().save());
            core.autonomy().assess(core);
            helper.assertTrue(
                core.autonomy().creeperPanic(), "Fixture did not select unsafe-creeper panic");
            var scout = core.coreMotion().save();
            scout.putLong("Target", core.getBlockPos().east().asLong());
            scout.putLong("Started", helper.getLevel().getGameTime());
            scout.putBoolean("Evasive", false);
            core.coreMotion().load(scout);
            helper.assertTrue(
                !core.coreMotion().tick(core)
                    && !core.coreMotion().active()
                    && core.brain().choose(core).action() == BioshipBrain.Action.FLEE,
                "Scouting continued to suppress crawling after escape became the selected need");
            helper.succeed();
          } finally {
            creeper.discard();
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 1400, batch = "mouth_transfer_phase_stress")
  public static void repeated_mouth_rearrangements_keep_inventory_and_owned_metadata(
      GameTestHelper helper) {
    repeatMouthTransfer(helper, 0);
  }

  private static void repeatMouthTransfer(GameTestHelper helper, int iteration) {
    if (iteration == 8) {
      helper.succeed();
      return;
    }
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = core.getBlockPos();
    level.setBlock(root.south(), Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    level.setBlock(root.north(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(root.south());
    mouth.setItem(0, new ItemStack(Items.STRUCTURE_VOID, 7));
    mouth.getPersistentData().putInt("ConservationFixture", iteration + 1);
    DigestiveSystem.connect(core, ship);
    BioshipIntegration.orientMouth(core);
    var trace = new ArrayList<String>();
    for (int at = 5; at <= 140; at += 5) {
      int age = at;
      helper.runAfterDelay(
          at,
          () -> {
            var current = core(ship);
            int held =
                BioshipLarynx.mouths(current).stream().mapToInt(m -> m.getItem(0).getCount()).sum();
            int carried = 0, marker = 0;
            var move = current.organMove();
            if (move != null) {
              var cargo = SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
              if (cargo instanceof ServerSubLevel part)
                for (var actor : BioshipIntegration.blockEntities(part))
                  if (actor instanceof FeedingMouthBlockEntity m) {
                    carried += m.getItem(0).getCount();
                    marker = m.getPersistentData().getInt("ConservationFixture");
                  }
            }
            trace.add(age + ":body=" + held + ",cargo=" + carried + ",marker=" + marker);
          });
    }
    helper.runAfterDelay(
        145,
        () -> {
          try {
            var current = core(ship);
            var moved = (FeedingMouthBlockEntity) level.getBlockEntity(current.mouthBlock());
            if (moved == null
                || moved.getItem(0).getCount() != 7
                || moved.getPersistentData().getInt("ConservationFixture") != iteration + 1)
              System.out.println(
                  "MOUTH_CONSERVATION_TRACE phase="
                      + iteration
                      + " trace="
                      + trace
                      + " snapshot="
                      + BioshipDiagnostics.snapshot(current));
            helper.assertTrue(
                moved != null
                    && moved.getItem(0).getCount() == 7
                    && moved.getPersistentData().getInt("ConservationFixture") == iteration + 1,
                "Mouth handoff lost data at phase "
                    + iteration
                    + " marker="
                    + (moved == null ? -1 : moved.getPersistentData().getInt("ConservationFixture"))
                    + " trace="
                    + trace.stream().skip(Math.max(0, trace.size() - 5)).toList());
          } finally {
            cleanup(helper, ship);
          }
          repeatMouthTransfer(helper, iteration + 1);
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void mouth_variants_pack_inventory_and_larynx_without_loose_duplicates(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = location();
    for (int variant = 0; variant < 4; variant++) {
      level.setBlock(pos, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
      var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(pos);
      mouth.setItem(0, new ItemStack(Items.PORKCHOP, 17));
      if ((variant & 1) != 0) {
        var voice = new net.minecraft.nbt.CompoundTag();
        voice.putString("Source", "biomancy:modular_larynx");
        mouth.absorbLarynx(voice);
      }
      mouth.swimmingGrasper(0, (variant & 2) != 0);
      var packed = packedRoundTrip(helper, pos);
      helper.assertTrue(packed instanceof FeedingMouthBlockEntity, "Mouth type lost");
      var restored = (FeedingMouthBlockEntity) packed;
      helper.assertTrue(
          restored.getItem(0).getCount() == 17
              && restored.voiced() == ((variant & 1) != 0)
              && restored.swimmingGrasper(0) == ((variant & 2) != 0),
          "Mouth variant lost inventory or upgrade");
      level.removeBlock(pos, false);
    }
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void stomach_packs_data_reserves_and_digestion_without_old_world_links(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = location();
    level.setBlock(pos, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    var stomach = (StomachBlockEntity) level.getBlockEntity(pos);
    var data = stomach.saveWithFullMetadata(level.registryAccess());
    data.putInt("NutrientReserve", 19);
    data.putInt("Progress", 7);
    data.putInt("Duration", 40);
    data.putInt("PaidTubeStock", 3);
    data.putInt("FreeTubeStock", 2);
    data.putBoolean("TubeBootstrapped", true);
    data.putLong("ConnectedMouth", pos.north().asLong());
    var reserves = new net.minecraft.nbt.CompoundTag();
    reserves.putInt("biomancy:flesh_bits", 31);
    data.put("TissueReserves", reserves);
    stomach.loadWithComponents(data, level.registryAccess());
    var restored = packedRoundTrip(helper, pos).saveWithFullMetadata(level.registryAccess());
    helper.assertTrue(
        restored.getInt("NutrientReserve") == 19
            && restored.getCompound("TissueReserves").getInt("biomancy:flesh_bits") == 31
            && restored.getInt("Progress") == 7
            && restored.getInt("PaidTubeStock") == 3
            && restored.getBoolean("TubeBootstrapped")
            && !restored.contains("ConnectedMouth"),
        "Stomach lost data-only resources/progress or retained stale topology");
    level.removeBlock(pos, false);
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void broken_core_has_no_survival_or_creative_portable_drop(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = location();
    level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    var core = (LivingCoreBlockEntity) level.getBlockEntity(pos);
    core.adaptation().creditRecovery(MaterialCost.FLESH, 37);
    var state = level.getBlockState(pos);
    var params =
        new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
            .withParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                Vec3.atCenterOf(pos))
            .withParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                ItemStack.EMPTY)
            .withParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY,
                core);
    helper.assertTrue(state.getDrops(params).isEmpty(), "Broken core yielded a reusable item");
    var user = player(helper, "CorePermanentLoss");
    user.setGameMode(GameType.CREATIVE);
    int before =
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2))
            .size();
    state.getBlock().playerWillDestroy(level, pos, state, user);
    BioshipContainerState.creativeBreak(level, pos, user);
    level.destroyBlock(pos, true, user);
    helper.runAfterDelay(
        2,
        () -> {
          helper.assertTrue(
              level
                      .getEntitiesOfClass(
                          net.minecraft.world.entity.item.ItemEntity.class,
                          new AABB(pos).inflate(2))
                      .size()
                  == before,
              "Creative or ordinary destruction recovered a packed living core");
          helper.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "closed_resting_jaw")
  public static void resting_mouth_closes_after_bites_and_vocalization(GameTestHelper helper) {
    var pos = location();
    var level = helper.getLevel();
    level.setBlock(pos, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    var mouth = (FeedingMouthBlockEntity) level.getBlockEntity(pos);
    helper.assertTrue(
        mouth.jawShift(0) == FeedingMouthBlockEntity.REST_JAW_SHIFT, "Idle mouth is open");
    mouth.animate(10, false);
    helper.runAfterDelay(
        5,
        () -> {
          helper.assertTrue(mouth.jawShift(0) < .01, "Bite did not open the closed resting jaw");
          mouth.animate(10, true);
          helper.runAfterDelay(
              5,
              () ->
                  helper.assertTrue(
                      mouth.jawShift(0) < 0,
                      "Vocalization did not open the mouth wider than a bite"));
          helper.runAfterDelay(
              11,
              () -> {
                helper.assertTrue(
                    mouth.jawShift(0) == FeedingMouthBlockEntity.REST_JAW_SHIFT,
                    "Finished vocalization left the resting mouth open");
                level.removeBlock(pos, false);
                helper.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "permanent_core_death")
  public static void replacement_core_does_not_revive_registered_dead_ship(GameTestHelper helper) {
    var ship = assemble(helper);
    helper.runAfterDelay(
        2,
        () -> {
          var original = core(ship);
          var pos = original.getBlockPos();
          helper.getLevel().setBlock(pos.east(), BioshipTissue.body().defaultBlockState(), 3);
          original.body().die(original, BioshipDeathCause.CORE_REMOVED);
          helper.assertTrue(
              BioshipDecay.dead(ship), "Fixture death was not registered on its owning ship");
          helper.getLevel().removeBlock(pos, false);
          helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
          helper.runAfterDelay(
              3,
              () ->
                  finish(
                      helper,
                      ship,
                      () -> {
                        var replacement = core(ship);
                        helper.assertTrue(
                            BioshipDecay.dead(ship)
                                && replacement.body().dead()
                                && replacement.brain().choose(replacement).action()
                                    == BioshipBrain.Action.DEAD,
                            "Replacement status: registry="
                                + BioshipDecay.dead(ship)
                                + " dead="
                                + replacement.body().dead()
                                + " action="
                                + replacement.brain().choose(replacement).action());
                      }));
        });
  }

  private static BlockEntity packedRoundTrip(GameTestHelper helper, BlockPos pos) {
    var level = helper.getLevel();
    var state = level.getBlockState(pos);
    var entity = level.getBlockEntity(pos);
    var drops =
        state.getDrops(
            new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(
                    net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                    Vec3.atCenterOf(pos))
                .withParameter(
                    net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
                    ItemStack.EMPTY)
                .withParameter(
                    net.minecraft.world.level.storage.loot.parameters.LootContextParams
                        .BLOCK_ENTITY,
                    entity));
    helper.assertTrue(
        drops.size() == 1 && drops.getFirst().getCount() == 1,
        "Container did not yield exactly one packed item");
    var box = new AABB(pos).inflate(2);
    int before =
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size();
    level.removeBlock(pos, false);
    helper.assertTrue(
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size()
            == before,
        "Removing a packed container also spilled loose duplicate contents");
    level.setBlock(pos, state, 3);
    boolean restoredNbt = BlockItem.updateCustomBlockEntityTag(level, null, pos, drops.getFirst());
    helper.assertTrue(
        !drops.getFirst().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
            || restoredNbt,
        "Packed block-entity NBT refused vanilla item placement");
    var restored = level.getBlockEntity(pos);
    restored.applyComponentsFromItemStack(drops.getFirst());
    return restored;
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void creative_break_keeps_packed_contents_once(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    var user = player(helper, "PackedCreative");
    user.setGameMode(GameType.CREATIVE);
    level.setBlock(pos, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    ((FeedingMouthBlockEntity) level.getBlockEntity(pos))
        .setItem(0, new ItemStack(Items.PORKCHOP, 23));
    var state = level.getBlockState(pos);
    state.getBlock().playerWillDestroy(level, pos, state, user);
    level.removeBlock(pos, false);
    var drops =
        level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2));
    helper.assertTrue(
        drops.size() == 1
            && drops.getFirst().getItem().is(Create_bioships.FEEDING_MOUTH.get().asItem()),
        "Creative break lost the packed item or also spilled contents");
    var item = drops.getFirst().getItem();
    level.setBlock(pos, state, 3);
    BlockItem.updateCustomBlockEntityTag(level, null, pos, item);
    level.getBlockEntity(pos).applyComponentsFromItemStack(item);
    helper.assertTrue(
        ((FeedingMouthBlockEntity) level.getBlockEntity(pos)).getItem(0).getCount() == 23,
        "Creative packed item lost its food");
    drops.forEach(Entity::discard);
    level.removeBlock(pos, false);
    level.setBlock(pos, state, 3);
    ((FeedingMouthBlockEntity) level.getBlockEntity(pos)).swimmingGrasper(0, true);
    state.getBlock().playerWillDestroy(level, pos, state, user);
    level.removeBlock(pos, false);
    var upgraded =
        level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2));
    helper.assertTrue(upgraded.size() == 1, "Empty paid aquatic mouth was discarded in creative");
    level.setBlock(pos, state, 3);
    BlockItem.updateCustomBlockEntityTag(level, null, pos, upgraded.getFirst().getItem());
    level.getBlockEntity(pos).applyComponentsFromItemStack(upgraded.getFirst().getItem());
    helper.assertTrue(
        ((FeedingMouthBlockEntity) level.getBlockEntity(pos)).swimmingGrasper(0),
        "Empty aquatic mouth item lost its paid grasper upgrade");
    upgraded.forEach(Entity::discard);
    level.removeBlock(pos, false);
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void native_storage_sac_still_keeps_inventory_in_item_form(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = location();
    level.setBlock(pos, block("biomancy:storage_sac").defaultBlockState(), 3);
    var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    helper.assertTrue(
        inventory != null
            && inventory.insertItem(0, new ItemStack(Items.BONE, 13), false).isEmpty(),
        "Native sac fixture refused inventory");
    packedRoundTrip(helper, pos);
    var restored = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    helper.assertTrue(
        restored != null && restored.getStackInSlot(0).getCount() == 13,
        "Native sac item roundtrip lost inventory");
    level.removeBlock(pos, false);
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 180)
  public static void organ_handoff_keeps_current_cargo_contents_instead_of_old_snapshot(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var source = core.getBlockPos().south();
    var target = core.getBlockPos().north();
    level.setBlock(source, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    ((FeedingMouthBlockEntity) level.getBlockEntity(source))
        .setItem(0, new ItemStack(Items.STRUCTURE_VOID, 7));
    helper.assertTrue(
        ComponentTransport.begin(core.amoebaComponents(), source, target, false),
        "Mouth transfer did not start");
    var move = core.organMove();
    var cargo = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
    var mouth =
        (FeedingMouthBlockEntity)
            cargo.getPlot().getLoadedChunks().stream()
                .flatMap(c -> c.getChunk().getBlockEntities().values().stream())
                .filter(e -> e instanceof FeedingMouthBlockEntity)
                .findFirst()
                .orElseThrow();
    helper.assertTrue(mouth.getItem(0).getCount() == 7, "Initial cargo handoff lost contents");
    mouth.removeItem(0, 4);
    mouth.getPersistentData().putInt("ChangedInTransit", 42);
    helper.runAfterDelay(
        60,
        () -> {
          try {
            var mouths = BioshipLarynx.mouths(core(ship));
            helper.assertTrue(
                mouths.size() == 1
                    && mouths.getFirst().getItem(0).getCount() == 3
                    && mouths.getFirst().getPersistentData().getInt("ChangedInTransit") == 42,
                "Commit lost current cargo state or resurrected the original seven items");
            helper.succeed();
          } finally {
            cleanup(helper, ship);
          }
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void waterline_contact_keeps_aquatic_history_while_mouth_can_breathe(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var water = BlockPos.containing(root.add(0, -.49, 0));
    level.setBlock(water, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4), 3);
    helper.assertTrue(BioshipAquatic.aboveFluid(core, root), "Fixture center must be above water");
    helper.assertTrue(BioshipAquatic.touchesWater(core), "Partly wet body was classified as dry");
    level.setBlock(water, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(!BioshipAquatic.touchesWater(core), "Dry body retained false water contact");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void underwater_open_mouth_does_not_request_pointless_reorientation(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var mouth = core.getBlockPos().north();
    level.setBlock(mouth, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    var outside =
        BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(mouth.north())));
    level.setBlock(outside, Blocks.WATER.defaultBlockState(), 3);
    helper.assertTrue(
        FeedingMouthBlock.openFace(core, mouth, Direction.NORTH),
        "Water treated as solid mouth obstruction");
    helper.assertTrue(
        !HatchlingFront.canArrange(core),
        "Clear underwater front mouth keeps monopolizing body adaptation");
    level.setBlock(outside, Blocks.STONE.defaultBlockState(), 3);
    helper.assertTrue(
        !FeedingMouthBlock.openFace(core, mouth, Direction.NORTH), "Solid obstruction was ignored");
    finish(helper, ship, () -> {});
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void ecosystem_full_refill_timer_is_persistent_capped_and_five_minutes(
      GameTestHelper helper) {
    var state = new EcosystemLab.State();
    helper.assertTrue(!LabPopulation.refillDue(state, 100), "Fresh lab refilled immediately");
    helper.assertTrue(
        state.nextRefill() == 6100 && !LabPopulation.refillDue(state, 6099),
        "Refill period is not five minutes");
    var saved = state.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
    helper.assertTrue(saved.getLong("NextRefill") == 6100, "Refill deadline was not saved");
    helper.assertTrue(
        LabPopulation.refillDue(state, 6100)
            && state.nextRefill() == 12100
            && !LabPopulation.refillDue(state, 6100),
        "Refill duplicated in one tick or lost its next deadline");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void fractional_aquatic_history_roundtrips_and_reads_legacy_scores(
      GameTestHelper helper) {
    var history = new BioshipAquatic();
    for (int i = 0; i < 73; i++) {
      history.legUsage().observe(i % 3 != 0);
      history.gillUsage().observe(i % 4 != 0);
    }
    var saved = history.save();
    var restored = new BioshipAquatic();
    restored.load(saved);
    helper.assertTrue(
        history.legUsage().preciseWater() == restored.legUsage().preciseWater()
            && history.gillUsage().preciseScore() == restored.gillUsage().preciseScore(),
        "Aquatic save lost fractional history");
    saved.remove("WaterUsePrecise");
    saved.remove("GillUsePrecise");
    restored.load(saved);
    helper.assertTrue(
        restored.legUsage().water() == saved.getInt("WaterUse")
            && restored.gillUsage().score() == saved.getInt("GillUse"),
        "Legacy exposure score was not migrated");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "health_components")
  public static void component_health_is_additive_armored_and_event_cached(GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var root = core.getBlockPos();
    level.setBlock(root.north(), BioshipTissue.body().defaultBlockState(), 3);
    level.setBlock(root.south(), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(root.east(), Create_bioships.FLESH.get().defaultBlockState(), 3);
    level.setBlock(root.west(), Create_bioships.ARMOR_II.get().defaultBlockState(), 3);
    level.setBlock(root.above(), Create_bioships.CARAPACE.get().defaultBlockState(), 3);
    core.body().observe(core);
    helper.assertTrue(
        core.body().capacity() == 152, "Health table did not count all five components plus core");
    level.removeBlock(root.west(), false);
    helper.runAfterDelay(
        2,
        () -> {
          helper.assertTrue(
              core.body().capacity() == 132, "Permanent armor loss did not reduce max health");
          cleanup(helper, ship);
          var quiet = assemble(helper);
          helper.runAfterDelay(
              2,
              () -> {
                long audits = core(quiet).body().healthAudits();
                helper.runAfterDelay(
                    25,
                    () ->
                        finish(
                            helper,
                            quiet,
                            () ->
                                helper.assertTrue(
                                    core(quiet).body().healthAudits() == audits,
                                    "Quiet body kept rechecking its health topology")));
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "health_stash")
  public static void stashing_redeploying_and_reload_preserve_health_and_wounds(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var mouth = core.getBlockPos().south();
    level.setBlock(mouth, Create_bioships.FEEDING_MOUTH.get().defaultBlockState(), 3);
    core.body().observe(core);
    core.body().combatDamage(core, level.damageSources().generic(), 20);
    int max = core.body().capacity();
    double health = core.body().health();
    helper.assertTrue(
        ComponentTransport.storeComponent(core.amoebaComponents(), mouth),
        "Mouth did not enter temporary storage");
    for (int at = 1; at <= 80; at++) {
      int age = at;
      helper.runAfterDelay(
          at,
          () -> {
            var live = core(ship);
            helper.assertTrue(
                live.body().capacity() == max && live.body().health() == health,
                "Temporary component handling changed health at "
                    + age
                    + ": "
                    + live.body().health()
                    + "/"
                    + live.body().capacity());
            if (age == 15)
              live.loadAdditional(
                  live.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
          });
    }
    helper.runAfterDelay(82, () -> finish(helper, ship, () -> {}));
  }

  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "creeper_meat_mutation")
  public static void creeper_meat_awards_only_after_actual_digestion_and_persists(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    var stomach = digestive(helper, core, false);
    var meat = new ItemStack(Create_bioships.MOB_MEATS.get("creeper").get(), 2);
    helper.assertTrue(
        stomach.enqueue(meat, false) && stomach.enqueue(meat, false),
        "Creeper meat was not digestible");
    helper.assertTrue(core.survival().blastPoints() == 0, "Merely storing meat awarded mutations");
    helper.runAfterDelay(
        360,
        () ->
            finish(
                helper,
                ship,
                () -> {
                  var live = core(ship);
                  helper.assertTrue(
                      live.survival().blastPoints() == 2,
                      "Mutation credit did not match two consumed pieces: "
                          + live.survival().blastPoints());
                  live.loadAdditional(
                      live.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                  helper.assertTrue(
                      live.survival().blastPoints() == 2, "Mutation points were lost on reload");
                }));
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "explosion_mutation")
  public static void surviving_real_blast_awards_once_and_reinforces_core_and_flesh(
      GameTestHelper helper) {
    var ship = assemble(helper);
    var core = core(ship);
    var level = helper.getLevel();
    afterAttackSurfaceIndexed(
        helper,
        core,
        proxy -> {
          var at = proxy.position().add(0, .4, 0);
          var calculator =
              new net.minecraft.world.level.ExplosionDamageCalculator() {
                @Override
                public boolean shouldBlockExplode(
                    net.minecraft.world.level.Explosion blast,
                    BlockGetter reader,
                    BlockPos pos,
                    net.minecraft.world.level.block.state.BlockState state,
                    float power) {
                  return false;
                }
              };
          level.explode(
              null, null, calculator, at.x, at.y, at.z, 1, false, Level.ExplosionInteraction.TNT);
          helper.runAfterDelay(
              2,
              () -> {
                helper.assertTrue(
                    !core.body().dead() && core.survival().blastPoints() == 3,
                    "A damaging explosion was not credited once after survival: "
                        + core.survival().blastPoints());
                var diagnostics = BioshipDiagnostics.snapshot(core);
                helper.assertTrue(
                    diagnostics.getInt("BlastAdaptationPoints") == 3
                        && diagnostics.getInt("MaxHealth") == core.body().capacity(),
                    "Developer snapshot omitted mutation points or current health capacity");
                var query = new net.minecraft.world.level.ExplosionDamageCalculator();
                var root = core.getBlockPos();
                var coreState = core.getBlockState();
                float reinforced =
                    query
                        .getBlockExplosionResistance(
                            null, level, root, coreState, level.getFluidState(root))
                        .orElseThrow();
                helper.assertTrue(
                    reinforced > coreState.getBlock().getExplosionResistance(),
                    "Core did not gain physical blast resistance");
                level.setBlock(root.north(), BioshipTissue.body().defaultBlockState(), 3);
                var flesh = level.getBlockState(root.north());
                helper.assertTrue(
                    query
                            .getBlockExplosionResistance(
                                null, level, root.north(), flesh, level.getFluidState(root.north()))
                            .orElseThrow()
                        > flesh.getBlock().getExplosionResistance(),
                    "Body tissue did not share resistance");
                var far = at.add(80, 0, 0);
                level.explode(
                    null,
                    null,
                    calculator,
                    far.x,
                    far.y,
                    far.z,
                    1,
                    false,
                    Level.ExplosionInteraction.TNT);
                helper.runAfterDelay(
                    2,
                    () -> {
                      helper.assertTrue(
                          core.survival().blastPoints() == 3,
                          "A harmless distant explosion awarded points");
                      var lethal =
                          new net.minecraft.world.level.ExplosionDamageCalculator() {
                            @Override
                            public boolean shouldBlockExplode(
                                net.minecraft.world.level.Explosion blast,
                                BlockGetter reader,
                                BlockPos pos,
                                net.minecraft.world.level.block.state.BlockState state,
                                float power) {
                              return false;
                            }

                            @Override
                            public float getEntityDamageAmount(
                                net.minecraft.world.level.Explosion blast, Entity entity) {
                              return 10000;
                            }
                          };
                      level.explode(
                          null,
                          null,
                          lethal,
                          at.x,
                          at.y,
                          at.z,
                          2,
                          false,
                          Level.ExplosionInteraction.TNT);
                      helper.runAfterDelay(
                          2,
                          () ->
                              finish(
                                  helper,
                                  ship,
                                  () -> {
                                    helper.assertTrue(
                                        core.body().dead() && core.survival().blastPoints() == 3,
                                        "Fatal blast granted survival progress");
                                    helper.assertTrue(
                                        core.body().deathCause() == BioshipDeathCause.EXPLOSION,
                                        "Fatal explosion lost its cause");
                                  }));
                    });
              });
        });
  }
}
