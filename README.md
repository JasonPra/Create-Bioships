# Create Bioships — core block foundation

Minecraft 1.21.1 / NeoForge 21.1.266, Java 21. Dependencies are pinned in `gradle.properties`.

The only registered gameplay block is `create_bioships:living_core`, using its original texture
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

Create a world with the **Core Development (Bedrock Superflat)** world type. A roofed glass enclosure
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
components share blink and wound behavior under `components.eye`; the aquatic variant changes
only the eyeball palette and shape.

Weapon and projectile hits leave a per-block, per-face wound. The struck eye remains visually
closed until its wound expires; other eyes keep blinking. Wounds fade over 1,200 simulation ticks
(one minute at normal tick rate). Original arrows and tridents remain embedded and follow the
moving grid, retaining their item data. Attachment data survives entity saves and is sent to new
observers. Tridents eligible for pickup are returned as their original item when the attachment expires.

The navigator now includes the **Core Arrow Regression** enclosure at x=128, z=0. Its native
dispenser fires at an assembled core. `/arrow_test start`, `/arrow_test reset`, and
`/arrow_test status` control it; START/RESET command-block buttons and an explanation hologram
are provided. Reset restores the sleeping, unassembled fixture and refills the dispenser.

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
non-core blocks on a creature grid. Create owns removal, drops, and pickup; capacity updates do
not count as damage. Current health is preserved up to the reduced maximum. Existing Create
block interactions outside creature grids are unchanged. The core cannot be collected with a wrench.

After death, ordinary block breaking is allowed again and the core can be removed immediately
by hand. **The core never drops an item**, alive or dead. Other components keep their normal drops.

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
