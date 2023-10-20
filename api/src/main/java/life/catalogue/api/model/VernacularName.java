package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Language;
import life.catalogue.api.vocab.Sex;

import java.util.Objects;

import javax.validation.constraints.Size;

public class VernacularName extends DatasetScopedEntity<Integer> implements SectorScopedEntity<Integer>, Referenced, VerbatimEntity, Remarkable {

  private Integer sectorKey;
  private Integer verbatimKey;
  private String name;
  private String latin;
  private boolean preferred;
  @Size(min = 3, max = 3)
  private String language;
  private Country country;
  private String area;
  private Sex sex;
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

  public boolean isPreferred() {
    return preferred;
  }

  public void setPreferred(boolean preferred) {
    this.preferred = preferred;
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
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VernacularName)) return false;
    if (!super.equals(o)) return false;
    VernacularName that = (VernacularName) o;
    return preferred == that.preferred
           && Objects.equals(sectorKey, that.sectorKey)
           && Objects.equals(verbatimKey, that.verbatimKey)
           && Objects.equals(name, that.name)
           && Objects.equals(latin, that.latin)
           && Objects.equals(language, that.language)
           && country == that.country
           && Objects.equals(area, that.area)
           && sex == that.sex
           && Objects.equals(referenceId, that.referenceId)
           && Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, name, latin, preferred, language, country, area, sex, referenceId, remarks);
  }

  @Override
  public String toString() {
    return "VernacularName{" + getId() + " " + name + "/" + language +  "}";
  }
}
