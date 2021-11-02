package life.catalogue.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.undercouch.citeproc.csl.CSLName;

public class CslName {
  
  private String family;
  private String given;
  private String literal;

  @JsonProperty("dropping-particle")
  private String droppingParticle;
  @JsonProperty("non-dropping-particle")
  private String nonDroppingParticle;
  private String suffix;
  private Boolean isInstitution;

  public CslName() {
  }

  public CslName(String literal) {
    this.literal = literal;
  }

  public CslName(String given, String family) {
    this.family = family;
    this.given = given;
  }

  public CslName(String given, String family, String literal) {
    this.family = family;
    this.given = given;
    this.literal = literal;
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
  
  public String getSuffix() {
    return suffix;
  }
  
  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public String getLiteral() {
    return literal;
  }
  
  public void setLiteral(String literal) {
    this.literal = literal;
  }

  public Boolean getIsInstitution() {
    return isInstitution;
  }
  
  public void setIsInstitution(Boolean isInstitution) {
    this.isInstitution = isInstitution;
  }

  public CSLName toCSL() {
    return new CSLName(getFamily(), getGiven(), getDroppingParticle(),
      getNonDroppingParticle(), getSuffix(), null, null,
      null, null, getLiteral(), null,
      getIsInstitution());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CslName)) return false;
    CslName cslName = (CslName) o;
    return Objects.equals(family, cslName.family)
           && Objects.equals(given, cslName.given)
           && Objects.equals(literal, cslName.literal)
           && Objects.equals(droppingParticle, cslName.droppingParticle)
           && Objects.equals(nonDroppingParticle, cslName.nonDroppingParticle)
           && Objects.equals(suffix, cslName.suffix)
           && Objects.equals(isInstitution, cslName.isInstitution);
  }

  @Override
  public int hashCode() {
    return Objects.hash(family, given, literal, droppingParticle, nonDroppingParticle, suffix, isInstitution);
  }
}
