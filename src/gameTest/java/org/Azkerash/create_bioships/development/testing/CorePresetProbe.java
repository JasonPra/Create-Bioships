package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.core.CoreBlock;
import org.Azkerash.create_bioships.core.*;

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
    if (layout.base().getY() != 64 || level.getSharedSpawnPos().getY() != 64)
      throw new IllegalStateException("New world fixtures/spawn are not at sea-level ground");
    if (!level.getBlockState(new BlockPos(100, 62, 100)).is(Blocks.BEDROCK)
        || !DevelopmentTestBlocks.protectedBlock(level.getBlockState(new BlockPos(100, 63, 100)))
        || !level.getBlockState(new BlockPos(100, 64, 100)).isAir()
        || !level.getBlockState(new BlockPos(100, level.getMinBuildHeight(), 100)).isAir())
      throw new IllegalStateException("Raised superflat floor layers invalid");
    for (var pos : layout.cores())
      if (!level.getBlockState(pos).is(ComponentRegistry.CORE.get())
          || !level.getBlockState(pos).getValue(CoreBlock.BLINK))
        throw new IllegalStateException("Generated core missing or assembled");
    if (!level
        .getBlockState(layout.base().above(CoreSplitFixture.HEIGHT))
        .is(DevelopmentTestBlocks.GLASS.get()))
      throw new IllegalStateException("Enclosure roof missing");
    var glue =
        level.getEntitiesOfClass(
            dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity.class,
            layout.glue());
    if (glue.size() != 1 || glue.getFirst().contains(layout.tip().below()))
      throw new IllegalStateException("Native fixture glue invalid: " + glue.size());
    var state = CoreTestState.get(level);
    if (state.combatOrigin.getY() != 64
        || state.arrowOrigin.getY() != 64
        || state.potionOrigin.getY() != 64)
      throw new IllegalStateException("Fixture elevations disagree");
    for (var origin :
        java.util.List.of(state.combatOrigin, state.arrowOrigin, state.potionOrigin, layout.base()))
      if (!level.getBlockState(origin.below()).is(Blocks.BEDROCK))
        throw new IllegalStateException("Fixture floor missing at " + origin);
    if (!state.combatBuilt || state.combatRunning)
      throw new IllegalStateException("Combat fixture not ready");
    var combatCore = state.combatOrigin.above(9);
    if (!level.getBlockState(combatCore).is(ComponentRegistry.CORE.get())
        || !level.getBlockState(combatCore).getValue(CoreBlock.BLINK))
      throw new IllegalStateException("Combat fixture assembled before START");
    if (!level
        .getBlockState(state.combatOrigin.offset(13, 2, 0))
        .is(DevelopmentTestBlocks.GLASS.get()))
      throw new IllegalStateException("Combat glass missing");
    var decoration = new java.util.HashSet<net.minecraft.world.level.block.Block>();
    // Sample a generated chunk far from every fixture, not a startup decoration patch.
    for (int x = 160; x < 176; x++)
      for (int z = 160; z < 176; z++)
        decoration.add(level.getBlockState(new BlockPos(x, 63, z)).getBlock());
    if (decoration.size() < 5
        || !decoration.containsAll(
            DevelopmentTestBlocks.FLOOR.values().stream().map(h -> h.get()).toList()))
      throw new IllegalStateException("New-world decorative palette missing: " + decoration);
    var navigatorPlayer =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "DevNavigatorProbe")) {
          @Override
          public void teleportTo(
              net.minecraft.server.level.ServerLevel destination,
              double x,
              double y,
              double z,
              float yaw,
              float pitch) {
            setPos(x, y, z);
          }

          @Override
          public java.util.OptionalInt openMenu(
              net.minecraft.world.MenuProvider provider,
              java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
            containerMenu = provider.createMenu(1, getInventory(), this);
            return java.util.OptionalInt.of(1);
          }
        };
    DevelopmentNavigatorItem.open(navigatorPlayer);
    if (!(navigatorPlayer.containerMenu instanceof net.minecraft.world.inventory.ChestMenu))
      throw new IllegalStateException("Navigator menu failed");
    navigatorPlayer.containerMenu.clicked(
        1, 0, net.minecraft.world.inventory.ClickType.PICKUP, navigatorPlayer);
    if (Math.abs(navigatorPlayer.getX() - state.combatOrigin.getX()) > 1
        || Math.abs(navigatorPlayer.getZ() - (state.combatOrigin.getZ() - 18)) > 1)
      throw new IllegalStateException("Navigator did not teleport to combat arena");
    if (state.combatOrigin.getX() != 1024 || state.combatOrigin.getZ() != 1024)
      throw new IllegalStateException("Combat arena not isolated");
    System.out.println("NAVIGATOR_PRESET_PASS menu=true teleport=true isolated=true");
    System.out.println(
        "COMBAT_PRESET_PASS unassembled=true glass=true decoration=" + decoration.size());
    System.out.println(
        "CORE_PRESET_PASS groundY=64 cores="
            + layout.cores().size()
            + " bedrock=true roof=true assembled=false commandBlocks="
            + event.getServer().isCommandBlockEnabled());
    event.getServer().halt(false);
  }
}
