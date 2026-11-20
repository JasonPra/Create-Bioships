package org.Azkerash.create_bioships.core.brain.program;

import java.util.List;

/** Pure Java checks shared by fast language verification and the in-game test suite. */
public final class ProgramMachineChecks {
  public static void main(String[] args) {
    run();
    System.out.println("CEREBRAL_MACHINE_CHECKS_PASS");
  }

  public static void run() {
    output("int a=7/2; double b=a+0.5; Console.WriteLine(a); Console.WriteLine(b);", "3", "3.5");
    output(
        "int n=0; for(int i=0;i<8;i++){ if(i==2)continue; if(i==6)break; n+=i; }"
            + " Console.WriteLine(n);",
        "13");
    output(
        "int n=0; while(n<3){n++;} if(n==3) Console.WriteLine(\"yes\"); else"
            + " Console.WriteLine(\"no\");",
        "yes");
    output(
        "bool a=false && 1/0==0; bool b=true || 1/0==0; Console.WriteLine(a);"
            + " Console.WriteLine(b);",
        "False",
        "True");
    output("string a=\"a\"; a+=2; Console.WriteLine(a); Console.WriteLine(1==1.0);", "a2", "True");
    output(
        "for(int i=0;i<2;i++){for(int j=0;j<3;j++){if(j==1)continue;Console.WriteLine(i*10+j);}}",
        "0",
        "2",
        "10",
        "12");
    output(
        "int x=1; x++; x*=4; x--; x%=5; Console.WriteLine(x); return; Console.WriteLine(9);", "2");
    output("Console.WriteLine(Brain.Health+Brain.Hunger+Brain.Oxygen+Brain.Tick);", "10.0");
    fault("while(true) {}", "budget");
    fault("int n=0; Console.WriteLine(1/n);", "zero");
    fault("Brain.Wait(-1);", "Wait");
    fault("Brain.Wait(72001);", "Wait");
    fault("string a=\"x\"; while(true){a+=a;}", "String");
    var waits =
        machine("Console.WriteLine(\"before\"); Brain.Wait(20); Console.WriteLine(\"after\");");
    waits.advance(100, HOST);
    check(waits.state() == ProgramMachine.State.WAITING, "Wait did not suspend");
    waits.advance(119, HOST);
    check(waits.output().equals(List.of("before")), "Wait resumed early");
    waits.advance(120, HOST);
    check(waits.state() == ProgramMachine.State.COMPLETED, "Wait did not resume");
    check(waits.output().equals(List.of("before", "after")), "Wait lost state");
    waits.advance(200, HOST);
    check(waits.output().size() == 2, "Completed program restarted");
    var zero = machine("Brain.Wait(0); Console.WriteLine(1);");
    zero.advance(1, HOST);
    zero.advance(1, HOST);
    check(zero.state() == ProgramMachine.State.WAITING, "Zero wait failed to yield");
    zero.advance(2, HOST);
    check(zero.state() == ProgramMachine.State.COMPLETED, "Zero wait never resumed");
    var stopped = machine("Brain.Wait(1); Console.WriteLine(1);");
    stopped.advance(0, HOST);
    stopped.stop();
    stopped.advance(20, HOST);
    check(
        stopped.state() == ProgramMachine.State.STOPPED && stopped.output().isEmpty(),
        "Stopped machine resumed");
    var history = machine("for(int i=0;i<80;i++){ Console.WriteLine(i); Brain.Wait(1); }");
    for (int i = 0; i < 82; i++) {
      history.advance(i, HOST);
    }
    check(
        history.output().size() == 64 && history.output().getFirst().equals("16"),
        "History was not bounded");
    var wide = machine("Console.WriteLine(\"" + "x".repeat(400) + "\");");
    wide.advance(0, HOST);
    check(wide.output().getFirst().length() == 256, "Console line was not bounded");
    var location = machine("int n=0;\nConsole.WriteLine(1/n);");
    location.advance(0, HOST);
    check(location.fault().line() == 2, "Runtime fault lost source position");
    var separate = machine("int x=1; Brain.Wait(1); x++; Console.WriteLine(x);");
    var other = machine("Console.WriteLine(9);");
    separate.advance(0, HOST);
    other.advance(0, HOST);
    separate.advance(1, HOST);
    check(
        separate.output().equals(List.of("2")) && other.output().equals(List.of("9")),
        "Machines shared state");
    for (String bad :
        List.of(
            "System.IO.File.ReadAllText(\"x\");",
            "var a=new object();",
            "Console.GetType();",
            "Brain.Health=1;",
            "while(1){}",
            "break;",
            "int a;",
            "int a=true;")) {
      check(!ProgramCompiler.compile(bad).success(), "Unsupported program compiled");
    }
    check(
        !ProgramCompiler.compile("(".repeat(200) + "1" + ")".repeat(200) + ";").success(),
        "Deep nesting compiled");
  }

  private static final ProgramMachine.Host HOST =
      name ->
          switch (name) {
            case "Health" -> 1;
            case "Hunger" -> 2;
            case "Oxygen" -> 3;
            case "Tick" -> 4;
            default -> throw new AssertionError("Unapproved host statistic");
          };

  private static ProgramMachine machine(String source) {
    var result = ProgramCompiler.compile(source);
    if (!result.success()) {
      throw new AssertionError(result.diagnostic());
    }
    return new ProgramMachine(result.program());
  }

  private static void output(String source, String... expected) {
    var machine = machine(source);
    machine.advance(0, HOST);
    check(
        machine.state() == ProgramMachine.State.COMPLETED, "Unexpected state: " + machine.fault());
    check(machine.output().equals(List.of(expected)), "Unexpected output: " + machine.output());
  }

  private static void fault(String source, String message) {
    var machine = machine(source);
    machine.advance(0, HOST);
    check(
        machine.state() == ProgramMachine.State.FAULTED
            && machine.fault().message().contains(message),
        "Expected fault: " + message + ", got " + machine.fault());
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
