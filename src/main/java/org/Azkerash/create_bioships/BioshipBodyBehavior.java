package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.nbt.*;

/** A separately ranked body-work tree. Shared mutation locks do not depend on the action tree. */
final class BioshipBodyBehavior {
  private final GoalQueue<BioshipAdaptation.Proposal> goals = new GoalQueue<>();
  private String previous = "", waiting = "Not evaluated";
  private BioshipAdaptation.Proposal selected;

  static String blocker(LivingCoreBlockEntity core) {
    if (core.getLevel() == null || core.ship() == null) return "Dormant core";
    if (core.body().dead()) return "Dead organism";
    if (core.blueprint().busy()) return "Approved reconstruction owns the layout";
    if (core.rescue().active()) return "Ally rescue owns the aperture";
    if (core.coreMotion().active()) return "Core relocation owns the layout";
    if (OrganMotion.busy(core) || core.assimilation().finishing())
      return "Component transaction owns the layout";
    return "";
  }

  private static String key(BioshipAdaptation.Proposal p) {
    return p.action().name() + ":" + p.pos();
  }

  private static BehaviorPriority.Offer priority(BioshipAdaptation.Proposal p) {
    return new BehaviorPriority.Offer(
        key(p),
        p.urgency() > 500 ? BehaviorPriority.Band.NEED : BehaviorPriority.Band.GOAL,
        p.urgency(),
        0,
        0);
  }

  BioshipAdaptation.Proposal choose(LivingCoreBlockEntity core) {
    waiting = blocker(core);
    var candidates = new ArrayList<BioshipAdaptation.Proposal>();
    if (waiting.isEmpty()) {
      candidates.addAll(core.adaptation().proposals(core));
      var configuration = BioshipConfiguration.proposal(core);
      if (configuration != null) candidates.add(configuration);
    }
    goals.refresh(candidates, BioshipBodyBehavior::priority, previous);
    selected = goals.pending().isEmpty() ? null : goals.pending().getFirst();
    if (selected != null) previous = key(selected);
    else if (waiting.isEmpty()) waiting = "No feasible funded body work";
    return selected;
  }

  boolean allowsConfiguration(LivingCoreBlockEntity core) {
    return blocker(core).isEmpty()
        && (selected == null || selected.action() == BioshipBrain.Action.CONFIGURE);
  }

  CompoundTag debug() {
    var data = new CompoundTag();
    data.putString("Action", selected == null ? "IDLE" : selected.action().name());
    data.putString("Reason", selected == null ? waiting : selected.reason());
    if (selected != null) data.putLong("Target", selected.pos().asLong());
    var priorities = new ListTag();
    for (var p : goals.pending().stream().limit(5).toList()) {
      var row = new CompoundTag();
      var offer = priority(p);
      row.putString("Action", p.action().name());
      row.putString("Band", offer.band().name());
      row.putInt("Urgency", p.urgency());
      row.putDouble("Weight", offer.weight(previous));
      row.putString("Reason", p.reason());
      priorities.add(row);
    }
    data.put("Priorities", priorities);
    data.putInt("QueuedGoals", goals.pending().size());
    return data;
  }
}
