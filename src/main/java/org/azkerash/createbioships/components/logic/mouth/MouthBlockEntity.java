package org.azkerash.createbioships.components.logic.mouth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.inventory.OrganInventory;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;

/** Food intake and Eldritch Maw wind-up. Digestion and acid ballistics remain separate. */
public final class MouthBlockEntity extends OrganInventory {
  public static final int CHEW_TICKS = 60;
  private final ChewingState detachedState = new ChewingState();
  private java.util.List<ItemStack> observed = java.util.List.of();
  private boolean observing;

  /** Only animation timing; inventory remains in the same grid-owned component entry. */
  private static final class ChewingState
      implements org.azkerash.createbioships.core.component.ComponentState {
    long until;
    private long acidReadyAt, acidReleaseAt, acidOpenUntil;
    private boolean acidPowered;
    private java.util.UUID acidActor;
    ItemStack food = ItemStack.EMPTY;

    public net.minecraft.nbt.CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
      var tag = new net.minecraft.nbt.CompoundTag();
      tag.putLong("ChewUntil", until);
      tag.putLong("AcidReadyAt", acidReadyAt);
      tag.putLong("AcidReleaseAt", acidReleaseAt);
      tag.putLong("AcidOpenUntil", acidOpenUntil);
      tag.putBoolean("AcidPowered", acidPowered);
      if (acidActor != null) {
        tag.putUUID("AcidActor", acidActor);
      }
      if (!food.isEmpty()) {
        tag.put("ChewFood", food.save(registries));
      }
      return tag;
    }

    public void load(
        net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      until = tag.getLong("ChewUntil");
      acidReadyAt = tag.getLong("AcidReadyAt");
      acidReleaseAt = tag.getLong("AcidReleaseAt");
      acidOpenUntil = tag.getLong("AcidOpenUntil");
      acidPowered = tag.getBoolean("AcidPowered");
      acidActor = tag.hasUUID("AcidActor") ? tag.getUUID("AcidActor") : null;
      food =
          tag.contains("ChewFood")
              ? ItemStack.parseOptional(registries, tag.getCompound("ChewFood"))
              : ItemStack.EMPTY;
    }
  }

  @Override
  protected org.azkerash.createbioships.core.component.ComponentState localState() {
    return detachedState;
  }

  private ChewingState chewing() {
    return (ChewingState) ownedState();
  }

  public int chewRemaining() {
    return level == null
        ? 0
        : (int) Math.clamp(chewing().until - level.getGameTime(), 0, CHEW_TICKS);
  }

  public boolean active() {
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
      return false;
    }
    var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(server, worldPosition);
    return body != null
        && org.azkerash.createbioships.core.SubLevelData.isLiving(body)
        && !org.azkerash.createbioships.core.SubLevelHealth.status(body).dead();
  }

  private static final int ACID_RECOVERY_POSE_TICKS = 6, ACID_CANCEL_COOLDOWN_TICKS = 10;
  private static final int ACID_POSE_INTERVAL_TICKS = 3;

  public MawAcidAttack.Result requestAcidAttack(java.util.UUID actor) {
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
      return MawAcidAttack.Result.INACTIVE;
    }
    var ready = MawAcidAttack.validate(server, worldPosition, actor);
    if (ready != MawAcidAttack.Result.READY) {
      return ready;
    }
    var state = chewing();
    if (state.acidReleaseAt > 0 || server.getGameTime() < state.acidReadyAt) {
      return MawAcidAttack.Result.BUSY;
    }
    state.acidReleaseAt = server.getGameTime() + MawAcidAttack.WINDUP_TICKS;
    state.acidActor = actor;
    setChanged();
    server.setBlock(
        worldPosition, getBlockState().setValue(MouthBlock.SPITTING, true), Block.UPDATE_CLIENTS);
    server.scheduleTick(worldPosition, getBlockState().getBlock(), ACID_POSE_INTERVAL_TICKS);
    var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(server, worldPosition);
    var point =
        body.logicalPose()
            .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition));
    server.playSound(
        null,
        point.x,
        point.y,
        point.z,
        net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
        net.minecraft.sounds.SoundSource.BLOCKS,
        .8f,
        .5f);
    return MawAcidAttack.Result.READY;
  }

  public void observeAcidSignal(boolean powered) {
    if (!MouthBlock.isArmored(getBlockState()) || level == null || level.isClientSide) {
      return;
    }
    var state = chewing();
    if (powered == state.acidPowered) {
      return;
    }
    state.acidPowered = powered;
    setChanged();
    if (powered) {
      requestAcidAttack(null);
    }
  }

  /**
   * Scheduled alongside the existing mouth animation; there is no new per-creature polling loop.
   */
  public boolean animateAcidAttack() {
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)
        || !MouthBlock.isArmored(getBlockState())) {
      return false;
    }
    var state = chewing();
    long now = server.getGameTime();
    if (state.acidReleaseAt > 0 && !active()) {
      state.acidReleaseAt = state.acidOpenUntil = 0;
      state.acidActor = null;
      setChanged();
    }
    if (state.acidReleaseAt > 0 && now >= state.acidReleaseAt) {
      var result = MawAcidAttack.release(server, worldPosition, state.acidActor);
      state.acidReleaseAt = 0;
      state.acidActor = null;
      state.acidReadyAt =
          now
              + (result == MawAcidAttack.Result.FIRED
                  ? MawAcidAttack.COOLDOWN_TICKS
                  : ACID_CANCEL_COOLDOWN_TICKS);
      state.acidOpenUntil =
          result == MawAcidAttack.Result.FIRED ? now + ACID_RECOVERY_POSE_TICKS : 0;
      setChanged();
    }
    boolean open = state.acidReleaseAt > 0 || now < state.acidOpenUntil;
    if (getBlockState().getValue(MouthBlock.SPITTING) != open) {
      server.setBlock(
          worldPosition, getBlockState().setValue(MouthBlock.SPITTING, open), Block.UPDATE_CLIENTS);
    }
    if (open) {
      server.scheduleTick(worldPosition, getBlockState().getBlock(), ACID_POSE_INTERVAL_TICKS);
    }
    return open;
  }

  private void remember() {
    observed = items().stream().map(ItemStack::copy).toList();
  }

  @Override
  public void onLoad() {
    super.onLoad();
    remember();
    if (level instanceof net.minecraft.server.level.ServerLevel server) {
      chewing().until = Math.min(chewing().until, server.getGameTime() + CHEW_TICKS);
      // Keep recovery across saves, but never resume an unseen, half-finished telegraph on load.
      chewing().acidReadyAt =
          Math.min(chewing().acidReadyAt, server.getGameTime() + MawAcidAttack.COOLDOWN_TICKS);
      chewing().acidReleaseAt = chewing().acidOpenUntil = 0;
      chewing().acidActor = null;
      server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
  }

  @Override
  public void setChanged() {
    super.setChanged();
    if (observing || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
      return;
    }
    observing = true;
    try {
      boolean added = false;
      for (var stack : items()) {
        if (!stack.isEmpty() && canPlaceItem(0, stack)) {
          int now =
              items().stream()
                  .filter(other -> ItemStack.isSameItemSameComponents(stack, other))
                  .mapToInt(ItemStack::getCount)
                  .sum();
          int before =
              observed.stream()
                  .filter(other -> ItemStack.isSameItemSameComponents(stack, other))
                  .mapToInt(ItemStack::getCount)
                  .sum();
          if (now > before) {
            added = true;
            chewing().food = stack.copyWithCount(1);
            break;
          }
        }
      }
      remember();
      if (added && active()) {
        boolean starting = chewRemaining() == 0;
        chewing().until = server.getGameTime() + CHEW_TICKS;
        super.setChanged();
        if (starting) {
          server.setBlock(
              worldPosition,
              getBlockState().setValue(MouthBlock.CHEW, 1).setValue(MouthBlock.BREATH, 0),
              Block.UPDATE_CLIENTS);
          MouthBlock.chewingSound(server, worldPosition);
        }
        server.scheduleTick(worldPosition, getBlockState().getBlock(), 3);
      }
    } finally {
      observing = false;
    }
  }

  /** One cosmetic droplet at the lower lip, transformed with the physical creature. */
  public boolean salivaParticles() {
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)
        || !MouthBlock.isArmored(getBlockState())
        || !active()) {
      return false;
    }
    var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(server, worldPosition);
    if (body == null) {
      return false;
    }
    var face = getBlockState().getValue(MouthBlock.FACING);
    var chin =
        face == Direction.UP
            ? Direction.SOUTH
            : face == Direction.DOWN ? Direction.NORTH : Direction.DOWN;
    var at =
        body.logicalPose()
            .transformPosition(
                net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)
                    .relative(face, .69)
                    .relative(chin, .3));
    server.sendParticles(
        net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,
        at.x,
        at.y,
        at.z,
        1,
        .035,
        .005,
        .035,
        0);
    return true;
  }

  public void chewingParticles() {
    if (!(level instanceof net.minecraft.server.level.ServerLevel server)
        || chewing().food.isEmpty()
        || !active()) {
      return;
    }
    var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(server, worldPosition);
    if (body == null) {
      return;
    }
    var face = getBlockState().getValue(MouthBlock.FACING);
    var logical = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).relative(face, .65);
    var at = body.logicalPose().transformPosition(logical);
    server.sendParticles(
        new net.minecraft.core.particles.ItemParticleOption(
            net.minecraft.core.particles.ParticleTypes.ITEM, chewing().food),
        at.x,
        at.y,
        at.z,
        3,
        .10,
        .10,
        .10,
        .025);
  }

  @Override
  protected void saveAdditional(
      net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.merge(chewing().save(registries));
  }

  @Override
  protected void loadAdditional(
      net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    chewing().load(tag, registries);
    remember();
  }

  public MouthBlockEntity(BlockPos pos, BlockState state) {
    super(ComponentRegistry.MOUTH_ENTITY.get(), pos, state, 9);
  }

  public static boolean accepts(net.minecraft.world.level.Level level, ItemStack stack) {
    return stack.is(StomachBlockEntity.paste()) || StomachBlockEntity.accepts(level, stack);
  }

  public boolean canPlaceItem(int slot, ItemStack stack) {
    return level != null && accepts(level, stack);
  }

  public ItemStack feed(ItemStack stack, boolean simulate) {
    return canPlaceItem(0, stack) ? insert(stack, 0, 9, simulate) : stack.copy();
  }

  public Component getDisplayName() {
    return Component.translatable(getBlockState().getBlock().getDescriptionId());
  }

  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new MouthMenu(id, inventory, this);
  }
}
