import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/**
 * Build-time syntax check; it parses source without loading Minecraft or resolving dependencies.
 */
public final class CheckControlFlowBraces {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length == 0 ? "src" : args[0]);
    var paths = new ArrayList<Path>();
    try (var files = Files.walk(root)) {
      files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(paths::add);
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("Brace validation requires a JDK, not a JRE.");
    }
    var failures = new ArrayList<String>();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task =
          (JavacTask)
              compiler.getTask(
                  null,
                  manager,
                  null,
                  List.of("-proc:none", "--release", "21"),
                  null,
                  manager.getJavaFileObjectsFromPaths(paths));
      var positions = Trees.instance(task).getSourcePositions();
      for (var unit : task.parse()) {
        new TreeScanner<Void, Void>() {
          private void requireBlock(StatementTree body) {
            if (body != null && !(body instanceof BlockTree)) {
              long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, body));
              failures.add(
                  unit.getSourceFile().getName()
                      + ":"
                      + line
                      + ": Control-flow bodies require braces.");
            }
          }

          @Override
          public Void visitIf(IfTree tree, Void unused) {
            requireBlock(tree.getThenStatement());
            // An else-if is one readable conditional chain; each branch still needs a block.
            if (!(tree.getElseStatement() instanceof IfTree)) {
              requireBlock(tree.getElseStatement());
            }
            return super.visitIf(tree, unused);
          }

          @Override
          public Void visitForLoop(ForLoopTree tree, Void unused) {
            requireBlock(tree.getStatement());
            return super.visitForLoop(tree, unused);
          }

          @Override
          public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
            requireBlock(tree.getStatement());
            return super.visitEnhancedForLoop(tree, unused);
          }

          @Override
          public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
            requireBlock(tree.getStatement());
            return super.visitWhileLoop(tree, unused);
          }

          @Override
          public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
            requireBlock(tree.getStatement());
            return super.visitDoWhileLoop(tree, unused);
          }
        }.scan(unit, null);
      }
    }
    if (!failures.isEmpty()) {
      failures.forEach(System.err::println);
      throw new IllegalStateException(failures.size() + " unbraced control-flow bodies.");
    }
    System.out.println("Brace policy passed for " + paths.size() + " Java source files.");
  }
}
