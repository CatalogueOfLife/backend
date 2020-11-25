package life.catalogue.api.model;

import life.catalogue.api.vocab.Issue;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;


public class VerbatimSource implements DSID<String>, IssueContainer, Serializable {

  private String id;
  private Integer datasetKey;
  private String sourceId;
  private Integer sourceDatasetKey;
  private Set<Issue> issues = EnumSet.noneOf(Issue.class);
  // instance hash created on load to see if the instance has been changed
  private int _hashKeyOnLoad = -1;

  public VerbatimSource() {
  }

  public VerbatimSource(Integer datasetKey, String id, Integer sourceDatasetKey, String sourceId) {
    this.id = id;
    this.datasetKey = datasetKey;
    this.sourceId = sourceId;
    this.sourceDatasetKey = sourceDatasetKey;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  public Integer getSourceDatasetKey() {
    return sourceDatasetKey;
  }

  public void setSourceDatasetKey(Integer sourceDatasetKey) {
    this.sourceDatasetKey = sourceDatasetKey;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  @Override
  public Set<Issue> getIssues() {
    return issues;
  }
  
  @Override
  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }

  /**
   * Stores the current state of the instance for subsequent hasChanged() tests.
   */
  public void setHashCode() {
    _hashKeyOnLoad = hashCode();
  }

  /**
   * @return true if the instance has been modified since the last time setHashCode was executed.
   */
  public boolean hasChanged() {
    return _hashKeyOnLoad == -1 || _hashKeyOnLoad != hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VerbatimSource)) return false;
    VerbatimSource that = (VerbatimSource) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(sourceId, that.sourceId) &&
      Objects.equals(sourceDatasetKey, that.sourceDatasetKey) &&
      Objects.equals(issues, that.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, sourceId, sourceDatasetKey, issues);
  }
}
