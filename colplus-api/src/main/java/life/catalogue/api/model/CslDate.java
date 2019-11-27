package life.catalogue.api.model;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CslDate {
  
  @JsonProperty("date-parts")
  private int[][] dateParts;
  private String season;
  private Boolean circa;
  private String literal;
  private String raw;
  
  public int[][] getDateParts() {
    return dateParts;
  }
  
  public void setDateParts(int[][] dateParts) {
    this.dateParts = dateParts;
  }
  
  public String getSeason() {
    return season;
  }
  
  public void setSeason(String season) {
    this.season = season;
  }
  
  public Boolean getCirca() {
    return circa;
  }
  
  public void setCirca(Boolean circa) {
    this.circa = circa;
  }
  
  public String getLiteral() {
    return literal;
  }
  
  public void setLiteral(String literal) {
    this.literal = literal;
  }
  
  public String getRaw() {
    return raw;
  }
  
  public void setRaw(String raw) {
    this.raw = raw;
  }
  
  @Override
  public int hashCode() {
    int result = 1;
    
    result = 31 * result + Arrays.deepHashCode(dateParts);
    result = 31 * result + ((season == null) ? 0 : season.hashCode());
    result = 31 * result + ((circa == null) ? 0 : circa.hashCode());
    result = 31 * result + ((literal == null) ? 0 : literal.hashCode());
    result = 31 * result + ((raw == null) ? 0 : raw.hashCode());
    
    return result;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof CslDate))
      return false;
    CslDate other = (CslDate) obj;
    
    if (!Arrays.deepEquals(dateParts, other.dateParts))
      return false;
    
    if (season == null) {
      if (other.season != null)
        return false;
    } else if (!season.equals(other.season))
      return false;
    
    if (circa == null) {
      if (other.circa != null)
        return false;
    } else if (!circa.equals(other.circa))
      return false;
    
    if (literal == null) {
      if (other.literal != null)
        return false;
    } else if (!literal.equals(other.literal))
      return false;
    
    if (raw == null) {
      if (other.raw != null)
        return false;
    } else if (!raw.equals(other.raw))
      return false;
    
    return true;
  }
}
