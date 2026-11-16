# Bioship behavior controller

`BioshipBrain` owns the current action and target. `BehaviorPriority` ranks proposals; `BioshipPriorities`, vision, defense and scavenging supply candidates. Crawling, flight, assimilation, wreck collection, growth and tentacle attacks execute the selected action rather than choosing competing targets independently.

The selector handles these branches:

1. Dormant/dead organisms do no autonomous work. Approved reconstruction is an exclusive transaction until completion, matching the blueprint controller's existing body lock.
2. Mandatory creeper retreat and critical-injury retreat interrupt ordinary work. High pilot trust can override injury retreat, but cannot make an unprotected single core fight a creeper.
3. Active ally rescue, immediate defense and in-progress component transfers complete their controlled actions. Rest and authorized trusted flight controls take precedence over optional gathering.
4. Weighted needs compete using actual digestive readiness, satiety, queued nutrition, connected food stores, growth materials, repair reserves and leg/hull costs.
5. Long-term construction, body reinforcement and excess-storage reclamation follow; wandering or idle is the fallback.

Within the weighted branch, a need always beats a goal. Urgency dominates distance; small distance/value adjustments break ties. A small preference for the existing target avoids switching back and forth between equivalent candidates. Satisfied needs disappear on the next evaluation. Targets lose eligibility after three real seconds without renewed sight, including the selected target. Fresh remembered coordinates can guide a physically clear grasp. Expiry invalidates the candidate and clears its old route; it does not force a particular replacement action. The controller does not inspect inventories through walls.

Missing mouth/stomach components are urgent. Useful additional components outrank routine growth when food is at least 50; below 50, accessible nutrition takes precedence. The threshold uses satiety, not construction biomass. Spare components are emergency fuel only when functional digestion exists. Accessible food and growth supplies compete according to the current shortage. Useful dead-ship tissue ranks above live hunting for that shortage; optional empty containers cannot distract the creature from a digestive prerequisite. Hunting stops competing when queued meals or connected food stores already cover immediate intake. Wreck collection and dropped-block recycling retain their existing material losses and do not grant free nutrition or trust.

Hatchling core scouting is target-aware and reassesses after organ transfers. Once the hull is complete, core movement is restricted to sheltered internal tissue; surface eyes track targets from exterior or interior mounts. Core swaps preserve flight intent. Room coordinates and workshop bounds use the fixed structural anchor, while repair memory and reconstruction maps use core-relative cells; blueprint conversion preserves that distinction after a swap. The active core remains pinned during room reconstruction.

Digestion, reserve-funded healing, sensing and body maintenance remain passive services. Physical controls remain physical. Each attack asks the brain for its chosen victim; limbs no longer run a second nearest-prey search. A resting creature can defend against an immediate attacker, but does not initiate ordinary hunting.

The goggles display the selected action and reason. F3+B also includes these in the sight/navigation debug overlay. The saved previous target is only a tie preference: loading always recomputes the action from current needs rather than resuming stale work blindly.

## Regression checks

`BehaviorPriorityTest` covers strict needs/goals ordering, urgency, escape precedence, wreck-before-hunt, stable ties and satisfied-need removal. The in-engine `needs_tree_prefers_wreck_over_hunting_and_interrupts_for_escape` test supplies competing visible prey, useful wreckage and an optional sac, verifies actual wreck collection, interrupts it for danger, and verifies resumption after safety returns. The full GameTest suite also exercises organ creation and relocation, inventory conservation, hunting, paid growth, flight, storage, core scouting and both glass mazes.

## Normal world loading

`BioshipChunks` gives each living ship expiring, UUID-owned world tickets around its physical body anchor. The view radius reuses the same creator/nearest connected player and server limit as vision. The simulation radius is capped by that view radius and the server simulation distance. Load-only and simulation tickets are separate; vanilla supporting border chunks are retained around the simulation area. Tickets refresh once per second, move with the ship, resize when settings change, and are released on death, block-entity removal or server shutdown. The expiry also clears abandoned tickets if updates cease. No additional test-only arena forcing is required for this behavior.

Ground routes validate the first step from the body’s actual physical position, including the lift/cross/lower sequence. They do not assume physics stopped exactly at a grid center; an obstructed initial edge is rejected during planning rather than selected again on every retry. The priority regression waits for the naturally spawned competing prey to enter Minecraft’s entity index before comparing proposals, without changing terrain, visibility or loading rules.

## Queued goals and collected stock

Each ordinary selection refreshes the ordered goal queue from currently eligible observations and current needs. Completed, expired, unreachable and no-longer-useful offers disappear on refresh. Safety and committed transfers interrupt selection without replacing this with a forced wander transition. F3+P shows the queue, collecting-arm count and swallowed-component count.

`BioshipAssimilation` separates pickup from placement. Up to four reachable components travel as real Sable cargo simultaneously, retaining their full block entities. The arms pull them into persistent internal stock before any final organ location is required. Nearby world components share an approach point when they can be reached together. Useful organs then grow out onto valid, balanced attachment points; sacs prefer an affordable intestinal connection. A blocked deployment remains stored and permits other needs to compete.

Selecting a dead grid commits a scavenging job beyond the usual reserve thresholds. It repeatedly collects reachable non-core blocks until the grid is empty or no remaining waypoint is reachable. Grabs require physical clearance even through sight-transparent glass. Cargo counts toward leg load and center of mass. A full carrying allowance triggers processing before continuing the same wreck where possible. Other bioship cores are always excluded. Healthy world components remain functional; surplus wreck tissue uses the existing fractional reclamation losses. Death releases active cargo and saved swallowed inventories rather than duplicating them.

## Funded adaptation

The core transfers a limited emergency reserve from surplus connected digestive stock: up to 32 Flesh Bits, 16 Elastic Fibers and 8 Bone Fragments. Transfers debit the original stores. Once the ship has learned its digestive organs, these reserves can regrow a missing mouth (8/4/2 flesh/fiber/bone) and stomach (16/8/4). The stomach price includes its initial paid tube material; regeneration does not bootstrap another free intestine network. Emergency metabolism can burn the core reserve when other fuel is exhausted.

Private construction products overflow from the stomach into connected sacs, with 128 units of stomach working storage and 256 additional units per sac. Disconnected stock cannot be spent. Overflow can propose a new sac; its component and additional tube costs are reserved, and an insufficient carrying allowance proposes leg strengthening first. Only empty automatically grown excess sacs are reclaimed during ordinary maintenance.

Overloaded grounded bodies can reclaim nonessential paid tissue or redundant empty components if stronger legs cannot be funded. Removal must preserve the core, digestive organs, gut path and connectivity. Optional armor/counterweight growth requires surplus resources, stable support and a precomputed carrying allowance including the new block; leg upgrades precede the added mass. The same underside attachment layout and mass-weighted support polygon guide rendering, posture and automatic relocation. Stationary bracing is distinct from walking animation.
