package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

public class LivingCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
  private static final TagKey<Item> MEAT =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "biomass"));
  private static final TagKey<Item> LEVITITE =
      TagKey.create(
          Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "levitite_amalgam"));
  private BlockPos preferredMouth;

  public BlockPos preferredMouth() {
    return preferredMouth;
  }

  public void preferredMouth(BlockPos pos) {
    preferredMouth = pos;
    setChanged();
  }

  private OrganMotion.Move organMove;

  public OrganMotion.Move organMove() {
    return organMove;
  }

  public void organMove(OrganMotion.Move move) {
    organMove = move;
    setChanged();
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  @Override
  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveWithoutMetadata(registries);
  }

  private final BioshipNeeds needs = new BioshipNeeds();
  private boolean awakened, resting;
  private java.util.UUID captain;
  private final BioshipAccess access = new BioshipAccess();

  public java.util.UUID creator() {
    return captain;
  }

  public void claimCreator(java.util.UUID owner) {
    if (captain == null) {
      captain = owner;
      setChanged();
    }
  }

  public BioshipAccess access() {
    return access;
  }

  private int bodyBlocks = 1;
  private int stage, growthIndex, metabolismTicks;
  private long lastServerTick = Long.MIN_VALUE;
  private double hoverHeight = Double.NaN;
  private net.minecraft.world.entity.item.ItemEntity foodTarget;
  private volatile FlightIntent flight = new FlightIntent(false, Vec3.ZERO, 0);

  private record FlightIntent(
      boolean lift, Vec3 velocity, double turn, double supportHeight, Vec3 stanceUp) {
    FlightIntent(boolean lift, Vec3 velocity, double turn) {
      this(lift, velocity, turn, Double.NaN, new Vec3(0, 1, 0));
    }
  }

  public LivingCoreBlockEntity(BlockPos pos, BlockState state) {
    super(Create_bioships.CORE_ENTITY.get(), pos, state);
  }

  ServerSubLevel ship() {
    if (!(level instanceof ServerLevel server)) return null;
    var container = SubLevelContainer.getContainer(server);
    if (container == null || !container.inBounds(worldPosition)) return null;
    var plot = container.getPlot(new ChunkPos(worldPosition));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel ship ? ship : null;
  }

  boolean primary(ServerSubLevel ship) {
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity other
          && other != this
          && other.getBlockPos().compareTo(worldPosition) < 0) return false;
    return true;
  }

  public void interact(Player player, ItemStack stack) {
    ServerSubLevel ship = ship();
    if (body.dead()) {
      message(player, "message.create_bioships.dead");
      return;
    }
    if (ship == null) {
      message(player, "message.create_bioships.dormant");
      return;
    }
    if (!primary(ship)) {
      message(player, "message.create_bioships.secondary");
      return;
    }
    if (!stack.isEmpty()) {
      message(player, "message.create_bioships.feed_mouth");
      return;
    }
    if (!player.isShiftKeyDown() && stage == 2) {
      if (aggressive()) {
        message(player, "message.create_bioships.refuses_rest");
        return;
      }
      resting = !resting;
      setChanged();
      message(
          player,
          resting ? "message.create_bioships.resting" : "message.create_bioships.controls_enabled");
      return;
    }
    message(player, "message.create_bioships.inspect");
  }

  private int goggleFlesh, goggleBone, goggleFiber;

  public java.util.List<Component> goggleTooltip() {
    var lines = new java.util.ArrayList<Component>();
    lines.add(Component.literal(body.dead() ? "Bioship remains" : "Living Bioship"));
    if (body.dead()) {
      lines.add(
          Component.literal("Dead. The wreck is rotting; useful components can be reclaimed."));
      return lines;
    }
    lines.add(
        Component.translatable("stage.create_bioships." + stage)
            .append(" | ")
            .append(Component.translatable("mood.create_bioships." + needs.mood())));
    lines.add(Component.literal("Growth biomass: " + needs.biomass() + " / 64"));
    lines.add(Component.literal("Stored flesh: " + goggleFlesh));
    lines.add(
        Component.literal(
            "Frame: "
                + goggleBone
                + " / 8 bone, "
                + goggleFiber
                + " / 12 fibers"
                + (framePaid ? " (grown)" : "")));
    lines.add(Component.literal("Amalgam: " + needs.levitite() + " / 8"));
    lines.add(Component.literal("Food: " + needs.satiety() + " / 1200 | Trust: " + needs.trust()));
    if (stage == 0)
      lines.add(
          Component.literal(
              "Growth needs open space: 9 wide, 21 long and 8 above the core. Feed through the"
                  + " connected mouth and stomach."));
    if (stage == 0)
      lines.add(
          Component.literal(
              "Stomach formation needs both a Decomposer and a Digester. Assimilate a Bio-Forge for"
                  + " the feeding maw."));
    if (crawl.walking()) lines.add(Component.literal("Walking to a clear growth site."));
    if (blueprint.busy()) lines.add(Component.literal(blueprint.status()));
    return lines;
  }

  public boolean feedAtMouth(Player player, ItemStack stack) {
    if (body.dead() || ship() == null || !primary(ship()) || !hasMouth()) return false;
    var stomach = DigestiveSystem.stomach(this);
    if (stomach == null || !stomach.connected(this)) {
      message(player, "message.create_bioships.need_stomach");
      return false;
    }
    boolean accepted = false;
    if (stack.is(LEVITITE)) accepted = needs.feedLevitite();
    else if (stack.is(MEAT)) accepted = DigestiveSystem.offerFood(this, stack, false);
    else if (stack.is(BioshipIntegration.constructionMaterial())) {
      stomach.retain(stack.copyWithCount(1));
      accepted = true;
    } else accepted = DigestiveSystem.offerFood(this, stack, false);
    if (!accepted) return false;
    if (captain == null) captain = player.getUUID();
    ItemStack remainder = stack.getCraftingRemainingItem();
    stack.shrink(1);
    if (!remainder.isEmpty() && !player.getInventory().add(remainder))
      player.drop(remainder, false);
    setChanged();
    level.playSound(
        null,
        worldPosition,
        net.minecraft.sounds.SoundEvents.GENERIC_EAT,
        net.minecraft.sounds.SoundSource.BLOCKS,
        0.8f,
        0.65f);
    return true;
  }

  public static boolean isMeat(ItemStack stack) {
    return stack.is(MEAT);
  }

  public void digestMeal(ItemStack stack) {
    if (stack.is(MEAT)) {
      boolean hunted =
          stack
              .getOrDefault(
                  net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                  net.minecraft.world.item.component.CustomData.EMPTY)
              .copyTag()
              .getBoolean("BioshipHunted");
      if (hunted) needs.consumeHuntedPrey(stack.is(Items.ROTTEN_FLESH));
      else {
        needs.feedMeat(stack.is(Items.ROTTEN_FLESH) ? 0 : 70, true);
        if (stack.is(Items.ROTTEN_FLESH)) needs.poison();
      }
      setChanged();
    }
  }

  public boolean acceptAmalgam(ItemStack stack) {
    if (!stack.is(LEVITITE)) return false;
    boolean accepted = needs.feedLevitite();
    if (accepted) setChanged();
    return accepted;
  }

  private final BioshipScavenging scavenging = new BioshipScavenging();

  public BioshipScavenging scavenging() {
    return scavenging;
  }

  private final BioshipCrawl crawl = new BioshipCrawl();

  public BioshipCrawl crawl() {
    return crawl;
  }

  private final BioshipBody body = new BioshipBody();
  private final BioshipBlueprint blueprint = new BioshipBlueprint();

  public BioshipBody body() {
    return body;
  }

  public BioshipBlueprint blueprint() {
    return blueprint;
  }

  public BlockPos layoutPos(BlockPos offset) {
    return worldPosition.offset(blueprint.offset(offset));
  }

  private boolean framePaid, larynxDeveloped;

  public boolean wantsStoredFood() {
    return hungry()
        || needs.biomass() < BioshipNeeds.BIOMASS_REQUIRED
        || !framePaid
        || !larynxDeveloped;
  }

  private boolean prepareFrame() {
    if (framePaid) return true;
    var stomach = DigestiveSystem.stomach(this);
    if (stomach == null
        || !stomach.connected(this)
        || stomach.material("biomancy:bone_fragments") < 8
        || stomach.material("biomancy:elastic_fibers") < 12) return false;
    stomach.spend("biomancy:bone_fragments", 8);
    stomach.spend("biomancy:elastic_fibers", 12);
    framePaid = true;
    setChanged();
    return true;
  }

  private void developLarynx(ServerSubLevel ship) {
    var stomach = DigestiveSystem.stomach(this);
    if (stage != 2
        || larynxDeveloped
        || stomach == null
        || stomach.meals() < 8
        || stomach.material("biomancy:flesh_bits") < 8
        || stomach.material("biomancy:bone_fragments") < 2
        || stomach.material("biomancy:elastic_fibers") < 4) return;
    BlockPos target = layoutPos(DinghyGrowth.LARYNX);
    if (!clearForGrowth(ship, target)) return;
    var larynx =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("biomancy", "modular_larynx"));
    if (level.setBlock(target, larynx.defaultBlockState(), 3)) {
      stomach.spend("biomancy:flesh_bits", 8);
      stomach.spend("biomancy:bone_fragments", 2);
      stomach.spend("biomancy:elastic_fibers", 4);
      larynxDeveloped = true;
      setChanged();
    }
  }

  public int incorporateBiomass(int amount) {
    int accepted = needs.incorporateBiomass(amount);
    if (accepted > 0) setChanged();
    return accepted;
  }

  public int missingBiomass() {
    return BioshipNeeds.BIOMASS_REQUIRED - needs.biomass();
  }

  private static void message(Player player, String key, Object... args) {
    player.displayClientMessage(Component.translatable(key, args), true);
  }

  public void mistreat() {
    if (ship() != null) {
      needs.mistreat();
      setChanged();
    }
  }

  private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos) {
    if (!ship.getPlot().contains(new ChunkPos(pos))
        || !level.isInWorldBounds(pos)
        || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
        || !level.getBlockState(pos).isAir()) return false;
    Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(pos));
    return level.getBlockState(BlockPos.containing(physical)).isAir();
  }

  private boolean roomToGrow(ServerSubLevel ship) {
    // The oval hull spans neighboring plot chunks. Allocate only its own empty plot cells.
    for (var part : DinghyGrowth.body()) {
      var chunk = new ChunkPos(worldPosition.offset(part.offset()));
      if (ship.getPlot().contains(chunk)
          && ship.getPlot().getChunkHolder(ship.getPlot().toLocal(chunk)) == null)
        ship.getPlot().newEmptyChunk(chunk);
    }
    for (var part : DinghyGrowth.body())
      if (!level.getBlockState(worldPosition.offset(part.offset())).equals(part.state())
          && !BioshipIntegration.isIntegratedMachine(level, worldPosition.offset(part.offset()))
          && !clearForGrowth(ship, worldPosition.offset(part.offset()))) return false;
    return true;
  }

  public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core) {
    if (level.isClientSide) return;
    if (core.body.dead()) {
      core.updateEye(false);
      core.flight = new FlightIntent(false, Vec3.ZERO, 0);
      if (core.ship() != null) BioshipDecay.mark(core.ship());
      BioshipAccess.tick(core);
      return;
    }
    if (core.ship() != null)
      core.crawl.tick(
          core, core.stage < 2 && core.needs.readyToGrow() && !core.roomToGrow(core.ship()));
    core.body.tick(core);
    if (core.blueprint.busy()) {
      core.serverTick();
      core.blueprint.tick(core);
      BioshipAccess.tick(core);
      return;
    }
    var before = BioshipBody.actual(core);
    OrganMotion.tick(core);
    core.serverTick();
    var ship = core.ship();
    if (ship != null && core.primary(ship) && level.getGameTime() % 20 == 0)
      BioshipIntegration.assimilate(core, ship);
    core.updateCabinLights();
    if (ship != null && level.getGameTime() % 20 == 0) DigestiveSystem.connect(core, ship);
    core.growBody();
    if (ship != null && core.isMature() && level.getGameTime() % 20 == 0) {
      EngineRoom.arrange(core, ship);
      BioshipIntegration.relocateMouth(core, ship);
      DigestiveSystem.connect(core, ship);
      core.developLarynx(ship);
      BioshipAccess.tick(core);
      var panel = core.layoutPos(new BlockPos(-3, 2, 2));
      if (level.getBlockState(panel).isAir()
          || level.getBlockState(panel).is(Create_bioships.FLESH.get()))
        level.setBlock(panel, Create_bioships.BLUEPRINT_PANEL.get().defaultBlockState(), 3);
    }
    core.body.reconcile(core, before);
  }

  private void updateCabinLights() {
    if (stage != 2) return;
    for (BlockPos offset : DinghyGrowth.CABIN_LIGHTS) {
      BlockPos pos = layoutPos(offset);
      if (level.getBlockState(pos).is(Create_bioships.PULSE_LIGHT.get())
          && DinghyGrowth.cabinLantern().canSurvive(level, pos))
        level.setBlock(pos, DinghyGrowth.cabinLantern(), 3);
    }
  }

  // New actor block entities are created outside Sable's actor iteration.
  private void growBody() {
    if (body.health() <= 0) return;
    ServerSubLevel ship = ship();
    if (ship == null || !primary(ship)) return;
    if (stage == 0
        && needs.readyToGrow()
        && level.getGameTime() % 20 == 0
        && roomToGrow(ship)
        && prepareFrame()) {
      stage = 1;
      setChanged();
    }
    if (stage != 1) return;
    var parts = DinghyGrowth.body();
    if (growthIndex < parts.size()) {
      var part = parts.get(growthIndex);
      BlockPos target = worldPosition.offset(part.offset());
      boolean placed = BioshipIntegration.isIntegratedMachine(level, target);
      if (placed) {
        if (level.getBlockState(target).is(Create_bioships.INTESTINE.get())
            && part.state().is(Create_bioships.FLESH.get()))
          level.setBlock(
              target, level.getBlockState(target).setValue(IntestineBlock.ENCASED, true), 3);
      } else if (part.state().getBlock() instanceof LivingDoorBlock) {
        var half = part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF);
        if (half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
            && level.getBlockState(target).equals(part.state())) placed = true;
        else if (half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
            && clearForGrowth(ship, target)
            && clearForGrowth(ship, target.above())) {
          // Install both halves before notifying neighbors, so neither half loses its partner.
          level.setBlock(target, part.state(), 18);
          level.setBlock(
              target.above(),
              part.state()
                  .setValue(
                      net.minecraft.world.level.block.DoorBlock.HALF,
                      net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
              18);
          level.updateNeighborsAt(target, Create_bioships.DOOR.get());
          level.updateNeighborsAt(target.above(), Create_bioships.DOOR.get());
          placed = true;
        }
      } else if (clearForGrowth(ship, target)) placed = level.setBlock(target, part.state(), 3);
      if (placed) {
        if (level.getBlockEntity(target) instanceof SteeringWheelBlockEntity helm) {
          helm.material = net.minecraft.world.level.block.Blocks.CRIMSON_PLANKS.defaultBlockState();
          helm.notifyUpdate();
        }
        growthIndex++;
        setChanged();
      }
    }
    if (growthIndex == parts.size()) {
      stage = 2;
      setChanged();
    }
  }

  @Override
  public void sable$tick(ServerSubLevel ship) {
    serverTick();
  }

  private void serverTick() {
    if (level == null || level.getGameTime() == lastServerTick) return;
    lastServerTick = level.getGameTime();
    ServerSubLevel ship = ship();
    boolean alive = ship != null && primary(ship) && !body.dead();
    updateEye(alive);
    if (!alive) {
      flight = new FlightIntent(false, Vec3.ZERO, 0);
      return;
    }
    BioshipIntegration.bind(ship);
    if (!awakened) {
      awakened = true;
      ship.setName("Living Bioship");
      setChanged();
    }
    if (++metabolismTicks >= 20) {
      metabolismTicks = 0;
      bodyBlocks = BioshipIntegration.countBlocks(ship);
      needs.metabolize(bodyBlocks);
      if (needs.satiety() == 0) {
        var stomach = DigestiveSystem.stomach(this);
        int energy = 0, demand = BioshipNeeds.foodDemand(bodyBlocks);
        while (energy < demand) {
          int next = stomach == null ? blueprint.burnBufferedReserve() : stomach.burnReserve();
          if (next == 0) break;
          energy += next;
        }
        if (energy > 0) needs.emergencyEnergy(energy);
        if (energy < demand) body.starve(this, demand - energy);
      }
      if (body.dead()) {
        flight = new FlightIntent(false, Vec3.ZERO, 0);
        updateEye(false);
        return;
      }
      setChanged();
    }
    if (blueprint.busy()) {
      var travel = blueprint.flight(this);
      flight = new FlightIntent(canFly(), travel == null ? Vec3.ZERO : travel, 0);
      return;
    }
    if (crawl.walking() || crawl.bracing()) {
      flight =
          new FlightIntent(false, crawl.velocity(this), 0, crawl.supportHeight(), crawl.stanceUp());
      return;
    }
    Vec3 desired = Vec3.ZERO;
    double turn = 0;
    boolean heightCommanded = false;
    if (canFly() && !resting()) {
      BlockState throttleState = level.getBlockState(layoutPos(DinghyGrowth.THROTTLE));
      double throttle =
          throttleState.is(Create_bioships.THROTTLE.get())
              ? LivingThrottleBlock.signed(throttleState)
              : 0;
      if (level.getBlockEntity(layoutPos(DinghyGrowth.HELM))
          instanceof SteeringWheelBlockEntity helm)
        turn = Math.clamp(-helm.getAngle() / 180.0, -1, 1);
      BlockState trimState = level.getBlockState(layoutPos(DinghyGrowth.VERTICAL_TRIM));
      double vertical =
          trimState.is(Create_bioships.VERTICAL_TRIM.get())
              ? LivingThrottleBlock.signed(trimState)
              : 0;
      heightCommanded = vertical != 0;
      Vec3 local = new Vec3(0, vertical * 1.5, -3 * throttle);
      if (local.lengthSqr() > 9) local = local.normalize().scale(3);
      double response = needs.obedience();
      if (needs.mood().equals("grumpy") && level.getGameTime() % 100 < 40) response = 0;
      desired = ship.logicalPose().transformNormal(local).scale(response);
      turn *= response * (0.25 + 0.75 * Math.abs(throttle));
    }
    if (desired.lengthSqr() == 0 && turn == 0) {
      desired = resting() || !canFly() ? Vec3.ZERO : forage(ship);
      heightCommanded = desired.lengthSqr() > 0;
      if (desired.lengthSqr() == 0 && !resting())
        desired = new Vec3(0, Math.sin(level.getGameTime() / 35.0) * 0.08, 0);
    }
    incorporateAmalgam();
    if (canFly()) {
      double muscles = FlagellumMotion.strength(swimmingFlagella(), level.getGameTime());
      desired = desired.scale(muscles);
      turn *= muscles;
      if (muscles == 0) heightCommanded = false;
      double height = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)).y;
      if (heightCommanded) hoverHeight = Double.NaN;
      else {
        if (!Double.isFinite(hoverHeight) || Math.abs(height - hoverHeight) > 8)
          hoverHeight = height;
        // Position feedback prevents tiny per-step buoyancy errors accumulating into drift.
        desired = desired.add(0, Math.clamp((hoverHeight - height) * 2.0, -1.5, 1.5), 0);
      }
    }
    if (blueprint.busy()) {
      var travel = blueprint.flight(this);
      flight = new FlightIntent(canFly(), travel == null ? Vec3.ZERO : travel, 0);
      return;
    }
    flight = new FlightIntent(canFly(), desired, turn);
  }

  private void incorporateAmalgam() {
    if (stage != 2) return;
    for (int i = 0; i < needs.levitite(); i++) {
      BlockPos pos = layoutPos(DinghyGrowth.BUOYANCY_ORGANS.get(i));
      if (level.getBlockState(pos).is(Create_bioships.FLESH.get()))
        level.setBlock(pos, Create_bioships.BUOYANCY_ORGAN.get().defaultBlockState(), 3);
    }
  }

  public boolean canFly() {
    if (stage != 2 || body.dead() || body.health() <= 0 || !needs.flightNourished()) return false;
    for (BlockPos offset : DinghyGrowth.BUOYANCY_ORGANS)
      if (!level.getBlockState(layoutPos(offset)).is(Create_bioships.BUOYANCY_ORGAN.get()))
        return false;
    return true;
  }

  private void updateEye(boolean active) {
    BlockState state = level.getBlockState(worldPosition);
    if (!state.is(Create_bioships.CORE.get())) return;
    int phase = Math.floorMod(level.getGameTime() + worldPosition.getZ(), 120);
    boolean blink = active && (phase < 4 || phase >= 8 && phase < 11);
    if (state.getValue(LivingCoreBlock.ACTIVE) != active
        || state.getValue(LivingCoreBlock.BLINK) != blink)
      level.setBlock(
          worldPosition,
          state.setValue(LivingCoreBlock.ACTIVE, active).setValue(LivingCoreBlock.BLINK, blink),
          2);
  }

  public boolean isMature() {
    return stage == 2;
  }

  public boolean resting() {
    return resting && !aggressive();
  }

  public boolean aggressive() {
    return needs.aggressive();
  }

  public boolean isCaptain(net.minecraft.world.entity.Entity entity) {
    return entity.getUUID().equals(captain);
  }

  public boolean huntingAllowed() {
    var state = level.getBlockState(layoutPos(DinghyGrowth.HUNT));
    return state.getBlock() instanceof NerveControlBlock nerve
        && nerve.command == NerveControlBlock.Command.HUNT
        && state.getValue(NerveControlBlock.ACTIVE);
  }

  public boolean starving() {
    return needs.satiety() == 0;
  }

  public void adoptLarynx() {
    larynxDeveloped = true;
    setChanged();
  }

  public boolean hungry() {
    return needs.satiety() < 240;
  }

  public boolean hasMouth() {
    return mouthBlock() != null || organMove != null && organMove.mouth();
  }

  public BlockPos mouthBlock() {
    if (level == null) return null;
    ServerSubLevel ship = ship();
    if (ship != null)
      for (var actor : BioshipIntegration.blockEntities(ship))
        if (BioshipIntegration.isMaw(level.getBlockState(actor.getBlockPos())))
          return actor.getBlockPos();
    BlockPos oldMouth = layoutPos(DinghyGrowth.MOUTH);
    return level.getBlockState(oldMouth).is(Create_bioships.MOUTH.get()) ? oldMouth : null;
  }

  public Vec3 mouthPosition(ServerSubLevel ship) {
    BlockPos mouth = mouthBlock();
    if (mouth != null && level.getBlockState(mouth).is(Create_bioships.FEEDING_MOUTH.get()))
      return ship.logicalPose()
          .transformPosition(
              Vec3.atCenterOf(mouth)
                  .relative(level.getBlockState(mouth).getValue(FeedingMouthBlock.FACING), .6));
    return ship.logicalPose()
        .transformPosition(Vec3.atCenterOf(mouth == null ? worldPosition : mouth))
        .add(0, -0.5, 0);
  }

  public void eatPrey() {
    eatPrey(false);
  }

  public void eatPrey(net.minecraft.world.entity.LivingEntity prey) {
    Item food =
        prey instanceof net.minecraft.world.entity.monster.Zombie
            ? Items.ROTTEN_FLESH
            : prey instanceof net.minecraft.world.entity.animal.Chicken
                ? Items.CHICKEN
                : prey instanceof net.minecraft.world.entity.animal.Sheep
                    ? Items.MUTTON
                    : prey instanceof net.minecraft.world.entity.animal.Pig
                        ? Items.PORKCHOP
                        : prey instanceof net.minecraft.world.entity.animal.Rabbit
                            ? Items.RABBIT
                            : Items.BEEF;
    ItemStack meal = new ItemStack(food);
    CompoundTag flags = new CompoundTag();
    flags.putBoolean("BioshipHunted", true);
    meal.set(
        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
        net.minecraft.world.item.component.CustomData.of(flags));
    DigestiveSystem.offerFood(this, meal, false);
  }

  public void eatPrey(boolean toxic) {
    if (!hasMouth()) return;
    ItemStack meal = new ItemStack(toxic ? Items.ROTTEN_FLESH : Items.BEEF);
    CompoundTag flags = new CompoundTag();
    flags.putBoolean("BioshipHunted", true);
    meal.set(
        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
        net.minecraft.world.item.component.CustomData.of(flags));
    DigestiveSystem.offerFood(this, meal, false);
  }

  public int swimmingFlagella() {
    int count = 0;
    for (BlockPos offset : DinghyGrowth.FLAGELLA)
      if (level.getBlockEntity(layoutPos(offset)) instanceof TentacleBlockEntity tentacle
          && tentacle.swimming()) count++;
    return count;
  }

  private Vec3 forage(ServerSubLevel ship) {
    if (!hasMouth() || !hungry() || !huntingAllowed() && !aggressive()) {
      foodTarget = null;
      return Vec3.ZERO;
    }
    Vec3 mouth =
        hasMouth()
            ? mouthPosition(ship)
            : ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition).add(0, 1.5, 0));
    if (foodTarget != null
        && (!foodTarget.isAlive()
            || !foodTarget.getItem().is(MEAT)
            || foodTarget.position().distanceToSqr(mouth) > 64)) foodTarget = null;
    if (foodTarget == null && level.getGameTime() % 20 == 0) {
      double closest = 64;
      for (var item :
          level.getEntitiesOfClass(
              net.minecraft.world.entity.item.ItemEntity.class,
              new AABB(mouth, mouth).inflate(8),
              entity -> entity.getItem().is(MEAT))) {
        double distance = item.position().distanceToSqr(mouth);
        if (distance < closest) {
          foodTarget = item;
          closest = distance;
        }
      }
    }
    if (foodTarget == null) return huntScent(ship);
    Vec3 food = foodTarget.position().add(0, 0.2, 0);
    if (level
            .clip(
                new ClipContext(
                    mouth, food, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, foodTarget))
            .getType()
        != HitResult.Type.MISS) return Vec3.ZERO;
    if (food.distanceToSqr(mouth) < 4 && level.getGameTime() % 40 == 0) {
      ItemStack stack = foodTarget.getItem();
      ItemStack meal = stack.copyWithCount(1);
      CompoundTag flags =
          meal.getOrDefault(
                  net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                  net.minecraft.world.item.component.CustomData.EMPTY)
              .copyTag();
      flags.putBoolean("BioshipHunted", true);
      meal.set(
          net.minecraft.core.component.DataComponents.CUSTOM_DATA,
          net.minecraft.world.item.component.CustomData.of(flags));
      if (DigestiveSystem.offerFood(this, meal, false)) {
        stack.shrink(1);
        if (stack.isEmpty()) foodTarget.discard();
        setChanged();
      }
    }
    Vec3 scent = food.subtract(mouth);
    return scent.lengthSqr() < 0.25 ? Vec3.ZERO : scent.normalize().scale(0.5);
  }

  private Vec3 huntScent(ServerSubLevel ship) {
    if (!hasMouth() || !aggressive() && (!hungry() || !huntingAllowed())) return Vec3.ZERO;
    TentacleBlockEntity limb = null;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof TentacleBlockEntity tentacle) {
        limb = tentacle;
        break;
      }
    if (limb == null) return Vec3.ZERO;
    Vec3 mouth = mouthPosition(ship);
    net.minecraft.world.entity.LivingEntity closest = null;
    double distance = 144;
    for (var prey :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class, new AABB(mouth, mouth).inflate(12))) {
      double next = prey.position().distanceToSqr(mouth);
      if (next < distance
          && (aggressive() || TentacleBlockEntity.edible(prey))
          && limb.valid(prey, this)
          && level
                  .clip(
                      new ClipContext(
                          mouth.add(0, -0.6, 0),
                          prey.getBoundingBox().getCenter(),
                          ClipContext.Block.COLLIDER,
                          ClipContext.Fluid.NONE,
                          prey))
                  .getType()
              == HitResult.Type.MISS) {
        closest = prey;
        distance = next;
      }
    }
    return closest == null || distance < 4
        ? Vec3.ZERO
        : closest.position().subtract(mouth).normalize().scale(0.65);
  }

  @Override
  public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt) {
    if (this.body.dead()) return;
    FlightIntent intent = flight;
    if (!body.isValid()) return;
    double mass = ship.getMassTracker().getMass();
    if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0) return;
    Vector3d up = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
    Vector3d correction =
        up.cross(
                new Vector3d(intent.stanceUp().x, intent.stanceUp().y, intent.stanceUp().z),
                new Vector3d())
            .mul(intent.lift() ? 3 : 30);
    correction
        .add(0, intent.turn() * 1.2, 0)
        .sub(body.getAngularVelocity(new Vector3d()).mul(intent.lift() ? 1.8 : 8));
    // Grounded muscles push and brace only within reach of a solid footing; no flight buoyancy.
    if (!intent.lift()) {
      var current = body.getLinearVelocity(new Vector3d());
      var grounded =
          new Vector3d(intent.velocity().x - current.x, 0, intent.velocity().z - current.z)
              .mul(12 * dt);
      if (Double.isFinite(intent.supportHeight())) {
        double height = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)).y;
        double target = Math.clamp((intent.supportHeight() - height) * 2, -.45, .65);
        double gravity = DimensionPhysicsData.getGravity(ship.getLevel()).y;
        grounded.y = ((target - current.y) * 6 - gravity) * dt;
      }
      body.addLinearAndAngularVelocity(grounded, correction.mul(dt));
      return;
    }
    Vector3d acceleration =
        new Vector3d(intent.velocity().x, intent.velocity().y, intent.velocity().z)
            .sub(body.getLinearVelocity(new Vector3d()))
            .mul(2)
            .sub(DimensionPhysicsData.getGravity(ship.getLevel()));
    body.addLinearAndAngularVelocity(acceleration.mul(dt), correction.mul(dt));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (preferredMouth != null) tag.putLong("PreferredMouth", preferredMouth.asLong());
    if (organMove != null) tag.put("OrganMove", organMove.save());
    tag.put("Scavenging", scavenging.save());
    tag.put("Crawl", crawl.save());
    tag.put("Access", access.save(captain));
    tag.put("BodyMemory", body.save());
    tag.put("Blueprint", blueprint.save());
    if (level != null && !level.isClientSide) {
      var stomach = DigestiveSystem.stomach(this);
      goggleFlesh = stomach == null ? 0 : stomach.material("biomancy:flesh_bits");
      goggleBone = stomach == null ? 0 : stomach.material("biomancy:bone_fragments");
      goggleFiber = stomach == null ? 0 : stomach.material("biomancy:elastic_fibers");
    }
    tag.putInt("GoggleFlesh", goggleFlesh);
    tag.putInt("GoggleBone", goggleBone);
    tag.putInt("GoggleFiber", goggleFiber);
    tag.putBoolean("FramePaid", framePaid);
    tag.putBoolean("LarynxDeveloped", larynxDeveloped);
    tag.putInt("BodyLayoutVersion", DinghyGrowth.LAYOUT_VERSION);
    tag.putInt("PoisonSeconds", needs.poisonSeconds());
    if (captain != null) tag.putUUID("Captain", captain);
    tag.putBoolean("Resting", resting);
    tag.putBoolean("Awakened", awakened);
    tag.putInt("Stage", stage);
    tag.putInt("GrowthIndex", growthIndex);
    tag.putInt("Biomass", needs.biomass());
    tag.putInt("Levitite", needs.levitite());
    tag.putInt("Satiety", needs.satiety());
    tag.putInt("Trust", needs.trust());
    tag.putInt("MetabolismTicks", metabolismTicks);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    preferredMouth =
        tag.contains("PreferredMouth") ? BlockPos.of(tag.getLong("PreferredMouth")) : null;
    organMove =
        tag.contains("OrganMove") ? OrganMotion.Move.load(tag.getCompound("OrganMove")) : null;
    scavenging.load(tag.getCompound("Scavenging"));
    crawl.load(tag.getCompound("Crawl"));
    access.load(tag.getCompound("Access"));
    body.load(tag.getCompound("BodyMemory"), registries);
    blueprint.load(tag.getCompound("Blueprint"), registries);
    goggleFlesh = tag.getInt("GoggleFlesh");
    goggleBone = tag.getInt("GoggleBone");
    goggleFiber = tag.getInt("GoggleFiber");
    framePaid = tag.getBoolean("FramePaid") || tag.getInt("Stage") > 0;
    larynxDeveloped = tag.getBoolean("LarynxDeveloped");
    needs.restorePoison(tag.getInt("PoisonSeconds"));
    captain = tag.hasUUID("Captain") ? tag.getUUID("Captain") : null;
    resting = tag.getBoolean("Resting");
    awakened = tag.getBoolean("Awakened");
    stage = Math.clamp(tag.getInt("Stage"), 0, 2);
    growthIndex = Math.clamp(tag.getInt("GrowthIndex"), 0, DinghyGrowth.body().size());
    if (stage == 1 && tag.getInt("BodyLayoutVersion") < DinghyGrowth.LAYOUT_VERSION) {
      stage = 0;
      growthIndex = 0;
    }
    needs.restore(
        tag.getInt("Biomass"),
        tag.getInt("Levitite"),
        tag.contains("Satiety") ? tag.getInt("Satiety") : 600,
        tag.contains("Trust") ? tag.getInt("Trust") : 60);
    metabolismTicks = Math.clamp(tag.getInt("MetabolismTicks"), 0, 19);
    foodTarget = null;
    hoverHeight = Double.NaN;
    flight = new FlightIntent(false, Vec3.ZERO, 0);
  }
}
