# Bioship Navigation Lab

The mod includes a **Bioship Navigation Lab** world type. Its data pack resources are packaged in the normal mod JAR; no separate save download is required.

Create a new world, choose **Creative**, enable commands, then select **World → World Type → Bioship Navigation Lab**. On the first login the courses receive creator-owned hatchlings, and the player receives Symbiotic Goggles and a bioship wrench. Normal daylight cycling and mob spawning remain enabled; the lab does not freeze ordinary survival conditions. Ordinary worlds are unaffected. The lab is saved normally and is not rebuilt every time you open it. Courses do not use persistent arena force-loading; the living ships use the same view/simulation loading as in ordinary worlds.

| Course | Hatchling start | Prize | Conditions |
| --- | --- | --- | --- |
| Flat glass maze | `16 80 0` | `34 80 0` | Three alternating openings, glass sides and ceiling, solid floor. |
| Elevation glass maze | `16 80 128` | `37 85 128` | Three rising floor levels, enclosed glass corridors, tall grass, a raised prize, and a hatchling with mouth, stomach, tubes and an additional workstation. |

The prize is a Bio-Forge. Both courses use `NavigationLab.course`, the same deterministic geometry used by the real-physics GameTests. Glass permits sight but blocks the body and grasping tentacles. The roof and closed sides prevent climbing over or walking around the maze. The elevated prize has a solid illuminated plinth so darkness does not obscure it at the start.

Use `/tp @s 12 84 118` to observe the elevation course. Equip the goggles and press **F3+B** for sight cones and ray results. Press **F3+P** for routes, target markers and the behavior/priority hologram; the path debugger needs no goggles. The hatchlings start moving on first login, so reset before observing another run:

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

Other regression tests cover the side-ledge/tree snag, working storage-sack attachment and food conservation, passive healing from biomass, permanent paid leg upgrades, supported body clearance, single-core creeper retreat, and the packaged world-preset/prey registries.

## Grounded movement and resources

Normal legs can lift the body **one extra block** above their usual supported stance. They remain grounded; climbing several stair levels is not flight. A permanent leg upgrade consumes **8 Flesh Bits, 4 Elastic Fibers and 2 Bone Fragments**. Each tier adds one block of reach and visibly thicker legs; up to three tiers are available. Strength uses the body's actual Sable mass relative to living-hull mass, rather than treating every block as weightless.

Airship hull construction, cockpit controls and levitite progression are currently dormant. These courses exercise the active hatchling, its real supported movement and paid leg upgrades. The creature does not reserve materials for the retired hull layout. See [prototype status](airship-prototype.md).

Healing is automatic while the creature is not starving and has stored Flesh Bits: one bit repairs four health, up to once per second. Missing hull blocks use those reserves too. A full food meter without stored tissue does not provide free regeneration.

Wandering traders and trader llamas are edible hunting targets. Owner/whitelist protections still apply. A hatchling with a connected mouth and stomach hunts for missing growth and leg materials even at full satiety, without needing a cockpit hunting control. Queued meals and accessible food stores take priority; ordinary growth hunting avoids zombies. Whole hunted beef carcasses provide one Bone Fragment and one Elastic Fiber in addition to their native flesh recipe; other prey use the native decomposition yields. A single-core creature retreats from nearby creepers; larger creatures retreat if there is no reachable connected tissue where the core can escape the blast radius. This tactical retreat also applies to a healthy single-core creature.

Automatic organ configuration keeps the mouth on an accessible front face, moves an obstructing organ out of that position, and routes reusable intestines over the core so the eye can move onto a higher connected vantage. Existing tubes can be used below the usual underfloor routing layer; net new tissue still costs materials. Walking animation follows measured body displacement, not merely an outstanding AI destination. Idle hatchlings stop their walking cycle; bodies can keep stationary supporting legs for balance and can keep a valid wall grip after collecting a component.
