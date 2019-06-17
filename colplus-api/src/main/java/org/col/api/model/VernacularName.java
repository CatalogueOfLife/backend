package org.col.api.model;

import java.util.Objects;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.Country;

public class VernacularName extends DataEntity implements Referenced, VerbatimEntity, GlobalEntity {
  
  @JsonIgnore
  private Integer key;
  private Integer verbatimKey;
  private String name;
  private String latin;
  @Size(min = 3, max = 3)
  private String language;
  private Country country;
  private String referenceId;
  
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  /**
   * Transliterated name into the latin script.
   */
  public String getLatin() {
    return latin;
  }
  
  public void setLatin(String latin) {
    this.latin = latin;
  }
  
  public String getLanguage() {
    return language;
  }
  
  public void setLanguage(String language) {
    this.language = language;
  }
  
  public Country getCountry() {
    return country;
  }
  
  public void setCountry(Country country) {
    this.country = country;
  }
  
  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VernacularName that = (VernacularName) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(name, that.name) &&
        Objects.equals(latin, that.latin) &&
        Objects.equals(language, that.language) &&
        country == that.country &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, verbatimKey, name, latin, language, country, referenceId);
  }
  
  @Override
  public String toString() {
    return "VernacularName{" + key + " " + name + "/" + language +  "}";
  }
}
