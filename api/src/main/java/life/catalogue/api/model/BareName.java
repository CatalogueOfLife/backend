package life.catalogue.api.model;

import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.Objects;

/**
 * A name without any associated name usage, i.e. no {@link Taxon} nor {@link Synonym}.
 * We indicate this by having no taxonomic status at all.
 */
public class BareName implements NameUsage {
  private Name name;
  
  public BareName() {
  }
  
  public BareName(Name name) {
    this.name = name;
  }


  @Override
  public String getLabel() {
    return name.getLabel(false);
  }

  @Override
  public String getLabelHtml() {
    return name.getLabel(true);
  }

  @Override
  public String getId() {
    return null;
  }
  
  @Override
  public void setId(String id) {
    // Do nothing
  }
  
  @Override
  public Integer getDatasetKey() {
    return name.getDatasetKey();
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    name.setDatasetKey(key);
  }
  
  @Override
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public String getAccordingToId() {
    return null;
  }
  
  @Override
  public void setAccordingToId(String according) {
    // nothing, throw if new status is supposed to be non null
    if (according != null) throw new IllegalArgumentException("Bare names do not have an accordingTo");
  }
  
  @Override
  public String getRemarks() {
    return name.getRemarks();
  }
  
  @Override
  public void setRemarks(String remarks) {
    name.setRemarks(remarks);
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return null;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    // nothing, throw if new status is supposed to be non null
    if (status != null) throw new IllegalArgumentException("Bare names do not have a taxonomic status");
  }
  
  @Override
  public Origin getOrigin() {
    return name.getOrigin();
  }
  
  @Override
  public void setOrigin(Origin origin) {
    name.setOrigin(origin);
  }
  
  @Override
  public Integer getVerbatimKey() {
    return name.getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    name.setVerbatimKey(verbatimKey);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BareName that = (BareName) o;
    return Objects.equals(name, that.name);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
  
}
