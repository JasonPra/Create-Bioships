# Create Bioships — core block foundation

Minecraft 1.21.1 / NeoForge 21.1.266, Java 21. Dependencies are pinned in `gradle.properties`.

The foundation block is `create_bioships:living_core`, using its original texture
and open/closed models. It is closed-eyed as a normal world block or inventory item. When
assembled into a Sable structure it slowly wakes once, then repeats the original double blink: 4 ticks closed,
4 open, 3 closed, and 109 open. Raised eyelid rims remain visible while awake.
Creature grids cannot be disassembled.

Direct placement by a real player automatically assembles the core through Sable after placement
finishes. Commands, automation and fixture construction leave cores unassembled. The core has a synchronized inspection block entity, but no leg animation, growth, or creature behavior tree.
Assembly uses the dependency tools. Sable user data stores the amoeba creature type and a persistent
creature UUID. Players cannot place a native physics assembler on tagged grids. A small guard at the
shared native disassembly entry also protects against assemblers that were present before assembly.
Ordinary untagged grids are unaffected.

If a grid has multiple cores, it retains its original primary core and detaches every extra core
into a separate one-block Sable object using the native assembly API. Other blocks stay on the
original grid. Each detached core receives its own persistent creature identity. This also applies
when additional cores are placed on an existing creature.

In survival the core is crafted in Biomancy's Bio-Forge → Machines: 1 Living Flesh, 16 Flesh Bits,
8 Elastic Fibers, 4 Exotic Dust, 1 Eye of Ender, and 4 nutrients. Mining, explosions, Silk Touch,
and other ordinary block drops cannot recover it. Pick-block yields nothing. Creative players
can still obtain it from its dedicated tab.

Build: `./gradlew build`. Integration check: `./gradlew runGameTestServer`.
The development-only checks cover blinking, the loaded native recipe, empty drops, grid identity,
assembler placement cancellation, protected disassembly, unaffected ordinary grids, and both
initial multi-core assembly and adding cores to an existing creature. It is excluded from the mod JAR.


## Development world and repeatable regression fixture

Create a world with the **Core Development (Bedrock Superflat)** world type. New worlds have
sea-level ground: bedrock at Y=62, a noise pattern of the five indestructible brick/cobblestone
variants at Y=63, and spawn/test bases at Y=64. The pattern is a per-chunk world-generation
feature across the entire floor, including newly explored areas. Fixtures replace their own
flooring with bedrock. Existing saved worlds retain their generator and fixture elevations.
 A roofed glass enclosure
is generated automatically near spawn. A hollow inverted pyramid of 49 closed, unassembled cores
balances on a narrow oak pole. Native Honey Glue joins the pyramid only; the pole is excluded.

The labeled command blocks and buttons in front run:

- `/core_test start` — assemble the glued pyramid through the native assembly API.
- `/core_test reset` — remove the test grids and glue, then rebuild the unassembled fixture.
- `/core_test status` — show whether the test is ready or running.

Start refuses a second run until Reset, preventing duplicate fixtures. Reset can be repeated without
recreating the world. Fixture chunks stay loaded for repeatable simulation. On a dedicated server,
set `enable-command-block=true`; single-player command blocks are supported normally.
Future regression fixtures should follow the same enclosed, visible, start/reset pattern.

## Creature-grid placement whitelist

`BlockPlacementRules` collects every registered `biomancy` and `create_bioships` block ID after
registration. Both early right-click cancellation and the placement-event backstop use it.
Forbidden block items are rejected before placement on creature surfaces. Individual extra block
IDs and nested block tags can be added through the `create_bioships:creature_placeable` data-pack
tag; its default list is empty. Ordinary world placement remains unrestricted.

Validation for this batch: build and all eight live tests passed, including two complete native
command-block Start/Reset cycles, all 84 loaded Biomancy block IDs, player-only assembly and early
placement denial. A fresh preset startup also passed with bedrock terrain, roof and 49 unassembled
cores. Visual inspection is left to the user.

## Equipment and source packages

Symbiotic Goggles (`bioship_goggles`) use Create's wearable goggles integration. The Ossified
Wrench (`bioship_wrench`) uses Create's wrench interactions with the restored equipment models.
Both are available in the creative tab and through their Biomancy Bio-Forge recipes. Neither
this wrench nor Create's own wrench can rotate or collect the core, including while sneaking.

Production packages are organized by responsibility: `core` contains the core and grid rules,
`equipment` contains the two item classes, `client.equipment` binds their display models,
`development.testing` owns the test fixtures, world setup, state, and commands, and `mixin` holds the native disassembly guard.
The root mod class only registers content and wires lifecycle hooks.

## Grid health and combat

`core.BlockHealthValues` registers every loaded block ID in a tree map at 10 HP per block (air
contributes zero). `core.SubLevelHealth` stores current/max health, permanent death, and cause
of death on the Sable sublevel, not on the core. It counts the grid once and then updates from
Sable's block-change notification. Adding blocks preserves damage; removing blocks reduces
both current and maximum HP. Health is clamped to zero and adding blocks cannot revive a dead grid.
`CoreGogglesDisplay` synchronizes an inspection snapshot for Create's goggles overlay.

`combat` adapts Minecraft hostile targeting and native player/projectile attacks to that pool.
Projectile impacts must hit a real sublevel block, so arrows through holes do not hit an enclosing
box. `client.combat` owns a ten-tick red shader overlay on the grid's block meshes. The shader
compiles in headless EGL; its final appearance still needs an in-game client check.

## Sequential combat regression enclosure

The development preset also contains a glass enclosure centered at x=1024, z=1024. The fixture-only glass
uses the vanilla glass model/texture, has no item or creative entry, and resists mining, explosions,
and boss block destruction. New dev worlds have a seeded mixed-stone walkway over bedrock;
the test arenas themselves have bedrock floors. Existing worlds retain their floor layout.

The combat fixture starts as an unassembled packed-flesh pillar with its core nine blocks above
its base. Its START button assembles a fresh subject at **1 HP**, then spawns each registered
`MONSTER`-category mob type one at a time. Death advances to a fresh subject. A ten-second
simulation-time timeout records failure and advances instead of stalling the suite. This is a
regression runner, not a claim that every specialized vanilla/modded hostile AI has passed.
Each enemy's outcome remains saved after the suite automatically restores the sleeping fixture.

- `/combat_test start` — run the sequential hostile suite.
- `/combat_test reset` — stop and restore the unassembled subject; retain results.
- `/combat_test status` — show progress and result count.
- `/combat_test results` — show a compact summary with **Copy failures** and **Copy all results** chat links. Console callers receive plain per-enemy outcomes.

The explanation hologram sits between START and RESET. `/tick rate` accelerates these simulation
windows as usual. An existing dev world receives the combat fixture when next opened; the
new decorative terrain applies only when a dev world is first set up.

### Updated development tools

`HostileCombatIntegration` attaches native targeting from the `MONSTER` registry category.
Breeze type restrictions and brain-memory attacks, Warden anger, slime contact damage, and the
Giant's missing native melee goal have dedicated compatibility handling. Dragon phase targeting
uses its native controller. This is automatic integration, not a guarantee for arbitrary mod AI.

Combat trials now last **200 ticks (10 seconds)**. Results distinguish target acquisition from
failure to land a lethal hit. The failure hologram next to the buttons is right-clickable to
expand/collapse and page through failed entity names. Summons and lingering effect clouds are
cleaned between trials. Piglin/Hoglin test subjects cannot convert into different mob types.

The combat arena is relocated to `(1024, 1024)`. Its marked Ender Dragon is constrained by the
complete root-and-part bounding volume; ordinary dragons outside the test are unaffected.
Development buttons and the five decorative floor blocks now use protected, itemless variants
with vanilla models/textures. Arena floors stay bedrock. New-world decorative generation uses
the protected variants. Existing nearby combat fixtures are relocated on startup.

The **Development Test Navigator** is supplied on joining a dev world and appears in the creative
tab. Right-click it for the read-only inventory menu, or use `/dev_tests` to open that menu and
`/dev_tests give` to obtain the tool. It teleports to the core-separation or isolated combat test.

Dangerous Mobs file 6093131 was temporarily loaded in an isolated test environment. Its enemies
use the AXOLOTLS spawn category; they are outside the deliberately selected MONSTER registry
scope. Cheat Killer acquired a target but missed the trial deadline; its global procedures later
exhausted the isolated server's heap. The temporary dependency, JARs, and test world were removed.

### Tissue wounds and eye weak points

The core and the exposed front of Land/Aquatic Eyes take **1.5×** incoming damage. Damage still
uses the grid's shared health pool. Core and eye hurtboxes have additional F3+B outlines. Eye
components share blink and wound behavior under `components.logic.eye`; the aquatic variant changes
only the eyeball palette and shape.

Weapon and projectile hits leave a per-block, per-face wound. The struck eye remains visually
closed until its wound expires; other eyes keep blinking. Wounds fade over 1,200 simulation ticks
(one minute at normal tick rate). Original arrows and tridents remain embedded and follow the
moving grid, retaining their item data. Attachment data survives entity saves and is sent to new
observers. Tridents eligible for pickup are returned as their original item when the attachment expires.

The navigator now includes the **Core Arrow Regression** enclosure at x=128, z=0. Its native
three native dispensers wait for wake-up, then fire at the core, land eye, and aquatic eye.
The fixture repeats those impacts after death to verify corpse wounds. `/arrow_test start`, `/arrow_test reset`, and
`/arrow_test status` control it; START/RESET command-block buttons and an explanation hologram
are provided. Reset restores the sleeping, unassembled fixture and refills all three dispensers.

Symbiotic Goggles use their own tooltip icon with room above the text. Both their inventory sprite
and dedicated worn-model texture are **16×16**, matching Create's original goggles textures.

### Optional Epic Fight bridge

`compat.epicfight` owns all optional hooks, with its mixins in a separate subpackage. Epic Fight
is not a compile or runtime requirement. When installed, its weapon colliders test each intersected
block's collision boxes and select the ordinary, core, or eye hurtbox. Empty space inside a grid
does not count as tissue. Multiple collider samples from one swing are consolidated to one target
per grid, favoring a struck weak point. Damage, wounds, and projectile behavior remain shared
with the vanilla combat adapter.

The compatibility regression runs its additional checks only when Epic Fight is actually loaded.
The supplied `epic-fight-21.17.3.1-mc1.21.1-neoforge.jar` is kept in an isolated testing `mods` folder,
not added to the project's dependency list or included in the mod JAR. A final in-game visual
check of attack animations, wound overlays, and the revised goggles remains separate from the
headless collision/damage checks.

### Player attacks and component removal

Vanilla sword sweeps retain the weapon's native sweep volume and damage. Intersected creature
blocks route to one damage target per grid, excluding the already-struck primary grid so its body
and eye adapters cannot multiply the hit. Normal entity victims keep vanilla sweep handling.
Damage adapters stay anchored to tissue rather than drifting under vanilla knockback.

Shift-right-click with the **Symbiotic Wrench** extends Create's normal pickup interaction to
non-core blocks on a creature grid. Create owns removal and loot; removed items pop into the
world at the struck block instead of entering the inventory directly. Capacity updates preserve
existing damage, so removing too much capacity can kill the creature. Existing Create
block interactions outside creature grids are unchanged. The core cannot be collected with a wrench.

After death, ordinary block breaking is allowed again and the core can be removed
by hand with its normal mining time; axes and pickaxes mine our components faster.
**The core never drops an item**, alive or dead. Other components keep their normal drops.

### Corpse decay and linked fragments

`core.CreatureDecay` slowly removes exposed blocks from dead creature grids without block or
inventory drops. Living creatures are excluded. A family's initial block count sets a shared
cadence targeting roughly three in-game days, with at most one block removed per second.
Unloaded fragments do not catch up by instantly disappearing when loaded again.

There are exactly **two decay groups**. All ordinary blocks across the entire remains family must
be gone before any registered final-group component is eligible. The final group initially contains
`living_core`, `land_eye`, and `aquatic_eye`. Once that group is eligible, any of its components may
rot even while several others remain. Exposed blocks are preferred within the eligible group.

Add future components through `CreatureDecay.registerLastToRot(ResourceLocation)` during common
setup. `lastToRotRegistry()` returns an immutable snapshot. Saved per-fragment block-ID summaries
use the registry when deciding eligibility, so future registrations also apply to saved remains.

A native Sable split preserves a persistent `create_bioships:remains_family` UUID and the death
state on detached fragments, including fragments without a core. Each retains independent Sable
physics. Saved summaries keep unloaded ordinary fragments from allowing the final group to rot
early; permanent deletion removes their membership. Once the last block of a fragment is due to
rot, the grid is removed through Sable rather than leaving an empty physics body.

The combat-results hologram also provides **−5s / +5s** controls for the per-enemy trial limit.
The setting ranges from 5 to 120 seconds and defaults to 10 for existing/new saves. It is saved
with the fixture and takes effect on the next trial; an active trial retains its original deadline
and timeout label. Changing the setting does not clear results. Existing dev worlds receive the
controls when the arena's chunks are ready, without resetting or rerunning their test.

The Ossified Wrench's normal player right-click path now handles grid actions before ordinary
block-use handlers. All Biomancy/own non-core blocks on a Sable sublevel support Create pickup;
ordinary Create interactions outside grids are retained. Both normal and sneak wrench actions
on creature grids preserve existing health through temporary block removal/restoration, so a
rollback cannot heal the grid. Core pickup/rotation stays disabled. Invisible combat adapters
also disable vanilla's building-obstruction flag, allowing placement on all six core faces.

Reaching full health on a living grid clears wound state from the core and both eye variants.
The wound system saves the cleared state and sends removal updates to clients, allowing normal
blinking to resume. Other tissue wounds retain their normal fade lifetime. Full-health recovery
is deferred until a wrench operation finishes, so temporary block removal/restoration cannot heal
eye injuries while the grid remains damaged. Dead grids are never treated as healed or revived.

Health now persists an injury balance independently of block capacity. For example,
58.5/60 becomes 48.5/50 after removing a ten-HP block, then returns to 58.5/60 when replaced.
Neither action erases damage. Removing enough capacity can kill the grid, and adding blocks
cannot revive it. Temporary removal/restoration is committed atomically so a rolled-back wrench
operation neither heals nor kills it. Explicit healing reduces the injury balance and triggers
full-health eye recovery; capacity changes do not count as healing.


## Shared state, potions, and Vital Readout

`core.SubLevelData` defines and initializes saved grid variables, including health state,
hunger (5/5 initially), and trust (0/100 initially). Trust remains a placeholder;
hunger uses the temporary fixed-rate metabolism described below. Health calculations and changes
remain in `SubLevelHealth`. Changing block capacity preserves accumulated damage and can
kill a creature if its remaining capacity cannot sustain that damage. Dead grids cannot revive.

The **Vital Readout** is a mounted display block available in the creative tab. It reuses
the core's inspection implementation, showing the same health, hunger bar, trust, and effects.
Status effects use vanilla icons and remaining durations.

Poison, wither, regeneration, instant healing, instant harming, and resistance operate on
one shared grid through the combat adapter's vanilla effect logic. Splash potions, lingering
clouds, and effects applied through core/eye hurtboxes resolve to that shared owner, avoiding
multiple doses from overlapping adapters. Ongoing effects are saved across adapter recreation.
Instant effects change health immediately rather than leaving a persistent icon.

The development world includes a roofed potion-test enclosure near (256, 0), with a dispenser,
a core-and-flesh subject, a Vital Readout, and START/RESET buttons. The navigator includes it.
Existing development worlds receive the missing fixture without resetting their other arenas.

- `/potion_test start` — run all six native dispenser cases, ten seconds per case.
- `/potion_test reset` — restore the unassembled subject and dispenser.
- `/potion_test status` — inspect the current case and saved results.
- `/potion_test results` — print results with a copy-to-clipboard link.

The resistance case uses two identical adult zombies with no weapon and the same attack
attribute. It records one native melee hit before the potion, removes that zombie, dispenses
Resistance I, and spawns a second zombie for exactly one hit. The second zombie is then
removed and the measured damage values are compared for the expected 20% reduction.
Each stage times out rather than fabricating an attack if the mob cannot reach the subject.
The front wall has indestructible, hand-operated double glass doors for goggle inspection;
use `/potion_test reset` to rebuild an existing enclosure with the new doors. These test-only
blocks remain outside the creative inventory.

The fixture automatically returns to an unassembled state when finished and retains its results.
Regression checks cover every dispenser case, shared core/readout data, one-dose routing across
multiple hurtboxes, effect persistence, nonlethal poison, lethal wither, and permanent death.


## Combat timeout corrections

The ordinary `biomancy:primordial_flesh_blob` is excluded from combat trials because its
native goals seek food and avoid threats rather than attacking. The hungry variant remains.
With the supplied Epic Fight build this leaves 42 trials.

- Melee targeting anchors near the attacker's feet on an exposed tissue face, avoiding a
  floating target that prevents Epic Fight's wither-skeleton swing selection.
- Summoned attackers receive their target and orientation before joining the world. The
  wither ghost uses its native six-block horizontal summoning offset.
- The enclosure resets at sea level or higher, with a 64-block ceiling. Phantoms start in
  the air. This avoids their sea-level flight anchor colliding with the old low roof.
- Drowned receive a water trial and start closer to the subject. Sea-level placement keeps
  their native swim-up goal from replacing attack navigation; the world's clock is unchanged.
- Dragons charge toward the creature using a native phase rather than following orbit nodes
  centred on the End fight. Optional Epic Fight hooks allow that phase only for grid targets.
  Native head/neck and wing attack boxes are checked against individual tissue collision
  shapes, using vanilla contact damage through the normal damage adapter and cooldown.
- Epic Fight swept weapon contact remains valid if its visibility ray first hits the same
  creature's tissue after a charge enters it. World obstacles and other grids still block
  the hit, and a covered eye/core is not granted a weak-point hit through other tissue.

Use `/combat_test reset` to rebuild an existing enclosure at the corrected height. The
navigation tool follows its saved position. The existing START/RESET and duration controls
are unchanged; no extra debugging commands were added.


## Bare-bones hunger

Living creature grids start at **5/5 hunger**, lose **1 point per 1,200 loaded game ticks**
(one in-game minute), and at zero take **1 HP of native starvation damage every 80 ticks**
(four seconds). Starvation is lethal on every difficulty, records `starve` as the cause of
death, and never revives a dead grid when hunger is changed. Raising hunger above zero stops
starvation. Existing hunger values above five are clamped during migration without changing
health, trust, or effects.

`SubLevelData` owns the saved value, `hungerPaused`, `hungerDrainTicks`, and `starvationTicks`
inside `create_bioships:state`. `SubLevelData.hungerPaused(body, true)` freezes both timers;
setting it to false resumes the saved remainder without offline/catch-up damage. The core
and Vital Readout show five food icons, the numeric value, and a paused label when applicable.
The separation, combat, arrow, and potion fixtures pause hunger on their subjects.

`SubLevelHunger` owns the temporary timing policy, evaluated once per loaded second. A later
saturation-based policy can replace it without moving shared data or display responsibilities.
Saturation remains future work; mouth/stomach feeding is described below.


## Components and stomach shell

`components.ComponentRegistry` owns simple gameplay block, block-item, and block-entity
registration. `ComponentDefinition` supplies a constructor and optional common-setup behavior
steps through `withBehavior(...)`. Register a definition once: its item and creative entry are
wired automatically. Model/blockstate assets retain their stable registry IDs and load on the
client; optional gameplay setup runs after registry binding. Behavior implementations live
under `components.logic.core`, `.eye`, `.readout`, and `.stomach`. Shared sublevel systems
remain separate. Development-only fixture blocks retain their dedicated testing registry.

`create_bioships:stomach` is a full-block internal-organ shell based on the old stomach's
folded panel. It directly reuses Biomancy's packed-flesh and flesh textures, with the panel
recessed within the block envelope so surrounding tissue can meet it flush. It supports six
facing directions, native Create wrench rotation, ordinary self-drops, the component creative
tab, and the existing 10-HP block contribution. Its passive digestion and inspection inventory
are described below.


## Stomach capacity, healing, and inspection access

A creature has 5 base hunger capacity. One stomach adds 5 capacity, for a maximum of 10,
without supplying food. Placing a second stomach on that creature is rejected. Existing or
command-assembled extra stomachs do not stack capacity; no imported blocks are deleted.
Capacity is initialized from the block census and updated on component changes, including
same-health block replacements. Losing the last stomach clamps excess food to the new limit;
replacing it never refills food.

Passive regeneration requires `ceil(maximum hunger × 0.90)`: 5/5 without a stomach, 9/10
with one. While damaged and above that threshold, it restores 1 HP per 80 loaded ticks.
Potion healing/regeneration remain independent of hunger, as in vanilla. Hunger pause freezes
passive regeneration as well as drain/starvation. Regeneration progress is saved. Saturation
and additional food costs remain future work.

The stomach has a Create inspection block entity showing acid, digestion, and queued food.
Its inventory menu is inspect-only and opens only from the panel face (`facing`);
other faces remain available for ordinary block interactions. This mod's core/readout/stomach and future Create
inspection displays require the exact symbiotic-goggles item worn in the head slot. A shared
whitelist check is applied only to our block entities in Create's client overlay pipeline;
engineer/aviator goggles are not permitted for these displays, while other mods' displays
retain their normal access rules. Core/readout bars show the current capacity dynamically.

### Persistent impact routing and development kit

`SubLevelImpactAdapter` routes wounds and projectile contacts using the persistent
`create_bioships:living` identity flag, independently of core presence. Health death does not
clear this flag: corpse wounds and embedded projectiles remain supported while the mob-target
adapter is removed. Legacy amoeba grids acquire the flag automatically.

The development preset equips Symbiotic Goggles and grants a wrench on first entry. Its
navigator is hidden from the creative tab and pinned to the rightmost hotbar slot; moving,
dropping, death, and `/clear` preserve it. `DevelopmentLoadout` owns these protections.

## Passive digestion and intake

- Stomachs generate 1 acid per loaded tick, up to 1,000, starting with 200. They require a living-creature-flagged Sable sublevel that is alive, but no
  mouth or intestine connection to digest existing input. World blocks, ordinary grids, and
  dead creature organs remain inactive and retain inventory, acid, and processing progress.
- Native Biomancy decomposing recipes supply component outputs, ingredient quantities, and
  processing times. Acid costs 100 times the recipe's nutrient cost, clamped to 50–1,000.
  Food items also produce one nutrient paste per recipe.
- The stomach contains 9 queued-food slots and 18 component/paste slots. Its GUI shows acid,
  digestion progress, and inventory. Players and hoppers cannot insert food directly into it.
  Full output storage stalls completion without consuming the input or rerolling output.
- A hungry living creature consumes one stored paste over 32 loaded ticks, restoring 4 hunger
  up to capacity. Full creatures retain paste. Hunger capacity remains 5 base plus 5 for one stomach.
- Feeding Mouth (`feeding_mouth`): right-click with food to feed one item; sneak-right-click
  offers the held stack. Empty-hand right-click opens its 9-slot intake UI. Native hopper
  insertion is supported; server slots reject non-digestible inputs.
- Fleshy Intestine (`fleshy_intestine`) joins directly touching faces in all six directions.
  Cached, bounded routes deliver one item per mouth every 8 ticks. Broken pipes and full
  destinations preserve food at the source. Routes never cross between separate Sable grids.
- Mouth/stomach item drops preserve their inventories and stomach processing state through
  vanilla block-entity item data, including wrench removal.

### Visible digestion test

The development preset adds a digestion enclosure at x=384, z=0, with indestructible glass,
a double door, START/RESET command buttons, an explanation hologram, and a navigator entry.
`/digestion_test start`, `/digestion_test reset`, and `/digestion_test status` control it.
START assembles the subject, seeds four pork chops in its mouth, and seeds one isolated
living stomach/core grid as a control without mouth or intestine connections. PASS requires transported digestion, component/paste
production, and hunger reaching capacity. RESET restores the unassembled setup.

### Eye support and corpse feedback

Both eye variants require a full solid block behind their mounting face; the shared exclusion
array rejects eye components as supports. Placement, assembly, and support changes enforce this.
Wounds force eye blocks closed and pause their blinking until healed or expired. Corpse hits
retain the red impact flash as well as wounds and embedded projectiles.
The arrow fixture now fires from the front at visible, front-mounted eyes; dispensers clear
away after each round for inspection. Existing worlds can apply that layout with `/arrow_test reset`.

Let it Walk! is pinned to CurseForge file **9540966** (project 1780503), version **0.1.5**,
for NeoForge 1.21.1. It is included as a required runtime dependency; no creature walking
behavior is wired to it in this batch.

The mouth and stomach screens share `client.components.OrganScreenStyle`: Biomancy flesh
texturing and ornamental frame details, recessed tissue-colored slots, bone text, and bordered
acid/digestion gauges. Each screen owns its layout. Normal wrench clicks bypass the organ
menus so Create rotation remains available.

Validation: 34 native regression tests passed with Let it Walk installed, including the
optional Epic Fight run; a final run without Epic Fight also passed all 34. Both themed
menus and the front-facing arrow fixture were inspected in the Minecraft client.

## Stomach gene serums

Load **Longevity Gene Serum** or **Efficiency Gene Serum** into Biomancy's Bio-Injector, then
right-click a stomach on an alive, living-creature-flagged grid. Injection works from any face;
only opening the inventory requires the panel face. These gene serums reject mob/self injection.
They are available in the component creative tab (`longevity_gene_serum`, `efficiency_gene_serum`);
crafting recipes have not yet been specified.

- Longevity: one dose per stomach adds 5 hunger capacity. Current food is retained, not refilled.
- Efficiency: three doses yield 2, 3, and 4 nutrient paste per food meal, respectively, plus a
  20%, 30%, or 35% chance of one additional native recipe component. Ordinary outputs stay intact.
- Native Bio-Injector consumption, vial returns, cooldown, and injection animation are reused.
  Incompatible, maxed, unassembled, and dead targets consume no dose.
- `StomachGenes` owns compatibility, saved gene state, and effects; `BioInjectorIntegration` owns
  tool mechanics. Simple serum identities/items are registered through `ComponentRegistry`.
  Genes are saved with stomach inventory and processing state in block-item drops. The stomach
  menu and symbiotic-goggles tooltip show installed genes.

Gene validation: all 39 regression tests passed, including native injector doses/vials, capacity
without free food, rejected duplicate/dead/world injections, gene persistence, guaranteed paste
yields, and deterministic statistical checks of the one-component bonus.
