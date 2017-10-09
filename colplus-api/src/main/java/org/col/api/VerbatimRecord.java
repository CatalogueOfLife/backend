package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public class VerbatimRecord {
  /**
   * dwc archive core id.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the core id.
   */
  private Dataset dataset;

  /**
   * The actual verbatim terms keyed on dwc terms.
   */
  private VerbatimRecordTerms terms = new VerbatimRecordTerms();

  /**
   * Issues related to this taxon with potential values in the map
   */
  private Map<Issue, String> issues = new EnumMap(Issue.class);

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public VerbatimRecordTerms getTerms() {
    return terms;
  }

  public void setTerms(VerbatimRecordTerms terms) {
    this.terms = terms;
  }

  /**
   * @return The dwca coreId
   */
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  /**
   * Get the value of a specific term.
   */
  @Nullable
  public String getCoreTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return terms.getCore().get(term);
  }

  /**
   * @return true if a verbatim term exists and is not null or an empty string
   */
  public boolean hasCoreTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return !Strings.isNullOrEmpty(terms.getCore().get(term));
  }

  /**
   * For setting a specific field without having to replace the entire core Map.
   *
   * @param term the term to set
   * @param value the term's value
   */
  public void setCoreTerm(Term term, @Nullable String value) {
    checkNotNull(term, "term can't be null");
    terms.getCore().put(term, value);
  }

  /**
   * @return list of extension row types
   */
  @JsonIgnore
  public Set<Term> getExtensionRowTypes() {
    return terms.getExtensions().keySet();
  }

  /**
   * @return true if at least one extension record exists
   */
  public boolean hasExtension(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    return terms.getExtensions().containsKey(rowType) && !terms.getExtensions().get(rowType).isEmpty();
  }

  /**
   * @return true if at least one extension record exists
   */
  public List<TermRecord> getExtensionRecords(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    return terms.getExtensions().get(rowType);
  }

  /**
   * Sets all extension records for a given rowType, replacing anything that might have existed for that rowType.
   */
  public void setExtensionRecords(Term rowType, List<TermRecord> extensionRecords) {
    checkNotNull(rowType, "term can't be null");
    terms.getExtensions().put(rowType, extensionRecords);
  }

  /**
   * Adds a new extension record for the given rowType
   */
  public void addExtensionRecord(Term rowType, TermRecord extensionRecord) {
    checkNotNull(rowType, "term can't be null");
    if (!terms.getExtensions().containsKey(rowType)) {
      terms.getExtensions().put(rowType, Lists.newArrayList());
    }
    terms.getExtensions().get(rowType).add(extensionRecord);
  }

  public Map<Issue, String> getIssues() {
    return issues;
  }

  public void setIssues(Map<Issue, String> issues) {
    this.issues = issues;
  }

  public void addIssue(Issue issue) {
    issues.put(issue, null);
  }

  public void addIssue(Issue issue, Object value) {
    issues.put(issue, value.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VerbatimRecord that = (VerbatimRecord) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(dataset, that.dataset) &&
        Objects.equals(terms, that.terms) &&
        Objects.equals(issues, that.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, dataset, terms, issues);
  }
}