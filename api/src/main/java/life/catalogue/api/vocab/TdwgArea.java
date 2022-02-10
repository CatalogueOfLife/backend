package life.catalogue.api.vocab;

import life.catalogue.common.text.CSVUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * World Geographical Scheme for Recording Plant Distributions published by TDWG at level 1, 2, 3 or 4.
 *  Level 1 = Continents
 *  Level 2 = Regions
 *  Level 3 = Botanical countries
 *  Level 4 = Basic recording units
 */
public class TdwgArea implements Area {

  public static final List<TdwgArea> AREAS = TdwgArea.build();

  private final String id;
  private final String name;
  private final String parent;
  private final int level;

  public TdwgArea(String id, String name, String parent, int level) {
    this.id = id;
    this.name = name;
    this.parent = parent;
    this.level = level;
  }

  @Override
  public Gazetteer getGazetteer() {
    return Gazetteer.TDWG;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  public String getParent() {
    return parent;
  }

  public int getLevel() {
    return level;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TdwgArea)) return false;
    TdwgArea tdwgArea = (TdwgArea) o;
    return level == tdwgArea.level
           && Objects.equals(id, tdwgArea.id)
           && Objects.equals(name, tdwgArea.name)
           && Objects.equals(parent, tdwgArea.parent);
  }

  @Override
  public String toString() {
    return getGlobalId();
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, parent, level);
  }

  static List<TdwgArea> build() {
    List<TdwgArea> areas = buildLevel(1, -1);
    for (int level = 2; level<=4; level++) {
      areas.addAll(buildLevel(level, 2));
    }
    return Collections.unmodifiableList(areas);
  }

  private static List<TdwgArea> buildLevel(int level, int parentIdColumn) {
    final char delimiter = '*';
    return CSVUtils.parse(TdwgArea.class.getResourceAsStream("/vocab/area/tdwg/tblLevel"+level+".txt"), 1, delimiter)
                   .map(row -> new TdwgArea(cleanID(row.get(0)), row.get(1), parentIdColumn < 0 ? null : cleanID(row.get(parentIdColumn)), level))
                   .collect(Collectors.toList());
  }

  private static String cleanID(String raw) {
    if (raw != null && raw.endsWith(",00")) {
      return raw.substring(0, raw.length() - 3);
    }
    return raw;
  }

  public static TdwgArea of(String id) throws IllegalArgumentException {
    for (var a : AREAS) {
      if (a.getId().equalsIgnoreCase(id)) return a;
    }
    throw new IllegalArgumentException(id + " is not a valid TDWG area");
  }
}
