package life.catalogue.api.model;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.vocab.MatchType;

import java.util.Objects;

public class SimpleNameWithNidx extends SimpleName {
  private MatchType nameIndexMatchType;
  private IntSet nameIndexIds = new IntOpenHashSet();

  public MatchType getNameIndexMatchType() {
    return nameIndexMatchType;
  }

  public void setNameIndexMatchType(MatchType nameIndexMatchType) {
    this.nameIndexMatchType = nameIndexMatchType;
  }

  public IntSet getNameIndexIds() {
    return nameIndexIds;
  }

  public void setNameIndexIds(IntSet nameIndexIds) {
    this.nameIndexIds = nameIndexIds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithNidx)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithNidx that = (SimpleNameWithNidx) o;
    return nameIndexMatchType == that.nameIndexMatchType &&
      Objects.equals(nameIndexIds, that.nameIndexIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), nameIndexMatchType, nameIndexIds);
  }
}
