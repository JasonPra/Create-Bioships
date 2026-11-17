package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.*;

/**
 * Visible sequential hostile test, with native assembly and persistent results; no production AI.
 */
public final class CombatFixture {
  private static final String MARK = "create_bioships.combat_fixture";
  public static final int RADIUS = 12, HEIGHT = 16, TRIAL_TICKS = 1200;

  private CombatFixture() {}

  private static BlockPos base(ServerLevel level) {
    return CoreTestState.get(level).combatOrigin;
  }

  private static AABB bounds(ServerLevel level) {
    return new AABB(
        Vec3.atLowerCornerOf(base(level).offset(-14, 0, -19)),
        Vec3.atLowerCornerOf(base(level).offset(15, 18, 15)));
  }

  public static List<BlockPos> subject(BlockPos base) {
    var cells = new ArrayList<BlockPos>();
    for (int y = 0; y < 3; y++)
      for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) cells.add(base.offset(x, y, z));
    for (int y = 3; y < 9; y++)
      for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(base.offset(x, y, z));
    cells.add(base.above(9));
    return cells;
  }

  private static void clearTrial(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel server
          && server.getUserDataTag() != null
          && server.getUserDataTag().getBoolean(MARK))
        container.removeSubLevel(server, SubLevelRemovalReason.REMOVED);
    for (var e :
        level.getEntitiesOfClass(
            Entity.class,
            bounds(level),
            e ->
                e.getTags().contains(MARK)
                    || e instanceof net.minecraft.world.entity.item.ItemEntity
                    || e instanceof net.minecraft.world.entity.projectile.Projectile)) e.discard();
    // Summons and enemies from a preceding trial are confined to this reserved test enclosure.
    for (var e : level.getEntitiesOfClass(Mob.class, bounds(level), e -> e instanceof Enemy))
      e.discard();
    var state = CoreTestState.get(level);
    state.combatBody = null;
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 12; z++)
        for (int y = 0; y < 16; y++)
          level.setBlock(base(level).offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
  }

  private static void sleepingSubject(ServerLevel level) {
    var flesh =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("biomancy", "packed_flesh"));
    for (var pos : subject(base(level))) level.setBlock(pos, flesh.defaultBlockState(), 3);
    level.setBlock(base(level).above(9), Create_bioships.CORE.get().defaultBlockState(), 3);
  }

  public static void reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    clearTrial(level);
    var b = base(level);
    for (int x = (b.getX() - 14) >> 4; x <= (b.getX() + 14) >> 4; x++)
      for (int z = (b.getZ() - 19) >> 4; z <= (b.getZ() + 14) >> 4; z++)
        level.setChunkForced(x, z, true);
    for (int x = -14; x <= 14; x++)
      for (int z = -19; z <= 14; z++)
        level.setBlock(b.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
    for (int x = -13; x <= 13; x++)
      for (int z = -13; z <= 13; z++)
        for (int y = 0; y <= HEIGHT; y++) {
          int edge = Math.max(Math.abs(x), Math.abs(z));
          if (edge == 13 || y == HEIGHT)
            level.setBlock(
                b.offset(x, y, z), DevelopmentTestBlocks.GLASS.get().defaultBlockState(), 2);
          else if (edge == 12 || y == HEIGHT - 1)
            level.setBlock(
                b.offset(x, y, z), DevelopmentTestBlocks.GLASS.get().defaultBlockState(), 2);
        }
    sleepingSubject(level);
    control(level, b.offset(-3, 0, -16), "combat_test start");
    control(level, b.offset(3, 0, -16), "combat_test reset");
    state.combatRunning = false;
    state.combatBuilt = true;
    state.setDirty();
    explanation(level, "Ready · subject is unassembled");
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    var block = (CommandBlockEntity) level.getBlockEntity(pos);
    block.getCommandBlock().setCommand(command);
    block.getCommandBlock().setTrackOutput(false);
    block.setChanged();
    level.setBlock(
        pos.above(),
        Blocks.STONE_BUTTON
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
  }

  private static void explanation(ServerLevel level, String progress) {
    for (var old :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.Display.TextDisplay.class,
            bounds(level),
            e -> e.getTags().contains(MARK))) old.discard();
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) return;
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "COMBAT REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Assembles a 1-HP flesh pillar, then tests\n"
                    + "every registered hostile type one at a time.\n"
                    + "Death advances; 60s timeout records failure.\n"
                    + "Automatically restores the sleeping fixture.\n"
                    + progress
                    + "\n/combat_test status · /combat_test results"),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putString("alignment", "center");
    tag.putInt("line_width", 240);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base(level)).add(.5, 1.62, -15.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }

  public static int start(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.combatBuilt || state.combatRunning) return 0;
    state.combatEnemies.clear();
    state.combatResults.clear();
    state.combatIndex = 0;
    for (var type : BuiltInRegistries.ENTITY_TYPE) {
      if (type.getCategory() != MobCategory.MONSTER) continue;
      try {
        if (type.create(level) instanceof Enemy)
          state.combatEnemies.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
      } catch (RuntimeException error) {
        state.combatResults.add(
            BuiltInRegistries.ENTITY_TYPE.getKey(type)
                + ": SKIP (cannot construct: "
                + error.getClass().getSimpleName()
                + ")");
      }
    }
    Collections.sort(state.combatEnemies);
    state.combatRunning = true;
    state.setDirty();
    next(level);
    return 1;
  }

  private static void next(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (state.combatIndex >= state.combatEnemies.size()) {
      reset(level);
      explanation(level, "Finished · " + state.combatResults.size() + " results retained");
      return;
    }
    clearTrial(level);
    sleepingSubject(level);
    var cells = subject(base(level));
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            base(level).above(9),
            cells,
            new BoundingBox3i(base(level).offset(-2, 0, -2), base(level).offset(2, 9, 2)));
    if (body == null) {
      finishTrial(level, "FAIL (assembly)");
      return;
    }
    CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    state.combatBody = body.getUniqueId();
    var health = SubLevelHealth.status(body);
    SubLevelHealth.damage(body, health.current() - 1, "test_setup");
    state.combatDeadline = level.getGameTime() + TRIAL_TICKS;
    var type =
        BuiltInRegistries.ENTITY_TYPE.get(
            ResourceLocation.parse(state.combatEnemies.get(state.combatIndex)));
    var enemy = type.create(level);
    if (enemy == null) {
      finishTrial(level, "SKIP (spawn unavailable)");
      return;
    }
    enemy.setPos(Vec3.atLowerCornerOf(base(level)).add(7, 1, .5));
    enemy.addTag(MARK);
    if (enemy instanceof Mob mob) {
      mob.setPersistenceRequired();
      mob.finalizeSpawn(
          level, level.getCurrentDifficultyAt(base(level)), MobSpawnType.COMMAND, null);
      mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
      mob.setDropChance(EquipmentSlot.HEAD, 0);
    }
    level.addFreshEntity(enemy);
    state.setDirty();
    explanation(
        level,
        (state.combatIndex + 1)
            + " / "
            + state.combatEnemies.size()
            + " · "
            + state.combatEnemies.get(state.combatIndex));
  }

  private static void finishTrial(ServerLevel level, String result) {
    var state = CoreTestState.get(level);
    state.combatResults.add(state.combatEnemies.get(state.combatIndex) + ": " + result);
    state.combatIndex++;
    state.setDirty();
    next(level);
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.combatRunning) return;
    var body =
        state.combatBody == null
            ? null
            : SubLevelContainer.getContainer(level).getSubLevel(state.combatBody);
    if (body instanceof ServerSubLevel server && SubLevelHealth.status(server).dead())
      finishTrial(level, "PASS (" + SubLevelHealth.status(server).causeOfDeath() + ")");
    else if (body == null || body.isRemoved()) finishTrial(level, "FAIL (subject lost)");
    else if (level.getGameTime() >= state.combatDeadline)
      finishTrial(level, "FAIL (no lethal attack within 60s)");
  }

  public static String status(ServerLevel level) {
    var s = CoreTestState.get(level);
    return "Combat test: "
        + (s.combatRunning
            ? "running " + (s.combatIndex + 1) + " / " + s.combatEnemies.size()
            : "ready")
        + ", saved results: "
        + s.combatResults.size();
  }

  public static List<String> results(ServerLevel level) {
    return List.copyOf(CoreTestState.get(level).combatResults);
  }
}
