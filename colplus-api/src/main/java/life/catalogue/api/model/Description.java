package life.catalogue.api.model;

import java.util.Objects;
import javax.validation.constraints.Size;

import life.catalogue.api.vocab.Language;
import life.catalogue.api.vocab.TextFormat;

public class Description extends DatasetScopedEntity<Integer> implements Referenced, VerbatimEntity {
  private Integer verbatimKey;
  private String category;
  private TextFormat format;
  private String description;
  @Size(min = 3, max = 3)
  private String language;
  private String referenceId;
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public String getCategory() {
    return category;
  }
  
  public void setCategory(String category) {
    this.category = category;
  }
  
  public TextFormat getFormat() {
    return format;
  }
  
  public void setFormat(TextFormat format) {
    this.format = format;
  }
  
  public String getDescription() {
    return description;
  }
  
  public void setDescription(String description) {
    this.description = description;
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
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Description that = (Description) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(category, that.category) &&
        format == that.format &&
        Objects.equals(description, that.description) &&
        Objects.equals(language, that.language) &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, category, format, description, language, referenceId);
  }
}
