package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.*;

import io.swagger.v3.oas.annotations.media.Schema;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import lombok.*;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

/**
 * A name usage match with additional classification information and a flag indicating if the name
 * is a synonym or not.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@Builder
@Schema(description = "A name usage match returned by the webservices. Includes higher taxonomy and diagnostics", title = "NameUsageMatch", type = "object")
@AllArgsConstructor
@NoArgsConstructor
public class NameUsageMatch implements LinneanClassification {

  @Schema(description = "If the matched usage is a synonym")
  boolean synonym;
  @Schema(description = "The matched name usage")
  Usage usage;
  @Schema(description = "The accepted name usage for the match. This will only be populated when we've matched a synonym name usage.")
  Usage acceptedUsage;
  @Schema(description = "The classification of the accepted name usage.")
  List<RankedName> classification;
  @Schema(description = "Diagnostics for a name match including the type of match and confidence level",  implementation = Diagnostics.class)
  Diagnostics diagnostics;
  @Schema(description = "Status information from external sources such as IUCN Red List")
  List<Status> additionalStatus;
  Long left;
  Long right;

  private String nameFor(Rank rank) {
    if (classification == null)
      return null;
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(RankedName::getName)
        .orElse(null);
  }

  private String keyFor(Rank rank) {
    if (classification == null)
      return null;
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(RankedName::getKey)
        .orElse(null);
  }

  private void setNameFor(String value, Rank rank) {
    if (classification == null) {
      this.classification = new ArrayList<>();
    }
    Optional<RankedName> name =
        this.classification.stream().filter(c -> c.getRank().equals(rank)).findFirst();
    if (name.isPresent()) {
      name.get().setName(value);
      name.get().setCanonicalName(value);
    } else {
      RankedName newRank = new RankedName();
      newRank.setRank(rank);
      newRank.setName(value);
      newRank.setCanonicalName(value);
      this.classification.add(newRank);
    }
  }

  private void setKeyFor(String key, Rank rank) {
    Optional<RankedName> name =
        this.getClassification().stream().filter(c -> c.getRank().equals(rank)).findFirst();
    if (name.isPresent()) {
      name.get().setName(key);
    } else {
      RankedName newRank = new RankedName();
      newRank.setRank(rank);
      newRank.setKey(key);
      this.getClassification().add(newRank);
    }
  }

  public String getHigherRankKey(Rank rank) {
    return this.getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(RankedName::getKey)
        .orElse(null);
  }

  @JsonIgnore
  public void addMatchIssue(MatchingIssue issue) {
    if (diagnostics == null) {
      diagnostics = Diagnostics.builder().build();
    }
    if (diagnostics.getIssues() == null){
      diagnostics.setIssues(new ArrayList<>());
    }

    diagnostics.getIssues().add(issue);
  }

  @JsonIgnore
  public void addAdditionalStatus(Status status) {
    if (additionalStatus == null) {
      additionalStatus = new ArrayList<>();
    }
    additionalStatus.add(status);
  }

  @Override
  @JsonIgnore
  public String getKingdom() {
    return nameFor(Rank.KINGDOM);
  }

  @Override
  @JsonIgnore
  public String getPhylum() {
    return nameFor(Rank.PHYLUM);
  }

  @Override
  @JsonIgnore
  public String getClazz() {
    return nameFor(Rank.CLASS);
  }

  @Override
  @JsonIgnore
  public String getOrder() {
    return nameFor(Rank.ORDER);
  }

  @Override
  @JsonIgnore
  public String getFamily() {
    return nameFor(Rank.FAMILY);
  }

  @Override
  @JsonIgnore
  public String getGenus() {
    return nameFor(Rank.GENUS);
  }

  @Override
  @JsonIgnore
  public String getSubgenus() {
    return nameFor(Rank.SUBGENUS);
  }

  @Override
  @JsonIgnore
  public String getSpecies() {
    return nameFor(Rank.SPECIES);
  }

  @JsonIgnore
  public String getKingdomKey() {
    return keyFor(Rank.KINGDOM);
  }

  @JsonIgnore
  public String getPhylumKey() {
    return keyFor(Rank.PHYLUM);
  }

  @JsonIgnore
  public String getClassKey() {
    return keyFor(Rank.CLASS);
  }

  @JsonIgnore
  public String getOrderKey() {
    return keyFor(Rank.ORDER);
  }

  @JsonIgnore
  public String getFamilyKey() {
    return keyFor(Rank.FAMILY);
  }

  @JsonIgnore
  public String getGenusKey() {
    return keyFor(Rank.GENUS);
  }

  @JsonIgnore
  public String getSubgenusKey() {
    return keyFor(Rank.SUBGENUS);
  }

  @JsonIgnore
  public String getSpeciesKey() {
    return keyFor(Rank.SPECIES);
  }

  @Override
  public void setKingdom(String v) {
    setNameFor(v, Rank.KINGDOM);
  }

  @Override
  public void setPhylum(String v) {
    setNameFor(v, Rank.PHYLUM);
  }

  @Override
  public void setClazz(String v) {
    setNameFor(v, Rank.CLASS);
  }

  @Override
  public void setOrder(String v) {
    setNameFor(v, Rank.ORDER);
  }

  @Override
  public void setFamily(String v) {
    setNameFor(v, Rank.FAMILY);
  }

  @Override
  public void setGenus(String v) {
    setNameFor(v, Rank.GENUS);
  }

  @Override
  public void setSubgenus(String v) {
    setNameFor(v, Rank.SUBGENUS);
  }

  @Override
  public void setSpecies(String v) {
    setNameFor(v, Rank.SPECIES);
  }

  @JsonIgnore
  public void setKingdomKey(String v) {
    setKeyFor(v, Rank.KINGDOM);
  }

  @JsonIgnore
  public void setPhylumKey(String v) {
    setKeyFor(v, Rank.PHYLUM);
  }

  @JsonIgnore
  public void setClassKey(String v) {
    setKeyFor(v, Rank.CLASS);
  }

  @JsonIgnore
  public void setOrderKey(String v) {
    setKeyFor(v, Rank.ORDER);
  }

  @JsonIgnore
  public void setFamilyKey(String v) {
    setKeyFor(v, Rank.FAMILY);
  }

  @JsonIgnore
  public void setGenusKey(String v) {
    setKeyFor(v, Rank.GENUS);
  }

  @JsonIgnore
  public void setSubgenusKey(String v) {
    setKeyFor(v, Rank.SUBGENUS);
  }

  @JsonIgnore
  public void setSpeciesKey(String v) {
    setKeyFor(v, Rank.SPECIES);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Schema(description = "Diagnostics for a name match including the type of match and confidence level", title = "Diagnostics", type = "object")
  public static class Diagnostics {
    @Schema(description = "The match type, e.g. 'exact', 'fuzzy', 'partial', 'none'")
    MatchType matchType;
    @Schema(description = "Issues with the name usage match that has been returned")
    List<MatchingIssue> issues;
    @Schema(description = "Issues encountered during steps of the match process")
    List<ProcessFlag> processingFlags;
    @Schema(description = "Confidence level in percent")
    Integer confidence;
    @Schema(description = "The status of the match e.g. ACCEPTED, SYNONYM, AMBIGUOUS, EXCLUDED, etc.")
    TaxonomicStatus status;
    @Schema(description = "Additional notes about the match")
    String note;
    @Schema(description = "Time taken to perform the match in milliseconds")
    long timeTaken;
    @Schema(description = "A list of similar matches with lower confidence scores ")
    List<NameUsageMatch> alternatives;
    Map<String, Long> timings;

    public MatchType getMatchType() {
      return matchType;
    }

    public void setMatchType(MatchType matchType) {
      this.matchType = matchType;
    }

    public List<MatchingIssue> getIssues() {
      return issues;
    }

    public void setIssues(List<MatchingIssue> issues) {
      this.issues = issues;
    }

    public List<ProcessFlag> getProcessingFlags() {
      return processingFlags;
    }

    public void setProcessingFlags(List<ProcessFlag> processingFlags) {
      this.processingFlags = processingFlags;
    }

    public Integer getConfidence() {
      return confidence;
    }

    public void setConfidence(Integer confidence) {
      this.confidence = confidence;
    }

    public TaxonomicStatus getStatus() {
      return status;
    }

    public void setStatus(TaxonomicStatus status) {
      this.status = status;
    }

    public String getNote() {
      return note;
    }

    public void setNote(String note) {
      this.note = note;
    }

    public long getTimeTaken() {
      return timeTaken;
    }

    public void setTimeTaken(long timeTaken) {
      this.timeTaken = timeTaken;
    }

    public List<NameUsageMatch> getAlternatives() {
      return alternatives;
    }

    public void setAlternatives(List<NameUsageMatch> alternatives) {
      this.alternatives = alternatives;
    }

    public Map<String, Long> getTimings() {
      return timings;
    }
  }

  /**
   * A name with an identifier and a taxonomic rank.
   */
  @Schema(description = "A name with an identifier and a taxonomic rank", title = "Usage", type = "object")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @ToString
  @Builder
  public static class Usage implements Serializable {

    private static final long serialVersionUID = 3423423423423L;

    @Schema(description = "The identifier for the name usage")
    private String key;
    @Schema(description = "The name usage")
    private String name;
    private String canonicalName;
    private String authorship;
    @JsonIgnore private String parentID;
    @Schema(description = "The taxonomic rank for the name usage")
    private Rank rank;
    @Schema(description = "The nomenclatural code for the name usage")
    private NomCode code;
    private String uninomial;
    private String genus;
    private String infragenericEpithet;
    private String specificEpithet;
    private String infraspecificEpithet;
    private String cultivarEpithet;
    private String phrase;
    private String voucher;
    private String nominatingParty;
    private boolean candidatus;
    private String notho;
    private Boolean originalSpelling;
    private Map<String, String> epithetQualifier;
    private String type;
    protected boolean extinct;
    private Authorship combinationAuthorship;
    private Authorship basionymAuthorship;
    private String sanctioningAuthor;
    private String taxonomicNote;
    private String nomenclaturalNote;
    private String publishedIn;
    private String unparsed;
    private boolean doubtful;
    private boolean manuscript;
    private String state;
    private Set<String> warnings;
    private String formattedName;

    //additional flags
    private boolean isAbbreviated;
    private boolean isAutonym;
    private boolean isBinomial;
    private boolean isTrinomial;
    private boolean isIncomplete;
    private boolean isIndetermined;
    private boolean isPhraseName;
    private String terminalEpithet;
  }

  @Schema(description = "An scientific name authorship for a name usage, split into components", title = "Authorship", type = "object")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @ToString
  @Builder
  public static class Authorship {
    private List<String> authors = new ArrayList();
    private List<String> exAuthors = new ArrayList();
    private String year;
  }

  /**
   * A name with an identifier and a taxonomic rank.
   */
  @Schema(description = "A name with an identifier and a taxonomic rank", title = "RankedName", type = "object")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @ToString
  @Builder
  public static class RankedName implements Serializable {

    private static final long serialVersionUID = 3423423423423L;

    @Schema(description = "The identifier for the name usage")
    private String key;
    @Schema(description = "The name usage")
    private String name;
    @JsonIgnore private String canonicalName;
    @JsonIgnore private String parentID;
    @Schema(description = "The taxonomic rank for the name usage")
    private Rank rank;
    @Schema(description = "The nomenclatural code for the name usage")
    private NomCode code;
  }

  /**
   * A status value derived from a dataset or external source. E.g. IUCN Red List status.
   */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @Schema(description = "A status value derived from a dataset or external source. E.g. IUCN Red List.",
    title = "Status", type = "object")
  public static class Status {
    @Schema(description = "The dataset key for the dataset that the status is associated with")
    private String datasetKey;
    @Schema(description = "The dataset alias for the dataset that the status is associated with")
    private String datasetAlias;
    @Schema(description = "The gbif registry key for the dataset that the status is associated with")
    private String gbifKey;
    @Schema(description = "The status value")
    private String status;
    @Schema(description = "The status code value")
    private String statusCode;
    @Schema(description = "The ID in the source dataset for this status. e.g. the IUCN ID for this taxon")
    private String sourceId;
  }
}
