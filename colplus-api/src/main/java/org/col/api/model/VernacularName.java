package org.col.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

public class VernacularName implements Referenced, VerbatimEntity, IntKey {
  
  @JsonIgnore
  private Integer key;
  private Integer verbatimKey;
  private String name;
  private String latin;
  private Language language;
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
  
  public Language getLanguage() {
    return language;
  }
  
  public void setLanguage(Language language) {
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
        language == that.language &&
        country == that.country &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, verbatimKey, name, latin, language, country, referenceId);
  }
}
