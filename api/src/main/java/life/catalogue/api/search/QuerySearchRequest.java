package life.catalogue.api.search;

import jakarta.ws.rs.QueryParam;

public class QuerySearchRequest {
  
  @QueryParam("q")
  private String query;

  public QuerySearchRequest() {
  }

  public QuerySearchRequest(String query) {
    this.query = query;
  }

  public String getQ() {
    return query;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }
}
