# Digestion, emergency recovery and water movement

This follow-up changes gameplay after the modular behavior checkpoint. The earlier checkpoint log records its own implementation and validation.

**Current scope:** the user has paused the airship stage for a redesign. Airship code, controls and models are preserved as dormant prototypes. Engine-room expansion and staged hull/flight progression described below are not active gameplay. See [prototype status](../development/airship-prototype.md). The hatchling physiology, storage, movement and recovery changes remain active.

## Energy and digestion

- Ordinary attached blocks contribute structural energy units. The core, finished stomachs, mouths and intestines contribute zero. Unconverted Decomposers and Digesters still contribute. The shared table converts every 64 consumer units, rounded upward, into one food unit of upkeep per second.
- A bare core needs no energy. Adding only zero-upkeep digestive organs does not change that. Other attached blocks activate upkeep.
- Digestive capacity scales from the same demand: one mouth/stomach pair per four units of demand, rounded upward. Every mouth has a distinct stomach and a physically intact intestinal connection. A spare mouth cannot borrow a stomach already assigned to another mouth.
- Stomachs process their own queues in parallel. Native Biomancy decomposition outputs go to shared construction storage. Meat digestion also produces one Nutrient Paste, retained in that stomach and burned for 20 energy when there is room in the food meter. Burning fuel does not award trust.
- New digestive pairs require paid materials, safe placement and adequate leg strength. A mouth costs 8 Flesh Bits, 4 Elastic Fibers and 2 Bone Fragments. A stomach costs 16/8/4, including its initial paid intestine material. Extending an existing stomach to a new mouth also funds new intestine stock.

## Preserved engine-room prototype (inactive)

- The engine room gains paired side bays as the required digestive capacity grows, keeping its central aisle. Width is saved with the core and reflected in the blueprint.
- Expansion quotes include the new tissue's upkeep, required digestive organs, frame materials, stronger legs and emergency stock. Geometry and load checks reject obstructed or unsupported expansion; a failed placement rolls back its material payment.
- Expansion preserves unchanged control panels and moves displaced sensory eyes rather than destroying them. Existing exterior attachments move with their connected room during blueprint relocation.
- F3+P reports outstanding flesh/fiber/bone needs alongside behavior and routes. Prototype room-capacity data is retained for future integration.
- These are complete affordability checks, not a multi-step escrow system. Explicit investment reservations are part of the next orchestration refactor described in the architecture review.

## Storage and reserves

- The core holds up to 256 resource units, with capacity reserved for its emergency stock: 32 Flesh Bits, 16 Elastic Fibers and 8 Bone Fragments.
- Construction products enter the core first. Overflow goes into physically connected Storage Sacs; any remainder stays as pending stomach output. Raw meals remain food, rather than becoming construction stock merely because they are inside a sac.
- All connected stomach products, sac material partitions and extractable native sac resource slots participate in one shared account. Spending uses external stock before drawing down the core. Refill moves available resources back into the core's emergency bank.
- When collective working storage fills, the creature can invest paid flesh, fiber and bone in another sac and its intestine connection, strengthening its legs first if necessary. It can borrow banked resources for that investment and replenish them from available external stock.
- Optional sensory growth, armor and additional organic digestive capacity check the emergency budget before spending. The dormant hull no longer reserves or consumes construction materials.

## Recovery and locomotion

- If essentials are missing, usable native Bio-Forge/Decomposer/Digester components already attached or observed nearby take precedence over fabrication.
- Otherwise, safe nonessential body components can be physically reclaimed into resources at a loss, then spent on digestive recovery. This recovery path works before digestion is restored. Fractional salvage yields survive save/load; the creature cannot get a full new resource from each partial component.
- Reclamation preserves essential organs, structural connectivity and supporting body attachments. It does not destroy an irreplaceable body merely because the combined recovery recipe cannot yet be funded.
- Grounded overturned hatchlings can brace against reachable solid footholds and apply righting torque, including recovery from a fully inverted pose. They cannot generate ground support while airborne.
- Tentacles can propel a sufficiently supported body through water without granting flight. Air is finite. Every attached mouth is checked against the actual fluid surface; one exposed mouth lets the entire creature breathe. When all mouths are submerged it seeks air, then takes drowning damage after its air runs out. A mouthless core uses its own position for breathing.
- Blueprint floor validation distinguishes room floors from exterior sac and intestine attachments at the same height, so new storage does not falsely invalidate a connected room layout.

## Reusable modules

See [module boundaries](../development/modules.md). `BlockEnergyTable` owns pure upkeep policy; `BioshipEnergy` adapts body cells; `BioshipMetabolism` performs one metabolic step; `BioshipStorage` owns shared transactions; `EmergencyReserve` owns reserve policy; `BioshipDigestiveGrowth` and `BioshipRecovery` produce read-only plans; `BioshipAquatic` and `BioshipRighting` handle water and supported recovery respectively. The core coordinates lifecycle and cadence.

## Airship stage suspended

- `AirshipPrototype` prevents automatic hull/engine construction, buoyancy incorporation, flight, piloting, blueprint reconstruction and control input.
- Older saved stage data cannot bypass the boundary. Existing block IDs, assets and serialized prototype state are retained; worlds are not wiped or rebuilt.
- The hatchling stops reserving the abandoned frame/hull budget and stops turning useful stored products into inactive staged-growth biomass. Salvage can populate emergency core storage even before a stomach is restored.
- Prototype controls are inert, remote throttle commands are rejected, the prototype door is hidden from the creative tab, and goggles label prototype components as unimplemented.
- Stage-dependent tests are preserved using `@DeferredAirshipTest`. The active suite adds stocked/legacy-core and disabled-control tests. This changes the test scope deliberately; unavailable stages are not reported as tested working gameplay.

## Pending follow-up

All-mouth hunting and paid permanent grasper growth are not implemented in this checkpoint. They should use the canonical queue and explicit investment interfaces rather than add another targeting or spending loop. See the [architecture review](../development/architecture-review.md).

## Validation

- Java 21 Gradle build with deprecation/removal diagnostics passed.
- All **36 unit tests** and **95 required active NeoForge GameTests** passed. The final active suite includes both real-physics glass mazes, native hostile targeting, storage and salvage conservation, supported righting, swimming/drowning, independent digestive pairs, zero-upkeep producers, resource-paid storage and disabled-stage/control boundaries.
- **23 airship-only scenarios are preserved but not registered**, using `@DeferredAirshipTest`. This is a deliberate scope change for the unavailable stage, not a claim that the dormant prototype passes its old gameplay scenarios.
- All **205 main resource JSON files** parse. Models and textures are unchanged. The runtime JAR excludes GameTests, deferred-test annotations, uploaded worlds and private probes.
- A salvage fixture now resolves the live core after core relocation and checks exact shared-resource deltas. It no longer reads a transferred-out core or expects resources to enter the disabled hull-growth counter.
- `git diff --check` passed. No interactive client rendering verification was needed for unchanged model/texture assets; the new gameplay checks run in the real Sable/NeoForge engine.
