package life.catalogue.matching.model;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.gbif.nameparser.api.Rank;

/**
 * A POJO for a classification query that can be used to match higher taxa.
 */
@Schema(description = "A set of higher taxa references", title = "ClassificationQuery", type = "object")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ClassificationQuery implements RankNameResolver {

  String kingdom;
  String phylum;
  @Hidden
  String clazz;
  String order;
  String family;
  String genus;
  String subgenus;
  String species;

  public String nameFor(Rank rank) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          return kingdom;
        case PHYLUM:
          return phylum;
        case CLASS:
          return clazz;
        case ORDER:
          return order;
        case FAMILY:
          return family;
        case GENUS:
          return genus;
        case SUBGENUS:
          return subgenus;
        case SPECIES:
          return species;
      }
    }
    return null;
  }

  public void setHigherRank(String name, Rank rank) {
    if (rank != null) {
      switch (rank) {
        case KINGDOM:
          setKingdom(name);
          break;
        case PHYLUM:
          setPhylum(name);
          break;
        case CLASS:
          setClazz(name);
          break;
        case ORDER:
          setOrder(name);
          break;
        case FAMILY:
          setFamily(name);
          break;
        case GENUS:
          setGenus(name);
          break;
        case SUBGENUS:
          setSubgenus(name);
          break;
        case SPECIES:
          setSpecies(name);
      }
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    appendIfNotNull(sb, "k", kingdom);
    appendIfNotNull(sb, "p", phylum);
    appendIfNotNull(sb, "c", clazz);
    appendIfNotNull(sb, "o", order);
    appendIfNotNull(sb, "f", family);
    appendIfNotNull(sb, "g", genus);
    appendIfNotNull(sb, "sg", subgenus);
    appendIfNotNull(sb, "s", species);
    return sb.toString();
  }

  private void appendIfNotNull(StringBuilder sb, String name, String value) {
    if (value != null && !value.isEmpty()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(name).append(":").append(value);
    }
  }
}
