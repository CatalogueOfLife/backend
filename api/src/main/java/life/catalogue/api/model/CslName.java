package life.catalogue.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.undercouch.citeproc.csl.CSLName;

public class CslName {
  
  private String family;
  private String given;
  @JsonProperty("dropping-particle")
  private String droppingParticle;
  @JsonProperty("non-dropping-particle")
  private String nonDroppingParticle;
  private String suffix;
  private Boolean isInstitution;
  private String orcid; // at this stage we only use this for CrossRef, see DoiResolver!

  public CslName() {
  }

  public CslName(String family) {
    this.family = family;
  }

  public CslName(String given, String family) {
    this.family = family;
    this.given = given;
  }

  public CslName(String given, String family, String nonDroppingParticle) {
    this.family = family;
    this.given = given;
    this.nonDroppingParticle = nonDroppingParticle;
  }

  public String getFamily() {
    return family;
  }
  
  public void setFamily(String family) {
    this.family = family;
  }
  
  public String getGiven() {
    return given;
  }
  
  public void setGiven(String given) {
    this.given = given;
  }
  
  public String getDroppingParticle() {
    return droppingParticle;
  }
  
  public void setDroppingParticle(String droppingParticle) {
    this.droppingParticle = droppingParticle;
  }
  
  public String getNonDroppingParticle() {
    return nonDroppingParticle;
  }
  
  public void setNonDroppingParticle(String nonDroppingParticle) {
    this.nonDroppingParticle = nonDroppingParticle;
  }

  public void addNonDroppingParticle(String nonDroppingParticle) {
    if (nonDroppingParticle != null && !nonDroppingParticle.trim().isEmpty()) {
      if (this.nonDroppingParticle == null) {
        this.nonDroppingParticle = nonDroppingParticle;
      } else {
        this.nonDroppingParticle = this.nonDroppingParticle + " " + nonDroppingParticle;
      }
    }
  }

  public String getSuffix() {
    return suffix;
  }
  
  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public Boolean getIsInstitution() {
    return isInstitution;
  }
  
  public void setIsInstitution(Boolean isInstitution) {
    this.isInstitution = isInstitution;
  }

  public String getOrcid() {
    return orcid;
  }

  public void setOrcid(String orcid) {
    this.orcid = orcid;
  }

  public CSLName toCSL() {
    return new CSLName(getFamily(), getGiven(), getDroppingParticle(),
      getNonDroppingParticle(), getSuffix(), null, null,
      null, null, null, null,
      getIsInstitution());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CslName)) return false;
    CslName cslName = (CslName) o;
    return Objects.equals(family, cslName.family)
           && Objects.equals(given, cslName.given)
           && Objects.equals(droppingParticle, cslName.droppingParticle)
           && Objects.equals(nonDroppingParticle, cslName.nonDroppingParticle)
           && Objects.equals(suffix, cslName.suffix)
           && Objects.equals(isInstitution, cslName.isInstitution)
           && Objects.equals(orcid, cslName.orcid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(family, given, droppingParticle, nonDroppingParticle, suffix, isInstitution, orcid);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (nonDroppingParticle != null) {
      sb.append(nonDroppingParticle);
    }
    if (family != null) {
      if (sb.length()>0) {
        sb.append(" ");
      }
      sb.append(family);
    }
    if (suffix != null) {
      if (sb.length()>0) {
        sb.append(" ");
      }
      sb.append(suffix);
    }
    if (given != null) {
      if (sb.length()>0) {
        sb.append(", ");
      }
      sb.append(given);
    }
    return sb.toString();
  }
}
