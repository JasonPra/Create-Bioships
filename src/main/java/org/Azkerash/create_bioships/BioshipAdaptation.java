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

  MaterialLedger ledger() {
    return reserve;
  }

  int material(String id) {
    return reserve.material(id);
  }

  void creditRecovery(String id, int count) {
    reserve.credit(id, count);
  }

  private boolean pay(LivingCoreBlockEntity core, int flesh, int fiber, int bone) {
    return new MaterialCost(flesh, fiber, bone).pay(BioshipStorage.account(core));
  }

  void maintain(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    armor.removeIf(
        p ->
            !BioshipTissue.isTissue(core.getLevel().getBlockState(p))
                && !core.getLevel().getBlockState(p).is(Create_bioships.CARAPACE.get()));
    if (stomach != null) BioshipStorage.flush(core);
  }

  List<Proposal> proposals(LivingCoreBlockEntity core) {
    return BioshipAdaptationPlanner.plan(
        core, new BioshipAdaptationPlanner.State(BioshipStorage.snapshot(core), armor, nextWork));
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() % 20 != 0) return;
    maintain(core);
    var chosen = core.brain().choose(core);
    for (var p : proposals(core))
      if (p.action == chosen.action() && Objects.equals(p.pos, chosen.block())) {
        if (p.action == BioshipBrain.Action.REGROW_MOUTH
            || p.action == BioshipBrain.Action.REGROW_STOMACH) {
          if (!pay(core, p.flesh, p.fiber, p.bone)) return;
          if (!core.getLevel().setBlock(p.pos, p.state, 3)) {
            new MaterialCost(p.flesh, p.fiber, p.bone).refund(BioshipStorage.account(core));
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
        } else if (p.action == BioshipBrain.Action.GROW_LARYNX) {
          if (!BioshipLarynx.develop(core, p.pos)) return;
        } else if (p.action == BioshipBrain.Action.GROW_ENGINE_ROOM) {
          if (!core.engineRoom().expand(core)) return;
        } else if (p.action == BioshipBrain.Action.GROW_DIGESTION) {
          var plan = BioshipDigestiveGrowth.plan(core);
          if (plan == null || plan.legUpgrades() > 0 || !plan.mouth().equals(p.pos)) return;
          var account = DigestiveSystem.stomach(core);
          if (!plan.cost().pay(account)) return;
          var originals = new LinkedHashMap<BlockPos, BlockState>();
          for (var pos : List.of(plan.mouth(), plan.stomach(), plan.tube()))
            originals.put(pos, core.getLevel().getBlockState(pos));
          boolean mouth =
              !plan.addMouth()
                  || core.getLevel()
                      .setBlock(
                          plan.mouth(),
                          Create_bioships.FEEDING_MOUTH
                              .get()
                              .defaultBlockState()
                              .setValue(FeedingMouthBlock.FACING, plan.facing()),
                          3);
          boolean stomach =
              !plan.addStomach()
                  || core.getLevel()
                      .setBlock(
                          plan.stomach(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
          if (!mouth || !stomach) {
            originals.forEach((pos, state) -> core.getLevel().setBlock(pos, state, 3));
            plan.cost().refund(account);
            return;
          }
          var organ = (StomachBlockEntity) core.getLevel().getBlockEntity(plan.stomach());
          if (plan.addStomach()) {
            organ.tubeBootstrapped = true;
            organ.retainFlesh(4);
            organ.credit(FIBER, 4);
          }
          organ.paidTubeStock += plan.tubeCredits();
          organ.connectedMouth = plan.mouth();
          DigestiveSystem.connect(core, core.ship());
        } else if (p.action == BioshipBrain.Action.STRENGTHEN_LEGS) {
          core.crawl().enhance(core);
        } else if (p.action == BioshipBrain.Action.RECLAIM_COMPONENT) {
          if (!core.hasMouth() || DigestiveSystem.stomach(core) == null) {
            if (OrganMotion.reclaimToCore(core, p.pos)) {
              armor.remove(p.pos);
              core.setChanged();
            }
            return;
          }
          var mouth = core.mouthBlock();
          for (var face : Direction.values()) {
            var target = mouth.relative(face);
            var host = core.getLevel().getBlockState(target);
            if (!target.equals(p.pos)
                && (host.isAir() || BioshipTissue.isTissue(host))
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

  int burnEmergency(LivingCoreBlockEntity core) {
    for (var id : List.of(FLESH, FIBER, BONE))
      if (material(id)
          > ((!core.hasMouth() ? (id.equals(FLESH) ? 8 : id.equals(FIBER) ? 4 : 2) : 0)
              + (DigestiveSystem.stomach(core) == null
                  ? (id.equals(FLESH) ? 16 : id.equals(FIBER) ? 8 : 4)
                  : 0))) {
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
    nextWork = t.getLong("Next");
  }
}
