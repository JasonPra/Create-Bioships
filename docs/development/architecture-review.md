# Architecture checkpoint: retain infrastructure, replace orchestration incrementally

The airship stage is now dormant for a substantial user-directed redesign; its code and assets are preserved. See [prototype status](airship-prototype.md).

A full project rewrite is not justified. The published modular checkpoint has passing regression coverage and useful rendering, Sable integration, recipes, storage, conservation and navigation infrastructure. Preserve it as the recovery point.

## What extraction has and has not fixed

Separate policies now own material transactions, energy prices, emergency reserve targets, digestive capacity, aquatic motion and supported righting. Engine-room geometry, read-only expansion quotes and expansion execution are also separate. These boundaries make those rules reusable.

The controller still does too much. `BioshipBrain.choose` assesses autonomy, reads live perception and anatomy, collects candidates and selects work. Configuration, route and attack consumers can call it again during the same server tick. `LivingCoreBlockEntity` still coordinates body construction, cockpit controls, physics intent and lifecycle. `DigestiveSystem` still combines assignment, route planning, intestinal mutation and feeding transfers. Splitting files alone does not eliminate these dependencies.

The multi-stomach change exposed several concrete assumptions: meal totals were read from one arbitrary stomach, hunting used one compatibility mouth, external storage was not attributed to its connected room during relocation, and existing tests assumed all resources stayed private to one stomach. Correct these assumptions and retain regression tests before extending hunting further.

## Recommended next boundary

1. Capture immutable perception, anatomy, resources and needs once per server decision tick. Use a revision to identify the body/resource state that produced each quote.
2. Evaluate the behavior tree and weighted goal queue once against that snapshot. Consumers read the chosen decision; they do not independently trigger a fresh selection.
3. Let action executors validate and execute commands. They report progress, obstruction, completion or failure back to the controller. Conserved cargo remains owned by the transport job across interruptions.
4. Represent a multi-step investment explicitly: strengthen legs, expand housing, obtain or fabricate organs, fund tubes, then connect and verify. A complete affordability quote is not an escrow reservation. Protect or deliberately borrow the remaining project budget, then expose that change in the debug snapshot.
5. Move digestive assignment and tube route planning into a read-only anatomy service. Keep food transport and paid topology mutation as separate executors.
6. Add all-mouth hunting and resource-funded permanent graspers against these interfaces, with secondary prey selected from the canonical queue rather than a competing limb-local target selector.

## Checkpoint rules

- Freeze additional feature work until this integration checkpoint passes all tests.
- Preserve the published passing checkpoint and the current work; do not delete or reset either.
- Keep pure policy tests, resource conservation tests and in-engine obstacle/physics tests. Tests must reflect ordinary terrain, light and inventory constraints.
- Record pending features explicitly. All-mouth hunting and new permanent grasper growth are pending; independent mouth/stomach feeding and expanded room budgeting do not by themselves implement those behaviors.
- Replace one boundary at a time and verify existing behavior before proceeding. A new file is not, by itself, evidence that a responsibility has been separated.
