package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Language;
import life.catalogue.api.vocab.Sex;

import javax.validation.constraints.Size;
import java.util.Objects;

public class VernacularName extends DatasetScopedEntity<Integer> implements Referenced, VerbatimEntity {
  
  private Integer verbatimKey;
  private String name;
  private String latin;
  @Size(min = 3, max = 3)
  private String language;
  private Country country;
  private String area;
  private Sex sex;
  private String referenceId;
  
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
  
  public void setLanguage(Language language) {
    this.language = language == null ? null : language.getCode();
  }

  public Country getCountry() {
    return country;
  }
  
  public void setCountry(Country country) {
    this.country = country;
  }
  
  public String getArea() {
    return area;
  }
  
  public void setArea(String area) {
    this.area = area;
  }

  public Sex getSex() {
    return sex;
  }

  public void setSex(Sex sex) {
    this.sex = sex;
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
    if (!super.equals(o)) return false;
    VernacularName that = (VernacularName) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
            Objects.equals(name, that.name) &&
            Objects.equals(latin, that.latin) &&
            Objects.equals(language, that.language) &&
            country == that.country &&
            Objects.equals(area, that.area) &&
            sex == that.sex &&
            Objects.equals(referenceId, that.referenceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, name, latin, language, country, area, sex, referenceId);
  }

  @Override
  public String toString() {
    return "VernacularName{" + getId() + " " + name + "/" + language +  "}";
  }
}
