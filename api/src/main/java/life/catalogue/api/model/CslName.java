package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CslName {
  
  private String family;
  private String given;
  @JsonProperty("dropping-particle")
  private String droppingParticle;
  @JsonProperty("non-dropping-particle")
  private String nonDroppingParticle;
  private String suffix;
  @JsonProperty("comma-prefix")
  private Boolean commaPrefix;
  @JsonProperty("comma-suffix")
  private Boolean commaSuffix;
  private Boolean staticOrdering;
  private Boolean staticParticles;
  private String literal;
  private Boolean parseNames;
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
  
  public Boolean getCommaPrefix() {
    return commaPrefix;
  }
  
  public void setCommaPrefix(Boolean commaPrefix) {
    this.commaPrefix = commaPrefix;
  }
  
  public Boolean getCommaSuffix() {
    return commaSuffix;
  }
  
  public void setCommaSuffix(Boolean commaSuffix) {
    this.commaSuffix = commaSuffix;
  }
  
  public Boolean getStaticOrdering() {
    return staticOrdering;
  }
  
  public void setStaticOrdering(Boolean staticOrdering) {
    this.staticOrdering = staticOrdering;
  }
  
  public Boolean getStaticParticles() {
    return staticParticles;
  }
  
  public void setStaticParticles(Boolean staticParticles) {
    this.staticParticles = staticParticles;
  }
  
  public String getLiteral() {
    return literal;
  }
  
  public void setLiteral(String literal) {
    this.literal = literal;
  }
  
  public Boolean getParseNames() {
    return parseNames;
  }
  
  public void setParseNames(Boolean parseNames) {
    this.parseNames = parseNames;
  }
  
  public Boolean getIsInstitution() {
    return isInstitution;
  }
  
  public void setIsInstitution(Boolean isInstitution) {
    this.isInstitution = isInstitution;
  }
  
  @Override
  public int hashCode() {
    int result = 1;
    
    result = 31 * result + ((family == null) ? 0 : family.hashCode());
    result = 31 * result + ((given == null) ? 0 : given.hashCode());
    result = 31
        * result
        + ((droppingParticle == null) ? 0 : droppingParticle.hashCode());
    result = 31
        * result
        + ((nonDroppingParticle == null) ? 0 : nonDroppingParticle
        .hashCode());
    result = 31 * result + ((suffix == null) ? 0 : suffix.hashCode());
    result = 31 * result
        + ((commaPrefix == null) ? 0 : commaPrefix.hashCode());
    result = 31 * result
        + ((commaSuffix == null) ? 0 : commaSuffix.hashCode());
    result = 31 * result
        + ((staticOrdering == null) ? 0 : staticOrdering.hashCode());
    result = 31 * result
        + ((staticParticles == null) ? 0 : staticParticles.hashCode());
    result = 31 * result + ((literal == null) ? 0 : literal.hashCode());
    result = 31 * result
        + ((parseNames == null) ? 0 : parseNames.hashCode());
    result = 31 * result
        + ((isInstitution == null) ? 0 : isInstitution.hashCode());
    
    return result;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof CslName))
      return false;
    CslName other = (CslName) obj;
    
    if (family == null) {
      if (other.family != null)
        return false;
    } else if (!family.equals(other.family))
      return false;
    
    if (given == null) {
      if (other.given != null)
        return false;
    } else if (!given.equals(other.given))
      return false;
    
    if (droppingParticle == null) {
      if (other.droppingParticle != null)
        return false;
    } else if (!droppingParticle.equals(other.droppingParticle))
      return false;
    
    if (nonDroppingParticle == null) {
      if (other.nonDroppingParticle != null)
        return false;
    } else if (!nonDroppingParticle.equals(other.nonDroppingParticle))
      return false;
    
    if (suffix == null) {
      if (other.suffix != null)
        return false;
    } else if (!suffix.equals(other.suffix))
      return false;
    
    if (commaPrefix == null) {
      if (other.commaPrefix != null)
        return false;
    } else if (!commaPrefix.equals(other.commaPrefix))
      return false;
    
    if (commaSuffix == null) {
      if (other.commaSuffix != null)
        return false;
    } else if (!commaSuffix.equals(other.commaSuffix))
      return false;
    
    if (staticOrdering == null) {
      if (other.staticOrdering != null)
        return false;
    } else if (!staticOrdering.equals(other.staticOrdering))
      return false;
    
    if (staticParticles == null) {
      if (other.staticParticles != null)
        return false;
    } else if (!staticParticles.equals(other.staticParticles))
      return false;
    
    if (literal == null) {
      if (other.literal != null)
        return false;
    } else if (!literal.equals(other.literal))
      return false;
    
    if (parseNames == null) {
      if (other.parseNames != null)
        return false;
    } else if (!parseNames.equals(other.parseNames))
      return false;
    
    if (isInstitution == null) {
      if (other.isInstitution != null)
        return false;
    } else if (!isInstitution.equals(other.isInstitution))
      return false;
    
    return true;
  }
}
