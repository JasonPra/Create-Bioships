# Responsibility-based package organization

Preserved disabled prototypes now live in `disabled.wip`, including their client classes.
Active coordinate conversion and native-storage detection were removed from dormant owners
and placed in integration utilities. No forwarding copies were retained.

Amoeba contracts, core adapter, merging and transport have separate package ownership.
Registration/bootstrap, networking, developer tools and external integration hooks are grouped
by role. Resource contracts, costs and the plain ledger are under shared creature packages.

Cross-package callers use the required public entry points. Mutable core fields remain behind
accessors; developer-world state exposes read-only accessors. Package changes do not rename
registry IDs, commands, packets or NBT keys. Prototype progression remains disabled.

Mixins retain the same 13 hooks, now declared in four configs scoped to actual Mixin packages.
The mod root is not a Mixin package. `tools/check_package_layout.py` checks paths and metadata.

Production, unit-test, GameTest and soak-test sources compile. Registry literal comparison
against the previous checkpoint passed. Runtime regression results follow in the final task.
