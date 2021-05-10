package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class Description {

  private String description;
  private DescriptionType descriptionType;
  private String lang;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public DescriptionType getDescriptionType() {
    return descriptionType;
  }

  public void setDescriptionType(DescriptionType descriptionType) {
    this.descriptionType = descriptionType;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Description)) return false;
    Description that = (Description) o;
    return Objects.equals(description, that.description) && descriptionType == that.descriptionType && Objects.equals(lang, that.lang);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, descriptionType, lang);
  }
}
