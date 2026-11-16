# Behavior and body module boundaries

The controller selects work, planners propose it, and actuators execute it. Reading a sensor or debug snapshot must not execute a new behavior.

```text
[Vision / memory / needs / material stock]
                   |
[Read-only adaptation proposals + available targets]
                   |
[Brain + weighted goal queue -> selected work]
       |                |               |
 [Crawl route]    [Assimilation]    [Adaptation]
       |                |               |
 [Support]         [OrganMotion]    [MaterialCost]
       |                |               |
 [Sable physics]   [Shared storage / digestion]

[Health / trust / poison] -> [Cosmetic feedback]
[Paid/native larynx] -> [Per-mouth voice capsule]
                    -> [Mouth animation + modulated sound]
```

For the complete class structure beneath each subsystem, see the
[text reference](systems-reference.md) or [paginated PDF](systems-reference.pdf).


| Module | Owns | Does not own |
| --- | --- | --- |
| `BioshipBrain` | Combining eligible proposals, action selection and action reason | Material debits, block copying or route search |
| `GoalQueue` | Bounded ordering, stable tie rules and target deduplication | World eligibility checks or task execution |
| `BioshipNutrition` | Pressing-food threshold, known fuel value and food-related task classification | Navigation, cargo mutation or independently selecting prey |
| `MaterialCost` | Affordability, atomic multi-material payment with rollback, refund and transfer | Where accounts live or which goal should spend them |
| `MaterialLedger` | Plain inventory quantities and immutable snapshots | Minecraft block entities, recipes or behavior |
| `BioshipAdaptationPlanner` | Read-only candidate generation and feasibility from a copied adaptive-state snapshot | Spending resources or placing/moving blocks |
| `BioshipAdaptation` | Persistent adaptive state and executing the selected funded proposal | Its own competing target selector or independent resource policy |
| `BioshipStructure` | Reading loaded Sable cells through one shared adapter | Deciding whether a block should be eaten, repaired or moved |
| `BioshipSupport` | Common mass-weighted attachments, support footprint and hypothetical relocation checks | Moving the core or paying for stronger legs |
| `BioshipVision` / `TargetMemory` | Visibility and observation lifetime | Choosing a goal or moving toward it |
| `GroundRoute` / `BioshipCrawl` | Collision-aware routes, movement progress/failure and supported gait | A second independent supply target selector |
| `BioshipAssimilation` | Batch progress, conserved swallowed stock and wreck-job continuation | Hunger thresholds or a second resource accounting policy |
| `OrganMotion` | One conserved Sable block transport and its complete block entity | Choosing which component is valuable |
| `NavigationDebug` | Snapshots of the actual controller and route state | Replanning just because the overlay is visible |

## Rules for adding behavior

1. Propose work using current facts and needs; do not add another nearest-target loop to an actuator.
2. Keep visibility, physical reach and route feasibility distinct. Seeing through glass does not permit grabbing or walking through it.
3. Give every goal a stable key. Omit it when it is completed, expired, unreachable or no longer useful; the queue refresh removes it.
4. Put food urgency and task-purpose rules in `BioshipNutrition`, so storage, hunting, scavenging and interruption use the same definition.
5. Express new multi-material prices with `MaterialCost` and debit the source account once. A transfer is not a newly generated resource. Refund a failed placement explicitly.
6. Keep planner snapshots immutable. Revalidate immediately before execution because observations and placement clearance may have changed.
7. Reuse support geometry in rendering and movement. Do not assume that the core remains the body's center or that the structural anchor is its underside.
8. Save job ownership and carried stock before removing originals. Safety interruption must preserve conservation; it must not duplicate or discard cargo.

`GoalQueueTest` and `MaterialCostTest` exercise the reusable parts without a running game. GameTests cover the Minecraft/Sable adapters, inventories, mass, organ regeneration, native hostile AI, both glass mazes and nutritional interruption. The supplied-world reproduction checks the same code against an existing save rather than replacing its terrain, light or resources with a special fixture.

## Digestion, energy and recovery

These modules extend the same proposal/account boundaries. A consumer must use the shared storage account rather than adding core and stomach quantities together: stomach material queries already include the core and connected sacs.

| Module | Responsibility | Reuse boundary |
| --- | --- | --- |
| `BlockEnergyTable` | Pure block-ID upkeep policy, structural-unit demand and digestive capacity | No world access; independently unit tested |
| `BioshipMetabolism` | One metabolic step and ordered emergency-fuel fallback | Core supplies cadence; metabolism does not select behavior or navigate |
| `BioshipEnergy` | Translate loaded body cells into a cached demand snapshot | Both metabolism and digestive growth use this adapter |
| `BioshipStorage` | Core-first deposits, external-first spending, connected sac access and overflow | One `MaterialCost.Account` for every funded behavior; raw meals remain food |
| `EmergencyReserve` | Reserve targets and discretionary spending eligibility | Policy stays separate from inventory transfers |
| `EngineRoomSizing` / `EngineRoomLayout` | Capacity policy and shared shell/bay geometry | No resource debits or block mutations |
| `BioshipEngineRoomPlanner` | Complete room, digestive and leg investment quote, including future upkeep | Read-only clearance and load checks with explicit failure reasons |
| `BioshipEngineRoom` | Saved room capacity and atomic paid expansion | Does not select its own behavior |
| `BioshipRoomOwnership` | Attribute exterior attachments to their connected room | Shared by blueprint translation; no world mutations |
| `BioshipDigestiveGrowth` | Read-only placement, capacity, material and leg-support planning | Returns a proposal; does not place organs or debit materials |
| `DigestiveSystem` | Distinct mouth/stomach assignments and physical intestinal networks | Feeding and storage use the assigned network rather than an arbitrary stomach |
| `BioshipRecovery` | Native precursor preference and safe nonessential-component sacrifice | Recovery returns materials through the conserved transport and recycling systems |
| `BioshipAquatic` | Submersion, breathing, saved air and water propulsion intent | Does not grant flight or choose ordinary food/collection targets |
| `BioshipRighting` | Reachable foothold support and recovery torque | Shared by bracing and physics; no airborne correction without support |

The core remains the lifecycle coordinator. Energy prices belong in the table, resource ownership belongs in storage, and anatomical feasibility belongs in the read-only planners. New callers should reuse these services instead of duplicating scans or maintaining parallel stock counters.

The [architecture checkpoint review](architecture-review.md) records remaining orchestration coupling and the recommended next refactor. Complete affordability checks currently precede expansion; this is not yet a multi-step project escrow.

## Tissue, voice and feedback

`BioshipTissue` owns native body material and exact embedded-host restoration. Armor registry entries remain independent.

`BioshipLarynx` owns voice eligibility and conserved per-mouth combination; `BioshipVoicePalette` is pure A-Z articulation policy; `BioshipVoice` dispatches mouth-position utterances. The client sound player owns envelopes, and `MouthRenderer` owns articulated jaw/lip rendering. `BioshipVoiceCommands` is an operator audition adapter, not a resource source.

`BioshipParticleSurface` supplies exposed world-space points. Trust feedback owns its shared cooldown; `TrustParticleBudget` bounds render frames and spawn count; poison feedback follows poison status. Damage feedback belongs to accepted body injury and its synchronized flash state. Cosmetic consumers do not choose tasks or debit materials.
