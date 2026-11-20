package org.Azkerash.create_bioships.core.naming;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.components.logic.nametag.NameTagDisplayEntity;
import org.Azkerash.create_bioships.core.*;

/**
 * Display labels are independent. The first nonempty panel deterministically supplies the construct
 * name.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelNames {
  private static final int REFRESH_INTERVAL_TICKS = 20;

  private static final Map<ServerSubLevel, List<NameTagDisplayEntity>> PANELS =
      new IdentityHashMap<>();
  private static final Map<ServerSubLevel, Integer> FAMILIES = new IdentityHashMap<>();
  private static final Set<ServerSubLevel> DIRTY =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final String MANAGED = "create_bioships:display_named";

  private SubLevelNames() {}

  public static void request(ServerSubLevel body) {
    DIRTY.add(SubLevelCreatureParts.root(body));
  }

  public static void changed(ServerSubLevel body, BlockPos p, BlockState state) {
    var root = SubLevelCreatureParts.root(body);
    var known = PANELS.get(root);
    if (state.getBlock()
            instanceof org.Azkerash.create_bioships.components.logic.nametag.NameTagDisplayBlock
        || known != null && known.stream().anyMatch(panel -> panel.getBlockPos().equals(p))) {
      request(root);
    }
  }

  public static String name(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    refresh(body);
    return body.getName() == null ? "" : body.getName();
  }

  public static void refresh(ServerSubLevel body) {
    boolean dirty = DIRTY.remove(body);
    body = SubLevelCreatureParts.root(body);
    dirty |= DIRTY.remove(body);
    if (body.isRemoved()) {
      return;
    }
    var members = SubLevelCreatureParts.members(body);
    if (!PANELS.containsKey(body) || dirty || FAMILIES.getOrDefault(body, 0) != members.size()) {
      FAMILIES.put(body, members.size());
      var panels = new ArrayList<NameTagDisplayEntity>();
      for (var member : members) {
        for (var holder : member.getPlot().getLoadedChunks()) {
          for (var be : holder.getChunk().getBlockEntities().values()) {
            if (be instanceof NameTagDisplayEntity panel && !panel.isRemoved()) {
              panels.add(panel);
            }
          }
        }
      }
      panels.sort(Comparator.comparing(NameTagDisplayEntity::getBlockPos));
      PANELS.put(body, List.copyOf(panels));
    }
    String chosen = "";
    for (var panel : PANELS.get(body)) {
      if (!panel.isRemoved() && !panel.label().getString().isBlank()) {
        chosen = panel.label().getString();
        break;
      }
    }
    var existing = body.getUserDataTag();
    boolean managed = existing != null && existing.getBoolean(MANAGED);
    if (!chosen.isBlank()) {
      if (!chosen.equals(body.getName())) {
        body.setName(chosen);
      }
      if (!managed) {
        var updated = existing == null ? new net.minecraft.nbt.CompoundTag() : existing.copy();
        updated.putBoolean(MANAGED, true);
        body.setUserDataTag(updated);
      }
    } else if (existing != null && managed) {
      body.setName(null);
      var updated = existing.copy();
      updated.remove(MANAGED);
      body.setUserDataTag(updated);
    }
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    PANELS.keySet().removeIf(ServerSubLevel::isRemoved);
    FAMILIES.keySet().removeIf(ServerSubLevel::isRemoved);
    DIRTY.removeIf(ServerSubLevel::isRemoved);
    for (var body : List.copyOf(DIRTY)) {
      refresh(body);
    }
    if (e.getServer().getTickCount() % REFRESH_INTERVAL_TICKS == 0) {
      for (var l : e.getServer().getAllLevels()) {
        var c = SubLevelContainer.getContainer(l);
        if (c != null) {
          for (var b : List.copyOf(c.getAllSubLevels())) {
            if (!SubLevelCreatureParts.isPart(b)) {
              refresh(b);
            }
          }
        }
      }
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
    PANELS.clear();
    FAMILIES.clear();
    DIRTY.clear();
  }
}
