# Create Bioships — core block foundation

Minecraft 1.21.1 / NeoForge 21.1.266, Java 21. Dependencies are pinned in `gradle.properties`.

The only registered gameplay block is `create_bioships:living_core`, using its original texture
and open/closed models. It is closed-eyed as a normal world block or inventory item. When
assembled into a Sable structure it opens, then repeats the original double blink: 4 ticks closed,
4 open, 3 closed, and 109 open. Creature grids cannot be disassembled.

There is no automatic assembly, block entity, leg animation, AI, growth or other creature system.
Assembly uses the dependency tools. Sable user data stores the amoeba creature type and a persistent
creature UUID. Players cannot place a native physics assembler on tagged grids. A small guard at the
shared native disassembly entry also protects against assemblers that were present before assembly.
Ordinary untagged grids are unaffected.

If a grid has multiple cores, it retains its original primary core and detaches every extra core
into a separate one-block Sable object using the native assembly API. Other blocks stay on the
original grid. Each detached core receives its own persistent creature identity. This also applies
when additional cores are placed on an existing creature; ordinary unassembled world blocks do
not assemble themselves.

In survival the core is crafted in Biomancy's Bio-Forge → Machines: 1 Living Flesh, 16 Flesh Bits,
8 Elastic Fibers, 4 Exotic Dust, 1 Eye of Ender, and 4 nutrients. Mining, explosions, Silk Touch,
and other ordinary block drops cannot recover it. Pick-block yields nothing. Creative players
can still obtain it from its dedicated tab.

Build: `./gradlew build`. Integration check: `./gradlew runGameTestServer`.
The development-only checks cover blinking, the loaded native recipe, empty drops, grid identity,
assembler placement cancellation, protected disassembly, unaffected ordinary grids, and both
initial multi-core assembly and adding cores to an existing creature. It is excluded from the mod JAR.
