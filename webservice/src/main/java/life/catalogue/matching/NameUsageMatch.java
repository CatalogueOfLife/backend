package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A name usage match with additional classification information and a flag indicating if the name
 * is a synonym or not.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class NameUsageMatch {

  private ParsedUsage usage;
  private ParsedUsage acceptedUsage;
  @JsonIgnoreProperties({ "canonicalId", "namesIndexId", "namesIndexMatchType", "marked", "parent", "parentId", "status", "code", "label", "labelHtml" })
  private List<SimpleNameCached> classification;
  private Diagnostics diagnostics;
  private List<Status> additionalStatus;

  public Boolean isSynonym() {
    return usage == null || usage.getStatus() == null ? null : usage.getStatus().isSynonym();
  }

  public ParsedUsage getUsage() {
    return usage;
  }

  public void setUsage(ParsedUsage usage) {
    this.usage = usage;
  }

  public ParsedUsage getAcceptedUsage() {
    return acceptedUsage;
  }

  public void setAcceptedUsage(ParsedUsage acceptedUsage) {
    this.acceptedUsage = acceptedUsage;
  }

  public List<SimpleNameCached> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleNameCached> classification) {
    this.classification = classification;
  }

  public Diagnostics getDiagnostics() {
    return diagnostics;
  }

  public void setDiagnostics(Diagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

  public List<Status> getAdditionalStatus() {
    return additionalStatus;
  }

  public void setAdditionalStatus(List<Status> additionalStatus) {
    this.additionalStatus = additionalStatus;
  }

  @JsonIgnore
  public void addMatchIssue(Issue issue) {
    if (diagnostics == null) {
      diagnostics = new Diagnostics();
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
  public static class Diagnostics {
    private MatchType matchType;
    private List<Issue> issues;
    private Integer confidence;
    private TaxonomicStatus status;
    private String note;
    private long timeTaken;
    private List<NameUsageMatch> alternatives;
    private Map<String, Long> timings;

    public MatchType getMatchType() {
      return matchType;
    }

    public void setMatchType(MatchType matchType) {
      this.matchType = matchType;
    }

    public List<Issue> getIssues() {
      return issues;
    }

    public void setIssues(List<Issue> issues) {
      this.issues = issues;
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

    public void setTimings(Map<String, Long> timings) {
      this.timings = timings;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      Diagnostics that = (Diagnostics) o;
      return timeTaken == that.timeTaken && matchType == that.matchType && Objects.equals(issues, that.issues) && Objects.equals(confidence, that.confidence) && status == that.status && Objects.equals(note, that.note) && Objects.equals(alternatives, that.alternatives) && Objects.equals(timings, that.timings);
    }

    @Override
    public int hashCode() {
      return Objects.hash(matchType, issues, confidence, status, note, timeTaken, alternatives, timings);
    }
  }

  /**
   * A status value derived from a dataset or external source. E.g. IUCN Red List status.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class Status {
    private String datasetKey;
    private String datasetAlias;
    private String gbifKey;
    private String status;
    private String statusCode;
    private String sourceId;
  }
}
