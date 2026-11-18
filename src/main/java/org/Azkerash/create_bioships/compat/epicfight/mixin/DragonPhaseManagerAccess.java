package org.Azkerash.create_bioships.compat.epicfight.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderDragonPhaseManager.class)
public interface DragonPhaseManagerAccess {
  @Accessor("dragon")
  EnderDragon core$dragon();
}
