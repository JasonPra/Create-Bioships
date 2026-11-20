# Readability and verification

- Use braces for every if/else branch and loop body, including single-statement guards. Normal else-if chains are allowed; each branch's actions still belong in braces. `checkControlFlowBraces` enforces this through Gradle `check`.
- Explain non-obvious intent, invariants, coordinate spaces, units, persistence boundaries, and compatibility constraints. Do not comment obvious assignments, getters, or straightforward statements.
- Replace unexplained tuning values, time/range limits, and protocol sizes with named constants. Keep geometry and fixture data readable; explain their units rather than naming every coordinate or color.
- Prefer switch for discrete dispatch on one stable discriminator. Keep unrelated predicates and range/geometry tests as conditions.
- Keep mutable state private. Expose getters and controlled update methods where other classes need access; protect collection invariants. Records already have read-only accessors. Public constants and framework-required interoperability members are exceptions.
- Preserve behavior during readability cleanup. Run the build and relevant regression tests. Do not compile while a Minecraft client or test-server runtime is using the shared build output.
