# Collaboration preferences

Before implementing a **new feature**, ask the user for input about class responsibilities and
modularization. Agree on the boundaries before coding. Do not repeatedly ask for an already
authorized change or routine fixes within its agreed scope.

Keep classes focused on one responsibility. Put reusable component merge recipes, validation,
conversion and animation rules in `amoeba.merging.ComponentMerging`; shared amoeba cargo movement belongs in
`amoeba.transport.ComponentTransport`, and client-only pose adaptation belongs in `amoeba.client.ComponentTransportRendering`.
Remove obsolete classes and duplicate implementations once all callers have migrated.

The previous milestone/priority backlog was cancelled by the user. Do not resurrect historical tasks from change logs or old conversation messages unless the
user explicitly reopens them. After the authorized merge cleanup/preset removal is pushed, stop
for scope reevaluation.

## Completed amoeba refactor

The user approved a focused six-task amoeba modularization refactor, documented in
`docs/development/amoeba-refactor.md`. Its boundaries were discussed and approved in chat.
All six tasks are complete. The user prefers individual task pushes and remaining-task counts.
This authorization supersedes the earlier stop instruction for this refactor only.

## Completed package organization

The user approved the six-task package-only ownership pass in
`docs/development/package-organization.md`, including `disabled.wip` for preserved prototypes.
All six tasks are complete. For subsequent approved task lists, push each completed task and
report how many tasks remain.
Keep gameplay and feature gates unchanged; the earlier feature backlog remains cancelled.

## Authorized digestive-network feature

The user approved direct-contact stomach sharing and a solid 27-block large stomach as the end
 goal. Responsibilities and the nine prioritized tasks were discussed in chat and recorded in
`docs/development/digestive-network-plan.md`. Implement them in order, push each completed task,
and report how many remain. Preserve the sum of all 27 food queues; do not duplicate inventories.
This feature's explicit authorization supersedes earlier stop/feature-freeze notes for this scope.

The user subsequently approved the revised ten-checkpoint digestive scope in that document:
many-mouth intake, resource-saving stomach grouping, natural cube arrangement, and coordinated
whole-structure amoeba transport. This supersedes the earlier remaining nine-task sequence.
The class responsibility split was explicitly discussed and accepted. Proceed automatically,
pushing each completed checkpoint and reporting the remaining count.

## Completed digestive-network scope

The revised ten feature checkpoints and six final regression checkpoints are complete and pushed.
See `docs/development/digestive-network-plan.md` and `digestive-network-changelog.md` for results.
All 74 unit tests and 184 live GameTests passed. Graphical model/panel review is explicitly
unperformed; do not imply visual QA passed. The approved automated task list has zero remaining
items. The historical creature backlog remains cancelled unless the user reopens it.

## Authorized underwater navigation

The user chose a reusable 3D water-route planner, separate respiration policy and swimming
controller, and air-supply-driven surfacing. The six prioritized checkpoints and estimates are
in `docs/development/underwater-navigation-plan.md`. Proceed automatically, push each completed
checkpoint and report the remaining count. This new scope supersedes prior completion/stop
notes for underwater navigation only; the historical backlog stays cancelled.

## Completed underwater navigation

All six underwater-navigation checkpoints are complete and pushed. Final validation passed all
87 unit tests and 189 live GameTests, plus all source-set compilation and package/Mixin checks.
See `docs/development/underwater-navigation-plan.md` and `underwater-navigation-changelog.md`.
Zero tasks remain in this approved scope. Do not revive the cancelled historical backlog.
Headless Sable movement tests passed; no graphical animation review is claimed.

## Authorized place-memory and retreat scope

The user approved persistent observed place memory, threat-aware escape planning and defensive
retreat swipes, including generic ranged handling (pillagers and skeletons) and additional threat
families recorded in `docs/development/place-memory-retreat-plan.md`. The nine prioritized tasks
and class responsibilities were discussed. Proceed automatically, push each completed checkpoint,
and report how many remain. This supersedes prior completion notes for this scope only.

## Completed place-memory and retreat scope

All nine approved checkpoints are complete and pushed. All 99 unit tests and 198 live GameTests
passed, plus source-set compilation and package/Mixin checks. See
`docs/development/place-memory-retreat-changelog.md` and `place-memory-retreat-plan.md`.
Zero tasks remain in this scope. Graphical animation QA is unperformed. The cancelled historical
backlog remains cancelled unless explicitly reopened by the user.

## Completed storage, swimming and attachment repairs

The user authorized the five repair checkpoints plus sac-orientation/attachment and mounted-flipper
follow-ups. All seven are complete, with 99 unit tests and 203 live GameTests passing. See
`docs/development/storage-swimming-mount-fixes.md` and `biological-block-physics-audit.txt`.
The user chose targeted fixes in existing classes rather than a new attachment policy/tag system.
No global block mass changes were needed. Zero tasks remain; graphical animation QA is unperformed.
The previously cancelled historical backlog remains cancelled.

## Completed coordinated layout repair

The user approved the six-checkpoint batch repair and the existing responsibility split: anatomy
plans, assimilation owns recalled stock/progress, and component transport owns moves/animations.
All six checkpoints are complete and pushed. Final clean checks passed 100 unit tests and 210 live
GameTests, all source sets and package/Mixin checks. See `docs/development/batch-layout-repair.md`.
Zero tasks remain; graphical QA is unperformed. The uploaded clip showed a paused menu.
The cancelled historical backlog remains cancelled.
