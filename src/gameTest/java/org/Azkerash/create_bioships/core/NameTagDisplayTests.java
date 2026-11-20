package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.compat.biomancy.ConstructMembranePassage;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.nametag.NameTagDisplayEntity;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.core.naming.SubLevelNames;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class NameTagDisplayTests {
  @GameTest(template = "test_empty", batch = "name_tag_display", timeoutTicks = 100)
  public static void underside_label_survives_assembly_and_names_construct(GameTestHelper h) {
    var l = h.getLevel();
    var p = new BlockPos(8100, 90, 128);
    l.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    l.setBlock(
        p.below(),
        ComponentRegistry.NAME_TAG_DISPLAYER
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.DOWN)
            .setValue(BlockStateProperties.WATERLOGGED, true),
        3);
    var panel = (NameTagDisplayEntity) l.getBlockEntity(p.below());
    panel.setItem(0, new ItemStack(Items.DIAMOND));
    h.assertTrue(panel.isEmpty(), "Panel accepted an unrelated item");
    panel.setItem(0, new ItemStack(Items.NAME_TAG));
    h.assertTrue(panel.isEmpty(), "Panel accepted an unnamed tag");
    var tag = new ItemStack(Items.NAME_TAG);
    tag.set(DataComponents.CUSTOM_NAME, Component.literal("Junior test"));
    panel.setItem(0, tag);
    panel.setOffset(-16, 96, -32);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p, List.of(p, p.below()), new BoundingBox3i(p.below(), p));
    var c = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, c);
    DevelopmentLifeSupport.pause(body);
    var moved = (NameTagDisplayEntity) l.getBlockEntity(c.below());
    h.assertTrue(
        moved != null
            && moved.label().getString().equals("Junior test")
            && moved.offset().y == 96
            && moved.offset().x == -16,
        "Assembly lost tag or hologram offsets");
    h.assertTrue(
        l.getBlockState(c.below()).getValue(BlockStateProperties.WATERLOGGED),
        "Assembly lost waterlogging");
    h.assertTrue(SubLevelNames.name(body).equals("Junior test"), "Panel did not name construct");
    var owner = UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(body, owner);
    var ownerPlayer =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(owner, "LabelOwner"));
    var stranger =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Stranger"));
    h.assertTrue(
        moved.editable(ownerPlayer) && !moved.editable(stranger),
        "Panel editing did not enforce ownership");

    var nbt = moved.saveWithoutMetadata(l.registryAccess());
    var reloaded = new NameTagDisplayEntity(c.below(), moved.getBlockState());
    reloaded.loadWithComponents(nbt, l.registryAccess());
    h.assertTrue(
        reloaded.offset().z == -32 && reloaded.label().equals(moved.label()),
        "Panel data did not survive serialization");
    moved.clearContent();
    h.assertTrue(SubLevelNames.name(body).isEmpty(), "Removing tag retained managed name");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "construct_passage", timeoutTicks = 100)
  public static void construct_passage_expires_outside_bounds_without_granting_player_access(
      GameTestHelper h) {
    var l = h.getLevel();
    var bodies = new ArrayList<dev.ryanhcode.sable.sublevel.ServerSubLevel>();
    for (int i = 0; i < 2; i++) {
      var p = new BlockPos(8150 + i * 20, 90, 128);
      l.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
      l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), 3);
      l.setBlock(p.above(), Blocks.STONE.defaultBlockState(), 3);
      var body =
          SubLevelAssemblyHelper.assembleBlocks(
              l, p, List.of(p, p.above()), new BoundingBox3i(p, p.above()));
      CoreAssemblyRules.mark(l, body.getPlot().getCenterBlock());
      DevelopmentLifeSupport.pause(body);
      bodies.add(body);
    }
    var host = bodies.get(0);
    var craft = bodies.get(1);
    craft.setName("Passenger craft");
    var owner = UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(host, owner);
    h.assertTrue(
        !SubLevelPermissions.addConstruct(host, UUID.randomUUID(), craft),
        "Stranger registered craft");
    h.assertTrue(
        SubLevelPermissions.addConstruct(host, owner, craft),
        "Owner could not register named craft");
    SubLevelPermissions.forget(host);
    h.assertTrue(ConstructMembranePassage.registered(host, craft), "Registration did not persist");
    var mob = EntityType.PIG.create(l);
    var c = craft.getPlot().getCenterBlock();
    var inside = craft.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(c));
    mob.setPos(inside.x, inside.y, inside.z);
    h.assertTrue(
        ConstructMembranePassage.passenger(host, mob), "Contained mob did not inherit passage");
    h.assertTrue(
        !SubLevelPermissions.get(host).permits(mob.getUUID()),
        "Passenger received permanent permission");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Passenger"));
    player.setPos(inside.x, inside.y, inside.z);
    h.assertTrue(
        ConstructMembranePassage.passenger(host, player)
            && !SubLevelPermissions.get(host).permits(player.getUUID()),
        "Player passage granted persistent access or rejected containment");
    player.setPos(inside.x, inside.y + 10, inside.z);
    h.assertTrue(
        !ConstructMembranePassage.passenger(host, player), "Player above hull retained passage");
    mob.setPos(inside.x + 10, inside.y, inside.z);
    h.assertTrue(
        !ConstructMembranePassage.passenger(host, mob), "Outside passenger retained passage");
    SubLevelPermissions.remove(host, owner, craft.getUniqueId());
    mob.setPos(inside.x, inside.y, inside.z);
    h.assertTrue(
        !ConstructMembranePassage.passenger(host, mob), "Revoked craft retained passenger passage");
    h.succeed();
  }
}
