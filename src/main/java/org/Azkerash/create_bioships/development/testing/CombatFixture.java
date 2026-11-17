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
@net.neoforged.fml.common.EventBusSubscriber(modid = Create_bioships.MODID)
public final class CombatFixture {
  private static final String MARK = "create_bioships.combat_fixture";
  public static final int RADIUS = 12, HEIGHT = 16, TRIAL_TICKS = 200;

  private static final Set<ServerLevel> UI_READY = Collections.newSetFromMap(new WeakHashMap<>());

  private CombatFixture() {}

  private static BlockPos base(ServerLevel level) {
    return CoreTestState.get(level).combatOrigin;
  }

  private static AABB bounds(ServerLevel level) {
    return new AABB(
        Vec3.atLowerCornerOf(base(level).offset(-14, 0, -19)),
        Vec3.atLowerCornerOf(base(level).offset(15, 18, 15)));
  }

  public static void relocate(ServerLevel level, BlockPos destination) {
    clearTrial(level);
    var old = base(level);
    for (int x = -14; x <= 14; x++)
      for (int z = -19; z <= 14; z++)
        for (int y = 0; y <= HEIGHT; y++) {
          var pos = old.offset(x, y, z);
          var state = level.getBlockState(pos);
          if (DevelopmentTestBlocks.protectedBlock(state) || state.is(Blocks.COMMAND_BLOCK))
            level.removeBlock(pos, false);
        }
    CoreTestState.get(level).combatOrigin = destination;
    reset(level);
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
                    || e instanceof net.minecraft.world.entity.projectile.Projectile
                    || e instanceof net.minecraft.world.entity.AreaEffectCloud)) e.discard();
    // Summons and enemies from a preceding trial are confined to this reserved test enclosure.
    for (var e : level.getEntitiesOfClass(Mob.class, bounds(level), e -> e instanceof Enemy))
      e.discard();
    var removed = new ArrayList<Entity>();
    for (var entity : level.getAllEntities())
      if (entity != null && entity.getTags().contains(MARK)) removed.add(entity);
    removed.forEach(Entity::discard);
    var state = CoreTestState.get(level);
    state.combatBody = null;
    state.combatEnemy = null;
    state.combatAcquired = false;
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
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
  }

  private static void explanation(ServerLevel level, String progress) {
    UI_READY.add(level);
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
                    + "Death advances; timeout records failure.\n"
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
    failureDisplay(level);
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
        if (type.create(level) instanceof Mob mob
            && org.Azkerash.create_bioships.combat.HostileCombatIntegration.isHostile(mob))
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
    state.combatActiveTrialSeconds = state.combatTrialSeconds;
    state.combatDeadline = level.getGameTime() + state.combatActiveTrialSeconds * 20L;
    var type =
        BuiltInRegistries.ENTITY_TYPE.get(
            ResourceLocation.parse(state.combatEnemies.get(state.combatIndex)));
    var enemy = type.create(level);
    if (enemy == null) {
      finishTrial(level, "SKIP (spawn unavailable)");
      return;
    }
    enemy.setPos(
        Vec3.atLowerCornerOf(base(level))
            .add(
                enemy instanceof net.minecraft.world.entity.monster.Slime
                    ? 3.6
                    : enemy instanceof net.minecraft.world.entity.monster.Giant ? 5 : 7,
                enemy instanceof net.minecraft.world.entity.monster.Vex ? 2 : 0,
                .5));
    enemy.addTag(MARK);
    state.combatEnemy = enemy.getUUID();
    state.combatAcquired = false;
    if (enemy instanceof Mob mob) {
      mob.setPersistenceRequired();
      mob.finalizeSpawn(
          level, level.getCurrentDifficultyAt(base(level)), MobSpawnType.COMMAND, null);
      if (mob instanceof net.minecraft.world.entity.monster.Slime slime) slime.setSize(2, true);
      if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin)
        piglin.setImmuneToZombification(true);
      if (mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hoglin)
        hoglin.setImmuneToZombification(true);
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
    state.combatResults.add(
        state.combatEnemies.get(state.combatIndex)
            + ": "
            + result
            + (state.combatAcquired ? "; target acquired" : "; no target acquired"));
    state.combatIndex++;
    state.setDirty();
    next(level);
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (state.combatBuilt
        && !UI_READY.contains(level)
        && level.isPositionEntityTicking(base(level))
        && level.isPositionEntityTicking(base(level).north(16))) explanation(level, status(level));
    if (!state.combatRunning) return;
    var enemy = state.combatEnemy == null ? null : level.getEntity(state.combatEnemy);
    if (enemy instanceof Mob mob
        && mob.getTarget() instanceof org.Azkerash.create_bioships.combat.SubLevelCombatTarget
        && !state.combatAcquired) {
      state.combatAcquired = true;
      state.setDirty();
    }
    var body =
        state.combatBody == null
            ? null
            : SubLevelContainer.getContainer(level).getSubLevel(state.combatBody);
    if (body instanceof ServerSubLevel server && SubLevelHealth.status(server).dead())
      finishTrial(level, "PASS (" + SubLevelHealth.status(server).causeOfDeath() + ")");
    else if (body == null || body.isRemoved()) finishTrial(level, "FAIL (subject lost)");
    else if (level.getGameTime() >= state.combatDeadline)
      finishTrial(level, "FAIL (no lethal attack within " + state.combatActiveTrialSeconds + "s)");
  }

  public static String status(ServerLevel level) {
    var s = CoreTestState.get(level);
    return "Combat test: "
        + (s.combatRunning
            ? "running " + (s.combatIndex + 1) + " / " + s.combatEnemies.size()
            : "ready")
        + ", saved results: "
        + s.combatResults.size()
        + ", trial limit: "
        + s.combatTrialSeconds
        + "s";
  }

  public static List<String> results(ServerLevel level) {
    return List.copyOf(CoreTestState.get(level).combatResults);
  }

  private static final String FAILURE_LABEL = MARK + ".failure_label",
      FAILURE_TOGGLE = MARK + ".failure_toggle",
      FAILURE_NEXT = MARK + ".failure_next",
      TIME_DECREASE = MARK + ".time_decrease",
      TIME_INCREASE = MARK + ".time_increase";

  public static void toggleFailures(ServerLevel level, boolean nextPage) {
    var state = CoreTestState.get(level);
    if (nextPage) state.combatFailurePage++;
    else state.combatFailuresExpanded = !state.combatFailuresExpanded;
    state.setDirty();
    failureDisplay(level);
  }

  public static void adjustTrialDuration(ServerLevel level, int seconds) {
    var state = CoreTestState.get(level);
    state.combatTrialSeconds = Math.clamp(state.combatTrialSeconds + seconds, 5, 120);
    state.setDirty();
    failureDisplay(level);
  }

  private static void failureDisplay(ServerLevel level) {
    var removed = new ArrayList<Entity>();
    for (var entity : level.getAllEntities())
      if (entity != null
          && (entity.getTags().contains(FAILURE_LABEL)
              || entity.getTags().contains(FAILURE_TOGGLE)
              || entity.getTags().contains(FAILURE_NEXT)
              || entity.getTags().contains(TIME_DECREASE)
              || entity.getTags().contains(TIME_INCREASE))) removed.add(entity);
    removed.forEach(Entity::discard);
    var state = CoreTestState.get(level);
    var failures = state.combatResults.stream().filter(r -> r.contains(": FAIL")).toList();
    int pages = Math.max(1, (failures.size() + 3) / 4);
    state.combatFailurePage = Math.floorMod(state.combatFailurePage, pages);
    var header = Vec3.atLowerCornerOf(base(level)).add(8.5, 1.62, -15.5);
    failureText(
        level,
        header,
        "Failures ("
            + failures.size()
            + ") "
            + (state.combatFailuresExpanded ? "▲" : "▼")
            + " · right-click");
    failureButton(level, header.add(0, -.17, 0), FAILURE_TOGGLE, 4);
    var timerRow = header.add(0, .9, 0);
    failureText(
        level, timerRow, "Trial limit: " + state.combatTrialSeconds + "s\nApplies to next test");
    failureText(level, timerRow.add(-3, 0, 0), "[−5s]");
    failureText(level, timerRow.add(3, 0, 0), "[+5s]");
    failureButton(level, timerRow.add(-3, -.17, 0), TIME_DECREASE, 1.2f);
    failureButton(level, timerRow.add(3, -.17, 0), TIME_INCREASE, 1.2f);
    if (state.combatFailuresExpanded) {
      var lines = new java.util.ArrayList<String>();
      for (int i = state.combatFailurePage * 4;
          i < Math.min(failures.size(), state.combatFailurePage * 4 + 4);
          i++) {
        String id = failures.get(i).substring(0, failures.get(i).indexOf(": FAIL"));
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        lines.add(type.getDescription().getString());
      }
      if (lines.isEmpty()) lines.add("No failures recorded");
      lines.add("Page " + (state.combatFailurePage + 1) + " / " + pages);
      failureText(level, header.add(0, -1.45, 0), String.join("\n", lines));
      if (pages > 1) {
        failureText(level, header.add(4, 0, 0), "Next ›");
        failureButton(level, header.add(4, -.17, 0), FAILURE_NEXT, 1.5f);
      }
    }
  }

  private static void failureText(ServerLevel level, Vec3 pos, String text) {
    var entity = EntityType.TEXT_DISPLAY.create(level);
    if (entity == null) return;
    var tag = new CompoundTag();
    tag.putString(
        "text", Component.Serializer.toJson(Component.literal(text), level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putString("alignment", "center");
    tag.putInt("line_width", 250);
    tag.putInt("background", 0x80000000);
    tag.putBoolean("shadow", true);
    entity.load(tag);
    entity.setPos(pos);
    entity.addTag(FAILURE_LABEL);
    level.addFreshEntity(entity);
  }

  private static void failureButton(ServerLevel level, Vec3 pos, String marker, float width) {
    var entity = EntityType.INTERACTION.create(level);
    if (entity == null) return;
    var tag = new CompoundTag();
    tag.putFloat("width", width);
    tag.putFloat("height", .4f);
    tag.putBoolean("response", true);
    entity.load(tag);
    entity.setPos(pos);
    entity.addTag(marker);
    level.addFreshEntity(entity);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void failureClick(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    boolean next = event.getTarget().getTags().contains(FAILURE_NEXT);
    boolean decrease = event.getTarget().getTags().contains(TIME_DECREASE);
    boolean increase = event.getTarget().getTags().contains(TIME_INCREASE);
    if (!next && !decrease && !increase && !event.getTarget().getTags().contains(FAILURE_TOGGLE))
      return;
    if (!CoreTestState.get(level).combatBuilt) return;
    if (decrease || increase) adjustTrialDuration(level, increase ? 5 : -5);
    else toggleFailures(level, next);
    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    event.setCanceled(true);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void trackSummons(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var state = CoreTestState.get(level);
    if (state.combatRunning
        && event.getEntity() instanceof Enemy
        && bounds(level).inflate(32).contains(event.getEntity().position()))
      event.getEntity().addTag(MARK);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void containDragon(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
    if (event.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
        && dragon.level() instanceof ServerLevel level
        && dragon.getTags().contains(MARK)) contain(level, dragon);
  }

  public static void contain(
      ServerLevel level, net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
    var b = base(level);
    var box = dragon.getBoundingBox();
    for (var part : dragon.getSubEntities()) box = box.minmax(part.getBoundingBox());
    double x = containAxis(box.minX, box.maxX, b.getX() - 12 + .05, b.getX() + 13 - .05);
    double y = containAxis(box.minY, box.maxY, b.getY() + .05, b.getY() + HEIGHT - .05);
    double z = containAxis(box.minZ, box.maxZ, b.getZ() - 12 + .05, b.getZ() + 13 - .05);
    if (x == 0 && y == 0 && z == 0) return;
    var offset = new Vec3(x, y, z);
    dragon.setPos(dragon.position().add(offset));
    dragon.xo += x;
    dragon.yo += y;
    dragon.zo += z;
    for (var part : dragon.getSubEntities()) {
      part.setPos(part.position().add(offset));
      part.xo += x;
      part.yo += y;
      part.zo += z;
    }
    var motion = dragon.getDeltaMovement();
    dragon.setDeltaMovement(x == 0 ? motion.x : 0, y == 0 ? motion.y : 0, z == 0 ? motion.z : 0);
    dragon.hasImpulse = true;
  }

  private static double containAxis(double min, double max, double low, double high) {
    if (max - min > high - low) return (low + high - min - max) / 2;
    return min < low ? low - min : max > high ? high - max : 0;
  }
}
