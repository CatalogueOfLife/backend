package life.catalogue.api.model;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.ExAuthorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ScientificName {

  String getScientificName();

  String getAuthorship();

  Rank getRank();

  void setRank(Rank rank);

  NomCode getCode();

  ExAuthorship getCombinationAuthorship();

  ExAuthorship getBasionymAuthorship();

  Authorship getEmendAuthorship();
  String getLabel();

  @JsonIgnore
  default String getLabelWithRank() {
    return getLabel() + " [" + getRank() + "]";
  }

  /**
   * @return true if any kind of authorship exists
   */
  @JsonIgnore
  default boolean hasAuthorship() {
    return getAuthorship() != null || hasParsedAuthorship();
  }

  default boolean hasParsedAuthorship() {
    return hasCombinationAuthorship() || hasBasionymAuthorship() || hasEmendAuthorship();
  }

  @JsonIgnore
  default boolean isCanonical() {
    return getRank() == IndexName.CANONICAL_RANK && !hasAuthorship();
  }

  @JsonIgnore
  default boolean hasCombinationAuthorship() {
    return getCombinationAuthorship() != null && !getCombinationAuthorship().isEmpty();
  }

  @JsonIgnore
  default boolean hasBasionymAuthorship() {
    return getBasionymAuthorship() != null && !getBasionymAuthorship().isEmpty();
  }

  @JsonIgnore
  default boolean hasEmendAuthorship() {
    return getEmendAuthorship() != null && !getEmendAuthorship().isEmpty();
  }
}
