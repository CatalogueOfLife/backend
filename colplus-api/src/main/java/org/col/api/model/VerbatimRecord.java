package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class VerbatimRecord {
  /**
   * dwc archive core id.
   */
  private String id;

  private Integer datasetKey;

  private ExtendedTermRecord terms;

  /**
   * @return The dwca coreId
   */
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public ExtendedTermRecord getTerms() {
    return terms;
  }

  public void setTerms(ExtendedTermRecord terms) {
    this.terms = terms;
  }

  /**
   * Get the value of a specific term.
   */
  @Nullable
  public String getTerm(Term term) {
    return terms.get(term);
  }

  /**
   * Get the first non blank term for a list of terms.
   * 
   * @param terms list to try
   */
  @Nullable
  public String getFirst(Term... terms) {
    return this.terms.getFirst(terms);
  }

  /**
   * @return true if a verbatim term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    return !Strings.isNullOrEmpty(terms.get(term));
  }

  /**
   * For setting a specific field without having to replace the entire core Map.
   *
   * @param term the term to set
   * @param value the term's value
   */
  public void setTerm(Term term, @Nullable String value) {
    terms.put(term, value);
  }

  public Map<Term, List<TermRecord>> getExtensions() {
    return terms.getExtensions();
  }

  public void setExtensions(Map<Term, List<TermRecord>> extensions) {
    terms.setExtensions(extensions);
  }

  @JsonIgnore
  public Set<Term> getExtensionRowTypes() {
    return terms.getExtensionRowTypes();
  }

  public boolean hasExtension(Term rowType) {
    return terms.hasExtension(rowType);
  }

  public List<TermRecord> getExtensionRecords(Term rowType) {
    return terms.getExtensionRecords(rowType);
  }

  public void setExtensionRecords(Term rowType, List<TermRecord> extensionRecords) {
    terms.setExtensionRecords(rowType, extensionRecords);
  }

  public void addExtensionRecord(Term rowType, TermRecord extensionRecord) {
    terms.addExtensionRecord(rowType, extensionRecord);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VerbatimRecord that = (VerbatimRecord) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(terms, that.terms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, terms);
  }
}
