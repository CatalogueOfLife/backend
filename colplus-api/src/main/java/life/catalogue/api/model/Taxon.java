package life.catalogue.api.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import life.catalogue.api.vocab.GeoTime;
import life.catalogue.api.vocab.Lifezone;
import life.catalogue.api.vocab.TaxonomicStatus;

/**
 *
 */
public class Taxon extends NameUsageBase {
  
  private LocalDate accordingToDate;
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
  
  public LocalDate getAccordingToDate() {
    return accordingToDate;
  }
  
  public void setAccordingToDate(LocalDate accordingToDate) {
    this.accordingToDate = accordingToDate;
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
    return Objects.equals(accordingToDate, taxon.accordingToDate) &&
        Objects.equals(extinct, taxon.extinct) &&
        Objects.equals(temporalRangeStart, taxon.temporalRangeStart) &&
        Objects.equals(temporalRangeEnd, taxon.temporalRangeEnd) &&
        Objects.equals(lifezones, taxon.lifezones);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accordingToDate, extinct, temporalRangeStart, temporalRangeEnd, lifezones);
  }
}
