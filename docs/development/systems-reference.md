# Bioship systems and class reference

This is a text flowchart and source-derived class reference, split into readable sections.
Arrows describe scheduling or data flow, not inheritance. Class declarations, fields and methods
are listed beneath each subsystem. Prototype airship code is preserved and disabled.

Generated from the working tree. Source paths and declaration line numbers identify the code.
The member index lists signatures rather than method bodies; nested implementation details remain in source.

## 1. Runtime and decisions

```text
[Core placement + Sable assembly]
                 |
                 v
[LivingCoreBlockEntity.tick]
   |-- chunk loading + aquatic safety
   |-- observe vision + remembered targets
   |-- action tree: BioshipBrain.choose
   |      |-- safety, needs before goals, weighted GoalQueue
   |      `-- crawl / defense / external collection
   |-- body tree: BioshipBodyBehavior.choose (20-tick cadence)
   |      |-- separate weighted GoalQueue + feasibility checks
   |      `-- BioshipAdaptation / BioshipConfiguration
   |             `-- repair, growth, native organ conversion
   |-- shared layout lock: carried organs / core moves / rescue / blueprint
   |-- atomic material account: revalidated debit at execution
   |
   `-- serverTick (once per game tick)
          |-- metabolism every 20 ticks
          |-- vision / health / repair
          `-- physics intent -> Sable body
```

The core remains an orchestrator. Some actuators still call choose() again in the same tick;
this is not yet a single immutable decision snapshot. Body work is selected independently of combat,
but in-flight component/layout transactions serialize conflicting mutations. An affordability quote
is not resource escrow; paid work debits the shared account before mutation.

## 2. Food, materials and growth

```text
[Visible component / dead wreck / dropped item]
                      |
       [Brain: rank current needs and goals]
                      |
       [Crawl route + reach + ComponentTransport]
                      |
       [Assimilation: conserved internal stock]
             |                    |
      keep needed part       lossy recycling
             |                    |
      settle / combine      flesh / fiber / bone
             |                    |
      mouth -> intestine -> stomach
                             |       |
                       local paste   native recipe products
                             |       |
                        metabolism   core reserve -> overflow sacs
                                      |
                         shared account pays adaptations
```

Paid adaptations require funding, reserves and applicable support checks. Digestive recovery
precedes vocal upgrades. Each working mouth has its own stomach and intact gut; each vocal mouth
has its own absorbed larynx capsule. Capsules preserve native configuration and essence data.
Ordinary tissue is Packed Flesh; reserved armor blocks do not enable adult hull progression.

## 3. Navigation, sight and combat

```text
[Core eyes + surface eyes]
             |
[Cones + light + obstruction rays]
             |
[Observed candidates + three-second memory]
             |
[Needs/goals -> selected target]
             |
[GroundRoute: body clearance + elevation]
             |
[Crawl: contacts + mass + leg capacity]
       |                    |
[Support / Righting]    [Defense / prey]
       |                    |
[Physics intent]      [Tentacle strike -> normal loot drops]
```

Open corridors use direct whole-body clearance checks. Obstructed routes use diagonal search and straight-segment simplification; extended surface search follows a failed quick detour. Known kill drops bypass visual memory expiry, not physical reach. Visibility and physical reach are separate checks. F3+B shows sight diagnostics; F3+P shows
navigation and behavior diagnostics. Neither toggle enables ordinary trust, poison, hurt or mouth effects.

## 4. Feedback and client rendering

```text
[Provisioned meal / player mistreatment]
             |
[Trust feedback: one burst per 60 game ticks]
             |
[Network -> client frame budget -> <=16 particles]

[Poison status] -> green status particles until recovery/death
[Actual damage] -> eight-tick whole-body red overlay + short voice

[Native or paid larynx] -> [one mouth's preserved capsule]
                                  |
             [Voice sample + envelope + pitch modulation]
                                  |
             [Mouth-position sound + synchronized jaw motion]

[Accepted food / bite] -> [closing jaw -> swallowing feedback]
```

The A-Z palette consists of distinct bounded creature articulations for future speech. It is
not natural-language speech synthesis. Sounds reuse Biomancy events; no dependency audio is copied.
Client sound envelopes stop samples after their short utterance, with attack/release and mild pitch bends.

## 5. Dormant airship boundary

```text
[AirshipPrototype.available() == false]
             |
             |-- no automatic adult hull / engine-room build
             |-- no flight or piloting
             |-- no blueprint reconstruction or new rescue jobs
             `-- registry IDs, models, source and saved data preserved

Shared helpers retained:
  BioshipBlueprint.physical() -> coordinate conversion
  EngineRoom.storage()       -> native sac classification
  layout constants          -> serialization / shared geometry
```

## Class structure by subsystem


### Registration and placement [active]

Registers blocks, items, menus and entities; placing a core creates its Sable body.

```text
[boot]
  Create_bioships
  LivingCoreBlock
```


#### Create_bioships

Source: `src/main/java/org/Azkerash/create_bioships/Create_bioships.java` (424 lines).

```java
public class Create_bioships

  public static final String MOD_ID; // line 15

  public static final DeferredRegister.Blocks BLOCKS; // line 16

  public static final DeferredRegister.Items ITEMS; // line 17

  public static final java.util.Map<String, DeferredItem<Item>> MOB_MEATS; // line 18

  private static java.util.Map<String, DeferredItem<Item>> registerMeats(); // line 19

  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES; // line 37

  public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES; // line 40

  public static final net.neoforged.neoforge.registries.DeferredHolder< net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<BioshipTargetEntity>> BIOSHIP_TARGET; // line 42

  public static final DeferredRegister<CreativeModeTab> TABS; // line 56

  public static final DeferredBlock<LivingCoreBlock> CORE; // line 58

  public static final DeferredBlock<LivingThrottleBlock> THROTTLE; // line 76

  public static final DeferredBlock<LivingThrottleBlock> VERTICAL_TRIM; // line 86

  public static final DeferredBlock<PulseLightBlock> PULSE_LIGHT; // line 96

  public static final DeferredBlock<SurfaceEyeBlock> SURFACE_EYE; // line 106

  public static final DeferredBlock<OneWayWindowBlock> ONE_WAY_WINDOW; // line 115

  public static final DeferredBlock<AccessPanelBlock> ACCESS_PANEL; // line 124

  public static final DeferredBlock<BlueprintPanelBlock> BLUEPRINT_PANEL; // line 133

  public static final DeferredBlock<StomachBlock> STOMACH; // line 139

  public static final DeferredBlock<IntestineBlock> INTESTINE; // line 145

  public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS; // line 154

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<StomachMenu>> STOMACH_MENU; // line 156

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<AccessMenu>> ACCESS_MENU; // line 165

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<BlueprintMenu>> BLUEPRINT_MENU; // line 174

  public static final DeferredBlock<FeedingMouthBlock> FEEDING_MOUTH; // line 183

  public static final DeferredBlock<Block> MOUTH; // line 192

  public static final DeferredBlock<BuoyancyOrganBlock> BUOYANCY_ORGAN; // line 196

  public static final DeferredBlock<Block> FLESH; // line 206

  public static final DeferredBlock<Block> ARMOR_II; // line 209

  public static final DeferredBlock<CaptainSeatBlock> PERCH; // line 215

  public static final DeferredBlock<CarapaceBlock> CARAPACE; // line 225

  public static final DeferredBlock<TransparentBlock> MEMBRANE; // line 234

  public static final DeferredBlock<LivingDoorBlock> DOOR; // line 243

  public static final DeferredBlock<AmoebaLegBlock> AMOEBA_LEG; // line 252

  public static final DeferredBlock<AmoebaLegBlock> SWIMMING_LEG; // line 261

  public static final DeferredBlock<RespiratoryOrganBlock> LUNGS; // line 270

  public static final DeferredBlock<RespiratoryOrganBlock> GILLS; // line 279

  public static final DeferredBlock<TentacleBlock> TENTACLE; // line 288

  public static final java.util.List<DeferredBlock<NerveControlBlock>> CONTROLS; // line 297

  public static final DeferredItem<DeveloperTestTool> DEVELOPER_TEST_TOOL; // line 313

  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH; // line 316

  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES; // line 319

  public static final DeferredItem<BlockItem> CORE_ITEM; // line 322

  public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> DOOR_ITEM; // line 324

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LivingCoreBlockEntity>> CORE_ENTITY; // line 338

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StomachBlockEntity>> STOMACH_ENTITY; // line 347

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedingMouthBlockEntity>> FEEDING_MOUTH_ENTITY; // line 354

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentacleBlockEntity>> TENTACLE_ENTITY; // line 361

  public Create_bioships(IEventBus bus); // line 391

```


#### LivingCoreBlock

Source: `src/main/java/org/Azkerash/create_bioships/LivingCoreBlock.java` (181 lines).

```java
public class LivingCoreBlock extends BaseEntityBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE; // line 15

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty BLINK; // line 17

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty RELOCATING; // line 19

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty CONCEALED; // line 21

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 23

  public static final MapCodec<LivingCoreBlock> CODEC; // line 25

  public LivingCoreBlock(Properties properties); // line 26

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder< net.minecraft.world.level.block.Block, BlockState> builder); // line 38

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 46

  public BlockState getStateForPlacement( net.minecraft.world.item.context.BlockPlaceContext context); // line 51

  public void setPlacedBy( Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack); // line 66

  protected void onPlace( BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved); // line 81

  protected void tick( BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random); // line 88

  protected void onRemove( BlockState state, Level level, BlockPos pos, BlockState next, boolean moved); // line 97

  public void beforeMove( net.minecraft.server.level.ServerLevel source, net.minecraft.server.level.ServerLevel destination, BlockState state, BlockPos from, BlockPos to); // line 106

  public void afterMove( net.minecraft.server.level.ServerLevel source, net.minecraft.server.level.ServerLevel destination, BlockState state, BlockPos from, BlockPos to); // line 117

  protected RenderShape getRenderShape(BlockState state); // line 128

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 133

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 138

  protected ItemInteractionResult useItemOn( ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit); // line 146

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 160

  public void attack(BlockState state, Level level, BlockPos pos, Player player); // line 168

  protected java.util.List<ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 174

```


### Creature lifecycle coordinator [active]

Owns subsystem instances and saved state; schedules game ticks and publishes physics intent.

```text
[core]
  LivingCoreBlockEntity
```


#### LivingCoreBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/LivingCoreBlockEntity.java` (1303 lines).

```java
public class LivingCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, org.Azkerash.create_bioships.amoeba.AmoebaComponentProvider

  private final org.Azkerash.create_bioships.amoeba.AmoebaComponentHost componentHost; // line 27

  public org.Azkerash.create_bioships.amoeba.AmoebaComponentHost amoebaComponents(); // line 29

  private static final TagKey<Item> MEAT; // line 33

  private static final TagKey<Item> LEVITITE; // line 38

  private BlockPos preferredMouth; // line 42

  public BlockPos preferredMouth(); // line 43

  public void preferredMouth(BlockPos pos); // line 47

  private ComponentTransport.Move organMove; // line 52

  private final BioshipAssimilation assimilation; // line 54

  public BioshipAssimilation assimilation(); // line 55

  public ComponentTransport.Move organMove(); // line 59

  void workingOrganMove(ComponentTransport.Move move); // line 63

  public void organMove(ComponentTransport.Move move); // line 67

  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(); // line 74

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 79

  private final BioshipNeeds needs; // line 84

  private final BioshipLootMemory loot; // line 86

  BioshipLootMemory loot(); // line 87

  private boolean awakened, resting; // line 91

  private java.util.UUID captain; // line 93

  private final BioshipAccess access; // line 94

  public java.util.UUID creator(); // line 95

  public void claimCreator(java.util.UUID owner); // line 99

  public BioshipAccess access(); // line 106

  private int bodyBlocks; // line 110

  private int stage, growthIndex, metabolismTicks; // line 112

  private final CoreMotion coreMotion; // line 113

  public CoreMotion coreMotion(); // line 114

  public BlockPos structuralOrigin(); // line 118

  private boolean hullBudgetAvailable(); // line 122

  boolean growthStockReady(); // line 135

  boolean growthSiteReady(); // line 139

  public double coreHeightOffset(); // line 146

  private final BioshipVision vision; // line 155

  public BioshipVision vision(); // line 157

  BlockPos supplyTarget; // line 161

  private final BioshipRescue rescue; // line 163

  public BioshipRescue rescue(); // line 164

  private final BioshipAutonomy autonomy; // line 168

  public BioshipAutonomy autonomy(); // line 170

  int trustLevel(); // line 174

  private final BioshipDefense defense; // line 178

  public Vec3 attackTip(); // line 180

  public boolean attackSweeping(); // line 184

  BioshipDefense defense(); // line 188

  public boolean hatchling(); // line 192

  private int bodyLayoutVersion; // line 196

  public int layoutVersion(); // line 198

  public java.util.List<DinghyGrowth.Part> plannedBody(); // line 202

  private String growthObstacle; // line 206

  private long lastServerTick; // line 208

  private final BioshipAquatic aquatic; // line 209

  private final BioshipEngineRoom engineRoom; // line 210

  BioshipEngineRoom engineRoom(); // line 211

  public BioshipAquatic aquatic(); // line 215

  private double hoverHeight; // line 219

  private volatile FlightIntent flight; // line 221

  void inheritFlight(LivingCoreBlockEntity previous); // line 222

  private record FlightIntent( boolean lift, Vec3 velocity, double turn, double supportHeight, Vec3 stanceUp); // line 226

  public LivingCoreBlockEntity(BlockPos pos, BlockState state); // line 233

  ServerSubLevel ship(); // line 237

  boolean primary(ServerSubLevel ship); // line 245

  public void interact(Player player, ItemStack stack); // line 253

  private int goggleFlesh, goggleBone, goggleFiber; // line 291

  public java.util.List<Component> goggleTooltip(); // line 293

  public boolean feedAtMouth(Player player, ItemStack stack); // line 389

  public boolean feedAtMouth(BlockPos mouth, Player player, ItemStack stack); // line 393

  public static boolean isMeat(ItemStack stack); // line 425

  final BioshipTrustFeedback trustFeedback; // line 429

  int poisonSeconds(); // line 431

  public void digestMeal(ItemStack stack); // line 435

  public boolean acceptAmalgam(ItemStack stack); // line 454

  private final BioshipScavenging scavenging; // line 462

  public BioshipScavenging scavenging(); // line 464

  private final BioshipCrawl crawl; // line 468

  public BioshipCrawl crawl(); // line 470

  private final BioshipSurvivalAdaptations survival; // line 474

  public BioshipSurvivalAdaptations survival(); // line 476

  private final BioshipAdaptation adaptation; // line 480

  BioshipAdaptation adaptation(); // line 482

  private final BioshipBody body; // line 486

  private final BioshipBlueprint blueprint; // line 488

  public BioshipBody body(); // line 489

  public BioshipBlueprint blueprint(); // line 493

  public BlockPos layoutPos(BlockPos offset); // line 497

  final BioshipChunks chunkLoading; // line 502

  private final BioshipBrain brain; // line 504

  public BioshipBrain brain(); // line 505

  int satiety(); // line 509

  boolean frameFunded(); // line 513

  boolean buildingHull(); // line 517

  private boolean framePaid, larynxDeveloped; // line 521

  public boolean wantsStoredFood(); // line 523

  private boolean prepareFrame(); // line 532

  private void developLarynx(ServerSubLevel ship); // line 565

  public int incorporateBiomass(int amount); // line 589

  public int missingBiomass(); // line 595

  private static void message(Player player, String key, Object... args); // line 599

  public void mistreat(); // line 603

  private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos); // line 611

  private void recordGrowthObstacle(ServerSubLevel ship, BlockPos offset); // line 620

  private boolean roomToGrow(ServerSubLevel ship); // line 638

  private net.minecraft.server.level.ServerLevel transferLevel; // line 659

  private BlockPos transferTarget; // line 661

  void recordTransfer(net.minecraft.server.level.ServerLevel destination, BlockPos pos); // line 662

  boolean hasTransferReceipt(); // line 667

  boolean transferred(); // line 671

  void coreRemoved(); // line 679

  public void setRemoved(); // line 689

  public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core); // line 696

  private void updateCabinLights(); // line 808

  private void growBody(); // line 819

  public void sable$tick(ServerSubLevel ship); // line 895

  private void serverTick(); // line 900

  private void incorporateAmalgam(); // line 1007

  public boolean canFly(); // line 1016

  private void updateEye(boolean active); // line 1025

  public boolean isMature(); // line 1038

  public boolean resting(); // line 1042

  public boolean aggressive(); // line 1046

  public boolean isCaptain(net.minecraft.world.entity.Entity entity); // line 1050

  public boolean huntingAllowed(); // line 1054

  public int foodLevel(); // line 1062

  public boolean starving(); // line 1066

  public void adoptLarynx(); // line 1070

  public boolean wantsToHunt(); // line 1075

  public boolean hungry(); // line 1079

  public boolean hasMouth(); // line 1083

  public BlockPos mouthBlock(); // line 1087

  public Vec3 mouthPosition(ServerSubLevel ship); // line 1109

  public int swimmingFlagella(); // line 1121

  Vec3 gatheringGoal(); // line 1129

  static boolean droppedSupply(ItemStack stack); // line 1134

  public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt); // line 1142

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 1196

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 1246

```


### World loading [active]

View and simulation leases follow the creature; tickets release on removal or death.

```text
[chunks]
  BioshipChunks
```


#### BioshipChunks

Source: `src/main/java/org/Azkerash/create_bioships/BioshipChunks.java` (110 lines).

```java
public final class BioshipChunks

  private static final Set<BioshipChunks> ACTIVE; // line 20

  private static final TicketType<UUID> VIEW; // line 21

  private static final TicketType<UUID> SIMULATION; // line 23

  private record Lease(ServerLevel level, ChunkPos center, UUID owner, int view, int simulation); // line 25

  private Lease lease; // line 27

  private long refreshed; // line 29

  void update(LivingCoreBlockEntity core); // line 30

  void release(); // line 58

  public static void keepLivingSimulation( net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event); // line 77

  public static void stopping(ServerStoppingEvent event); // line 87

  boolean active(); // line 95

  int viewDistance(); // line 99

  int simulationDistance(); // line 104

```


### Body geometry and reach [active]

Read loaded Sable cells, integrate native machines, distinguish visibility from physical reach.

```text
[geometry]
  BioshipStructure
  BioshipIntegration
  BioshipReach
  BodyTopology
  BioshipCoreLifecycle
```


#### BioshipStructure

Source: `src/main/java/org/Azkerash/create_bioships/BioshipStructure.java` (52 lines).

```java
public final class BioshipStructure

  public static void visit(SubLevel ship, BiConsumer<BlockPos, BlockState> visitor); // line 10

  static Map<BlockPos, BlockState> cells(SubLevel ship); // line 33

  static List<BlockPos> positions(SubLevel ship); // line 39

  static int count(SubLevel ship); // line 45

```


#### BioshipIntegration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipIntegration.java` (303 lines).

```java
public final class BioshipIntegration

  private static final String BOUND; // line 25

  private static final TagKey<net.minecraft.world.level.block.Block> MACHINES; // line 26

  private static final TagKey<net.minecraft.world.level.block.Block> FLESH; // line 30

  private static final TagKey<Item> MATERIAL; // line 34

  public static TagKey<Item> constructionMaterial(); // line 38

  public static void bind(ServerSubLevel ship); // line 42

  public static boolean bound(SubLevel ship); // line 52

  public static ServerSubLevel containing(Level level, BlockPos pos); // line 60

  public static void awaken(ServerLevel level, BlockPos pos); // line 67

  public static boolean isMaw(BlockState state); // line 76

  public static boolean isIntegratedMachine(Level level, BlockPos pos); // line 83

  public static List<net.minecraft.world.level.block.entity.BlockEntity> blockEntities( ServerSubLevel ship); // line 92

  public static int countBlocks(ServerSubLevel ship); // line 100

  static boolean knownReachableSource(LivingCoreBlockEntity core, BlockPos source); // line 104

  public static void assimilate(LivingCoreBlockEntity core, ServerSubLevel ship); // line 119

  static void collect(LivingCoreBlockEntity core); // line 125

  public static boolean exposedAndVisible(ServerLevel level, BlockPos eye, BlockPos source); // line 131

  static boolean growthSpace(Level level, BlockPos pos); // line 145

  static boolean aquaticGrowthSpace(Level level, BlockPos pos); // line 150

  static void clearGrowthPlant(Level level, BlockPos pos); // line 157

  public static void finishAssimilation(LivingCoreBlockEntity core, BlockPos target); // line 162

  static void convertFirstBioForge(LivingCoreBlockEntity core, BlockPos pos); // line 178

  static void orientMouth(LivingCoreBlockEntity core); // line 203

  public static void relocateMouth(LivingCoreBlockEntity core, ServerSubLevel ship); // line 224

  public static void routeBioshipWrench(PlayerInteractEvent.RightClickBlock event); // line 272

  public static void feedMaw(PlayerInteractEvent.RightClickBlock event); // line 280

```


#### BioshipReach

Source: `src/main/java/org/Azkerash/create_bioships/BioshipReach.java` (63 lines).

```java
final class BioshipReach

  static Vec3 anchor(LivingCoreBlockEntity core, Vec3 target); // line 7

  static boolean at(LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget); // line 23

  static boolean at( LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget, double reach); // line 27

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget, double reach); // line 54

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget); // line 58

```


#### BodyTopology

Source: `src/main/java/org/Azkerash/create_bioships/BodyTopology.java` (62 lines).

```java
final class BodyTopology

  static Set<BlockPos> structuralCells(Map<BlockPos, BlockState> cells); // line 8

  static int components(Set<BlockPos> cells); // line 18

  static boolean canRemove(Set<BlockPos> cells, BlockPos source); // line 38

  static boolean canMove( Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource); // line 45

  static boolean canCommit( Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource); // line 53

```


#### BioshipCoreLifecycle

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCoreLifecycle.java` (60 lines).

```java
final class BioshipCoreLifecycle

  private record Removal( LivingCoreBlockEntity source, ServerSubLevel owner, ServerLevel level, BlockPos pos); // line 14

  private static final Map<LivingCoreBlockEntity, Removal> pending; // line 16

  static void removed(LivingCoreBlockEntity core); // line 20

  public static void tick(ServerTickEvent.Post event); // line 27

  public static void stop(ServerStoppedEvent event); // line 52

  private BioshipCoreLifecycle(); // line 57

```


### Perception and memory [active]

Directional cones, light, occlusion, observations and three-real-second target memory.

```text
[vision]
  BioshipVision
  TargetMemory
  BioshipPriorities
  BioshipLootMemory
```


#### BioshipVision

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVision.java` (635 lines).

```java
public final class BioshipVision

  static final MaterialCost EYE_COST; // line 10

  private List<BlockPos> eyes; // line 11

  private long scanned; // line 12

  private void scan(LivingCoreBlockEntity core); // line 13

  public record View(Vec3 origin, Vec3 forward, double range); // line 37

  public int maximumDistance(LivingCoreBlockEntity core); // line 45

  static int distanceLimit(int serverChunks, net.minecraft.server.level.ServerPlayer observer); // line 61

  boolean inView(LivingCoreBlockEntity core, View view, Vec3 point); // line 67

  static boolean clearMaterial( net.minecraft.world.level.block.state.BlockState state, Vec3 direction); // line 82

  void invalidate(); // line 104

  private record Sighting( net.minecraft.world.level.block.state.BlockState state, long seen, boolean full); // line 108

  private final Map<BlockPos, Sighting> remembered; // line 111

  private final TargetMemory<java.util.UUID> entityFreshness; // line 113

  private net.minecraft.world.entity.LivingEntity trackedEntity; // line 114

  private Vec3 trackedPoint; // line 115

  void rememberEntity(net.minecraft.world.entity.LivingEntity entity); // line 116

  net.minecraft.world.entity.LivingEntity rememberedEntity(LivingCoreBlockEntity core); // line 123

  Vec3 knownPosition(net.minecraft.world.entity.LivingEntity entity); // line 135

  Vec3 focus(LivingCoreBlockEntity core); // line 141

  private final TargetMemory<BlockPos> sourceFreshness; // line 153

  long unseenNanos(BlockPos pos); // line 155

  void forget(LivingCoreBlockEntity core, BlockPos pos); // line 159

  java.util.Set<BlockPos> rememberedSources(); // line 165

  net.minecraft.world.level.block.state.BlockState rememberedState(BlockPos pos); // line 169

  boolean rememberedFull(BlockPos pos); // line 174

  net.minecraft.world.level.block.state.BlockState sourceState( LivingCoreBlockEntity core, BlockPos source); // line 178

  private int surveyed; // line 212

  public List<View> views(LivingCoreBlockEntity core); // line 214

  boolean knows(LivingCoreBlockEntity core, BlockPos source); // line 243

  boolean surveyed(LivingCoreBlockEntity core); // line 247

  void look(LivingCoreBlockEntity core, Direction face); // line 251

  private void turn(LivingCoreBlockEntity core); // line 259

  static boolean exposedTube(net.minecraft.world.level.block.state.BlockState state); // line 276

  static boolean ray(LivingCoreBlockEntity core, Vec3 from, Vec3 to); // line 280

  static boolean rayAtCore(LivingCoreBlockEntity core, Vec3 from, Vec3 to, BlockPos origin); // line 284

  public boolean visible(LivingCoreBlockEntity core, Vec3 point); // line 321

  static boolean openToNature(net.minecraft.world.level.Level level, BlockPos source); // line 328

  boolean exposed(LivingCoreBlockEntity core, BlockPos source); // line 344

  void observe(LivingCoreBlockEntity core); // line 348

  boolean externalEyesPreferred(LivingCoreBlockEntity core); // line 352

  void tick(LivingCoreBlockEntity core); // line 364

  net.minecraft.nbt.CompoundTag save(); // line 374

  void load( net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries); // line 392

  boolean reabsorb(LivingCoreBlockEntity core); // line 419

  boolean trackFocus(LivingCoreBlockEntity core); // line 473

  void growEyes(LivingCoreBlockEntity core); // line 549

```


#### TargetMemory

Source: `src/main/java/org/Azkerash/create_bioships/TargetMemory.java` (57 lines).

```java
final class TargetMemory<K>

  static final long TIMEOUT; // line 8

  private record Seen(long nanos, long epochMillis); // line 9

  private final Map<K, Seen> seen; // line 11

  private final LongSupplier nanos, millis; // line 13

  TargetMemory(); // line 14

  TargetMemory(LongSupplier nanos, LongSupplier millis); // line 18

  void observe(K key); // line 23

  boolean retains(K key); // line 27

  long age(K key); // line 31

  long observedMillis(K key); // line 36

  void restore(K key, long observedMillis); // line 41

  void forget(K key); // line 48

  void clear(); // line 52

```


#### BioshipPriorities

Source: `src/main/java/org/Azkerash/create_bioships/BioshipPriorities.java` (136 lines).

```java
final class BioshipPriorities

  record Objective(BlockPos pos, int rank); // line 11

  static int rank(LivingCoreBlockEntity core, BlockState state, BlockPos pos); // line 12

  private static int rank( LivingCoreBlockEntity core, BlockState state, BlockPos pos, boolean observed); // line 16

  private record Sources(long at, BlockPos center, int range, java.util.List<BlockPos> positions); // line 59

  private static final java.util.Map<LivingCoreBlockEntity, Sources> SOURCE_CACHE; // line 61

  static boolean supply(BlockState state); // line 64

  private static java.util.List<BlockPos> sources( LivingCoreBlockEntity core, ServerLevel level, Vec3 here, int range); // line 69

  static java.util.List<Objective> worldObjectives(LivingCoreBlockEntity core); // line 108

  static Objective bestWorld(LivingCoreBlockEntity core); // line 131

```


#### BioshipLootMemory

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLootMemory.java` (77 lines).

```java
final class BioshipLootMemory

  record Target(UUID id, Vec3 position, ItemEntity item); // line 11

  private record Known(Vec3 position, long at); // line 12

  private final Map<UUID, Known> drops; // line 14

  void remember(ItemEntity item, long at); // line 16

  boolean knows(Vec3 point); // line 21

  List<Target> targets(LivingCoreBlockEntity core); // line 26

  CompoundTag save(); // line 49

  void load(CompoundTag data); // line 64

```


### Organic surfaces [active]

Eye mounting, window faces and reusable body block shapes. No automatic staged hull.

```text
[surfaces]
  SurfaceEyeBlock
  OneWayWindowBlock
  CarapaceBlock
  PulseLightBlock
```


#### SurfaceEyeBlock

Source: `src/main/java/org/Azkerash/create_bioships/SurfaceEyeBlock.java` (98 lines).

```java
public final class SurfaceEyeBlock extends DirectionalBlock

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty WINDOW_MOUNTED; // line 12

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty WINDOW_INTERNAL; // line 15

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty AUTOMATIC; // line 19

  public static final MapCodec<SurfaceEyeBlock> CODEC; // line 21

  public SurfaceEyeBlock(Properties properties); // line 22

  protected MapCodec<? extends DirectionalBlock> codec(); // line 33

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 38

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 43

  static BlockState mounted(BlockState state, LevelAccessor level, BlockPos pos); // line 51

  protected BlockState updateShape( BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos); // line 62

  protected void onPlace( BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved); // line 73

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 83

```


#### OneWayWindowBlock

Source: `src/main/java/org/Azkerash/create_bioships/OneWayWindowBlock.java` (33 lines).

```java
public class OneWayWindowBlock extends HorizontalDirectionalBlock

  public static final MapCodec<OneWayWindowBlock> CODEC; // line 8

  public OneWayWindowBlock(Properties properties); // line 9

  protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side); // line 14

  protected MapCodec<? extends HorizontalDirectionalBlock> codec(); // line 22

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 27

```


#### CarapaceBlock

Source: `src/main/java/org/Azkerash/create_bioships/CarapaceBlock.java` (29 lines).

```java
public class CarapaceBlock extends HorizontalDirectionalBlock

  public static final MapCodec<CarapaceBlock> CODEC; // line 12

  private static final VoxelShape NORTH; // line 13

  public CarapaceBlock(Properties properties); // line 14

  protected MapCodec<? extends HorizontalDirectionalBlock> codec(); // line 15

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 16

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 17

  protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 18

```


#### PulseLightBlock

Source: `src/main/java/org/Azkerash/create_bioships/PulseLightBlock.java` (30 lines).

```java
public class PulseLightBlock extends Block

  public static final BooleanProperty LIT; // line 9

  public PulseLightBlock(Properties properties); // line 10

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 15

  protected net.minecraft.world.phys.shapes.VoxelShape getShape( BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context); // line 20

```


### Needs and food policy [active]

Food, trust, poison and urgent food-task classification; pressing food threshold is 50.

```text
[needs]
  BioshipNeeds
  BioshipNutrition
```


#### BioshipNeeds

Source: `src/main/java/org/Azkerash/create_bioships/BioshipNeeds.java` (133 lines).

```java
public final class BioshipNeeds

  public static final int BIOMASS_REQUIRED, LEVITITE_REQUIRED, MAX_SATIETY; // line 4

  private int biomass, levitite, satiety, satiety, trust; // line 5

  public int biomass(); // line 6

  public int levitite(); // line 10

  public int satiety(); // line 14

  public int trust(); // line 18

  public int poisonSeconds(); // line 22

  public boolean aggressive(); // line 26

  public int incorporateBiomass(int amount); // line 30

  public boolean canEatMeat(); // line 37

  public boolean feedMeat(int nutrition, boolean grown); // line 41

  public void digestFedMeal(int nutrition); // line 49

  public void consumeHuntedPrey(boolean toxic); // line 55

  public void poison(); // line 61

  public void restorePoison(int seconds); // line 67

  public boolean feedLevitite(); // line 71

  public boolean readyToGrow(); // line 77

  public boolean flightNourished(); // line 81

  public static int foodDemand(int bodyBlocks); // line 85

  public void metabolize(); // line 89

  public void metabolize(int bodyBlocks); // line 93

  public void metabolizeDemand(int demand); // line 97

  public void emergencyEnergy(int amount); // line 103

  public void mistreat(); // line 108

  public String mood(); // line 112

  public double obedience(); // line 120

  public void restore(int biomass, int levitite, int satiety, int trust); // line 125

```


#### BioshipNutrition

Source: `src/main/java/org/Azkerash/create_bioships/BioshipNutrition.java` (90 lines).

```java
final class BioshipNutrition

  static final int PRESSING_FOOD; // line 7

  static boolean critical(LivingCoreBlockEntity core); // line 8

  static int fuelValue(ItemStack stack); // line 12

  static boolean foodRelated(LivingCoreBlockEntity core, BioshipBrain.Decision decision); // line 26

  static boolean inventoryFuel(LivingCoreBlockEntity core, net.minecraft.core.BlockPos pos); // line 60

  static boolean movingFood(LivingCoreBlockEntity core); // line 70

```


### Per-block energy demand [active]

Read body cells; zero-cost producers/connectors; ordinary blocks contribute upkeep.

```text
[energy]
  BioshipEnergy
  BlockEnergyTable
```


#### BioshipEnergy

Source: `src/main/java/org/Azkerash/create_bioships/BioshipEnergy.java` (33 lines).

```java
final class BioshipEnergy

  private record Snapshot(long tick, int units); // line 7

  private static final Map<LivingCoreBlockEntity, Snapshot> CACHE; // line 8

  static int demand(LivingCoreBlockEntity core); // line 11

  static int units(LivingCoreBlockEntity core); // line 15

```


#### BlockEnergyTable

Source: `src/main/java/org/Azkerash/create_bioships/BlockEnergyTable.java` (27 lines).

```java
final class BlockEnergyTable

  static final Map<String, Integer> COSTS; // line 6

  static int units(String id); // line 14

  static int demand(int units); // line 18

  static int pairs(int demand); // line 22

```


### Behavior selection and goal queue [active]

Safety branches, committed work, NEED before GOAL, weighted ranking, one selected Decision per call.

```text
[brain]
  BioshipBrain
  BehaviorPriority
  GoalQueue
```


#### BioshipBrain

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBrain.java` (676 lines).

```java
public final class BioshipBrain

  public enum Action; // line 19

  public record Decision( Action action, Vec3 point, BlockPos block, LivingEntity entity, ItemEntity item, BehaviorPriority.Offer offer, String reason, boolean recycle); // line 55

  private record Needs( boolean digestive, boolean hungry, int food, int flesh, int bone, int fiber, boolean growth, boolean criticalFood); // line 80

  private String previous, reason; // line 94

  private Action action; // line 96

  private boolean growthBlocked; // line 97

  private Decision lastDecision; // line 98

  private final GoalQueue<Decision> goals; // line 99

  java.util.List<Decision> queuedGoals(); // line 100

  private void refreshGoals(List<Decision> proposals); // line 104

  private net.minecraft.nbt.ListTag debugPriorities; // line 108

  private int debugOfferCount; // line 110

  net.minecraft.nbt.ListTag priorities(); // line 111

  int offerCount(); // line 115

  private void rankForDebug(List<Decision> offers); // line 119

  Decision lastDecision(); // line 138

  public Action action(); // line 142

  public String reason(); // line 146

  void growthBlocked(boolean blocked); // line 150

  private Needs needs(LivingCoreBlockEntity core); // line 154

  boolean shouldHunt(LivingCoreBlockEntity core); // line 237

  private Decision fixed(Action next, String why); // line 253

  private Decision remember(Decision selected); // line 265

  private Decision offer( Action next, Vec3 point, BlockPos block, LivingEntity entity, ItemEntity item, BehaviorPriority.Band band, int urgency, int value, double effort, String why); // line 279

  private Decision chooseOffers(List<Decision> offers); // line 306

  private double effort(LivingCoreBlockEntity core, Vec3 point); // line 311

  boolean interruptsForFood(LivingCoreBlockEntity core); // line 315

  private int materialUrgency(Needs n, BlockState state, boolean wreck); // line 319

  private int itemUrgency(Needs n, ItemStack stack); // line 331

  private Decision source( LivingCoreBlockEntity core, Needs n, BlockPos pos, Vec3 point, BlockState state, int rank, boolean wreck); // line 346

  BioshipPriorities.Objective bestWorld(LivingCoreBlockEntity core); // line 411

  Decision choose(LivingCoreBlockEntity core); // line 430

  boolean allowsConfiguration(LivingCoreBlockEntity core); // line 583

  MaterialCost materialNeeds(LivingCoreBlockEntity core); // line 587

  boolean needsMaterials(LivingCoreBlockEntity core); // line 592

  LivingEntity attackTarget(LivingCoreBlockEntity core); // line 596

  Vec3 groundGoal(LivingCoreBlockEntity core); // line 603

  Vec3 flightVelocity(LivingCoreBlockEntity core); // line 639

  CompoundTag save(); // line 654

  void load(CompoundTag tag); // line 662

```


#### BehaviorPriority

Source: `src/main/java/org/Azkerash/create_bioships/BehaviorPriority.java` (31 lines).

```java
final class BehaviorPriority

  enum Band; // line 6

  record Offer(String key, Band band, int urgency, int value, double effort); // line 12

  static Comparator<Offer> order(String previous); // line 18

  static Offer choose(Collection<Offer> offers, String previous); // line 24

  private BehaviorPriority(); // line 28

```


#### GoalQueue

Source: `src/main/java/org/Azkerash/create_bioships/GoalQueue.java` (23 lines).

```java
final class GoalQueue<T>

  private List<T> pending; // line 7

  List<T> pending(); // line 8

  void refresh( Collection<T> candidates, Function<T, BehaviorPriority.Offer> priority, String previous); // line 12

```


### Escape and exploration [active]

Threat assessment, retreat and idle wandering. Pilot authority is currently disabled.

```text
[autonomy]
  BioshipAutonomy
```


#### BioshipAutonomy

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAutonomy.java` (222 lines).

```java
public final class BioshipAutonomy

  private Vec3 wander, retreat, danger; // line 9

  private long nextWander, panicUntil, evaluated, evaluated; // line 10

  private boolean fleeing; // line 11

  private long creeperUntil; // line 12

  public boolean creeperPanic(); // line 13

  private long coreTime; // line 17

  private java.util.UUID pilot; // line 19

  private long commandUntil; // line 20

  void command(LivingCoreBlockEntity core, net.minecraft.server.level.ServerPlayer player); // line 21

  boolean trustedPilot(LivingCoreBlockEntity core); // line 28

  public boolean fleeing(); // line 65

  void assess(LivingCoreBlockEntity core); // line 69

  void activity(LivingCoreBlockEntity core); // line 104

  Vec3 groundEscape(LivingCoreBlockEntity core); // line 109

  Vec3 groundWander(LivingCoreBlockEntity core); // line 129

  Vec3 flight(LivingCoreBlockEntity core, boolean escape); // line 157

  CompoundTag save(); // line 193

  void load(CompoundTag tag); // line 207

```


### Ground navigation and gait [active]

Full-body routes, stuck handling, paid legs, footing, speed and accessible front-mouth configuration.

```text
[route]
  BioshipCrawl
  GroundRoute
  HatchlingFront
```


#### BioshipCrawl

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCrawl.java` (606 lines).

```java
public final class BioshipCrawl

  private Vec3 goal; // line 10

  private Vec3 lastStepPosition; // line 11

  private long gaitAt; // line 12

  private double gaitSpeed, gaitPhase; // line 13

  public boolean animating(); // line 14

  public double gaitSpeed(); // line 18

  public double gaitPhase(); // line 22

  public long gaitAt(); // line 26

  private void measureGait(LivingCoreBlockEntity core); // line 30

  private boolean walking; // line 43

  private String mode; // line 45

  public String mode(); // line 46

  public boolean running(); // line 50

  private final BioshipLegAnchors legAnchors; // line 54

  public BioshipLegAnchors legAnchors(); // line 56

  private int legTier; // line 60

  private double measuredLoad; // line 62

  public double measuredLoad(); // line 63

  public int legTier(); // line 67

  public int extraLift(); // line 71

  public int footingReach(); // line 75

  public double legThickness(); // line 79

  public double loadCapacity(); // line 83

  static double capacityFor(int tier); // line 87

  int hullTier(LivingCoreBlockEntity core); // line 91

  double blockLoad( LivingCoreBlockEntity core, BlockPos pos, net.minecraft.world.level.block.state.BlockState state); // line 105

  public double carriedLoad(LivingCoreBlockEntity core); // line 117

  boolean canEnhance(LivingCoreBlockEntity core); // line 127

  private void payEnhancement(LivingCoreBlockEntity core); // line 137

  boolean enhance(LivingCoreBlockEntity core); // line 147

  private boolean supportLoad(LivingCoreBlockEntity core); // line 154

  private int cursor; // line 161

  private final GroundRoute route; // line 163

  private String selection; // line 164

  private void retireChangedSelection(LivingCoreBlockEntity core); // line 165

  private final Map<BlockPos, Long> unreachable; // line 171

  net.minecraft.nbt.CompoundTag navigationDebug(long now); // line 173

  public java.util.List<Vec3> path(); // line 182

  boolean reachableGoal(BlockPos pos); // line 186

  private long currentTime; // line 190

  void lowerHeight(double height); // line 192

  void stepHeight(double height); // line 196

  private boolean gathering; // line 200

  private Direction wallFacing; // line 202

  private double wallDistance; // line 203

  public Direction wallFacing(); // line 204

  public double wallDistance(); // line 208

  void clingHeight(double height); // line 212

  private Vec3 reach; // line 216

  public Vec3 reach(); // line 218

  void reach(Vec3 point); // line 222

  private double supportHeight; // line 226

  private Vec3 stanceUp; // line 228

  public boolean bracing(); // line 229

  public double supportHeight(); // line 233

  public Vec3 stanceUp(); // line 237

  public boolean walking(); // line 241

  public boolean gathering(); // line 245

  void stop(); // line 249

  Vec3 steeringGoal(); // line 258

  public Vec3 goal(); // line 263

  void tick(LivingCoreBlockEntity core, boolean blocked); // line 267

  boolean clear(LivingCoreBlockEntity core, Vec3 candidate); // line 341

  Vec3 velocity(LivingCoreBlockEntity core); // line 379

  private BlockPos ground(ServerLevel level, BlockPos point); // line 415

  private BlockPos ground(ServerLevel level, BlockPos point, double highestTop); // line 419

  private void braceGrip(LivingCoreBlockEntity core, Vec3 here, GroundRoute.Grip grip); // line 430

  private void brace(LivingCoreBlockEntity core); // line 446

  CompoundTag save(); // line 539

  void load(CompoundTag t); // line 571

```


#### GroundRoute

Source: `src/main/java/org/Azkerash/create_bioships/GroundRoute.java` (465 lines).

```java
final class GroundRoute

  private List<Vec3> path; // line 9

  private Vec3 destination; // line 10

  private int cursor; // line 11

  private long retryAfter; // line 12

  private boolean failed; // line 13

  private boolean complete; // line 14

  private String debugStatus; // line 15

  private long plannedAt; // line 16

  net.minecraft.nbt.CompoundTag debug(long now); // line 17

  void clear(); // line 28

  boolean complete(); // line 39

  boolean failed(); // line 43

  List<Vec3> remaining(); // line 47

  List<Vec3> points(); // line 51

  record Grip(Direction direction, double distance); // line 55

  static Grip grip(LivingCoreBlockEntity core, Vec3 point); // line 57

  private List<Vec3> offsets(LivingCoreBlockEntity core); // line 61

  private boolean fits(LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets); // line 71

  private boolean fits( LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets, double clearance); // line 75

  private Vec3 standing( LivingCoreBlockEntity core, int x, int z, int floorTop, List<Vec3> offsets, double currentHeight); // line 92

  private boolean firstStepClear(LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts); // line 124

  static boolean turnClear(LivingCoreBlockEntity core, double angle); // line 136

  static Vec3 groundedDestination(LivingCoreBlockEntity core, Vec3 requested); // line 152

  static boolean flightClear(LivingCoreBlockEntity core, Vec3 target); // line 171

  private boolean supportedAt(LivingCoreBlockEntity core, Vec3 point); // line 181

  private boolean lineClear( LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts, double clearance); // line 196

  private List<Vec3> simplify( LivingCoreBlockEntity core, Vec3 from, List<Vec3> original, List<Vec3> parts); // line 207

  boolean plan(LivingCoreBlockEntity core, Vec3 goal); // line 237

  Vec3 next(LivingCoreBlockEntity core, Vec3 goal); // line 393

```


#### HatchlingFront

Source: `src/main/java/org/Azkerash/create_bioships/HatchlingFront.java` (133 lines).

```java
final class HatchlingFront

  static BlockPos port(LivingCoreBlockEntity core, BlockPos source); // line 9

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos source, BlockPos target); // line 41

  static boolean canArrange(LivingCoreBlockEntity core); // line 59

  static void arrangeMouth(LivingCoreBlockEntity core); // line 82

  static double turn(LivingCoreBlockEntity core, Vec3 goal); // line 119

```


### Balance and self-righting [active]

Mass-weighted COM, leg attachments, support polygon, reachable contacts and inverted recovery torque.

```text
[support]
  BioshipSupport
  BioshipRighting
  BioshipLegAnchors
  BioshipLegGrowth
  BioshipMountGrowth
  AmoebaLegBlock
  BioshipAnatomy
  BioshipLegEvolution
  LegUsageHistory
```


#### BioshipSupport

Source: `src/main/java/org/Azkerash/create_bioships/BioshipSupport.java` (334 lines).

```java
public final class BioshipSupport

  public record Leg(Vec3 root, Vec3 foot, Direction face, boolean bootstrap, boolean swimming); // line 14

  private static final List<java.util.function.Function<LivingCoreBlockEntity, List<Leg>>> LEG_PROVIDERS; // line 47

  public static void registerLegProvider( java.util.function.Function<LivingCoreBlockEntity, List<Leg>> provider); // line 50

  public record Layout(Vec3 center, List<Leg> legs, double margin); // line 57

  private record Cached(long time, Layout layout); // line 63

  private static final Map<LivingCoreBlockEntity, Cached> CACHE; // line 65

  private static Map<BlockPos, BlockState> cells(LivingCoreBlockEntity core); // line 68

  public static Layout layout(LivingCoreBlockEntity core); // line 80

  static Layout layout(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 89

  static boolean safeAddition(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 225

  static boolean safeMove( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh); // line 231

  static boolean safeMerge( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh, BlockState result); // line 236

  static Layout movedLayout( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh); // line 252

  static boolean safeCoreEntry(LivingCoreBlockEntity core, BlockPos target); // line 257

  private static Layout movedLayout( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh, boolean swapCoreHost); // line 261

  private static long invalidMounts(Map<BlockPos, BlockState> cells); // line 306

  static boolean containsProjection(Vec3 point, Vec3[] contacts); // line 315

```


#### BioshipRighting

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRighting.java` (65 lines).

```java
final class BioshipRighting

  record Support(double height); // line 8

  static Support support(LivingCoreBlockEntity core); // line 9

  static Vec3 torque(Vec3 up, Vec3 target, Vec3 right); // line 58

```


#### BioshipLegAnchors

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegAnchors.java` (156 lines).

```java
public final class BioshipLegAnchors

  public static final double TIP_RADIUS; // line 10

  private static final int STEP_TICKS; // line 11

  private final Vec3[] from, to, up; // line 12

  private final long[] started; // line 13

  public Vec3 normal(int leg); // line 14

  public AABB contactBox(int leg); // line 18

  public Vec3 foot(int leg, double time); // line 22

  void tick(LivingCoreBlockEntity core); // line 28

  Vec3 supportContact(ServerLevel level, int leg, long now); // line 108

  private static boolean touching(ServerLevel level, AABB box); // line 117

  CompoundTag save(); // line 129

  void load(CompoundTag tag); // line 143

```


#### BioshipLegGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegGrowth.java` (142 lines).

```java
final class BioshipLegGrowth

  static final MaterialCost COST; // line 9

  static int missing(LivingCoreBlockEntity core); // line 10

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 20

  static List<Map.Entry<BlockPos, BlockState>> sites( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 50

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 103

  static List<BioshipSupport.Leg> attachments( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 117

```


#### BioshipMountGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipMountGrowth.java` (212 lines).

```java
final class BioshipMountGrowth

  static final int URGENCY; // line 9

  record Plan(BlockPos pos, BlockState state, int legUpgrades, MaterialCost completeCost); // line 10

  private record Demand(long at, MaterialCost cost); // line 12

  private static final Map<LivingCoreBlockEntity, Demand> DEMAND; // line 14

  static MaterialCost demand(LivingCoreBlockEntity core); // line 16

  private record Step(BlockPos pos, BlockState state, double score); // line 27

  static Plan plan(LivingCoreBlockEntity core); // line 29

  private static int siteCount(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 161

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 166

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core, Plan plan); // line 170

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 188

  private BioshipMountGrowth(); // line 209

```


#### AmoebaLegBlock

Source: `src/main/java/org/Azkerash/create_bioships/AmoebaLegBlock.java` (92 lines).

```java
public final class AmoebaLegBlock extends DirectionalBlock

  public static final MapCodec<AmoebaLegBlock> CODEC; // line 14

  public AmoebaLegBlock(Properties properties); // line 15

  static Direction outward(LivingCoreBlockEntity core, BlockPos target); // line 20

  static boolean supportsFace(BlockState host, BlockPos hostPos, Direction face); // line 24

  static boolean canHostLeg(BlockState host, BlockPos hostPos, Direction face); // line 30

  static boolean mounted(java.util.Map<BlockPos, BlockState> cells, BlockPos pos, BlockState leg); // line 41

  static Direction outward(java.util.Map<BlockPos, BlockState> cells, BlockPos target); // line 47

  protected MapCodec<? extends DirectionalBlock> codec(); // line 55

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 60

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 65

  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos); // line 71

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 78

```


#### BioshipAnatomy

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAnatomy.java` (317 lines).

```java
final class BioshipAnatomy

  record Move(BlockPos source, BlockPos target, String reason, int urgency); // line 12

  private record Audit(long at, Move move); // line 13

  private static final Map<LivingCoreBlockEntity, Audit> CACHE; // line 15

  static final MaterialCost ARMOR_COST; // line 18

  static void observe(LivingCoreBlockEntity core); // line 19

  static void invalidate(LivingCoreBlockEntity core); // line 23

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 27

  static boolean execute(LivingCoreBlockEntity core, BlockPos source); // line 41

  private static Audit audit(LivingCoreBlockEntity core); // line 54

  static boolean mouthOpening(LivingCoreBlockEntity core, BlockPos pos); // line 68

  private static boolean free(LivingCoreBlockEntity core, BlockPos target); // line 78

  private static boolean canMove(LivingCoreBlockEntity core, BlockPos source, BlockPos target); // line 87

  private static List<BlockPos> ports(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 97

  private static Move opening(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 111

  private static Move legs(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 132

  static int anchorIndex( BlockPos origin, BlockPos socket, Direction face, BioshipSupport.Layout layout); // line 178

  static Move compact(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 192

  static BioshipAdaptation.Proposal protection(LivingCoreBlockEntity core); // line 234

```


#### BioshipLegEvolution

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegEvolution.java` (80 lines).

```java
final class BioshipLegEvolution

  static final MaterialCost SWIM_COST, LAND_COST; // line 8

  static boolean leg(BlockState state); // line 10

  static boolean swimming(BlockState state); // line 14

  static List<Map.Entry<BlockPos, BlockState>> legs(LivingCoreBlockEntity core); // line 18

  static double swimmingFraction(LivingCoreBlockEntity core); // line 25

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 32

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 63

```


#### LegUsageHistory

Source: `src/main/java/org/Azkerash/create_bioships/LegUsageHistory.java` (40 lines).

```java
final class LegUsageHistory

  private final ExposureHistory history; // line 4

  void observe(boolean wet); // line 5

  int samples(); // line 9

  int water(); // line 13

  double preciseWater(); // line 17

  void restore(int samples, double water); // line 21

  void restore(int samples, int water); // line 25

  int desired(int legs, int current); // line 29

```


### Swimming and breathing [active]

Saved air supply, all-mouth fluid checks, surfacing need and non-flight propulsion intent.

```text
[water]
  BioshipAquatic
  BioshipRespiration
  RespiratoryOrganBlock
  ExposureHistory
```


#### BioshipAquatic

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAquatic.java` (240 lines).

```java
public final class BioshipAquatic

  private int air; // line 13

  private long lastTick; // line 14

  private volatile boolean wet, swimming; // line 15

  private boolean waterContact; // line 16

  private boolean breathing, airAccess; // line 17

  private final ExposureHistory gillUsage; // line 18

  ExposureHistory gillUsage(); // line 19

  private volatile Vec3 velocity; // line 23

  private final LegUsageHistory legUsage; // line 25

  private long nextLegEvolution; // line 27

  LegUsageHistory legUsage(); // line 28

  long nextLegEvolution(); // line 32

  void legEvolved(long next); // line 36

  public boolean wet(); // line 40

  boolean waterContact(); // line 44

  public boolean swimming(); // line 48

  public int air(); // line 52

  boolean needsAir(); // line 56

  void tick(LivingCoreBlockEntity core); // line 60

  static boolean touchesWater(LivingCoreBlockEntity core); // line 125

  private static boolean waterAt(LivingCoreBlockEntity core, Vec3 point); // line 156

  static boolean canBreathe(LivingCoreBlockEntity core); // line 161

  static boolean aboveFluid(LivingCoreBlockEntity core, Vec3 point); // line 165

  private static boolean waterWithinReach(LivingCoreBlockEntity core, Vec3 here); // line 171

  private boolean clear(LivingCoreBlockEntity core, Vec3 offset); // line 179

  void propel(ServerSubLevel ship, RigidBodyHandle body, double dt); // line 191

  CompoundTag save(); // line 203

  void load(CompoundTag t); // line 222

```


#### BioshipRespiration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRespiration.java` (166 lines).

```java
final class BioshipRespiration

  static final MaterialCost LUNG_COST, GILL_COST; // line 11

  static int bodySize(LivingCoreBlockEntity core); // line 13

  static int bodySize(Map<BlockPos, BlockState> cells); // line 17

  static boolean has(LivingCoreBlockEntity core, boolean gills); // line 29

  static boolean airAccess(LivingCoreBlockEntity core); // line 34

  static boolean exposedGill(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 45

  static boolean activeGills(LivingCoreBlockEntity core); // line 58

  static boolean canBreathe(LivingCoreBlockEntity core); // line 66

  static boolean wantsGills(LivingCoreBlockEntity core); // line 70

  static boolean prefersGills(LivingCoreBlockEntity core); // line 75

  static boolean missingLungs(LivingCoreBlockEntity core); // line 79

  static boolean needsLungs(LivingCoreBlockEntity core); // line 83

  static boolean blocksOpening(LivingCoreBlockEntity core, BlockPos target); // line 87

  record Growth(BlockPos pos, BlockState state, MaterialCost cost, boolean gills); // line 97

  static Growth plan(LivingCoreBlockEntity core); // line 99

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 131

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 150

```


#### RespiratoryOrganBlock

Source: `src/main/java/org/Azkerash/create_bioships/RespiratoryOrganBlock.java` (33 lines).

```java
public final class RespiratoryOrganBlock extends DirectionalBlock

  public static final MapCodec<RespiratoryOrganBlock> CODEC; // line 10

  public RespiratoryOrganBlock(Properties properties); // line 12

  protected MapCodec<? extends DirectionalBlock> codec(); // line 17

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 22

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 27

```


#### ExposureHistory

Source: `src/main/java/org/Azkerash/create_bioships/ExposureHistory.java` (36 lines).

```java
final class ExposureHistory

  private int samples; // line 7

  private double score; // line 8

  void observe(boolean exposed); // line 9

  int samples(); // line 14

  int score(); // line 18

  double preciseScore(); // line 22

  void restore(int samples, int score); // line 26

  void restore(int samples, double score); // line 30

```


### Move the existing core [active]

Reposition through connected anatomy for sight or defense; preserve state and inventory.

```text
[scout]
  CoreMotion
```


#### CoreMotion

Source: `src/main/java/org/Azkerash/create_bioships/CoreMotion.java` (742 lines).

```java
public final class CoreMotion

  private static final int FULL_COVERAGE, EVADE_TICKS; // line 14

  private BlockPos home, target; // line 15

  private long started, nextScan; // line 16

  private boolean evasive, sheltered, reclaimEntry; // line 17

  private boolean layoutDirty; // line 18

  void layoutChanged(LivingCoreBlockEntity core); // line 19

  static boolean protectedInside(LivingCoreBlockEntity core, BlockPos pos); // line 25

  private static boolean protectedInside( LivingCoreBlockEntity core, BlockPos pos, Set<BlockPos> cells); // line 29

  private BlockPos shelteredStep( LivingCoreBlockEntity core, net.minecraft.world.entity.LivingEntity threat); // line 48

  private double shelterScore( LivingCoreBlockEntity core, BlockPos pos, net.minecraft.world.entity.LivingEntity threat); // line 86

  private BlockPos entry(LivingCoreBlockEntity core); // line 94

  public int duration(); // line 111

  public BlockPos home(LivingCoreBlockEntity core); // line 115

  public BlockPos target(); // line 119

  public long started(); // line 123

  public boolean active(); // line 127

  static boolean tissue(BlockState state); // line 131

  private boolean clearWorld(LivingCoreBlockEntity core, BlockPos pos); // line 137

  private boolean clearWorld(LivingCoreBlockEntity core, Vec3 local); // line 141

  int coverage(LivingCoreBlockEntity core, BlockPos vantage); // line 164

  private Vec3 blockedSupply(LivingCoreBlockEntity core); // line 177

  private int score(LivingCoreBlockEntity core, BlockPos vantage); // line 183

  boolean canAvoidBlast( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 196

  private record BlastStep(BlockPos pos, BlockPos first, int ticks); // line 201

  private BlockPos blastExit( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 203

  private BlockPos choose(LivingCoreBlockEntity core, boolean returning); // line 243

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos from, BlockPos to); // line 285

  private boolean clearPerchTravel(LivingCoreBlockEntity core, BlockPos pos); // line 303

  private boolean supportedPerch(LivingCoreBlockEntity core, BlockPos pos); // line 312

  private boolean stableSupport(LivingCoreBlockEntity core, BlockPos pos); // line 330

  private BlockPos perch(LivingCoreBlockEntity core, boolean returning); // line 339

  private BlockPos reconnect(LivingCoreBlockEntity core); // line 361

  private boolean preparePerch(LivingCoreBlockEntity core); // line 396

  boolean tick(LivingCoreBlockEntity core); // line 428

  private boolean begin(LivingCoreBlockEntity core, BlockPos next, boolean emergency); // line 535

  boolean evade(LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 554

  static double blastSafety( LivingCoreBlockEntity core, BlockPos point, net.minecraft.world.entity.monster.Creeper creeper); // line 570

  private BlockPos safestStep( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 582

  String scoutingHint(LivingCoreBlockEntity core); // line 620

  void scoutNow(LivingCoreBlockEntity core); // line 651

  private boolean feeding; // line 656

  boolean feeding(); // line 658

  void cancel(LivingCoreBlockEntity core); // line 662

  private void commit(LivingCoreBlockEntity core); // line 673

  CompoundTag save(); // line 715

  void load(CompoundTag tag); // line 729

```


### Batch assimilation [active]

Mouth-bound collection slots, persistent swallowed stock, batch settlement and committed wreck ownership.

```text
[collect]
  BioshipAssimilation
  BioshipFoodCollection
```


#### BioshipAssimilation

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAssimilation.java` (460 lines).

```java
public final class BioshipAssimilation

  private final List<ComponentTransport.Move> arms; // line 14

  private final Map<UUID, String> grasperBindings; // line 15

  Collection<String> busyGraspers(); // line 16

  public String grasper(UUID cargo); // line 20

  private final List<CompoundTag> stock; // line 24

  private UUID wreck; // line 26

  private boolean settling, deploymentBlocked, resumeWreck, feedingJob; // line 27

  boolean feeding(); // line 28

  private long retry; // line 32

  public List<ComponentTransport.Move> arms(); // line 34

  boolean active(); // line 38

  boolean committed(); // line 42

  boolean pending(); // line 46

  boolean finishing(); // line 50

  int stored(); // line 54

  int health(LivingCoreBlockEntity core); // line 58

  double load(LivingCoreBlockEntity core); // line 69

  private static BlockState state(LivingCoreBlockEntity core, CompoundTag tag); // line 78

  boolean acceptItem(LivingCoreBlockEntity core, net.minecraft.world.item.ItemStack stack); // line 86

  void receive(LivingCoreBlockEntity core, ServerSubLevel cargo, BlockPos pos); // line 104

  List<BioshipScavenging.Source> remaining(LivingCoreBlockEntity core); // line 137

  private List<Vec3> cluster(LivingCoreBlockEntity core, Vec3 primary); // line 160

  Vec3 gatheringPoint(LivingCoreBlockEntity core, Vec3 primary); // line 172

  boolean clusterReachable(LivingCoreBlockEntity core, Vec3 candidate); // line 179

  boolean start(LivingCoreBlockEntity core, BioshipBrain.Decision chosen); // line 188

  void tick(LivingCoreBlockEntity core); // line 252

  private void settle(LivingCoreBlockEntity core); // line 283

  private void take(LivingCoreBlockEntity core, CompoundTag tag); // line 380

  void abandon(LivingCoreBlockEntity core); // line 387

  CompoundTag save(); // line 417

  void load(CompoundTag tag); // line 435

```


#### BioshipFoodCollection

Source: `src/main/java/org/Azkerash/create_bioships/BioshipFoodCollection.java` (54 lines).

```java
final class BioshipFoodCollection

  static Vec3 collect(LivingCoreBlockEntity core, Vec3 point, ItemEntity item); // line 8

```


### Conserved component transport [active]

Move one actual component/Sable cargo with its complete state; route tube costs before transfer.

```text
[transport]
  amoeba.ComponentTransport
  AmoebaCoreAdapter
  amoeba.AmoebaComponentHost
  amoeba.AmoebaComponentProvider
  amoeba.ComponentAttachment
  BioshipContainerState
  amoeba.ComponentMerging
```


#### amoeba.ComponentTransport

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/ComponentTransport.java` (348 lines).

```java
public final class ComponentTransport

  public record Move( UUID cargo, BlockPos cargoBlock, BlockPos source, BlockPos target, Vec3 start, long started, boolean external, boolean restoreFlesh, boolean mouth, boolean stomach, boolean manual, boolean ownerForced, List<BlockPos> tissuePath); // line 21

  public static boolean busy(AmoebaComponentHost core); // line 85

  public static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external); // line 89

  public static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual); // line 94

  public static boolean reabsorb(AmoebaComponentHost core, BlockPos source, BlockPos target); // line 103

  public static boolean reclaimToCore(AmoebaComponentHost core, BlockPos source); // line 107

  public static boolean storeComponent(AmoebaComponentHost core, BlockPos source); // line 111

  public static boolean swallow(AmoebaComponentHost core, BlockPos source); // line 115

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming); // line 119

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash); // line 129

  public static boolean merge( AmoebaComponentHost core, BlockPos source, BlockPos target, String recipe); // line 140

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash, String mergeRecipe); // line 145

  public static List<BlockPos> findTissuePath( AmoebaComponentHost core, BlockPos source, BlockPos target); // line 198

  private static Vec3 alongTissue(List<BlockPos> path, double progress); // line 232

  public static void tick(AmoebaComponentHost core); // line 239

  static void finishCargo(AmoebaComponentHost core, ServerSubLevel cargo); // line 314

  static void release(AmoebaComponentHost core, ServerSubLevel cargo); // line 326

  public static void abandon(AmoebaComponentHost core); // line 339

```


#### AmoebaCoreAdapter

Source: `src/main/java/org/Azkerash/create_bioships/AmoebaCoreAdapter.java` (263 lines).

```java
final class AmoebaCoreAdapter implements AmoebaComponentHost

  private final LivingCoreBlockEntity core; // line 17

  AmoebaCoreAdapter(LivingCoreBlockEntity core); // line 18

  public Level level(); // line 22

  public ServerSubLevel body(); // line 26

  public BlockPos controllerPosition(); // line 30

  public ResourceAccount resources(); // line 34

  public ComponentTransport.Move movement(); // line 38

  public void movement(ComponentTransport.Move move); // line 42

  public boolean alive(); // line 46

  public List<ComponentTransport.Move> absorptions(); // line 50

  public BlockState hostTissue(BlockEntity entity); // line 54

  public boolean absorptionActive(); // line 58

  public Prepared prepare(Request request); // line 62

  public void departed(Request request, Prepared prepared); // line 156

  public void forgetSource(BlockPos source); // line 165

  public boolean owns(BlockPos pos); // line 169

  public boolean travelTissue(BlockState state); // line 173

  public boolean leg(BlockState state); // line 180

  public Direction legFacing(BlockPos target); // line 184

  public Direction mouthFacing(BlockPos target); // line 188

  public boolean validAttachment(BlockState state, BlockPos target); // line 192

  public BlockEntity component(ServerSubLevel owner, BlockPos pos); // line 199

  public List<BlockEntity> components(); // line 203

  public CompoundTag capture(BlockEntity entity); // line 207

  public void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule); // line 211

  public void rememberHost(BlockEntity entity, BlockState host); // line 215

  public void changed(); // line 219

  public void invalidateVision(); // line 223

  public void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim); // line 227

  public void placed( ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh); // line 232

  public void merged(); // line 240

  public boolean needsStomach(); // line 245

  public boolean digestionReady(); // line 249

  public List<FeedingMouthBlockEntity> mouths(); // line 253

  public void stomachFormed(); // line 257

```


#### amoeba.AmoebaComponentHost

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/AmoebaComponentHost.java` (99 lines).

```java
public interface AmoebaComponentHost extends CreatureBodyAccess

  record Request( BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash, String mergeRecipe); // line 15

  record Prepared( ServerSubLevel sourceBody, Vec3 sourceWorld, BlockState sourceHost, CompoundTag capsule, boolean mouth, boolean stomach, List<BlockPos> tissuePath, CompoundTag cargoTags); // line 23

  ResourceAccount resources(); // line 37

  ComponentTransport.Move movement(); // line 39

  void movement(ComponentTransport.Move move); // line 41

  boolean absorptionActive(); // line 43

  boolean alive(); // line 45

  List<ComponentTransport.Move> absorptions(); // line 47

  Prepared prepare(Request request); // line 49

  void departed(Request request, Prepared prepared); // line 52

  void forgetSource(BlockPos source); // line 54

  boolean owns(BlockPos pos); // line 56

  boolean travelTissue(BlockState state); // line 58

  boolean validAttachment(BlockState state, BlockPos target); // line 60

  boolean leg(BlockState state); // line 62

  Direction legFacing(BlockPos target); // line 64

  Direction mouthFacing(BlockPos target); // line 66

  BlockEntity component(ServerSubLevel owner, BlockPos pos); // line 68

  List<BlockEntity> components(); // line 70

  CompoundTag capture(BlockEntity entity); // line 72

  void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule); // line 74

  void rememberHost(BlockEntity entity, BlockState host); // line 76

  BlockState hostTissue(BlockEntity entity); // line 78

  void changed(); // line 80

  void invalidateVision(); // line 82

  void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim); // line 84

  void placed(ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh); // line 86

  void merged(); // line 88

  boolean needsStomach(); // line 90

  boolean digestionReady(); // line 92

  List<FeedingMouthBlockEntity> mouths(); // line 94

  void stomachFormed(); // line 96

```


#### amoeba.AmoebaComponentProvider

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/AmoebaComponentProvider.java` (6 lines).

```java
public interface AmoebaComponentProvider

  AmoebaComponentHost amoebaComponents(); // line 4

```


#### amoeba.ComponentAttachment

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/ComponentAttachment.java` (56 lines).

```java
final class ComponentAttachment

  private ComponentAttachment(); // line 9

  static void restore( AmoebaComponentHost core, ComponentTransport.Move move, BlockState host, CompoundTag capsule); // line 10

```


#### BioshipContainerState

Source: `src/main/java/org/Azkerash/create_bioships/BioshipContainerState.java` (119 lines).

```java
final class BioshipContainerState

  static CompoundTag capture(Level level, BlockEntity entity); // line 13

  static BlockEntity entity( Level level, dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos pos); // line 25

  static void restore( Level level, dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos destination, CompoundTag capsule); // line 33

  static List<ItemStack> packDrops(List<ItemStack> drops, LootParams.Builder params); // line 65

  static void creativeBreak( Level level, BlockPos pos, net.minecraft.world.entity.player.Player player); // line 85

  private BioshipContainerState(); // line 116

```


#### amoeba.ComponentMerging

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/ComponentMerging.java` (182 lines).

```java
public final class ComponentMerging

  private ComponentMerging(); // line 14

  public static final int DURATION_TICKS; // line 15

  public static final class Animation; // line 17

  public interface Commit; // line 30

  public record Recipe( String id, Class<? extends BlockEntity> source, Class<? extends BlockEntity> destination, Supplier<BlockState> result, Predicate<AmoebaComponentHost> needed, Commit commit); // line 35

  private static final class Registry; // line 47

  public static void register(Recipe recipe); // line 64

  public static boolean matches(String id, BlockEntity source, BlockEntity destination); // line 69

  public static BlockState result(String id); // line 74

  private static boolean commit( String id, AmoebaComponentHost core, BlockEntity source, BlockEntity destination); // line 78

  public static void tryAvailable(AmoebaComponentHost core); // line 86

  public static boolean absorbLarynx(AmoebaComponentHost core, CompoundTag capsule); // line 102

  public static void attachLarynx( AmoebaComponentHost core, FeedingMouthBlockEntity mouth, CompoundTag capsule); // line 110

  private static final String RECIPE_TAG; // line 116

  public static void tag(CompoundTag data, String recipe); // line 118

  static boolean finish( AmoebaComponentHost core, dev.ryanhcode.sable.sublevel.ServerSubLevel cargo, ComponentTransport.Move move); // line 122

  private static void finishStomach( AmoebaComponentHost core, com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity decomposer, com.github.elenterius.biomancy.block.digester.DigesterBlockEntity digester); // line 146

  private static void copyItems( net.neoforged.neoforge.items.IItemHandler inventory, List<net.minecraft.world.item.ItemStack> destination); // line 174

```


### Scavenging and recycling [active]

Find eligible wreck sources, keep needed organs, conserve inventories and apply fractional lossy yields.

```text
[salvage]
  BioshipScavenging
```


#### BioshipScavenging

Source: `src/main/java/org/Azkerash/create_bioships/BioshipScavenging.java` (386 lines).

```java
public final class BioshipScavenging

  private int fleshRemainder, boneRemainder, fiberRemainder, pendingFlesh, pendingBone, pendingFiber, pendingFreeTubes; // line 18

  static boolean frameMaterial(BlockState state); // line 25

  static boolean fiberMaterial(BlockState state); // line 34

  static boolean keepComponent(LivingCoreBlockEntity core, BlockState state, BlockPos exclude); // line 46

  static boolean component(BlockState state); // line 76

  static boolean emptyComponent(LivingCoreBlockEntity core, BlockPos pos); // line 80

  static boolean needed(LivingCoreBlockEntity core, BlockState state); // line 94

  record Source(BlockPos pos, Vec3 point, BlockState state); // line 122

  static java.util.List<Source> visibleSources(LivingCoreBlockEntity core); // line 124

  static boolean collect(LivingCoreBlockEntity core, ServerSubLevel own); // line 149

  static boolean physicallyReachable( LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 point); // line 156

  private static boolean visible( LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 eye, Vec3 point); // line 173

  void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost); // line 205

  void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost, boolean forced); // line 209

  static boolean droppedBlock(ItemStack stack); // line 274

  boolean consumeDroppedBlock(LivingCoreBlockEntity core, ItemStack stack); // line 281

  void recycleToCore(LivingCoreBlockEntity core, BlockState state); // line 297

  void recycle(BlockState state); // line 309

  void deliver(LivingCoreBlockEntity core); // line 329

  CompoundTag save(); // line 363

  void load(CompoundTag t); // line 375

```


### Mouths and physical gut graph [active]

Assign distinct stomachs, validate tube paths, buffer mouth input and connect food sacs.

```text
[gut]
  DigestiveSystem
  BioshipDigestiveRepair
  FeedingMouthBlockEntity
  FeedingMouthBlock
  IntestineBlock
```


#### DigestiveSystem

Source: `src/main/java/org/Azkerash/create_bioships/DigestiveSystem.java` (713 lines).

```java
public final class DigestiveSystem

  public static StomachBlockEntity stomach(LivingCoreBlockEntity core); // line 15

  static List<StomachBlockEntity> stomachs(LivingCoreBlockEntity core); // line 26

  static List<BlockPos> mouths(LivingCoreBlockEntity core); // line 36

  static int requiredPairs(LivingCoreBlockEntity core); // line 46

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth); // line 50

  private static boolean linked(LivingCoreBlockEntity core, BlockPos a, BlockPos b); // line 57

  private static boolean pathIntact( LivingCoreBlockEntity core, BlockPos start, BlockPos end, List<BlockPos> path); // line 72

  public static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 84

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end); // line 95

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end, BlockPos blocked, BlockPos freed); // line 100

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end, BlockPos blocked, BlockPos freed, boolean spatial); // line 111

  static boolean permitsPlacement(LivingCoreBlockEntity core, BlockPos pos); // line 183

  static List<BlockPos> repairPath( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos freed); // line 192

  static int repairPurchases( LivingCoreBlockEntity core, StomachBlockEntity stomach, List<BlockPos> route); // line 198

  private record Plan(Set<BlockPos> wanted, List<BlockPos> retired, int purchases); // line 210

  private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> tubes( LivingCoreBlockEntity core); // line 212

  private static boolean removable(BlockPos point, Set<BlockPos> cells); // line 222

  private static boolean coreLink( LivingCoreBlockEntity core, BlockPos removed, Set<BlockPos> cells, Set<BlockPos> wanted); // line 241

  static boolean reserveOrganRoute(LivingCoreBlockEntity core, BlockPos source, BlockPos target); // line 262

  static boolean reserveOrganRoute( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean reserve); // line 266

  static int storageCost(LivingCoreBlockEntity core, BlockPos target); // line 320

  static BlockPos storagePort(LivingCoreBlockEntity core, BlockPos source); // line 337

  static void arrangeStorage(LivingCoreBlockEntity core); // line 383

  private static Plan plan( LivingCoreBlockEntity core, StomachBlockEntity stomach, Set<BlockPos> wanted, Set<BlockPos> starter, Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing); // line 401

  private static boolean affordable(StomachBlockEntity stomach, Plan plan); // line 438

  static boolean reserveCoreRoute(LivingCoreBlockEntity core, BlockPos target, boolean reserve); // line 442

  private static boolean reserveCorePairRoute( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos target, boolean reserve); // line 451

  public static void connect(LivingCoreBlockEntity core, ServerSubLevel ship); // line 487

  private static void connectPair( LivingCoreBlockEntity core, ServerSubLevel ship, StomachBlockEntity stomach, BlockPos mouth); // line 514

  private static boolean connectedStore( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos pos, List<BlockPos> path); // line 607

  static int connectedFoodCount(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 616

  static int foodCount(LivingCoreBlockEntity core); // line 630

  static boolean storeConnected( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos pos); // line 637

  static boolean functional(LivingCoreBlockEntity core); // line 643

  public static boolean offerFood(LivingCoreBlockEntity core, ItemStack stack, boolean inherited); // line 653

  static boolean offerFood( LivingCoreBlockEntity core, BlockPos mouth, ItemStack stack, boolean inherited); // line 658

  private static boolean offerFood( LivingCoreBlockEntity core, StomachBlockEntity stomach, ItemStack stack, boolean inherited); // line 663

  public static void pullStoredFood(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 685

```


#### BioshipDigestiveRepair

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDigestiveRepair.java` (111 lines).

```java
final class BioshipDigestiveRepair

  record Move(BlockPos source, BlockPos target, int tubes); // line 11

  private record Audit(long at, int purchases, Move move); // line 12

  private static final Map<LivingCoreBlockEntity, Audit> CACHE; // line 14

  static void invalidate(LivingCoreBlockEntity core); // line 17

  static int purchases(LivingCoreBlockEntity core); // line 21

  private static Audit audit(LivingCoreBlockEntity core); // line 25

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 42

  static boolean execute(LivingCoreBlockEntity core, BlockPos source); // line 58

  private static Move move(LivingCoreBlockEntity core); // line 65

```


#### FeedingMouthBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/FeedingMouthBlockEntity.java` (260 lines).

```java
public class FeedingMouthBlockEntity extends BaseContainerBlockEntity

  private NonNullList<ItemStack> items; // line 15

  private int inheritedBiomass; // line 16

  private CompoundTag larynx; // line 17

  private int swimmingGraspers; // line 18

  public boolean swimmingGrasper(int ordinal); // line 19

  void swimmingGrasper(int ordinal, boolean swimming); // line 23

  private long motionStart; // line 32

  private int motionTicks; // line 34

  private boolean vocalMotion; // line 35

  static void bite(LivingCoreBlockEntity core, Vec3 point); // line 36

  public boolean voiced(); // line 43

  public void absorbLarynx(CompoundTag capsule); // line 47

  CompoundTag larynxCapsule(); // line 54

  public static final double REST_JAW_SHIFT; // line 58

  public double jawShift(float partial); // line 60

  void animate(int ticks, boolean vocal); // line 67

  public void vocalize(boolean hurt); // line 76

  public void vocalize(char letter); // line 82

  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(); // line 87

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 92

  private boolean inheritedSlot, embeddedInHull; // line 97

  public FeedingMouthBlockEntity(BlockPos pos, BlockState state); // line 99

  public int getContainerSize(); // line 103

  protected NonNullList<ItemStack> getItems(); // line 108

  protected void setItems(NonNullList<ItemStack> items); // line 113

  protected Component getDefaultName(); // line 118

  protected AbstractContainerMenu createMenu(int id, Inventory player); // line 124

  public void setItem(int slot, ItemStack stack); // line 129

  public boolean stillValid(Player player); // line 135

  public void inheritBioForge(ItemStack stack, int biomass); // line 144

  public boolean embeddedInHull(); // line 151

  public void embeddedInHull(boolean value); // line 155

  public static void tick( Level level, BlockPos pos, BlockState state, FeedingMouthBlockEntity mouth); // line 160

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 219

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 233

```


#### FeedingMouthBlock

Source: `src/main/java/org/Azkerash/create_bioships/FeedingMouthBlock.java` (110 lines).

```java
public class FeedingMouthBlock extends BaseEntityBlock

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 14

  public static final MapCodec<FeedingMouthBlock> CODEC; // line 16

  public FeedingMouthBlock(Properties properties); // line 17

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder); // line 23

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 29

  protected RenderShape getRenderShape(BlockState state); // line 34

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 39

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 44

  static Direction outward(LivingCoreBlockEntity core, BlockPos pos); // line 53

  static boolean openFace(LivingCoreBlockEntity core, BlockPos pos, Direction direction); // line 75

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 88

  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 97

  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player); // line 103

```


#### IntestineBlock

Source: `src/main/java/org/Azkerash/create_bioships/IntestineBlock.java` (141 lines).

```java
public class IntestineBlock extends Block

  public static final BooleanProperty MANUAL; // line 13

  public static final BooleanProperty PAID; // line 14

  public static final BooleanProperty ENCASED; // line 15

  public static final Map<Direction, BooleanProperty> CONNECTIONS; // line 16

  public IntestineBlock(Properties properties); // line 30

  public static boolean compatible(BlockState s); // line 42

  public BlockState connections(BlockState s, BlockGetter level, BlockPos pos); // line 50

  public BlockState getStateForPlacement(BlockPlaceContext c); // line 56

  protected BlockState updateShape( BlockState s, Direction d, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos); // line 64

  protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving); // line 75

  protected VoxelShape getShape( BlockState s, BlockGetter level, BlockPos pos, CollisionContext context); // line 84

  protected net.minecraft.world.ItemInteractionResult useItemOn( net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit); // line 106

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b); // line 134

```


### Recipe digestion [active]

Native Biomancy recipes and output rolls, acid, input queues, local nutrient paste and tube stock.

```text
[stomach]
  StomachBlockEntity
  StomachBlock
```


#### StomachBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/StomachBlockEntity.java` (467 lines).

```java
public class StomachBlockEntity extends BlockEntity implements MenuProvider, ResourceAccount

  public static final int MAX_ACID; // line 22

  private final NonNullList<ItemStack> input; // line 23

  private final boolean[] inherited; // line 24

  private final Map<ResourceLocation, Integer> reserves; // line 25

  final List<BlockPos> gutPath; // line 26

  final Map<BlockPos, List<BlockPos>> sacPaths; // line 27

  BlockPos connectedMouth; // line 28

  boolean tubeBootstrapped; // line 29

  int freeTubeStock, paidTubeStock; // line 30

  private int acid, activeSlot; // line 31

  private String activeRecipe; // line 32

  public StomachBlockEntity(BlockPos pos, BlockState state); // line 33

  private int nutrientReserve; // line 37

  public int nutrients(); // line 39

  public void addNutrients(int amount); // line 43

  int burnPaste(); // line 48

  int burnNutrients(); // line 54

  public int burnReserve(); // line 68

  static int burnStored(CompoundTag stores); // line 86

  public int material(String id); // line 108

  public void retain(ItemStack stack); // line 113

  public void credit(String id, int count); // line 122

  void creditLocal(String id, int count); // line 129

  public void retainFlesh(int count); // line 135

  int localMaterial(String id); // line 139

  int localMaterialTotal(); // line 143

  java.util.List<String> localMaterialIds(); // line 147

  boolean spendLocal(String item, int count); // line 151

  public boolean spend(String item, int count); // line 160

  public int acid(); // line 165

  public int meals(); // line 169

  public void continueGrowth(LivingCoreBlockEntity core); // line 173

  public void inheritInput(ItemStack stack); // line 185

  void transferTo(StomachBlockEntity target); // line 197

  int queuedNutrition(); // line 218

  public int queuedFood(); // line 226

  public boolean canEnqueue(ItemStack stack, boolean oldStock); // line 232

  public boolean enqueue(ItemStack stack, boolean oldStock); // line 249

  public boolean connected(LivingCoreBlockEntity core); // line 270

  public static void tick(Level level, BlockPos pos, BlockState state, StomachBlockEntity stomach); // line 274

  public Component getDisplayName(); // line 349

  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player); // line 354

  public ContainerData menuData(); // line 359

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 384

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 423

```


#### StomachBlock

Source: `src/main/java/org/Azkerash/create_bioships/StomachBlock.java` (73 lines).

```java
public class StomachBlock extends BaseEntityBlock

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 13

  public static final MapCodec<StomachBlock> CODEC; // line 15

  public StomachBlock(Properties properties); // line 16

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder); // line 22

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 28

  protected RenderShape getRenderShape(BlockState state); // line 33

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 38

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 43

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 51

  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 60

  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player); // line 66

```


### Shared usable storage [active]

Core-first deposits, connected native/private sac stocks, external-first withdrawal and reserve refill.

```text
[storage]
  BioshipStorage
  BioshipStores
```


#### BioshipStorage

Source: `src/main/java/org/Azkerash/create_bioships/BioshipStorage.java` (200 lines).

```java
final class BioshipStorage

  static final int CORE_CAPACITY; // line 10

  static boolean fuel(String id); // line 11

  static List<BlockEntity> sacks(LivingCoreBlockEntity core); // line 15

  static int deposit(LivingCoreBlockEntity core, String id, int count); // line 22

  static void flush(LivingCoreBlockEntity core); // line 51

  static Map<String, Integer> snapshot(LivingCoreBlockEntity core); // line 62

  static Map<String, Integer> metabolicStock(LivingCoreBlockEntity core); // line 88

  static int amount(LivingCoreBlockEntity core, String id); // line 103

  static boolean spend(LivingCoreBlockEntity core, String id, int count); // line 107

  private static int withdrawExternal(LivingCoreBlockEntity core, String id, int count); // line 120

  private static void refill(LivingCoreBlockEntity core); // line 147

  static ResourceAccount account(LivingCoreBlockEntity core); // line 165

  static int capacity(LivingCoreBlockEntity core); // line 186

  static int products(LivingCoreBlockEntity core); // line 192

```


#### BioshipStores

Source: `src/main/java/org/Azkerash/create_bioships/BioshipStores.java` (67 lines).

```java
final class BioshipStores

  static final String MATERIALS, AUTOMATIC; // line 7

  static final int STOMACH_CAPACITY, SAC_CAPACITY; // line 8

  static LivingCoreBlockEntity core(StomachBlockEntity stomach); // line 9

  static List<BlockEntity> connected(StomachBlockEntity stomach); // line 18

  static int stored(BlockEntity sac, String id); // line 31

  static int total(BlockEntity sac); // line 35

  static void change(BlockEntity sac, String id, int delta); // line 42

  static int total(StomachBlockEntity stomach); // line 51

  static int capacity(StomachBlockEntity stomach); // line 56

  static void balance(StomachBlockEntity stomach); // line 61

```


### Costs, ledgers and reserve policy [active]

Affordability, payment rollback and refunds; core emergency target 32 flesh / 16 fiber / 8 bone.

```text
[money]
  MaterialCost
  creature.ResourceAccount
  creature.CreatureBodyAccess
  MaterialLedger
  EmergencyReserve
```


#### MaterialCost

Source: `src/main/java/org/Azkerash/create_bioships/MaterialCost.java` (55 lines).

```java
record MaterialCost(int flesh, int fiber, int bone)

  static final String FLESH, FIBER, BONE; // line 7

  MaterialCost; // line 10

  private Map<String, Integer> entries(); // line 15

  boolean funded(ResourceAccount account); // line 19

  boolean pay(ResourceAccount account); // line 24

  void refund(ResourceAccount account); // line 38

  static boolean transfer(ResourceAccount from, ResourceAccount to, String id, int count); // line 46

```


#### creature.ResourceAccount

Source: `src/main/java/org/Azkerash/create_bioships/creature/ResourceAccount.java` (10 lines).

```java
public interface ResourceAccount

  int material(String id); // line 4

  boolean spend(String id, int count); // line 5

  void credit(String id, int count); // line 7

```


#### creature.CreatureBodyAccess

Source: `src/main/java/org/Azkerash/create_bioships/creature/CreatureBodyAccess.java` (14 lines).

```java
public interface CreatureBodyAccess

  Level level(); // line 8

  ServerSubLevel body(); // line 9

  BlockPos controllerPosition(); // line 11

```


#### MaterialLedger

Source: `src/main/java/org/Azkerash/create_bioships/MaterialLedger.java` (38 lines).

```java
final class MaterialLedger implements ResourceAccount

  private final Map<String, Integer> amounts; // line 7

  public int material(String id); // line 8

  public boolean spend(String id, int count); // line 12

  public void credit(String id, int count); // line 20

  Map<String, Integer> snapshot(); // line 25

  void restore(Map<String, Integer> saved); // line 29

```


#### EmergencyReserve

Source: `src/main/java/org/Azkerash/create_bioships/EmergencyReserve.java` (24 lines).

```java
final class EmergencyReserve

  static final MaterialCost TARGET; // line 8

  static Map<String, Integer> targets(); // line 9

  static boolean missing(LivingCoreBlockEntity core); // line 13

  static boolean after(LivingCoreBlockEntity core, MaterialCost price); // line 18

```


### Read-only anatomical proposals [active]

Propose funded organ recovery, digestion, storage, reinforcement or safe reclamation; no block edits.

```text
[plans]
  BioshipAdaptationPlanner
  BioshipDigestiveGrowth
  BioshipRecovery
```


#### BioshipAdaptationPlanner

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAdaptationPlanner.java` (309 lines).

```java
final class BioshipAdaptationPlanner

  record State(Map<String, Integer> reserve, Set<BlockPos> armor, long nextWork); // line 12

  private static boolean clear(LivingCoreBlockEntity core, BlockPos pos); // line 18

  private static BlockPos organPort(LivingCoreBlockEntity core, boolean mouth); // line 32

  static List<Proposal> plan(LivingCoreBlockEntity core, State memory); // line 44

  private static List<Proposal> offers( LivingCoreBlockEntity core, State memory, BioshipMountGrowth.Plan mounting); // line 63

  private static boolean funded(StomachBlockEntity stomach, int flesh, int fiber, int bone); // line 248

  private static BlockPos shedCandidate( LivingCoreBlockEntity core, StomachBlockEntity stomach, boolean sacsOnly, Set<BlockPos> armor); // line 252

  private static boolean connectedWithout(Set<BlockPos> body, BlockPos removed, BlockPos root); // line 291

```


#### BioshipDigestiveGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDigestiveGrowth.java` (161 lines).

```java
final class BioshipDigestiveGrowth

  record Plan( BlockPos mouth, BlockPos stomach, BlockPos tube, Direction facing, boolean addMouth, boolean addStomach, MaterialCost cost, int tubeCredits, int legUpgrades); // line 8

  static int target(LivingCoreBlockEntity core); // line 18

  static Plan plan(LivingCoreBlockEntity core); // line 24

  private static List<Direction> mouthDirections(LivingCoreBlockEntity core); // line 79

  private static Plan check( LivingCoreBlockEntity core, BlockPos mouth, BlockPos stomach, Direction face, boolean addMouth, boolean addStomach); // line 97

  private static boolean replaceable(LivingCoreBlockEntity core, BlockPos pos); // line 150

```


#### BioshipRecovery

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRecovery.java` (123 lines).

```java
final class BioshipRecovery

  record Options(boolean mouth, boolean stomach); // line 8

  static Options options(LivingCoreBlockEntity core); // line 9

  static Options available(LivingCoreBlockEntity core); // line 14

  static BlockPos sacrifice( LivingCoreBlockEntity core, Map<String, Integer> reserve, Options options); // line 27

  private static boolean eligible(LivingCoreBlockEntity core, BlockPos pos); // line 82

  private static boolean connectedWithout(Set<BlockPos> cells, BlockPos removed, BlockPos root); // line 107

```


### Execute anatomical adaptation [active]

Rank body work independently of actions, share layout transaction locks, and execute paid adaptation or native configuration.

```text
[adapt]
  BioshipAdaptation
  BioshipBodyBehavior
  BioshipConfiguration
```


#### BioshipAdaptation

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAdaptation.java` (252 lines).

```java
final class BioshipAdaptation

  static final String FLESH, FIBER, BONE; // line 12

  record Proposal( BioshipBrain.Action action, BlockPos pos, BlockState state, int flesh, int fiber, int bone, int urgency, String reason); // line 15

  private final BioshipBodyBehavior behavior; // line 25

  BioshipBodyBehavior behavior(); // line 27

  private final MaterialLedger reserve; // line 31

  private final Set<BlockPos> armor; // line 33

  private long nextWork; // line 34

  private boolean starterLegSalvage; // line 35

  private int starterLegBudget; // line 36

  int starterLegBudget(); // line 37

  void usedStarterLeg(); // line 41

  void salvageStarterLegs(); // line 45

  MaterialLedger ledger(); // line 52

  int material(String id); // line 56

  void creditRecovery(String id, int count); // line 60

  private boolean pay(LivingCoreBlockEntity core, int flesh, int fiber, int bone); // line 64

  void maintain(LivingCoreBlockEntity core); // line 68

  List<Proposal> proposals(LivingCoreBlockEntity core); // line 77

  void tick(LivingCoreBlockEntity core); // line 82

  int burnEmergency(LivingCoreBlockEntity core); // line 214

  CompoundTag save(); // line 227

  void load(CompoundTag t); // line 239

```


#### BioshipBodyBehavior

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBodyBehavior.java` (77 lines).

```java
final class BioshipBodyBehavior

  private final GoalQueue<BioshipAdaptation.Proposal> goals; // line 8

  private String previous, waiting; // line 9

  private BioshipAdaptation.Proposal selected; // line 10

  static String blocker(LivingCoreBlockEntity core); // line 11

  private static String key(BioshipAdaptation.Proposal p); // line 22

  private static BehaviorPriority.Offer priority(BioshipAdaptation.Proposal p); // line 26

  BioshipAdaptation.Proposal choose(LivingCoreBlockEntity core); // line 35

  boolean allowsConfiguration(LivingCoreBlockEntity core); // line 50

  CompoundTag debug(); // line 55

```


#### BioshipConfiguration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipConfiguration.java` (76 lines).

```java
final class BioshipConfiguration

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 7

  static void execute(LivingCoreBlockEntity core); // line 61

  private BioshipConfiguration(); // line 73

```


### Metabolism and emergency fuel [active]

One metabolic step at the core cadence; nutrient paste, emergency resources, then starvation damage.

```text
[metabolism]
  BioshipMetabolism
```


#### BioshipMetabolism

Source: `src/main/java/org/Azkerash/create_bioships/BioshipMetabolism.java` (36 lines).

```java
final class BioshipMetabolism

  static void step(LivingCoreBlockEntity core, BioshipNeeds needs); // line 4

  private static int emergencyFuel(LivingCoreBlockEntity core); // line 22

```


### Health and intended body [active]

Health, capacity, intended-cell memory, injury, paid repair and permanent death.

```text
[health]
  BlockHealthTable
  BioshipHealthPool
  BioshipHealthChanges
  mixin.ComponentHealthChangeMixin
  BioshipBody
  BioshipDeathCause
```


#### BlockHealthTable

Source: `src/main/java/org/Azkerash/create_bioships/BlockHealthTable.java` (27 lines).

```java
final class BlockHealthTable

  static final int CORE, COMPONENT; // line 6

  static final Map<String, Integer> VALUES; // line 7

  static int value(String id); // line 14

  private BlockHealthTable(); // line 24

```


#### BioshipHealthPool

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHealthPool.java` (68 lines).

```java
final class BioshipHealthPool

  static int part(BlockState state, CompoundTag data, boolean host); // line 9

  private static CompoundTag anatomy(net.minecraft.world.level.block.entity.BlockEntity actor); // line 24

  static int total( LivingCoreBlockEntity core, Map<net.minecraft.core.BlockPos, BlockState> current); // line 32

  private BioshipHealthPool(); // line 65

```


#### BioshipHealthChanges

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHealthChanges.java` (58 lines).

```java
public final class BioshipHealthChanges

  private static final Set<ServerSubLevel> DIRTY; // line 15

  public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after); // line 17

  static void changed(LivingCoreBlockEntity core); // line 34

  public static void flush(ServerTickEvent.Post event); // line 38

  public static void stop(ServerStoppedEvent event); // line 50

  private BioshipHealthChanges(); // line 55

```


#### mixin.ComponentHealthChangeMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/ComponentHealthChangeMixin.java` (21 lines).

```java
abstract class ComponentHealthChangeMixin

```


#### BioshipBody

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBody.java` (393 lines).

```java
public final class BioshipBody

  final Map<BlockPos, BlockState> memory; // line 17

  private final Set<BlockPos> injured; // line 18

  private double health; // line 19

  private int capacity; // line 20

  private boolean healthInitialized; // line 21

  private long healthAudits, lastDamageTick, lastDamageTick; // line 22

  private BioshipDeathCause lastDamageCause; // line 23

  long healthAudits(); // line 24

  void initializeHealth(LivingCoreBlockEntity core); // line 28

  private volatile boolean dead; // line 32

  private BioshipDeathCause deathCause; // line 34

  private long deathAt; // line 35

  public BioshipDeathCause deathCause(); // line 36

  public long deathAt(); // line 40

  private long lastCombatAt; // line 44

  private float lastCombatAmount; // line 46

  private long flashUntil; // line 47

  public boolean flashing(LivingCoreBlockEntity core); // line 48

  public boolean dead(); // line 52

  void starve(LivingCoreBlockEntity core, int demand); // line 56

  void drown(LivingCoreBlockEntity core); // line 60

  void asphyxiate(LivingCoreBlockEntity core); // line 64

  private void hurt(LivingCoreBlockEntity core, double amount, BioshipDeathCause cause); // line 68

  private void damageFeedback(LivingCoreBlockEntity core); // line 84

  void die(LivingCoreBlockEntity core); // line 91

  void die(LivingCoreBlockEntity core, BioshipDeathCause cause); // line 95

  public double health(); // line 119

  public int capacity(); // line 123

  static Map<BlockPos, BlockState> actual(LivingCoreBlockEntity core); // line 127

  void observe(LivingCoreBlockEntity core); // line 135

  void rebase(BlockPos delta); // line 171

  void reconcile(LivingCoreBlockEntity core, Map<BlockPos, BlockState> before); // line 180

  void tick(LivingCoreBlockEntity core); // line 199

  boolean combatDamage( LivingCoreBlockEntity core, net.minecraft.world.damagesource.DamageSource source, float amount); // line 260

  void struck(LivingCoreBlockEntity core, Player player); // line 285

  void damage(LivingCoreBlockEntity core, BlockPos pos, Player player); // line 291

  private void damage( LivingCoreBlockEntity core, BlockPos pos, Player player, BioshipDeathCause cause); // line 295

  public static void broken(BlockEvent.BreakEvent event); // line 304

  public static void blast(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event); // line 321

  CompoundTag save(); // line 336

  void load(CompoundTag tag, HolderLookup.Provider registries); // line 352

  static ListTag saveCells(Map<BlockPos, BlockState> cells); // line 369

  static Map<BlockPos, BlockState> loadCells(ListTag list, HolderLookup.Provider registries); // line 381

```


#### BioshipDeathCause

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDeathCause.java` (23 lines).

```java
public enum BioshipDeathCause

  NONE, UNKNOWN, STARVATION, DROWNING, ASPHYXIATION, CORE_REMOVED, COMBAT, EXPLOSION, INJURY; // line 4

  static BioshipDeathCause read(String value); // line 13

```


### Temporary arms and combat [active]

Threat/prey sensing and shared telegraphed swipes. Native drops remain world items; missing meats are added by a drop adapter.

```text
[combat]
  BioshipSurvivalAdaptations
  mixin.SurvivalExplosionMixin
  mixin.SurvivalBlastResistanceMixin
  BioshipDefense
  BioshipCreeperHazard
  mixin.CreeperBlastAccess
  TentacleStrike
  ImpactDamage
  BioshipPrey
  BioshipMeatDrops
```


#### BioshipSurvivalAdaptations

Source: `src/main/java/org/Azkerash/create_bioships/BioshipSurvivalAdaptations.java` (95 lines).

```java
public final class BioshipSurvivalAdaptations

  static final int MAX_BLAST_POINTS, MEAT_POINTS, SURVIVAL_POINTS; // line 15

  private int blastPoints; // line 16

  private record Subject(ServerLevel level, UUID ship); // line 17

  private static final Map<Explosion, Set<Subject>> PENDING; // line 19

  private static final ThreadLocal<Deque<Explosion>> ACTIVE; // line 21

  public int blastPoints(); // line 23

  void ateCreeperMeat(int count); // line 27

  void survivedExplosion(); // line 32

  public float blockResistance(float base); // line 36

  float damage(float amount); // line 40

  public static void begin(Explosion explosion); // line 44

  public static void end(); // line 48

  static void damaged(LivingCoreBlockEntity core); // line 52

  public static void settle(ServerTickEvent.Post event); // line 60

  public static void stop(ServerStoppedEvent event); // line 78

  CompoundTag save(); // line 84

  void load(CompoundTag tag); // line 90

```


#### mixin.SurvivalExplosionMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/SurvivalExplosionMixin.java` (20 lines).

```java
abstract class SurvivalExplosionMixin

```


#### mixin.SurvivalBlastResistanceMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/SurvivalBlastResistanceMixin.java` (29 lines).

```java
abstract class SurvivalBlastResistanceMixin

```


#### BioshipDefense

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDefense.java` (248 lines).

```java
final class BioshipDefense

  private int cooldown; // line 10

  private boolean extending; // line 11

  private final TentacleStrike strike; // line 12

  private double strikeReach; // line 13

  public net.minecraft.world.phys.Vec3 collisionTip(); // line 14

  public boolean sweeping(long now); // line 18

  private java.util.UUID attacker; // line 22

  private long threatenedUntil; // line 24

  private Vec3 lastThreat; // line 25

  void remember(LivingCoreBlockEntity core, LivingEntity entity); // line 26

  Vec3 dangerPosition(LivingCoreBlockEntity core); // line 35

  static boolean threatening(LivingCoreBlockEntity core, LivingEntity entity); // line 44

  static net.minecraft.world.entity.monster.Creeper creeperThreat(LivingCoreBlockEntity core); // line 52

  net.minecraft.nbt.CompoundTag save(); // line 70

  void load(net.minecraft.nbt.CompoundTag tag); // line 81

  static LivingEntity target(LivingCoreBlockEntity core); // line 94

  private static boolean eligiblePrey(LivingCoreBlockEntity core, LivingEntity entity); // line 135

  static LivingEntity prey(LivingCoreBlockEntity core); // line 145

  private static boolean sensesPrey(LivingCoreBlockEntity core, LivingEntity entity); // line 166

  void tick(LivingCoreBlockEntity core); // line 185

```


#### BioshipCreeperHazard

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCreeperHazard.java` (32 lines).

```java
final class BioshipCreeperHazard

  record Estimate(double damageReach, int fuseRemaining); // line 7

  static Estimate estimate(Creeper creeper); // line 16

  private BioshipCreeperHazard(); // line 29

```


#### mixin.CreeperBlastAccess

Source: `src/main/java/org/Azkerash/create_bioships/mixin/CreeperBlastAccess.java` (18 lines).

```java
public interface CreeperBlastAccess

  int bioships$explosionRadius(); // line 9

  int bioships$swell(); // line 11

  int bioships$maxSwell(); // line 14

```


#### TentacleStrike

Source: `src/main/java/org/Azkerash/create_bioships/TentacleStrike.java` (123 lines).

```java
public final class TentacleStrike

  public static final int WINDUP, SWEEP; // line 11

  public static final double TIP_HALF_SIZE; // line 12

  private Vec3 aim; // line 13

  private UUID victim; // line 14

  private long started; // line 15

  private boolean hit; // line 16

  private Vec3 previousTip; // line 17

  private long previousTick; // line 18

  public Vec3 collisionTip(); // line 19

  public boolean active(); // line 23

  public LivingEntity victim(ServerLevel level); // line 27

  void begin(LivingEntity target, long now); // line 31

  void clear(); // line 40

  public boolean finished(long now); // line 48

  public Vec3 tip(Vec3 root, double now, double reach); // line 52

  public boolean sweeping(long now); // line 63

  boolean tick( LivingCoreBlockEntity core, Vec3 root, LivingEntity target, double reach, float damage); // line 67

  static boolean tipCollision(AABB target, Vec3 previous, Vec3 current); // line 94

  CompoundTag save(); // line 102

  void load(CompoundTag tag); // line 113

```


#### ImpactDamage

Source: `src/main/java/org/Azkerash/create_bioships/ImpactDamage.java` (11 lines).

```java
final class ImpactDamage

  static float resolve(float base, double closingSpeed); // line 4

```


#### BioshipPrey

Source: `src/main/java/org/Azkerash/create_bioships/BioshipPrey.java` (15 lines).

```java
final class BioshipPrey

  static boolean eligible(LivingEntity entity); // line 8

```


#### BioshipMeatDrops

Source: `src/main/java/org/Azkerash/create_bioships/BioshipMeatDrops.java` (62 lines).

```java
public final class BioshipMeatDrops

  public static void drops(LivingDropsEvent event); // line 13

```


### Native mob targeting [active]

Inject hostile target goals; attack-surface proxy redirects native attacks to the core body.

```text
[hostile]
  BioshipHostility
  BioshipTargetEntity
```


#### BioshipHostility

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHostility.java` (34 lines).

```java
public final class BioshipHostility

  private static final java.util.Set<Mob> ATTACHED; // line 11

  public static void joined(EntityJoinLevelEvent event); // line 13

```


#### BioshipTargetEntity

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTargetEntity.java` (270 lines).

```java
public final class BioshipTargetEntity extends LivingEntity

  private static final Map< ServerLevel, Map< UUID, EnumMap< net.minecraft.core.Direction, java.lang.ref.WeakReference<BioshipTargetEntity>>>> LIVE; // line 16

  private record SurfaceCells(long at, List<net.minecraft.core.BlockPos> cells); // line 23

  private static final Map<LivingCoreBlockEntity, SurfaceCells> SURFACE_CELLS; // line 25

  static List<BioshipTargetEntity> surfaces(LivingCoreBlockEntity core); // line 27

  static BioshipTargetEntity attackSource(LivingCoreBlockEntity core, LivingEntity victim); // line 41

  private UUID shipId; // line 53

  private net.minecraft.core.Direction face; // line 55

  private LivingCoreBlockEntity cachedCore; // line 56

  public BioshipTargetEntity(EntityType<? extends BioshipTargetEntity> type, Level level); // line 57

  public static AttributeSupplier.Builder attributes(); // line 65

  public LivingCoreBlockEntity core(); // line 69

  boolean belongsTo(LivingCoreBlockEntity core); // line 84

  public static BioshipTargetEntity ensure(LivingCoreBlockEntity core); // line 88

  public void tick(); // line 145

  private void positionOnBody(LivingCoreBlockEntity core); // line 166

  public boolean hurt(DamageSource source, float amount); // line 189

  public boolean isPushable(); // line 201

  public void push(Entity entity); // line 206

  public boolean isPickable(); // line 209

  public boolean canBeHitByProjectile(); // line 214

  public void remove(RemovalReason reason); // line 219

  public double getVisibilityPercent(Entity observer); // line 231

  public Iterable<ItemStack> getArmorSlots(); // line 236

  public ItemStack getItemBySlot(EquipmentSlot slot); // line 241

  public void setItemSlot(EquipmentSlot slot, ItemStack stack); // line 246

  public HumanoidArm getMainArm(); // line 249

  public void addAdditionalSaveData(CompoundTag tag); // line 254

  public void readAdditionalSaveData(CompoundTag tag); // line 261

```


### Owner and ally permissions [active]

Owner/whitelist checks protect allies and refresh legacy membrane permissions.

```text
[allies]
  BioshipAccess
  ShipMembraneAccess
```


#### BioshipAccess

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAccess.java` (98 lines).

```java
public final class BioshipAccess

  public final Map<UUID, String> players, animals; // line 13

  public boolean allows(LivingCoreBlockEntity core, Entity entity); // line 14

  public CompoundTag save(UUID owner); // line 22

  public void load(CompoundTag tag); // line 34

  private static void read(CompoundTag tag, Map<UUID, String> out); // line 41

  public static boolean permits(CompoundTag tag, Entity entity); // line 49

  public static LivingCoreBlockEntity core(net.minecraft.world.level.Level level, BlockPos pos); // line 59

  public static void tick(LivingCoreBlockEntity core); // line 67

```


#### ShipMembraneAccess

Source: `src/main/java/org/Azkerash/create_bioships/ShipMembraneAccess.java` (7 lines).

```java
public interface ShipMembraneAccess

  void bioships$access(CompoundTag access); // line 5

```


### Corpse lineage and decay [active]

Mark core removal/death, retain saved fragment lineage and rot hull before organs/special blocks.

```text
[death]
  BioshipDecay
```


#### BioshipDecay

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDecay.java` (281 lines).

```java
public final class BioshipDecay

  static final int GRACE_TICKS; // line 15

  static final int DECAY_INTERVAL; // line 16

  static int priority(ServerLevel level, BlockPos pos); // line 17

  static final class Deaths extends net.minecraft.world.level.saveddata.SavedData; // line 32

  public static boolean dead(ServerSubLevel ship); // line 93

  public static UUID lineage(ServerSubLevel ship); // line 97

  public static void link(ServerSubLevel child, ServerSubLevel original); // line 104

  public static void mark(ServerSubLevel ship); // line 112

  public static void mark(ServerSubLevel ship, BioshipDeathCause cause); // line 116

  public static void inherit(ServerSubLevel child, ServerSubLevel original); // line 144

  static List<BlockPos> blocks(ServerSubLevel ship); // line 159

  static boolean recoverLegacyHull(ServerSubLevel part); // line 163

  public static void tick(LevelTickEvent.Post event); // line 194

  static void decay(ServerLevel level); // line 256

```


### Read-only diagnostic transport [active]

Rate-limited server snapshots of actual perception, route, chosen behavior and outstanding needs.

```text
[debug]
  BioshipNetwork
  NavigationDebug
  VisionDebug
```


#### BioshipNetwork

Source: `src/main/java/org/Azkerash/create_bioships/BioshipNetwork.java` (204 lines).

```java
public final class BioshipNetwork

  public record Throttle(BlockPos pos, int setting) implements CustomPacketPayload; // line 14

  public record SightRequest() implements CustomPacketPayload; // line 30

  public record SightSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 42

  public record PathRequest() implements CustomPacketPayload; // line 56

  public record PathSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 68

  public record TrustBurst(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 82

  public record Voice(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 96

  public static void register(RegisterPayloadHandlersEvent event); // line 110

  public static boolean setThrottle(ServerPlayer player, BlockPos pos, int setting); // line 178

```


#### NavigationDebug

Source: `src/main/java/org/Azkerash/create_bioships/NavigationDebug.java` (108 lines).

```java
public final class NavigationDebug

  public static final String DATA; // line 13

  public static CompoundTag inspect(LivingCoreBlockEntity core); // line 14

  static CompoundTag request(ServerPlayer player); // line 82

  private NavigationDebug(); // line 105

```


#### VisionDebug

Source: `src/main/java/org/Azkerash/create_bioships/VisionDebug.java` (105 lines).

```java
public final class VisionDebug

  public static final String DATA; // line 13

  public static CompoundTag inspect(LivingCoreBlockEntity core, Vec3 target); // line 14

  static String reason(LivingCoreBlockEntity core, BioshipVision.View view, Vec3 target); // line 43

  public static CompoundTag vector(Vec3 point); // line 55

  public static Vec3 vector(CompoundTag tag); // line 63

  static CompoundTag request(ServerPlayer player); // line 67

```


### Client rendering [active]

Client registration, core/collection arms, supported legs and equipment meshes.

```text
[render]
  client.BioshipClient
  client.AssimilationArmRenderer
  client.EquipmentModels
  amoeba.client.ComponentTransportRendering
  mixin.ComponentTransportRenderMixin
```


#### client.BioshipClient

Source: `src/main/java/org/Azkerash/create_bioships/client/BioshipClient.java` (47 lines).

```java
public final class BioshipClient

  public static final LivingControlHandler CONTROLS; // line 15

  public static void setup(FMLClientSetupEvent event); // line 16

  public static void startControl(Level level, Player player, BlockPos pos); // line 22

  public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event); // line 26

  public static void renderers(EntityRenderersEvent.RegisterRenderers event); // line 33

```


#### client.AssimilationArmRenderer

Source: `src/main/java/org/Azkerash/create_bioships/client/AssimilationArmRenderer.java` (343 lines).

```java
public class AssimilationArmRenderer implements BlockEntityRenderer<LivingCoreBlockEntity>

  private final ModelPart segment; // line 19

  private static final ResourceLocation SKIN; // line 20

  public AssimilationArmRenderer(BlockEntityRendererProvider.Context context); // line 23

  public boolean shouldRenderOffScreen(LivingCoreBlockEntity core); // line 33

  public void render( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 38

  private void renderBatch( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light); // line 194

  private void renderLegs( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light); // line 251

```


#### client.EquipmentModels

Source: `src/main/java/org/Azkerash/create_bioships/client/EquipmentModels.java` (58 lines).

```java
public final class EquipmentModels

  private static ModelResourceLocation extra(String name); // line 16

  public static void additional(ModelEvent.RegisterAdditional event); // line 20

  public static void bake(ModelEvent.ModifyBakingResult event); // line 26

  private static void bind( ModelEvent.ModifyBakingResult event, String item, String mesh, boolean headOnly); // line 32

```


#### amoeba.client.ComponentTransportRendering

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/client/ComponentTransportRendering.java` (57 lines).

```java
public final class ComponentTransportRendering

  private record Active(ComponentTransport.Move move, boolean swallowed); // line 15

  private static final Map<UUID, Active> ACTIVE; // line 16

  private static WeakReference<ClientLevel> level; // line 18

  private static long tick; // line 19

  private ComponentTransportRendering(); // line 20

  public static Pose3dc pose(ClientSubLevel cargo, Pose3dc original, float partial); // line 22

```


#### mixin.ComponentTransportRenderMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/ComponentTransportRenderMixin.java` (23 lines).

```java
public class ComponentTransportRenderMixin

```


### Sight, path and status overlays [active]

F3+B sight; F3+P native floating route/priority display; goggles status above hotbar.

```text
[overlay]
  BioshipDiagnostics
  client.SightDebugOverlay
  client.PathDebugOverlay
  client.AppendageDebugOverlay
  client.SymbioticTooltip
```


#### BioshipDiagnostics

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDiagnostics.java` (250 lines).

```java
public final class BioshipDiagnostics

  static List<LivingCoreBlockEntity> cores(ServerLevel level); // line 18

  static void decayMarked(ServerSubLevel ship); // line 27

  static void removalCheck( LivingCoreBlockEntity core, ServerSubLevel owner, net.minecraft.core.BlockPos pos); // line 48

  static void death(LivingCoreBlockEntity core, BioshipDeathCause cause, long at); // line 80

  static CompoundTag snapshot(LivingCoreBlockEntity core); // line 104

  public static void commands(RegisterCommandsEvent event); // line 191

```


#### client.SightDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/client/SightDebugOverlay.java` (213 lines).

```java
public final class SightDebugOverlay

  private static boolean enabled(); // line 20

  private static int nextRequest; // line 23

  private static net.minecraft.client.multiplayer.ClientLevel lastLevel; // line 25

  private static boolean equipped(); // line 26

  public static void tick(ClientTickEvent.Post event); // line 33

  private static CompoundTag snapshot(); // line 52

  private static int color(String reason); // line 60

  private static String label(String reason); // line 70

  public static void hud(RenderGuiEvent.Post event); // line 82

  private static int drawText( net.minecraft.client.gui.GuiGraphics g, String text, int y, int color); // line 137

  public static void render(RenderLevelStageEvent event); // line 150

  private static void line( com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, int color); // line 195

```


#### client.PathDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/client/PathDebugOverlay.java` (246 lines).

```java
public final class PathDebugOverlay

  private static boolean enabled; // line 21

  private static int nextRequest; // line 22

  private static net.minecraft.client.multiplayer.ClientLevel lastLevel; // line 23

  public static void toggle(); // line 24

  public static void tick(ClientTickEvent.Post event); // line 36

  private static CompoundTag snapshot(); // line 52

  private static String position(CompoundTag data, String key); // line 62

  private static final class Lines extends RenderType; // line 68

  public static void render(RenderLevelStageEvent event); // line 99

  private static String priority(CompoundTag entry, int index); // line 145

  private static void hologram( CompoundTag data, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers); // line 155

  private static void marker( PoseStack.Pose pose, VertexConsumer out, Vec3 p, double size, int color); // line 227

  private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, int color); // line 234

```


#### client.AppendageDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/client/AppendageDebugOverlay.java` (68 lines).

```java
public final class AppendageDebugOverlay

  public static void render(RenderLevelStageEvent event); // line 15

```


#### client.SymbioticTooltip

Source: `src/main/java/org/Azkerash/create_bioships/client/SymbioticTooltip.java` (90 lines).

```java
public final class SymbioticTooltip

  public static void render(RenderGuiEvent.Post event); // line 16

```


### Player tools and stomach UI [active]

Conserved manual component moves, wearable inspection and digestive inventory UI.

```text
[tools]
  BioshipWrenchItem
  BioshipGogglesItem
  StomachMenu
  client.StomachScreen
```


#### BioshipWrenchItem

Source: `src/main/java/org/Azkerash/create_bioships/BioshipWrenchItem.java` (126 lines).

```java
public class BioshipWrenchItem extends Item

  public BioshipWrenchItem(Properties properties); // line 15

  public static boolean movable(net.minecraft.world.level.Level level, BlockPos pos); // line 18

  public InteractionResult useOn(UseOnContext context); // line 35

  public InteractionResultHolder<ItemStack> use( net.minecraft.world.level.Level level, Player player, InteractionHand hand); // line 107

  public void appendHoverText( ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags); // line 118

```


#### BioshipGogglesItem

Source: `src/main/java/org/Azkerash/create_bioships/BioshipGogglesItem.java` (9 lines).

```java
public class BioshipGogglesItem extends GogglesItem

  public BioshipGogglesItem(Properties properties); // line 5

```


#### StomachMenu

Source: `src/main/java/org/Azkerash/create_bioships/StomachMenu.java` (47 lines).

```java
public class StomachMenu extends AbstractContainerMenu

  private final ContainerData data; // line 10

  private final BlockPos pos; // line 11

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach); // line 12

  private StomachMenu(int id, Inventory inventory, BlockPos pos, ContainerData data); // line 16

  public static StomachMenu fromNetwork( int id, Inventory inventory, RegistryFriendlyByteBuf buffer); // line 23

  public int value(int index); // line 28

  public ItemStack quickMoveStack(Player player, int slot); // line 32

  public boolean stillValid(Player player); // line 37

```


#### client.StomachScreen

Source: `src/main/java/org/Azkerash/create_bioships/client/StomachScreen.java` (62 lines).

```java
public class StomachScreen extends AbstractContainerScreen<StomachMenu>

  public StomachScreen(StomachMenu menu, Inventory inventory, Component title); // line 9

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY); // line 14

  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY); // line 50

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial); // line 55

```


### Packaged development world [active]

Normal-world flat/elevation glass courses, world preset and reset command.

```text
[lab]
  NavigationLab
  EcosystemLab
  AquaticLab
  OceanLabTerrain
  LabHabitatTerrain
  EndHabitatLab
  DeveloperTestTool
  LabPopulation
  LabStarters
```


#### NavigationLab

Source: `src/main/java/org/Azkerash/create_bioships/NavigationLab.java` (278 lines).

```java
public final class NavigationLab

  private static final ResourceKey<Biome> BIOME; // line 30

  private static final BlockPos FLAT, ELEVATED; // line 34

  public record Course( Map<BlockPos, BlockState> blocks, Set<BlockPos> glass, Set<BlockPos> grass, BlockPos prize, int[] gates); // line 36

  private NavigationLab(); // line 43

  public static Course course(BlockPos origin, boolean elevated); // line 45

  public static void place(ServerLevel level, Course course); // line 99

  static boolean lab(ServerLevel level); // line 107

  private static final class State extends SavedData; // line 113

  private static void build(ServerLevel level); // line 139

  private static void seed(ServerLevel level, UUID owner); // line 168

  public static void started(ServerStartedEvent event); // line 205

  public static void joined(PlayerEvent.PlayerLoggedInEvent event); // line 211

  public static void commands(RegisterCommandsEvent event); // line 227

```


#### EcosystemLab

Source: `src/main/java/org/Azkerash/create_bioships/EcosystemLab.java` (346 lines).

```java
public final class EcosystemLab

  private static final ResourceKey<net.minecraft.world.level.biome.Biome> BIOME; // line 24

  static final int RADIUS, PREY_CAP, HOSTILE_CAP; // line 28

  static final class State extends SavedData; // line 29

  static boolean lab(ServerLevel level); // line 68

  public static void start(ServerStartedEvent event); // line 75

  static void prepare(ServerLevel level, BlockPos center); // line 83

  private static void installFixtures(ServerLevel level, State state); // line 154

  static void reset(ServerLevel level); // line 162

  static BlockPos habitat(ServerLevel level, BlockPos base, int kind); // line 177

  public static void tick(ServerTickEvent.Post event); // line 210

  static void sunProtection(Mob mob); // line 222

  public static void join(PlayerEvent.PlayerLoggedInEvent event); // line 229

  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event); // line 249

```


#### AquaticLab

Source: `src/main/java/org/Azkerash/create_bioships/AquaticLab.java` (334 lines).

```java
final class AquaticLab

  static final int RADIUS, DEPTH, AIR_HEIGHT, FISH_CAP; // line 16

  private static final int LEGACY_RADIUS, VERSION; // line 17

  static final int ISOLATION_RADIUS; // line 18

  private static final class State extends SavedData; // line 20

  static BlockPos center(ServerLevel level); // line 52

  static void build(ServerLevel level, BlockPos landCenter); // line 56

  static BlockPos viewpoint(ServerLevel level); // line 156

  private static void plants(ServerLevel level, BlockPos center, boolean upgrading); // line 160

  static void isolate(ServerLevel level, BlockPos center); // line 184

  private static AABB bounds(BlockPos center); // line 250

  static int fishCount(ServerLevel level); // line 256

  static void replenish(ServerLevel level); // line 263

  static boolean contains(ServerLevel level, net.minecraft.world.phys.Vec3 point); // line 273

  static void reset(ServerLevel level, BlockPos landCenter); // line 277

  private record SpawnSites(long tick, BlockPos center, java.util.List<BlockPos> sites); // line 291

  private static final java.util.Map<ServerLevel, SpawnSites> SPAWN_SITES; // line 293

  private static void spawnFish(ServerLevel level, BlockPos center); // line 296

```


#### OceanLabTerrain

Source: `src/main/java/org/Azkerash/create_bioships/OceanLabTerrain.java` (51 lines).

```java
final class OceanLabTerrain

  static int floor(int x, int z); // line 7

  static int pattern(int x, int z); // line 15

  static Block sediment(int x, int z); // line 19

  static Block rock(int x, int y, int z); // line 24

```


#### LabHabitatTerrain

Source: `src/main/java/org/Azkerash/create_bioships/LabHabitatTerrain.java` (70 lines).

```java
final class LabHabitatTerrain

  static void land(ServerLevel level, BlockPos center); // line 8

```


#### EndHabitatLab

Source: `src/main/java/org/Azkerash/create_bioships/EndHabitatLab.java` (125 lines).

```java
final class EndHabitatLab

  static final BlockPos CENTER; // line 16

  static final int RADIUS; // line 17

  static boolean inside(double x, double y, double z); // line 18

  static BlockPos prepare(ServerLevel level); // line 25

  private static final java.util.Map<ServerLevel, Long> NEXT_REFILL; // line 88

  public static void population(ServerTickEvent.Post event); // line 90

  public static void contain(EntityTeleportEvent.EnderEntity event); // line 114

```


#### DeveloperTestTool

Source: `src/main/java/org/Azkerash/create_bioships/DeveloperTestTool.java` (119 lines).

```java
public final class DeveloperTestTool extends Item

  public DeveloperTestTool(Properties properties); // line 18

  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand); // line 21

  private static ItemStack icon(Item item, String name); // line 27

  private static void open(ServerPlayer player, int section); // line 33

  public static void joined(PlayerEvent.PlayerLoggedInEvent event); // line 95

```


#### LabPopulation

Source: `src/main/java/org/Azkerash/create_bioships/LabPopulation.java` (137 lines).

```java
final class LabPopulation

  static final String MARKER; // line 15

  private static final Map<ServerLevel, Long> LAST; // line 16

  static BlockPos controller(BlockPos center); // line 18

  static void install(ServerLevel level, BlockPos center); // line 22

  static AABB landBounds(ServerLevel level, BlockPos center); // line 33

  record Counts(int fish, int prey, int hostiles, int cores); // line 43

  static Counts counts(ServerLevel level, BlockPos center); // line 45

  static void pulse(ServerLevel level, EcosystemLab.State state); // line 64

  static final long REFILL_INTERVAL; // line 72

  static boolean refillDue(EcosystemLab.State state, long now); // line 74

  static void replenish(ServerLevel level, EcosystemLab.State state); // line 86

  static void prime(ServerLevel level, BlockPos center); // line 95

  static boolean spawnLand(ServerLevel level, BlockPos center, boolean hostile); // line 100

```


#### LabStarters

Source: `src/main/java/org/Azkerash/create_bioships/LabStarters.java` (112 lines).

```java
final class LabStarters

  private static final String MARKER; // line 14

  static void seed(ServerLevel level, EcosystemLab.State state); // line 15

  private static void component( ServerLevel level, EcosystemLab.State state, BlockPos pos, int index); // line 45

  static void core(ServerLevel level, EcosystemLab.State state, BlockPos pos); // line 55

  static void clear(ServerLevel level, EcosystemLab.State state); // line 72

```


### Sable and assembly hooks [active]

Protected assembly, safe transfer/disassembly, split lineage and Sable-owned plot unloading.

```text
[platform]
  mixin.SimAssemblyHelperMixin
  mixin.DisassemblyPreventionMixin
  mixin.ServerSubLevelMixin
  mixin.SablePlotUnloadMixin
```


#### mixin.SimAssemblyHelperMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/SimAssemblyHelperMixin.java` (19 lines).

```java
public class SimAssemblyHelperMixin

```


#### mixin.DisassemblyPreventionMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/DisassemblyPreventionMixin.java` (20 lines).

```java
public class DisassemblyPreventionMixin

```


#### mixin.ServerSubLevelMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/ServerSubLevelMixin.java` (17 lines).

```java
public class ServerSubLevelMixin

```


#### mixin.SablePlotUnloadMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/SablePlotUnloadMixin.java` (30 lines).

```java
abstract class SablePlotUnloadMixin

  private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads; // line 18

```


### Membrane and key hooks [active]

Native biometric permission/collision hooks and independent F3+P key handling.

```text
[accesshooks]
  mixin.BiometricMembraneAccessMixin
  mixin.BiometricSubLevelCollisionMixin
  mixin.PathDebugKeyMixin
```


#### mixin.BiometricMembraneAccessMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/BiometricMembraneAccessMixin.java` (55 lines).

```java
public class BiometricMembraneAccessMixin implements ShipMembraneAccess

  private CompoundTag bioships$permissions; // line 16

  public void bioships$access(CompoundTag access); // line 17

```


#### mixin.BiometricSubLevelCollisionMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/BiometricSubLevelCollisionMixin.java` (34 lines).

```java
public class BiometricSubLevelCollisionMixin

```


#### mixin.PathDebugKeyMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/PathDebugKeyMixin.java` (19 lines).

```java
abstract class PathDebugKeyMixin

```


### Airship implementation boundary [gate]

available() returns false. Saved stage data does not activate hull construction or flight.

```text
[gate]
  AirshipPrototype
```


#### AirshipPrototype

Source: `src/main/java/org/Azkerash/create_bioships/AirshipPrototype.java` (31 lines).

```java
public final class AirshipPrototype

  private AirshipPrototype(); // line 7

  public static boolean available(); // line 8

  public static boolean component(Block block); // line 12

```


### Preserved hull and room geometry [dormant]

Prototype shell, bay capacity and attachment-to-room attribution; shared constants/geometry are retained.

```text
[layout]
  DinghyGrowth
  EngineRoomLayout
  EngineRoomSizing
  BioshipRoomOwnership
```


#### DinghyGrowth

Source: `src/main/java/org/Azkerash/create_bioships/DinghyGrowth.java` (207 lines).

```java
public final class DinghyGrowth

  public static final int LAYOUT_VERSION; // line 12

  public static final BlockPos HELM, THROTTLE, VERTICAL_TRIM, HUNT, ACCESS_PANEL; // line 13

  public static final BlockPos BLUEPRINT_PANEL; // line 18

  public static final BlockPos DOOR, MOUTH, LARYNX; // line 19

  public static final List<BlockPos> ACCESS_MEMBRANES; // line 22

  public static final List<BlockPos> CABIN_LIGHTS; // line 24

  public static final List<BlockPos> FLAGELLA; // line 30

  public static final List<BlockPos> BUOYANCY_ORGANS; // line 38

  public static final BlockPos PERCH; // line 48

  public record Part(BlockPos offset, BlockState state); // line 50

  public static BlockState cabinLantern(); // line 52

  public static BlockState helm(); // line 60

  public static int radius(int z); // line 68

  public static int radius(int z, int version); // line 72

  public static BlockPos atVersion(BlockPos offset, int version); // line 83

  public static List<Part> body(); // line 93

  public static List<Part> body(int version); // line 97

  private static final class Layout; // line 101

  private static List<Part> createBody(int version); // line 105

  private static BlockState shell(Direction facing); // line 199

```


#### EngineRoomLayout

Source: `src/main/java/org/Azkerash/create_bioships/EngineRoomLayout.java` (47 lines).

```java
final class EngineRoomLayout

  static boolean skin(BlockState state); // line 9

  static List<DinghyGrowth.Part> body(int version, int columns); // line 14

  static BlockPos at(LivingCoreBlockEntity core, BlockPos local); // line 42

```


#### EngineRoomSizing

Source: `src/main/java/org/Azkerash/create_bioships/EngineRoomSizing.java` (20 lines).

```java
final class EngineRoomSizing

  static int columns(int digestivePairs); // line 4

  static int capacity(int columns); // line 7

  static int widening(int columns); // line 11

  static int width(int columns); // line 15

```


#### BioshipRoomOwnership

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRoomOwnership.java` (31 lines).

```java
final class BioshipRoomOwnership

  static Map<BlockPos, BioshipBlueprint.Room> assign( Set<BlockPos> cells, List<BioshipBlueprint.Room> rooms, BlockPos structuralOffset); // line 7

```


### Preserved engine-room investment [dormant]

Room quotes and expansion are disabled. EngineRoom.storage() remains a shared native-sac predicate.

```text
[engine]
  BioshipEngineRoomPlanner
  BioshipEngineRoom
  EngineRoom
```


#### BioshipEngineRoomPlanner

Source: `src/main/java/org/Azkerash/create_bioships/BioshipEngineRoomPlanner.java` (164 lines).

```java
final class BioshipEngineRoomPlanner

  record Quote( int columns, int pairs, Map<BlockPos, BlockState> changes, MaterialCost cost, int legUpgrades); // line 13

  static Quote quote(LivingCoreBlockEntity core); // line 32

  static String reason(LivingCoreBlockEntity core); // line 36

  private static Quote blocked(java.util.function.Consumer<String> report, String reason); // line 43

  private static Quote quote( LivingCoreBlockEntity core, java.util.function.Consumer<String> report); // line 48

```


#### BioshipEngineRoom

Source: `src/main/java/org/Azkerash/create_bioships/BioshipEngineRoom.java` (57 lines).

```java
final class BioshipEngineRoom

  private int columns; // line 9

  int columns(); // line 10

  int capacity(); // line 14

  int widening(); // line 18

  void restore(int value); // line 22

  BioshipEngineRoomPlanner.Quote quote(LivingCoreBlockEntity core); // line 26

  boolean expand(LivingCoreBlockEntity core); // line 30

```


#### EngineRoom

Source: `src/main/java/org/Azkerash/create_bioships/EngineRoom.java` (88 lines).

```java
public final class EngineRoom

  public static boolean inside(LivingCoreBlockEntity core, BlockPos pos); // line 10

  public static boolean storage(BlockEntity entity); // line 18

  static List<BlockPos> stomachBays(LivingCoreBlockEntity core); // line 25

  public static void arrange(LivingCoreBlockEntity core, ServerSubLevel ship); // line 34

  public static boolean move( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos source, BlockPos target); // line 81

```


### Preserved reconstruction prototype [dormant]

Reconstruction jobs and UI are disabled; BioshipBlueprint.physical() remains a shared coordinate helper.

```text
[blueprint]
  BioshipBlueprint
  BlueprintMenu
  BlueprintPanelBlock
  client.BlueprintScreen
```


#### BioshipBlueprint

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBlueprint.java` (866 lines).

```java
public final class BioshipBlueprint

  public record Room(int id, String name, int x, int z, int width, int length); // line 20

  public static class Draft; // line 50

  private List<Room> rooms; // line 88

  private List<Room> pendingRooms; // line 92

  private final Map<BlockPos, BlockState> target; // line 93

  private final Map<BlockPos, CompoundTag> cargo; // line 94

  private final Map<BlockPos, BlockPos> sources; // line 95

  private String phase, status; // line 96

  private int revision, search, workTicks, carryTicks; // line 97

  private Vec3 landing, searchGoal, carryStart, carryEnd; // line 98

  private UUID carried; // line 99

  private int paid; // line 100

  private int baseCockpitZ; // line 101

  private boolean demolitionDone; // line 102

  void engineWidth(int width); // line 103

  public List<Room> rooms(); // line 109

  public String phase(); // line 113

  public String status(); // line 117

  public int revision(); // line 121

  public boolean busy(); // line 125

  public boolean locked(); // line 129

  public UUID carried(); // line 133

  public int carryTicks(); // line 137

  public Vec3 carryEnd(); // line 141

  public Vec3 carryStart(); // line 145

  public BlockPos offset(BlockPos original); // line 149

  public BlockPos engineLocal(BlockPos local); // line 158

  public int available(LivingCoreBlockEntity core); // line 163

  private static BlockPos structureLocal(LivingCoreBlockEntity core, BlockPos local); // line 168

  private static BlockPos coreLocal(LivingCoreBlockEntity core, BlockPos local); // line 172

  public Map<BlockPos, BlockState> proposal(LivingCoreBlockEntity core, Draft draft); // line 176

  public int cost(LivingCoreBlockEntity core, Draft draft); // line 238

  public String validate(LivingCoreBlockEntity core, Draft d, Map<BlockPos, BlockState> plan); // line 245

  private static boolean editable(BlockState s); // line 313

  public boolean confirm(LivingCoreBlockEntity core, Draft d, int baseRevision); // line 325

  public void tick(LivingCoreBlockEntity core); // line 375

  Vec3 site(LivingCoreBlockEntity core, Vec3 candidate); // line 441

  public Vec3 flight(LivingCoreBlockEntity core); // line 472

  static boolean worldClear(net.minecraft.world.level.Level level, Vec3 from, Vec3 to); // line 502

  static Vec3 physical(LivingCoreBlockEntity core, BlockPos relative); // line 513

  boolean aboard(LivingCoreBlockEntity core, Entity entity); // line 519

  private Vec3 exit(LivingCoreBlockEntity core); // line 538

  private boolean evacuate(LivingCoreBlockEntity core); // line 565

  private void sync(LivingCoreBlockEntity core); // line 629

  private void clear(LivingCoreBlockEntity core, BlockPos local); // line 636

  private static boolean matches(BlockState live, BlockState wanted); // line 642

  private void build(LivingCoreBlockEntity core); // line 651

  int burnBufferedReserve(); // line 753

  CompoundTag save(); // line 767

  void load(CompoundTag t, HolderLookup.Provider registries); // line 812

  private static CompoundTag vector(Vec3 v); // line 853

  private static Vec3 readVector(CompoundTag t); // line 861

```


#### BlueprintMenu

Source: `src/main/java/org/Azkerash/create_bioships/BlueprintMenu.java` (115 lines).

```java
public class BlueprintMenu extends AbstractContainerMenu

  public final LivingCoreBlockEntity model; // line 12

  public final BioshipBlueprint.Draft draft; // line 13

  private final LivingCoreBlockEntity core; // line 14

  private final BlockPos panel; // line 15

  private final int revision; // line 16

  private final ContainerData data; // line 17

  public BlueprintMenu(int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos panel); // line 18

  public static BlueprintMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf b); // line 46

  public int available(); // line 59

  public int cost(); // line 63

  public void edit(int action); // line 67

  public boolean clickMenuButton(Player player, int action); // line 71

  public boolean stillValid(Player p); // line 95

  public ItemStack quickMoveStack(Player p, int slot); // line 109

```


#### BlueprintPanelBlock

Source: `src/main/java/org/Azkerash/create_bioships/BlueprintPanelBlock.java` (67 lines).

```java
public class BlueprintPanelBlock extends HorizontalDirectionalBlock

  public static final MapCodec<BlueprintPanelBlock> CODEC; // line 15

  public BlueprintPanelBlock(Properties properties); // line 16

  protected MapCodec<? extends HorizontalDirectionalBlock> codec(); // line 21

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 26

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 31

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 36

```


#### client.BlueprintScreen

Source: `src/main/java/org/Azkerash/create_bioships/client/BlueprintScreen.java` (181 lines).

```java
public class BlueprintScreen extends AbstractContainerScreen<BlueprintMenu>

  private int mode; // line 11

  public BlueprintScreen(BlueprintMenu menu, Inventory inventory, Component title); // line 12

  private void send(int id); // line 18

  protected void init(); // line 28

  public boolean mouseClicked(double x, double y, int button); // line 96

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY); // line 106

  protected void renderLabels(GuiGraphics g, int x, int y); // line 150

```


### Preserved flight controls [dormant]

Control input and throttle packets are rejected; client hold-control registration is disabled.

```text
[controls]
  LivingHelmBlock
  LivingThrottleBlock
  NerveControlBlock
  PilotAuthority
  client.LivingControlHandler
```


#### LivingHelmBlock

Source: `src/main/java/org/Azkerash/create_bioships/LivingHelmBlock.java` (56 lines).

```java
public class LivingHelmBlock extends Block

  public static final IntegerProperty STEERING; // line 15

  public LivingHelmBlock(Properties properties); // line 16

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 21

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 26

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 32

```


#### LivingThrottleBlock

Source: `src/main/java/org/Azkerash/create_bioships/LivingThrottleBlock.java` (71 lines).

```java
public class LivingThrottleBlock extends Block implements QuietUse

  public static final IntegerProperty THROTTLE; // line 15

  public static final int NEUTRAL; // line 16

  public enum Kind; // line 17

  private final Kind kind; // line 22

  public LivingThrottleBlock(Properties properties); // line 24

  public LivingThrottleBlock(Properties properties, Kind kind); // line 28

  public Kind kind(); // line 34

  public static double signed(BlockState state); // line 38

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 42

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 47

  public InteractionResult quietUse( Player player, InteractionHand hand, BlockPos pos, BlockState state); // line 53

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 61

```


#### NerveControlBlock

Source: `src/main/java/org/Azkerash/create_bioships/NerveControlBlock.java` (107 lines).

```java
public class NerveControlBlock extends Block

  public static final BooleanProperty LATCHED; // line 15

  public static final BooleanProperty ACTIVE; // line 16

  public enum Command; // line 17

  private static final VoxelShape SHAPE; // line 41

  public final Command command; // line 43

  public NerveControlBlock(Command command, Properties properties); // line 44

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 50

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 55

  protected void neighborChanged( BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moved); // line 61

  protected void onPlace( BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved); // line 67

  private void updateSignal(BlockState state, Level level, BlockPos pos); // line 74

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 80

```


#### PilotAuthority

Source: `src/main/java/org/Azkerash/create_bioships/PilotAuthority.java` (13 lines).

```java
final class PilotAuthority

  static final int TRUST_THRESHOLD; // line 4

  private PilotAuthority(); // line 5

  static boolean obeys( int trust, boolean authorized, boolean present, boolean aboard, long now, long commandUntil); // line 7

```


#### client.LivingControlHandler

Source: `src/main/java/org/Azkerash/create_bioships/client/LivingControlHandler.java` (94 lines).

```java
public final class LivingControlHandler extends BlockHoldInteraction

  private double value; // line 16

  private int sent; // line 17

  private LivingThrottleBlock.Kind kind; // line 18

  public void startHold(Level level, Player player, BlockPos pos); // line 19

  public boolean activeOnMouseMove(double yaw, double pitch); // line 29

  private void send(); // line 37

  public boolean activeTick(Level level, LocalPlayer player); // line 45

  public int getCrouchBlockingTicks(); // line 59

  public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui); // line 64

```


### Preserved cockpit models and UI [dormant]

Models, registry IDs, shapes and screens retained; no active airship progression or control UI.

```text
[cockpit]
  CaptainSeatBlock
  BuoyancyOrganBlock
  LivingDoorBlock
  AccessMenu
  AccessPanelBlock
  client.AccessScreen
```


#### CaptainSeatBlock

Source: `src/main/java/org/Azkerash/create_bioships/CaptainSeatBlock.java` (17 lines).

```java
public class CaptainSeatBlock extends SeatBlock

  private static final VoxelShape SHAPE; // line 12

  public CaptainSeatBlock(Properties properties); // line 14

  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 15

```


#### BuoyancyOrganBlock

Source: `src/main/java/org/Azkerash/create_bioships/BuoyancyOrganBlock.java` (35 lines).

```java
public final class BuoyancyOrganBlock extends Block

  public static final MapCodec<BuoyancyOrganBlock> CODEC; // line 13

  private static final VoxelShape SHAPE; // line 14

  public BuoyancyOrganBlock(Properties properties); // line 19

  protected MapCodec<? extends Block> codec(); // line 23

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 28

```


#### LivingDoorBlock

Source: `src/main/java/org/Azkerash/create_bioships/LivingDoorBlock.java` (27 lines).

```java
public class LivingDoorBlock extends DoorBlock

  public LivingDoorBlock(Properties properties); // line 15

  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 16

  public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open); // line 20

```


#### AccessMenu

Source: `src/main/java/org/Azkerash/create_bioships/AccessMenu.java` (120 lines).

```java
public class AccessMenu extends AbstractContainerMenu

  public record Choice(UUID id, String label, boolean animal); // line 12

  private final LivingCoreBlockEntity core; // line 13

  private final BlockPos pos; // line 15

  public final List<Choice> choices; // line 16

  private final ContainerData allowed; // line 17

  public AccessMenu( int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos pos, List<Choice> choices); // line 18

  public static AccessMenu fromNetwork( int id, Inventory inventory, RegistryFriendlyByteBuf buffer); // line 45

  public boolean allowed(int index); // line 56

  public static List<Choice> choices(LivingCoreBlockEntity core); // line 60

  public boolean clickMenuButton(Player player, int index); // line 81

  public boolean stillValid(Player player); // line 102

  public ItemStack quickMoveStack(Player player, int slot); // line 114

```


#### AccessPanelBlock

Source: `src/main/java/org/Azkerash/create_bioships/AccessPanelBlock.java` (55 lines).

```java
public class AccessPanelBlock extends Block

  public AccessPanelBlock(Properties properties); // line 13

  protected net.minecraft.world.phys.shapes.VoxelShape getShape( BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context); // line 16

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 25

```


#### client.AccessScreen

Source: `src/main/java/org/Azkerash/create_bioships/client/AccessScreen.java` (95 lines).

```java
public class AccessScreen extends AbstractContainerScreen<AccessMenu>

  private int page; // line 11

  private final List<Button> entries; // line 12

  public AccessScreen(AccessMenu menu, Inventory inventory, Component title); // line 13

  protected void init(); // line 19

  private void buttons(); // line 25

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY); // line 67

  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY); // line 73

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial); // line 80

```


### Preserved flagella prototype [dormant]

Physical flagella source/models retained. Mature-owner hunting and flight muscles are inactive; temporary hatchling arms remain active.

```text
[legacyarms]
  TentacleBlockEntity
  TentacleBlock
  FlagellumMotion
  client.FlagellumRenderer
```


#### TentacleBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/TentacleBlockEntity.java` (205 lines).

```java
public class TentacleBlockEntity extends BlockEntity implements BlockEntitySubLevelActor

  private UUID target; // line 21

  private final TentacleStrike strike; // line 22

  public TentacleStrike strike(); // line 23

  private int clientTarget; // line 27

  private long lastTick; // line 29

  public TentacleBlockEntity(BlockPos pos, BlockState state); // line 30

  public boolean swimming(); // line 34

  public int targetEntityId(); // line 38

  public UUID targetId(); // line 42

  private ServerSubLevel ship(); // line 46

  public LivingCoreBlockEntity owner(ServerSubLevel ship); // line 54

  static boolean attackable(LivingEntity entity); // line 62

  boolean valid(LivingEntity entity, LivingCoreBlockEntity core); // line 70

  static boolean edible(LivingEntity entity); // line 81

  private boolean claimed(ServerSubLevel ship, UUID entity); // line 85

  private void syncTarget(); // line 93

  private void release(); // line 98

  public void setRemoved(); // line 108

  public void sable$tick(ServerSubLevel ship); // line 114

  public void serverTick(); // line 119

  boolean visible( Vec3 from, Vec3 to, Entity target, LivingCoreBlockEntity core, ServerSubLevel ship); // line 167

  public ClientboundBlockEntityDataPacket getUpdatePacket(); // line 182

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 187

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 195

```


#### TentacleBlock

Source: `src/main/java/org/Azkerash/create_bioships/TentacleBlock.java` (21 lines).

```java
public class TentacleBlock extends BaseEntityBlock

  public static final MapCodec<TentacleBlock> CODEC; // line 11

  public TentacleBlock(Properties properties); // line 12

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 13

  protected RenderShape getRenderShape(BlockState state); // line 14

  protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 15

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 16

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type); // line 17

```


#### FlagellumMotion

Source: `src/main/java/org/Azkerash/create_bioships/FlagellumMotion.java` (9 lines).

```java
public final class FlagellumMotion

  private FlagellumMotion(); // line 3

  public static double strength(int available, long ticks); // line 4

```


#### client.FlagellumRenderer

Source: `src/main/java/org/Azkerash/create_bioships/client/FlagellumRenderer.java` (130 lines).

```java
public class FlagellumRenderer implements BlockEntityRenderer<TentacleBlockEntity>

  private static final ResourceLocation SKIN; // line 20

  private final ModelPart segment; // line 23

  public FlagellumRenderer(BlockEntityRendererProvider.Context context); // line 24

  public boolean shouldRenderOffScreen(TentacleBlockEntity entity); // line 34

  public void render( TentacleBlockEntity limb, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 39

```


### Preserved flight rescue [dormant]

New flight rescues are unavailable; saved active rescues may close safely and restore retracted tissue.

```text
[rescue]
  BioshipRescue
  mixin.ReconfigurationHeatMapMixin
```


#### BioshipRescue

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRescue.java` (373 lines).

```java
public final class BioshipRescue

  private final Map<BlockPos, BlockState> retracted; // line 14

  private List<BlockPos> path; // line 15

  private UUID passenger; // line 16

  private int cursor, animation, stalled; // line 17

  private String phase; // line 18

  private Vec3 carried; // line 19

  private long nextAttempt; // line 20

  public boolean active(); // line 21

  public Vec3 carriedPosition(); // line 25

  public boolean retracted(BlockPos pos); // line 29

  public Map<BlockPos, BlockState> tissue(); // line 33

  public double withdrawal(); // line 37

  private static boolean hull(BlockState state); // line 43

  private Set<BlockPos> obstruction(LivingCoreBlockEntity core, Vec3 world, boolean allowHull); // line 50

  private List<BlockPos> route(LivingCoreBlockEntity core, Player player, BlockPos goal); // line 97

  boolean begin(LivingCoreBlockEntity core, Player player); // line 139

  void tick(LivingCoreBlockEntity core); // line 189

  private static boolean connectedWithout(LivingCoreBlockEntity core, Set<BlockPos> holes); // line 320

  private void sync(LivingCoreBlockEntity core); // line 337

  CompoundTag save(); // line 343

  void load(CompoundTag tag, HolderLookup.Provider registries); // line 357

```


#### mixin.ReconfigurationHeatMapMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/ReconfigurationHeatMapMixin.java` (25 lines).

```java
public class ReconfigurationHeatMapMixin

  private ServerSubLevel subLevel; // line 12

```


### Leg capabilities and mouth-bound graspers [active]

Reloadable movement capabilities, physical surface contacts, per-mouth grasper slots and shared sucker rendering. Legs remain separate and are the future flight lineage.

```text
[appendages]
  LocomotionProfile
  LocomotionProfiles
  BioshipSurfaceContacts
  BioshipGraspers
  BioshipGrasperEvolution
  client.TentacleSuckers
  MaterialRenderPass
```


#### LocomotionProfile

Source: `src/main/java/org/Azkerash/create_bioships/LocomotionProfile.java` (30 lines).

```java
record LocomotionProfile( boolean walls, boolean ceilings, double gripReach, double comfortableClearance, int directDistance, int quickNodes, int searchNodes)

  static final LocomotionProfile TENTACLES; // line 11

  LocomotionProfile; // line 13

```


#### LocomotionProfiles

Source: `src/main/java/org/Azkerash/create_bioships/LocomotionProfiles.java` (67 lines).

```java
public final class LocomotionProfiles extends SimpleJsonResourceReloadListener

  private static volatile Map<ResourceLocation, LocomotionProfile> profiles; // line 14

  private static final ResourceLocation DEFAULT; // line 15

  private static final Map<ResourceLocation, java.util.function.Predicate<LivingCoreBlockEntity>> adapters; // line 17

  private LocomotionProfiles(); // line 19

  public static void register(AddReloadListenerEvent event); // line 23

  public static void registerAdapter( ResourceLocation profile, java.util.function.Predicate<LivingCoreBlockEntity> matches); // line 28

  static LocomotionProfile forCreature(LivingCoreBlockEntity core); // line 33

  protected void apply( Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler); // line 40

```


#### BioshipSurfaceContacts

Source: `src/main/java/org/Azkerash/create_bioships/BioshipSurfaceContacts.java` (72 lines).

```java
final class BioshipSurfaceContacts

  static GroundRoute.Grip grip(LivingCoreBlockEntity core, Vec3 point); // line 11

  static BlockHitResult terrainRay(ServerLevel level, Vec3 from, Vec3 to); // line 57

```


#### BioshipGraspers

Source: `src/main/java/org/Azkerash/create_bioships/BioshipGraspers.java` (95 lines).

```java
public final class BioshipGraspers

  private static final java.util.List< java.util.function.Function<LivingCoreBlockEntity, java.util.List<Slot>>> providers; // line 10

  public static void registerProvider( java.util.function.Function<LivingCoreBlockEntity, java.util.List<Slot>> provider); // line 13

  public record Slot(String key, BlockPos mouth, int ordinal, Vec3 root, boolean swimming); // line 19

  public static List<Slot> slots(LivingCoreBlockEntity core); // line 25

  static Slot available(LivingCoreBlockEntity core, Vec3 target, Collection<String> busy); // line 66

  public static boolean swimming(LivingCoreBlockEntity core, String key); // line 75

  static double attackReach(LivingCoreBlockEntity core, Vec3 target); // line 79

  public static Vec3 root(LivingCoreBlockEntity core, String key); // line 84

```


#### BioshipGrasperEvolution

Source: `src/main/java/org/Azkerash/create_bioships/BioshipGrasperEvolution.java` (53 lines).

```java
final class BioshipGrasperEvolution

  record Change(FeedingMouthBlockEntity mouth, int ordinal, boolean swimming, MaterialCost cost); // line 6

  static Change plan(LivingCoreBlockEntity core); // line 7

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 26

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos); // line 42

```


#### client.TentacleSuckers

Source: `src/main/java/org/Azkerash/create_bioships/client/TentacleSuckers.java` (48 lines).

```java
final class TentacleSuckers

  private static final ResourceLocation TEXTURE; // line 11

  private static final ModelPart CUPS; // line 14

  private static ModelPart bake(); // line 15

  static void renderSegment( ModelPart segment, PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light, int overlay, int index); // line 30

```


#### MaterialRenderPass

Source: `src/main/java/org/Azkerash/create_bioships/MaterialRenderPass.java` (17 lines).

```java
public final class MaterialRenderPass

  public static <Material, Buffer> void draw( Function<Material, Buffer> buffers, Material skin, Material detail, Consumer<Buffer> drawSkin, Consumer<Buffer> drawDetail, boolean includeDetail); // line 6

```


### Ordinary tissue and reserved armor [active]

New ordinary tissue uses biomancy:packed_flesh. Legacy living_hull and new living_hull_tier_2 retain armor IDs; embedded hosts preserve their material.

```text
[tissue]
  BioshipTissue
```


#### BioshipTissue

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTissue.java` (43 lines).

```java
public final class BioshipTissue

  static final MaterialCost GROWTH_COST; // line 7

  public static Block body(); // line 9

  public static boolean isTissue(BlockState state); // line 13

  static void remember( net.minecraft.world.level.block.entity.BlockEntity entity, BlockState state); // line 17

  static BlockState host(net.minecraft.world.level.block.entity.BlockEntity entity); // line 27

```


### Trust, poison and damage feedback [active]

Shared trust cooldown; continuous poison status; synchronized whole-body hurt overlay. Feedback never owns a navigation goal.

```text
[feedback]
  BioshipTrustFeedback
  BioshipPoisonFeedback
  BioshipParticleSurface
  TrustParticleBudget
  client.TrustParticleFeedback
  client.BioshipHurtFlash
```


#### BioshipTrustFeedback

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTrustFeedback.java` (50 lines).

```java
final class BioshipTrustFeedback

  static final String INBOX; // line 8

  static final int PARTICLES; // line 9

  private long nextTick; // line 10

  boolean ready(long tick); // line 11

  long nextTick(); // line 15

  void restore(long value); // line 19

  boolean emit(LivingCoreBlockEntity core, boolean positive); // line 23

```


#### BioshipPoisonFeedback

Source: `src/main/java/org/Azkerash/create_bioships/BioshipPoisonFeedback.java` (33 lines).

```java
final class BioshipPoisonFeedback

  static boolean active(LivingCoreBlockEntity core); // line 8

  static void tick(LivingCoreBlockEntity core); // line 11

```


#### BioshipParticleSurface

Source: `src/main/java/org/Azkerash/create_bioships/BioshipParticleSurface.java` (23 lines).

```java
final class BioshipParticleSurface

  static List<Vec3> points(LivingCoreBlockEntity core); // line 8

```


#### TrustParticleBudget

Source: `src/main/java/org/Azkerash/create_bioships/TrustParticleBudget.java` (16 lines).

```java
public final class TrustParticleBudget

  public static final int PARTICLES, FRAMES; // line 4

  public static final long MAX_AGE_NANOS; // line 5

  private int frame, point; // line 6

  public int next(long ageNanos, int available); // line 7

```


#### client.TrustParticleFeedback

Source: `src/main/java/org/Azkerash/create_bioships/client/TrustParticleFeedback.java` (65 lines).

```java
public final class TrustParticleFeedback

  private static final String INBOX; // line 16

  private static final List<Burst> active; // line 17

  private static net.minecraft.client.multiplayer.ClientLevel lastLevel; // line 18

  private static final class Burst; // line 19

  public static void render(RenderLevelStageEvent event); // line 30

```


#### client.BioshipHurtFlash

Source: `src/main/java/org/Azkerash/create_bioships/client/BioshipHurtFlash.java` (49 lines).

```java
final class BioshipHurtFlash

  static int overlay(net.minecraft.world.level.block.entity.BlockEntity entity, int fallback); // line 12

  static void render( LivingCoreBlockEntity core, PoseStack pose, MultiBufferSource buffers, int light); // line 22

```


### Per-mouth voice and articulation [active]

A larynx is conserved inside one upgraded mouth. Paid voice growth follows functional digestion and urgent survival. A-Z is a pre-speech creature palette, not spoken letter names.

```text
[voice]
  BioshipLarynx
  BioshipVoiceCommands
  BioshipVoice
  BioshipVoicePalette
  client.BioshipVoicePlayer
  client.MouthRenderer
```


#### BioshipLarynx

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLarynx.java` (113 lines).

```java
final class BioshipLarynx

  static final MaterialCost COST; // line 14

  static boolean isLarynx(BlockState state); // line 15

  static List<FeedingMouthBlockEntity> mouths(LivingCoreBlockEntity core); // line 22

  static long missingCount(LivingCoreBlockEntity core); // line 30

  static boolean missing(LivingCoreBlockEntity core); // line 34

  static boolean ready(LivingCoreBlockEntity core); // line 38

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 42

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos); // line 64

  private static BlockEntity nativeSource( LivingCoreBlockEntity core, FeedingMouthBlockEntity mouth); // line 84

  static void growl(LivingCoreBlockEntity core); // line 96

  static void hurt(LivingCoreBlockEntity core); // line 100

  private static void vocalize(LivingCoreBlockEntity core, boolean hurt); // line 104

```


#### BioshipVoiceCommands

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVoiceCommands.java` (68 lines).

```java
public final class BioshipVoiceCommands

  public static void register(RegisterCommandsEvent event); // line 14

```


#### BioshipVoice

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVoice.java` (59 lines).

```java
final class BioshipVoice

  enum Utterance; // line 12

  static void play(FeedingMouthBlockEntity mouth, CompoundTag capsule, Utterance utterance); // line 16

  static void letter(FeedingMouthBlockEntity mouth, CompoundTag capsule, char letter); // line 24

  private static void emit( FeedingMouthBlockEntity mouth, CompoundTag capsule, BioshipVoicePalette.Profile profile, boolean hurt); // line 28

```


#### BioshipVoicePalette

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVoicePalette.java` (20 lines).

```java
public final class BioshipVoicePalette

  public record Profile(float pitch, int ticks, float volume, int attack, boolean breathy); // line 4

  private static final float[] PITCH; // line 5

  public static Profile letter(char value); // line 10

```


#### client.BioshipVoicePlayer

Source: `src/main/java/org/Azkerash/create_bioships/client/BioshipVoicePlayer.java` (84 lines).

```java
public final class BioshipVoicePlayer

  public static void tick(ClientTickEvent.Post event); // line 20

  private static final class VocalSound extends AbstractTickableSoundInstance; // line 32

```


#### client.MouthRenderer

Source: `src/main/java/org/Azkerash/create_bioships/client/MouthRenderer.java` (88 lines).

```java
public final class MouthRenderer implements BlockEntityRenderer<FeedingMouthBlockEntity>

  public MouthRenderer(BlockEntityRendererProvider.Context context); // line 19

  private static ModelResourceLocation model(String name); // line 20

  public static void models(ModelEvent.RegisterAdditional event); // line 25

  public void render( FeedingMouthBlockEntity mouth, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 30

  private static void part( FeedingMouthBlockEntity mouth, String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 63

```
