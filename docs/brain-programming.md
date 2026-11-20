# Cerebral IDE and C# program cards

## First-version contract

The programmable brain uses a documented **sandboxed C# subset**, implemented in Java. It does not embed Lua, a native compiler, the CLR, or arbitrary .NET libraries. This contract describes the implementation being added on the feature branch; the editor, compiler and runtime are separate changes.

Programs may use top-level statements, or a `class Program` containing `static void Main()`. `using System;` is accepted for console output. Supported local types are `int`, `double`, `bool`, `string`, and inferred `var`; declarations require initializers. Expressions support arithmetic, comparisons, equality, boolean operators, assignment, compound assignment and increment/decrement. Statements include blocks, `if`/`else`, `while`, `for`, `break`, `continue`, and `return`.

The initial host surface is `Console.WriteLine(value)`, `Brain.Wait(ticks)`, and read-only `Brain.Health`, `Brain.Hunger`, `Brain.Oxygen`, and `Brain.Tick`. There is no component-control API in this version. Calls outside the documented host surface are compiler errors, including file/network access, reflection, object construction and arbitrary library calls.

```csharp
using System;
class Program {
    static void Main() {
        for (int pulse = 0; pulse < 3; pulse++) {
            Console.WriteLine("Pulse " + pulse);
            Brain.Wait(20);
        }
    }
}
```

Source and its format version live on the card item, so ordinary inventory and brain saves preserve the program. Draft saving does not make a card executable. Compilation is authoritative on the server; writing a program card requires successful compilation of that exact source. Factory cards retain their existing behavior and cannot be overwritten through the editor.

The living brain selects one active card in inventory order. Program state belongs to that active brain execution, not to every brain block. Removing/changing the active card or killing/removing the creature stops that execution. Saving/loading preserves card source; runtime locals restart on loading rather than being a VM checkpoint.

Execution is bounded by source/token/instruction/local-variable/stack/string/output limits. A loop that does not yield faults when its instruction budget is exhausted. `Brain.Wait` suspends execution without sleeping the server thread; its timing follows the brain update cadence. Compiler diagnostics identify a line and column, and runtime faults are reported as program status/output.

Brain-backed editing and output use the existing owner/invite UUID policy on the server, with active-menu, slot, range and card-identity validation. Revoking permission or replacing/removing the card invalidates the editing context. An ordinary card held in the player's own inventory can be edited without granting access to another creature. Source and console output are not broadcast to nearby players.

Compiler, machine, card persistence, menu/network validation and client editor each have separate responsibilities. Host calls are explicit extension points so later component support does not require rewriting the language parser.
