package org.col.admin.importer.neo.model;

import java.util.Objects;
import java.util.Set;

import org.col.api.model.VerbatimEntity;
import org.col.api.vocab.Issue;

public class NeoNameRel implements VerbatimEntity {
  private Integer verbatimKey;
  private RelType type;
  private Integer refKey;
  private String note;

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }

  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  @Override
  public Set<Issue> getIssues() {
    return null;
  }

  @Override
  public void addIssue(Issue issue) {

  }

  public RelType getType() {
    return type;
  }

  public void setType(RelType type) {
    this.type = type;
  }

  public Integer getRefKey() {
    return refKey;
  }

  public void setRefKey(Integer refKey) {
    this.refKey = refKey;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoNameRel that = (NeoNameRel) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        type == that.type &&
        Objects.equals(refKey, that.refKey) &&
        Objects.equals(note, that.note);
  }

  @Override
  public int hashCode() {

    return Objects.hash(verbatimKey, type, refKey, note);
  }
}
