# Component setup

Simple gameplay blocks use one registry, `components.ComponentRegistry`. Their constructors
and behavior live under `components.logic.<component>`. Shared sublevel data and services
remain under `core`; client rendering remains under `client`.

A `ComponentDefinition` declares the ID and block constructor. Registration binds the block,
creates the matching block item, and includes it in the component creative tab. Its optional
`withBehavior(ComponentLogic::setup)` callbacks run during common setup after registries bind.
These callbacks delegate to logic classes rather than implementing behavior in the registry.
Model and blockstate resources use that same ID and are loaded through Minecraft's client
resource pipeline; server setup never imports a client renderer.

Keep complex registration separate only after discussing its responsibility with the project
owner. Development fixture blocks retain their existing testing-only registry.

## This batch

- Moved existing component registrations without changing block/item/block-entity IDs.
- Moved core, eye, and readout component classes under `components.logic` and updated callers.
- Added the `stomach` shell, using the old folded-panel geometry and Biomancy tissue textures.
- Its geometry remains inside one full block. It supports six facings, Create wrench rotation,
  self-drops, the creative tab, and the existing per-block health table.
- No stomach GUI, inventory, digestion, feeding, or old growth logic was introduced.
- Checked the model in the native Minecraft client beside packed flesh and ordinary flesh.

Combat entity/behavior setup is a separate follow-up batch.
