package org.col.api.model;

import java.util.Objects;
import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

/**
 * A taxonomic synonym, linking a name to potentially multiple taxa.
 * Can be used for both homo-and heterotypic synonyms as well as misapplied names.
 */
public class Synonym extends DataEntity implements NameUsage {
  
  private String id;
  @Nonnull
  private Name name;
  @Nonnull
  private Taxon accepted;
  @Nonnull
  private Origin origin;
  private Integer verbatimKey;
  private TaxonomicStatus status;
  private String accordingTo;
  private String remarks;
  
  @Override
  public String getId() {
    return id;
  }
  
  @Override
  public void setId(String id) {
    this.id = id;
  }
  
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
  
  private static TaxonomicStatus onlySynonym(TaxonomicStatus status) {
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
  
  public String getRemarks() {
    return remarks;
  }
  
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }
  
  /**
   * @return true if the synonym is a homotypic synonym for at least one of the accepted names.
   */
  public boolean isHomotypic() {
    return name.getHomotypicNameId().equals(accepted.getName().getHomotypicNameId());
  }
  
  public Taxon getAccepted() {
    return accepted;
  }
  
  public void setAccepted(Taxon accepted) {
    this.accepted = accepted;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public Origin getOrigin() {
    return origin;
  }
  
  public void setOrigin(Origin origin) {
    this.origin = origin;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Synonym synonym = (Synonym) o;
    return Objects.equals(id, synonym.id) &&
        Objects.equals(name, synonym.name) &&
        status == synonym.status &&
        Objects.equals(accordingTo, synonym.accordingTo) &&
        Objects.equals(remarks, synonym.remarks) &&
        Objects.equals(accepted, synonym.accepted) &&
        origin == synonym.origin &&
        Objects.equals(verbatimKey, synonym.verbatimKey);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(id, name, status, accordingTo, remarks, accepted, origin, verbatimKey);
  }
}
