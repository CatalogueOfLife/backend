package org.col.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.col.api.jackson.ExtensionSerde;
import org.col.api.jackson.TermMapListSerde;
import org.col.api.vocab.Extension;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public class VerbatimRecord {
  private String id;

  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Dataset dataset;

  /**
   * The actual verbatim data keyed on dwc terms.
   */
  @JsonIgnore
  private VerbatimRecordTerms data;

  /**
   * Issues related to this taxon with potential values in the map
   */
  private Map<Issue, String> issues = new EnumMap(Issue.class);


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
   * A map of extension records, holding all verbatim extension terms.
   */
  @JsonSerialize(keyUsing = ExtensionSerde.Serializer.class, contentUsing = TermMapListSerde.Serializer.class)
  public Map<Term, List<Map<Term, String>>> getExtensions() {
    return data.getExtensions();
  }

  @JsonDeserialize(keyUsing = ExtensionSerde.ExtensionKeyDeserializer.class, contentUsing = TermMapListSerde.Deserializer.class)
  public void setExtensions(Map<Term, List<Map<Term, String>>> extensions) {
    this.data.setExtensions(extensions);
  }

  /**
   * Get the value of a specific term.
   */
  @Nullable
  public String getCoreTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return data.getCore().get(term);
  }

  /**
   * @return true if a verbatim term exists and is not null or an empty string
   */
  public boolean hasCoreTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return !Strings.isNullOrEmpty(data.getCore().get(term));
  }

  /**
   * @return true if at least one extension record exists
   */
  public boolean hasExtension(Extension extension) {
    return data.getExtensions().containsKey(extension) && !data.getExtensions().get(extension).isEmpty();
  }

  public boolean hasExtension(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    Extension ext = Extension.fromRowType(rowType.qualifiedName());
    return ext != null && hasExtension(ext);
  }

  /**
   * For setting a specific field without having to replace the entire core Map.
   *
   * @param term the term to set
   * @param value the term's value
   */
  public void setCoreTerm(Term term, @Nullable String value) {
    checkNotNull(term, "term can't be null");
    data.getCore().put(term, value);
  }

  /**
   * This private method is only for deserialization via jackson and not exposed anywhere else!
   */
  @JsonAnySetter
  private void addJsonVerbatimField(String key, String value) {
    Term t = TermFactory.instance().findTerm(key);
    data.getCore().put(t, value);
  }

  /**
   * This private method is only for serialization via jackson and not exposed anywhere else!
   * It maps the verbatimField terms into properties with their full qualified name.
   */
  @JsonAnyGetter
  private Map<String, String> jsonVerbatimFields() {
    Map<String, String> extendedProps = Maps.newHashMap();
    for (Map.Entry<Term, String> prop : data.getCore().entrySet()) {
      extendedProps.put(prop.getKey().qualifiedName(), prop.getValue());
    }
    return extendedProps;
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
        Objects.equals(data, that.data) &&
        Objects.equals(issues, that.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, dataset, data, issues);
  }
}