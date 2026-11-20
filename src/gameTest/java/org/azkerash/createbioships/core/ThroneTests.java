package org.azkerash.createbioships.core;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ThroneTests {
  @GameTest(template = "test_empty", batch = "throne_furniture", timeoutTicks = 100)
  public static void throne_places_two_waterlogged_halves_sits_and_removes_as_one_item(
      GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 3, 2));
    l.setBlock(p.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(p.above(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    var profile = new GameProfile(UUID.randomUUID(), "ThroneTester");
    var player = new ServerPlayer(l.getServer(), l, profile, ClientInformation.createDefault());
    player.connection = new net.neoforged.neoforge.common.util.FakePlayer(l, profile).connection;
    player.setPos(Vec3.atCenterOf(p).add(0, 0, 2));
    player.setItemInHand(
        InteractionHand.MAIN_HAND,
        new ItemStack(ComponentRegistry.THRONE_OF_AZKERASH.get().asItem(), 1));
    var floorHit =
        new BlockHitResult(
            Vec3.atCenterOf(p.below()).add(0, .5, 0), Direction.UP, p.below(), false);
    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, floorHit));
    var lower = l.getBlockState(p);
    var upper = l.getBlockState(p.above());
    h.assertTrue(
        lower.is(ComponentRegistry.THRONE_OF_AZKERASH.get())
            && upper.is(lower.getBlock())
            && lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
            && upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER,
        "Throne did not place two linked halves");
    h.assertTrue(
        lower.getValue(BlockStateProperties.WATERLOGGED)
            && upper.getValue(BlockStateProperties.WATERLOGGED),
        "Throne lost waterlogging");
    h.assertTrue(
        Block.getDrops(lower, l, p, null, player, ItemStack.EMPTY).size() == 1
            && Block.getDrops(upper, l, p.above(), null, player, ItemStack.EMPTY).isEmpty(),
        "Throne halves duplicate loot");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.gameMode.useItemOn(
        player,
        l,
        ItemStack.EMPTY,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(p.above()), Direction.SOUTH, p.above(), false));
    h.assertTrue(
        player.getVehicle() instanceof SeatEntity,
        "Upper-half interaction did not use native Create seating");
    player.stopRiding();
    player.gameMode.destroyBlock(p.above());
    h.assertTrue(
        !l.getBlockState(p).is(lower.getBlock())
            && !l.getBlockState(p.above()).is(lower.getBlock()),
        "Breaking upper throne stranded lower half");
    h.assertTrue(
        l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER),
        "Removing throne deleted the water it held");
    h.assertTrue(
        l.getRecipeManager()
            .byKey(
                net.minecraft.resources.ResourceLocation.parse(
                    "create_bioships:throne_of_azkerash"))
            .isPresent(),
        "Throne recipe missing");
    h.succeed();
  }
}
