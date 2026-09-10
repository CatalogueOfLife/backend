package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import javax.annotation.Nullable;

public class SimpleNameLink extends SimpleName {
  private boolean broken;
  private boolean stale;

  public static SimpleNameLink of(@Nullable Taxon t) {
    return t == null ? null : t.toSimpleNameLink();
  }

  public static SimpleNameLink of(SimpleName sn) {
    return new SimpleNameLink(sn);
  }

  public static SimpleNameLink of(SimpleNameLink sn) {
    SimpleNameLink snl = new SimpleNameLink(sn);
    snl.broken = sn.broken;
    snl.stale = sn.stale;
    return snl;
  }

  public static SimpleNameLink of(String id) {
    return new SimpleNameLink(id, null, null, null);
  }

  public static SimpleNameLink of(DSID<String> key) {
    return SimpleNameLink.of(key.getId());
  }

  public static SimpleNameLink of(String name, Rank rank) {
    return new SimpleNameLink(null, name, null, rank);
  }

  public static SimpleNameLink of(String id, String name, Rank rank) {
    return new SimpleNameLink(id, name, null, rank);
  }

  public static SimpleNameLink of(String id, String name, String authorship, Rank rank) {
    return new SimpleNameLink(id, name, authorship, rank);
  }

  public SimpleNameLink() {
  }

  private SimpleNameLink(SimpleName src) {
    super(src);
  }

  private SimpleNameLink(String id, String scientificName, String authorship, Rank rank) {
    super(id, scientificName, authorship, rank);
  }

  public boolean isBroken() {
    return broken;
  }

  public void setBroken(boolean broken) {
    this.broken = broken;
  }

  /**
   * True if the id does resolve, but to a usage with a different name than the one stored here.
   */
  public boolean isStale() {
    return stale;
  }

  public void setStale(boolean stale) {
    this.stale = stale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameLink)) return false;
    if (!super.equals(o)) return false;
    SimpleNameLink that = (SimpleNameLink) o;
    return broken == that.broken && stale == that.stale;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), broken, stale);
  }
}
