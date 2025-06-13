package life.catalogue.matching.model;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.Serializable;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

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
public class NameUsageMatch implements RankNameResolver {

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
  @Schema(description = "If the matched usage is a synonym")
  boolean synonym;
  @Schema(description = "The left ID of the nested set")
  Long left;
  @Schema(description = "The right ID of the nested set")
  Long right;

  public String nameFor(Rank rank) {
    if (classification == null)
      return null;
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(RankedName::getName)
        .orElse(null);
  }

  public String keyFor(Rank rank) {
    if (classification == null)
      return null;
    return getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(RankedName::getKey)
        .orElse(null);
  }

  public void setNameFor(String value, Rank rank) {
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

  public void setKeyFor(String key, Rank rank) {
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
    @Schema(description = "Additional notes about the match")
    String note;
    @Schema(description = "Time taken to perform the match in milliseconds")
    long timeTaken;
    @Schema(description = "A list of similar matches with lower confidence scores")
    List<NameUsageMatch> alternatives;
    @Schema(description = "A set of timings for the different steps of the match process")
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
    @Schema(description = "The canonical name without authorship")
    private String canonicalName;
    @Schema(description = "The authorship for the name usage")
    private String authorship;
    @JsonIgnore private String parentID;
    @Schema(description = "The taxonomic rank for the name usage")
    private Rank rank;
    @Schema(description = "The nomenclatural code for the name usage")
    private NomCode code;
    @Schema(description = "The status of the usage e.g. ACCEPTED, SYNONYM, PROVISIONALLY_ACCEPTED, etc.")
    private TaxonomicStatus status;
    @Schema(description = "The unominal name for the name usage")
    private String uninomial;
    @Schema(description = "The genus or genericName for the name usage")
    private String genericName;
    @Schema(description = "The infrageneric epithet, typically a subgenus or section within a genus")
    private String infragenericEpithet;
    @Schema(description = "The specific epithet, forming the second part of a species name")
    private String specificEpithet;
    @Schema(description = "The infraspecific epithet, used for taxa below the species level (e.g., subspecies)")
    private String infraspecificEpithet;
    @Schema(description = "The nomenclatural type of the name (e.g., scientific, cultivar, informal)")
    private String type;
    @Schema(description = "The name formatted in HTML")
    private String formattedName;
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
    @Schema(description = "The checklistbank dataset key for the dataset that the status is associated with")
    private String clbDatasetKey;
    @Schema(description = "The dataset alias for the dataset that the status is associated with")
    private String datasetAlias;
    @Schema(description = "The GBIF registry key (UUID) for the dataset that the status is associated with")
    private String datasetKey;
    @Schema(description = "The status value")
    private String status;
    @Schema(description = "The status code value")
    private String statusCode;
    @Schema(description = "The ID in the source dataset for this status. e.g. the IUCN ID for this taxon")
    private String sourceId;
  }
}
