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
          Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "biomass"));
  private static final TagKey<Item> LEVITITE =
      TagKey.create(
          Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "levitite_amalgam"));
  private BlockPos preferredMouth;

  public BlockPos preferredMouth() {
    return preferredMouth;
  }

  public void preferredMouth(BlockPos pos) {
    preferredMouth = pos;
    setChanged();
  }

  private OrganMotion.Move organMove;
  private final BioshipAssimilation assimilation = new BioshipAssimilation();

  public BioshipAssimilation assimilation() {
    return assimilation;
  }

  public OrganMotion.Move organMove() {
    return organMove;
  }

  void workingOrganMove(OrganMotion.Move move) {
    organMove = move;
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
  private final BioshipLootMemory loot = new BioshipLootMemory();

  BioshipLootMemory loot() {
    return loot;
  }

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
  private final CoreMotion coreMotion = new CoreMotion();

  public CoreMotion coreMotion() {
    return coreMotion;
  }

  public BlockPos structuralOrigin() {
    return coreMotion.home(this);
  }

  private boolean hullBudgetAvailable() {
    var stomach = DigestiveSystem.stomach(this);
    int tier = crawl.hullTier(this), upgrades = Math.max(0, tier - crawl.legTier());
    return stomach != null
        && tier <= 3
        && needs.biomass() + stomach.material("biomancy:flesh_bits")
            >= 64 + 8 * upgrades + EmergencyReserve.TARGET.flesh()
        && stomach.material("biomancy:bone_fragments")
            >= (framePaid ? 0 : 8) + 2 * upgrades + EmergencyReserve.TARGET.bone()
        && stomach.material("biomancy:elastic_fibers")
            >= (framePaid ? 0 : 12) + 4 * upgrades + EmergencyReserve.TARGET.fiber();
  }

  boolean growthStockReady() {
    return AirshipPrototype.available() && hullBudgetAvailable();
  }

  boolean growthSiteReady() {
    return AirshipPrototype.available()
        && needs.readyToGrow()
        && hullBudgetAvailable()
        && roomToGrow(ship());
  }

  public double coreHeightOffset() {
    return ship() == null
        ? 0
        : ship()
            .logicalPose()
            .transformNormal(Vec3.atLowerCornerOf(worldPosition.subtract(structuralOrigin())))
            .y;
  }

  private final BioshipVision vision = new BioshipVision();

  public BioshipVision vision() {
    return vision;
  }

  BlockPos supplyTarget;
  private final BioshipRescue rescue = new BioshipRescue();

  public BioshipRescue rescue() {
    return rescue;
  }

  private final BioshipAutonomy autonomy = new BioshipAutonomy();

  public BioshipAutonomy autonomy() {
    return autonomy;
  }

  int trustLevel() {
    return needs.trust();
  }

  private final BioshipDefense defense = new BioshipDefense();

  public Vec3 attackTip() {
    return defense.collisionTip();
  }

  public boolean attackSweeping() {
    return level != null && defense.sweeping(level.getGameTime());
  }

  BioshipDefense defense() {
    return defense;
  }

  public boolean hatchling() {
    return !AirshipPrototype.available() || stage == 0;
  }

  private int bodyLayoutVersion = DinghyGrowth.LAYOUT_VERSION;

  public int layoutVersion() {
    return bodyLayoutVersion;
  }

  public java.util.List<DinghyGrowth.Part> plannedBody() {
    return EngineRoomLayout.body(bodyLayoutVersion, engineRoom.columns());
  }

  private String growthObstacle = "";
  private long lastServerTick = Long.MIN_VALUE;
  private final BioshipAquatic aquatic = new BioshipAquatic();
  private final BioshipEngineRoom engineRoom = new BioshipEngineRoom();

  BioshipEngineRoom engineRoom() {
    return engineRoom;
  }

  public BioshipAquatic aquatic() {
    return aquatic;
  }

  private double hoverHeight = Double.NaN;
  private volatile FlightIntent flight = new FlightIntent(false, Vec3.ZERO, 0);

  void inheritFlight(LivingCoreBlockEntity previous) {
    if (!hatchling()) flight = previous.flight;
  }

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
      if (player instanceof net.minecraft.server.level.ServerPlayer pilot)
        autonomy.command(this, pilot);
      setChanged();
      message(
          player,
          resting ? "message.create_bioships.resting" : "message.create_bioships.controls_enabled");
      return;
    }
    if (!player
        .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
        .is(Create_bioships.BIOSHIP_GOGGLES.get()))
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
        Component.translatable(
                "stage.create_bioships." + (AirshipPrototype.available() ? stage : 0))
            .append(" | ")
            .append(Component.translatable("mood.create_bioships." + needs.mood())));
    if (aquatic.air() < 300) lines.add(Component.literal("Air: " + aquatic.air() + " / 300"));
    if (ship() != null)
      lines.add(
          Component.literal(
              "Energy demand: "
                  + BioshipEnergy.demand(this)
                  + " /s | Digestive pairs: "
                  + DigestiveSystem.stomachs(this).stream().filter(s -> s.connected(this)).count()
                  + " / "
                  + DigestiveSystem.requiredPairs(this)));
    lines.add(
        Component.literal(
            String.format(
                java.util.Locale.ROOT, "Health: %.1f / %d", body.health(), body.capacity())));
    lines.add(
        Component.literal(
            String.format(
                java.util.Locale.ROOT,
                "Leg tier: %d | Load: %.0f / %.0f | Extra lift: %d",
                crawl.legTier(),
                crawl.measuredLoad(),
                crawl.loadCapacity(),
                crawl.extraLift())));
    if (aquatic.legUsage().samples() > 0)
      lines.add(
          Component.literal(
              "Recent water use: " + aquatic.legUsage().water() / 10 + "% (leg adaptation)"));
    lines.add(Component.literal("Behavior: " + brain.action() + " · " + brain.reason()));
    if (AirshipPrototype.available())
      lines.add(Component.literal("Growth biomass: " + needs.biomass() + " / 64"));
    lines.add(Component.literal("Stored flesh: " + goggleFlesh));
    if (AirshipPrototype.available())
      lines.add(
          Component.literal(
              "Frame: "
                  + goggleBone
                  + " / 8 bone, "
                  + goggleFiber
                  + " / 12 fibers"
                  + (framePaid ? " (incorporated)" : "")));
    if (AirshipPrototype.available() && !isMature()) {
      int upgrades = Math.max(0, crawl.hullTier(this) - crawl.legTier());
      lines.add(
          Component.literal(
              "Leg budget before hull: "
                  + (8 * upgrades)
                  + " flesh, "
                  + (2 * upgrades)
                  + " bone, "
                  + (4 * upgrades)
                  + " fibers (plus frame)."));
    }
    if (AirshipPrototype.available())
      lines.add(Component.literal("Amalgam: " + needs.levitite() + " / 8"));
    lines.add(Component.literal("Food: " + needs.satiety() + " / 1200 | Trust: " + needs.trust()));
    if (AirshipPrototype.available() && stage == 0)
      lines.add(
          Component.literal(
              "Growth needs open space: 9 wide, 23 long and 8 above the core. Feed through the"
                  + " connected mouth and stomach."));
    if (stage == 0)
      lines.add(
          Component.literal(
              "Stomach formation needs both a Decomposer and a Digester. Assimilate a Bio-Forge for"
                  + " the feeding maw."));
    if (AirshipPrototype.available() && stage == 1)
      lines.add(
          Component.literal("Hull construction: " + growthIndex + " / " + plannedBody().size()));
    if (AirshipPrototype.available() && !growthObstacle.isEmpty())
      lines.add(Component.literal(growthObstacle));
    if (crawl.walking())
      lines.add(
          Component.literal(
              crawl.gathering()
                  ? hatchling()
                      ? "Scuttling toward current needs."
                      : "Walking toward visible supplies."
                  : "Walking to a clear growth site."));
    else if (crawl.bracing()) lines.add(Component.literal("Bracing against the ground."));
    if (blueprint.busy()) lines.add(Component.literal(blueprint.status()));
    return lines;
  }

  public boolean feedAtMouth(Player player, ItemStack stack) {
    return feedAtMouth(mouthBlock(), player, stack);
  }

  public boolean feedAtMouth(BlockPos mouth, Player player, ItemStack stack) {
    if (stack.isEmpty() || body.dead() || ship() == null || !primary(ship()) || !hasMouth())
      return false;
    var stomach = mouth == null ? null : DigestiveSystem.forMouth(this, mouth);
    if (stomach == null || !stomach.connected(this)) {
      message(player, "message.create_bioships.need_stomach");
      return false;
    }
    boolean accepted = false;
    if (stack.is(LEVITITE)) accepted = AirshipPrototype.available() && needs.feedLevitite();
    else if (stack.is(MEAT)) accepted = DigestiveSystem.offerFood(this, mouth, stack, false);
    else if (stack.is(BioshipIntegration.constructionMaterial())) {
      stomach.retain(stack.copyWithCount(1));
      accepted = true;
    } else accepted = DigestiveSystem.offerFood(this, mouth, stack, false);
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

  final BioshipTrustFeedback trustFeedback = new BioshipTrustFeedback();

  int poisonSeconds() {
    return needs.poisonSeconds();
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
        needs.digestFedMeal(stack.is(Items.ROTTEN_FLESH) ? 0 : 70);
        if (stack.is(Items.ROTTEN_FLESH)) needs.poison();
        trustFeedback.emit(this, !stack.is(Items.ROTTEN_FLESH));
      }
      setChanged();
    }
  }

  public boolean acceptAmalgam(ItemStack stack) {
    if (!AirshipPrototype.available()) return false;
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

  private final BioshipSurvivalAdaptations survival = new BioshipSurvivalAdaptations();

  public BioshipSurvivalAdaptations survival() {
    return survival;
  }

  private final BioshipAdaptation adaptation = new BioshipAdaptation();

  BioshipAdaptation adaptation() {
    return adaptation;
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
    return structuralOrigin()
        .offset(blueprint.offset(DinghyGrowth.atVersion(offset, bodyLayoutVersion)));
  }

  final BioshipChunks chunkLoading = new BioshipChunks();
  private final BioshipBrain brain = new BioshipBrain();

  public BioshipBrain brain() {
    return brain;
  }

  int satiety() {
    return needs.satiety();
  }

  boolean frameFunded() {
    return !AirshipPrototype.available() || framePaid;
  }

  boolean buildingHull() {
    return AirshipPrototype.available() && stage == 1;
  }

  private boolean framePaid, larynxDeveloped;

  public boolean wantsStoredFood() {
    return hungry()
        || needs.biomass() < BioshipNeeds.BIOMASS_REQUIRED
        || !framePaid
        || !canFly() && crawl.carriedLoad(this) > crawl.loadCapacity()
        || !larynxDeveloped
        || needs.trust() < 100;
  }

  private boolean prepareFrame() {
    var stomach = DigestiveSystem.stomach(this);
    int tier = crawl.hullTier(this), upgrades = Math.max(0, tier - crawl.legTier());
    if (stomach == null
        || !stomach.connected(this)
        || tier > 3
        || !framePaid
            && !EmergencyReserve.after(
                this, new MaterialCost(8 * upgrades, 4 * upgrades + 12, 2 * upgrades + 8))
        || stomach.material("biomancy:flesh_bits") < 8 * upgrades
        || stomach.material("biomancy:bone_fragments") < 2 * upgrades + (framePaid ? 0 : 8)
        || stomach.material("biomancy:elastic_fibers") < 4 * upgrades + (framePaid ? 0 : 12)) {
      growthObstacle =
          "Waiting for hull and leg materials: "
              + (8 * upgrades)
              + " flesh, "
              + (2 * upgrades + (framePaid ? 0 : 8))
              + " bone, "
              + (4 * upgrades + (framePaid ? 0 : 12))
              + " fibers.";
      return false;
    }
    // All checks precede spending: the legs can carry the completed plan before hull growth starts.
    for (int i = 0; i < upgrades; i++) if (!crawl.enhance(this)) return false;
    if (!framePaid) {
      stomach.spend("biomancy:bone_fragments", 8);
      stomach.spend("biomancy:elastic_fibers", 12);
    }
    framePaid = true;
    setChanged();
    return true;
  }

  private void developLarynx(ServerSubLevel ship) {
    var stomach = DigestiveSystem.stomach(this);
    if (stage != 2
        || larynxDeveloped
        || stomach == null
        || DigestiveSystem.stomachs(this).stream().mapToInt(StomachBlockEntity::meals).sum() < 8
        || !EmergencyReserve.after(this, new MaterialCost(8, 4, 2))
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
      trustFeedback.emit(this, false);
      setChanged();
    }
  }

  private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos) {
    if (!ship.getPlot().contains(new ChunkPos(pos))
        || !level.isInWorldBounds(pos)
        || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
        || !level.getBlockState(pos).isAir()) return false;
    Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(pos));
    return BioshipIntegration.growthSpace(level, BlockPos.containing(physical));
  }

  private void recordGrowthObstacle(ServerSubLevel ship, BlockPos offset) {
    var pos = structuralOrigin().offset(offset);
    var state = level.getBlockState(pos);
    if (state.isAir())
      pos = BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(pos)));
    state = level.getBlockState(pos);
    growthObstacle =
        "Growth blocked by "
            + state.getBlock().getName().getString()
            + " at "
            + offset.getX()
            + ", "
            + offset.getY()
            + ", "
            + offset.getZ()
            + " from the core.";
  }

  private boolean roomToGrow(ServerSubLevel ship) {
    growthObstacle = "";
    // The oval hull spans neighboring plot chunks. Allocate only its own empty plot cells.
    for (var part : plannedBody()) {
      var chunk = new ChunkPos(structuralOrigin().offset(part.offset()));
      if (ship.getPlot().contains(chunk)
          && ship.getPlot().getChunkHolder(ship.getPlot().toLocal(chunk)) == null)
        ship.getPlot().newEmptyChunk(chunk);
    }
    for (var part : plannedBody())
      if (!structuralOrigin().offset(part.offset()).equals(worldPosition)
          && !level.getBlockState(structuralOrigin().offset(part.offset())).equals(part.state())
          && !BioshipIntegration.isIntegratedMachine(
              level, structuralOrigin().offset(part.offset()))
          && !clearForGrowth(ship, structuralOrigin().offset(part.offset()))) {
        recordGrowthObstacle(ship, part.offset());
        return false;
      }
    return true;
  }

  private net.minecraft.server.level.ServerLevel transferLevel;
  private BlockPos transferTarget;

  void recordTransfer(net.minecraft.server.level.ServerLevel destination, BlockPos pos) {
    transferLevel = destination;
    transferTarget = pos.immutable();
  }

  boolean hasTransferReceipt() {
    return transferLevel != null && transferTarget != null;
  }

  boolean transferred() {
    return transferLevel != null
        && transferTarget != null
        && transferLevel.getBlockEntity(transferTarget) instanceof LivingCoreBlockEntity replacement
        && replacement != this
        && !replacement.body().dead();
  }

  void coreRemoved() {
    if (!(level instanceof net.minecraft.server.level.ServerLevel) || body.dead() || transferred())
      return;
    if (hasTransferReceipt()) {
      BioshipCoreLifecycle.removed(this);
      return;
    }
    if (ship() != null) body.die(this, BioshipDeathCause.CORE_REMOVED);
  }

  @Override
  public void setRemoved() {
    BioshipCoreLifecycle.removed(this);
    chunkLoading.release();
    super.setRemoved();
  }

  public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core) {
    if (level.isClientSide) return;
    var owner = core.ship();
    if (owner != null && BioshipDecay.dead(owner) && !core.body().dead())
      core.body()
          .die(core, BioshipDeathCause.read(owner.getUserDataTag().getString("BioshipDeathCause")));
    core.chunkLoading.update(core);
    core.body.initializeHealth(core);
    if (core.ship() != null && !core.body().dead() && level.getGameTime() % 20 == 0)
      BioshipAnatomy.observe(core);
    if (core.ship() != null) {
      BioshipTargetEntity.ensure(core);
      core.scavenging.deliver(core);
      core.autonomy.assess(core);
    }
    if (core.ship() != null && !core.body.dead()) core.aquatic.tick(core);
    if (core.ship() != null) core.rescue.tick(core);
    if (core.body.dead()) {
      if (core.coreMotion.active()) core.coreMotion.cancel(core);
      core.updateEye(false);
      core.flight = new FlightIntent(false, Vec3.ZERO, 0);
      if (core.ship() != null
          && !(core.ship().getUserDataTag() != null
              && core.ship().getUserDataTag().getBoolean("BioshipScavenged")))
        BioshipDecay.mark(core.ship());
      BioshipAccess.tick(core);
      return;
    }
    if (core.ship() != null
        && core.coreMotion.active()
        && BioshipNutrition.critical(core)
        && !core.coreMotion.feeding()) {
      core.brain.choose(core);
      if (core.brain.interruptsForFood(core)) core.coreMotion.cancel(core);
    }
    if (core.ship() != null && core.coreMotion.tick(core)) {
      if (!core.isRemoved()) core.defense.tick(core);
      if (!core.isRemoved()) core.serverTick();
      if (core.hatchling())
        core.flight =
            new FlightIntent(
                false, Vec3.ZERO, 0, core.crawl.supportHeight(), core.crawl.stanceUp());
      return;
    }
    if (core.ship() != null) core.vision.observe(core);
    if (core.ship() != null && core.isMature() && level.getGameTime() % 10 == 0) {
      core.gatheringGoal();
      level.sendBlockUpdated(pos, state, state, 2);
    }
    if (core.ship() != null)
      core.crawl.tick(
          core,
          AirshipPrototype.available()
              && core.stage < 2
              && core.needs.readyToGrow()
              && !core.roomToGrow(core.ship()));
    if (core.ship() != null) core.defense.tick(core);
    core.body.tick(core);
    if (core.blueprint.busy()) {
      core.serverTick();
      core.blueprint.tick(core);
      BioshipAccess.tick(core);
      return;
    }
    if (core.rescue.active()) {
      core.serverTick();
      BioshipAccess.tick(core);
      return;
    }
    var before = BioshipBody.actual(core);
    core.assimilation.tick(core);
    OrganMotion.tick(core);
    core.serverTick();
    var ship = core.ship();
    if (ship != null) core.adaptation.tick(core);
    if (ship != null && core.primary(ship) && level.getGameTime() % 20 == 0)
      BioshipIntegration.collect(core);
    core.updateCabinLights();
    if (ship != null && level.getGameTime() % 20 == 0) DigestiveSystem.connect(core, ship);
    if (AirshipPrototype.available()) core.growBody();
    if (core.ship() != null) core.vision.tick(core);
    if (ship != null
        && core.isMature()
        && level.getGameTime() % 20 == 0
        && core.brain.allowsConfiguration(core)) {
      EngineRoom.arrange(core, ship);
      BioshipIntegration.relocateMouth(core, ship);
      DigestiveSystem.connect(core, ship);
      if (!core.brain.needsMaterials(core)) core.developLarynx(ship);
      BioshipAccess.tick(core);
      var panel = core.layoutPos(DinghyGrowth.BLUEPRINT_PANEL);
      var oldPanel = core.layoutPos(new BlockPos(-3, 2, 2));
      if (level.getBlockState(oldPanel).is(Create_bioships.BLUEPRINT_PANEL.get())
          && (level.getBlockState(panel).isAir()
              || BioshipTissue.isTissue(level.getBlockState(panel))))
        level.setBlock(oldPanel, BioshipTissue.body().defaultBlockState(), 3);
      if (level.getBlockState(panel).isAir() || BioshipTissue.isTissue(level.getBlockState(panel)))
        level.setBlock(panel, Create_bioships.BLUEPRINT_PANEL.get().defaultBlockState(), 3);
    }
    if (AirshipPrototype.available()
        && core.stage > 0
        && (core.isMature()
            || java.util.Arrays.stream(Direction.values())
                    .filter(d -> BioshipTissue.isTissue(level.getBlockState(pos.relative(d))))
                    .count()
                >= 3))
      level.setBlock(pos, core.getBlockState().setValue(LivingCoreBlock.CONCEALED, true), 2);
    core.body.reconcile(core, before);
    if (level.getGameTime() % 20 == 0 && core.hatchling())
      level.sendBlockUpdated(pos, core.getBlockState(), core.getBlockState(), 2);
  }

  private void updateCabinLights() {
    if (!AirshipPrototype.available()) return;
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
    if (!AirshipPrototype.available()) return;
    if (body.health() <= 0 || brain.choose(this).action() != BioshipBrain.Action.GROW) return;
    ServerSubLevel ship = ship();
    if (ship == null || !primary(ship)) return;
    if (stage == 0
        && worldPosition.equals(structuralOrigin())
        && needs.readyToGrow()
        && level.getGameTime() % 20 == 0
        && roomToGrow(ship)
        && prepareFrame()) {
      stage = 1;
      setChanged();
    }
    if (stage != 1 || !prepareFrame()) return;
    var parts = plannedBody();
    if (growthIndex < parts.size()) {
      var part = parts.get(growthIndex);
      BlockPos target = structuralOrigin().offset(part.offset());
      boolean placed =
          level.getBlockState(target).is(part.state().getBlock())
              || BioshipIntegration.isIntegratedMachine(level, target);
      growthObstacle = "";
      if (placed) {
        if (level.getBlockState(target).is(Create_bioships.INTESTINE.get())
            && BioshipTissue.isTissue(part.state()))
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
      } else if (clearForGrowth(ship, target)) {
        var physical =
            BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(target)));
        BioshipIntegration.clearGrowthPlant(level, physical);
        placed = level.setBlock(target, part.state(), 3);
      }
      if (!placed) recordGrowthObstacle(ship, part.offset());
      if (placed) {
        var embedded = level.getBlockEntity(target);
        if (BioshipTissue.isTissue(part.state()) && embedded != null) {
          BioshipTissue.remember(embedded, BioshipTissue.body().defaultBlockState());
          embedded.setChanged();
        }
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
    BioshipPoisonFeedback.tick(this);
    BioshipIntegration.bind(ship);
    if (!awakened) {
      awakened = true;
      ship.setName("Living Bioship");
      setChanged();
    }
    if (++metabolismTicks >= 20) {
      metabolismTicks = 0;
      bodyBlocks = BioshipIntegration.countBlocks(ship);
      BioshipMetabolism.step(this, needs);
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
    incorporateAmalgam();
    if (!canFly()
        && (crawl.walking()
            || crawl.bracing()
            || hatchling() && (crawl.goal() != null || crawl.reach() != null))) {
      flight =
          new FlightIntent(
              false,
              crawl.velocity(this),
              HatchlingFront.turn(
                  this, crawl.steeringGoal() != null ? crawl.steeringGoal() : crawl.reach()),
              crawl.supportHeight(),
              crawl.stanceUp());
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
    if (desired.lengthSqr() == 0 && turn == 0 && !autonomy.trustedPilot(this)) {
      desired = resting() || !canFly() ? Vec3.ZERO : brain.flightVelocity(this);
      heightCommanded = desired.lengthSqr() > 0;
      if (desired.lengthSqr() == 0 && !resting())
        desired = new Vec3(0, Math.sin(level.getGameTime() / 35.0) * 0.08, 0);
    }

    if (canFly() && brain.choose(this).action() == BioshipBrain.Action.FLEE) {
      desired = brain.flightVelocity(this);
      turn = 0;
      heightCommanded = true;
    }
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
    if (!AirshipPrototype.available() || stage != 2) return;
    for (int i = 0; i < needs.levitite(); i++) {
      BlockPos pos = layoutPos(DinghyGrowth.BUOYANCY_ORGANS.get(i));
      if (BioshipTissue.isTissue(level.getBlockState(pos)))
        level.setBlock(pos, Create_bioships.BUOYANCY_ORGAN.get().defaultBlockState(), 3);
    }
  }

  public boolean canFly() {
    if (!AirshipPrototype.available()) return false;
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
    return AirshipPrototype.available() && stage == 2;
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
    if (!AirshipPrototype.available()) return false;
    var state = level.getBlockState(layoutPos(DinghyGrowth.HUNT));
    return state.getBlock() instanceof NerveControlBlock nerve
        && nerve.command == NerveControlBlock.Command.HUNT
        && state.getValue(NerveControlBlock.ACTIVE);
  }

  public int foodLevel() {
    return needs.satiety();
  }

  public boolean starving() {
    return needs.satiety() == 0;
  }

  public void adoptLarynx() {
    larynxDeveloped = true;
    setChanged();
  }

  public boolean wantsToHunt() {
    return brain.shouldHunt(this);
  }

  public boolean hungry() {
    return BioshipEnergy.demand(this) > 0 && needs.satiety() < 240;
  }

  public boolean hasMouth() {
    return mouthBlock() != null || organMove != null && organMove.mouth();
  }

  public BlockPos mouthBlock() {
    if (level == null) return null;
    ServerSubLevel ship = ship();
    if (ship != null && huntingAllowed())
      for (var offset :
          java.util.List.of(
              DinghyGrowth.MOUTH,
              new BlockPos(0, 0, 7),
              new BlockPos(1, 0, -5),
              new BlockPos(-1, 0, 5),
              new BlockPos(0, 2, -7))) {
        var candidate = layoutPos(offset);
        if (BioshipIntegration.isMaw(level.getBlockState(candidate))) return candidate;
      }
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

  public int swimmingFlagella() {
    int count = 0;
    for (BlockPos offset : DinghyGrowth.FLAGELLA)
      if (level.getBlockEntity(layoutPos(offset)) instanceof TentacleBlockEntity tentacle
          && tentacle.swimming()) count++;
    return count;
  }

  /** Compatibility entry point: all movement and collection choices belong to the behavior tree. */
  Vec3 gatheringGoal() {
    return brain.groundGoal(this);
  }

  static boolean droppedSupply(ItemStack stack) {
    if (stack.is(Create_bioships.CORE.get().asItem())) return false;
    return stack.is(MEAT)
        || BioshipNutrition.fuelValue(stack) > 0
        || stack.is(BioshipIntegration.constructionMaterial())
        || BioshipScavenging.droppedBlock(stack);
  }

  @Override
  public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt) {
    if (this.body.dead()) return;
    FlightIntent intent = flight;
    if (!body.isValid()) return;
    double mass = ship.getMassTracker().getMass();
    if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0) return;
    Vector3d up = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
    boolean supported =
        intent.lift() || aquatic.swimming() || Double.isFinite(intent.supportHeight());
    var axis =
        BioshipRighting.torque(
            new Vec3(up.x, up.y, up.z),
            intent.stanceUp(),
            ship.logicalPose().transformNormal(new Vec3(1, 0, 0)));
    Vector3d correction =
        new Vector3d(axis.x, axis.y, axis.z).mul(intent.lift() ? 3 : supported ? 30 : 0);
    correction
        .add(0, intent.turn() * 1.2, 0)
        .sub(body.getAngularVelocity(new Vector3d()).mul(intent.lift() ? 1.8 : supported ? 8 : 0));
    if (aquatic.swimming()) {
      aquatic.propel(ship, body, dt);
      body.addLinearAndAngularVelocity(new Vector3d(), correction.mul(dt));
      return;
    }
    // Grounded muscles push and brace only within reach of a solid footing; no flight buoyancy.
    if (!intent.lift()) {
      if (!supported) return;
      var current = body.getLinearVelocity(new Vector3d());
      boolean clinging = crawl.wallFacing() != null;
      var grounded =
          new Vector3d(intent.velocity().x - current.x, 0, intent.velocity().z - current.z)
              .mul(12 * dt);
      if (Double.isFinite(intent.supportHeight())) {
        double height = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)).y;
        double target =
            Math.clamp(
                (intent.supportHeight() - height) * 2,
                -.45,
                clinging ? 1.8 : .65 + .1 * crawl.legTier());
        double gravity = DimensionPhysicsData.getGravity(ship.getLevel()).y;
        grounded.y = ((target - current.y) * (clinging ? 12 : 6) - gravity) * dt;
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
    tag.put("Aquatic", aquatic.save());
    tag.putInt("EngineColumns", engineRoom.columns());
    tag.put("Assimilation", assimilation.save());
    tag.put("Crawl", crawl.save());
    tag.put("Brain", brain.save());
    tag.put("Loot", loot.save());
    tag.put("CoreMotion", coreMotion.save());
    tag.put("Adaptation", adaptation.save());
    tag.put("SurvivalAdaptations", survival.save());
    tag.put("Defense", defense.save());
    tag.put("Autonomy", autonomy.save());
    tag.put("Rescue", rescue.save());
    tag.put("Vision", vision.save());
    if (supplyTarget != null) tag.putLong("SupplyTarget", supplyTarget.asLong());
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
    tag.putLong("TrustFeedbackNext", trustFeedback.nextTick());
    tag.putInt("BodyLayoutVersion", bodyLayoutVersion);
    tag.putInt("PoisonSeconds", needs.poisonSeconds());
    if (captain != null) tag.putUUID("Captain", captain);
    tag.putBoolean("Resting", resting);
    tag.putBoolean("Awakened", awakened);
    tag.putInt("Stage", stage);
    tag.putInt("GrowthIndex", growthIndex);
    tag.putString("GrowthObstacle", growthObstacle);
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
    aquatic.load(tag.getCompound("Aquatic"));
    engineRoom.restore(tag.getInt("EngineColumns"));
    assimilation.load(tag.getCompound("Assimilation"));
    crawl.load(tag.getCompound("Crawl"));
    brain.load(tag.getCompound("Brain"));
    loot.load(tag.getCompound("Loot"));
    coreMotion.load(tag.getCompound("CoreMotion"));
    adaptation.load(tag.getCompound("Adaptation"));
    survival.load(tag.getCompound("SurvivalAdaptations"));
    defense.load(tag.getCompound("Defense"));
    autonomy.load(tag.getCompound("Autonomy"));
    rescue.load(tag.getCompound("Rescue"), registries);
    vision.load(tag.getCompound("Vision"), registries);
    supplyTarget = tag.contains("SupplyTarget") ? BlockPos.of(tag.getLong("SupplyTarget")) : null;
    access.load(tag.getCompound("Access"));
    body.load(tag.getCompound("BodyMemory"), registries);
    blueprint.load(tag.getCompound("Blueprint"), registries);
    goggleFlesh = tag.getInt("GoggleFlesh");
    goggleBone = tag.getInt("GoggleBone");
    goggleFiber = tag.getInt("GoggleFiber");
    framePaid = tag.getBoolean("FramePaid") || tag.getInt("Stage") > 0;
    larynxDeveloped = tag.getBoolean("LarynxDeveloped");
    trustFeedback.restore(tag.getLong("TrustFeedbackNext"));
    needs.restorePoison(tag.getInt("PoisonSeconds"));
    captain = tag.hasUUID("Captain") ? tag.getUUID("Captain") : null;
    resting = tag.getBoolean("Resting");
    awakened = tag.getBoolean("Awakened");
    stage = Math.clamp(tag.getInt("Stage"), 0, 2);
    bodyLayoutVersion = tag.contains("BodyLayoutVersion") ? tag.getInt("BodyLayoutVersion") : 5;
    growthIndex = Math.clamp(tag.getInt("GrowthIndex"), 0, plannedBody().size());
    growthObstacle = tag.getString("GrowthObstacle");
    if (stage == 1 && bodyLayoutVersion < 5) {
      bodyLayoutVersion = DinghyGrowth.LAYOUT_VERSION;
      stage = 0;
      growthIndex = 0;
    }
    needs.restore(
        tag.getInt("Biomass"),
        tag.getInt("Levitite"),
        tag.contains("Satiety") ? tag.getInt("Satiety") : 600,
        tag.contains("Trust") ? tag.getInt("Trust") : 60);
    metabolismTicks = Math.clamp(tag.getInt("MetabolismTicks"), 0, 19);
    hoverHeight = Double.NaN;
    flight = new FlightIntent(false, Vec3.ZERO, 0);
  }
}
