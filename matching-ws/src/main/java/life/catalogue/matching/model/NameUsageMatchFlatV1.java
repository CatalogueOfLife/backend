package life.catalogue.matching.model;

import org.gbif.nameparser.api.Rank;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Version 1 of the name usage match response object. This is the flattered version of the NameUsageMatch object.
 * This class is used to serialize the response of the name usage matching service in the v1 format
 * and to read legacy integration test data to create an index for matching tests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Schema(description = "A version 1 name usage match returned by the webservices. Includes higher taxonomy and diagnostics", title = "NameUsageMatchV1Flat", type = "object")
public class NameUsageMatchFlatV1 implements Serializable {

  private static final long serialVersionUID = -8927655067465421358L;
  private Integer usageKey;
  private Integer acceptedUsageKey;
  private String scientificName;
  private String canonicalName;
  private String rank;
  private NameUsageMatchV1.TaxonomicStatusV1 status;
  private Boolean synonym;
  private Integer confidence;
  private String note;
  private NameUsageMatchV1.MatchTypeV1 matchType;
  private List<NameUsageMatchFlatV1> alternatives;
  private String kingdom;
  private String phylum;
  @JsonProperty("class")
  private String clazz;
  private String order;
  private String family;
  private String genus;
  private String subgenus;
  private String species;
  private Integer kingdomKey;
  private Integer phylumKey;
  private Integer classKey;
  private Integer orderKey;
  private Integer familyKey;
  private Integer genusKey;
  private Integer subgenusKey;
  private Integer speciesKey;

  private void setHigherRanks(Collection<NameUsageMatch.RankedName> rns) {
    for (NameUsageMatch.RankedName rn : rns) {
      switch (rn.getRank()) {
        case KINGDOM:
          setKingdom(rn.getName());
          setKingdomKey(Integer.parseInt(rn.getKey()));
          break;
        case PHYLUM:
          setPhylum(rn.getName());
          setPhylumKey(Integer.parseInt(rn.getKey()));
          break;
        case CLASS:
          setClazz(rn.getName());
          setClassKey(Integer.parseInt(rn.getKey()));
          break;
        case ORDER:
          setOrder(rn.getName());
          setOrderKey(Integer.parseInt(rn.getKey()));
        case FAMILY:
          setFamily(rn.getName());
          setFamilyKey(Integer.parseInt(rn.getKey()));
          break;
        case GENUS:
          setGenus(rn.getName());
          setGenusKey(Integer.parseInt(rn.getKey()));
        case SUBGENUS:
          setSubgenus(rn.getName());
          setSubgenusKey(Integer.parseInt(rn.getKey()));
          break;
        case SPECIES:
          setSpecies(rn.getName());
          setSpeciesKey(Integer.parseInt(rn.getKey()));
          break;
      }
    }
  }
  public static Optional<NameUsageMatchFlatV1> createFrom(NameUsageMatch nameUsageMatch) {
    if (nameUsageMatch == null || nameUsageMatch.getUsage() == null) return Optional.empty();

    try {
      //check if usageKey is a number
      Integer.parseInt(nameUsageMatch.getUsage().getKey());
    } catch (NumberFormatException e) {
      return Optional.empty();
    }

    NameUsageMatchFlatV1 match = new NameUsageMatchFlatV1();
    if (nameUsageMatch.getUsage() != null) {
      match.setUsageKey(Integer.parseInt(nameUsageMatch.getUsage().getKey()));
      match.setScientificName(nameUsageMatch.getUsage().getName());
      match.setCanonicalName(nameUsageMatch.getUsage().getCanonicalName());
      match.setRank(nameUsageMatch.getUsage().getRank().name());
    }
    if (nameUsageMatch.getAcceptedUsage() != null)
      match.setAcceptedUsageKey(Integer.parseInt(nameUsageMatch.getAcceptedUsage().getKey()));

    if (nameUsageMatch.getDiagnostics().getStatus() != null)
      match.setStatus(NameUsageMatchV1.TaxonomicStatusV1.convert(nameUsageMatch.getDiagnostics().getStatus()));
    match.setConfidence(nameUsageMatch.getDiagnostics().getConfidence());
    match.setNote(nameUsageMatch.getDiagnostics().getNote());
    match.setMatchType(NameUsageMatchV1.MatchTypeV1.convert(nameUsageMatch.getDiagnostics().getMatchType()));
    if (nameUsageMatch.getDiagnostics().getAlternatives() != null)
      match.setAlternatives(
          nameUsageMatch.getDiagnostics().getAlternatives().stream()
              .map(NameUsageMatchFlatV1::createFrom)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList()));
    match.setHigherRanks(nameUsageMatch.classification);
    return Optional.of(match);
  }
}
