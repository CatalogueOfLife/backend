package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.jackson.IdentifierSerde;

import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.apache.commons.lang3.StringUtils;

@JsonSerialize(using = IdentifierSerde.Serializer.class)
@JsonDeserialize(using = IdentifierSerde.Deserializer.class)
public class Identifier {
  private static final Pattern SCOPE_PARSER = Pattern.compile("^([a-zA-Z]+):(.+)$");

  public enum Scope {
    LOCAL,
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


  private String scope;
  private String id;

  public static Identifier parse(String identifier) {
    identifier = StringUtils.trimToNull(identifier);
    Objects.requireNonNull(identifier, "identifier required");
    // URN, doi or http(s) schemes can be dois - prefer those
    if (DOI.PARSER.matcher(identifier).find() || DOI.HTTP.matcher(identifier).find()) {
      try {
        return new Identifier(new DOI(identifier));
      } catch (IllegalArgumentException e) {
        // continue to try as a regular identifier
      }
    }

    var m = SCOPE_PARSER.matcher(identifier);
    if (m.find()) {
      return new Identifier(m.group(1), m.group(2));
    } else {
      return new Identifier(Scope.LOCAL, identifier);
    }
  }

  public Identifier() {
  }

  public Identifier(Scope scope, String id) {
    this.scope = scope.prefix();
    this.id = id.trim();
  }

  public Identifier(String scope, String id) {
    this.scope = scope.toLowerCase().trim();
    this.id = id.trim();
  }

  public Identifier(DOI doi) {
    this.scope = Scope.DOI.prefix();
    this.id = doi.getDoiName();
  }

  @JsonIgnore
  public boolean isLocal() {
    return Objects.equals(scope, Scope.LOCAL.prefix());
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
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
    return Objects.equals(scope, that.scope) && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scope, id);
  }

  @Override
  public String toString() {
    return scope + ':' + id;
  }
}
