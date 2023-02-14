package life.catalogue.api.model;

import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.GeoTime;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.date.FuzzyDate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

/**
 *
 */
public class Taxon extends NameUsageBase {
  private String scrutinizer;
  private FuzzyDate scrutinizerDate;
  private Boolean extinct;
  private String temporalRangeStart;
  private String temporalRangeEnd;
  private Set<Environment> environments = EnumSet.noneOf(Environment.class);

  public Taxon() {
  }

  public Taxon(Name n) {
    super(n);
  }

  public Taxon(NameUsageBase other) {
    super(other);
  }

  public Taxon(Taxon other) {
    super(other);
    this.scrutinizer = other.scrutinizer;
    this.scrutinizerDate = other.scrutinizerDate;
    this.extinct = other.extinct;
    this.temporalRangeStart = other.temporalRangeStart;
    this.temporalRangeEnd = other.temporalRangeEnd;
    this.environments = other.environments;
  }

  public Taxon(SimpleName sn) {
    super(sn);
  }

  @Override
  public NameUsageBase copy() {
    return new Taxon(this);
  }

  @Override
  public String getLabel(boolean html) {
    return labelBuilder(getName(), extinct, getStatus(), getNamePhrase(), getAccordingTo(), html).toString();
  }

  @Override
  public void setStatus(TaxonomicStatus status) {
    if (!Preconditions.checkNotNull(status).isTaxon()) {
      throw new IllegalArgumentException("Taxa cannot have a " + status + " status");
    }
    super.setStatus(status);
  }
  
  @JsonIgnore
  public boolean isProvisional() {
    return getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
  }

  public String getScrutinizer() {
    return scrutinizer;
  }

  public void setScrutinizer(String scrutinizer) {
    this.scrutinizer = scrutinizer;
  }

  public FuzzyDate getScrutinizerDate() {
    return scrutinizerDate;
  }
  
  public void setScrutinizerDate(FuzzyDate scrutinizerDate) {
    this.scrutinizerDate = scrutinizerDate;
  }
  
  public Boolean isExtinct() {
    return extinct;
  }
  
  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }
  
  public String getTemporalRangeStart() {
    return temporalRangeStart;
  }
  
  public void setTemporalRangeStart(String temporalRangeStart) {
    this.temporalRangeStart = temporalRangeStart;
  }
  
  public void setTemporalRangeStart(GeoTime start) {
    this.temporalRangeStart = start == null ? null : start.getName();
  }

  public String getTemporalRangeEnd() {
    return temporalRangeEnd;
  }
  
  public void setTemporalRangeEnd(String temporalRangeEnd) {
    this.temporalRangeEnd = temporalRangeEnd;
  }
  
  public void setTemporalRangeEnd(GeoTime end) {
    this.temporalRangeEnd = end == null ? null : end.getName();
  }

  public Set<Environment> getEnvironments() {
    return environments;
  }
  
  public void setEnvironments(Set<Environment> environments) {
    this.environments = environments == null ? EnumSet.noneOf(Environment.class) : EnumSet.copyOf(environments);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Taxon taxon = (Taxon) o;
    return Objects.equals(scrutinizerDate, taxon.scrutinizerDate) &&
        Objects.equals(scrutinizer, taxon.scrutinizer) &&
        Objects.equals(extinct, taxon.extinct) &&
        Objects.equals(temporalRangeStart, taxon.temporalRangeStart) &&
        Objects.equals(temporalRangeEnd, taxon.temporalRangeEnd) &&
        Objects.equals(environments, taxon.environments);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), scrutinizer, scrutinizerDate, extinct, temporalRangeStart, temporalRangeEnd, environments);
  }
}
