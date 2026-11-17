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
  registry.Create_bioships
  LivingCoreBlock
```


#### registry.Create_bioships

Source: `src/main/java/org/Azkerash/create_bioships/registry/Create_bioships.java` (457 lines).

```java
public class Create_bioships

  public static final String MOD_ID; // line 48

  public static final DeferredRegister.Blocks BLOCKS; // line 49

  public static final DeferredRegister.Items ITEMS; // line 50

  public static final java.util.Map<String, DeferredItem<Item>> MOB_MEATS; // line 51

  private static java.util.Map<String, DeferredItem<Item>> registerMeats(); // line 52

  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES; // line 70

  public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES; // line 73

  public static final net.neoforged.neoforge.registries.DeferredHolder< net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<BioshipTargetEntity>> BIOSHIP_TARGET; // line 75

  public static final DeferredRegister<CreativeModeTab> TABS; // line 89

  public static final DeferredBlock<LivingCoreBlock> CORE; // line 91

  public static final DeferredBlock<LivingThrottleBlock> THROTTLE; // line 109

  public static final DeferredBlock<LivingThrottleBlock> VERTICAL_TRIM; // line 119

  public static final DeferredBlock<PulseLightBlock> PULSE_LIGHT; // line 129

  public static final DeferredBlock<SurfaceEyeBlock> SURFACE_EYE; // line 139

  public static final DeferredBlock<OneWayWindowBlock> ONE_WAY_WINDOW; // line 148

  public static final DeferredBlock<AccessPanelBlock> ACCESS_PANEL; // line 157

  public static final DeferredBlock<BlueprintPanelBlock> BLUEPRINT_PANEL; // line 166

  public static final DeferredBlock<StomachBlock> STOMACH; // line 172

  public static final DeferredBlock<IntestineBlock> INTESTINE; // line 178

  public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS; // line 187

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<StomachMenu>> STOMACH_MENU; // line 189

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<AccessMenu>> ACCESS_MENU; // line 198

  public static final DeferredHolder< net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<BlueprintMenu>> BLUEPRINT_MENU; // line 207

  public static final DeferredBlock<FeedingMouthBlock> FEEDING_MOUTH; // line 216

  public static final DeferredBlock<Block> MOUTH; // line 225

  public static final DeferredBlock<BuoyancyOrganBlock> BUOYANCY_ORGAN; // line 229

  public static final DeferredBlock<Block> FLESH; // line 239

  public static final DeferredBlock<Block> ARMOR_II; // line 242

  public static final DeferredBlock<CaptainSeatBlock> PERCH; // line 248

  public static final DeferredBlock<CarapaceBlock> CARAPACE; // line 258

  public static final DeferredBlock<TransparentBlock> MEMBRANE; // line 267

  public static final DeferredBlock<LivingDoorBlock> DOOR; // line 276

  public static final DeferredBlock<AmoebaLegBlock> AMOEBA_LEG; // line 285

  public static final DeferredBlock<AmoebaLegBlock> SWIMMING_LEG; // line 294

  public static final DeferredBlock<RespiratoryOrganBlock> LUNGS; // line 303

  public static final DeferredBlock<RespiratoryOrganBlock> GILLS; // line 312

  public static final DeferredBlock<TentacleBlock> TENTACLE; // line 321

  public static final java.util.List<DeferredBlock<NerveControlBlock>> CONTROLS; // line 330

  public static final DeferredItem<DeveloperTestTool> DEVELOPER_TEST_TOOL; // line 346

  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH; // line 349

  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES; // line 352

  public static final DeferredItem<BlockItem> CORE_ITEM; // line 355

  public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> DOOR_ITEM; // line 357

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LivingCoreBlockEntity>> CORE_ENTITY; // line 371

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StomachBlockEntity>> STOMACH_ENTITY; // line 380

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedingMouthBlockEntity>> FEEDING_MOUTH_ENTITY; // line 387

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentacleBlockEntity>> TENTACLE_ENTITY; // line 394

  public Create_bioships(IEventBus bus); // line 424

```


#### LivingCoreBlock

Source: `src/main/java/org/Azkerash/create_bioships/LivingCoreBlock.java` (182 lines).

```java
public class LivingCoreBlock extends BaseEntityBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE; // line 16

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty BLINK; // line 18

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty RELOCATING; // line 20

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty CONCEALED; // line 22

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 24

  public static final MapCodec<LivingCoreBlock> CODEC; // line 26

  public LivingCoreBlock(Properties properties); // line 27

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder< net.minecraft.world.level.block.Block, BlockState> builder); // line 39

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 47

  public BlockState getStateForPlacement( net.minecraft.world.item.context.BlockPlaceContext context); // line 52

  public void setPlacedBy( Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack); // line 67

  protected void onPlace( BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved); // line 82

  protected void tick( BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random); // line 89

  protected void onRemove( BlockState state, Level level, BlockPos pos, BlockState next, boolean moved); // line 98

  public void beforeMove( net.minecraft.server.level.ServerLevel source, net.minecraft.server.level.ServerLevel destination, BlockState state, BlockPos from, BlockPos to); // line 107

  public void afterMove( net.minecraft.server.level.ServerLevel source, net.minecraft.server.level.ServerLevel destination, BlockState state, BlockPos from, BlockPos to); // line 118

  protected RenderShape getRenderShape(BlockState state); // line 129

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 134

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 139

  protected ItemInteractionResult useItemOn( ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit); // line 147

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 161

  public void attack(BlockState state, Level level, BlockPos pos, Player player); // line 169

  protected java.util.List<ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 175

```


### Creature lifecycle coordinator [active]

Owns subsystem instances and saved state; schedules game ticks and publishes physics intent.

```text
[core]
  LivingCoreBlockEntity
```


#### LivingCoreBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/LivingCoreBlockEntity.java` (1338 lines).

```java
public class LivingCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, org.Azkerash.create_bioships.amoeba.components.AmoebaComponentProvider

  private final org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost componentHost; // line 42

  public BioshipChunks chunkLoading(); // line 44

  public org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost amoebaComponents(); // line 48

  private static final TagKey<Item> MEAT; // line 52

  private static final TagKey<Item> LEVITITE; // line 57

  private BlockPos preferredMouth; // line 61

  public BlockPos preferredMouth(); // line 62

  public void preferredMouth(BlockPos pos); // line 66

  private ComponentTransport.Move organMove; // line 71

  private final BioshipAssimilation assimilation; // line 73

  public BioshipAssimilation assimilation(); // line 74

  public ComponentTransport.Move organMove(); // line 78

  void workingOrganMove(ComponentTransport.Move move); // line 82

  public void organMove(ComponentTransport.Move move); // line 86

  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(); // line 93

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 98

  private final BioshipNeeds needs; // line 103

  private final BioshipLootMemory loot; // line 105

  BioshipLootMemory loot(); // line 106

  private boolean awakened, resting; // line 110

  private java.util.UUID captain; // line 112

  private final BioshipAccess access; // line 113

  public java.util.UUID creator(); // line 114

  public void claimCreator(java.util.UUID owner); // line 118

  public BioshipAccess access(); // line 125

  private int bodyBlocks; // line 129

  private int stage, growthIndex, metabolismTicks; // line 131

  private final CoreMotion coreMotion; // line 132

  public CoreMotion coreMotion(); // line 133

  public BlockPos structuralOrigin(); // line 137

  private boolean hullBudgetAvailable(); // line 141

  boolean growthStockReady(); // line 154

  boolean growthSiteReady(); // line 158

  public double coreHeightOffset(); // line 165

  private final BioshipVision vision; // line 174

  public BioshipVision vision(); // line 176

  BlockPos supplyTarget; // line 180

  private final BioshipRescue rescue; // line 182

  public BioshipRescue rescue(); // line 183

  private final BioshipAutonomy autonomy; // line 187

  private final PlaceObservations placeObservations; // line 189

  private final org.Azkerash.create_bioships.creature.memory.PlaceMemory places; // line 190

  public org.Azkerash.create_bioships.creature.memory.PlaceMemory placeMemory(); // line 192

  public BioshipAutonomy autonomy(); // line 196

  int trustLevel(); // line 200

  private final BioshipDefense defense; // line 204

  public Vec3 attackTip(); // line 206

  public boolean attackSweeping(); // line 210

  BioshipDefense defense(); // line 214

  public boolean hatchling(); // line 218

  private int bodyLayoutVersion; // line 222

  public int layoutVersion(); // line 224

  public java.util.List<DinghyGrowth.Part> plannedBody(); // line 228

  private String growthObstacle; // line 232

  private long lastServerTick; // line 234

  private final BioshipAquatic aquatic; // line 235

  private final BioshipEngineRoom engineRoom; // line 236

  public BioshipEngineRoom engineRoom(); // line 237

  public BioshipAquatic aquatic(); // line 241

  private double hoverHeight; // line 245

  private volatile FlightIntent flight; // line 247

  void inheritFlight(LivingCoreBlockEntity previous); // line 248

  private record FlightIntent( boolean lift, Vec3 velocity, double turn, double supportHeight, Vec3 stanceUp); // line 252

  public LivingCoreBlockEntity(BlockPos pos, BlockState state); // line 259

  public ServerSubLevel ship(); // line 263

  boolean primary(ServerSubLevel ship); // line 271

  public void interact(Player player, ItemStack stack); // line 279

  private int goggleFlesh, goggleBone, goggleFiber; // line 317

  public java.util.List<Component> goggleTooltip(); // line 319

  public boolean feedAtMouth(Player player, ItemStack stack); // line 415

  public boolean feedAtMouth(BlockPos mouth, Player player, ItemStack stack); // line 419

  public static boolean isMeat(ItemStack stack); // line 451

  final BioshipTrustFeedback trustFeedback; // line 455

  int poisonSeconds(); // line 457

  public void digestMeal(ItemStack stack); // line 461

  public boolean acceptAmalgam(ItemStack stack); // line 480

  private final BioshipScavenging scavenging; // line 488

  public BioshipScavenging scavenging(); // line 490

  private final BioshipCrawl crawl; // line 494

  public BioshipCrawl crawl(); // line 496

  private final BioshipSurvivalAdaptations survival; // line 500

  public BioshipSurvivalAdaptations survival(); // line 502

  private final BioshipAdaptation adaptation; // line 506

  public BioshipAdaptation adaptation(); // line 508

  private final BioshipBody body; // line 512

  private final BioshipBlueprint blueprint; // line 514

  public BioshipBody body(); // line 515

  public BioshipBlueprint blueprint(); // line 519

  public BlockPos layoutPos(BlockPos offset); // line 523

  final BioshipChunks chunkLoading; // line 528

  private final BioshipBrain brain; // line 530

  public BioshipBrain brain(); // line 531

  int satiety(); // line 535

  boolean frameFunded(); // line 539

  boolean buildingHull(); // line 543

  private boolean framePaid, larynxDeveloped; // line 547

  public boolean wantsStoredFood(); // line 549

  private boolean prepareFrame(); // line 558

  private void developLarynx(ServerSubLevel ship); // line 591

  public int incorporateBiomass(int amount); // line 615

  public int missingBiomass(); // line 621

  private static void message(Player player, String key, Object... args); // line 625

  public void mistreat(); // line 629

  private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos); // line 637

  private void recordGrowthObstacle(ServerSubLevel ship, BlockPos offset); // line 646

  private boolean roomToGrow(ServerSubLevel ship); // line 664

  private net.minecraft.server.level.ServerLevel transferLevel; // line 685

  private BlockPos transferTarget; // line 687

  void recordTransfer(net.minecraft.server.level.ServerLevel destination, BlockPos pos); // line 688

  public boolean hasTransferReceipt(); // line 693

  public boolean transferred(); // line 697

  void coreRemoved(); // line 705

  public void setRemoved(); // line 715

  public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core); // line 722

  private void updateCabinLights(); // line 839

  private void growBody(); // line 850

  public void sable$tick(ServerSubLevel ship); // line 926

  private void serverTick(); // line 931

  private void incorporateAmalgam(); // line 1038

  public boolean canFly(); // line 1047

  private void updateEye(boolean active); // line 1056

  public boolean isMature(); // line 1069

  public boolean resting(); // line 1073

  public boolean aggressive(); // line 1077

  public boolean isCaptain(net.minecraft.world.entity.Entity entity); // line 1081

  public boolean huntingAllowed(); // line 1085

  public int foodLevel(); // line 1093

  public boolean starving(); // line 1097

  public void adoptLarynx(); // line 1101

  public boolean wantsToHunt(); // line 1106

  public boolean hungry(); // line 1110

  public boolean hasMouth(); // line 1114

  public BlockPos mouthBlock(); // line 1118

  public Vec3 mouthPosition(ServerSubLevel ship); // line 1140

  public int swimmingFlagella(); // line 1152

  Vec3 gatheringGoal(); // line 1160

  static boolean droppedSupply(ItemStack stack); // line 1165

  public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt); // line 1173

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 1227

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 1279

```


### World loading [active]

View and simulation leases follow the creature; tickets release on removal or death.

```text
[chunks]
  BioshipChunks
```


#### BioshipChunks

Source: `src/main/java/org/Azkerash/create_bioships/BioshipChunks.java` (114 lines).

```java
public final class BioshipChunks

  private static final Set<BioshipChunks> ACTIVE; // line 22

  private static final TicketType<UUID> VIEW; // line 23

  private static final TicketType<UUID> SIMULATION; // line 25

  private record Lease(ServerLevel level, ChunkPos center, UUID owner, int view, int simulation); // line 27

  private Lease lease; // line 29

  private long refreshed; // line 31

  public void update(LivingCoreBlockEntity core); // line 32

  public void release(); // line 62

  public static void keepLivingSimulation( net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event); // line 81

  public static void stopping(ServerStoppingEvent event); // line 91

  public boolean active(); // line 99

  public int viewDistance(); // line 103

  public int simulationDistance(); // line 108

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

  public static Map<BlockPos, BlockState> cells(SubLevel ship); // line 33

  static List<BlockPos> positions(SubLevel ship); // line 39

  static int count(SubLevel ship); // line 45

```


#### BioshipIntegration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipIntegration.java` (290 lines).

```java
public final class BioshipIntegration

  private static final String BOUND; // line 28

  private static final TagKey<net.minecraft.world.level.block.Block> MACHINES; // line 29

  private static final TagKey<net.minecraft.world.level.block.Block> FLESH; // line 33

  private static final TagKey<Item> MATERIAL; // line 37

  public static TagKey<Item> constructionMaterial(); // line 41

  public static void bind(ServerSubLevel ship); // line 45

  public static boolean bound(SubLevel ship); // line 55

  public static ServerSubLevel containing(Level level, BlockPos pos); // line 63

  public static void awaken(ServerLevel level, BlockPos pos); // line 70

  public static boolean isMaw(BlockState state); // line 79

  public static boolean isIntegratedMachine(Level level, BlockPos pos); // line 86

  public static List<net.minecraft.world.level.block.entity.BlockEntity> blockEntities( ServerSubLevel ship); // line 95

  public static int countBlocks(ServerSubLevel ship); // line 103

  static boolean knownReachableSource(LivingCoreBlockEntity core, BlockPos source); // line 107

  public static void assimilate(LivingCoreBlockEntity core, ServerSubLevel ship); // line 122

  static void collect(LivingCoreBlockEntity core); // line 128

  public static boolean exposedAndVisible(ServerLevel level, BlockPos eye, BlockPos source); // line 134

  public static boolean growthSpace(Level level, BlockPos pos); // line 148

  public static boolean aquaticGrowthSpace(Level level, BlockPos pos); // line 153

  public static void clearGrowthPlant(Level level, BlockPos pos); // line 160

  public static void finishAssimilation(LivingCoreBlockEntity core, BlockPos target); // line 165

  static void convertFirstBioForge(LivingCoreBlockEntity core, BlockPos pos); // line 181

  static void orientMouth(LivingCoreBlockEntity core); // line 206

  public static void relocateMouth(LivingCoreBlockEntity core, ServerSubLevel ship); // line 211

  public static void routeBioshipWrench(PlayerInteractEvent.RightClickBlock event); // line 259

  public static void feedMaw(PlayerInteractEvent.RightClickBlock event); // line 267

```


#### BioshipReach

Source: `src/main/java/org/Azkerash/create_bioships/BioshipReach.java` (74 lines).

```java
final class BioshipReach

  static Vec3 anchor(LivingCoreBlockEntity core, Vec3 target); // line 8

  static boolean at(LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget); // line 24

  static boolean at( LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget, double reach); // line 28

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget, double reach); // line 56

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget); // line 65

```


#### BodyTopology

Source: `src/main/java/org/Azkerash/create_bioships/BodyTopology.java` (63 lines).

```java
public final class BodyTopology

  public static Set<BlockPos> structuralCells(Map<BlockPos, BlockState> cells); // line 9

  public static int components(Set<BlockPos> cells); // line 19

  static boolean canRemove(Set<BlockPos> cells, BlockPos source); // line 39

  static boolean canMove( Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource); // line 46

  static boolean canCommit( Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource); // line 54

```


#### BioshipCoreLifecycle

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCoreLifecycle.java` (62 lines).

```java
final class BioshipCoreLifecycle

  private record Removal( LivingCoreBlockEntity source, ServerSubLevel owner, ServerLevel level, BlockPos pos); // line 16

  private static final Map<LivingCoreBlockEntity, Removal> pending; // line 18

  static void removed(LivingCoreBlockEntity core); // line 22

  public static void tick(ServerTickEvent.Post event); // line 29

  public static void stop(ServerStoppedEvent event); // line 54

  private BioshipCoreLifecycle(); // line 59

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVision.java` (641 lines).

```java
public final class BioshipVision

  static final MaterialCost EYE_COST; // line 14

  private List<BlockPos> eyes; // line 15

  private long scanned; // line 16

  private void scan(LivingCoreBlockEntity core); // line 17

  public record View(Vec3 origin, Vec3 forward, double range); // line 41

  public int maximumDistance(LivingCoreBlockEntity core); // line 49

  static int distanceLimit(int serverChunks, net.minecraft.server.level.ServerPlayer observer); // line 65

  public boolean inView(LivingCoreBlockEntity core, View view, Vec3 point); // line 71

  static boolean clearMaterial( net.minecraft.world.level.block.state.BlockState state, Vec3 direction); // line 86

  public void invalidate(); // line 108

  private record Sighting( net.minecraft.world.level.block.state.BlockState state, long seen, boolean full); // line 112

  private final Map<BlockPos, Sighting> remembered; // line 115

  private final TargetMemory<java.util.UUID> entityFreshness; // line 117

  private net.minecraft.world.entity.LivingEntity trackedEntity; // line 118

  private Vec3 trackedPoint; // line 119

  void rememberEntity(net.minecraft.world.entity.LivingEntity entity); // line 120

  net.minecraft.world.entity.LivingEntity rememberedEntity(LivingCoreBlockEntity core); // line 127

  Vec3 knownPosition(net.minecraft.world.entity.LivingEntity entity); // line 139

  Vec3 focus(LivingCoreBlockEntity core); // line 145

  private final TargetMemory<BlockPos> sourceFreshness; // line 157

  public long unseenNanos(BlockPos pos); // line 159

  public void forget(LivingCoreBlockEntity core, BlockPos pos); // line 163

  java.util.Set<BlockPos> rememberedSources(); // line 169

  public net.minecraft.world.level.block.state.BlockState rememberedState(BlockPos pos); // line 173

  boolean rememberedFull(BlockPos pos); // line 178

  net.minecraft.world.level.block.state.BlockState sourceState( LivingCoreBlockEntity core, BlockPos source); // line 182

  private int surveyed; // line 216

  public List<View> views(LivingCoreBlockEntity core); // line 218

  boolean knows(LivingCoreBlockEntity core, BlockPos source); // line 247

  boolean surveyed(LivingCoreBlockEntity core); // line 251

  void look(LivingCoreBlockEntity core, Direction face); // line 255

  private void turn(LivingCoreBlockEntity core); // line 263

  public static boolean exposedTube(net.minecraft.world.level.block.state.BlockState state); // line 280

  public static boolean ray(LivingCoreBlockEntity core, Vec3 from, Vec3 to); // line 284

  static boolean rayAtCore(LivingCoreBlockEntity core, Vec3 from, Vec3 to, BlockPos origin); // line 288

  public boolean visible(LivingCoreBlockEntity core, Vec3 point); // line 325

  public static boolean openToNature(net.minecraft.world.level.Level level, BlockPos source); // line 332

  boolean exposed(LivingCoreBlockEntity core, BlockPos source); // line 348

  void observe(LivingCoreBlockEntity core); // line 352

  boolean externalEyesPreferred(LivingCoreBlockEntity core); // line 356

  void tick(LivingCoreBlockEntity core); // line 368

  net.minecraft.nbt.CompoundTag save(); // line 378

  void load( net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries); // line 396

  boolean reabsorb(LivingCoreBlockEntity core); // line 423

  boolean trackFocus(LivingCoreBlockEntity core); // line 477

  void growEyes(LivingCoreBlockEntity core); // line 554

```


#### TargetMemory

Source: `src/main/java/org/Azkerash/create_bioships/TargetMemory.java` (57 lines).

```java
public final class TargetMemory<K>

  public static final long TIMEOUT; // line 8

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipPriorities.java` (139 lines).

```java
final class BioshipPriorities

  record Objective(BlockPos pos, int rank); // line 14

  static int rank(LivingCoreBlockEntity core, BlockState state, BlockPos pos); // line 15

  private static int rank( LivingCoreBlockEntity core, BlockState state, BlockPos pos, boolean observed); // line 19

  private record Sources(long at, BlockPos center, int range, java.util.List<BlockPos> positions); // line 62

  private static final java.util.Map<LivingCoreBlockEntity, Sources> SOURCE_CACHE; // line 64

  static boolean supply(BlockState state); // line 67

  private static java.util.List<BlockPos> sources( LivingCoreBlockEntity core, ServerLevel level, Vec3 here, int range); // line 72

  static java.util.List<Objective> worldObjectives(LivingCoreBlockEntity core); // line 111

  static Objective bestWorld(LivingCoreBlockEntity core); // line 134

```


#### BioshipLootMemory

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLootMemory.java` (78 lines).

```java
final class BioshipLootMemory

  record Target(UUID id, Vec3 position, ItemEntity item); // line 12

  private record Known(Vec3 position, long at); // line 13

  private final Map<UUID, Known> drops; // line 15

  void remember(ItemEntity item, long at); // line 17

  boolean knows(Vec3 point); // line 22

  List<Target> targets(LivingCoreBlockEntity core); // line 27

  CompoundTag save(); // line 50

  void load(CompoundTag data); // line 65

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

Source: `src/main/java/org/Azkerash/create_bioships/SurfaceEyeBlock.java` (115 lines).

```java
public final class SurfaceEyeBlock extends DirectionalBlock

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty WINDOW_MOUNTED; // line 13

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty WINDOW_INTERNAL; // line 16

  public static final net.minecraft.world.level.block.state.properties.BooleanProperty AUTOMATIC; // line 20

  public static final MapCodec<SurfaceEyeBlock> CODEC; // line 22

  public SurfaceEyeBlock(Properties properties); // line 23

  protected MapCodec<? extends DirectionalBlock> codec(); // line 34

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 39

  public static boolean canHostEye(BlockState host, BlockPos pos, Direction face); // line 44

  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos); // line 54

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 61

  static BlockState mounted(BlockState state, LevelAccessor level, BlockPos pos); // line 68

  protected BlockState updateShape( BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos); // line 79

  protected void onPlace( BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved); // line 90

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 100

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
public final class BioshipNutrition

  static final int PRESSING_FOOD; // line 7

  public static boolean critical(LivingCoreBlockEntity core); // line 8

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
public final class BioshipEnergy

  private record Snapshot(long tick, int units); // line 7

  private static final Map<LivingCoreBlockEntity, Snapshot> CACHE; // line 8

  static int demand(LivingCoreBlockEntity core); // line 11

  public static int units(LivingCoreBlockEntity core); // line 15

```


#### BlockEnergyTable

Source: `src/main/java/org/Azkerash/create_bioships/BlockEnergyTable.java` (27 lines).

```java
public final class BlockEnergyTable

  static final Map<String, Integer> COSTS; // line 6

  static int units(String id); // line 14

  public static int demand(int units); // line 18

  public static int pairs(int demand); // line 22

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBrain.java` (686 lines).

```java
public final class BioshipBrain

  public enum Action; // line 22

  public record Decision( Action action, Vec3 point, BlockPos block, LivingEntity entity, ItemEntity item, BehaviorPriority.Offer offer, String reason, boolean recycle); // line 58

  private record Needs( boolean digestive, boolean hungry, int food, int flesh, int bone, int fiber, boolean growth, boolean criticalFood); // line 83

  private String previous, reason; // line 97

  private Action action; // line 99

  private boolean growthBlocked; // line 100

  private Decision lastDecision; // line 101

  private final GoalQueue<Decision> goals; // line 102

  public java.util.List<Decision> queuedGoals(); // line 103

  private void refreshGoals(List<Decision> proposals); // line 107

  private net.minecraft.nbt.ListTag debugPriorities; // line 111

  private int debugOfferCount; // line 113

  public net.minecraft.nbt.ListTag priorities(); // line 114

  public int offerCount(); // line 118

  private void rankForDebug(List<Decision> offers); // line 122

  public Decision lastDecision(); // line 141

  public Action action(); // line 145

  public String reason(); // line 149

  void growthBlocked(boolean blocked); // line 153

  private Needs needs(LivingCoreBlockEntity core); // line 157

  boolean shouldHunt(LivingCoreBlockEntity core); // line 243

  private Decision fixed(Action next, String why); // line 260

  private Decision remember(Decision selected); // line 272

  private Decision offer( Action next, Vec3 point, BlockPos block, LivingEntity entity, ItemEntity item, BehaviorPriority.Band band, int urgency, int value, double effort, String why); // line 286

  private Decision chooseOffers(List<Decision> offers); // line 313

  private double effort(LivingCoreBlockEntity core, Vec3 point); // line 318

  boolean interruptsForFood(LivingCoreBlockEntity core); // line 322

  private int materialUrgency(Needs n, BlockState state, boolean wreck); // line 326

  private int itemUrgency(Needs n, ItemStack stack); // line 338

  private Decision source( LivingCoreBlockEntity core, Needs n, BlockPos pos, Vec3 point, BlockState state, int rank, boolean wreck); // line 353

  BioshipPriorities.Objective bestWorld(LivingCoreBlockEntity core); // line 418

  Decision choose(LivingCoreBlockEntity core); // line 437

  boolean allowsConfiguration(LivingCoreBlockEntity core); // line 590

  public MaterialCost materialNeeds(LivingCoreBlockEntity core); // line 594

  boolean needsMaterials(LivingCoreBlockEntity core); // line 599

  public LivingEntity attackTarget(LivingCoreBlockEntity core); // line 603

  Vec3 groundGoal(LivingCoreBlockEntity core); // line 611

  Vec3 flightVelocity(LivingCoreBlockEntity core); // line 649

  CompoundTag save(); // line 664

  void load(CompoundTag tag); // line 672

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
  creature.memory.PlaceMemory
  creature.memory.PlaceMemoryCodec
  PlaceObservations
  EncounterObservations
  ThreatObservations
  RetreatPlanning
  RetreatCombat
  creature.survival.ThreatProfile
  creature.survival.EscapeScoring
```


#### BioshipAutonomy

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAutonomy.java` (231 lines).

```java
public final class BioshipAutonomy

  private Vec3 wander, danger; // line 14

  private final ThreatObservations threats; // line 15

  private final RetreatPlanning escape; // line 16

  private java.util.List<org.Azkerash.create_bioships.creature.survival.EscapeScoring.Threat> observed; // line 17

  private long nextWander, panicUntil, evaluated, evaluated; // line 19

  private boolean fleeing; // line 20

  private long creeperUntil; // line 21

  public boolean creeperPanic(); // line 22

  private long coreTime; // line 26

  private java.util.UUID pilot; // line 28

  private long commandUntil; // line 29

  public void command(LivingCoreBlockEntity core, net.minecraft.server.level.ServerPlayer player); // line 30

  public boolean trustedPilot(LivingCoreBlockEntity core); // line 37

  public boolean fleeing(); // line 74

  void assess(LivingCoreBlockEntity core); // line 78

  void activity(LivingCoreBlockEntity core); // line 120

  Vec3 groundEscape(LivingCoreBlockEntity core); // line 125

  Vec3 waterEscape(LivingCoreBlockEntity core); // line 131

  Vec3 groundWander(LivingCoreBlockEntity core); // line 137

  Vec3 flight(LivingCoreBlockEntity core, boolean escape); // line 165

  CompoundTag save(); // line 201

  void load(CompoundTag tag); // line 214

```


#### creature.memory.PlaceMemory

Source: `src/main/java/org/Azkerash/create_bioships/creature/memory/PlaceMemory.java` (189 lines).

```java
public final class PlaceMemory

  public static final int DEFAULT_CAPACITY; // line 6

  public static final long PREY_FRESH_TICKS, DANGER_FRESH_TICKS; // line 7

  public enum Feature; // line 8

  public record Point(double x, double y, double z); // line 19

  private record Key(String dimension, int x, int y, int z); // line 30

  public record Entry( String dimension, Point point, String biome, Set<Feature> features, long terrainSeen, long updatedAt, Map<String, Long> prey, Map<String, Long> danger); // line 40

  private final int capacity; // line 79

  private final Map<Key, Entry> places; // line 81

  public PlaceMemory(); // line 82

  public PlaceMemory(int capacity); // line 86

  public int size(); // line 92

  public List<Entry> snapshot(); // line 96

  public List<Entry> inDimension(String dimension); // line 100

  public void terrain( String dimension, Point point, String biome, Set<Feature> features, long now); // line 104

  public void prey(String dimension, Point point, String biome, String species, long now); // line 121

  public void danger(String dimension, Point point, String biome, String kind, long now); // line 125

  private void evidence( String dimension, Point point, String biome, String id, long now, boolean prey); // line 129

  public void restore(Collection<Entry> entries); // line 150

  private void put(Key key, Entry entry); // line 155

  private static Map<String, Long> bounded(Map<String, Long> source, int limit); // line 166

  private static double freshness(long now, long seen, long lifetime); // line 184

```


#### creature.memory.PlaceMemoryCodec

Source: `src/main/java/org/Azkerash/create_bioships/creature/memory/PlaceMemoryCodec.java` (86 lines).

```java
public final class PlaceMemoryCodec

  public static CompoundTag save(PlaceMemory memory); // line 9

  private static ListTag evidence(Map<String, Long> sightings); // line 31

  private static Map<String, Long> evidence(ListTag list, int limit); // line 43

  public static void load(PlaceMemory memory, CompoundTag tag); // line 52

  private PlaceMemoryCodec(); // line 83

```


#### PlaceObservations

Source: `src/main/java/org/Azkerash/create_bioships/PlaceObservations.java` (97 lines).

```java
final class PlaceObservations

  private long next; // line 14

  private int spoke; // line 15

  private final EncounterObservations encounters; // line 16

  void tick(LivingCoreBlockEntity core); // line 17

  static void terrain(LivingCoreBlockEntity core, Vec3 point, boolean occupied); // line 42

  static String dimension(LivingCoreBlockEntity core); // line 80

  static String biome(LivingCoreBlockEntity core, BlockPos pos); // line 84

  static PlaceMemory.Point point(Vec3 p); // line 92

```


#### EncounterObservations

Source: `src/main/java/org/Azkerash/create_bioships/EncounterObservations.java` (45 lines).

```java
final class EncounterObservations

  private int cursor; // line 13

  void observe(LivingCoreBlockEntity core, Vec3 here); // line 14

```


#### ThreatObservations

Source: `src/main/java/org/Azkerash/create_bioships/ThreatObservations.java` (88 lines).

```java
final class ThreatObservations

  private record Seen(Threat threat, long until); // line 14

  private final Map<UUID, Seen> seen; // line 15

  private long next; // line 17

  private Vec3 explosive; // line 18

  static ThreatProfile profile(Entity entity); // line 19

  List<Threat> update(LivingCoreBlockEntity core, Vec3 here); // line 44

  void clear(); // line 77

  Vec3 explosive(); // line 83

```


#### RetreatPlanning

Source: `src/main/java/org/Azkerash/create_bioships/RetreatPlanning.java` (221 lines).

```java
final class RetreatPlanning

  private Vec3 goal; // line 18

  private long retry; // line 19

  private boolean waterMode; // line 20

  private Vec3 lastDanger; // line 21

  private record Ranked(Vec3 point, double score); // line 22

  void clear(); // line 24

  Vec3 choose(LivingCoreBlockEntity core, Vec3 danger, List<Threat> observed, boolean water); // line 29

  private static void add( LivingCoreBlockEntity core, Vec3 here, Vec3 requested, PlaceMemory.Entry memory, List<Threat> threats, List<PlaceMemory.Entry> places, boolean water, Map<BlockPos, Ranked> results); // line 123

  static boolean blocked(LivingCoreBlockEntity core, Vec3 from, Vec3 to); // line 164

  static boolean safePath(LivingCoreBlockEntity core, Vec3 from, List<Vec3> path, boolean water); // line 177

  static boolean safe(LivingCoreBlockEntity core, Vec3 point, boolean water); // line 191

  private static boolean hazardFree(LivingCoreBlockEntity core, Vec3 point); // line 198

  static Vec3 vector(PlaceMemory.Point p); // line 216

```


#### RetreatCombat

Source: `src/main/java/org/Azkerash/create_bioships/RetreatCombat.java` (39 lines).

```java
final class RetreatCombat

  private RetreatCombat(); // line 12

  static LivingEntity select(LivingCoreBlockEntity core, LivingEntity current); // line 13

  private static boolean eligible(LivingCoreBlockEntity core, LivingEntity entity); // line 25

```


#### creature.survival.ThreatProfile

Source: `src/main/java/org/Azkerash/create_bioships/creature/survival/ThreatProfile.java` (13 lines).

```java
public record ThreatProfile( boolean explosive, boolean ranged, boolean climbing, boolean flying, boolean aquatic, boolean jumping)

  public static final ThreatProfile GROUND; // line 10

```


#### creature.survival.EscapeScoring

Source: `src/main/java/org/Azkerash/create_bioships/creature/survival/EscapeScoring.java` (55 lines).

```java
public final class EscapeScoring

  private EscapeScoring(); // line 9

  public record Threat(Point position, ThreatProfile profile); // line 10

  public record Candidate( Point position, Set<Feature> features, double confidence, boolean breaksSight, boolean safe, double danger, double prey); // line 12

  public static double score(Point origin, Candidate candidate, List<Threat> threats); // line 25

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCrawl.java` (610 lines).

```java
public final class BioshipCrawl

  private Vec3 goal; // line 13

  private Vec3 lastStepPosition; // line 14

  private long gaitAt; // line 15

  private double gaitSpeed, gaitPhase; // line 16

  public boolean animating(); // line 17

  public double gaitSpeed(); // line 21

  public double gaitPhase(); // line 25

  public long gaitAt(); // line 29

  private void measureGait(LivingCoreBlockEntity core); // line 33

  private boolean walking; // line 47

  private String mode; // line 49

  public String mode(); // line 50

  public boolean running(); // line 54

  private final BioshipLegAnchors legAnchors; // line 58

  public BioshipLegAnchors legAnchors(); // line 60

  private int legTier; // line 64

  private double measuredLoad; // line 66

  public double measuredLoad(); // line 67

  public int legTier(); // line 71

  public int extraLift(); // line 75

  public int footingReach(); // line 79

  public double legThickness(); // line 83

  public double loadCapacity(); // line 87

  public static double capacityFor(int tier); // line 91

  int hullTier(LivingCoreBlockEntity core); // line 95

  public double blockLoad( LivingCoreBlockEntity core, BlockPos pos, net.minecraft.world.level.block.state.BlockState state); // line 109

  public double carriedLoad(LivingCoreBlockEntity core); // line 121

  boolean canEnhance(LivingCoreBlockEntity core); // line 131

  private void payEnhancement(LivingCoreBlockEntity core); // line 141

  boolean enhance(LivingCoreBlockEntity core); // line 151

  private boolean supportLoad(LivingCoreBlockEntity core); // line 158

  private int cursor; // line 165

  private final GroundRoute route; // line 167

  private String selection; // line 168

  private void retireChangedSelection(LivingCoreBlockEntity core); // line 169

  private final Map<BlockPos, Long> unreachable; // line 175

  public net.minecraft.nbt.CompoundTag navigationDebug(long now); // line 177

  public java.util.List<Vec3> path(); // line 186

  boolean reachableGoal(BlockPos pos); // line 190

  private long currentTime; // line 194

  void lowerHeight(double height); // line 196

  void stepHeight(double height); // line 200

  private boolean gathering; // line 204

  private Direction wallFacing; // line 206

  private double wallDistance; // line 207

  public Direction wallFacing(); // line 208

  public double wallDistance(); // line 212

  void clingHeight(double height); // line 216

  private Vec3 reach; // line 220

  public Vec3 reach(); // line 222

  void reach(Vec3 point); // line 226

  private double supportHeight; // line 230

  private Vec3 stanceUp; // line 232

  public boolean bracing(); // line 233

  public double supportHeight(); // line 237

  public Vec3 stanceUp(); // line 241

  public boolean walking(); // line 245

  public boolean gathering(); // line 249

  void stop(); // line 253

  Vec3 steeringGoal(); // line 262

  public Vec3 goal(); // line 267

  void tick(LivingCoreBlockEntity core, boolean blocked); // line 271

  boolean clear(LivingCoreBlockEntity core, Vec3 candidate); // line 345

  Vec3 velocity(LivingCoreBlockEntity core); // line 383

  private BlockPos ground(ServerLevel level, BlockPos point); // line 419

  private BlockPos ground(ServerLevel level, BlockPos point, double highestTop); // line 423

  private void braceGrip(LivingCoreBlockEntity core, Vec3 here, GroundRoute.Grip grip); // line 434

  private void brace(LivingCoreBlockEntity core); // line 450

  CompoundTag save(); // line 543

  void load(CompoundTag t); // line 575

```


#### GroundRoute

Source: `src/main/java/org/Azkerash/create_bioships/GroundRoute.java` (490 lines).

```java
final class GroundRoute

  private List<Vec3> path; // line 11

  private Vec3 destination; // line 12

  private int cursor; // line 13

  private long retryAfter; // line 14

  private boolean failed; // line 15

  private boolean complete; // line 16

  private String debugStatus; // line 17

  private long plannedAt; // line 18

  net.minecraft.nbt.CompoundTag debug(long now); // line 19

  void clear(); // line 30

  boolean complete(); // line 41

  boolean failed(); // line 45

  List<Vec3> remaining(); // line 49

  List<Vec3> points(); // line 53

  record Grip(Direction direction, double distance); // line 57

  static Grip grip(LivingCoreBlockEntity core, Vec3 point); // line 59

  private List<Vec3> offsets(LivingCoreBlockEntity core); // line 63

  private boolean fits(LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets); // line 73

  private boolean fits( LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets, double clearance); // line 77

  private Vec3 standing( LivingCoreBlockEntity core, int x, int z, int floorTop, List<Vec3> offsets, double currentHeight); // line 94

  private boolean firstStepClear(LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts); // line 126

  static boolean turnClear(LivingCoreBlockEntity core, double angle); // line 138

  static Vec3 groundedDestination(LivingCoreBlockEntity core, Vec3 requested); // line 154

  static Vec3 escapeDestination(LivingCoreBlockEntity core, Vec3 requested); // line 175

  static boolean flightClear(LivingCoreBlockEntity core, Vec3 target); // line 196

  private boolean supportedAt(LivingCoreBlockEntity core, Vec3 point); // line 206

  private boolean lineClear( LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts, double clearance); // line 221

  private List<Vec3> simplify( LivingCoreBlockEntity core, Vec3 from, List<Vec3> original, List<Vec3> parts); // line 232

  boolean plan(LivingCoreBlockEntity core, Vec3 goal); // line 262

  Vec3 next(LivingCoreBlockEntity core, Vec3 goal); // line 418

```


#### HatchlingFront

Source: `src/main/java/org/Azkerash/create_bioships/HatchlingFront.java` (98 lines).

```java
final class HatchlingFront

  static BlockPos port(LivingCoreBlockEntity core, BlockPos source); // line 11

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos source, BlockPos target); // line 43

  static boolean canArrange(LivingCoreBlockEntity core); // line 61

  static double turn(LivingCoreBlockEntity core, Vec3 goal); // line 84

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipSupport.java` (357 lines).

```java
public final class BioshipSupport

  public record Leg(Vec3 root, Vec3 foot, Direction face, boolean bootstrap, boolean swimming); // line 15

  private static final List<java.util.function.Function<LivingCoreBlockEntity, List<Leg>>> LEG_PROVIDERS; // line 48

  public static void registerLegProvider( java.util.function.Function<LivingCoreBlockEntity, List<Leg>> provider); // line 51

  public record Layout(Vec3 center, List<Leg> legs, double margin); // line 58

  private record Cached(long time, Layout layout); // line 64

  private static final Map<LivingCoreBlockEntity, Cached> CACHE; // line 66

  private static Map<BlockPos, BlockState> cells(LivingCoreBlockEntity core); // line 69

  public static Layout layout(LivingCoreBlockEntity core); // line 81

  static Layout layout(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 90

  static boolean safeAddition(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 229

  public static boolean safeMove( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh); // line 235

  public static boolean safeGroupMove( LivingCoreBlockEntity core, List<BlockPos> sources, BlockPos delta); // line 240

  public static boolean safeMerge( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh, BlockState result); // line 259

  static Layout movedLayout( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh); // line 275

  static boolean safeCoreEntry(LivingCoreBlockEntity core, BlockPos target); // line 280

  private static Layout movedLayout( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean restoreFlesh, boolean swapCoreHost); // line 284

  private static long invalidMounts(Map<BlockPos, BlockState> cells); // line 329

  static boolean containsProjection(Vec3 point, Vec3[] contacts); // line 338

```


#### BioshipRighting

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRighting.java` (66 lines).

```java
final class BioshipRighting

  record Support(double height); // line 9

  static Support support(LivingCoreBlockEntity core); // line 10

  static Vec3 torque(Vec3 up, Vec3 target, Vec3 right); // line 59

```


#### BioshipLegAnchors

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegAnchors.java` (157 lines).

```java
public final class BioshipLegAnchors

  public static final double TIP_RADIUS; // line 11

  private static final int STEP_TICKS; // line 12

  private final Vec3[] from, to, up; // line 13

  private final long[] started; // line 14

  public Vec3 normal(int leg); // line 15

  public AABB contactBox(int leg); // line 19

  public Vec3 foot(int leg, double time); // line 23

  void tick(LivingCoreBlockEntity core); // line 29

  Vec3 supportContact(ServerLevel level, int leg, long now); // line 109

  private static boolean touching(ServerLevel level, AABB box); // line 118

  CompoundTag save(); // line 130

  void load(CompoundTag tag); // line 144

```


#### BioshipLegGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegGrowth.java` (144 lines).

```java
final class BioshipLegGrowth

  static final MaterialCost COST; // line 11

  static int missing(LivingCoreBlockEntity core); // line 12

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 22

  static List<Map.Entry<BlockPos, BlockState>> sites( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 52

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 105

  static List<BioshipSupport.Leg> attachments( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 119

```


#### BioshipMountGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipMountGrowth.java` (214 lines).

```java
final class BioshipMountGrowth

  static final int URGENCY; // line 11

  record Plan(BlockPos pos, BlockState state, int legUpgrades, MaterialCost completeCost); // line 12

  private record Demand(long at, MaterialCost cost); // line 14

  private static final Map<LivingCoreBlockEntity, Demand> DEMAND; // line 16

  static MaterialCost demand(LivingCoreBlockEntity core); // line 18

  private record Step(BlockPos pos, BlockState state, double score); // line 29

  static Plan plan(LivingCoreBlockEntity core); // line 31

  private static int siteCount(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 163

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 168

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core, Plan plan); // line 172

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 190

  private BioshipMountGrowth(); // line 211

```


#### AmoebaLegBlock

Source: `src/main/java/org/Azkerash/create_bioships/AmoebaLegBlock.java` (98 lines).

```java
public final class AmoebaLegBlock extends DirectionalBlock

  public static final MapCodec<AmoebaLegBlock> CODEC; // line 15

  public AmoebaLegBlock(Properties properties); // line 16

  public static Direction outward(LivingCoreBlockEntity core, BlockPos target); // line 21

  static boolean supportsFace(BlockState host, BlockPos hostPos, Direction face); // line 25

  static boolean canHostLeg(BlockState host, BlockPos hostPos, Direction face); // line 32

  public static boolean mounted( java.util.Map<BlockPos, BlockState> cells, BlockPos pos, BlockState leg); // line 46

  public static Direction outward(java.util.Map<BlockPos, BlockState> cells, BlockPos target); // line 53

  protected MapCodec<? extends DirectionalBlock> codec(); // line 61

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 66

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 71

  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos); // line 77

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 84

```


#### BioshipAnatomy

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAnatomy.java` (682 lines).

```java
final class BioshipAnatomy

  record Move(BlockPos source, BlockPos target, String reason, int urgency); // line 14

  private record Audit(long at, Move move); // line 15

  private static final Map<LivingCoreBlockEntity, Audit> CACHE; // line 17

  static final MaterialCost ARMOR_COST; // line 20

  static void observe(LivingCoreBlockEntity core); // line 21

  static void invalidate(LivingCoreBlockEntity core); // line 25

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 29

  static boolean execute(LivingCoreBlockEntity core, BlockPos source); // line 43

  private static Audit audit(LivingCoreBlockEntity core); // line 56

  record RepairPart(BlockPos source, BlockPos target, BlockState state); // line 72

  static boolean blockedMouths(LivingCoreBlockEntity core); // line 74

  static boolean invalidAttachment( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells, BlockPos pos, BlockState state); // line 86

  static boolean blockedGill(Map<BlockPos, BlockState> cells, BlockPos pos, BlockState state); // line 116

  private static boolean primaryNeedsRepair( LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 121

  static boolean needsBatchRepair(LivingCoreBlockEntity core); // line 133

  static List<RepairPart> repairPlan(LivingCoreBlockEntity core); // line 144

  static int[] deploymentOrder(LivingCoreBlockEntity core, List<RepairPart> plan); // line 420

  private static boolean connectedCells(BlockPos root, Set<BlockPos> cells); // line 459

  private static boolean layoutStable(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 474

  static boolean mouthOpening(LivingCoreBlockEntity core, BlockPos pos); // line 478

  private static boolean free(LivingCoreBlockEntity core, BlockPos target); // line 488

  private static boolean canMove(LivingCoreBlockEntity core, BlockPos source, BlockPos target); // line 497

  private static List<BlockPos> ports(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 507

  private static Move opening(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 521

  static int anchorIndex( BlockPos origin, BlockPos socket, Direction face, BioshipSupport.Layout layout); // line 542

  static Move compact(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells); // line 556

  static BioshipAdaptation.Proposal protection(LivingCoreBlockEntity core); // line 598

```


#### BioshipLegEvolution

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLegEvolution.java` (82 lines).

```java
public final class BioshipLegEvolution

  static final MaterialCost SWIM_COST, LAND_COST; // line 10

  public static boolean leg(BlockState state); // line 12

  static boolean swimming(BlockState state); // line 16

  static List<Map.Entry<BlockPos, BlockState>> legs(LivingCoreBlockEntity core); // line 20

  static double swimmingFraction(LivingCoreBlockEntity core); // line 27

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 34

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 65

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

Bounded 3D water routes, whole-body clearance, low-air roof detours, recovery hysteresis and buoyancy-aware velocity feedback.

```text
[water]
  creature.respiration.AirSupply
  creature.navigation.WaterRoutePlanner
  creature.navigation.SwimVelocityControl
  WaterNavigation
  WaterNavigationSpace
  BioshipAquatic
  BioshipRespiration
  RespiratoryOrganBlock
  ExposureHistory
```


#### creature.respiration.AirSupply

Source: `src/main/java/org/Azkerash/create_bioships/creature/respiration/AirSupply.java` (31 lines).

```java
public final class AirSupply

  public static final int CAPACITY, SEEK_BELOW; // line 4

  private int remaining; // line 5

  private boolean seeking; // line 6

  public int remaining(); // line 7

  public boolean seeking(); // line 11

  public boolean needsAir(boolean breathing, boolean airAccess); // line 15

  public void tick(boolean breathing, boolean airAccess, boolean workingGills); // line 19

  public void restore(int air, boolean seeking); // line 25

```


#### creature.navigation.WaterRoutePlanner

Source: `src/main/java/org/Azkerash/create_bioships/creature/navigation/WaterRoutePlanner.java` (161 lines).

```java
public final class WaterRoutePlanner

  public record Point(double x, double y, double z); // line 7

  public interface Space; // line 17

  public enum Status; // line 23

  public record Result(List<Point> points, Status status, int expanded); // line 30

  private record Cell(int x, int y, int z); // line 36

  private record Entry(Cell cell, double cost, double estimate, long order); // line 46

  public static Result toTarget( Point start, Point target, double arrival, Space space, int budget, int radius); // line 48

  public static Result find( Point start, Predicate<Point> goal, ToDoubleFunction<Point> heuristic, Space space, int budget, int radius); // line 75

  private static List<Point> smooth(List<Point> path, Space space); // line 145

  private WaterRoutePlanner(); // line 158

```


#### creature.navigation.SwimVelocityControl

Source: `src/main/java/org/Azkerash/create_bioships/creature/navigation/SwimVelocityControl.java` (20 lines).

```java
public final class SwimVelocityControl

  public record Vector(double x, double y, double z); // line 4

  private final double[] correction; // line 5

  public Vector acceleration(Vector wanted, Vector actual, double dt); // line 7

```


#### WaterNavigation

Source: `src/main/java/org/Azkerash/create_bioships/WaterNavigation.java` (125 lines).

```java
final class WaterNavigation

  private List<Vec3> path; // line 12

  private Vec3 destination; // line 13

  private int cursor, expanded; // line 14

  private long plannedAt; // line 15

  private String status; // line 16

  private boolean airRoute; // line 17

  void clear(); // line 18

  List<Vec3> remaining(); // line 27

  Vec3 toward(LivingCoreBlockEntity core, Vec3 target); // line 31

  Vec3 towardAir(LivingCoreBlockEntity core); // line 35

  private Vec3 steer(LivingCoreBlockEntity core, Vec3 target, boolean air); // line 39

  private WaterRoutePlanner.Result airRoute( LivingCoreBlockEntity core, Vec3 here, WaterNavigationSpace space); // line 82

  private boolean nextClear(WaterNavigationSpace space, Vec3 from, Vec3 to); // line 106

  CompoundTag debug(long now); // line 113

```


#### WaterNavigationSpace

Source: `src/main/java/org/Azkerash/create_bioships/WaterNavigationSpace.java` (84 lines).

```java
final class WaterNavigationSpace implements Space

  private final ServerLevel level; // line 11

  private final List<Vec3> offsets; // line 12

  private final Vec3 half; // line 13

  private final boolean surface; // line 14

  private final Map<BlockPos, Integer> samples; // line 15

  private int sample(BlockPos cell); // line 16

  WaterNavigationSpace(LivingCoreBlockEntity core, boolean surface); // line 29

  static Point point(Vec3 p); // line 48

  static Vec3 vector(Point p); // line 52

  public boolean fits(Point point); // line 56

  public boolean clear(Point from, Point to); // line 77

```


#### BioshipAquatic

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAquatic.java` (243 lines).

```java
public final class BioshipAquatic

  private final WaterNavigation navigation; // line 16

  private final SwimVelocityControl steering; // line 17

  public java.util.List<Vec3> path(); // line 18

  public CompoundTag navigationDebug(long now); // line 22

  private final AirSupply air; // line 31

  private long lastTick; // line 33

  private volatile boolean wet, swimming; // line 34

  private boolean waterContact; // line 35

  private boolean breathing, airAccess; // line 36

  private final ExposureHistory gillUsage; // line 37

  ExposureHistory gillUsage(); // line 38

  private volatile Vec3 velocity; // line 42

  private final LegUsageHistory legUsage; // line 44

  private long nextLegEvolution; // line 46

  LegUsageHistory legUsage(); // line 47

  long nextLegEvolution(); // line 51

  void legEvolved(long next); // line 55

  public boolean wet(); // line 59

  boolean waterContact(); // line 63

  public boolean swimming(); // line 67

  public int air(); // line 71

  boolean needsAir(); // line 75

  void tick(LivingCoreBlockEntity core); // line 79

  static boolean touchesWater(LivingCoreBlockEntity core); // line 140

  private static boolean waterAt(LivingCoreBlockEntity core, Vec3 point); // line 171

  static boolean canBreathe(LivingCoreBlockEntity core); // line 176

  static boolean aboveFluid(LivingCoreBlockEntity core, Vec3 point); // line 180

  private static boolean waterWithinReach(LivingCoreBlockEntity core, Vec3 here); // line 186

  void propel(ServerSubLevel ship, RigidBodyHandle body, double dt); // line 194

  public CompoundTag save(); // line 206

  void load(CompoundTag t); // line 225

```


#### BioshipRespiration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRespiration.java` (177 lines).

```java
public final class BioshipRespiration

  static final MaterialCost LUNG_COST, GILL_COST; // line 14

  static int bodySize(LivingCoreBlockEntity core); // line 16

  static int bodySize(Map<BlockPos, BlockState> cells); // line 20

  public static boolean has(LivingCoreBlockEntity core, boolean gills); // line 32

  static boolean airAccess(LivingCoreBlockEntity core); // line 37

  static boolean airAccessAt(LivingCoreBlockEntity core, Vec3 root); // line 41

  static boolean exposedGill(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 56

  public static boolean activeGills(LivingCoreBlockEntity core); // line 69

  static boolean canBreathe(LivingCoreBlockEntity core); // line 77

  static boolean wantsGills(LivingCoreBlockEntity core); // line 81

  static boolean prefersGills(LivingCoreBlockEntity core); // line 86

  static boolean missingLungs(LivingCoreBlockEntity core); // line 90

  static boolean needsLungs(LivingCoreBlockEntity core); // line 94

  static boolean blocksOpening(LivingCoreBlockEntity core, BlockPos target); // line 98

  record Growth(BlockPos pos, BlockState state, MaterialCost cost, boolean gills); // line 108

  static Growth plan(LivingCoreBlockEntity core); // line 110

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 142

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state); // line 161

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

Source: `src/main/java/org/Azkerash/create_bioships/CoreMotion.java` (744 lines).

```java
public final class CoreMotion

  private static final int FULL_COVERAGE, EVADE_TICKS; // line 16

  private BlockPos home, target; // line 17

  private long started, nextScan; // line 18

  private boolean evasive, sheltered, reclaimEntry; // line 19

  private boolean layoutDirty; // line 20

  public void layoutChanged(LivingCoreBlockEntity core); // line 21

  static boolean protectedInside(LivingCoreBlockEntity core, BlockPos pos); // line 27

  private static boolean protectedInside( LivingCoreBlockEntity core, BlockPos pos, Set<BlockPos> cells); // line 31

  private BlockPos shelteredStep( LivingCoreBlockEntity core, net.minecraft.world.entity.LivingEntity threat); // line 50

  private double shelterScore( LivingCoreBlockEntity core, BlockPos pos, net.minecraft.world.entity.LivingEntity threat); // line 88

  private BlockPos entry(LivingCoreBlockEntity core); // line 96

  public int duration(); // line 113

  public BlockPos home(LivingCoreBlockEntity core); // line 117

  public BlockPos target(); // line 121

  public long started(); // line 125

  public boolean active(); // line 129

  static boolean tissue(BlockState state); // line 133

  private boolean clearWorld(LivingCoreBlockEntity core, BlockPos pos); // line 139

  private boolean clearWorld(LivingCoreBlockEntity core, Vec3 local); // line 143

  public int coverage(LivingCoreBlockEntity core, BlockPos vantage); // line 166

  private Vec3 blockedSupply(LivingCoreBlockEntity core); // line 179

  private int score(LivingCoreBlockEntity core, BlockPos vantage); // line 185

  boolean canAvoidBlast( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 198

  private record BlastStep(BlockPos pos, BlockPos first, int ticks); // line 203

  private BlockPos blastExit( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 205

  private BlockPos choose(LivingCoreBlockEntity core, boolean returning); // line 245

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos from, BlockPos to); // line 287

  private boolean clearPerchTravel(LivingCoreBlockEntity core, BlockPos pos); // line 305

  private boolean supportedPerch(LivingCoreBlockEntity core, BlockPos pos); // line 314

  private boolean stableSupport(LivingCoreBlockEntity core, BlockPos pos); // line 332

  private BlockPos perch(LivingCoreBlockEntity core, boolean returning); // line 341

  private BlockPos reconnect(LivingCoreBlockEntity core); // line 363

  private boolean preparePerch(LivingCoreBlockEntity core); // line 398

  boolean tick(LivingCoreBlockEntity core); // line 430

  private boolean begin(LivingCoreBlockEntity core, BlockPos next, boolean emergency); // line 537

  boolean evade(LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 556

  static double blastSafety( LivingCoreBlockEntity core, BlockPos point, net.minecraft.world.entity.monster.Creeper creeper); // line 572

  private BlockPos safestStep( LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper); // line 584

  public String scoutingHint(LivingCoreBlockEntity core); // line 622

  void scoutNow(LivingCoreBlockEntity core); // line 653

  private boolean feeding; // line 658

  boolean feeding(); // line 660

  void cancel(LivingCoreBlockEntity core); // line 664

  private void commit(LivingCoreBlockEntity core); // line 675

  public CompoundTag save(); // line 717

  void load(CompoundTag tag); // line 731

```


### Batch assimilation [active]

Mouth-bound collection slots, persistent swallowed stock, batch settlement and committed wreck ownership.

```text
[collect]
  BioshipAssimilation
  BioshipFoodCollection
```


#### BioshipAssimilation

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAssimilation.java` (624 lines).

```java
public final class BioshipAssimilation

  private final List<ComponentTransport.Move> arms; // line 16

  private final Map<UUID, String> grasperBindings; // line 17

  Collection<String> busyGraspers(); // line 18

  public String grasper(UUID cargo); // line 22

  private final List<CompoundTag> stock; // line 26

  private UUID wreck; // line 28

  private boolean settling, deploymentBlocked, resumeWreck, feedingJob; // line 29

  boolean feeding(); // line 30

  private long retry; // line 34

  private CompoundTag repair; // line 36

  public boolean repairing(); // line 37

  boolean reservesRepairCell(BlockPos pos); // line 41

  public List<ComponentTransport.Move> arms(); // line 57

  public boolean active(); // line 61

  boolean committed(); // line 65

  boolean pending(); // line 69

  boolean finishing(); // line 73

  public int stored(); // line 77

  int health(LivingCoreBlockEntity core); // line 81

  double load(LivingCoreBlockEntity core); // line 92

  private static BlockState state(LivingCoreBlockEntity core, CompoundTag tag); // line 101

  boolean acceptItem(LivingCoreBlockEntity core, net.minecraft.world.item.ItemStack stack); // line 109

  public void receive(LivingCoreBlockEntity core, ServerSubLevel cargo, BlockPos pos); // line 127

  List<BioshipScavenging.Source> remaining(LivingCoreBlockEntity core); // line 162

  private List<Vec3> cluster(LivingCoreBlockEntity core, Vec3 primary); // line 185

  Vec3 gatheringPoint(LivingCoreBlockEntity core, Vec3 primary); // line 197

  boolean clusterReachable(LivingCoreBlockEntity core, Vec3 candidate); // line 204

  boolean start(LivingCoreBlockEntity core, BioshipBrain.Decision chosen); // line 213

  void tick(LivingCoreBlockEntity core); // line 280

  boolean beginRepair(LivingCoreBlockEntity core, List<BioshipAnatomy.RepairPart> plan); // line 315

  private void tickRepair(LivingCoreBlockEntity core); // line 347

  private void settle(LivingCoreBlockEntity core); // line 444

  private void take(LivingCoreBlockEntity core, CompoundTag tag); // line 541

  void abandon(LivingCoreBlockEntity core); // line 548

  CompoundTag save(); // line 579

  void load(CompoundTag tag); // line 598

```


#### BioshipFoodCollection

Source: `src/main/java/org/Azkerash/create_bioships/BioshipFoodCollection.java` (55 lines).

```java
final class BioshipFoodCollection

  static Vec3 collect(LivingCoreBlockEntity core, Vec3 point, ItemEntity item); // line 9

```


### Conserved component transport [active]

Move actual single components or indivisible formed organs with complete state; validate support and reserve tube costs before transfer.

```text
[transport]
  amoeba.transport.ComponentTransport
  amoeba.core.AmoebaCoreAdapter
  amoeba.components.AmoebaComponentHost
  amoeba.components.AmoebaComponentProvider
  amoeba.transport.ComponentAttachment
  amoeba.transport.ComponentGroupTransport
  BioshipContainerState
  integration.sable.BodyCoordinates
  integration.sable.BodyRevision
  integration.biomancy.NativeStorage
  amoeba.merging.ComponentMerging
```


#### amoeba.transport.ComponentTransport

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/transport/ComponentTransport.java` (464 lines).

```java
public final class ComponentTransport

  public record Move( UUID cargo, BlockPos cargoBlock, BlockPos source, BlockPos target, Vec3 start, long started, boolean external, boolean restoreFlesh, boolean mouth, boolean stomach, boolean manual, boolean ownerForced, List<BlockPos> tissuePath); // line 24

  public static boolean busy(AmoebaComponentHost core); // line 88

  public static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external); // line 92

  public static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual); // line 97

  public static boolean reabsorb(AmoebaComponentHost core, BlockPos source, BlockPos target); // line 106

  public static boolean reclaimToCore(AmoebaComponentHost core, BlockPos source); // line 110

  public static boolean storeComponent(AmoebaComponentHost core, BlockPos source); // line 114

  public static boolean swallow(AmoebaComponentHost core, BlockPos source); // line 118

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming); // line 122

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash); // line 132

  public static boolean merge( AmoebaComponentHost core, BlockPos source, BlockPos target, String recipe); // line 143

  private static boolean begin( AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash, String mergeRecipe); // line 148

  public static boolean deployStored( AmoebaComponentHost core, BlockPos target, net.minecraft.world.level.block.state.BlockState state, CompoundTag capsule); // line 207

  private static boolean movementIsGrouped(AmoebaComponentHost core, BlockPos source); // line 267

  public static List<BlockPos> findTissuePath( AmoebaComponentHost core, BlockPos source, BlockPos target); // line 271

  private static Vec3 alongTissue(List<BlockPos> path, double progress); // line 305

  public static void tick(AmoebaComponentHost core); // line 312

  private static void retainDeployment(AmoebaComponentHost core, ServerSubLevel cargo, Move move); // line 407

  public static void finishCargo(AmoebaComponentHost core, ServerSubLevel cargo); // line 430

  public static void release(AmoebaComponentHost core, ServerSubLevel cargo); // line 442

  public static void abandon(AmoebaComponentHost core); // line 455

```


#### amoeba.core.AmoebaCoreAdapter

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/core/AmoebaCoreAdapter.java` (322 lines).

```java
public final class AmoebaCoreAdapter implements AmoebaComponentHost

  private final LivingCoreBlockEntity core; // line 37

  public AmoebaCoreAdapter(LivingCoreBlockEntity core); // line 38

  public Level level(); // line 42

  public ServerSubLevel body(); // line 46

  public BlockPos controllerPosition(); // line 50

  public ResourceAccount resources(); // line 54

  public ComponentTransport.Move movement(); // line 58

  public void movement(ComponentTransport.Move move); // line 62

  public boolean alive(); // line 66

  public List<ComponentTransport.Move> absorptions(); // line 70

  public BlockState hostTissue(BlockEntity entity); // line 74

  public boolean absorptionActive(); // line 78

  public List<BlockPos> movementGroup(BlockPos source); // line 82

  public boolean prepareGroup(List<BlockPos> sources, BlockPos delta); // line 92

  public boolean permitsGroupTarget(BlockPos target); // line 97

  public void groupDeparted(List<BlockPos> sources); // line 103

  public Prepared prepare(Request request); // line 112

  public void departed(Request request, Prepared prepared); // line 212

  public void forgetSource(BlockPos source); // line 222

  public boolean owns(BlockPos pos); // line 226

  public boolean travelTissue(BlockState state); // line 230

  public boolean leg(BlockState state); // line 237

  public Direction legFacing(BlockPos target); // line 241

  public Direction mouthFacing(BlockPos target); // line 245

  public boolean validAttachment(BlockState state, BlockPos target); // line 249

  public BlockEntity component(ServerSubLevel owner, BlockPos pos); // line 256

  public List<BlockEntity> components(); // line 260

  public CompoundTag capture(BlockEntity entity); // line 264

  public void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule); // line 268

  public void rememberHost(BlockEntity entity, BlockState host); // line 272

  public void changed(); // line 276

  public void invalidateVision(); // line 281

  public void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim); // line 285

  public void placed( ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh); // line 290

  public void merged(); // line 298

  public boolean needsStomach(); // line 304

  public boolean digestionReady(); // line 308

  public List<FeedingMouthBlockEntity> mouths(); // line 312

  public void stomachFormed(); // line 316

```


#### amoeba.components.AmoebaComponentHost

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/components/AmoebaComponentHost.java` (117 lines).

```java
public interface AmoebaComponentHost extends CreatureBodyAccess

  record Request( BlockPos source, BlockPos target, boolean external, boolean manual, boolean consuming, boolean stash, String mergeRecipe); // line 18

  record Prepared( ServerSubLevel sourceBody, Vec3 sourceWorld, BlockState sourceHost, CompoundTag capsule, boolean mouth, boolean stomach, List<BlockPos> tissuePath, CompoundTag cargoTags); // line 26

  default List<BlockPos> movementGroup(BlockPos source); // line 40

  default boolean prepareGroup(List<BlockPos> sources, BlockPos delta); // line 45

  default boolean permitsGroupTarget(BlockPos target); // line 49

  default void groupDeparted(List<BlockPos> sources); // line 53

  ResourceAccount resources(); // line 55

  ComponentTransport.Move movement(); // line 57

  void movement(ComponentTransport.Move move); // line 59

  boolean absorptionActive(); // line 61

  boolean alive(); // line 63

  List<ComponentTransport.Move> absorptions(); // line 65

  Prepared prepare(Request request); // line 67

  void departed(Request request, Prepared prepared); // line 70

  void forgetSource(BlockPos source); // line 72

  boolean owns(BlockPos pos); // line 74

  boolean travelTissue(BlockState state); // line 76

  boolean validAttachment(BlockState state, BlockPos target); // line 78

  boolean leg(BlockState state); // line 80

  Direction legFacing(BlockPos target); // line 82

  Direction mouthFacing(BlockPos target); // line 84

  BlockEntity component(ServerSubLevel owner, BlockPos pos); // line 86

  List<BlockEntity> components(); // line 88

  CompoundTag capture(BlockEntity entity); // line 90

  void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule); // line 92

  void rememberHost(BlockEntity entity, BlockState host); // line 94

  BlockState hostTissue(BlockEntity entity); // line 96

  void changed(); // line 98

  void invalidateVision(); // line 100

  void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim); // line 102

  void placed(ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh); // line 104

  void merged(); // line 106

  boolean needsStomach(); // line 108

  boolean digestionReady(); // line 110

  List<FeedingMouthBlockEntity> mouths(); // line 112

  void stomachFormed(); // line 114

```


#### amoeba.components.AmoebaComponentProvider

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/components/AmoebaComponentProvider.java` (6 lines).

```java
public interface AmoebaComponentProvider

  AmoebaComponentHost amoebaComponents(); // line 4

```


#### amoeba.transport.ComponentAttachment

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/transport/ComponentAttachment.java` (66 lines).

```java
final class ComponentAttachment

  private ComponentAttachment(); // line 18

  static void restore( AmoebaComponentHost core, ComponentTransport.Move move, BlockState host, CompoundTag capsule); // line 19

```


#### amoeba.transport.ComponentGroupTransport

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/transport/ComponentGroupTransport.java` (173 lines).

```java
final class ComponentGroupTransport

  private static final String MEMBERS; // line 19

  static boolean begin(AmoebaComponentHost core, BlockPos source, BlockPos target, boolean manual); // line 20

  static boolean finish( AmoebaComponentHost core, ServerSubLevel cargo, ComponentTransport.Move move); // line 105

  private static boolean available( AmoebaComponentHost core, List<BlockPos> offsets, BlockPos origin); // line 159

  private ComponentGroupTransport(); // line 170

```


#### BioshipContainerState

Source: `src/main/java/org/Azkerash/create_bioships/BioshipContainerState.java` (119 lines).

```java
public final class BioshipContainerState

  public static CompoundTag capture(Level level, BlockEntity entity); // line 13

  public static BlockEntity entity( Level level, dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos pos); // line 25

  public static void restore( Level level, dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos destination, CompoundTag capsule); // line 33

  static List<ItemStack> packDrops(List<ItemStack> drops, LootParams.Builder params); // line 65

  static void creativeBreak( Level level, BlockPos pos, net.minecraft.world.entity.player.Player player); // line 85

  private BioshipContainerState(); // line 116

```


#### integration.sable.BodyCoordinates

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/BodyCoordinates.java` (16 lines).

```java
public final class BodyCoordinates

  private BodyCoordinates(); // line 8

  public static Vec3 physical(CreatureBodyAccess body, BlockPos relative); // line 9

```


#### integration.sable.BodyRevision

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/BodyRevision.java` (33 lines).

```java
public final class BodyRevision

  private static final Map<ServerSubLevel, Long> VERSIONS; // line 11

  public static long at(ServerSubLevel body); // line 13

  public static void changed(ServerSubLevel body); // line 17

  public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after); // line 24

  private BodyRevision(); // line 30

```


#### integration.biomancy.NativeStorage

Source: `src/main/java/org/Azkerash/create_bioships/integration/biomancy/NativeStorage.java` (16 lines).

```java
public final class NativeStorage

  private NativeStorage(); // line 7

  public static boolean isStorage(BlockEntity entity); // line 8

```


#### amoeba.merging.ComponentMerging

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/merging/ComponentMerging.java` (192 lines).

```java
public final class ComponentMerging

  private ComponentMerging(); // line 19

  public static final int DURATION_TICKS; // line 20

  public static final class Animation; // line 22

  public interface Commit; // line 40

  public record Recipe( String id, Class<? extends BlockEntity> source, Class<? extends BlockEntity> destination, Supplier<BlockState> result, Predicate<AmoebaComponentHost> needed, Commit commit); // line 45

  private static final class Registry; // line 57

  public static void register(Recipe recipe); // line 74

  public static boolean matches(String id, BlockEntity source, BlockEntity destination); // line 79

  public static BlockState result(String id); // line 84

  private static boolean commit( String id, AmoebaComponentHost core, BlockEntity source, BlockEntity destination); // line 88

  public static void tryAvailable(AmoebaComponentHost core); // line 96

  public static boolean absorbLarynx(AmoebaComponentHost core, CompoundTag capsule); // line 112

  public static void attachLarynx( AmoebaComponentHost core, FeedingMouthBlockEntity mouth, CompoundTag capsule); // line 120

  private static final String RECIPE_TAG; // line 126

  public static void tag(CompoundTag data, String recipe); // line 128

  public static boolean finish( AmoebaComponentHost core, dev.ryanhcode.sable.sublevel.ServerSubLevel cargo, ComponentTransport.Move move); // line 132

  private static void finishStomach( AmoebaComponentHost core, com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity decomposer, com.github.elenterius.biomancy.block.digester.DigesterBlockEntity digester); // line 156

  private static void copyItems( net.neoforged.neoforge.items.IItemHandler inventory, List<net.minecraft.world.item.ItemStack> destination); // line 184

```


### Scavenging and recycling [active]

Find eligible wreck sources, keep needed organs, conserve inventories and apply fractional lossy yields.

```text
[salvage]
  BioshipScavenging
```


#### BioshipScavenging

Source: `src/main/java/org/Azkerash/create_bioships/BioshipScavenging.java` (396 lines).

```java
public final class BioshipScavenging

  private int fleshRemainder, boneRemainder, fiberRemainder, pendingFlesh, pendingBone, pendingFiber, pendingFreeTubes; // line 27

  static boolean frameMaterial(BlockState state); // line 34

  static boolean fiberMaterial(BlockState state); // line 43

  static boolean keepComponent(LivingCoreBlockEntity core, BlockState state, BlockPos exclude); // line 55

  static boolean component(BlockState state); // line 85

  static boolean emptyComponent(LivingCoreBlockEntity core, BlockPos pos); // line 89

  static boolean needed(LivingCoreBlockEntity core, BlockState state); // line 103

  record Source(BlockPos pos, Vec3 point, BlockState state); // line 131

  static java.util.List<Source> visibleSources(LivingCoreBlockEntity core); // line 133

  static boolean collect(LivingCoreBlockEntity core, ServerSubLevel own); // line 158

  static boolean physicallyReachable( LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 point); // line 165

  private static boolean visible( LivingCoreBlockEntity core, ServerSubLevel wreck, BlockPos source, Vec3 eye, Vec3 point); // line 182

  public void finish(LivingCoreBlockEntity core, BlockPos target, boolean restoreHost); // line 214

  public void finish( LivingCoreBlockEntity core, BlockPos target, boolean restoreHost, boolean forced); // line 218

  static boolean droppedBlock(ItemStack stack); // line 284

  boolean consumeDroppedBlock(LivingCoreBlockEntity core, ItemStack stack); // line 291

  public void recycleToCore(LivingCoreBlockEntity core, BlockState state); // line 307

  void recycle(BlockState state); // line 319

  void deliver(LivingCoreBlockEntity core); // line 339

  CompoundTag save(); // line 373

  void load(CompoundTag t); // line 385

```


### Mouths and physical gut graph [active]

Compile cached many-mouth physical intake routes; share food between direct-contact stomachs and form solid large stomachs.

```text
[gut]
  DigestiveSystem
  StomachConnections
  StomachMultiblock
  StomachPorts
  StomachInventoryView
  creature.digestion.StomachFormation
  amoeba.digestion.DigestiveLayoutPlanner
  DigestiveIntakes
  creature.digestion.DigestiveIntakeRoutes
  creature.digestion.DigestiveFoodRouting
  creature.digestion.DigestiveNetwork
  BioshipDigestiveRepair
  FeedingMouthBlockEntity
  FeedingMouthBlock
  IntestineBlock
```


#### DigestiveSystem

Source: `src/main/java/org/Azkerash/create_bioships/DigestiveSystem.java` (813 lines).

```java
public final class DigestiveSystem

  public static StomachBlockEntity stomach(LivingCoreBlockEntity core); // line 20

  public static List<StomachBlockEntity> stomachs(LivingCoreBlockEntity core); // line 31

  public static List<BlockPos> mouths(LivingCoreBlockEntity core); // line 41

  public static int requiredPairs(LivingCoreBlockEntity core); // line 48

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth); // line 52

  static boolean linked(LivingCoreBlockEntity core, BlockPos a, BlockPos b); // line 56

  public static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 71

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end); // line 75

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end, BlockPos blocked, BlockPos freed); // line 80

  private static List<BlockPos> findPath( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end, BlockPos blocked, BlockPos freed, boolean spatial, Set<BlockPos> excluded); // line 93

  public static boolean reserveGroupRoute( LivingCoreBlockEntity core, List<BlockPos> sources, BlockPos delta); // line 168

  public static boolean permitsPlacement(LivingCoreBlockEntity core, BlockPos pos); // line 198

  static List<BlockPos> repairPath( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos freed); // line 209

  static int repairPurchases( LivingCoreBlockEntity core, StomachBlockEntity stomach, List<BlockPos> route); // line 215

  private record Plan(Set<BlockPos> wanted, List<BlockPos> retired, int purchases); // line 227

  private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> tubes( LivingCoreBlockEntity core); // line 229

  private static boolean removable(BlockPos point, Set<BlockPos> cells); // line 239

  private static boolean coreLink( LivingCoreBlockEntity core, BlockPos removed, Set<BlockPos> cells, Set<BlockPos> wanted); // line 258

  public static boolean reserveOrganRoute( LivingCoreBlockEntity core, BlockPos source, BlockPos target); // line 279

  public static boolean reserveOrganRoute( LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean reserve); // line 284

  static int storageCost(LivingCoreBlockEntity core, BlockPos target); // line 350

  static BlockPos storagePort(LivingCoreBlockEntity core, BlockPos source); // line 367

  static boolean needsStorageRepair(LivingCoreBlockEntity core, BlockPos pos); // line 413

  static void arrangeStorage(LivingCoreBlockEntity core); // line 425

  private static Plan plan( LivingCoreBlockEntity core, StomachBlockEntity stomach, Set<BlockPos> wanted, Set<BlockPos> starter, Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing); // line 432

  private static Plan plan( LivingCoreBlockEntity core, StomachBlockEntity stomach, Set<BlockPos> wanted, Set<BlockPos> starter, Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing, BlockPos replacingMouth); // line 441

  private static boolean affordable(StomachBlockEntity stomach, Plan plan); // line 482

  static boolean reserveCoreRoute(LivingCoreBlockEntity core, BlockPos target, boolean reserve); // line 486

  private static boolean reserveCorePairRoute( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos target, boolean reserve); // line 495

  public static void connect(LivingCoreBlockEntity core, ServerSubLevel ship); // line 531

  public static void orientStorageSac(net.minecraft.world.level.Level level, BlockPos pos); // line 575

  private static boolean connectPair( LivingCoreBlockEntity core, ServerSubLevel ship, StomachBlockEntity stomach, BlockPos mouth, boolean primary); // line 590

  private static boolean connectedStore( LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos pos); // line 691

  static boolean canStoreFood(LivingCoreBlockEntity core, ItemStack stack); // line 697

  static int foodCount(LivingCoreBlockEntity core); // line 714

  static boolean functional(LivingCoreBlockEntity core); // line 740

  public static boolean offerFood(LivingCoreBlockEntity core, ItemStack stack, boolean inherited); // line 750

  static boolean offerFood( LivingCoreBlockEntity core, BlockPos mouth, ItemStack stack, boolean inherited); // line 755

  private static boolean offerFood( LivingCoreBlockEntity core, StomachBlockEntity stomach, ItemStack stack, boolean inherited); // line 763

  public static void pullStoredFood(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 787

```


#### StomachConnections

Source: `src/main/java/org/Azkerash/create_bioships/StomachConnections.java` (74 lines).

```java
public final class StomachConnections

  private record Snapshot( UUID body, long revision, Map<DigestiveNetwork.Cell, StomachBlockEntity> nodes, DigestiveNetwork<StomachBlockEntity> network); // line 9

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE; // line 14

  private static DigestiveNetwork.Cell cell(BlockPos pos); // line 17

  private static Snapshot snapshot(LivingCoreBlockEntity core); // line 21

  public static List<StomachBlockEntity> members( LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 39

  public static List<List<StomachBlockEntity>> groups(LivingCoreBlockEntity core); // line 46

  static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 50

  static boolean retainsConnection( LivingCoreBlockEntity core, StomachBlockEntity member, StomachBlockEntity removed); // line 57

  private StomachConnections(); // line 71

```


#### StomachMultiblock

Source: `src/main/java/org/Azkerash/create_bioships/StomachMultiblock.java` (50 lines).

```java
public final class StomachMultiblock

  private record Snapshot( Map<Cell, StomachBlockEntity> members, List<StomachFormation.Cube<StomachBlockEntity>> cubes); // line 12

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE; // line 15

  public static Cell cell(BlockPos pos); // line 18

  public static BlockPos pos(Cell cell); // line 22

  public static List<StomachFormation.Cube<StomachBlockEntity>> formations( LivingCoreBlockEntity core); // line 26

  public static StomachFormation.Cube<StomachBlockEntity> containing( LivingCoreBlockEntity core, BlockPos pos); // line 39

  private StomachMultiblock(); // line 47

```


#### StomachPorts

Source: `src/main/java/org/Azkerash/create_bioships/StomachPorts.java` (71 lines).

```java
public final class StomachPorts

  private record Port(BlockPos pos, Direction face); // line 10

  public static void refresh(LivingCoreBlockEntity core); // line 11

  private static Port intake( LivingCoreBlockEntity core, StomachFormation.Cube<StomachBlockEntity> cube); // line 36

  private static Port panel( LivingCoreBlockEntity core, StomachFormation.Cube<StomachBlockEntity> cube, BlockPos intake); // line 53

  private StomachPorts(); // line 68

```


#### StomachInventoryView

Source: `src/main/java/org/Azkerash/create_bioships/StomachInventoryView.java` (128 lines).

```java
final class StomachInventoryView implements Container

  static final int PAGE_SIZE, FIELDS; // line 10

  private final StomachBlockEntity anchor; // line 11

  private final List<StomachBlockEntity> members; // line 12

  private final boolean formed; // line 13

  private int page; // line 14

  private long sampled; // line 15

  private final int[] values; // line 16

  StomachInventoryView(StomachBlockEntity anchor); // line 17

  int pages(); // line 30

  boolean turn(int direction); // line 34

  private boolean present(StomachBlockEntity organ); // line 42

  private void sample(); // line 52

  ContainerData data(); // line 72

  public int getContainerSize(); // line 88

  public boolean isEmpty(); // line 92

  public ItemStack getItem(int slot); // line 97

  public ItemStack removeItem(int slot, int amount); // line 105

  public ItemStack removeItemNoUpdate(int slot); // line 109

  public void setItem(int slot, ItemStack stack); // line 113

  public void setChanged(); // line 115

  public void clearContent(); // line 117

  public boolean stillValid(Player player); // line 119

```


#### creature.digestion.StomachFormation

Source: `src/main/java/org/Azkerash/create_bioships/creature/digestion/StomachFormation.java` (58 lines).

```java
public final class StomachFormation

  public record Cube<T>(Cell origin, Map<Cell, T> members); // line 7

  public static List<Cell> cells(Cell origin); // line 16

  public static Set<Cell> origins(Collection<Cell> stomachs); // line 25

  public static <T> List<Cube<T>> detect(Map<Cell, T> stomachs); // line 34

  public static int workUnits(long tick, boolean formed); // line 50

  private StomachFormation(); // line 55

```


#### amoeba.digestion.DigestiveLayoutPlanner

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/digestion/DigestiveLayoutPlanner.java` (103 lines).

```java
public final class DigestiveLayoutPlanner

  public static boolean arrange(LivingCoreBlockEntity core); // line 9

  private static boolean arrangeCube(LivingCoreBlockEntity core); // line 36

  private DigestiveLayoutPlanner(); // line 100

```


#### DigestiveIntakes

Source: `src/main/java/org/Azkerash/create_bioships/DigestiveIntakes.java` (122 lines).

```java
final class DigestiveIntakes

  private record Snapshot( UUID body, long revision, DigestiveIntakeRoutes<StomachBlockEntity> routes, Map<StomachBlockEntity, List<net.minecraft.world.level.block.entity.BlockEntity>> stores, Set<StomachBlockEntity> functioning); // line 12

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE; // line 18

  private static Cell cell(BlockPos p); // line 21

  static DigestiveIntakeRoutes<StomachBlockEntity> routes(LivingCoreBlockEntity core); // line 25

  static List<net.minecraft.world.level.block.entity.BlockEntity> connectedStores( LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 29

  static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach); // line 34

  private static Snapshot snapshot(LivingCoreBlockEntity core); // line 38

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth); // line 112

  private DigestiveIntakes(); // line 119

```


#### creature.digestion.DigestiveIntakeRoutes

Source: `src/main/java/org/Azkerash/create_bioships/creature/digestion/DigestiveIntakeRoutes.java` (65 lines).

```java
public final class DigestiveIntakeRoutes<T>

  public record Route<T>(Cell mouth, T stomach, List<Cell> tubes); // line 7

  private final Map<Cell, List<Route<T>>> routes; // line 12

  private final Set<Cell> mouths; // line 14

  public DigestiveIntakeRoutes( Map<Cell, T> stomachs, Set<Cell> mouths, Set<Cell> tubes, Map<Cell, Set<Cell>> links); // line 15

  public Set<Cell> mouths(); // line 48

  public List<Route<T>> routes(Cell mouth); // line 52

  public Route<T> route(Cell mouth); // line 56

  public List<Route<T>> routes(); // line 60

```


#### creature.digestion.DigestiveFoodRouting

Source: `src/main/java/org/Azkerash/create_bioships/creature/digestion/DigestiveFoodRouting.java` (35 lines).

```java
public final class DigestiveFoodRouting

  public interface Port<T>; // line 8

  public static <T extends Port<T>> int balance(List<T> members); // line 13

  private DigestiveFoodRouting(); // line 32

```


#### creature.digestion.DigestiveNetwork

Source: `src/main/java/org/Azkerash/create_bioships/creature/digestion/DigestiveNetwork.java` (58 lines).

```java
public final class DigestiveNetwork<T>

  public record Cell(int x, int y, int z) implements Comparable<Cell>; // line 7

  private final Map<Cell, List<T>> membership; // line 18

  private final List<List<T>> groups; // line 20

  public DigestiveNetwork(Map<Cell,T> stomachs); // line 21

  public List<T> members(Cell cell); // line 49

  public List<List<T>> groups(); // line 51

  public boolean hasConnection(Cell cell, Predicate<T> directlyConnected); // line 52

```


#### BioshipDigestiveRepair

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDigestiveRepair.java` (113 lines).

```java
final class BioshipDigestiveRepair

  record Move(BlockPos source, BlockPos target, int tubes); // line 13

  private record Audit(long at, int purchases, Move move); // line 14

  private static final Map<LivingCoreBlockEntity, Audit> CACHE; // line 16

  static void invalidate(LivingCoreBlockEntity core); // line 19

  static int purchases(LivingCoreBlockEntity core); // line 23

  private static Audit audit(LivingCoreBlockEntity core); // line 27

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 44

  static boolean execute(LivingCoreBlockEntity core, BlockPos source); // line 60

  private static Move move(LivingCoreBlockEntity core); // line 67

```


#### FeedingMouthBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/FeedingMouthBlockEntity.java` (264 lines).

```java
public class FeedingMouthBlockEntity extends BaseContainerBlockEntity

  private NonNullList<ItemStack> items; // line 16

  private int inheritedBiomass; // line 17

  private CompoundTag larynx; // line 18

  private int swimmingGraspers; // line 19

  public boolean swimmingGrasper(int ordinal); // line 20

  void swimmingGrasper(int ordinal, boolean swimming); // line 24

  private long motionStart; // line 33

  private int motionTicks; // line 35

  private boolean vocalMotion; // line 36

  static void bite(LivingCoreBlockEntity core, Vec3 point); // line 37

  public boolean voiced(); // line 44

  public void absorbLarynx(CompoundTag capsule); // line 48

  CompoundTag larynxCapsule(); // line 55

  public static final double REST_JAW_SHIFT; // line 59

  public double jawShift(float partial); // line 61

  void animate(int ticks, boolean vocal); // line 68

  public void vocalize(boolean hurt); // line 77

  public void vocalize(char letter); // line 83

  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(); // line 88

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 93

  private boolean inheritedSlot, embeddedInHull; // line 98

  public FeedingMouthBlockEntity(BlockPos pos, BlockState state); // line 100

  public int getContainerSize(); // line 104

  protected NonNullList<ItemStack> getItems(); // line 109

  protected void setItems(NonNullList<ItemStack> items); // line 114

  protected Component getDefaultName(); // line 119

  protected AbstractContainerMenu createMenu(int id, Inventory player); // line 125

  public void setItem(int slot, ItemStack stack); // line 130

  public boolean stillValid(Player player); // line 136

  public void inheritBioForge(ItemStack stack, int biomass); // line 145

  public boolean embeddedInHull(); // line 152

  public void embeddedInHull(boolean value); // line 156

  public static void tick( Level level, BlockPos pos, BlockState state, FeedingMouthBlockEntity mouth); // line 161

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 223

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 237

```


#### FeedingMouthBlock

Source: `src/main/java/org/Azkerash/create_bioships/FeedingMouthBlock.java` (112 lines).

```java
public class FeedingMouthBlock extends BaseEntityBlock

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 16

  public static final MapCodec<FeedingMouthBlock> CODEC; // line 18

  public FeedingMouthBlock(Properties properties); // line 19

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder); // line 25

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 31

  protected RenderShape getRenderShape(BlockState state); // line 36

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 41

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 46

  public static Direction outward(LivingCoreBlockEntity core, BlockPos pos); // line 55

  static boolean openFace(LivingCoreBlockEntity core, BlockPos pos, Direction direction); // line 77

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 90

  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 99

  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player); // line 105

```


#### IntestineBlock

Source: `src/main/java/org/Azkerash/create_bioships/IntestineBlock.java` (142 lines).

```java
public class IntestineBlock extends Block

  public static final BooleanProperty MANUAL; // line 14

  public static final BooleanProperty PAID; // line 15

  public static final BooleanProperty ENCASED; // line 16

  public static final Map<Direction, BooleanProperty> CONNECTIONS; // line 17

  public IntestineBlock(Properties properties); // line 31

  public static boolean compatible(BlockState s); // line 43

  public BlockState connections(BlockState s, BlockGetter level, BlockPos pos); // line 51

  public BlockState getStateForPlacement(BlockPlaceContext c); // line 57

  protected BlockState updateShape( BlockState s, Direction d, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos); // line 65

  protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving); // line 76

  protected VoxelShape getShape( BlockState s, BlockGetter level, BlockPos pos, CollisionContext context); // line 85

  protected net.minecraft.world.ItemInteractionResult useItemOn( net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit); // line 107

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b); // line 135

```


### Recipe digestion [active]

Native Biomancy recipes and output rolls, acid, input queues, local nutrient paste and tube stock.

```text
[stomach]
  StomachBlockEntity
  StomachBlock
```


#### StomachBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/StomachBlockEntity.java` (527 lines).

```java
public class StomachBlockEntity extends BlockEntity implements MenuProvider, ResourceAccount, org.Azkerash.create_bioships.creature.digestion.DigestiveFoodRouting.Port< StomachBlockEntity>

  public static final int MAX_ACID; // line 29

  private final NonNullList<ItemStack> input; // line 30

  private final boolean[] inherited; // line 31

  private final Map<ResourceLocation, Integer> reserves; // line 32

  final List<BlockPos> gutPath; // line 33

  final Map<BlockPos, List<BlockPos>> sacPaths; // line 34

  BlockPos connectedMouth; // line 35

  boolean tubeBootstrapped; // line 36

  int freeTubeStock, paidTubeStock; // line 37

  private int acid, activeSlot; // line 38

  private String activeRecipe; // line 39

  public StomachBlockEntity(BlockPos pos, BlockState state); // line 40

  private int nutrientReserve; // line 44

  public int nutrients(); // line 46

  public void addNutrients(int amount); // line 50

  int burnPaste(); // line 55

  int burnNutrients(); // line 61

  public int burnReserve(); // line 75

  public static int burnStored(CompoundTag stores); // line 93

  public int material(String id); // line 115

  public void retain(ItemStack stack); // line 120

  public void credit(String id, int count); // line 129

  void creditLocal(String id, int count); // line 136

  public void retainFlesh(int count); // line 142

  int localMaterial(String id); // line 146

  int localMaterialTotal(); // line 150

  java.util.List<String> localMaterialIds(); // line 154

  boolean spendLocal(String item, int count); // line 158

  public boolean spend(String item, int count); // line 167

  public int acid(); // line 172

  public int meals(); // line 176

  public void continueGrowth(LivingCoreBlockEntity core); // line 180

  public void inheritInput(ItemStack stack); // line 192

  void transferTo(StomachBlockEntity target); // line 204

  int queuedNutrition(); // line 225

  public ItemStack foodStack(int slot); // line 233

  public int queuedFood(); // line 238

  public boolean canEnqueue(ItemStack stack, boolean oldStock); // line 244

  private boolean canReceiveMeal(ItemStack stack, boolean oldStock); // line 248

  boolean receiveMeal(ItemStack stack, boolean oldStock); // line 266

  public boolean enqueue(ItemStack stack, boolean oldStock); // line 291

  public boolean moveMealTo(StomachBlockEntity target); // line 295

  public boolean connected(LivingCoreBlockEntity core); // line 325

  public static void tick(Level level, BlockPos pos, BlockState state, StomachBlockEntity stomach); // line 329

  public Component getDisplayName(); // line 409

  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player); // line 414

  public ContainerData menuData(); // line 419

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 444

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 483

```


#### StomachBlock

Source: `src/main/java/org/Azkerash/create_bioships/StomachBlock.java` (91 lines).

```java
public class StomachBlock extends BaseEntityBlock

  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING; // line 14

  public enum Role implements net.minecraft.util.StringRepresentable; // line 16

  public static final net.minecraft.world.level.block.state.properties.EnumProperty<Role> ROLE; // line 27

  public static final MapCodec<StomachBlock> CODEC; // line 30

  public StomachBlock(Properties properties); // line 31

  protected void createBlockStateDefinition( net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder); // line 40

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 46

  protected RenderShape getRenderShape(BlockState state); // line 51

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 56

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 61

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 69

  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops( BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params); // line 78

  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player); // line 84

```


### Shared usable storage [active]

Core-first deposits, connected native/private sac stocks, external-first withdrawal and reserve refill.

```text
[storage]
  BioshipStorage
  BioshipStores
```


#### BioshipStorage

Source: `src/main/java/org/Azkerash/create_bioships/BioshipStorage.java` (201 lines).

```java
public final class BioshipStorage

  static final int CORE_CAPACITY; // line 11

  static boolean fuel(String id); // line 12

  static List<BlockEntity> sacks(LivingCoreBlockEntity core); // line 16

  static int deposit(LivingCoreBlockEntity core, String id, int count); // line 23

  static void flush(LivingCoreBlockEntity core); // line 52

  static Map<String, Integer> snapshot(LivingCoreBlockEntity core); // line 63

  static Map<String, Integer> metabolicStock(LivingCoreBlockEntity core); // line 89

  static int amount(LivingCoreBlockEntity core, String id); // line 104

  static boolean spend(LivingCoreBlockEntity core, String id, int count); // line 108

  private static int withdrawExternal(LivingCoreBlockEntity core, String id, int count); // line 121

  private static void refill(LivingCoreBlockEntity core); // line 148

  public static ResourceAccount account(LivingCoreBlockEntity core); // line 166

  static int capacity(LivingCoreBlockEntity core); // line 187

  static int products(LivingCoreBlockEntity core); // line 193

```


#### BioshipStores

Source: `src/main/java/org/Azkerash/create_bioships/BioshipStores.java` (60 lines).

```java
final class BioshipStores

  static final String MATERIALS, AUTOMATIC; // line 7

  static final int STOMACH_CAPACITY, SAC_CAPACITY; // line 8

  static LivingCoreBlockEntity core(StomachBlockEntity stomach); // line 9

  static List<BlockEntity> connected(StomachBlockEntity stomach); // line 18

  static int stored(BlockEntity sac, String id); // line 24

  static int total(BlockEntity sac); // line 28

  static void change(BlockEntity sac, String id, int delta); // line 35

  static int total(StomachBlockEntity stomach); // line 44

  static int capacity(StomachBlockEntity stomach); // line 49

  static void balance(StomachBlockEntity stomach); // line 54

```


### Costs, ledgers and reserve policy [active]

Affordability, payment rollback and refunds; core emergency target 32 flesh / 16 fiber / 8 bone.

```text
[money]
  creature.resources.MaterialCost
  creature.resources.ResourceAccount
  creature.api.CreatureBodyAccess
  creature.resources.MaterialLedger
  EmergencyReserve
```


#### creature.resources.MaterialCost

Source: `src/main/java/org/Azkerash/create_bioships/creature/resources/MaterialCost.java` (54 lines).

```java
public record MaterialCost(int flesh, int fiber, int bone)

  public static final String FLESH, FIBER, BONE; // line 6

  public MaterialCost; // line 9

  private Map<String, Integer> entries(); // line 14

  public boolean funded(ResourceAccount account); // line 18

  public boolean pay(ResourceAccount account); // line 23

  public void refund(ResourceAccount account); // line 37

  public static boolean transfer(ResourceAccount from, ResourceAccount to, String id, int count); // line 45

```


#### creature.resources.ResourceAccount

Source: `src/main/java/org/Azkerash/create_bioships/creature/resources/ResourceAccount.java` (10 lines).

```java
public interface ResourceAccount

  int material(String id); // line 4

  boolean spend(String id, int count); // line 5

  void credit(String id, int count); // line 7

```


#### creature.api.CreatureBodyAccess

Source: `src/main/java/org/Azkerash/create_bioships/creature/api/CreatureBodyAccess.java` (14 lines).

```java
public interface CreatureBodyAccess

  Level level(); // line 8

  ServerSubLevel body(); // line 9

  BlockPos controllerPosition(); // line 11

```


#### creature.resources.MaterialLedger

Source: `src/main/java/org/Azkerash/create_bioships/creature/resources/MaterialLedger.java` (37 lines).

```java
public final class MaterialLedger implements ResourceAccount

  private final Map<String, Integer> amounts; // line 6

  public int material(String id); // line 7

  public boolean spend(String id, int count); // line 11

  public void credit(String id, int count); // line 19

  public Map<String, Integer> snapshot(); // line 24

  public void restore(Map<String, Integer> saved); // line 28

```


#### EmergencyReserve

Source: `src/main/java/org/Azkerash/create_bioships/EmergencyReserve.java` (25 lines).

```java
public final class EmergencyReserve

  static final MaterialCost TARGET; // line 9

  static Map<String, Integer> targets(); // line 10

  static boolean missing(LivingCoreBlockEntity core); // line 14

  public static boolean after(LivingCoreBlockEntity core, MaterialCost price); // line 19

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAdaptationPlanner.java` (312 lines).

```java
final class BioshipAdaptationPlanner

  record State(Map<String, Integer> reserve, Set<BlockPos> armor, long nextWork); // line 15

  private static boolean clear(LivingCoreBlockEntity core, BlockPos pos); // line 21

  private static BlockPos organPort(LivingCoreBlockEntity core, boolean mouth); // line 35

  static List<Proposal> plan(LivingCoreBlockEntity core, State memory); // line 47

  private static List<Proposal> offers( LivingCoreBlockEntity core, State memory, BioshipMountGrowth.Plan mounting); // line 66

  private static boolean funded(StomachBlockEntity stomach, int flesh, int fiber, int bone); // line 251

  private static BlockPos shedCandidate( LivingCoreBlockEntity core, StomachBlockEntity stomach, boolean sacsOnly, Set<BlockPos> armor); // line 255

  private static boolean connectedWithout(Set<BlockPos> body, BlockPos removed, BlockPos root); // line 294

```


#### BioshipDigestiveGrowth

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDigestiveGrowth.java` (170 lines).

```java
public final class BioshipDigestiveGrowth

  record Plan( BlockPos mouth, BlockPos stomach, BlockPos tube, Direction facing, boolean addMouth, boolean addStomach, MaterialCost cost, int tubeCredits, int legUpgrades); // line 12

  public static int target(LivingCoreBlockEntity core); // line 22

  static int mouthTarget(LivingCoreBlockEntity core); // line 26

  static Plan plan(LivingCoreBlockEntity core); // line 30

  private static List<Direction> mouthDirections(LivingCoreBlockEntity core); // line 88

  private static Plan check( LivingCoreBlockEntity core, BlockPos mouth, BlockPos stomach, Direction face, boolean addMouth, boolean addStomach); // line 106

  private static boolean replaceable(LivingCoreBlockEntity core, BlockPos pos); // line 159

```


#### BioshipRecovery

Source: `src/main/java/org/Azkerash/create_bioships/BioshipRecovery.java` (125 lines).

```java
final class BioshipRecovery

  record Options(boolean mouth, boolean stomach); // line 10

  static Options options(LivingCoreBlockEntity core); // line 11

  static Options available(LivingCoreBlockEntity core); // line 16

  static BlockPos sacrifice( LivingCoreBlockEntity core, Map<String, Integer> reserve, Options options); // line 29

  private static boolean eligible(LivingCoreBlockEntity core, BlockPos pos); // line 84

  private static boolean connectedWithout(Set<BlockPos> cells, BlockPos removed, BlockPos root); // line 109

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAdaptation.java` (255 lines).

```java
public final class BioshipAdaptation

  static final String FLESH, FIBER, BONE; // line 15

  record Proposal( BioshipBrain.Action action, BlockPos pos, BlockState state, int flesh, int fiber, int bone, int urgency, String reason); // line 18

  private final BioshipBodyBehavior behavior; // line 28

  public BioshipBodyBehavior behavior(); // line 30

  private final MaterialLedger reserve; // line 34

  private final Set<BlockPos> armor; // line 36

  private long nextWork; // line 37

  private boolean starterLegSalvage; // line 38

  private int starterLegBudget; // line 39

  int starterLegBudget(); // line 40

  void usedStarterLeg(); // line 44

  public void salvageStarterLegs(); // line 48

  MaterialLedger ledger(); // line 55

  int material(String id); // line 59

  void creditRecovery(String id, int count); // line 63

  private boolean pay(LivingCoreBlockEntity core, int flesh, int fiber, int bone); // line 67

  void maintain(LivingCoreBlockEntity core); // line 71

  List<Proposal> proposals(LivingCoreBlockEntity core); // line 80

  void tick(LivingCoreBlockEntity core); // line 85

  int burnEmergency(LivingCoreBlockEntity core); // line 217

  CompoundTag save(); // line 230

  void load(CompoundTag t); // line 242

```


#### BioshipBodyBehavior

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBodyBehavior.java` (77 lines).

```java
public final class BioshipBodyBehavior

  private final GoalQueue<BioshipAdaptation.Proposal> goals; // line 8

  private String previous, waiting; // line 9

  private BioshipAdaptation.Proposal selected; // line 10

  static String blocker(LivingCoreBlockEntity core); // line 11

  private static String key(BioshipAdaptation.Proposal p); // line 22

  private static BehaviorPriority.Offer priority(BioshipAdaptation.Proposal p); // line 26

  BioshipAdaptation.Proposal choose(LivingCoreBlockEntity core); // line 35

  boolean allowsConfiguration(LivingCoreBlockEntity core); // line 50

  public CompoundTag debug(); // line 55

```


#### BioshipConfiguration

Source: `src/main/java/org/Azkerash/create_bioships/BioshipConfiguration.java` (74 lines).

```java
final class BioshipConfiguration

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 8

  static void execute(LivingCoreBlockEntity core); // line 55

  private BioshipConfiguration(); // line 71

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
  mixin.BodyBlockChangeMixin
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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHealthPool.java` (69 lines).

```java
final class BioshipHealthPool

  static int part(BlockState state, CompoundTag data, boolean host); // line 10

  private static CompoundTag anatomy(net.minecraft.world.level.block.entity.BlockEntity actor); // line 25

  static int total( LivingCoreBlockEntity core, Map<net.minecraft.core.BlockPos, BlockState> current); // line 33

  private BioshipHealthPool(); // line 66

```


#### BioshipHealthChanges

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHealthChanges.java` (59 lines).

```java
public final class BioshipHealthChanges

  private static final Set<ServerSubLevel> DIRTY; // line 16

  public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after); // line 18

  public static void changed(LivingCoreBlockEntity core); // line 35

  public static void flush(ServerTickEvent.Post event); // line 39

  public static void stop(ServerStoppedEvent event); // line 51

  private BioshipHealthChanges(); // line 56

```


#### mixin.BodyBlockChangeMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/BodyBlockChangeMixin.java` (24 lines).

```java
abstract class BodyBlockChangeMixin

```


#### BioshipBody

Source: `src/main/java/org/Azkerash/create_bioships/BioshipBody.java` (401 lines).

```java
public final class BioshipBody

  public Map<BlockPos, BlockState> intendedCells(); // line 19

  final Map<BlockPos, BlockState> memory; // line 23

  private final Set<BlockPos> injured; // line 25

  private double health; // line 26

  private int capacity; // line 27

  private boolean healthInitialized; // line 28

  private long healthAudits, lastDamageTick, lastDamageTick; // line 29

  private BioshipDeathCause lastDamageCause; // line 30

  long healthAudits(); // line 31

  void initializeHealth(LivingCoreBlockEntity core); // line 35

  private volatile boolean dead; // line 39

  private BioshipDeathCause deathCause; // line 41

  private long deathAt; // line 42

  public BioshipDeathCause deathCause(); // line 43

  public long deathAt(); // line 47

  private long lastCombatAt; // line 51

  private float lastCombatAmount; // line 53

  private long flashUntil; // line 54

  public boolean flashing(LivingCoreBlockEntity core); // line 55

  public boolean dead(); // line 59

  void starve(LivingCoreBlockEntity core, int demand); // line 63

  void drown(LivingCoreBlockEntity core); // line 67

  void asphyxiate(LivingCoreBlockEntity core); // line 71

  private void hurt(LivingCoreBlockEntity core, double amount, BioshipDeathCause cause); // line 75

  private void damageFeedback(LivingCoreBlockEntity core); // line 91

  void die(LivingCoreBlockEntity core); // line 98

  void die(LivingCoreBlockEntity core, BioshipDeathCause cause); // line 102

  public double health(); // line 126

  public int capacity(); // line 130

  public static Map<BlockPos, BlockState> actual(LivingCoreBlockEntity core); // line 134

  public void observe(LivingCoreBlockEntity core); // line 142

  void rebase(BlockPos delta); // line 178

  public void reconcile(LivingCoreBlockEntity core, Map<BlockPos, BlockState> before); // line 187

  void tick(LivingCoreBlockEntity core); // line 206

  boolean combatDamage( LivingCoreBlockEntity core, net.minecraft.world.damagesource.DamageSource source, float amount); // line 267

  void struck(LivingCoreBlockEntity core, Player player); // line 292

  void damage(LivingCoreBlockEntity core, BlockPos pos, Player player); // line 298

  private void damage( LivingCoreBlockEntity core, BlockPos pos, Player player, BioshipDeathCause cause); // line 302

  public static void broken(BlockEvent.BreakEvent event); // line 311

  public static void blast(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event); // line 328

  public CompoundTag save(); // line 343

  public void load(CompoundTag tag, HolderLookup.Provider registries); // line 359

  public static ListTag saveCells(Map<BlockPos, BlockState> cells); // line 376

  public static Map<BlockPos, BlockState> loadCells( ListTag list, HolderLookup.Provider registries); // line 388

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipSurvivalAdaptations.java` (96 lines).

```java
public final class BioshipSurvivalAdaptations

  static final int MAX_BLAST_POINTS, MEAT_POINTS, SURVIVAL_POINTS; // line 16

  private int blastPoints; // line 17

  private record Subject(ServerLevel level, UUID ship); // line 18

  private static final Map<Explosion, Set<Subject>> PENDING; // line 20

  private static final ThreadLocal<Deque<Explosion>> ACTIVE; // line 22

  public int blastPoints(); // line 24

  void ateCreeperMeat(int count); // line 28

  void survivedExplosion(); // line 33

  public float blockResistance(float base); // line 37

  float damage(float amount); // line 41

  public static void begin(Explosion explosion); // line 45

  public static void end(); // line 49

  static void damaged(LivingCoreBlockEntity core); // line 53

  public static void settle(ServerTickEvent.Post event); // line 61

  public static void stop(ServerStoppedEvent event); // line 79

  CompoundTag save(); // line 85

  void load(CompoundTag tag); // line 91

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDefense.java` (256 lines).

```java
final class BioshipDefense

  private int cooldown; // line 13

  private boolean extending; // line 14

  private final TentacleStrike strike; // line 15

  private double strikeReach; // line 16

  public net.minecraft.world.phys.Vec3 collisionTip(); // line 17

  public boolean sweeping(long now); // line 21

  private java.util.UUID attacker; // line 25

  private long threatenedUntil; // line 27

  private Vec3 lastThreat; // line 28

  void remember(LivingCoreBlockEntity core, LivingEntity entity); // line 29

  Vec3 dangerPosition(LivingCoreBlockEntity core); // line 38

  static boolean threatening(LivingCoreBlockEntity core, LivingEntity entity); // line 47

  static net.minecraft.world.entity.monster.Creeper creeperThreat(LivingCoreBlockEntity core); // line 55

  net.minecraft.nbt.CompoundTag save(); // line 73

  void load(net.minecraft.nbt.CompoundTag tag); // line 84

  static LivingEntity target(LivingCoreBlockEntity core); // line 97

  static boolean eligiblePrey(LivingCoreBlockEntity core, LivingEntity entity); // line 138

  static LivingEntity prey(LivingCoreBlockEntity core); // line 148

  private static boolean sensesPrey(LivingCoreBlockEntity core, LivingEntity entity); // line 169

  LivingEntity retreatTarget(LivingCoreBlockEntity core); // line 188

  void tick(LivingCoreBlockEntity core); // line 193

```


#### BioshipCreeperHazard

Source: `src/main/java/org/Azkerash/create_bioships/BioshipCreeperHazard.java` (32 lines).

```java
public final class BioshipCreeperHazard

  public record Estimate(double damageReach, int fuseRemaining); // line 7

  public static Estimate estimate(Creeper creeper); // line 16

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

Source: `src/main/java/org/Azkerash/create_bioships/TentacleStrike.java` (124 lines).

```java
public final class TentacleStrike

  public static final int WINDUP, SWEEP; // line 12

  public static final double TIP_HALF_SIZE; // line 13

  private Vec3 aim; // line 14

  private UUID victim; // line 15

  private long started; // line 16

  private boolean hit; // line 17

  private Vec3 previousTip; // line 18

  private long previousTick; // line 19

  public Vec3 collisionTip(); // line 20

  public boolean active(); // line 24

  public LivingEntity victim(ServerLevel level); // line 28

  public void begin(LivingEntity target, long now); // line 32

  public void clear(); // line 41

  public boolean finished(long now); // line 49

  public Vec3 tip(Vec3 root, double now, double reach); // line 53

  public boolean sweeping(long now); // line 64

  public boolean tick( LivingCoreBlockEntity core, Vec3 root, LivingEntity target, double reach, float damage); // line 68

  static boolean tipCollision(AABB target, Vec3 previous, Vec3 current); // line 95

  public CompoundTag save(); // line 103

  public void load(CompoundTag tag); // line 114

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
public final class BioshipPrey

  public static boolean eligible(LivingEntity entity); // line 8

```


#### BioshipMeatDrops

Source: `src/main/java/org/Azkerash/create_bioships/BioshipMeatDrops.java` (63 lines).

```java
public final class BioshipMeatDrops

  public static void drops(LivingDropsEvent event); // line 14

```


### Native mob targeting [active]

Inject hostile target goals; attack-surface proxy redirects native attacks to the core body.

```text
[hostile]
  BioshipHostility
  BioshipTargetEntity
```


#### BioshipHostility

Source: `src/main/java/org/Azkerash/create_bioships/BioshipHostility.java` (35 lines).

```java
public final class BioshipHostility

  private static final java.util.Set<Mob> ATTACHED; // line 12

  public static void joined(EntityJoinLevelEvent event); // line 14

```


#### BioshipTargetEntity

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTargetEntity.java` (271 lines).

```java
public final class BioshipTargetEntity extends LivingEntity

  private static final Map< ServerLevel, Map< UUID, EnumMap< net.minecraft.core.Direction, java.lang.ref.WeakReference<BioshipTargetEntity>>>> LIVE; // line 17

  private record SurfaceCells(long at, List<net.minecraft.core.BlockPos> cells); // line 24

  private static final Map<LivingCoreBlockEntity, SurfaceCells> SURFACE_CELLS; // line 26

  static List<BioshipTargetEntity> surfaces(LivingCoreBlockEntity core); // line 28

  static BioshipTargetEntity attackSource(LivingCoreBlockEntity core, LivingEntity victim); // line 42

  private UUID shipId; // line 54

  private net.minecraft.core.Direction face; // line 56

  private LivingCoreBlockEntity cachedCore; // line 57

  public BioshipTargetEntity(EntityType<? extends BioshipTargetEntity> type, Level level); // line 58

  public static AttributeSupplier.Builder attributes(); // line 66

  public LivingCoreBlockEntity core(); // line 70

  boolean belongsTo(LivingCoreBlockEntity core); // line 85

  public static BioshipTargetEntity ensure(LivingCoreBlockEntity core); // line 89

  public void tick(); // line 146

  private void positionOnBody(LivingCoreBlockEntity core); // line 167

  public boolean hurt(DamageSource source, float amount); // line 190

  public boolean isPushable(); // line 202

  public void push(Entity entity); // line 207

  public boolean isPickable(); // line 210

  public boolean canBeHitByProjectile(); // line 215

  public void remove(RemovalReason reason); // line 220

  public double getVisibilityPercent(Entity observer); // line 232

  public Iterable<ItemStack> getArmorSlots(); // line 237

  public ItemStack getItemBySlot(EquipmentSlot slot); // line 242

  public void setItemSlot(EquipmentSlot slot, ItemStack stack); // line 247

  public HumanoidArm getMainArm(); // line 250

  public void addAdditionalSaveData(CompoundTag tag); // line 255

  public void readAdditionalSaveData(CompoundTag tag); // line 262

```


### Owner and ally permissions [active]

Owner/whitelist checks protect allies and refresh legacy membrane permissions.

```text
[allies]
  BioshipAccess
  ShipMembraneAccess
```


#### BioshipAccess

Source: `src/main/java/org/Azkerash/create_bioships/BioshipAccess.java` (101 lines).

```java
public final class BioshipAccess

  public final Map<UUID, String> players, animals; // line 16

  public boolean allows(LivingCoreBlockEntity core, Entity entity); // line 17

  public CompoundTag save(UUID owner); // line 25

  public void load(CompoundTag tag); // line 37

  private static void read(CompoundTag tag, Map<UUID, String> out); // line 44

  public static boolean permits(CompoundTag tag, Entity entity); // line 52

  public static LivingCoreBlockEntity core(net.minecraft.world.level.Level level, BlockPos pos); // line 62

  public static void tick(LivingCoreBlockEntity core); // line 70

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipDecay.java` (283 lines).

```java
public final class BioshipDecay

  static final int GRACE_TICKS; // line 17

  static final int DECAY_INTERVAL; // line 18

  static int priority(ServerLevel level, BlockPos pos); // line 19

  static final class Deaths extends net.minecraft.world.level.saveddata.SavedData; // line 34

  public static boolean dead(ServerSubLevel ship); // line 95

  public static UUID lineage(ServerSubLevel ship); // line 99

  public static void link(ServerSubLevel child, ServerSubLevel original); // line 106

  public static void mark(ServerSubLevel ship); // line 114

  public static void mark(ServerSubLevel ship, BioshipDeathCause cause); // line 118

  public static void inherit(ServerSubLevel child, ServerSubLevel original); // line 146

  public static List<BlockPos> blocks(ServerSubLevel ship); // line 161

  static boolean recoverLegacyHull(ServerSubLevel part); // line 165

  public static void tick(LevelTickEvent.Post event); // line 196

  static void decay(ServerLevel level); // line 258

```


### Read-only diagnostic transport [active]

Rate-limited server snapshots of actual perception, route, chosen behavior and outstanding needs.

```text
[debug]
  network.BioshipNetwork
  network.NavigationDebug
  network.VisionDebug
```


#### network.BioshipNetwork

Source: `src/main/java/org/Azkerash/create_bioships/network/BioshipNetwork.java` (209 lines).

```java
public final class BioshipNetwork

  public record Throttle(BlockPos pos, int setting) implements CustomPacketPayload; // line 19

  public record SightRequest() implements CustomPacketPayload; // line 35

  public record SightSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 47

  public record PathRequest() implements CustomPacketPayload; // line 61

  public record PathSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 73

  public record TrustBurst(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 87

  public record Voice(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload; // line 101

  public static void register(RegisterPayloadHandlersEvent event); // line 115

  public static boolean setThrottle(ServerPlayer player, BlockPos pos, int setting); // line 183

```


#### network.NavigationDebug

Source: `src/main/java/org/Azkerash/create_bioships/network/NavigationDebug.java` (122 lines).

```java
public final class NavigationDebug

  public static final String DATA; // line 20

  public static CompoundTag inspect(LivingCoreBlockEntity core); // line 21

  public static CompoundTag request(ServerPlayer player); // line 95

  private NavigationDebug(); // line 119

```


#### network.VisionDebug

Source: `src/main/java/org/Azkerash/create_bioships/network/VisionDebug.java` (113 lines).

```java
public final class VisionDebug

  public static final String DATA; // line 18

  public static CompoundTag inspect(LivingCoreBlockEntity core, Vec3 target); // line 19

  static String reason(LivingCoreBlockEntity core, BioshipVision.View view, Vec3 target); // line 50

  public static CompoundTag vector(Vec3 point); // line 62

  public static Vec3 vector(CompoundTag tag); // line 70

  static CompoundTag request(ServerPlayer player); // line 74

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

Source: `src/main/java/org/Azkerash/create_bioships/client/BioshipClient.java` (53 lines).

```java
public final class BioshipClient

  public static final LivingControlHandler CONTROLS; // line 21

  public static void setup(FMLClientSetupEvent event); // line 22

  public static void startControl(Level level, Player player, BlockPos pos); // line 28

  public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event); // line 32

  public static void renderers(EntityRenderersEvent.RegisterRenderers event); // line 39

```


#### client.AssimilationArmRenderer

Source: `src/main/java/org/Azkerash/create_bioships/client/AssimilationArmRenderer.java` (348 lines).

```java
public class AssimilationArmRenderer implements BlockEntityRenderer<LivingCoreBlockEntity>

  private final ModelPart segment; // line 24

  private static final ResourceLocation SKIN; // line 25

  public AssimilationArmRenderer(BlockEntityRendererProvider.Context context); // line 28

  public boolean shouldRenderOffScreen(LivingCoreBlockEntity core); // line 38

  public void render( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 43

  private void renderBatch( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light); // line 199

  private void renderLegs( LivingCoreBlockEntity core, float partial, PoseStack pose, MultiBufferSource buffers, int light); // line 256

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

Source: `src/main/java/org/Azkerash/create_bioships/amoeba/client/ComponentTransportRendering.java` (65 lines).

```java
public final class ComponentTransportRendering

  private record Active(ComponentTransport.Move move, boolean swallowed); // line 17

  private static final Map<UUID, Active> ACTIVE; // line 18

  private static WeakReference<ClientLevel> level; // line 20

  private static long tick; // line 21

  private ComponentTransportRendering(); // line 22

  public static Pose3dc pose(ClientSubLevel cargo, Pose3dc original, float partial); // line 24

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
  development.BioshipDiagnostics
  development.client.SightDebugOverlay
  development.client.PathDebugOverlay
  development.client.AppendageDebugOverlay
  client.SymbioticTooltip
```


#### development.BioshipDiagnostics

Source: `src/main/java/org/Azkerash/create_bioships/development/BioshipDiagnostics.java` (268 lines).

```java
public final class BioshipDiagnostics

  public static List<LivingCoreBlockEntity> cores(ServerLevel level); // line 33

  public static void decayMarked(ServerSubLevel ship); // line 42

  public static void removalCheck( LivingCoreBlockEntity core, ServerSubLevel owner, net.minecraft.core.BlockPos pos); // line 63

  public static void death(LivingCoreBlockEntity core, BioshipDeathCause cause, long at); // line 95

  public static CompoundTag snapshot(LivingCoreBlockEntity core); // line 119

  public static void commands(RegisterCommandsEvent event); // line 208

```


#### development.client.SightDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/development/client/SightDebugOverlay.java` (216 lines).

```java
public final class SightDebugOverlay

  private static boolean enabled(); // line 23

  private static int nextRequest; // line 26

  private static net.minecraft.client.multiplayer.ClientLevel lastLevel; // line 28

  private static boolean equipped(); // line 29

  public static void tick(ClientTickEvent.Post event); // line 36

  private static CompoundTag snapshot(); // line 55

  private static int color(String reason); // line 63

  private static String label(String reason); // line 73

  public static void hud(RenderGuiEvent.Post event); // line 85

  private static int drawText( net.minecraft.client.gui.GuiGraphics g, String text, int y, int color); // line 140

  public static void render(RenderLevelStageEvent event); // line 153

  private static void line( com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, int color); // line 198

```


#### development.client.PathDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/development/client/PathDebugOverlay.java` (251 lines).

```java
public final class PathDebugOverlay

  private static boolean enabled; // line 25

  private static int nextRequest; // line 26

  private static net.minecraft.client.multiplayer.ClientLevel lastLevel; // line 27

  public static void toggle(); // line 28

  public static void tick(ClientTickEvent.Post event); // line 40

  private static CompoundTag snapshot(); // line 56

  private static String position(CompoundTag data, String key); // line 66

  private static final class Lines extends RenderType; // line 72

  public static void render(RenderLevelStageEvent event); // line 103

  private static String priority(CompoundTag entry, int index); // line 149

  private static void hologram( CompoundTag data, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers); // line 159

  private static void marker( PoseStack.Pose pose, VertexConsumer out, Vec3 p, double size, int color); // line 232

  private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, int color); // line 239

```


#### development.client.AppendageDebugOverlay

Source: `src/main/java/org/Azkerash/create_bioships/development/client/AppendageDebugOverlay.java` (72 lines).

```java
public final class AppendageDebugOverlay

  public static void render(RenderLevelStageEvent event); // line 19

```


#### client.SymbioticTooltip

Source: `src/main/java/org/Azkerash/create_bioships/client/SymbioticTooltip.java` (94 lines).

```java
public final class SymbioticTooltip

  public static void render(RenderGuiEvent.Post event); // line 20

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipWrenchItem.java` (128 lines).

```java
public class BioshipWrenchItem extends Item

  public BioshipWrenchItem(Properties properties); // line 17

  public static boolean movable(net.minecraft.world.level.Level level, BlockPos pos); // line 20

  public InteractionResult useOn(UseOnContext context); // line 37

  public InteractionResultHolder<ItemStack> use( net.minecraft.world.level.Level level, Player player, InteractionHand hand); // line 109

  public void appendHoverText( ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags); // line 120

```


#### BioshipGogglesItem

Source: `src/main/java/org/Azkerash/create_bioships/BioshipGogglesItem.java` (9 lines).

```java
public class BioshipGogglesItem extends GogglesItem

  public BioshipGogglesItem(Properties properties); // line 5

```


#### StomachMenu

Source: `src/main/java/org/Azkerash/create_bioships/StomachMenu.java` (89 lines).

```java
public class StomachMenu extends AbstractContainerMenu

  private final ContainerData data; // line 13

  private final BlockPos pos; // line 14

  private final StomachInventoryView view; // line 15

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach); // line 16

  private StomachMenu(int id, Inventory inventory, BlockPos pos, StomachInventoryView view); // line 20

  private StomachMenu( int id, Inventory inventory, BlockPos pos, ContainerData data, Container food, StomachInventoryView view); // line 24

  public static StomachMenu fromNetwork( int id, Inventory inventory, RegistryFriendlyByteBuf buffer); // line 50

  public int value(int index); // line 61

  public boolean clickMenuButton(Player player, int button); // line 65

  public ItemStack quickMoveStack(Player player, int slot); // line 73

  public boolean stillValid(Player player); // line 78

```


#### client.StomachScreen

Source: `src/main/java/org/Azkerash/create_bioships/client/StomachScreen.java` (105 lines).

```java
public class StomachScreen extends AbstractContainerScreen<StomachMenu>

  public StomachScreen(StomachMenu menu, Inventory inventory, Component title); // line 10

  protected void init(); // line 15

  private void page(int direction); // line 28

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY); // line 33

  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY); // line 87

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial); // line 98

```


### Packaged development world [active]

Normal-world flat/elevation glass courses, world preset and reset command.

```text
[lab]
  development.NavigationLab
  development.EcosystemLab
  development.AquaticLab
  development.OceanLabTerrain
  development.LabHabitatTerrain
  development.EndHabitatLab
  development.DeveloperTestTool
  development.LabPopulation
  development.LabStarters
```


#### development.NavigationLab

Source: `src/main/java/org/Azkerash/create_bioships/development/NavigationLab.java` (281 lines).

```java
public final class NavigationLab

  private static final ResourceKey<Biome> BIOME; // line 33

  private static final BlockPos FLAT, ELEVATED; // line 37

  public record Course( Map<BlockPos, BlockState> blocks, Set<BlockPos> glass, Set<BlockPos> grass, BlockPos prize, int[] gates); // line 39

  private NavigationLab(); // line 46

  public static Course course(BlockPos origin, boolean elevated); // line 48

  public static void place(ServerLevel level, Course course); // line 102

  static boolean lab(ServerLevel level); // line 110

  private static final class State extends SavedData; // line 116

  private static void build(ServerLevel level); // line 142

  private static void seed(ServerLevel level, UUID owner); // line 171

  public static void started(ServerStartedEvent event); // line 208

  public static void joined(PlayerEvent.PlayerLoggedInEvent event); // line 214

  public static void commands(RegisterCommandsEvent event); // line 230

```


#### development.EcosystemLab

Source: `src/main/java/org/Azkerash/create_bioships/development/EcosystemLab.java` (359 lines).

```java
public final class EcosystemLab

  private static final ResourceKey<net.minecraft.world.level.biome.Biome> BIOME; // line 25

  public static final int RADIUS, PREY_CAP, HOSTILE_CAP; // line 29

  public static final class State extends SavedData; // line 30

  public static boolean lab(ServerLevel level); // line 81

  public static void start(ServerStartedEvent event); // line 88

  public static void prepare(ServerLevel level, BlockPos center); // line 96

  private static void installFixtures(ServerLevel level, State state); // line 167

  static void reset(ServerLevel level); // line 175

  static BlockPos habitat(ServerLevel level, BlockPos base, int kind); // line 190

  public static void tick(ServerTickEvent.Post event); // line 223

  public static void sunProtection(Mob mob); // line 235

  public static void join(PlayerEvent.PlayerLoggedInEvent event); // line 242

  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event); // line 262

```


#### development.AquaticLab

Source: `src/main/java/org/Azkerash/create_bioships/development/AquaticLab.java` (334 lines).

```java
public final class AquaticLab

  public static final int RADIUS, DEPTH, AIR_HEIGHT, FISH_CAP; // line 16

  private static final int LEGACY_RADIUS, VERSION; // line 17

  public static final int ISOLATION_RADIUS; // line 18

  private static final class State extends SavedData; // line 20

  public static BlockPos center(ServerLevel level); // line 52

  public static void build(ServerLevel level, BlockPos landCenter); // line 56

  static BlockPos viewpoint(ServerLevel level); // line 156

  private static void plants(ServerLevel level, BlockPos center, boolean upgrading); // line 160

  static void isolate(ServerLevel level, BlockPos center); // line 184

  private static AABB bounds(BlockPos center); // line 250

  public static int fishCount(ServerLevel level); // line 256

  public static void replenish(ServerLevel level); // line 263

  static boolean contains(ServerLevel level, net.minecraft.world.phys.Vec3 point); // line 273

  public static void reset(ServerLevel level, BlockPos landCenter); // line 277

  private record SpawnSites(long tick, BlockPos center, java.util.List<BlockPos> sites); // line 291

  private static final java.util.Map<ServerLevel, SpawnSites> SPAWN_SITES; // line 293

  private static void spawnFish(ServerLevel level, BlockPos center); // line 296

```


#### development.OceanLabTerrain

Source: `src/main/java/org/Azkerash/create_bioships/development/OceanLabTerrain.java` (51 lines).

```java
public final class OceanLabTerrain

  public static int floor(int x, int z); // line 7

  static int pattern(int x, int z); // line 15

  static Block sediment(int x, int z); // line 19

  static Block rock(int x, int y, int z); // line 24

```


#### development.LabHabitatTerrain

Source: `src/main/java/org/Azkerash/create_bioships/development/LabHabitatTerrain.java` (70 lines).

```java
final class LabHabitatTerrain

  static void land(ServerLevel level, BlockPos center); // line 8

```


#### development.EndHabitatLab

Source: `src/main/java/org/Azkerash/create_bioships/development/EndHabitatLab.java` (125 lines).

```java
final class EndHabitatLab

  public static final BlockPos CENTER; // line 16

  static final int RADIUS; // line 17

  public static boolean inside(double x, double y, double z); // line 18

  public static BlockPos prepare(ServerLevel level); // line 25

  private static final java.util.Map<ServerLevel, Long> NEXT_REFILL; // line 88

  public static void population(ServerTickEvent.Post event); // line 90

  public static void contain(EntityTeleportEvent.EnderEntity event); // line 114

```


#### development.DeveloperTestTool

Source: `src/main/java/org/Azkerash/create_bioships/development/DeveloperTestTool.java` (120 lines).

```java
public final class DeveloperTestTool extends Item

  public DeveloperTestTool(Properties properties); // line 19

  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand); // line 22

  private static ItemStack icon(Item item, String name); // line 28

  private static void open(ServerPlayer player, int section); // line 34

  public static void joined(PlayerEvent.PlayerLoggedInEvent event); // line 96

```


#### development.LabPopulation

Source: `src/main/java/org/Azkerash/create_bioships/development/LabPopulation.java` (140 lines).

```java
public final class LabPopulation

  static final String MARKER; // line 18

  private static final Map<ServerLevel, Long> LAST; // line 19

  public static BlockPos controller(BlockPos center); // line 21

  static void install(ServerLevel level, BlockPos center); // line 25

  static AABB landBounds(ServerLevel level, BlockPos center); // line 36

  public record Counts(int fish, int prey, int hostiles, int cores); // line 46

  public static Counts counts(ServerLevel level, BlockPos center); // line 48

  static void pulse(ServerLevel level, EcosystemLab.State state); // line 67

  static final long REFILL_INTERVAL; // line 75

  public static boolean refillDue(EcosystemLab.State state, long now); // line 77

  public static void replenish(ServerLevel level, EcosystemLab.State state); // line 89

  static void prime(ServerLevel level, BlockPos center); // line 98

  static boolean spawnLand(ServerLevel level, BlockPos center, boolean hostile); // line 103

```


#### development.LabStarters

Source: `src/main/java/org/Azkerash/create_bioships/development/LabStarters.java` (118 lines).

```java
public final class LabStarters

  private static final String MARKER; // line 19

  public static void seed(ServerLevel level, EcosystemLab.State state); // line 20

  private static void component( ServerLevel level, EcosystemLab.State state, BlockPos pos, int index); // line 50

  public static void core(ServerLevel level, EcosystemLab.State state, BlockPos pos); // line 60

  public static void clear(ServerLevel level, EcosystemLab.State state); // line 78

```


### Sable and assembly hooks [active]

Protected assembly, safe transfer/disassembly, split lineage and Sable-owned plot unloading.

```text
[platform]
  integration.sable.mixin.SimAssemblyHelperMixin
  integration.sable.mixin.DisassemblyPreventionMixin
  integration.sable.mixin.ServerSubLevelMixin
  integration.sable.mixin.SablePlotUnloadMixin
```


#### integration.sable.mixin.SimAssemblyHelperMixin

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/mixin/SimAssemblyHelperMixin.java` (26 lines).

```java
public class SimAssemblyHelperMixin

```


#### integration.sable.mixin.DisassemblyPreventionMixin

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/mixin/DisassemblyPreventionMixin.java` (23 lines).

```java
public class DisassemblyPreventionMixin

```


#### integration.sable.mixin.ServerSubLevelMixin

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/mixin/ServerSubLevelMixin.java` (18 lines).

```java
public class ServerSubLevelMixin

```


#### integration.sable.mixin.SablePlotUnloadMixin

Source: `src/main/java/org/Azkerash/create_bioships/integration/sable/mixin/SablePlotUnloadMixin.java` (30 lines).

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
  development.client.mixin.PathDebugKeyMixin
```


#### mixin.BiometricMembraneAccessMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/BiometricMembraneAccessMixin.java` (57 lines).

```java
public class BiometricMembraneAccessMixin implements ShipMembraneAccess

  private CompoundTag bioships$permissions; // line 18

  public void bioships$access(CompoundTag access); // line 19

```


#### mixin.BiometricSubLevelCollisionMixin

Source: `src/main/java/org/Azkerash/create_bioships/mixin/BiometricSubLevelCollisionMixin.java` (34 lines).

```java
public class BiometricSubLevelCollisionMixin

```


#### development.client.mixin.PathDebugKeyMixin

Source: `src/main/java/org/Azkerash/create_bioships/development/client/mixin/PathDebugKeyMixin.java` (19 lines).

```java
abstract class PathDebugKeyMixin

```


### Airship implementation boundary [gate]

available() returns false. Saved stage data does not activate hull construction or flight.

```text
[gate]
  disabled.wip.AirshipPrototype
```


#### disabled.wip.AirshipPrototype

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/AirshipPrototype.java` (32 lines).

```java
public final class AirshipPrototype

  private AirshipPrototype(); // line 8

  public static boolean available(); // line 9

  public static boolean component(Block block); // line 13

```


### Preserved hull and room geometry [dormant]

Prototype shell, bay capacity and attachment-to-room attribution; shared constants/geometry are retained.

```text
[layout]
  disabled.wip.DinghyGrowth
  disabled.wip.EngineRoomLayout
  disabled.wip.EngineRoomSizing
  disabled.wip.BioshipRoomOwnership
```


#### disabled.wip.DinghyGrowth

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/DinghyGrowth.java` (211 lines).

```java
public final class DinghyGrowth

  public static final int LAYOUT_VERSION; // line 16

  public static final BlockPos HELM, THROTTLE, VERTICAL_TRIM, HUNT, ACCESS_PANEL; // line 17

  public static final BlockPos BLUEPRINT_PANEL; // line 22

  public static final BlockPos DOOR, MOUTH, LARYNX; // line 23

  public static final List<BlockPos> ACCESS_MEMBRANES; // line 26

  public static final List<BlockPos> CABIN_LIGHTS; // line 28

  public static final List<BlockPos> FLAGELLA; // line 34

  public static final List<BlockPos> BUOYANCY_ORGANS; // line 42

  public static final BlockPos PERCH; // line 52

  public record Part(BlockPos offset, BlockState state); // line 54

  public static BlockState cabinLantern(); // line 56

  public static BlockState helm(); // line 64

  public static int radius(int z); // line 72

  public static int radius(int z, int version); // line 76

  public static BlockPos atVersion(BlockPos offset, int version); // line 87

  public static List<Part> body(); // line 97

  public static List<Part> body(int version); // line 101

  private static final class Layout; // line 105

  private static List<Part> createBody(int version); // line 109

  private static BlockState shell(Direction facing); // line 203

```


#### disabled.wip.EngineRoomLayout

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/EngineRoomLayout.java` (50 lines).

```java
public final class EngineRoomLayout

  static boolean skin(BlockState state); // line 12

  public static List<DinghyGrowth.Part> body(int version, int columns); // line 17

  static BlockPos at(LivingCoreBlockEntity core, BlockPos local); // line 45

```


#### disabled.wip.EngineRoomSizing

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/EngineRoomSizing.java` (20 lines).

```java
public final class EngineRoomSizing

  static int columns(int digestivePairs); // line 4

  static int capacity(int columns); // line 7

  static int widening(int columns); // line 11

  public static int width(int columns); // line 15

```


#### disabled.wip.BioshipRoomOwnership

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BioshipRoomOwnership.java` (31 lines).

```java
final class BioshipRoomOwnership

  static Map<BlockPos, BioshipBlueprint.Room> assign( Set<BlockPos> cells, List<BioshipBlueprint.Room> rooms, BlockPos structuralOffset); // line 7

```


### Preserved engine-room investment [dormant]

Room quotes and expansion are disabled. EngineRoom.storage() remains a shared native-sac predicate.

```text
[engine]
  disabled.wip.BioshipEngineRoomPlanner
  disabled.wip.BioshipEngineRoom
  disabled.wip.EngineRoom
```


#### disabled.wip.BioshipEngineRoomPlanner

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BioshipEngineRoomPlanner.java` (175 lines).

```java
public final class BioshipEngineRoomPlanner

  public record Quote( int columns, int pairs, Map<BlockPos, BlockState> changes, MaterialCost cost, int legUpgrades); // line 24

  static Quote quote(LivingCoreBlockEntity core); // line 43

  public static String reason(LivingCoreBlockEntity core); // line 47

  private static Quote blocked(java.util.function.Consumer<String> report, String reason); // line 54

  private static Quote quote( LivingCoreBlockEntity core, java.util.function.Consumer<String> report); // line 59

```


#### disabled.wip.BioshipEngineRoom

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BioshipEngineRoom.java` (62 lines).

```java
public final class BioshipEngineRoom

  private int columns; // line 14

  public int columns(); // line 15

  public int capacity(); // line 19

  public int widening(); // line 23

  public void restore(int value); // line 27

  public BioshipEngineRoomPlanner.Quote quote(LivingCoreBlockEntity core); // line 31

  public boolean expand(LivingCoreBlockEntity core); // line 35

```


#### disabled.wip.EngineRoom

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/EngineRoom.java` (88 lines).

```java
public final class EngineRoom

  public static boolean inside(LivingCoreBlockEntity core, BlockPos pos); // line 14

  public static List<BlockPos> stomachBays(LivingCoreBlockEntity core); // line 22

  public static void arrange(LivingCoreBlockEntity core, ServerSubLevel ship); // line 31

  public static boolean move( LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos source, BlockPos target); // line 81

```


### Preserved reconstruction prototype [dormant]

Reconstruction jobs and UI are disabled; BioshipBlueprint.physical() remains a shared coordinate helper.

```text
[blueprint]
  disabled.wip.BioshipBlueprint
  disabled.wip.BlueprintMenu
  disabled.wip.BlueprintPanelBlock
  disabled.wip.client.BlueprintScreen
```


#### disabled.wip.BioshipBlueprint

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BioshipBlueprint.java` (877 lines).

```java
public final class BioshipBlueprint

  public record Room(int id, String name, int x, int z, int width, int length); // line 32

  public static class Draft; // line 62

  private List<Room> rooms; // line 100

  private List<Room> pendingRooms; // line 104

  private final Map<BlockPos, BlockState> target; // line 105

  private final Map<BlockPos, CompoundTag> cargo; // line 106

  private final Map<BlockPos, BlockPos> sources; // line 107

  private String phase, status; // line 108

  private int revision, search, workTicks, carryTicks; // line 109

  private Vec3 landing, searchGoal, carryStart, carryEnd; // line 110

  private UUID carried; // line 111

  private int paid; // line 112

  private int baseCockpitZ; // line 113

  private boolean demolitionDone; // line 114

  void engineWidth(int width); // line 115

  public List<Room> rooms(); // line 121

  public String phase(); // line 125

  public String status(); // line 129

  public int revision(); // line 133

  public boolean busy(); // line 137

  public boolean locked(); // line 141

  public UUID carried(); // line 145

  public int carryTicks(); // line 149

  public Vec3 carryEnd(); // line 153

  public Vec3 carryStart(); // line 157

  public BlockPos offset(BlockPos original); // line 161

  public BlockPos engineLocal(BlockPos local); // line 170

  public int available(LivingCoreBlockEntity core); // line 175

  private static BlockPos structureLocal(LivingCoreBlockEntity core, BlockPos local); // line 180

  private static BlockPos coreLocal(LivingCoreBlockEntity core, BlockPos local); // line 184

  public Map<BlockPos, BlockState> proposal(LivingCoreBlockEntity core, Draft draft); // line 188

  public int cost(LivingCoreBlockEntity core, Draft draft); // line 250

  public String validate(LivingCoreBlockEntity core, Draft d, Map<BlockPos, BlockState> plan); // line 258

  private static boolean editable(BlockState s); // line 326

  public boolean confirm(LivingCoreBlockEntity core, Draft d, int baseRevision); // line 338

  public void tick(LivingCoreBlockEntity core); // line 388

  Vec3 site(LivingCoreBlockEntity core, Vec3 candidate); // line 454

  public Vec3 flight(LivingCoreBlockEntity core); // line 486

  static boolean worldClear(net.minecraft.world.level.Level level, Vec3 from, Vec3 to); // line 518

  public boolean aboard(LivingCoreBlockEntity core, Entity entity); // line 529

  private Vec3 exit(LivingCoreBlockEntity core); // line 548

  private boolean evacuate(LivingCoreBlockEntity core); // line 575

  private void sync(LivingCoreBlockEntity core); // line 640

  private void clear(LivingCoreBlockEntity core, BlockPos local); // line 647

  private static boolean matches(BlockState live, BlockState wanted); // line 653

  private void build(LivingCoreBlockEntity core); // line 662

  public int burnBufferedReserve(); // line 764

  public CompoundTag save(); // line 778

  public void load(CompoundTag t, HolderLookup.Provider registries); // line 823

  private static CompoundTag vector(Vec3 v); // line 864

  private static Vec3 readVector(CompoundTag t); // line 872

```


#### disabled.wip.BlueprintMenu

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BlueprintMenu.java` (117 lines).

```java
public class BlueprintMenu extends AbstractContainerMenu

  public final LivingCoreBlockEntity model; // line 14

  public final BioshipBlueprint.Draft draft; // line 15

  private final LivingCoreBlockEntity core; // line 16

  private final BlockPos panel; // line 17

  private final int revision; // line 18

  private final ContainerData data; // line 19

  public BlueprintMenu(int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos panel); // line 20

  public static BlueprintMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf b); // line 48

  public int available(); // line 61

  public int cost(); // line 65

  public void edit(int action); // line 69

  public boolean clickMenuButton(Player player, int action); // line 73

  public boolean stillValid(Player p); // line 97

  public ItemStack quickMoveStack(Player p, int slot); // line 111

```


#### disabled.wip.BlueprintPanelBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BlueprintPanelBlock.java` (68 lines).

```java
public class BlueprintPanelBlock extends HorizontalDirectionalBlock

  public static final MapCodec<BlueprintPanelBlock> CODEC; // line 16

  public BlueprintPanelBlock(Properties properties); // line 17

  protected MapCodec<? extends HorizontalDirectionalBlock> codec(); // line 22

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 27

  public BlockState getStateForPlacement(BlockPlaceContext context); // line 32

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 37

```


#### disabled.wip.client.BlueprintScreen

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/client/BlueprintScreen.java` (183 lines).

```java
public class BlueprintScreen extends AbstractContainerScreen<BlueprintMenu>

  private int mode; // line 13

  public BlueprintScreen(BlueprintMenu menu, Inventory inventory, Component title); // line 14

  private void send(int id); // line 20

  protected void init(); // line 30

  public boolean mouseClicked(double x, double y, int button); // line 98

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY); // line 108

  protected void renderLabels(GuiGraphics g, int x, int y); // line 152

```


### Preserved flight controls [dormant]

Control input and throttle packets are rejected; client hold-control registration is disabled.

```text
[controls]
  disabled.wip.LivingHelmBlock
  disabled.wip.LivingThrottleBlock
  disabled.wip.NerveControlBlock
  disabled.wip.PilotAuthority
  disabled.wip.client.LivingControlHandler
```


#### disabled.wip.LivingHelmBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/LivingHelmBlock.java` (56 lines).

```java
public class LivingHelmBlock extends Block

  public static final IntegerProperty STEERING; // line 15

  public LivingHelmBlock(Properties properties); // line 16

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 21

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 26

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 32

```


#### disabled.wip.LivingThrottleBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/LivingThrottleBlock.java` (72 lines).

```java
public class LivingThrottleBlock extends Block implements QuietUse

  public static final IntegerProperty THROTTLE; // line 16

  public static final int NEUTRAL; // line 17

  public enum Kind; // line 18

  private final Kind kind; // line 23

  public LivingThrottleBlock(Properties properties); // line 25

  public LivingThrottleBlock(Properties properties, Kind kind); // line 29

  public Kind kind(); // line 35

  public static double signed(BlockState state); // line 39

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder); // line 43

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 48

  public InteractionResult quietUse( Player player, InteractionHand hand, BlockPos pos, BlockState state); // line 54

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 62

```


#### disabled.wip.NerveControlBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/NerveControlBlock.java` (107 lines).

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


#### disabled.wip.PilotAuthority

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/PilotAuthority.java` (13 lines).

```java
public final class PilotAuthority

  public static final int TRUST_THRESHOLD; // line 4

  private PilotAuthority(); // line 5

  public static boolean obeys( int trust, boolean authorized, boolean present, boolean aboard, long now, long commandUntil); // line 7

```


#### disabled.wip.client.LivingControlHandler

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/client/LivingControlHandler.java` (97 lines).

```java
public final class LivingControlHandler extends BlockHoldInteraction

  private double value; // line 19

  private int sent; // line 20

  private LivingThrottleBlock.Kind kind; // line 21

  public void startHold(Level level, Player player, BlockPos pos); // line 22

  public boolean activeOnMouseMove(double yaw, double pitch); // line 32

  private void send(); // line 40

  public boolean activeTick(Level level, LocalPlayer player); // line 48

  public int getCrouchBlockingTicks(); // line 62

  public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui); // line 67

```


### Preserved cockpit models and UI [dormant]

Models, registry IDs, shapes and screens retained; no active airship progression or control UI.

```text
[cockpit]
  disabled.wip.CaptainSeatBlock
  disabled.wip.BuoyancyOrganBlock
  disabled.wip.LivingDoorBlock
  disabled.wip.AccessMenu
  disabled.wip.AccessPanelBlock
  disabled.wip.client.AccessScreen
```


#### disabled.wip.CaptainSeatBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/CaptainSeatBlock.java` (29 lines).

```java
public class CaptainSeatBlock extends SeatBlock

  private static final VoxelShape SHAPE; // line 12

  public CaptainSeatBlock(Properties properties); // line 18

  public VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 22

```


#### disabled.wip.BuoyancyOrganBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BuoyancyOrganBlock.java` (35 lines).

```java
public final class BuoyancyOrganBlock extends Block

  public static final MapCodec<BuoyancyOrganBlock> CODEC; // line 13

  private static final VoxelShape SHAPE; // line 14

  public BuoyancyOrganBlock(Properties properties); // line 19

  protected MapCodec<? extends Block> codec(); // line 23

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 28

```


#### disabled.wip.LivingDoorBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/LivingDoorBlock.java` (40 lines).

```java
public class LivingDoorBlock extends DoorBlock

  public LivingDoorBlock(Properties properties); // line 15

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 18

  public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open); // line 25

```


#### disabled.wip.AccessMenu

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/AccessMenu.java` (123 lines).

```java
public class AccessMenu extends AbstractContainerMenu

  public record Choice(UUID id, String label, boolean animal); // line 15

  private final LivingCoreBlockEntity core; // line 16

  private final BlockPos pos; // line 18

  public final List<Choice> choices; // line 19

  private final ContainerData allowed; // line 20

  public AccessMenu( int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos pos, List<Choice> choices); // line 21

  public static AccessMenu fromNetwork( int id, Inventory inventory, RegistryFriendlyByteBuf buffer); // line 48

  public boolean allowed(int index); // line 59

  public static List<Choice> choices(LivingCoreBlockEntity core); // line 63

  public boolean clickMenuButton(Player player, int index); // line 84

  public boolean stillValid(Player player); // line 105

  public ItemStack quickMoveStack(Player player, int slot); // line 117

```


#### disabled.wip.AccessPanelBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/AccessPanelBlock.java` (56 lines).

```java
public class AccessPanelBlock extends Block

  public AccessPanelBlock(Properties properties); // line 14

  protected net.minecraft.world.phys.shapes.VoxelShape getShape( BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context); // line 17

  protected InteractionResult useWithoutItem( BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit); // line 26

```


#### disabled.wip.client.AccessScreen

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/client/AccessScreen.java` (95 lines).

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
  disabled.wip.TentacleBlockEntity
  disabled.wip.TentacleBlock
  disabled.wip.FlagellumMotion
  disabled.wip.client.FlagellumRenderer
```


#### disabled.wip.TentacleBlockEntity

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/TentacleBlockEntity.java` (211 lines).

```java
public class TentacleBlockEntity extends BlockEntity implements BlockEntitySubLevelActor

  private UUID target; // line 27

  private final TentacleStrike strike; // line 28

  public TentacleStrike strike(); // line 29

  private int clientTarget; // line 33

  private long lastTick; // line 35

  public TentacleBlockEntity(BlockPos pos, BlockState state); // line 36

  public boolean swimming(); // line 40

  public int targetEntityId(); // line 44

  public UUID targetId(); // line 48

  private ServerSubLevel ship(); // line 52

  public LivingCoreBlockEntity owner(ServerSubLevel ship); // line 60

  public static boolean attackable(LivingEntity entity); // line 68

  boolean valid(LivingEntity entity, LivingCoreBlockEntity core); // line 76

  public static boolean edible(LivingEntity entity); // line 87

  private boolean claimed(ServerSubLevel ship, UUID entity); // line 91

  private void syncTarget(); // line 99

  private void release(); // line 104

  public void setRemoved(); // line 114

  public void sable$tick(ServerSubLevel ship); // line 120

  public void serverTick(); // line 125

  public boolean visible( Vec3 from, Vec3 to, Entity target, LivingCoreBlockEntity core, ServerSubLevel ship); // line 173

  public ClientboundBlockEntityDataPacket getUpdatePacket(); // line 188

  public CompoundTag getUpdateTag(HolderLookup.Provider registries); // line 193

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries); // line 201

```


#### disabled.wip.TentacleBlock

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/TentacleBlock.java` (50 lines).

```java
public class TentacleBlock extends BaseEntityBlock

  public static final MapCodec<TentacleBlock> CODEC; // line 12

  public TentacleBlock(Properties properties); // line 13

  protected MapCodec<? extends BaseEntityBlock> codec(); // line 17

  protected RenderShape getRenderShape(BlockState state); // line 22

  protected VoxelShape getShape( BlockState state, BlockGetter level, BlockPos pos, CollisionContext context); // line 27

  public BlockEntity newBlockEntity(BlockPos pos, BlockState state); // line 33

  public <T extends BlockEntity> BlockEntityTicker<T> getTicker( Level level, BlockState state, BlockEntityType<T> type); // line 38

```


#### disabled.wip.FlagellumMotion

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/FlagellumMotion.java` (10 lines).

```java
public final class FlagellumMotion

  private FlagellumMotion(); // line 3

  public static double strength(int available, long ticks); // line 4

```


#### disabled.wip.client.FlagellumRenderer

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/client/FlagellumRenderer.java` (135 lines).

```java
public class FlagellumRenderer implements BlockEntityRenderer<TentacleBlockEntity>

  private static final ResourceLocation SKIN; // line 25

  private final ModelPart segment; // line 28

  public FlagellumRenderer(BlockEntityRendererProvider.Context context); // line 29

  public boolean shouldRenderOffScreen(TentacleBlockEntity entity); // line 39

  public void render( TentacleBlockEntity limb, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 44

```


### Preserved flight rescue [dormant]

New flight rescues are unavailable; saved active rescues may close safely and restore retracted tissue.

```text
[rescue]
  disabled.wip.BioshipRescue
  disabled.wip.mixin.ReconfigurationHeatMapMixin
```


#### disabled.wip.BioshipRescue

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/BioshipRescue.java` (382 lines).

```java
public final class BioshipRescue

  private final Map<BlockPos, BlockState> retracted; // line 23

  private List<BlockPos> path; // line 24

  private UUID passenger; // line 25

  private int cursor, animation, stalled; // line 26

  private String phase; // line 27

  private Vec3 carried; // line 28

  private long nextAttempt; // line 29

  public boolean active(); // line 30

  public Vec3 carriedPosition(); // line 34

  public boolean retracted(BlockPos pos); // line 38

  public Map<BlockPos, BlockState> tissue(); // line 42

  public double withdrawal(); // line 46

  private static boolean hull(BlockState state); // line 52

  private Set<BlockPos> obstruction(LivingCoreBlockEntity core, Vec3 world, boolean allowHull); // line 59

  private List<BlockPos> route(LivingCoreBlockEntity core, Player player, BlockPos goal); // line 106

  public boolean begin(LivingCoreBlockEntity core, Player player); // line 148

  public void tick(LivingCoreBlockEntity core); // line 198

  private static boolean connectedWithout(LivingCoreBlockEntity core, Set<BlockPos> holes); // line 329

  private void sync(LivingCoreBlockEntity core); // line 346

  public CompoundTag save(); // line 352

  public void load(CompoundTag tag, HolderLookup.Provider registries); // line 366

```


#### disabled.wip.mixin.ReconfigurationHeatMapMixin

Source: `src/main/java/org/Azkerash/create_bioships/disabled/wip/mixin/ReconfigurationHeatMapMixin.java` (25 lines).

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

Source: `src/main/java/org/Azkerash/create_bioships/LocomotionProfiles.java` (68 lines).

```java
public final class LocomotionProfiles extends SimpleJsonResourceReloadListener

  private static volatile Map<ResourceLocation, LocomotionProfile> profiles; // line 15

  private static final ResourceLocation DEFAULT; // line 16

  private static final Map<ResourceLocation, java.util.function.Predicate<LivingCoreBlockEntity>> adapters; // line 18

  private LocomotionProfiles(); // line 20

  public static void register(AddReloadListenerEvent event); // line 24

  public static void registerAdapter( ResourceLocation profile, java.util.function.Predicate<LivingCoreBlockEntity> matches); // line 29

  static LocomotionProfile forCreature(LivingCoreBlockEntity core); // line 34

  protected void apply( Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler); // line 41

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipGrasperEvolution.java` (54 lines).

```java
final class BioshipGrasperEvolution

  record Change(FeedingMouthBlockEntity mouth, int ordinal, boolean swimming, MaterialCost cost); // line 7

  static Change plan(LivingCoreBlockEntity core); // line 8

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 27

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos); // line 43

```


#### client.TentacleSuckers

Source: `src/main/java/org/Azkerash/create_bioships/client/TentacleSuckers.java` (49 lines).

```java
public final class TentacleSuckers

  private static final ResourceLocation TEXTURE; // line 12

  private static final ModelPart CUPS; // line 15

  private static ModelPart bake(); // line 16

  public static void renderSegment( ModelPart segment, PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light, int overlay, int index); // line 31

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTissue.java` (45 lines).

```java
public final class BioshipTissue

  static final MaterialCost GROWTH_COST; // line 9

  public static Block body(); // line 11

  public static boolean isTissue(BlockState state); // line 15

  public static void remember( net.minecraft.world.level.block.entity.BlockEntity entity, BlockState state); // line 19

  public static BlockState host(net.minecraft.world.level.block.entity.BlockEntity entity); // line 29

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

Source: `src/main/java/org/Azkerash/create_bioships/BioshipTrustFeedback.java` (52 lines).

```java
public final class BioshipTrustFeedback

  public static final String INBOX; // line 10

  static final int PARTICLES; // line 11

  private long nextTick; // line 12

  boolean ready(long tick); // line 13

  long nextTick(); // line 17

  void restore(long value); // line 21

  boolean emit(LivingCoreBlockEntity core, boolean positive); // line 25

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

Source: `src/main/java/org/Azkerash/create_bioships/client/BioshipHurtFlash.java` (51 lines).

```java
final class BioshipHurtFlash

  static int overlay(net.minecraft.world.level.block.entity.BlockEntity entity, int fallback); // line 14

  static void render( LivingCoreBlockEntity core, PoseStack pose, MultiBufferSource buffers, int light); // line 24

```


### Per-mouth voice and articulation [active]

A larynx is conserved inside one upgraded mouth. Paid voice growth follows functional digestion and urgent survival. A-Z is a pre-speech creature palette, not spoken letter names.

```text
[voice]
  BioshipLarynx
  development.BioshipVoiceCommands
  BioshipVoice
  BioshipVoicePalette
  client.BioshipVoicePlayer
  client.MouthRenderer
```


#### BioshipLarynx

Source: `src/main/java/org/Azkerash/create_bioships/BioshipLarynx.java` (114 lines).

```java
public final class BioshipLarynx

  static final MaterialCost COST; // line 15

  static boolean isLarynx(BlockState state); // line 16

  public static List<FeedingMouthBlockEntity> mouths(LivingCoreBlockEntity core); // line 23

  static long missingCount(LivingCoreBlockEntity core); // line 31

  static boolean missing(LivingCoreBlockEntity core); // line 35

  public static boolean ready(LivingCoreBlockEntity core); // line 39

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core); // line 43

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos); // line 65

  private static BlockEntity nativeSource( LivingCoreBlockEntity core, FeedingMouthBlockEntity mouth); // line 85

  static void growl(LivingCoreBlockEntity core); // line 97

  static void hurt(LivingCoreBlockEntity core); // line 101

  private static void vocalize(LivingCoreBlockEntity core, boolean hurt); // line 105

```


#### development.BioshipVoiceCommands

Source: `src/main/java/org/Azkerash/create_bioships/development/BioshipVoiceCommands.java` (71 lines).

```java
public final class BioshipVoiceCommands

  public static void register(RegisterCommandsEvent event); // line 17

```


#### BioshipVoice

Source: `src/main/java/org/Azkerash/create_bioships/BioshipVoice.java` (60 lines).

```java
final class BioshipVoice

  enum Utterance; // line 13

  static void play(FeedingMouthBlockEntity mouth, CompoundTag capsule, Utterance utterance); // line 17

  static void letter(FeedingMouthBlockEntity mouth, CompoundTag capsule, char letter); // line 25

  private static void emit( FeedingMouthBlockEntity mouth, CompoundTag capsule, BioshipVoicePalette.Profile profile, boolean hurt); // line 29

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

Source: `src/main/java/org/Azkerash/create_bioships/client/MouthRenderer.java` (91 lines).

```java
public final class MouthRenderer implements BlockEntityRenderer<FeedingMouthBlockEntity>

  public MouthRenderer(BlockEntityRendererProvider.Context context); // line 22

  private static ModelResourceLocation model(String name); // line 23

  public static void models(ModelEvent.RegisterAdditional event); // line 28

  public void render( FeedingMouthBlockEntity mouth, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 33

  private static void part( FeedingMouthBlockEntity mouth, String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay); // line 66

```
