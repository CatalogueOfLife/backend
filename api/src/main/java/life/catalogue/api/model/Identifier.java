package life.catalogue.api.model;

import life.catalogue.api.jackson.IdentifierSerde;

import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = IdentifierSerde.Serializer.class)
@JsonDeserialize(using = IdentifierSerde.Deserializer.class)
public class Identifier {
  private static final Pattern PARSER = Pattern.compile("^([a-zA-Z]+):(.+)$");

  public enum Scheme {
    DOI,
    HTTP,
    URN,
    LSID,

    TSN,
    IPNI,
    ZOOBANK,
    COL,
    GBIF;

    public String prefix() {
      return name().toLowerCase();
    }
  }


  private String scheme;
  private String id;

  public Identifier() {
  }

  public Identifier(Scheme scheme, String id) {
    this.scheme = scheme.prefix();
    this.id = id;
  }

  public Identifier(String scheme, String id) {
    this.scheme = scheme.toLowerCase().trim();
    this.id = id;
  }

  public Identifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier required");
    try {
      DOI doi = new DOI(identifier);
      this.scheme = Scheme.DOI.prefix();
      this.id = doi.getDoiName();

    } catch (IllegalArgumentException e) {
      // not a DOI
      var m = PARSER.matcher(identifier.trim());
      if (m.find()) {
        this.scheme = m.group(1).toLowerCase();
        this.id = m.group(2);
      } else {
        throw new IllegalArgumentException("A colon delimited scheme is required");
      }
    }
  }

  public String getScheme() {
    return scheme;
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Identifier)) return false;
    Identifier that = (Identifier) o;
    return Objects.equals(scheme, that.scheme) && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, id);
  }

  @Override
  public String toString() {
    return scheme + ':' + id;
  }
}
