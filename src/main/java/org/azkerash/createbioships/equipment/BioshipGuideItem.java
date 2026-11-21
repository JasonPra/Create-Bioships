package org.azkerash.createbioships.equipment;

/** General use manual; its crafting chapters are populated from the world's real recipe manager. */
public final class BioshipGuideItem extends FieldGuideItem {
  public static final String RESOURCE = "guide/bioships.json";

  public BioshipGuideItem(Properties properties) {
    super(properties, RESOURCE);
  }
}
