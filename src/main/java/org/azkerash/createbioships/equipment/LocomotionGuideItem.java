package org.azkerash.createbioships.equipment;

/** Leg setup and troubleshooting manual, sharing the general resource-backed reader. */
public final class LocomotionGuideItem extends FieldGuideItem {
  public static final String RESOURCE = "guide/locomotion.json";

  public LocomotionGuideItem(Properties properties) {
    super(properties, RESOURCE);
  }
}
