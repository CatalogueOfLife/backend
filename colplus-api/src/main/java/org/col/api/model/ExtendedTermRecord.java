package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gbif.dwc.terms.Term;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An extended term records with associated extensions.
 * E.g. a DwC star record.
 */
public class ExtendedTermRecord extends TermRecord {

  /**
   * The verbatim extension records, e.g. as read by a dwc star record, keyed on the extension
   */
  private Map<Term, List<TermRecord>> extensions = Maps.newHashMap();

  public ExtendedTermRecord() {

  }

  public ExtendedTermRecord(int line, String file, Term type) {
    super(line, file, type);
  }

  public Map<Term, List<TermRecord>> getExtensions() {
    return extensions;
  }

  public void setExtensions(Map<Term, List<TermRecord>> extensions) {
    this.extensions = extensions;
  }


  /**
   * @return list of extension row types
   */
  @JsonIgnore
  public Set<Term> getExtensionRowTypes() {
    return extensions.keySet();
  }

  /**
   * @return true if at least one extension record exists
   */
  public boolean hasExtension(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    return extensions.containsKey(rowType)
        && !extensions.get(rowType).isEmpty();
  }

  /**
   * never null
   */
  public List<TermRecord> getExtensionRecords(Term rowType) {
    checkNotNull(rowType, "term can't be null");
    return extensions.getOrDefault(rowType, Lists.newArrayList());
  }

  /**
   * Sets all extension records for a given rowType, replacing anything that might have existed for
   * that rowType.
   */
  public void setExtensionRecords(Term rowType, List<TermRecord> extensionRecords) {
    checkNotNull(rowType, "term can't be null");
    extensions.put(rowType, extensionRecords);
  }

  /**
   * Adds a new extension record for the given rowType
   */
  public void addExtensionRecord(Term rowType, TermRecord extensionRecord) {
    checkNotNull(rowType, "term can't be null");
    if (!extensions.containsKey(rowType)) {
      extensions.put(rowType, Lists.newArrayList());
    }
    extensions.get(rowType).add(extensionRecord);
  }

  /**
   * @return the total number of all extension records
   */
  public int extendedSize() {
    return extensions.values().stream().mapToInt(List::size).sum();
  }

  @Override
  public String toString() {
    return "ExtendedRecord{" + getFile() + "#" + getLine()+", "+ size() + " terms, "+extendedSize()+" ext.rec";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExtendedTermRecord that = (ExtendedTermRecord) o;
    return Objects.equals(extensions, that.extensions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), extensions);
  }
}
