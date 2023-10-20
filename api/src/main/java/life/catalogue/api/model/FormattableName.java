package life.catalogue.api.model;

import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.tax.SciNameNormalizer;

import org.gbif.nameparser.api.LinneanName;
import org.gbif.nameparser.api.NamePart;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

/**
 * Most of the Name class with all getters needed to format a Name using the NameFormatter.
 */
public interface FormattableName extends LinneanName, ScientificName {

  String getSanctioningAuthor();

  String getCultivarEpithet();

  @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
  boolean isCandidatus();

  default Boolean isOriginalSpelling() {
    return null;
  }

  String getNomenclaturalNote();

  String getUnparsed();

  /**
   * @return the terminal epithet. Infraspecific epithet if existing, the species epithet or null
   */
  @JsonIgnore
  default String getTerminalEpithet() {
    return getInfraspecificEpithet() == null ? getSpecificEpithet() : getInfraspecificEpithet();
  }

  default String getEpithet(NamePart part) {
    switch (part) {
      case GENERIC:
        return ObjectUtils.coalesce(getGenus(), getUninomial());
      case INFRAGENERIC:
        return getInfragenericEpithet();
      case SPECIFIC:
        return getSpecificEpithet();
      case INFRASPECIFIC:
        return getInfraspecificEpithet();
    }
    return null;
  }

  @JsonIgnore
  default boolean isAutonym() {
    return getSpecificEpithet() != null && getSpecificEpithet().equals(getInfraspecificEpithet());
  }

  /**
   * @return true if the name is a bi- or trinomial with at least a genus and species epithet given.
   */
  @JsonIgnore
  default boolean isBinomial() {
    return getGenus() != null && getSpecificEpithet() != null;
  }

  /**
   * @return true if the name is a trinomial with at least a genus, species and infraspecific
   * epithet given.
   */
  @JsonIgnore
  default boolean isTrinomial() {
    return isBinomial() && getInfraspecificEpithet() != null;
  }

  @JsonIgnore
  default boolean isInfrageneric() {
    return isParsed() && (
             (getRank().isInfragenericStrictly() && getUninomial() != null)
          || (getInfragenericEpithet() != null && getSpecificEpithet() == null &&  getInfraspecificEpithet() == null)
    );
  }

  @JsonIgnore
  default boolean isIndetermined() {
    return isParsed() && (
      getRank().isInfragenericStrictly() && getInfragenericEpithet() == null && getUninomial() == null
      || getRank().isSpeciesOrBelow() && getSpecificEpithet() == null
      || getRank().isCultivarRank() && getCultivarEpithet() == null
      || getRank().isInfraspecific() && !getRank().isCultivarRank() && getInfraspecificEpithet() == null
    );
  }

  /**
   * @return true if there is any parsed content
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  default boolean isParsed() {
    return getUninomial() != null || getGenus() != null || getInfragenericEpithet() != null
           || getSpecificEpithet() != null || getInfraspecificEpithet() != null || getCultivarEpithet() != null;
  }

  /**
   * Lists all non empty atomized name parts for parsed names.
   * Cultivar epithets, authorship and strains are excluded.
   *
   * @return all non null name parts
   */
  default List<String> nameParts() {
    List<String> parts = Lists.newArrayList(getUninomial(), getGenus(), getInfragenericEpithet(), getSpecificEpithet(), getInfraspecificEpithet());
    parts.removeIf(Objects::isNull);
    return parts;
  }

  /**
   * @return a normalized version of the scientific name useful for matching.
   * Only used on db level from MyBatis
   */
  @JsonIgnore
  default String getScientificNameNormalized() {
    return SciNameNormalizer.normalize(getScientificName());
  }
}
