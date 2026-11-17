package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.disabled.wip.EngineRoom;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;

public class BioshipWrenchItem extends Item {
  public BioshipWrenchItem(Properties properties) {
    super(properties);
  }

  public static boolean movable(net.minecraft.world.level.Level level, BlockPos pos) {
    var state = level.getBlockState(pos);
    if (BioshipTissue.isTissue(state)
        || net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .toString()
            .equals("biomancy:biometric_membrane")) return false;
    return state.is(Create_bioships.SURFACE_EYE.get())
        || state.is(Create_bioships.FEEDING_MOUTH.get())
        || state.is(Create_bioships.STOMACH.get())
        || net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .getNamespace()
                .equals("biomancy")
            && level.getBlockEntity(pos) != null;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    if (player == null) return InteractionResult.PASS;
    var level = context.getLevel();
    if (level.isClientSide) return InteractionResult.SUCCESS;
    var pos = context.getClickedPos();
    var core = BioshipAccess.core(level, pos);
    if (core == null || !player.getUUID().equals(core.creator())) {
      player.displayClientMessage(
          Component.literal("Only the bioship's creator can move its organs."), true);
      return InteractionResult.FAIL;
    }
    if (core.body().dead() || core.blueprint().busy()) return InteractionResult.FAIL;
    var stack = context.getItemInHand();
    var state = level.getBlockState(pos);
    if (player.isShiftKeyDown() && movable(level, pos)) {
      if (state.hasProperty(BlockStateProperties.FACING)) {
        var face = state.getValue(BlockStateProperties.FACING);
        Direction next = Direction.values()[(face.ordinal() + 1) % 6];
        level.setBlock(pos, state.setValue(BlockStateProperties.FACING, next), 3);
        return InteractionResult.SUCCESS;
      }
      if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
        level.setBlock(
            pos,
            state.setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise()),
            3);
        return InteractionResult.SUCCESS;
      }
      player.displayClientMessage(
          Component.literal("This Biomancy machine has no rotatable model."), true);
      return InteractionResult.SUCCESS;
    }
    var selection = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (selection.contains("BioshipSource")) {
      var source = BlockPos.of(selection.getLong("BioshipSource"));
      if (!selection.hasUUID("Bioship")
          || !selection.getUUID("Bioship").equals(core.ship().getUniqueId())
          || BioshipAccess.core(level, source) != core
          || !movable(level, source)) {
        stack.remove(DataComponents.CUSTOM_DATA);
        return InteractionResult.FAIL;
      }
      var target = BioshipTissue.isTissue(state) ? pos : pos.relative(context.getClickedFace());
      if (!level.getBlockState(source).is(Create_bioships.SURFACE_EYE.get())
          && !EngineRoom.inside(core, target)) {
        player.displayClientMessage(
            Component.literal("Place organs inside the engine room."), true);
        return InteractionResult.FAIL;
      }
      boolean mouth = level.getBlockState(source).is(Create_bioships.FEEDING_MOUTH.get());
      if (ComponentTransport.begin(core.amoebaComponents(), source, target, false, true)) {
        if (mouth) core.preferredMouth(target.subtract(core.getBlockPos()));
        stack.remove(DataComponents.CUSTOM_DATA);
        player.displayClientMessage(
            Component.literal("The organ moves through the living hull."), true);
        return InteractionResult.SUCCESS;
      }
      return InteractionResult.FAIL;
    }
    if (!movable(level, pos)) return InteractionResult.PASS;
    selection.putLong("BioshipSource", pos.asLong());
    selection.putUUID("Bioship", core.ship().getUniqueId());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(selection));
    player.displayClientMessage(
        Component.literal("Selected. Click a destination; sneak-click rotates."), true);
    return InteractionResult.SUCCESS;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(
      net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
    var stack = player.getItemInHand(hand);
    if (player.isShiftKeyDown()) {
      stack.remove(DataComponents.CUSTOM_DATA);
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    return InteractionResultHolder.pass(stack);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
    lines.add(Component.literal("Use twice: move organ | Sneak-use: rotate"));
    lines.add(Component.literal("Sneak-use in air: clear selection"));
  }
}
