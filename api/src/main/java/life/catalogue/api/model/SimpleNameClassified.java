package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Objects;

public class SimpleNameClassified<T extends SimpleName> extends SimpleNameWithPub {
  // classificaiton starting with direct parent
  private List<T> classification;

  public static SimpleNameClassified<SimpleName> snc(String id, Rank rank, NomCode code, TaxonomicStatus status, String name, String authorship) {
    var sn = new SimpleNameClassified<>();
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

  public SimpleNameClassified(SimpleNameWithPub other) {
    super(other);
  }

  public List<T> getClassification() {
    return classification;
  }

  public void setClassification(List<T> classification) {
    this.classification = classification;
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
