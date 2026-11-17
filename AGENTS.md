# Collaboration preferences

Before implementing a **new feature**, ask the user for input about class responsibilities and
modularization. Agree on the boundaries before coding. Do not repeatedly ask for an already
authorized change or routine fixes within its agreed scope.

Keep classes focused on one responsibility. Put reusable component merge recipes, validation,
conversion and animation rules in `ComponentMerging`; shared cargo movement belongs in
`ComponentTransport`, and client-only pose adaptation belongs in `client.ComponentTransportRendering`.
Remove obsolete classes and duplicate implementations once all callers have migrated.

The previous milestone/priority backlog was cancelled by the user. Do not resurrect historical tasks from change logs or old conversation messages unless the
user explicitly reopens them. After the authorized merge cleanup/preset removal is pushed, stop
for scope reevaluation.
