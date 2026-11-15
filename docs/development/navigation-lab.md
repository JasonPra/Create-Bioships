# Bioship Navigation Lab

The mod includes a **Bioship Navigation Lab** world type. Its data pack resources are packaged in the normal mod JAR; no separate save download is required.

Create a new world, choose **Creative**, enable commands, then select **World → World Type → Bioship Navigation Lab**. On the first login the courses receive creator-owned hatchlings, and the player receives Symbiotic Goggles and a bioship wrench. Ordinary worlds are unaffected. The lab is saved normally and is not rebuilt every time you open it.

| Course | Hatchling start | Prize | Conditions |
| --- | --- | --- | --- |
| Flat glass maze | `16 80 0` | `34 80 0` | Three alternating openings, glass sides and ceiling, solid floor. |
| Elevation glass maze | `16 80 128` | `37 85 128` | Three rising floor levels, enclosed glass corridors, tall grass, a raised prize, and a hatchling with mouth, stomach, tubes and an additional workstation. |

The prize is a Bio-Forge. Both courses use `NavigationLab.course`, the same deterministic geometry used by the real-physics GameTests. Glass permits sight but blocks the body and grasping tentacles. The roof and closed sides prevent climbing over or walking around the maze. The elevated prize has a solid illuminated plinth so darkness does not obscure it at the start.

Use `/tp @s 12 84 118` to observe the elevation course. Equip the goggles and press **F3+B** for sight cones, ray results and navigation waypoints. The hatchlings start moving on first login, so reset before observing another run:

```text
/bioship_lab reset
```

Reset reconstructs both course areas and their test creatures. It removes their old physics bodies and moving cargo, then creates new hatchlings owned by the command's player. It works only in the lab world type. A server-console reset rebuilds the courses; hatchlings are seeded on the next player login. Keep valuable player builds outside the course areas.

## Automated verification

```sh
./gradlew build
./gradlew runGameTestServer
```

`hatchling_navigates_visible_component_through_roofed_glass_maze` verifies real traversal of all three alternating openings, collision clearance, intact glass and completed assimilation. `wide_hatchling_navigates_enclosed_glass_maze_with_steps_and_tall_grass` repeats the challenge with attached organs and changing elevations. These tests do not teleport the creature or supply a hand-written route.

Other regression tests cover the side-ledge/tree snag, working storage-sack attachment and food conservation, passive healing from biomass, permanent paid leg upgrades, preflight hull/leg budgeting, heavy grounded walking, single-core creeper retreat, and the packaged world-preset/prey registries.

## Grounded movement and resources

Normal legs can lift the body **one extra block** above their usual supported stance. They remain grounded; climbing several stair levels is not flight. A permanent leg upgrade consumes **8 Flesh Bits, 4 Elastic Fibers and 2 Bone Fragments**. Each tier adds one block of reach and visibly thicker legs; up to three tiers are available. Strength uses the body's actual Sable mass relative to living-hull mass, rather than treating every block as weightless.

A new dinghy normally needs two leg tiers. Before its hull starts growing, the creature checks the entire combined budget: the 64 growth biomass, remaining leg costs, and the frame's 8 Bone Fragments and 12 Elastic Fibers. With no existing upgrades this is normally **80 flesh biomass, 12 Bone Fragments and 20 Elastic Fibers**, plus any separately paid intestinal extensions. The legs strengthen first, then hull construction starts. An incomplete budget is not partially spent. The goggles show current leg tier, load, extra lift and remaining leg costs.

A completed hull can still walk before it receives levitite amalgam. Insufficient leg strength prevents it from moving an unsupported load until it can afford the necessary upgrades. Automatic route upgrades are paid only when the stronger legs produce a complete usable route. Additional tubing likewise reuses existing tubes/private stock before buying new tissue.

Healing is automatic while the creature is not starving and has stored Flesh Bits: one bit repairs four health, up to once per second. Missing hull blocks use those reserves too. A full food meter without stored tissue does not provide free regeneration.

Wandering traders and trader llamas are edible hunting targets. Owner/whitelist protections still apply. A hatchling with a connected mouth and stomach hunts for missing growth and leg materials even at full satiety, without needing a cockpit hunting control. Queued meals and accessible food stores take priority; ordinary growth hunting avoids zombies. Whole hunted beef carcasses provide one Bone Fragment and one Elastic Fiber in addition to their native flesh recipe; other prey use the native decomposition yields. A single-core creature retreats from nearby creepers; larger creatures retreat if there is no reachable connected tissue where the core can escape the blast radius. This tactical retreat takes priority over the low-health trust override.

Automatic organ configuration keeps the mouth on an accessible front face, moves an obstructing organ out of that position, and routes reusable intestines over the core so the eye can move onto a higher connected vantage. Existing tubes can be used below the usual underfloor routing layer; net new tissue still costs materials. Walking animation follows measured body displacement, not merely an outstanding AI destination. Idle hatchlings without a growth budget retract their legs; raised/heavy bodies can keep stationary supporting legs.
