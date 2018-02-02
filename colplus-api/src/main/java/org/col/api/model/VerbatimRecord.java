package org.col.api.model;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.gbif.dwc.terms.Term;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 *
 */
public class VerbatimRecord {
  /**
   * dwc archive core id.
   */
  private String id;

  private Integer datasetKey;

  /**
   * The actual verbatim terms keyed on dwc terms.
   */
  private VerbatimRecordTerms terms = new VerbatimRecordTerms();

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
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
   * Get the first non blank term for a list of terms.
   * 
   * @param terms list to try
   */
  @Nullable
  public String getFirst(Term... terms) {
    return this.terms.getCore().getFirst(terms);
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
    return terms.getExtensions().containsKey(rowType)
        && !terms.getExtensions().get(rowType).isEmpty();
  }

  /**
   * @return true if at least one extension record exists
   */
  public List<TermRecord> getExtensionRecords(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    return terms.getExtensions().get(rowType);
  }

  /**
   * Sets all extension records for a given rowType, replacing anything that might have existed for
   * that rowType.
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    VerbatimRecord that = (VerbatimRecord) o;
    return Objects.equals(id, that.id) && Objects.equals(datasetKey, that.datasetKey)
        && Objects.equals(terms, that.terms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, terms);
  }
}
