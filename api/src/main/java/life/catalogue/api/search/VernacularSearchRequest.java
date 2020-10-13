package life.catalogue.api.search;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class VernacularSearchRequest {
  
  @QueryParam("q")
  private String q;

  @QueryParam("language")
  private String language;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VernacularSearchRequest)) return false;
    VernacularSearchRequest that = (VernacularSearchRequest) o;
    return Objects.equals(q, that.q) &&
      Objects.equals(language, that.language);
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, language);
  }
}
