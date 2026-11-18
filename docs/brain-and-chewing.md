# Mouth chewing and brain foundation

## Mouth

The teeth are centered horizontally and vertically on the mouth face. Idle teeth meet
in a closed pose. Accepted food starts a three-second open/bite/close sequence and
vanilla eating sounds. Hand feeding, menu insertion and normal container insertion
share the same change detection. Simulated or rejected input does not animate.
Transporting food out does not cancel an already started chew, and moving a stack
between slots without adding food does not restart it. Dead or unassembled mouths
do not begin chewing; death stops an ongoing animation.

Animation timing is component state in the sublevel-owned component store. The mouth
block only supplies synchronized visual phases and short scheduled animation updates;
it has no continuously running block-entity ticker.

## Brain

The brain is a physical, player-interactable layer for one sublevel-owned behavior
inventory. It has no block entity or per-block inventory. Cards and port selection
are saved in `create_bioships:brain` on the grid. `SubLevelBrains` owns that data and
cached topology; `BrainShape` handles placement geometry; `BrainInventory` is only a
container view. Brain block logic and the menu are under `components.logic.brain`.
Registration remains in `ComponentRegistry`.

Player placement permits a first brain block anywhere on the grid. Further blocks
must connect face-to-face, stay inside a 3×3×3 volume and not exceed 27 blocks.
Disconnected secondary brains cannot be placed. If external assembly imports
multiple disconnected groups, only one valid group is active and exposes a port.
Topology is rebuilt after edits, rather than scanned every tick.

One brain block enables one behavior-card slot, up to 27. The backing inventory has
27 persistent positions, so reducing physical capacity never destroys cards.
Inactive slots reject new cards but existing overflow cards remain retrievable.
Removing all brain blocks leaves the data on the original grid; rebuilding a brain
restores access. The inventory does not travel inside a removed brain block item.

The default port is centered on the brain's north-facing exterior. Normal growth
keeps that default centered. Right-click an exterior brain-block face with a Create
wrench to pin the one port to that exact block face. It cannot move to non-brain
blocks or an internal brain-to-brain face. Breaking or covering the selected face
chooses an exposed replacement. Shift-wrench retains normal component removal.
Blocks and wrenches bypass the inventory interaction so they can still be used on
an access face.

## Behavior cards

Blank Behavior Cards are nonstackable, craftable graph documents. Their initial
`BehaviorGraph` contains a version and empty Nodes, Connections and Variables lists.
The `create_bioships:behavior_cards` item tag controls which items fit the inventory.
The current scope is storage and multiblock foundation: it does not execute behavior
cards, provide factory behaviors, interpret Lua, or include a visual node editor.
Those systems will be added as a separate feature.

## Development fixture

The navigator includes **Brain memory and chewing** at x=768. Existing development
saves gain the fixture on startup. START assembles a 27-block brain, a core and a
mouth, with one blank behavior card in grid memory. Hunger is paused. The enclosure
uses indestructible glass, a bedrock floor, a double door, command-block buttons and
an explanation hologram.

Commands:

- `/brain_test start`: assemble the specimen.
- `/brain_test reset`: restore its unassembled state.
- `/brain_test feed`: insert one pork chop to repeat the chewing demonstration.
- `/brain_test status`: report capacity and port direction.

Regression coverage includes accepted/simulated/menu food input, closed idle, death,
full capacity and dimension bounds, native placement rejection, exact Create-wrench
port movement, one active group, card filtering, saved graph/port state, overflow
retrieval and data retention after removal. Native client checks cover the animated
mouth, eating-sound events, card insertion through the actual menu, exact port
relocation and client-side maximum-size prediction.

## Connected brain appearance

The grid's brain controller synchronizes the active group and its access face. A cached client mesh draws a continuous folded surface over the connected cells, with a central fissure and Biomancy's fibrous-flesh material. World-placed inactive blocks keep a normal tissue model. Internal faces are omitted; topology is rebuilt only when the group changes.

The single port uses the cleaned administrative panel's flesh and bone frame, with a dark programming display, blinking `>_` cursor and logic glyph. Moving the port preserves the existing wrench and inventory behavior. Procedural surfaces share the existing hurt shader, and wound decals are projected onto the rounded surface.
