# Create Bioships — core block foundation

Minecraft 1.21.1 / NeoForge 21.1.266, Java 21. Dependencies are pinned in `gradle.properties`.

The only registered gameplay block is `create_bioships:living_core`, using its original texture
and open/closed models. It is closed-eyed as a normal world block or inventory item. When
assembled into a Sable structure it opens, then repeats the original double blink: 4 ticks closed,
4 open, 3 closed, and 109 open. Disassembly resets it to closed.

There is no automatic assembly, block entity, leg animation, AI, growth or other creature system.
Assembly/disassembly use the dependency tools; the block listens to Sable's native move callbacks.

Build: `./gradlew build`. Integration check: `./gradlew runGameTestServer`.
The development-only check covers the idle block, real assembly, the blink cycle, and native
Simulated/Aeronautics disassembly. It is excluded from the mod JAR.
