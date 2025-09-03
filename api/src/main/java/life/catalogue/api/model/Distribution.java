package life.catalogue.api.model;

import life.catalogue.api.vocab.*;

import java.util.Objects;

/**
 *
 */
public class Distribution extends DatasetScopedEntity<Integer> implements ExtensionEntity {

  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer verbatimKey;
  private Integer verbatimSourceKey;
  private Area area;
  private EstablishmentMeans establishmentMeans;
  private DegreeOfEstablishment degreeOfEstablishment;
  private String pathway;
  private ThreatStatus threatStatus;
  private Integer year;
  private Season season;
  private String lifeStage;
  private String referenceId;
  private String remarks;

  @Override
  public Integer getSectorKey() {
    return sectorKey;
  }

  @Override
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public Integer getVerbatimSourceKey() {
    return verbatimSourceKey;
  }

  public void setVerbatimSourceKey(Integer verbatimSourceKey) {
    this.verbatimSourceKey = verbatimSourceKey;
  }

  public Area getArea() {
    return area;
  }
  
  public void setArea(Area area) {
    this.area = area;
  }

  public EstablishmentMeans getEstablishmentMeans() {
    return establishmentMeans;
  }

  public void setEstablishmentMeans(EstablishmentMeans establishmentMeans) {
    this.establishmentMeans = establishmentMeans;
  }

  public DegreeOfEstablishment getDegreeOfEstablishment() {
    return degreeOfEstablishment;
  }

  public void setDegreeOfEstablishment(DegreeOfEstablishment degreeOfEstablishment) {
    this.degreeOfEstablishment = degreeOfEstablishment;
  }

  public String getPathway() {
    return pathway;
  }

  public void setPathway(String pathway) {
    this.pathway = pathway;
  }

  public ThreatStatus getThreatStatus() {
    return threatStatus;
  }

  public void setThreatStatus(ThreatStatus threatStatus) {
    this.threatStatus = threatStatus;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }

  public Season getSeason() {
    return season;
  }

  public void setSeason(Season season) {
    this.season = season;
  }

  public String getLifeStage() {
    return lifeStage;
  }

  public void setLifeStage(String lifeStage) {
    this.lifeStage = lifeStage;
  }

  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Distribution)) return false;
    if (!super.equals(o)) return false;

    Distribution that = (Distribution) o;
    return Objects.equals(sectorKey, that.sectorKey) &&
      sectorMode == that.sectorMode &&
      Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(verbatimSourceKey, that.verbatimSourceKey) &&
      Objects.equals(area, that.area) &&
      establishmentMeans == that.establishmentMeans &&
      degreeOfEstablishment == that.degreeOfEstablishment &&
      Objects.equals(pathway, that.pathway) &&
      threatStatus == that.threatStatus &&
      Objects.equals(year, that.year) &&
      season == that.season &&
      Objects.equals(lifeStage, that.lifeStage) &&
      Objects.equals(referenceId, that.referenceId) &&
      Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, sectorMode, verbatimKey, verbatimSourceKey, area, establishmentMeans, degreeOfEstablishment, pathway, threatStatus, year, season, lifeStage, referenceId, remarks);
  }

  @Override
  public String toString() {
    return area.toString();
  }
}
