package life.catalogue.analytics;


import life.catalogue.api.vocab.Country;

import java.util.Map;
import java.util.Objects;

public class ExternalRequestsMetrics {

  private long requestsCount;
  private Map<Country, Long> geolocationAgg;
  private Map<Integer, Long> responseCodeAgg;
  private Map<String, Long> agentAgg;
  private Map<String, Long> requestPatternAgg;

  public long getRequestsCount() {
    return requestsCount;
  }

  public void setRequestsCount(long requestsCount) {
    this.requestsCount = requestsCount;
  }

  public Map<Country, Long> getGeolocationAgg() {
    return geolocationAgg;
  }

  public void setGeolocationAgg(Map<Country, Long> geolocationAgg) {
    this.geolocationAgg = geolocationAgg;
  }

  public Map<Integer, Long> getResponseCodeAgg() {
    return responseCodeAgg;
  }

  public void setResponseCodeAgg(Map<Integer, Long> responseCodeAgg) {
    this.responseCodeAgg = responseCodeAgg;
  }

  public Map<String, Long> getAgentAgg() {
    return agentAgg;
  }

  public void setAgentAgg(Map<String, Long> agentAgg) {
    this.agentAgg = agentAgg;
  }

  public Map<String, Long> getRequestPatternAgg() {
    return requestPatternAgg;
  }

  public void setRequestPatternAgg(Map<String, Long> requestPatternAgg) {
    this.requestPatternAgg = requestPatternAgg;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExternalRequestsMetrics)) return false;
    ExternalRequestsMetrics that = (ExternalRequestsMetrics) o;
    return requestsCount == that.requestsCount
           && Objects.equals(geolocationAgg, that.geolocationAgg)
           && Objects.equals(responseCodeAgg, that.responseCodeAgg)
           && Objects.equals(agentAgg, that.agentAgg)
           && Objects.equals(requestPatternAgg, that.requestPatternAgg);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestsCount, geolocationAgg, responseCodeAgg, agentAgg, requestPatternAgg);
  }
}