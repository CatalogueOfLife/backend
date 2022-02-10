package life.catalogue.api.vocab;

import life.catalogue.common.text.CSVUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A Longhurst Biogeographical Province, a partition of the world oceans into provinces
 * as defined by Longhurst, A.R. (2006). Ecological Geography of the Sea. 2nd Edition.
 */
public class LonghurstArea implements Area {
  public final static List<LonghurstArea> AREAS = LonghurstArea.build();

  private final String id;
  private final String name;
  private final char biome;
  private final int productivity;

  LonghurstArea(String id, String name, char biome, int productivity) {
    this.id = id;
    this.name = name;
    this.biome = biome;
    this.productivity = productivity;
  }

  @Override
  public Gazetteer getGazetteer() {
    return Gazetteer.LONGHURST;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  public char getBiome() {
    return biome;
  }

  public int getProductivity() {
    return productivity;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LonghurstArea)) return false;
    LonghurstArea that = (LonghurstArea) o;
    return biome == that.biome && productivity == that.productivity && Objects.equals(id, that.id) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, biome, productivity);
  }

  @Override
  public String toString() {
    return getGlobalId();
  }

  static List<LonghurstArea> build() {
    // PROVCODE;PROVDESCR;Biome;Productivity(gC/m²*d);Prod(Class);;;CHL(mg/m²);Chl(class);;;Photic Depth(m);Photic Depth(class);;;MLD(sigma,m);MLD(sigma,class);;;ST0(°C);ST50(°C);Diff;;;;
    return CSVUtils.parse(LonghurstArea.class.getResourceAsStream("/vocab/area/Longhurst_Province_Summary.csv"), 1)
                   .map(row -> new LonghurstArea(row.get(0), row.get(1), row.get(2).charAt(0), Integer.parseInt(row.get(4))))
                   .collect(Collectors.toList());
  }

  public static LonghurstArea of(String id) throws IllegalArgumentException {
    for (var a : AREAS) {
      if (a.getId().equalsIgnoreCase(id)) return a;
    }
    throw new IllegalArgumentException(id + " is not a valid Longhurst area");
  }
}
