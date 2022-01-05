package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * A taxonomic synonym, linking a name to potentially multiple taxa.
 * Can be used for both homo-and heterotypic synonyms as well as misapplied names.
 */
public class Synonym extends NameUsageBase {
  
  private Taxon accepted;

  public Synonym() {
  }

  public Synonym(NameUsageBase other) {
    super(other);
  }

  public Synonym(Synonym other) {
    super(other);
    this.accepted = other.accepted;
  }

  public Synonym(SimpleName sn) {
    super(sn);
  }

  @Override
  public String getLabel(boolean html) {
    return getLabelBuilder(getName(), accepted != null ? accepted.isExtinct() : null, getNamePhrase(), getAccordingTo(), html).toString();
  }

  @Override
  public void setStatus(TaxonomicStatus status) {
    if (!Preconditions.checkNotNull(status).isSynonym()) {
      throw new IllegalArgumentException("Synonym cannot have a " + status + " status");
    }
    super.setStatus(status);
  }
  
  public Taxon getAccepted() {
    return accepted;
  }
  
  public void setAccepted(Taxon accepted) {
    this.accepted = accepted;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Synonym synonym = (Synonym) o;
    return Objects.equals(accepted, synonym.accepted);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accepted);
  }
}
