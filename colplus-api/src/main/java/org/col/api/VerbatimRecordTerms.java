package org.col.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Maps;
import org.col.api.jackson.TermSerde;
import org.gbif.dwc.terms.Term;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public class VerbatimRecordTerms {
  /**
   * The verbatim core of the record
   */
  private TermRecord core = new TermRecord();

  /**
   * The verbatim extension records as read by a dwc star record, keyed on the extension
   */
  @JsonDeserialize(keyUsing = TermSerde.TermKeyDeserializer.class)
  @JsonSerialize(keyUsing = TermSerde.FieldSerializer.class)
  private Map<Term, List<TermRecord>> extensions = Maps.newHashMap();

  public TermRecord getCore() {
    return core;
  }

  public void setCore(TermRecord core) {
    this.core = core;
  }

  public Map<Term, List<TermRecord>> getExtensions() {
    return extensions;
  }

  public void setExtensions(Map<Term, List<TermRecord>> extensions) {
    this.extensions = extensions;
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