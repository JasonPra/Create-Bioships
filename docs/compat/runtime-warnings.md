# Runtime log findings: NeoForge 1.21.1

The supplied client log uses NeoForge 21.1.266, Sable 2.0.6, Create 6.0.10,
Aeronautics 1.3.2, Biomancy Reforged 2.9.11.0, GeckoLib 4.9.3 and Veil 4.3.2.
It reaches a playable integrated-server session; these messages are not a loader crash.

## Compatibility resources included here

- Biomancy's `decomposing/block_of_slime_rear` recipe references the optional
  `sons_of_sins` mod without a load condition. Our recipe override retains the
  original recipe and adds `neoforge:mod_loaded`, so an absent optional ingredient
  is not parsed as an unknown registry item. No substitute recipe is invented.
- `assets/minecraft/atlases/blocks.json` adds a sprite alias from the missing
  `biomancy:item/ravenous_claws` name to Biomancy's existing weapon inventory icon.
  It does not copy or alter Biomancy artwork or replace the other atlas sources.
- A Biomancy language addition supplies `sounds.biomancy.fleshkin.burp`.
- Veil's `light/indirect_sphere.comp` uses compute shaders, shader-storage buffers,
  `readonly` and `writeonly`, but its original resource has no explicit GLSL version.
  The supplied Intel UHD / OpenGL 4.6 log rejects `readonly` as reserved. Our
  resource override declares `#version 430 core`, matching the shader's features.
  Its otherwise unchanged shader is from FoundryMC/Veil 4.3.2 under LGPL-3.0;
  see `VEIL-LICENSE.txt` and <https://github.com/FoundryMC/Veil>.
  A standalone OpenGL 4.3+ compilation check passes on Mesa llvmpipe. The user's
  Intel driver and full Veil preprocessing still require a client relaunch check.

## Dependency/development messages retained

- Missing dependency refmaps explicitly identify a development environment.
  IDE terminal-feature warnings, vanilla command ambiguities and NeoForge's
  `union:` development resource URLs do not identify a Bioships source error.
- Veil's missing Iris mixin target occurs without the optional Iris integration.
  The JetBrains `ScheduledForRemoval` annotation lookup is also dependency-side.
- Three Biomancy geometry files declare `format_version: 1.21.20` while this
  GeckoLib version advertises 1.12.0. These are upstream model-version warnings;
  their artwork is not copied or silently relabelled in this project.
- Sable explicitly warns about its renderer/light-storage interaction with
  Flywheel. The emissive shader's missing `Sampler2` warning also belongs to the
  renderer stack. These messages should remain visible when diagnosing graphics.
- The unsupported UDP `SO_BROADCAST` option is a Netty/Sable configuration warning.
- A movement packet for a missing Sable grid means the client lacks the referenced
  sub-level when it arrives. A late packet after cargo removal is one possibility;
  a lifecycle/synchronization fault is another. This log alone cannot distinguish
  them, and no logging filter or speculative networking workaround is installed.
- The two missing vanilla sound-event messages are not Bioships registrations.

The recipe exception in this log is an unknown optional item, not the previously
reported `Expected BEGIN_OBJECT but was STRING` exception. That exact exception
and its surrounding stack trace are not present in this supplied log.

## Sable plot-holder shutdown compatibility

Sable 2.0.6's `PlotChunkHolder.isReadyForSaving()` always returns false because Sable owns plot serialization. If such a holder enters vanilla `ChunkMap.scheduleUnload`, Minecraft repeatedly schedules the already-completed save callback and can spin indefinitely while stopping a world with a live ship. `SablePlotUnloadMixin` removes only these managed plot holders from the pending vanilla unload map; terrain chunks retain the normal save/unload path. Sable remains responsible for saving and restoring its grids. Bioship view/simulation tickets are also released on `ServerStoppingEvent` before world shutdown begins.
