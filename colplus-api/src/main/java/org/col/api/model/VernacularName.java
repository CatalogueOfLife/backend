package org.col.api.model;

import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
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
  private Set<String> referenceIds = Sets.newHashSet();
  
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
  public Set<String> getReferenceIds() {
    return referenceIds;
  }
  
  public void setReferenceIds(Set<String> referenceIds) {
    this.referenceIds = referenceIds;
  }
  
  public void addReferenceId(String referenceId) {
    this.referenceIds.add(referenceId);
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
        Objects.equals(referenceIds, that.referenceIds);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, verbatimKey, name, latin, language, country, referenceIds);
  }
}
