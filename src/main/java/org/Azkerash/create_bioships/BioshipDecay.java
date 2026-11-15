package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Decay belongs to the physics body so fragments continue to rot without a core. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipDecay {
  public static boolean dead(ServerSubLevel ship) {
    return ship.getUserDataTag() != null && ship.getUserDataTag().getBoolean("BioshipDead");
  }

  public static void mark(ServerSubLevel ship) {
    if (dead(ship)) return;
    var tag = ship.getUserDataTag() == null ? new CompoundTag() : ship.getUserDataTag().copy();
    tag.putBoolean("BioshipDead", true);
    tag.putUUID("BioshipCorpseRoot", ship.getUniqueId());
    tag.putLong("BioshipDecayAfter", ship.getLevel().getGameTime() + 200);
    ship.setUserDataTag(tag);
  }

  public static void inherit(ServerSubLevel child, ServerSubLevel original) {
    if (!dead(original)) return;
    mark(child);
    var tag = child.getUserDataTag().copy();
    var old = original.getUserDataTag();
    if (old.hasUUID("BioshipCorpseRoot"))
      tag.putUUID("BioshipCorpseRoot", old.getUUID("BioshipCorpseRoot"));
    tag.putLong("BioshipDecayAfter", old.getLong("BioshipDecayAfter"));
    child.setUserDataTag(tag);
  }

  static List<BlockPos> blocks(ServerSubLevel ship) {
    List<BlockPos> result = new ArrayList<>();
    for (var holder : ship.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int index = 0; index < sections.length; index++) {
        var section = sections[index];
        if (section.hasOnlyAir()) continue;
        int sy = chunk.getSectionYFromSectionIndex(index) * 16;
        for (int y = 0; y < 16; y++)
          for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++)
              if (!section.getBlockState(x, y, z).isAir())
                result.add(
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        sy + y,
                        chunk.getPos().getMinBlockZ() + z));
      }
    }
    return result;
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return;
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel ship
          && !ship.isRemoved()
          && dead(ship)
          && level.getGameTime() >= ship.getUserDataTag().getLong("BioshipDecayAfter")) {
        var cells = blocks(ship);
        Collections.shuffle(cells, new Random(level.getGameTime() ^ ship.getUniqueId().hashCode()));
        cells.sort(
            Comparator.comparing(p -> level.getBlockState(p).is(Create_bioships.CORE.get())));
        int amount = Math.min(cells.size(), 1 + cells.size() / 128);
        for (int i = 0; i < amount; i++) level.destroyBlock(cells.get(i), false);
      }
  }
}
