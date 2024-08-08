package life.catalogue.matching.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import org.gbif.nameparser.api.Rank;
import java.util.Set;
import java.util.stream.Collectors;
import static life.catalogue.matching.util.CleanupUtils.*;
import static life.catalogue.matching.util.CleanupUtils.bool;

@AllArgsConstructor
@Builder
public class NameUsageQuery {
  final public String usageKey;
  final public String taxonID;
  final public String taxonConceptID;
  final public String scientificNameID;
  final public String scientificName;
  final public String authorship;
  final public String genericName;
  final public String specificEpithet;
  final public String infraSpecificEpithet;
  final public Rank rank;
  final public LinneanClassification classification;
  final public Set<String> exclude;
  final public Boolean strict;
  final public Boolean verbose;

  public static NameUsageQuery create(
    String usageKey,
    String taxonID,
    String taxonConceptID,
    String scientificNameID,
    String scientificName2,
    String scientificName,
    String authorship2,
    String authorship,
    String rank2,
    String rank,
    String genericName,
    String specificEpithet,
    String infraspecificEpithet,
    Classification classification,
    Set<String> exclude,
    Boolean strict,
    Boolean verbose
  ){
    return new NameUsageQuery(
      removeNulls(usageKey),
      removeNulls(taxonID),
      removeNulls(taxonConceptID),
      removeNulls(scientificNameID),
      first(removeNulls(scientificName), removeNulls(scientificName2)),
      first(removeNulls(authorship), removeNulls(authorship2)),
      removeNulls(genericName),
      removeNulls(specificEpithet),
      removeNulls(infraspecificEpithet),
      parseRank(first(removeNulls(rank), removeNulls(rank2))),
      clean(classification),
      exclude != null ? exclude.stream().map(Object::toString).collect(Collectors.toSet()) : Set.of(),
      bool(strict),
      bool(verbose));
  }
}
