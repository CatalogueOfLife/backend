package life.catalogue.api.model;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ScientificName {

  String getScientificName();

  String getAuthorship();

  Rank getRank();

  void setRank(Rank rank);

  NomCode getCode();

  Authorship getCombinationAuthorship();

  Authorship getBasionymAuthorship();

  /**
   * @return the basionym authorship if existing, otherwise the combination authorship
   */
  default Authorship getBasionymOrCombinationAuthorship() {
    return hasBasionymAuthorship() ? getBasionymAuthorship() : getCombinationAuthorship();
  }

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
    return hasCombinationAuthorship() || hasBasionymAuthorship();
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

  class ScientificParsedName implements ScientificName{
    private final ParsedName pn;

    public ScientificParsedName(ParsedName pn) {
      this.pn = pn;
    }

    @Override
    public String getScientificName() {
      return pn.canonicalNameWithoutAuthorship();
    }

    @Override
    public String getAuthorship() {
      return pn.authorshipComplete();
    }

    @Override
    public Rank getRank() {
      return pn.getRank();
    }

    @Override
    public void setRank(Rank rank) {
      pn.setRank(rank);
    }

    @Override
    public NomCode getCode() {
      return pn.getCode();
    }

    @Override
    public Authorship getCombinationAuthorship() {
      return pn.getCombinationAuthorship();
    }

    @Override
    public Authorship getBasionymAuthorship() {
      return pn.getBasionymAuthorship();
    }

    @Override
    public String getLabel() {
      return pn.canonicalNameComplete();
    }
  }
  static ScientificName wrap(ParsedName pn) {
    return new ScientificParsedName(pn);
  }
}
