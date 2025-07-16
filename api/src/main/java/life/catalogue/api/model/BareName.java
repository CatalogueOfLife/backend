package life.catalogue.api.model;

import life.catalogue.api.jackson.LabelPropertyFilter;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * A name without any associated name usage, i.e. no {@link Taxon} nor {@link Synonym}.
 * We indicate this by having no taxonomic status at all.
 */
public class BareName implements NameUsage {
  private static final Logger LOG = LoggerFactory.getLogger(BareName.class);

  @JsonFilter(LabelPropertyFilter.NAME)
  private Name name;
  
  public BareName() {
  }
  
  public BareName(Name name) {
    this.name = name;
  }

  @Override
  public String getLabel() {
    return name == null ? null : name.getLabel();
  }

  @Override
  public String getLabelHtml() {
    return name == null ? null : name.getLabelHtml();
  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  public String getParentId() {
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
  public Integer getSectorKey() {
    return name.getSectorKey();
  }

  @Override
  public void setSectorKey(Integer sectorKey) {
    name.setSectorKey(sectorKey);
  }

  @Override
  public Sector.Mode getSectorMode() {
    return name.getSectorMode();
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    name.setSectorMode(sectorMode);
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
  public void setAccordingToId(String accordingToID) {
    // nothing, throw if according is supposed to be non null
    if (accordingToID != null) throw new IllegalArgumentException("Bare names do not have an accordingTo");
  }

  @Override
  public String getAccordingTo() {
    return null;
  }

  @Override
  public void setAccordingTo(String accordingTo) {
    // log not throw if unexpected accordingTo is used
    if (!StringUtils.isBlank(accordingTo)) {
      LOG.warn("Bare name with a usage accordingTo: {}", accordingTo);
    }
  }

  @Override
  public String getNamePhrase() {
    return null;
  }

  @Override
  public void setNamePhrase(String namePhrase) {
    // log not throw if unexpected namePhrase is used
    if (!StringUtils.isBlank(namePhrase)) {
      LOG.warn("Bare name with a usage namePhrase: {}", namePhrase);
    }
  }

  @Override
  public String getRemarks() {
    return name == null ? null : name.getRemarks();
  }
  
  @Override
  public void setRemarks(String remarks) {
    if (name != null) {
      name.setRemarks(remarks);
    }
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return TaxonomicStatus.BARE_NAME;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    // nothing, throw if new status is supposed to be no bare name
    if (status != null && !status.isBareName()) {
      throw new IllegalArgumentException("BareName cannot have a " + status + " status");
    }
  }
  
  @Override
  public Origin getOrigin() {
    return name == null ? null : name.getOrigin();
  }
  
  @Override
  public void setOrigin(Origin origin) {
    if (name != null) {
      name.setOrigin(origin);
    }
  }
  
  @Override
  public Integer getVerbatimKey() {
    return name == null ? null : name.getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    if (name != null) {
      name.setVerbatimKey(verbatimKey);
    }
  }

  @Override
  public Integer getVerbatimSourceKey() {
    return name == null ? null : name.getVerbatimSourceKey();
  }

  @Override
  public void setVerbatimSourceKey(Integer verbatimKey) {
    if (name != null) {
      name.setVerbatimSourceKey(verbatimKey);
    }
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
