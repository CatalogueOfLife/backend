package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Maps;
import org.col.api.jackson.ExtensionSerde;
import org.col.api.jackson.TermMapListSerde;
import org.gbif.dwc.terms.Term;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public class VerbatimRecordTerms {
  /**
   * The verbatim taxon core for the usage
   */
  @JsonIgnore
  private Map<Term, String> core = Maps.newHashMap();

  /**
   * The verbatim extension records as read by a dwc star record, keyed on the extension
   */
  @JsonSerialize(keyUsing = ExtensionSerde.Serializer.class, contentUsing = TermMapListSerde.Serializer.class)
  @JsonDeserialize(keyUsing = ExtensionSerde.ExtensionKeyDeserializer.class, contentUsing = TermMapListSerde.Deserializer.class)
  private Map<Term, List<Map<Term, String>>> extensions = Maps.newHashMap();

  /**
   * A map of extension records, holding all verbatim extension terms.
   */
  public Map<Term, List<Map<Term, String>>> getExtensions() {
    return extensions;
  }

  public void setExtensions(Map<Term, List<Map<Term, String>>> extensions) {
    this.extensions = extensions;
  }

  /**
   * A map holding all verbatim core terms.
   */
  public Map<Term, String> getCore() {
    return core;
  }

  public void setCore(Map<Term, String> core) {
    this.core = core;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VerbatimRecordTerms verbatimRecordTerms = (VerbatimRecordTerms) o;
    return Objects.equals(core, verbatimRecordTerms.core) &&
        Objects.equals(extensions, verbatimRecordTerms.extensions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(core, extensions);
  }
}