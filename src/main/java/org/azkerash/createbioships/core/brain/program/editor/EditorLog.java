package org.azkerash.createbioships.core.brain.program.editor;

import java.util.*;

/** Bounded session build/error history, kept separately from the current problem list. */
public final class EditorLog {
  private static final int MAX_ENTRIES = 64;

  private final Deque<EditorProblem> entries = new ArrayDeque<>();

  public void add(EditorProblem entry) {
    entries.addFirst(entry);
    while (entries.size() > MAX_ENTRIES) {
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
