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

## Authorized package organization

The user approved the six-task package-only ownership pass in
`docs/development/package-organization.md`, including `disabled.wip` for preserved prototypes.
Proceed in that order, push after each completed task, and report how many tasks remain.
Keep gameplay and feature gates unchanged; the earlier feature backlog remains cancelled.
