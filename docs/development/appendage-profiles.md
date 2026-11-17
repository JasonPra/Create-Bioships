# Leg capabilities and mouth-bound graspers

Walking legs and food/component graspers have separate responsibilities. Legs provide support, grip and movement; mouth slots own gathering jobs. A level-one feeding mouth provides one grasper slot, while its larynx upgrade is level two and provides two. Jobs save their mouth/slot association. A bare amoeboid core retains one bootstrap uptake slot so it can acquire its first mouth. The existing four walking legs are not consumed by gathering jobs.

`BioshipGraspers.registerProvider` is the extension point for future explicit grasper components. Providers return named mouth/body slots and root positions, without selecting behavior or spending resources. Ordinary mouth slots are anatomical capabilities of the paid mouth, not separately created material blocks. The collector has a bounded 32-job safety limit.

Movement capability data lives at:

```text
data/create_bioships/bioship_locomotion/tentacles.json
```

A datapack can replace that resource and use `/reload`. No Java rebuild is needed for its tuning values:

```json
{
  "walls": true,
  "ceilings": true,
  "grip_reach": 1.7,
  "comfortable_clearance": 0.7,
  "direct_distance": 24,
  "quick_nodes": 220,
  "search_nodes": 1600
}
```

Invalid profiles are rejected with a warning; the built-in bounded profile remains available. Future leg-component modules can use `LocomotionProfiles.registerAdapter(profileId, matchesCreature)` to select their own data. This separates geometry/navigation from appendage-specific capability policy. New mechanics still require implementation and tests; data tuning does not.

Navigation checks a supported direct corridor first. If blocked, it searches diagonally with whole-body swept clearance, preferring room around obstacles. After a bounded quick search, it permits expanded wall/ceiling routes. Clear waypoint stretches collapse into straight segments; there are no spline curves or deliberate lateral weaving. Surface contacts require three reachable terrain contacts and do not grant flight.

`BioshipSurfaceContacts` owns terrain contact sensing. `GroundRoute` owns route planning. `BioshipCrawl` and the physics actuator own stance and motion. `TentacleSuckers` shares cosmetic sucker geometry between the leg and grasper renderers.

## Future airship constraints

The locomotion legs are the intended lineage for future flight appendages. Airship behavior is still disabled. Keep one internal mouth in the future hull, with extra exterior mouths for hunting; an internal mouth does not need to migrate outside merely because hunting starts. This is a recorded design constraint, not enabled airship behavior.

## Procedural locomotion attachments

`create_bioships:amoeba_leg` supplies a face-mounted locomotion socket. Its `facing` state points away from the host block. `BioshipLegGrowth` quotes and pays growth; it never animates limbs. Each socket costs 4 Flesh Bits, 2 Elastic Fibers and 1 Bone Fragment. Four sockets take priority over a larynx upgrade. The first native stomach conversion supplies a one-time, saved material allocation for that initial set; replacements retain the normal cost and emergency-reserve check.

`BioshipSupport.Leg` carries the attachment root, resting foot, outward block face and a separate bare-core bootstrap marker. Body-mounted sockets and future `BioshipSupport.registerLegProvider` adapters supply these records. Once four sockets exist, the body uses those attachments instead of its temporary layout. A provider's coordinates are relative to the core's structural origin; registered providers must exist on both logical sides.

`BioshipLegAnchors` owns up to 32 world-space feet. Terrain shape intersections produce tip contact boxes and surface normals. Planted feet remain fixed until a new step is needed; five-tick step arcs transfer alternating groups. Missing contacts retract the endpoint. `BioshipCrawl` consumes settled contacts for support, and the renderer converts world feet through the current Sable render transform. A cubic curve starts along the attachment face and ends against the sensed surface normal. Swimming retains a separate paddling stroke. No animation library or prerecorded walking clip is required.

F3+B displays green 0.24-block foot/contact volumes. Attacking graspers display red 0.4-block tip volumes only during a lash's damaging sweep; swept collision between consecutive tip positions prevents tunneling. The shaft itself does not deal damage. F3+P remains the independent route/behavior display.

## Solid mounting invariant

A leg socket requires a directly adjacent host with a full supporting face opposite its outward `facing`. Edge/corner contacts, thin exposed intestines, other leg sockets and unsupported air do not qualify. Growth, player placement, hypothetical body moves and organ transport share this check. Transport revalidates the host at commit; if it disappeared, the real leg cargo is released rather than attached to empty space or converted into free materials.

Transitional procedural legs also choose exposed full body faces; they cannot use a thin pipe as a root. Invalid sockets are excluded from the mounted-leg support set. The anatomy audit can propose conserved relocation of legacy invalid sockets even before digestion exists and with fewer than four sockets. Moving a supporting body block may not introduce additional invalid mounts. These rules do not replace the pending reconfiguration-stability work or graphical animation QA.

Paid mounting tissue is proposed by `BioshipMountGrowth` when there are no free
legal leg sites. It preserves existing gut tubes while encasing them, or grows
Packed Flesh beside the structural body. Its quote includes remaining supports,
legs, strength upgrades and future lung materials, plus the emergency reserve.
The body queue uses `GROW_SUPPORT`; lower-priority paid upgrades preserve that
budget. Occupied-slot stashing and the temporary-support transition remain open.

Anatomy audits resolve a socket's planted contact by its attachment root and outward face. Invalid sockets omitted from the support layout cannot shift the contact indices of valid sockets; bootstrap contacts are excluded from this association. This preserves the distinction between core-only locomotion and physical leg blocks.

## Amoeba component merging

`ComponentMergeAnimation` supplies one bounded visual envelope for absorption and redeployment. Client cargo rendering scales around the carried block's center without changing logical poses or collisions. Swallowed components remain visible during approach and sink at arrival; internal moves sink and re-emerge, including movement without a tissue corridor. Internal transport no longer renders a carrying arm. Core relocation uses the same envelope.

Adjacent digester/decomposer fusion now retains the real digester as timed cargo before committing the stomach and its inventories. Missing destination organs release that real cargo instead of creating a free stomach. The established physical cargo trajectory is retained independently of the new rendering treatment. Animation appearance still requires current-client observation; headless curve and transaction tests are separate evidence.
