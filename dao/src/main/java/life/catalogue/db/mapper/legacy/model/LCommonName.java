package life.catalogue.db.mapper.legacy.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LCommonName implements LName {
  private String name;
  // API uses full english names: "language": "French",
  private String language;
  // API uses full english names: "country": "French Polynesia",
  private String country;
  private LSpeciesName acceptedName;
  private List<LReference> references;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @JsonProperty("name_status")
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
    return acceptedName == null ? null : acceptedName.getUrl();
  }

  @JsonProperty("source_database")
  public String getSourceDatabase() {
    return acceptedName == null ? null : acceptedName.getSourceDatabase();
  }

  @JsonProperty("source_database_url")
  public String getSourceDatabaseUrl() {
    return acceptedName == null ? null : acceptedName.getSourceDatabaseUrl();
  }

  @JsonProperty("accepted_name")
  public LSpeciesName getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(LSpeciesName acceptedName) {
    this.acceptedName = acceptedName;
  }

  public List<LReference> getReferences() {
    return references;
  }

  public void setReferences(List<LReference> references) {
    this.references = references;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LCommonName)) return false;
    LCommonName that = (LCommonName) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(language, that.language) &&
      Objects.equals(country, that.country) &&
      Objects.equals(acceptedName, that.acceptedName) &&
      Objects.equals(references, that.references);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, language, country, acceptedName, references);
  }
}
