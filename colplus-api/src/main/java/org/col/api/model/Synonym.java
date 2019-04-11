package org.col.api.model;

import java.util.Objects;
import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;
import org.col.api.vocab.TaxonomicStatus;

/**
 * A taxonomic synonym, linking a name to potentially multiple taxa.
 * Can be used for both homo-and heterotypic synonyms as well as misapplied names.
 */
public class Synonym extends NameUsageBase {
  
  @Nonnull
  private Taxon accepted;
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    if (!Preconditions.checkNotNull(status).isSynonym()) {
      throw new IllegalArgumentException("Synonym status required");
    }
    super.setStatus(status);
  }
  
  /**
   * @return true if the synonym is a homotypic synonym for at least one of the accepted names.
   */
  public boolean isHomotypic() {
    return getName().getHomotypicNameId().equals(accepted.getName().getHomotypicNameId());
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
    return accepted.equals(synonym.accepted);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accepted);
  }
}
