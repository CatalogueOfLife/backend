package life.catalogue.api.search;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class EstimateSearchRequest extends BaseDecisionSearchRequest {

  @QueryParam("min")
  private Integer min;
  
  @QueryParam("max")
  private Integer max;

  public static EstimateSearchRequest byProject(int datasetKey){
    EstimateSearchRequest req = new EstimateSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }

  public Integer getMin() {
    return min;
  }

  public void setMin(Integer min) {
    this.min = min;
  }

  public Integer getMax() {
    return max;
  }

  public void setMax(Integer max) {
    this.max = max;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EstimateSearchRequest)) return false;
    EstimateSearchRequest that = (EstimateSearchRequest) o;
    return Objects.equals(min, that.min) &&
      Objects.equals(max, that.max);
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max);
  }
}
