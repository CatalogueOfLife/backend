package org.col.api.model;

import java.util.Objects;

import com.google.common.base.Preconditions;
import org.col.api.vocab.TaxonomicStatus;

/**
 * A taxonomic synonym, linking a name to potentially multiple taxa.
 * Can be used for both homo-and heterotypic synonyms as well as misapplied names.
 */
public class Synonym implements NameUsage {

  private Name name;
  private TaxonomicStatus status;
  private String accordingTo;
  private Taxon accepted;

  @Override
  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = onlySynonym(status);
  }

  private static TaxonomicStatus onlySynonym(TaxonomicStatus status){
    Preconditions.checkArgument(status != null && status.isSynonym(), "Synonym status required");
    return status;
  }

  /**
   * Any informal note found in the name that informs about the taxonomic concept.
   * For example "sensu latu"
   */
  @Override
  public String getAccordingTo() {
    return accordingTo;
  }

  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }

  /**
   * @return true if the synonym is a homotypic synonym for at least one of the accepted names.
   */
  public boolean isHomotypic() {
    return name.getHomotypicNameKey().equals(accepted.getName().getHomotypicNameKey());
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
    Synonym synonym = (Synonym) o;
    return Objects.equals(name, synonym.name) &&
        status == synonym.status &&
        Objects.equals(accordingTo, synonym.accordingTo) &&
        Objects.equals(accepted, synonym.accepted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, status, accordingTo, accepted);
  }
}
