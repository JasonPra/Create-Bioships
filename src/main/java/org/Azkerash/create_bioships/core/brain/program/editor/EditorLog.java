package org.Azkerash.create_bioships.core.brain.program.editor;

import java.util.*;

/** Bounded session build/error history, kept separately from the current problem list. */
public final class EditorLog {
  private final Deque<EditorProblem> entries = new ArrayDeque<>();

  public void add(EditorProblem entry) {
    entries.addFirst(entry);
    while (entries.size() > 64) {
      entries.removeLast();
    }
  }

  public List<EditorProblem> entries() {
    return List.copyOf(entries);
  }

  public void clear() {
    entries.clear();
  }
}
