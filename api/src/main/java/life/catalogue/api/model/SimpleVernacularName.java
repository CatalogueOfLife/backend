package life.catalogue.api.model;

import life.catalogue.api.vocab.Language;

import java.util.Objects;

import jakarta.validation.constraints.Size;

public class SimpleVernacularName {

  private String name;
  @Size(min = 3, max = 3)
  private String language;

  public SimpleVernacularName() {
  }

  public SimpleVernacularName(String language, String name) {
    this.language = language;
    this.name = name;
  }

  public SimpleVernacularName(VernacularName vn) {
    this.name = vn.getName();
    this.language = vn.getLanguage();
  }

  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
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


  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleVernacularName)) return false;
    if (!super.equals(o)) return false;

    SimpleVernacularName that = (SimpleVernacularName) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(language, that.language);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, language);
  }

  @Override
  public String toString() {
    return language + ":" + name;
  }
}
