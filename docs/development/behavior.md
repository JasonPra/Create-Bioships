# Bioship behavior controller

`BioshipBrain` owns the current action and target. `BehaviorPriority` ranks proposals; `BioshipPriorities`, vision, defense and scavenging supply candidates. Crawling, flight, assimilation, wreck collection, growth and tentacle attacks execute the selected action rather than choosing competing targets independently.

The selector handles these branches:

1. Dormant/dead organisms do no autonomous work. Approved reconstruction is an exclusive transaction until completion, matching the blueprint controller's existing body lock.
2. Mandatory creeper retreat and critical-injury retreat interrupt ordinary work. High pilot trust can override injury retreat, but cannot make an unprotected single core fight a creeper.
3. Active ally rescue, immediate defense and in-progress component transfers complete their controlled actions. Rest and authorized trusted flight controls take precedence over optional gathering.
4. Weighted needs compete using actual digestive readiness, satiety, queued nutrition, connected food stores, growth materials, repair reserves and leg/hull costs.
5. Long-term construction, optional components and storage follow; wandering or idle is the fallback.

Within the weighted branch, a need always beats a goal. Urgency dominates distance; small distance/value adjustments break ties. A small preference for the existing target avoids switching back and forth between equivalent candidates. Satisfied needs disappear on the next evaluation. Hidden remembered components retain the existing vision/memory rules; the controller does not inspect inventories through walls.

Missing mouth/stomach components are urgent. Accessible food and growth supplies compete according to the current shortage. Useful dead-ship tissue ranks above live hunting for that shortage; optional empty containers cannot distract the creature from a digestive prerequisite. Hunting stops competing when queued meals or connected food stores already cover immediate intake. Wreck collection and dropped-block recycling retain their existing material losses and do not grant free nutrition or trust.

Digestion, reserve-funded healing, sensing and body maintenance remain passive services. Physical controls remain physical. Each attack asks the brain for its chosen victim; limbs no longer run a second nearest-prey search. A resting creature can defend against an immediate attacker, but does not initiate ordinary hunting.

The goggles display the selected action and reason. F3+B also includes these in the sight/navigation debug overlay. The saved previous target is only a tie preference: loading always recomputes the action from current needs rather than resuming stale work blindly.

## Regression checks

`BehaviorPriorityTest` covers strict needs/goals ordering, urgency, escape precedence, wreck-before-hunt, stable ties and satisfied-need removal. The in-engine `needs_tree_prefers_wreck_over_hunting_and_interrupts_for_escape` test supplies competing visible prey, useful wreckage and an optional sac, verifies actual wreck collection, interrupts it for danger, and verifies resumption after safety returns. The full GameTest suite also exercises organ creation and relocation, inventory conservation, hunting, paid growth, flight, storage, core scouting and both glass mazes.

## Normal world loading

`BioshipChunks` gives each living ship expiring, UUID-owned world tickets around its physical body anchor. The view radius reuses the same creator/nearest connected player and server limit as vision. The simulation radius is capped by that view radius and the server simulation distance. Load-only and simulation tickets are separate; vanilla supporting border chunks are retained around the simulation area. Tickets refresh once per second, move with the ship, resize when settings change, and are released on death, block-entity removal or server shutdown. The expiry also clears abandoned tickets if updates cease. No additional test-only arena forcing is required for this behavior.

Ground routes validate the first step from the body’s actual physical position, including the lift/cross/lower sequence. They do not assume physics stopped exactly at a grid center; an obstructed initial edge is rejected during planning rather than selected again on every retry. The priority regression waits for the naturally spawned competing prey to enter Minecraft’s entity index before comparing proposals, without changing terrain, visibility or loading rules.
