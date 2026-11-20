package org.Azkerash.create_bioships.core.brain.program;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable language checks shared by the fast Java runner and the regression server. */
public final class ProgramSwitchChecks {
  public static void main(String[] args) {
    run();
    System.out.println("CEREBRAL_SWITCH_CHECKS_PASS");
  }

  public static void run() {
    output(
        "switch(2){case 1:Console.WriteLine(1);break;case"
            + " 2:Console.WriteLine(2);break;default:Console.WriteLine(3);break;}",
        "2");
    output("switch(2){default:Console.WriteLine(9);break;case 2:Console.WriteLine(2);break;}", "2");
    output("switch(7){case 1:Console.WriteLine(1);break;} Console.WriteLine(7);", "7");
    output("switch(7){} Console.WriteLine(7);", "7");
    output("switch(7){default:Console.WriteLine(9);break;case 2:Console.WriteLine(2);break;}", "9");
    output(
        "switch(\"fish\"){case \"land\":Console.WriteLine(1);break;case"
            + " \"fish\":Console.WriteLine(2);break;}",
        "2");
    output(
        "switch(true){case false:Console.WriteLine(0);break;case true:Console.WriteLine(1);break;}",
        "1");
    output(
        "switch(-2147483648){case -2147483648:case"
            + " -1:Console.WriteLine(1);break;default:Console.WriteLine(0);break;}",
        "1");
    output("switch(2){case 1:case 2:default:Console.WriteLine(8);break;}", "8");
    output(
        "switch(1){case 1:{int x=5;Console.WriteLine(x);break;}case 2:{int"
            + " x=6;Console.WriteLine(x);break;}}",
        "5");
    output(
        "int i=0;int sum=0;while(i<4){i++;switch(i){case 1:continue;case"
            + " 2:sum+=20;break;default:sum+=i;break;}sum+=100;}Console.WriteLine(sum);",
        "327");
    output(
        "switch(1){case 1:for(int"
            + " i=0;i<4;i++){if(i==2)break;Console.WriteLine(i);}Console.WriteLine(8);break;}Console.WriteLine(9);",
        "0",
        "1",
        "8",
        "9");
    output(
        "switch(1){case 1:switch(2){case"
            + " 2:Console.WriteLine(2);break;default:break;}Console.WriteLine(1);break;}Console.WriteLine(9);",
        "2",
        "1",
        "9");
    output(
        "int x=1;switch(x){case 1:if(x==1){Console.WriteLine(1);break;}else"
            + " return;default:return;}Console.WriteLine(9);",
        "1",
        "9");
    var reads = new AtomicInteger();
    var once =
        machine(
            "switch(Brain.Health==2){case false:Console.WriteLine(0);break;case"
                + " true:Console.WriteLine(1);break;}");
    once.advance(
        0,
        name -> {
          reads.incrementAndGet();
          return 2;
        });
    check(
        reads.get() == 1 && once.output().equals(List.of("1")),
        "Switch evaluated selector more than once");
    var wait =
        machine(
            "for(int i=0;i<2;i++){switch(i){case"
                + " 0:Brain.Wait(20);Console.WriteLine(0);break;default:Console.WriteLine(1);break;}}");
    wait.advance(0, name -> 0);
    check(wait.state() == ProgramMachine.State.WAITING, "Switch lost wait state");
    wait.advance(19, name -> 0);
    check(wait.output().isEmpty(), "Switch wait resumed early");
    wait.advance(20, name -> 0);
    check(
        wait.state() == ProgramMachine.State.COMPLETED && wait.output().equals(List.of("0", "1")),
        "Switch did not resume correctly");
    var infinite = machine("while(true){switch(1){case 1:continue;default:break;}}");
    infinite.advance(0, name -> 0);
    check(
        infinite.state() == ProgramMachine.State.FAULTED
            && infinite.fault().message().contains("budget"),
        "Switch bypassed instruction budget");
    reject("switch(1){case 1:break;case 1:break;}", "Duplicate case");
    reject("switch(0){case -0:break;case 0:break;}", "Duplicate case");
    reject("switch(1){default:break;default:break;}", "Duplicate default");
    reject("switch(1){case true:break;}", "Expected int");
    reject("switch(1.0){case 1:break;}", "Switch supports");
    reject("switch(1){case 1+1:break;}", "Expected ':'");
    reject("switch(1){case 1:Console.WriteLine(1);case 2:break;}", "fall-through");
    reject("switch(1){case 1:}", "fall-through");
    reject("switch(1){case 1:if(Brain.Health>0)break;}", "fall-through");
    reject("switch(1){case 1:while(false){break;}}", "fall-through");
    reject("switch(1){case 1:continue;}", "Continue requires");
    reject("switch(1){case 2147483648:break;}", "out of range");
    reject("switch(1){case 1 break;}", "Expected ':'");
    reject("switch(1){case 1:break;", "Expected '}'");
    reject("int switch=1;", "reserved");
    reject("switch(1){case 1:int x=1;break;case 2:Console.WriteLine(x);break;}", "Unknown local");
    reject("switch(1){case 1:break;}continue;", "Continue requires");
    reject("switch(1){case 1:break;}break;", "Break requires");
    var location = ProgramCompiler.compile("switch(1) {\ncase 1: break;\ncase 1: break;\n}");
    check(location.diagnostic().line() == 3, "Switch diagnostic lost source line");
    var deep =
        ProgramCompiler.compile(
            "switch(1){case 1:".repeat(80) + "break;" + "}break;".repeat(79) + "}");
    check(!deep.success(), "Switch bypassed nesting limit");
  }

  private static ProgramMachine machine(String source) {
    var result = ProgramCompiler.compile(source);
    if (!result.success()) throw new AssertionError(result.diagnostic());
    return new ProgramMachine(result.program());
  }

  private static void output(String source, String... expected) {
    var machine = machine(source);
    machine.advance(0, name -> 0);
    check(
        machine.state() == ProgramMachine.State.COMPLETED,
        "Unexpected machine state: " + machine.fault());
    check(
        machine.output().equals(List.of(expected)),
        "Unexpected switch output: " + machine.output());
  }

  private static void reject(String source, String message) {
    var result = ProgramCompiler.compile(source);
    check(
        !result.success() && result.diagnostic().message().contains(message),
        "Unexpected switch diagnostic: " + result.diagnostic());
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
