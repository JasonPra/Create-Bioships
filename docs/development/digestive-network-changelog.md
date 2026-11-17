# Shared digestion and large stomachs

## Gameplay changes

- Mouths are food inputs, not exclusive stomach partners. Multiple mouths can feed one stomach
  group through actual face-connected intestines. Additional mouths do not force stomach growth.
- Intestines only connect inventories and digestive organs. Stomachs perform decomposition and
  digestion; they produce construction materials and nutrients using the existing recipes.
- Stomachs share queued meals only through direct face contact. Intestines do not make separated
  stomach inventories share their queued meals. A working intake serves the direct-contact group.
- Food goes to available member queues. Balancing transfers complete future meals, preserves item
  components and inherited-food provenance, and leaves active recipe inputs and progress intact.
- Native storage sacs are discovered through actual physical intestine links, including manually
  built links without historical route assignments. Cutting a link removes access immediately.
  Shared sacs and their contents are counted once.
- Isolated stomachs can move beside an existing functioning group to avoid redundant tubing.
  With 27 existing stomachs, the amoeba can arrange a solid 3×3×3 cube in a suitable footprint.
  This does not grant free stomach blocks or override space, support, resource, or pinned-organ rules.
- A formed cube retains all 27 authoritative inventories: 243 slots, up to 15,552 ordinary stackable
  food items. Formation and dismantling do not copy or replace those inventories.
- Formed cubes move as one real cargo body using the shared sinking animation. Placement checks
  the whole footprint and support; a replacement intake route must be affordable. A blocked
  destination rolls back the whole structure. If the original footprint is also blocked, the real
  cargo remains intact until it can be restored.
- Movement preserves each member's queue and digestion progress. Pooled health includes carried
  organs and counts restored flesh hosts once.
- A distinct intake ring and outward player panel identify the structure. Breaking the cube resets
  its member roles. The inspection screen provides nine read-only inventory pages and aggregate
  digestion statistics. Shared material totals are not multiplied by the member count.
- Formed stomachs digest 25% faster (five work units per four ticks). Ingredient quantities,
  nutrient costs, and recipe yields are unchanged. Overlapping cubes cannot stack the bonus.

## Runtime and architecture

- Reusable, Minecraft-independent classes own direct-contact groups, physical intake routing,
  bounded meal distribution, solid-cube detection, and efficiency math.
- The amoeba layout planner chooses arrangements; transport owns movement and state handoffs.
  Formation detection, visual ports, and inventory inspection have separate responsibilities.
- Topology is cached by Sable body revision, invalidated on block/property changes and explicit
  cargo handoffs. Group functionality and connected storage lists are compiled into the snapshot.
  Ordinary digestion does not rebuild the world graph on every query.
- Existing mouth/intestinal route tags remain construction hints. Physical connections determine
  whether food and storage are actually reachable. Additional branches preserve other intakes;
  rerouting an intake may reuse its own retired tubing.
- No intestine or network object owns a replacement inventory or generates resources.

## Validation

Automated coverage includes many-mouth intake, paid tube construction/reuse, broken links,
queue capacity and conservation, inherited meals, isolated-stomach relocation, cube formation and
breakage, overlapping-cube rejection, complete-group movement/rollback, pooled health in transit,
all inventory pages, large menu values, physical storage access, and cached topology invalidation.
The integration suite also exercises existing creature behavior outside this feature.
Regression plots retain their established physics coordinates, with habitat-overlap avoidance;
otherwise adding tests could place a later recovery fixture underneath the End habitat roof.

All main/client, GameTest, and soak-test source sets are compiled by `check`. The six orientations
and four stomach roles have valid model variants. Graphical client review of the new panel and
models has not been performed; server tests do not substitute for visual review.

Final automated result: **74 unit tests and all 184 live GameTests passed** using
`python /workspace/work/run_bioship_build.py check runGameTestServer`.
