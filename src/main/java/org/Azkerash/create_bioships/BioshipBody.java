package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Persistent intended tissue, independent of current holes. Never exposes a health HUD. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipBody {
  final Map<BlockPos, BlockState> memory = new LinkedHashMap<>();
  private final Set<BlockPos> injured = new HashSet<>();
  private double health = 100;
  private int capacity = 100;
  private volatile boolean dead;
  private long lastCombatAt = Long.MIN_VALUE;
  private float lastCombatAmount;

  public boolean dead() {
    return dead;
  }

  void starve(LivingCoreBlockEntity core, int demand) {
    hurt(core, Math.max(1, demand));
  }

  void drown(LivingCoreBlockEntity core) {
    hurt(core, 2);
  }

  private void hurt(LivingCoreBlockEntity core, double amount) {
    health = Math.max(0, health - amount);
    if (health <= 0) die(core);
    core.setChanged();
    core.getLevel()
        .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }

  void die(LivingCoreBlockEntity core) {
    if (dead) return;
    dead = true;
    core.chunkLoading.release();
    health = 0;
    if (core.ship() != null) {
      BioshipDecay.mark(core.ship());
      core.assimilation().abandon(core);
      var move = core.organMove();
      if (move != null) {
        var cargo =
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(core.getLevel())
                .getSubLevel(move.cargo());
        if (cargo instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel child)
          BioshipDecay.mark(child);
        OrganMotion.abandon(core);
      }
    }
    core.setChanged();
  }

  public double health() {
    return health;
  }

  public int capacity() {
    return capacity;
  }

  static Map<BlockPos, BlockState> actual(LivingCoreBlockEntity core) {
    Map<BlockPos, BlockState> out = new LinkedHashMap<>();
    if (core.ship() == null) return out;
    BioshipStructure.visit(
        core.ship(), (pos, state) -> out.put(pos.subtract(core.getBlockPos()), state));
    return out;
  }

  void observe(LivingCoreBlockEntity core) {
    if (dead) return;
    var current = actual(core);
    current.forEach(
        (p, s) -> {
          var expected = memory.get(p);
          if (expected == null || expected.is(s.getBlock())) {
            memory.put(p, s);
            injured.remove(p);
          } else if (injured.add(p)) health = Math.max(0, health - 4);
        });
    int next = Math.max(100, memory.size() * 4);
    health = Math.min(next, health + Math.max(0, next - capacity));
    capacity = next;
    for (var entry : memory.entrySet())
      if (!current.containsKey(entry.getKey())
          && !core.rescue().retracted(core.getBlockPos().offset(entry.getKey()))
          && injured.add(entry.getKey())) health = Math.max(0, health - 4);
    if (health <= 0) die(core);
    core.setChanged();
  }

  void rebase(BlockPos delta) {
    var cells = new LinkedHashMap<>(memory);
    memory.clear();
    cells.forEach((p, s) -> memory.put(p.offset(delta), s));
    var damage = new HashSet<>(injured);
    injured.clear();
    damage.forEach(p -> injured.add(p.offset(delta)));
  }

  /** Only changes made by the ship itself retire old schematic cells. */
  void reconcile(LivingCoreBlockEntity core, Map<BlockPos, BlockState> before) {
    var after = actual(core);
    before.forEach(
        (p, s) -> {
          if (!after.containsKey(p)) {
            memory.remove(p);
            injured.remove(p);
          }
        });
    after.forEach(
        (p, s) -> {
          if (!s.equals(before.get(p)) || !memory.containsKey(p)) {
            memory.put(p, s);
            injured.remove(p);
          }
        });
  }

  void tick(LivingCoreBlockEntity core) {
    if (dead || core.starving()) return;
    if (core.getLevel().getGameTime() % 20 != 0 || core.ship() == null || core.blueprint().busy())
      return;
    observe(core);
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null || OrganMotion.busy(core)) return;
    for (var e : memory.entrySet()) {
      var pos = core.getBlockPos().offset(e.getKey());
      if (!core.getLevel().getBlockState(pos).isAir() || core.rescue().retracted(pos)) continue;
      Vec3 physical = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
      if (!core.getLevel().getBlockState(BlockPos.containing(physical)).isAir()) continue;
      // Repair never grows a solid block through a passenger or nearby creature.
      if (!core.getLevel()
          .getEntitiesOfClass(
              net.minecraft.world.entity.Entity.class,
              new net.minecraft.world.phys.AABB(
                  physical.x - .5,
                  physical.y - .5,
                  physical.z - .5,
                  physical.x + .5,
                  physical.y + .5,
                  physical.z + .5),
              entity -> !(entity instanceof BioshipTargetEntity))
          .isEmpty()) continue;
      boolean supported = false;
      for (Direction d : Direction.values())
        if (!core.getLevel().getBlockState(pos.relative(d)).isAir()) supported = true;
      if (!supported || !e.getValue().canSurvive(core.getLevel(), pos)) continue;
      var restored = e.getValue();
      if (restored.is(Create_bioships.INTESTINE.get())) {
        boolean paid;
        if (stomach.freeTubeStock > 0) {
          stomach.freeTubeStock--;
          paid = false;
        } else if (stomach.paidTubeStock > 0) {
          stomach.paidTubeStock--;
          paid = true;
        } else {
          if (stomach.material("biomancy:flesh_bits") < 1
              || stomach.material("biomancy:elastic_fibers") < 1) return;
          stomach.spend("biomancy:flesh_bits", 1);
          stomach.spend("biomancy:elastic_fibers", 1);
          paid = true;
        }
        restored = restored.setValue(IntestineBlock.PAID, paid);
        stomach.setChanged();
      } else if (!stomach.spend("biomancy:flesh_bits", 1)) return;
      core.getLevel().setBlock(pos, restored, 3);
      injured.remove(e.getKey());
      health = Math.min(capacity, health + 4);
      core.setChanged();
      return;
    }
    if (health < capacity && stomach.spend("biomancy:flesh_bits", 1)) {
      health = Math.min(capacity, health + 4);
      core.setChanged();
    }
  }

  boolean combatDamage(
      LivingCoreBlockEntity core,
      net.minecraft.world.damagesource.DamageSource source,
      float amount) {
    if (dead) return false;
    long now = core.getLevel().getGameTime();
    float actual = amount;
    if (lastCombatAt != Long.MIN_VALUE && now - lastCombatAt < 10) {
      if (amount <= lastCombatAmount) return false;
      actual -= lastCombatAmount;
    } else lastCombatAt = now;
    lastCombatAmount = amount;
    hurt(core, actual);
    if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
      core.defense().remember(core, attacker);
    if (source.getEntity() instanceof Player player && player.getUUID().equals(core.creator()))
      core.mistreat();
    return true;
  }

  void struck(LivingCoreBlockEntity core, Player player) {
    hurt(core, 1);
    if (player.getUUID().equals(core.creator())) core.mistreat();
    core.setChanged();
  }

  void damage(LivingCoreBlockEntity core, BlockPos pos, Player player) {
    var local = pos.subtract(core.getBlockPos());
    memory.putIfAbsent(local, core.getLevel().getBlockState(pos));
    if (injured.add(local)) hurt(core, 4);
    if (player != null && player.getUUID().equals(core.creator())) core.mistreat();
    core.setChanged();
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public static void broken(BlockEvent.BreakEvent event) {
    if (event.isCanceled() || !(event.getLevel() instanceof net.minecraft.world.level.Level level))
      return;
    var core = BioshipAccess.core(level, event.getPos());
    if (core != null) {
      core.body().damage(core, event.getPos(), event.getPlayer());
      if (event.getPos().equals(core.getBlockPos())) core.body().die(core);
    }
  }

  @SubscribeEvent
  public static void blast(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
    Set<LivingCoreBlockEntity> blamed = new HashSet<>();
    var source = event.getExplosion().getIndirectSourceEntity();
    for (var pos : event.getAffectedBlocks()) {
      var core = BioshipAccess.core(event.getLevel(), pos);
      if (core == null) continue;
      core.body().damage(core, pos, null);
      if (pos.equals(core.getBlockPos())) core.body().die(core);
      if (source instanceof Player player
          && player.getUUID().equals(core.creator())
          && blamed.add(core)) core.mistreat();
    }
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.putBoolean("Dead", dead);
    tag.putDouble("Health", health);
    tag.putLong("CombatAt", lastCombatAt);
    tag.putFloat("CombatAmount", lastCombatAmount);
    tag.putInt("Capacity", capacity);
    tag.put("Cells", saveCells(memory));
    tag.putLongArray("Injured", injured.stream().mapToLong(BlockPos::asLong).toArray());
    return tag;
  }

  void load(CompoundTag tag, HolderLookup.Provider registries) {
    lastCombatAt = tag.contains("CombatAt") ? tag.getLong("CombatAt") : Long.MIN_VALUE;
    lastCombatAmount = tag.getFloat("CombatAmount");
    memory.clear();
    memory.putAll(loadCells(tag.getList("Cells", Tag.TAG_COMPOUND), registries));
    capacity = Math.max(100, tag.getInt("Capacity"));
    health = tag.contains("Health") ? Math.clamp(tag.getDouble("Health"), 0, capacity) : 100;
    dead = tag.getBoolean("Dead") || health <= 0;
    injured.clear();
    for (long p : tag.getLongArray("Injured")) injured.add(BlockPos.of(p));
  }

  static ListTag saveCells(Map<BlockPos, BlockState> cells) {
    var list = new ListTag();
    cells.forEach(
        (p, s) -> {
          var t = new CompoundTag();
          t.putLong("P", p.asLong());
          t.put("S", NbtUtils.writeBlockState(s));
          list.add(t);
        });
    return list;
  }

  static Map<BlockPos, BlockState> loadCells(ListTag list, HolderLookup.Provider registries) {
    Map<BlockPos, BlockState> out = new LinkedHashMap<>();
    var blocks = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
    for (int i = 0; i < Math.min(list.size(), 12000); i++) {
      var t = list.getCompound(i);
      var s = NbtUtils.readBlockState(blocks, t.getCompound("S"));
      if (!s.isAir()) out.put(BlockPos.of(t.getLong("P")), s);
    }
    return out;
  }
}
