package life.catalogue.api.search;

import java.util.Objects;

import jakarta.ws.rs.QueryParam;

public class VernacularSearchRequest {
  
  @QueryParam("q")
  private String q;

  @QueryParam("language")
  private String language;

  /**
   * The sector key attached to a taxon. Synonyms inherit the key by their accepted taxon, but do not expose the key on the Synonym instance
   * itself.
   */
  @QueryParam("sectorKey")
  private Integer sectorKey;

  /**
   * The subject dataset key of the corresponding sector attached to a taxon. Synonyms inherit the key by their accepted taxon, but do not expose
   * the key on the Synonym instance itself.
   */
  @QueryParam("sectorDatasetKey")
  private Integer sectorDatasetKey;


  public static VernacularSearchRequest byQuery(String query) {
    VernacularSearchRequest q = new VernacularSearchRequest();
    q.q = query;
    return q;
  }
  
  public String getQ() {
    return q;
  }
  
  public void setQ(String q) {
    this.q = q;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public Integer getSectorDatasetKey() {
    return sectorDatasetKey;
  }

  public void setSectorDatasetKey(Integer sectorDatasetKey) {
    this.sectorDatasetKey = sectorDatasetKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VernacularSearchRequest)) return false;
    VernacularSearchRequest that = (VernacularSearchRequest) o;
    return Objects.equals(q, that.q) &&
      Objects.equals(language, that.language) &&
      Objects.equals(sectorKey, that.sectorKey) &&
      Objects.equals(sectorDatasetKey, that.sectorDatasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, language, sectorKey, sectorDatasetKey);
  }
}
