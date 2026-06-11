package life.catalogue.api.vocab;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  /** lazily compiled reverse of the resolver template, used to extract a local id from a full URL.
   * volatile because registry scopes are shared singletons read concurrently by sync threads. */
  private transient volatile Pattern extractor;
  private transient volatile boolean extractorBuilt;

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
  public void setResolver(String resolver) { this.resolver = resolver; this.extractorBuilt = false; }

  public String getExample() { return example; }
  public void setExample(String example) { this.example = example; }

  public String getRegex() { return regex; }
  public void setRegex(String regex) { this.regex = regex; this.extractorBuilt = false; }

  public Integer getDatasetKey() { return datasetKey; }
  public void setDatasetKey(Integer datasetKey) { this.datasetKey = datasetKey; }

  /**
   * Extracts the local identifier from a raw value that may be a full resolver URL.
   * Some sources publish website URLs (e.g. {@code https://www.inaturalist.org/taxa/42007})
   * instead of the plain local id ({@code 42007}). If the raw value matches this scope's
   * {@link #resolver} template, the embedded id is returned; otherwise the value is returned
   * unchanged. The {@code http}/{@code https} scheme is matched leniently (a common mistake).
   */
  public String extractId(String rawId) {
    if (rawId == null) return null;
    Pattern p = extractor();
    if (p != null) {
      Matcher m = p.matcher(rawId.trim());
      if (m.matches()) {
        return m.group(1);
      }
    }
    return rawId;
  }

  private Pattern extractor() {
    if (!extractorBuilt) {
      extractor = buildExtractor();
      extractorBuilt = true;
    }
    return extractor;
  }

  private static final Pattern SCHEME = Pattern.compile("^https?://");

  private Pattern buildExtractor() {
    if (resolver == null) return null;
    int idx = resolver.indexOf("{id}");
    if (idx < 0) return null;
    String prefix = resolver.substring(0, idx);
    String suffix = resolver.substring(idx + "{id}".length());
    return Pattern.compile("^" + literalWithLenientScheme(prefix) + "(" + idGroupRegex() + ")" + Pattern.quote(suffix) + "$");
  }

  /** Quotes a literal URL part, but turns a leading http(s) scheme into a lenient {@code https?://} so http and https both match. */
  private static String literalWithLenientScheme(String literal) {
    Matcher m = SCHEME.matcher(literal);
    if (m.find()) {
      return "https?://" + Pattern.quote(literal.substring(m.end()));
    }
    return Pattern.quote(literal);
  }

  /** The id capture group: reuses the scope {@link #regex} (anchors stripped) when present, else a reluctant catch-all. */
  private String idGroupRegex() {
    if (regex != null && !regex.isBlank()) {
      String r = regex.trim();
      if (r.startsWith("^")) r = r.substring(1);
      if (r.endsWith("$")) r = r.substring(0, r.length() - 1);
      if (!r.isBlank()) return r;
    }
    return ".+?";
  }

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
