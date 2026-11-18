package org.Azkerash.create_bioships.compat.biomancy;

import com.github.elenterius.biomancy.api.serum.Serum;
import com.github.elenterius.biomancy.init.ModSoundEvents;
import com.github.elenterius.biomancy.item.injector.InjectorItem;
import java.util.function.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Only injector mechanics: components supply their own gene acceptance and mutation. */
public final class BioInjectorIntegration {
  private BioInjectorIntegration() {}

  public static ItemInteractionResult inject(
      Level level,
      Player player,
      ItemStack stack,
      Predicate<Serum> accepts,
      Consumer<Serum> apply) {
    if (!(stack.getItem() instanceof InjectorItem injector))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    var serum = injector.getSerum(stack);
    if (player.getCooldowns().isOnCooldown(injector) || serum.isEmpty() || !accepts.test(serum))
      return ItemInteractionResult.FAIL;
    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
    apply.accept(serum);
    injector.consumeSerum(stack, player);
    injector.setInjectionSuccess(stack, true);
    player.getCooldowns().addCooldown(injector, InjectorItem.COOL_DOWN_TICKS);
    level.playSound(
        null,
        player.blockPosition(),
        ModSoundEvents.INJECTOR_INJECT.get(),
        net.minecraft.sounds.SoundSource.PLAYERS,
        1,
        1);
    if (player instanceof ServerPlayer server
        && server.connection.getConnection().channel() != null)
      injector.triggerAnim(
          player,
          software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, (ServerLevel) level),
          "main",
          "inject");
    return ItemInteractionResult.CONSUME;
  }
}
