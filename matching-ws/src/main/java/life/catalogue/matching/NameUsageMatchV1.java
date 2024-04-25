package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import life.catalogue.api.vocab.MatchType;
import lombok.Data;
import org.gbif.nameparser.api.Rank;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class NameUsageMatchV1 {

  private static final long serialVersionUID = -8927655067465421358L;
  private Integer usageKey;
  private Integer acceptedUsageKey;
  private String scientificName;
  private String canonicalName;
  private String rank;
  private String status;
  private Integer confidence;
  private String note;
  private MatchType matchType;
  private List<NameUsageMatchV1> alternatives;
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

  public static NameUsageMatchV1 createFrom(NameUsageMatch nameUsageMatch) {
    NameUsageMatchV1 match = new NameUsageMatchV1();
    if (nameUsageMatch.getUsage() != null) {
      match.setUsageKey(Integer.parseInt(nameUsageMatch.getUsage().getKey()));
      match.setScientificName(nameUsageMatch.getUsage().getName());
      match.setCanonicalName(nameUsageMatch.getUsage().getCanonicalName());
      match.setRank(nameUsageMatch.getUsage().getRank().name());
    }
    if (nameUsageMatch.getAcceptedUsage() != null)
      match.setAcceptedUsageKey(Integer.parseInt(nameUsageMatch.getAcceptedUsage().getKey()));

    if (nameUsageMatch.getStatus() != null) match.setStatus(nameUsageMatch.getStatus().name());
    match.setConfidence(nameUsageMatch.getDiagnostics().getConfidence());
    match.setNote(nameUsageMatch.getDiagnostics().getNote());
    match.setMatchType(nameUsageMatch.getDiagnostics().getMatchType());
    if (nameUsageMatch.getAlternatives() != null)
      match.setAlternatives(
          nameUsageMatch.getAlternatives().stream()
              .map(NameUsageMatchV1::createFrom)
              .collect(Collectors.toList()));
    match.setKingdom(nameUsageMatch.getKingdom());
    match.setPhylum(nameUsageMatch.getPhylum());
    match.setClazz(nameUsageMatch.getClazz());
    match.setOrder(nameUsageMatch.getOrder());
    match.setFamily(nameUsageMatch.getFamily());
    match.setGenus(nameUsageMatch.getGenus());
    match.setSubgenus(nameUsageMatch.getSubgenus());
    match.setSpecies(nameUsageMatch.getSpecies());
    if (nameUsageMatch.getHigherRankKey(Rank.KINGDOM) != null)
      match.setKingdomKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.KINGDOM)));
    if (nameUsageMatch.getHigherRankKey(Rank.PHYLUM) != null)
      match.setPhylumKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.PHYLUM)));
    if (nameUsageMatch.getHigherRankKey(Rank.CLASS) != null)
      match.setClassKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.CLASS)));
    if (nameUsageMatch.getHigherRankKey(Rank.ORDER) != null)
      match.setOrderKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.ORDER)));
    if (nameUsageMatch.getHigherRankKey(Rank.FAMILY) != null)
      match.setFamilyKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.FAMILY)));
    if (nameUsageMatch.getHigherRankKey(Rank.GENUS) != null)
      match.setGenusKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.GENUS)));
    if (nameUsageMatch.getHigherRankKey(Rank.SUBGENUS) != null)
      match.setSubgenusKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.SUBGENUS)));
    if (nameUsageMatch.getHigherRankKey(Rank.SPECIES) != null)
      match.setSpeciesKey(Integer.parseInt(nameUsageMatch.getHigherRankKey(Rank.SPECIES)));
    return match;
  }
}
