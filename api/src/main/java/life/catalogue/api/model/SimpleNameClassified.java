package life.catalogue.api.model;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Objects;

public class SimpleNameClassified<T extends SimpleName> extends SimpleNameCached {
  // classification starting with direct parent
  private List<T> classification;

  public static SimpleNameClassified<SimpleNameCached> snc(String id, Rank rank, NomCode code, TaxonomicStatus status, String name, String authorship) {
    var sn = new SimpleNameClassified<SimpleNameCached>();
    sn.setId(id);
    sn.setName(name);
    sn.setAuthorship(authorship);
    sn.setRank(rank);
    sn.setCode(code);
    sn.setStatus(status);
    return sn;
  }

  public SimpleNameClassified() {
  }

  public SimpleNameClassified(SimpleName other) {
    super(other);
  }

  public SimpleNameClassified(NameUsageBase other, Integer canonicalId) {
    super(other, canonicalId);
  }

  public SimpleNameClassified(SimpleNameWithNidx other) {
    super(other);
  }

  public SimpleNameClassified(SimpleNameCached other) {
    super(other);
  }

  public SimpleNameClassified(SimpleNameClassified<T> other) {
    super(other);
    this.classification = other.classification;
  }

  public SimpleNameClassified(SimpleNameCached other, List<T> classification) {
    super(other);
    this.classification = classification;
  }

  public static <T extends SimpleName> SimpleNameClassified<T> canonicalCopy(SimpleNameClassified<T> src) {
    var snc =  new SimpleNameClassified<>(src);
    snc.setAuthorship(null);
    snc.setNamesIndexId(src.getCanonicalId());
    snc.setNamesIndexMatchType(MatchType.CANONICAL);
    return snc;
  }

  public boolean hasClassification() {
    return classification != null && !classification.isEmpty();
  }

  public List<T> getClassification() {
    return classification;
  }

  public void setClassification(List<T> classification) {
    this.classification = classification;
  }

  public T getByRank(Rank rank) {
    for (T t : classification) {
      if (t.getRank() == rank) {
        return t;
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameClassified)) return false;
    if (!super.equals(o)) return false;
    SimpleNameClassified that = (SimpleNameClassified) o;
    return Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classification);
  }
}
