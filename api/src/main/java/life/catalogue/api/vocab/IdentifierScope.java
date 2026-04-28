package life.catalogue.api.vocab;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in the identifier scope registry, defining a single CURIE-style scope
 * such as "col", "gbif" or "worms" together with optional metadata.
 * Loaded from the YAML registry by {@link IdentifierScopes}.
 */
public class IdentifierScope {

  private String scope;
  private String title;
  private String description;
  private String link;
  private String resolver;
  private String example;
  private String regex;
  private Integer datasetKey;

  public IdentifierScope() {}

  @JsonCreator
  public IdentifierScope(@JsonProperty("scope") String scope,
                         @JsonProperty("title") String title,
                         @JsonProperty("description") String description,
                         @JsonProperty("link") String link,
                         @JsonProperty("resolver") String resolver,
                         @JsonProperty("example") String example,
                         @JsonProperty("regex") String regex,
                         @JsonProperty("datasetKey") Integer datasetKey) {
    this.scope = scope == null ? null : scope.toLowerCase().trim();
    this.title = title;
    this.description = description;
    this.link = link;
    this.resolver = resolver;
    this.example = example;
    this.regex = regex;
    this.datasetKey = datasetKey;
  }

  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope == null ? null : scope.toLowerCase().trim(); }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getLink() { return link; }
  public void setLink(String link) { this.link = link; }

  public String getResolver() { return resolver; }
  public void setResolver(String resolver) { this.resolver = resolver; }

  public String getExample() { return example; }
  public void setExample(String example) { this.example = example; }

  public String getRegex() { return regex; }
  public void setRegex(String regex) { this.regex = regex; }

  public Integer getDatasetKey() { return datasetKey; }
  public void setDatasetKey(Integer datasetKey) { this.datasetKey = datasetKey; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdentifierScope)) return false;
    IdentifierScope that = (IdentifierScope) o;
    return Objects.equals(scope, that.scope)
      && Objects.equals(title, that.title)
      && Objects.equals(description, that.description)
      && Objects.equals(link, that.link)
      && Objects.equals(resolver, that.resolver)
      && Objects.equals(example, that.example)
      && Objects.equals(regex, that.regex)
      && Objects.equals(datasetKey, that.datasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scope, title, description, link, resolver, example, regex, datasetKey);
  }

  @Override
  public String toString() {
    return scope + " - " + title;
  }
}
