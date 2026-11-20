package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.combat.CombatAdapter;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** Visible, repeatable native-dispenser checks. Each case gets a fresh damaged subject. */
public final class PotionFixture {
  private static final String ZOMBIE_MARK = "create_bioships.potion_zombie";
  private static final String MARK = "create_bioships.potion_fixture";
  public static final List<Holder<MobEffect>> EFFECTS =
      List.of(
          MobEffects.POISON,
          MobEffects.WITHER,
          MobEffects.REGENERATION,
          MobEffects.HEAL,
          MobEffects.HARM,
          MobEffects.DAMAGE_RESISTANCE);

  private PotionFixture() {}

  private static BlockPos base(ServerLevel level) {
    return CoreTestState.get(level).getPotionOrigin();
  }

  public static BlockPos core(ServerLevel level) {
    return base(level).above(2);
  }

  public static BlockPos dispenser(ServerLevel level) {
    return core(level).north(4);
  }

  private static List<BlockPos> subject(ServerLevel level) {
    var b = base(level);
    return List.of(b, b.above(), b.east(), core(level), b.above().north());
  }

  public static ItemStack potion(Holder<MobEffect> effect) {
    var stack = new ItemStack(Items.SPLASH_POTION);
    stack.set(
        DataComponents.POTION_CONTENTS,
        new PotionContents(
            Optional.empty(), Optional.empty(), List.of(new MobEffectInstance(effect, 400, 0))));
    return stack;
  }

  private static void clear(ServerLevel level) {
    var state = CoreTestState.get(level);
    var container = SubLevelContainer.getContainer(level);
    if (state.getPotionBody() != null) {
      var body = container.getSubLevel(state.getPotionBody());
      if (body != null) {
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
      }
    }
    state.setPotionBody(null);
    removeZombie(level);
    var remove = new ArrayList<Entity>();
    var arena = new AABB(base(level)).inflate(8);
    for (var entity : level.getAllEntities()) {
      if (entity.getTags().contains(MARK)
          || entity instanceof net.minecraft.world.entity.projectile.ThrownPotion
              && arena.contains(entity.position())) {
        remove.add(entity);
      }
    }
    remove.forEach(Entity::discard);
  }

  public static void reset(ServerLevel level) {
    clear(level);
    var state = CoreTestState.get(level);
    var b = base(level);
    for (int x = (b.getX() - 8) >> 4; x <= (b.getX() + 8) >> 4; x++) {
      for (int z = (b.getZ() - 10) >> 4; z <= (b.getZ() + 8) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -7; x <= 7; x++) {
      for (int z = -9; z <= 7; z++) {
        level.setBlock(
            b.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
          for (int y = 0; y <= 7; y++) {
            level.setBlock(
                b.offset(x, y, z),
                (Math.abs(x) == 6 || Math.abs(z) == 6 || y == 7
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                Block.UPDATE_CLIENTS);
          }
        }
      }
    }
    // Two adjacent, oppositely hinged doors on the front wall.
    for (int x = 1; x <= 2; x++) {
      var door =
          DevelopmentTestBlocks.GLASS_DOOR
              .get()
              .defaultBlockState()
              .setValue(DoorBlock.FACING, Direction.NORTH)
              .setValue(DoorBlock.HINGE, x == 1 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
      level.setBlock(
          b.offset(x, 0, -6),
          door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
          Block.UPDATE_CLIENTS);
      level.setBlock(
          b.offset(x, 1, -6),
          door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
          Block.UPDATE_CLIENTS);
    }
    sleepingSubject(level);
    level.setBlock(
        dispenser(level),
        Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH),
        Block.UPDATE_ALL);
    button(level, b.offset(-3, 0, -8), "potion_test start");
    button(level, b.offset(3, 0, -8), "potion_test reset");
    state.setPotionBuilt(true);
    state.setPotionRunning(false);
    state.setDirty();
    explain(level, "Ready");
  }

  private static void sleepingSubject(ServerLevel level) {
    var flesh =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("biomancy", "packed_flesh"));
    for (var cell : subject(level)) {
      level.setBlock(cell, flesh.defaultBlockState(), Block.UPDATE_ALL);
    }
    level.setBlock(core(level), ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        base(level).above().north(),
        ComponentRegistry.VITAL_READOUT
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        Block.UPDATE_ALL);
  }

  public static int start(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.isPotionBuilt() || state.isPotionRunning()) {
      return 0;
    }
    state.getPotionResults().clear();
    state.setPotionIndex(0);
    state.setPotionRunning(true);
    next(level);
    return 1;
  }

  private static void next(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (state.getPotionIndex() >= EFFECTS.size()) {
      reset(level);
      explain(level, "Finished; results retained");
      return;
    }
    clear(level);
    sleepingSubject(level);
    level.setBlock(dispenser(level).north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    var cells = subject(level);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            core(level),
            cells,
            new BoundingBox3i(base(level).north(), base(level).offset(1, 2, 0)));
    if (body == null) {
      state.getPotionResults().add("FAIL: assembly");
      state.setPotionRunning(false);
      state.setDirty();
      return;
    }
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    var root = body.getUserDataTag().copy();
    root.putBoolean(MARK, true);
    body.setUserDataTag(root);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    DevelopmentLifeSupport.pause(body);
    DevelopmentTestOwnership.assign(body);
    state.setPotionBody(body.getUniqueId());
    var health = SubLevelHealth.status(body);
    SubLevelHealth.damage(body, health.current() - health.maximum() * .6, "test_setup");
    state.setPotionBefore(SubLevelHealth.status(body).current());
    state.setPotionDeadline(level.getGameTime() + 200);
    state.setPotionResistanceHit(false);
    state.setPotionResistancePhase(0);
    state.setPotionProtectedDamage(0);
    state.setPotionBaselineDamage(0);
    var inventory = (DispenserBlockEntity) level.getBlockEntity(dispenser(level));
    inventory.clearContent();
    inventory.setItem(0, potion(EFFECTS.get(state.getPotionIndex())));
    inventory.setChanged();
    if (EFFECTS.get(state.getPotionIndex()) != MobEffects.DAMAGE_RESISTANCE) {
      firePotion(level);
    }
    state.setDirty();
    explain(level, "Running " + (state.getPotionIndex() + 1) + " / " + EFFECTS.size());
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.isPotionRunning()) {
      return;
    }
    var entry = SubLevelContainer.getContainer(level).getSubLevel(state.getPotionBody());
    if (!(entry instanceof ServerSubLevel body) || body.isRemoved()) {
      state.getPotionResults().add("FAIL: subject lost");
      state.setPotionRunning(false);
      state.setDirty();
      return;
    }
    var target = CombatAdapter.target(body);
    var effect = EFFECTS.get(state.getPotionIndex());
    if (effect == MobEffects.DAMAGE_RESISTANCE) {
      resistanceTick(level, body);
      return;
    }
    if (level.getGameTime() < state.getPotionDeadline()) {
      return;
    }
    double after = SubLevelHealth.status(body).current(), delta = after - state.getPotionBefore();
    boolean passed =
        effect == MobEffects.HEAL || effect == MobEffects.REGENERATION ? delta > 0 : delta < 0;
    state
        .getPotionResults()
        .add(
            BuiltInRegistries.MOB_EFFECT.getKey(effect.value())
                + ": "
                + (passed ? "PASS" : "FAIL")
                + String.format(
                    java.util.Locale.ROOT, " (HP %.1f → %.1f)", state.getPotionBefore(), after));
    state.incrementPotionIndex();
    next(level);
  }

  private static void firePotion(ServerLevel level) {
    level.setBlock(
        dispenser(level).north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    level.neighborChanged(dispenser(level), Blocks.REDSTONE_BLOCK, dispenser(level).north());
  }

  private static void removeZombie(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (state.getPotionZombie() != null) {
      var zombie = level.getEntity(state.getPotionZombie());
      if (zombie != null) {
        zombie.discard();
      }
    }
    for (var entity : level.getAllEntities()) {
      if (entity.getTags().contains(ZOMBIE_MARK)) {
        entity.discard();
      }
    }
    state.setPotionZombie(null);
  }

  private static boolean spawnZombie(ServerLevel level, ServerSubLevel body) {
    var state = CoreTestState.get(level);
    var zombie = EntityType.ZOMBIE.create(level);
    if (zombie == null) {
      return false;
    }
    zombie.setPersistenceRequired();
    zombie.setBaby(false);
    zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    zombie.setDropChance(EquipmentSlot.HEAD, 0);
    zombie
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
        .setBaseValue(3);
    zombie.setPos(Vec3.atBottomCenterOf(base(level).east(3)));
    zombie.addTag(ZOMBIE_MARK);
    level.addFreshEntity(zombie);
    // Compare the same body adapter in both trials, never a critical-hit weak point or visitor.
    zombie.targetSelector.removeAllGoals(goal -> true);
    zombie.setTarget(CombatAdapter.target(body));
    state.setPotionZombie(zombie.getUUID());
    state.setPotionHitBefore(SubLevelHealth.status(body).current());
    state.setPotionDeadline(level.getGameTime() + 200);
    state.setDirty();
    return true;
  }

  private static void resistanceTick(ServerLevel level, ServerSubLevel body) {
    var state = CoreTestState.get(level);
    var target = CombatAdapter.target(body);
    if (state.getPotionResistancePhase() == 0) {
      if (!spawnZombie(level, body)) {
        finishResistance(level, false, "zombie spawn failed");
        return;
      }
      state.setPotionResistancePhase(1);
      explain(level, "Resistance: waiting for ONE unprotected zombie hit");
    } else if (state.getPotionResistancePhase() == 1 || state.getPotionResistancePhase() == 3) {
      double damage = state.getPotionHitBefore() - SubLevelHealth.status(body).current();
      if (damage > 0) {
        removeZombie(level);
        if (state.getPotionResistancePhase() == 1) {
          state.setPotionBaselineDamage(damage);
          state.setPotionResistancePhase(2);
          target.invulnerableTime = 0;
          firePotion(level);
          state.setPotionDeadline(level.getGameTime() + 200);
          explain(level, "Baseline hit: " + damage + " HP. Applying resistance; zombie removed.");
          state.setDirty();
        } else {
          state.setPotionProtectedDamage(damage);
          state.setPotionResistanceHit(true);
          finishResistance(
              level,
              Math.abs(damage - state.getPotionBaselineDamage() * .8) < .001,
              "one zombie hit before / after Resistance I");
        }
        return;
      }
    } else if (state.getPotionResistancePhase() == 2
        && target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
      target.invulnerableTime = 0;
      if (!spawnZombie(level, body)) {
        finishResistance(level, false, "zombie spawn failed");
        return;
      }
      state.setPotionResistancePhase(3);
      explain(level, "Resistance: waiting for ONE protected zombie hit");
      state.setDirty();
    }
    if (level.getGameTime() >= state.getPotionDeadline()) {
      finishResistance(level, false, "timeout in phase " + state.getPotionResistancePhase());
    }
  }

  private static void finishResistance(ServerLevel level, boolean passed, String detail) {
    var state = CoreTestState.get(level);
    removeZombie(level);
    state
        .getPotionResults()
        .add(
            String.format(
                java.util.Locale.ROOT,
                "minecraft:resistance: %s (zombie damage %.2f → %.2f; %s)",
                passed ? "PASS" : "FAIL",
                state.getPotionBaselineDamage(),
                state.getPotionProtectedDamage(),
                detail));
    state.incrementPotionIndex();
    next(level);
  }

  public static String status(ServerLevel level) {
    var s = CoreTestState.get(level);
    return "Potion test: "
        + (s.isPotionRunning() ? "running " + (s.getPotionIndex() + 1) + "/6" : "ready")
        + ", saved results: "
        + s.getPotionResults().size();
  }

  public static List<String> results(ServerLevel level) {
    return List.copyOf(CoreTestState.get(level).getPotionResults());
  }

  private static void button(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    var entity = (CommandBlockEntity) level.getBlockEntity(pos);
    entity.getCommandBlock().setCommand(command);
    entity.getCommandBlock().setTrackOutput(false);
    entity.setChanged();
    level.setBlock(
        pos.above(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        Block.UPDATE_ALL);
  }

  private static void explain(ServerLevel level, String progress) {
    var removed = new ArrayList<Entity>();
    for (var entity : level.getAllEntities()) {
      if (entity.getTags().contains(MARK)) {
        removed.add(entity);
      }
    }
    removed.forEach(Entity::discard);
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) {
      return;
    }
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "POTION REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Six native splash potions; fresh subject each time.\n"
                    + "Inspect core or Vital Readout with goggles.\n"
                    + "Resistance: ONE zombie hit before, then ONE after the potion.\n"
                    + progress
                    + "\n"
                    + String.join("\n", results(level))),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 280);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base(level)).add(.5, 1.62, -7.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
