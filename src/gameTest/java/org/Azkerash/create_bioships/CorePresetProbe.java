package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.development.*;

/** Opt-in fresh-world startup check, excluded from the shipped JAR. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CorePresetProbe {
  private static int ticks;

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("core.presetProbe") || ++ticks != 20) return;
    var level = event.getServer().overworld();
    var layout = CoreTestState.get(level).layout();
    if (!CoreTestCommands.development(level))
      throw new IllegalStateException("Wrong development preset generator");
    if (!level.getBlockState(new BlockPos(100, level.getMinBuildHeight(), 100)).is(Blocks.BEDROCK))
      throw new IllegalStateException("Superflat bedrock layer missing");
    for (var pos : layout.cores())
      if (!level.getBlockState(pos).is(Create_bioships.CORE.get())
          || !level.getBlockState(pos).getValue(CoreBlock.BLINK))
        throw new IllegalStateException("Generated core missing or assembled");
    if (!level.getBlockState(layout.base().above(CoreSplitFixture.HEIGHT)).is(Blocks.GLASS))
      throw new IllegalStateException("Enclosure roof missing");
    var glue =
        level.getEntitiesOfClass(
            dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity.class,
            layout.glue());
    if (glue.size() != 1 || glue.getFirst().contains(layout.tip().below()))
      throw new IllegalStateException("Native fixture glue invalid: " + glue.size());
    System.out.println(
        "CORE_PRESET_PASS cores="
            + layout.cores().size()
            + " bedrock=true roof=true assembled=false commandBlocks="
            + event.getServer().isCommandBlockEnabled());
    event.getServer().halt(false);
  }
}
