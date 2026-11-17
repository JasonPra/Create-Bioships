package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Persistent grid identity and player assembler restrictions; no creature behavior. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CoreAssemblyRules {
  public static final String TYPE = "create_bioships:creature_type",
      ID = "create_bioships:creature_id";

  public static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) return null;
    var plot = container.getPlot(new ChunkPos(pos));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel body && !body.isRemoved()
        ? body
        : null;
  }

  public static void mark(ServerLevel level, BlockPos core) {
    var body = containing(level, core);
    if (body == null) return;
    var old = body.getUserDataTag();
    if (old != null && "amoeba".equals(old.getString(TYPE)) && old.hasUUID(ID)) return;
    var tag = old == null ? new CompoundTag() : old.copy();
    tag.putString(TYPE, "amoeba");
    if (!tag.hasUUID(ID)) tag.putUUID(ID, body.getUniqueId());
    body.setUserDataTag(tag);
  }

  public static boolean isCreature(SubLevel body) {
    return body instanceof ServerSubLevel server
        && server.getUserDataTag() != null
        && "amoeba".equals(server.getUserDataTag().getString(TYPE));
  }

  @SubscribeEvent
  public static void placed(BlockEvent.EntityPlaceEvent event) {
    if (event.getEntity() instanceof Player
        && event.getLevel() instanceof ServerLevel level
        && event.getPlacedBlock().getBlock() instanceof PhysicsAssemblerBlock
        && isCreature(containing(level, event.getPos()))) event.setCanceled(true);
  }
}
