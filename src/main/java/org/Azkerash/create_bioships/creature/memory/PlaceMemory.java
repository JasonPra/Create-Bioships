package org.Azkerash.create_bioships.creature.memory;

import java.util.*;

/** Bounded evidence about places; not an authoritative map of current terrain or prey. */
public final class PlaceMemory {
  public static final int DEFAULT_CAPACITY = 128;
  public static final long PREY_FRESH_TICKS = 6000, DANGER_FRESH_TICKS = 1200;

  public enum Feature {
    WATER,
    LAKE,
    RIVER,
    OCEAN,
    COVER,
    FOREST,
    CLIFF,
    LEDGE
  }

  public record Point(double x, double y, double z) {
    public Point {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new IllegalArgumentException("Non-finite location");
    }

    public double distance(Point p) {
      return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
    }
  }

  private record Key(String dimension, int x, int y, int z) {
    static Key at(String dimension, Point p) {
      return new Key(
          dimension,
          (int) Math.floor(p.x / 16),
          (int) Math.floor(p.y / 8),
          (int) Math.floor(p.z / 16));
    }
  }

  public record Entry(
      String dimension,
      Point point,
      String biome,
      Set<Feature> features,
      long terrainSeen,
      long updatedAt,
      Map<String, Long> prey,
      Map<String, Long> danger) {
    public Entry {
      Objects.requireNonNull(dimension);
      Objects.requireNonNull(point);
      Objects.requireNonNull(biome);
      features = Set.copyOf(features);
      prey = bounded(prey, 16);
      danger = bounded(danger, 8);
      if (dimension.isBlank() || dimension.length() > 160 || biome.length() > 160)
        throw new IllegalArgumentException("Invalid place identity");
    }

    public double terrainConfidence(long now) {
      return terrainSeen < 0 ? 0 : Math.max(.1, 1 / (1 + Math.max(0, now - terrainSeen) / 72000d));
    }

    public double preyConfidence(long now) {
      return prey.values().stream()
          .mapToDouble(t -> freshness(now, t, PREY_FRESH_TICKS))
          .max()
          .orElse(0);
    }

    public double dangerConfidence(long now) {
      return danger.values().stream()
          .mapToDouble(t -> freshness(now, t, DANGER_FRESH_TICKS))
          .max()
          .orElse(0);
    }
  }

  private final int capacity;
  private final Map<Key, Entry> places = new LinkedHashMap<>();

  public PlaceMemory() {
    this(DEFAULT_CAPACITY);
  }

  public PlaceMemory(int capacity) {
    if (capacity < 1 || capacity > DEFAULT_CAPACITY)
      throw new IllegalArgumentException("Invalid capacity");
    this.capacity = capacity;
  }

  public int size() {
    return places.size();
  }

  public List<Entry> snapshot() {
    return List.copyOf(places.values());
  }

  public List<Entry> inDimension(String dimension) {
    return places.values().stream().filter(p -> p.dimension.equals(dimension)).toList();
  }

  public void terrain(
      String dimension, Point point, String biome, Set<Feature> features, long now) {
    var key = Key.at(dimension, point);
    var old = places.get(key);
    put(
        key,
        new Entry(
            dimension,
            point,
            biome,
            features,
            now,
            now,
            old == null ? Map.of() : old.prey,
            old == null ? Map.of() : old.danger));
  }

  public void prey(String dimension, Point point, String biome, String species, long now) {
    evidence(dimension, point, biome, species, now, true);
  }

  public void danger(String dimension, Point point, String biome, String kind, long now) {
    evidence(dimension, point, biome, kind, now, false);
  }

  private void evidence(
      String dimension, Point point, String biome, String id, long now, boolean prey) {
    if (id == null || id.isBlank() || id.length() > 160) return;
    var key = Key.at(dimension, point);
    var old = places.get(key);
    var sightings =
        new HashMap<String, Long>(old == null ? Map.of() : prey ? old.prey : old.danger);
    sightings.put(id, now);
    put(
        key,
        new Entry(
            dimension,
            old == null ? point : old.point,
            old == null ? biome : old.biome,
            old == null ? Set.of() : old.features,
            old == null ? -1 : old.terrainSeen,
            now,
            prey ? sightings : old == null ? Map.of() : old.prey,
            prey ? old == null ? Map.of() : old.danger : sightings));
  }

  public void restore(Collection<Entry> entries) {
    places.clear();
    for (var entry : entries) put(Key.at(entry.dimension, entry.point), entry);
  }

  private void put(Key key, Entry entry) {
    places.put(key, entry);
    while (places.size() > capacity) {
      var oldest =
          places.entrySet().stream()
              .min(Comparator.comparingLong(e -> e.getValue().updatedAt))
              .orElseThrow();
      places.remove(oldest.getKey());
    }
  }

  private static Map<String, Long> bounded(Map<String, Long> source, int limit) {
    var result = new LinkedHashMap<String, Long>();
    source.entrySet().stream()
        .filter(
            e ->
                e.getKey() != null
                    && !e.getKey().isBlank()
                    && e.getKey().length() <= 160
                    && e.getValue() != null)
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(limit)
        .forEach(e -> result.put(e.getKey(), e.getValue()));
    return Map.copyOf(result);
  }

  private static double freshness(long now, long seen, long lifetime) {
    return Math.max(0, 1 - Math.max(0, now - seen) / (double) lifetime);
  }
}
