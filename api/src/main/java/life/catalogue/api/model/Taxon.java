package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import life.catalogue.api.vocab.GeoTime;
import life.catalogue.api.vocab.Lifezone;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.date.FuzzyDate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class Taxon extends NameUsageBase {

  private String scrutinizer;
  private FuzzyDate scrutinizerDate;
  private Boolean extinct;
  private String temporalRangeStart;
  private String temporalRangeEnd;
  private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    if (Preconditions.checkNotNull(status).isSynonym()) {
      throw new IllegalArgumentException("Taxa cannot have a synonym status");
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

  public Set<Lifezone> getLifezones() {
    return lifezones;
  }
  
  public void setLifezones(Set<Lifezone> lifezones) {
    this.lifezones = lifezones;
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
        Objects.equals(lifezones, taxon.lifezones);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), scrutinizer, scrutinizerDate, extinct, temporalRangeStart, temporalRangeEnd, lifezones);
  }
}
