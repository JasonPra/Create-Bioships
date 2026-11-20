package org.Azkerash.create_bioships.components;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.logic.nerve.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class NervePanelTests {
  @GameTest(template = "test_empty", batch = "nerve_panel", timeoutTicks = 300)
  public static void panel_packets_validate_owner_grid_range_and_named_mob_identity(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    helper.runAfterDelay(
        1,
        () -> {
          level.setBlock(
              origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              origin.east(),
              ComponentRegistry.NERVE_PANEL
                  .get()
                  .defaultBlockState()
                  .setValue(BlockStateProperties.FACING, Direction.EAST),
              Block.UPDATE_ALL);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.east()),
                  new BoundingBox3i(origin, origin.east()));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          var panel = core.east();
          var world = body.logicalPose().transformPosition(Vec3.atCenterOf(panel));
          var owner =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PanelOwner"));
          var other =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PanelOther"));
          owner.setPos(world.add(0, 0, -2));
          other.setPos(owner.position());
          SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
          var mob = EntityType.COW.create(level);
          mob.setCustomName(Component.literal("Guest"));
          mob.setPos(world.add(3, 0, 0));
          helper.assertTrue(
              SubLevelPermissions.add(body, owner.getUUID(), mob),
              "Could not seed named guest permission");
          var add = new NervePanelAction(body.getUniqueId(), panel, 2, mob.getUUID());
          helper.assertTrue(
              !NervePanelControl.handle(other, add),
              "Non-owner changed permissions through a packet");
          helper.assertTrue(
              !NervePanelControl.handle(
                  owner, new NervePanelAction(UUID.randomUUID(), panel, 2, mob.getUUID())),
              "Wrong-grid packet accepted");
          helper.assertTrue(
              NervePanelControl.handle(owner, add)
                  && !SubLevelPermissions.get(body).permits(mob.getUUID()),
              "Owner could not remove a named guest through the panel");
          SubLevelPermissions.add(body, owner.getUUID(), mob);
          owner.setPos(world.add(100, 0, 0));
          helper.assertTrue(
              !NervePanelControl.handle(
                  owner, new NervePanelAction(body.getUniqueId(), panel, 2, mob.getUUID())),
              "Remote panel packet edited permissions");
          owner.setPos(world.add(0, 0, -2));
          helper.assertTrue(
              !NervePanelControl.handle(
                  owner, new NervePanelAction(body.getUniqueId(), panel, 3, UUID.randomUUID())),
              "Ownership transferred to an unresolved player");
          var payload =
              new NervePanelPayload(
                  body.getUniqueId(),
                  panel,
                  "PanelOwner",
                  true,
                  SubLevelPermissions.get(body).allowed(),
                  List.of());
          var buffer =
              new RegistryFriendlyByteBuf(
                  io.netty.buffer.Unpooled.buffer(), level.registryAccess());
          NervePanelPayload.CODEC.encode(buffer, payload);
          helper.assertTrue(
              NervePanelPayload.CODEC.decode(buffer).equals(payload),
              "Panel wire snapshot lost ownership/entries");
          buffer.release();
          var actionBuffer =
              new RegistryFriendlyByteBuf(
                  io.netty.buffer.Unpooled.buffer(), level.registryAccess());
          NervePanelAction.CODEC.encode(actionBuffer, add);
          helper.assertTrue(
              NervePanelAction.CODEC.decode(actionBuffer).equals(add),
              "Action wire snapshot lost target UUID");
          actionBuffer.release();
          level.removeBlock(panel, false);
          helper.assertTrue(
              !NervePanelControl.handle(
                  owner, new NervePanelAction(body.getUniqueId(), panel, 2, mob.getUUID())),
              "Removed panel still authorized edits");
          mob.discard();
          helper.succeed();
        });
  }
}
