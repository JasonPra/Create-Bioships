# Cerebral IDE and C# program cards

## First-version contract

The programmable brain uses a documented **sandboxed C# subset**, implemented in Java. It does not embed Lua, a native compiler, the CLR, or arbitrary .NET libraries. The editor, compiler, card storage and interpreter have separate responsibilities.

Programs may use top-level statements, or a `class Program` containing `static void Main()`. `using System;` is accepted for console output. Supported local types are `int`, `double`, `bool`, `string`, and inferred `var`; declarations require initializers. Expressions support arithmetic, comparisons, equality and short-circuit boolean operators. Assignment, compound assignment and postfix increment/decrement are supported as statements and for-loop steps. Statements include blocks, `if`/`else`, `while`, `for`, `break`, `continue`, and `return`.

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

## Limits and timing

The implementation limits source to 8,192 characters, 2,048 tokens, 4,096 instructions,
128 local variables, 128 evaluation-stack entries, and 64 parser nesting levels.
Each brain update permits 1,024 instructions before a non-yielding program faults.
Strings are limited to 4,096 characters; console history retains 64 lines of up to
256 characters. Waits accept 0–72,000 game ticks and yield at least one update.
The brain updates every 10 ticks, so waits resume on the first brain update at or
after the requested time. These are server-enforced bounds, not client preferences.

The draft editor was exercised in the native client: typing the inventory key did
not close it, Tab inserted spaces, and saving/reopening preserved the source.
A public creative inventory copy restored source only from the author's private
backup. Ordinary item network serialization omitted source and compiled state.

## Using the IDE

Use a blank card in your hand, or select a card in an authorized brain inventory
and choose **Edit card**. **Save draft** (Ctrl+S) stores source without compiling.
**Compile & write** (Ctrl+Enter) checks the exact source on the server and converts
that physical item into a Cerebral Program Card. Failed compilation preserves the
previous card and reports a line and column. Hover the status line to read its full
message. Saving changed source disables the old executable until compilation
succeeds again. Closing without saving discards only the unsaved editor changes.

Place the compiled card in active brain memory. The first recognized executable
card in inventory order runs; existing factory cards keep their original behavior.
Open that installed card's editor and choose **Source / console** to inspect its
state and bounded output. A held card does not execute. Each console request
rechecks the server's current menu, card identity, range, and owner/invite access.
The source and console are never broadcast to nearby players.

Use either top-level statements or the Program.Main wrapper, without mixing them.
This subset has no custom methods, classes beyond the entry wrapper, arrays,
objects, exceptions, namespaces, or arbitrary library imports. Variable names use
ASCII letters, digits and underscores. Numeric types are signed 32-bit int and
finite double; ordinary integer addition, subtraction and multiplication wrap as
in unchecked C#. Double-to-int narrowing is rejected. String concatenation and
literal equality are supported; null and object identity are not language features.

The card stores its private persistent source and compiled-source fingerprint.
A brain saves the card as part of its inventory. A bounded server-side backup per
player restores that player's creative copies without exposing source in public
item synchronization. The interpreter always recompiles source on activation;
client-provided bytecode is never accepted. Runtime locals and console history
are ephemeral and restart after world loading.

## Verification

Language checks cover typing, branching, nested loops, loop control, short-circuit
operators, source positions, rejected external APIs, malformed input, and enforced
resource limits. Machine checks cover arithmetic, waits, isolated state, stopping,
completion, history bounds and runtime faults. Card and brain tests cover source
independence, persistence, failed writes, stale leases, permission revocation,
replacement/removal, inventory-order selection and creature death.

The native client exercised Compile & write, persistence and reopening, and the
source/console switch. An intermittent Create registry-callback startup failure
also appears in older pre-change logs; a retry loaded the world and completed the
native checks. This startup failure has not been fixed by the programming changes.

Final verification: **153/153 required tests passed without Epic Fight**, and
**153/153 passed with Epic Fight 21.17.3.1**. The release JAR contains the compiler
and interpreter and excludes temporary native-QA helpers and development test
classes. All Brain statistic properties return double values; Brain.Tick is the
world's current game tick, while Brain.Wait takes an int duration in game ticks.
