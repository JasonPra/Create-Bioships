package org.Azkerash.create_bioships.creature;

/** Material transactions independent of a core type or inventory layout. */
public interface ResourceAccount {
  int material(String id);

  boolean spend(String id, int count);

  void credit(String id, int count);
}
