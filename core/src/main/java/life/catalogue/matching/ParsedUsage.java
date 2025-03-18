package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonInclude;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.ParsedName;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParsedUsage extends ParsedName {
  private String key;
  private String parentKey;
  private String name;
  private String canonicalName;
  private String authorship;
  private TaxonomicStatus status;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getParentKey() {
    return parentKey;
  }

  public void setParentKey(String parentKey) {
    this.parentKey = parentKey;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public void setCanonicalName(String canonicalName) {
    this.canonicalName = canonicalName;
  }

  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ParsedUsage that = (ParsedUsage) o;
    return Objects.equals(key, that.key) && Objects.equals(parentKey, that.parentKey) && Objects.equals(name, that.name) && Objects.equals(canonicalName, that.canonicalName) && Objects.equals(authorship, that.authorship) && status == that.status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, parentKey, name, canonicalName, authorship, status);
  }
}
