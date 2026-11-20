package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.Azkerash.create_bioships.components.logic.eye.*;

/** Reusable component weak point; front-facing eye hits share one grid-health pool. */
public class EyeCombatHurtbox extends SubLevelCombatTarget {
  private CustomHitboxes.Hitbox hitbox;

  /** Latest server geometry snapshot; null until this target has been positioned. */
  public CustomHitboxes.Hitbox getCombatHitbox() {
    return hitbox;
  }

  private static final net.minecraft.network.syncher.EntityDataAccessor<
          java.util.Optional<java.util.UUID>>
      GRID =
          net.minecraft.network.syncher.SynchedEntityData.defineId(
              EyeCombatHurtbox.class,
              net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_UUID);
  private static final net.minecraft.network.syncher.EntityDataAccessor<BlockPos> CELL =
      net.minecraft.network.syncher.SynchedEntityData.defineId(
          EyeCombatHurtbox.class, net.minecraft.network.syncher.EntityDataSerializers.BLOCK_POS);

  @Override
  protected void defineSynchedData(
      net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(GRID, java.util.Optional.empty());
    builder.define(CELL, BlockPos.ZERO);
  }

  public java.util.Optional<java.util.UUID> gridId() {
    return entityData.get(GRID);
  }

  public BlockPos cell() {
    return entityData.get(CELL);
  }

  public EyeCombatHurtbox(EntityType<? extends EyeCombatHurtbox> type, Level level) {
    super(type, level);
  }

  public void bindEye(ServerSubLevel body, BlockPos cell) {
    this.setImpactCell(cell);
    entityData.set(GRID, java.util.Optional.of(body.getUniqueId()));
    entityData.set(CELL, cell);
    bind(body);
  }

  protected boolean accepts(BlockState state) {
    return EyeWoundBehavior.isEye(state);
  }

  protected Direction facing(ServerSubLevel body, BlockState state) {
    return state.getValue(BlockStateProperties.FACING);
  }

  @Override
  protected double damageMultiplier() {
    return EyeWoundBehavior.DAMAGE_MULTIPLIER;
  }

  @Override
  protected void position(ServerSubLevel body) {
    if (this.getImpactCell() == null) {
      return;
    }
    var state = body.getLevel().getBlockState(this.getImpactCell());
    if (!accepts(state)) {
      discard();
      return;
    }
    this.setImpactFace(facing(body, state));
    var local =
        state.getShape(body.getLevel(), this.getImpactCell()).bounds().move(this.getImpactCell());
    var center = local.getCenter();
    double extent =
        switch (this.getImpactFace().getAxis()) {
          case X -> local.getXsize();
          case Y -> local.getYsize();
          case Z -> local.getZsize();
        };
    focusForAttack(
        body.logicalPose()
            .transformPosition(center.relative(this.getImpactFace(), extent / 2 + .02)));
    hitbox = CustomHitboxes.create(body, local);
    setBoundingBox(hitbox.getBounds());
  }
}
