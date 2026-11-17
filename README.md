# Create Bioships — core block foundation

Minecraft 1.21.1 / NeoForge 21.1.266, Java 21. Dependencies are pinned in `gradle.properties`.

The only registered gameplay block is `create_bioships:living_core`, using its original texture
and open/closed models. It is closed-eyed as a normal world block or inventory item. When
assembled into a Sable structure it opens, then repeats the original double blink: 4 ticks closed,
4 open, 3 closed, and 109 open. Creature grids cannot be disassembled.

Direct placement by a real player automatically assembles the core through Sable after placement
finishes. Commands, automation and fixture construction leave cores unassembled. There is no block
entity, leg animation, AI, growth or other creature system.
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
