package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

/** Walk-through native Biomancy membrane, owner terminal and two individually named guests. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "create_bioships")
public final class MembraneFixture {
  private static final String MARK = "create_bioships:membrane_fixture";

  private MembraneFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(640, CoreTestState.get(level).layout().base().getY(), 0);
  }

  public static BlockPos core(ServerLevel level) {
    return base(level).above(4);
  }

  private static List<BlockPos> cells(ServerLevel level) {
    var result = new ArrayList<BlockPos>();
    var base = base(level);
    for (int x = -5; x <= 5; x++) for (int y = 0; y <= 5; y++) result.add(base.offset(x, y, 0));
    // A stable flesh footing outside the two-block-wide opening prevents the tall wall tipping.
    for (int x = -5; x <= 5; x++)
      if (x < 0 || x > 1) for (int z : new int[] {-1, 1}) result.add(base.offset(x, 0, z));
    result.add(base.offset(-2, 1, -1));
    return result;
  }

  public static ServerSubLevel body(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    if (container != null)
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body
            && !body.isRemoved()
            && body.getUserDataTag() != null
            && body.getUserDataTag().getBoolean(MARK)) return body;
    return null;
  }

  public static void reset(ServerLevel level) {
    var base = base(level);
    for (int x = (base.getX() - 10) >> 4; x <= (base.getX() + 10) >> 4; x++)
      for (int z = -1; z <= 0; z++) level.setChunkForced(x, z, true);
    var body = body(level);
    if (body != null)
      SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    for (var e :
        level.getEntitiesOfClass(
            Entity.class, new AABB(base).inflate(20), e -> e.getTags().contains(MARK))) e.discard();
    for (int x = -10; x <= 10; x++)
      for (int z = -9; z <= 7; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        if (Math.abs(x) <= 8 && Math.abs(z) <= 6)
          for (int y = 0; y <= 6; y++)
            level.setBlock(
                base.offset(x, y, z),
                (Math.abs(x) == 8 || Math.abs(z) == 6 || y == 6
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                2);
      }
    var flesh = com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH.get();
    for (var cell : cells(level)) level.setBlock(cell, flesh.defaultBlockState(), 3);
    level.setBlock(core(level), ComponentRegistry.CORE.get().defaultBlockState(), 3);
    for (int x = 0; x <= 1; x++)
      for (int y = 0; y <= 1; y++)
        level.setBlock(
            base.offset(x, y, 0),
            com.github.elenterius.biomancy.init.ModBlocks.BIOMETRIC_MEMBRANE
                .get()
                .defaultBlockState(),
            3);
    level.setBlock(
        base.offset(-2, 1, -1),
        ComponentRegistry.NERVE_PANEL
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        3);
    for (int x = 0; x <= 1; x++)
      for (int y = 0; y <= 1; y++)
        level.setBlock(
            base.offset(x, y, -6),
            DevelopmentTestBlocks.GLASS_DOOR
                .get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT),
            3);
    control(level, base.offset(-3, 0, -8), "membrane_test start");
    control(level, base.offset(3, 0, -8), "membrane_test reset");
    CoreTestState.get(level).membraneBuilt = true;
    CoreTestState.get(level).setDirty();
    explain(level, "Ready: START assembles the wall");
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    var be = (CommandBlockEntity) level.getBlockEntity(pos);
    be.getCommandBlock().setCommand(command);
    be.getCommandBlock().setTrackOutput(false);
    be.setChanged();
    level.setBlock(
        pos.above(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
  }

  public static int start(ServerLevel level) {
    if (body(level) != null) return 0;
    if (!CoreTestState.get(level).membraneBuilt) reset(level);
    var origin = core(level);
    var base = base(level);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            cells(level),
            new BoundingBox3i(base.offset(-5, 0, -1), base.offset(5, 5, 1)));
    if (body == null) return 0;
    CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    DevelopmentTestOwnership.assign(body);
    DevelopmentLifeSupport.pause(body);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var cow = EntityType.COW.create(level);
    var pig = EntityType.PIG.create(level);
    if (cow != null) guest(level, cow, base.offset(3, 0, -3), "Juniper");
    if (pig != null) guest(level, pig, base.offset(-3, 0, -3), "Truffle");
    var guests = body.getUserDataTag().copy();
    if (cow != null) guests.putUUID("MembraneCow", cow.getUUID());
    if (pig != null) guests.putUUID("MembranePig", pig.getUUID());
    body.setUserDataTag(guests);
    explain(level, "Running: first development player owns this subject");
    return 1;
  }

  private static void guest(ServerLevel level, Mob mob, BlockPos pos, String name) {
    mob.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    mob.setCustomName(Component.literal(name));
    mob.setCustomNameVisible(true);
    mob.setNoAi(false);
    configureGuest(mob);
    mob.setPersistenceRequired();
    mob.addTag(MARK);
    boolean added = level.addFreshEntity(mob);
    if (Boolean.getBoolean("core.membraneTrace"))
      System.out.println(
          "MEMBRANE_GUEST_SPAWN " + name + " added=" + added + " pos=" + mob.position());
  }

  public static java.util.List<Mob> guests(ServerLevel level) {
    var grid = body(level);
    var found = new java.util.ArrayList<Mob>();
    if (grid != null) {
      var tag = grid.getUserDataTag();
      for (var key : java.util.List.of("MembraneCow", "MembranePig"))
        if (tag.hasUUID(key)
            && level.getEntity(tag.getUUID(key)) instanceof Mob mob
            && !mob.isRemoved()) found.add(mob);
    }
    if (!found.isEmpty()) return found;
    return level.getEntitiesOfClass(
        Mob.class, new AABB(base(level)).inflate(40), e -> e.getTags().contains(MARK));
  }

  private static void configureGuest(Mob mob) {
    if (mob.goalSelector.getAvailableGoals().stream()
        .anyMatch(g -> g.getGoal() instanceof GuestCircuit)) return;
    mob.setNoAi(false);
    mob.goalSelector.removeAllGoals(g -> true);
    mob.targetSelector.removeAllGoals(g -> true);
    mob.goalSelector.addGoal(0, new GuestCircuit(mob));
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void maintainGuests(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    if (e.getServer().getTickCount() % 20 != 0) return;
    for (var level : e.getServer().getAllLevels())
      if (CoreTestState.get(level).membraneBuilt && body(level) != null)
        for (var mob : guests(level)) configureGuest(mob);
  }

  /** Fixture-only waypoints keep ordinary navigation active and deliberately retry the membrane. */
  private static final class GuestCircuit extends net.minecraft.world.entity.ai.goal.Goal {
    private final Mob mob;
    private static final String PHASE = "create_bioships:membrane_phase",
        ALLOWED = "create_bioships:membrane_allowed";

    GuestCircuit(Mob mob) {
      this.mob = mob;
      setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
      return mob.isAlive() && mob.level() instanceof ServerLevel level && body(level) != null;
    }

    @Override
    public boolean canContinueToUse() {
      return canUse();
    }

    @Override
    public void tick() {
      var level = (ServerLevel) mob.level();
      var grid = body(level);
      if (grid == null) return;
      var data = mob.getPersistentData();
      boolean allowed = SubLevelPermissions.get(grid).permits(mob.getUUID());
      int phase = data.getInt(PHASE);
      var origin = grid.getPlot().getCenterBlock().below(4);
      var local = grid.logicalPose().transformPositionInverse(mob.position());
      if (allowed != data.getBoolean(ALLOWED)) {
        phase = allowed ? 0 : (local.z > origin.getZ() + .5 ? 10 : 0);
        data.putBoolean(ALLOWED, allowed);
        data.putInt(PHASE, phase);
        mob.getNavigation().stop();
      }
      double lane = mob.getType() == EntityType.COW ? 1 : 0;
      double side = mob.getType() == EntityType.COW ? 7 : -7;
      Vec3 target =
          switch (phase) {
            case 10 -> Vec3.atBottomCenterOf(origin).add(side, 0, 3);
            case 11 -> Vec3.atBottomCenterOf(origin).add(side, 0, -3);
            case 0, 12 -> Vec3.atBottomCenterOf(origin).add(lane, 0, -2);
            default -> Vec3.atBottomCenterOf(origin).add(lane, 0, 3);
          };
      if (phase == 2) {
        mob.getNavigation().stop();
        return;
      }
      var world = grid.logicalPose().transformPosition(target);
      double distance = Math.hypot(mob.getX() - world.x, mob.getZ() - world.z);
      if (distance < .18) {
        phase = phase == 0 ? 1 : phase == 10 ? 11 : phase == 11 ? 12 : phase == 12 ? 13 : 2;
        data.putInt(PHASE, phase);
        mob.getNavigation().stop();
        return;
      }
      if (phase == 1 || phase == 13 || distance < 3) {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(world.x, world.y, world.z, 1.1);
      } else if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone())
        mob.getNavigation().moveTo(world.x, world.y, world.z, 1.1);
      if (Boolean.getBoolean("core.membraneTrace") && mob.tickCount % 80 == 0)
        System.out.println(
            "MEMBRANE_CIRCUIT "
                + mob.getName().getString()
                + " phase="
                + phase
                + " allowed="
                + allowed
                + " pos="
                + mob.position()
                + " target="
                + world
                + " up="
                + grid.logicalPose().orientation());
      mob.getLookControl().setLookAt(world.x, world.y + mob.getEyeHeight(), world.z);
    }
  }

  public static String status(ServerLevel level) {
    var body = body(level);
    if (body == null) return "Membrane fixture: ready, unassembled";
    var access = SubLevelPermissions.get(body);
    return "Membrane fixture: owner "
        + access.owner()
        + ", allowed guests "
        + access.allowed().size();
  }

  private static void explain(ServerLevel level, String result) {
    var base = base(level);
    for (var old :
        level.getEntitiesOfClass(
            Display.TextDisplay.class, new AABB(base).inflate(20), e -> e.getTags().contains(MARK)))
      old.discard();
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) return;
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "BIOMETRIC MEMBRANE REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Enter the glass doors, then use the Nerve Panel.\n"
                    + "Owner passes automatically; add/remove named guests.\n"
                    + "Guests cross when allowed; revoking sends them around to retry.\n"
                    + "Players tab: add players or confirm an ownership transfer.\n"
                    + result),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 340);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -7.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
