package org.Azkerash.create_bioships.amoeba;

/** A block actor may expose amoeba component abilities without a particular core class. */
public interface AmoebaComponentProvider {
  AmoebaComponentHost amoebaComponents();
}
