package org.Azkerash.create_bioships.core;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class StomachPanelTests {
  @GameTest(template = "test_empty", batch = "stomach_panel_access")
  public static void only_panel_face_opens_menu_in_every_orientation(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PanelAccess");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(level, profile) {
          @Override
          public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) {
            containerMenu = provider.createMenu(1, getInventory(), this);
            return java.util.OptionalInt.of(1);
          }

          @Override
          public void closeContainer() {
            containerMenu = inventoryMenu;
          }
        };
    player.setPos(Vec3.atCenterOf(pos));
    for (var facing : Direction.values()) {
      var state =
          ComponentRegistry.STOMACH.get().defaultBlockState().setValue(StomachBlock.FACING, facing);
      level.setBlock(pos, state, 3);
      for (var face : Direction.values()) {
        player.closeContainer();
        var result =
            state.useWithoutItem(
                level,
                player,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(face, .5), face, pos, false));
        helper.assertTrue(
            result.consumesAction() == (face == facing),
            "Wrong face handled inventory access: panel=" + facing + " hit=" + face);
        helper.assertTrue(
            (player.containerMenu instanceof StomachMenu) == (face == facing),
            "Wrong face opened inventory: panel=" + facing + " hit=" + face);
      }
    }
    player.closeContainer();
    helper.succeed();
  }
}
