package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Resource-funded recovery and body changes proposed to the canonical goal queue. */
final class BioshipAdaptation {
  static final String FLESH = MaterialCost.FLESH,
      FIBER = MaterialCost.FIBER,
      BONE = MaterialCost.BONE;

  record Proposal(
      BioshipBrain.Action action,
      BlockPos pos,
      BlockState state,
      int flesh,
      int fiber,
      int bone,
      int urgency,
      String reason) {}

  private final MaterialLedger reserve = new MaterialLedger();
  private final Set<BlockPos> armor = new HashSet<>();
  private long nextWork;
  private boolean learnedMouth, learnedStomach;

  int material(String id) {
    return reserve.material(id);
  }

  private boolean pay(int flesh, int fiber, int bone) {
    return new MaterialCost(flesh, fiber, bone).pay(reserve);
  }

  void maintain(LivingCoreBlockEntity core) {
    learnedMouth |= core.hasMouth();
    var stomach = DigestiveSystem.stomach(core);
    learnedStomach |= stomach != null;
    armor.removeIf(
        p ->
            !core.getLevel().getBlockState(p).is(Create_bioships.FLESH.get())
                && !core.getLevel().getBlockState(p).is(Create_bioships.CARAPACE.get()));
    if (stomach == null || !stomach.connected(core)) return;
    BioshipStores.balance(stomach);
    if (BioshipNutrition.critical(core)) return;
    String[] ids = {FLESH, FIBER, BONE};
    int[] targets = {32, 16, 8}, floors = {core.isMature() ? 32 : 128, 32, 32};
    for (int i = 0; i < ids.length; i++) {
      int take =
          Math.min(
              targets[i] - material(ids[i]), Math.max(0, stomach.material(ids[i]) - floors[i]));
      if (take > 0 && MaterialCost.transfer(stomach, reserve, ids[i], take)) {
        core.setChanged();
      }
    }
  }

  List<Proposal> proposals(LivingCoreBlockEntity core) {
    return BioshipAdaptationPlanner.plan(
        core,
        new BioshipAdaptationPlanner.State(
            reserve.snapshot(), armor, nextWork, learnedMouth, learnedStomach));
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() % 20 != 0) return;
    maintain(core);
    var chosen = core.brain().choose(core);
    for (var p : proposals(core))
      if (p.action == chosen.action() && Objects.equals(p.pos, chosen.block())) {
        if (p.action == BioshipBrain.Action.REGROW_MOUTH
            || p.action == BioshipBrain.Action.REGROW_STOMACH) {
          if (!pay(p.flesh, p.fiber, p.bone)) return;
          if (!core.getLevel().setBlock(p.pos, p.state, 3)) {
            new MaterialCost(p.flesh, p.fiber, p.bone).refund(reserve);
            return;
          }
          if (p.action == BioshipBrain.Action.REGROW_MOUTH)
            core.getLevel()
                .setBlock(
                    p.pos,
                    p.state.setValue(
                        FeedingMouthBlock.FACING, FeedingMouthBlock.outward(core, p.pos)),
                    3);
          if (core.getLevel().getBlockEntity(p.pos) instanceof StomachBlockEntity stomach) {
            stomach.tubeBootstrapped = true;
            stomach.retainFlesh(4);
            stomach.retain(
                new ItemStack(
                    BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(FIBER)),
                    4));
          }
          DigestiveSystem.connect(core, core.ship());
        } else if (p.action == BioshipBrain.Action.STRENGTHEN_LEGS) {
          core.crawl().enhance(core);
        } else if (p.action == BioshipBrain.Action.RECLAIM_COMPONENT) {
          var mouth = core.mouthBlock();
          for (var face : Direction.values()) {
            var target = mouth.relative(face);
            var host = core.getLevel().getBlockState(target);
            if (!target.equals(p.pos)
                && (host.isAir() || host.is(Create_bioships.FLESH.get()))
                && OrganMotion.reabsorb(core, p.pos, target)) {
              armor.remove(p.pos);
              return;
            }
          }
        } else {
          var stomach = DigestiveSystem.stomach(core);
          if (stomach == null || !new MaterialCost(p.flesh, p.fiber, p.bone).funded(stomach))
            return;
          if (!new MaterialCost(p.flesh, p.fiber, p.bone).pay(stomach)) return;
          if (!core.getLevel().setBlock(p.pos, p.state, 3)) {
            new MaterialCost(p.flesh, p.fiber, p.bone).refund(stomach);
            return;
          }
          if (p.action == BioshipBrain.Action.GROW_STORAGE)
            stomach.paidTubeStock += Math.max(0, p.flesh - 8);
          if (p.action == BioshipBrain.Action.GROW_STORAGE) {
            var sac = core.getLevel().getBlockEntity(p.pos);
            if (sac != null) sac.getPersistentData().putBoolean(BioshipStores.AUTOMATIC, true);
            DigestiveSystem.connect(core, core.ship());
          } else armor.add(p.pos);
        }
        nextWork = core.getLevel().getGameTime() + 100;
        core.setChanged();
        return;
      }
  }

  int burnEmergency() {
    for (var id : List.of(FLESH, FIBER, BONE))
      if (material(id) > 0) {
        reserve.spend(id, 1);
        return id.equals(FLESH) ? 10 : id.equals(FIBER) ? 8 : 2;
      }
    return 0;
  }

  CompoundTag save() {
    var t = new CompoundTag();
    var stock = new CompoundTag();
    reserve.snapshot().forEach(stock::putInt);
    t.put("Reserve", stock);
    t.putLongArray("Armor", armor.stream().mapToLong(BlockPos::asLong).toArray());
    t.putBoolean("Mouth", learnedMouth);
    t.putBoolean("Stomach", learnedStomach);
    t.putLong("Next", nextWork);
    return t;
  }

  void load(CompoundTag t) {
    var stock = t.getCompound("Reserve");
    var saved = new HashMap<String, Integer>();
    for (var id : stock.getAllKeys()) saved.put(id, stock.getInt(id));
    reserve.restore(saved);
    armor.clear();
    for (long pos : t.getLongArray("Armor")) armor.add(BlockPos.of(pos));
    learnedMouth = t.getBoolean("Mouth");
    learnedStomach = t.getBoolean("Stomach");
    nextWork = t.getLong("Next");
  }
}
