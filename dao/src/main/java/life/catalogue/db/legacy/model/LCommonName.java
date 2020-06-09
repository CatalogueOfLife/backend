package life.catalogue.db.legacy.model;

import java.util.Objects;

public class LCommonName implements LName {
  private String name;
  // API uses full english names: "language": "French",
  private String language;
  // API uses full english names: "country": "French Polynesia",
  private String country;
  private LSpeciesName acceptedName;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  public String getNameStatus() {
    return "common name";
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getUrl() {
    return acceptedName.getUrl();
  }

  public String getSourceDatabase() {
    return acceptedName.getSourceDatabase();
  }

  public String getSourceDatabaseUrl() {
    return acceptedName.getSourceDatabaseUrl();
  }

  public LSpeciesName getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(LSpeciesName acceptedName) {
    this.acceptedName = acceptedName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LCommonName)) return false;
    LCommonName that = (LCommonName) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(language, that.language) &&
      Objects.equals(country, that.country) &&
      Objects.equals(acceptedName, that.acceptedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, language, country, acceptedName);
  }
}
