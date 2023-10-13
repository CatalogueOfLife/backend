package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class ApiAnalytics implements Entity<Long> {

  private Long key;
  private LocalDateTime from;
  private LocalDateTime to;
  private Integer requestCount;
  private Map<Country, Integer> countryAgg;
  private Map<Integer, Integer> responseCodeAgg;
  private Map<String, Integer> agentAgg;
  private Map<String, Integer> requestPatternAgg;
  private Map<String, Integer> otherMetrics;

  @Override
  public Long getKey() {
    return key;
  }

  @Override
  public void setKey(Long key) {
    this.key = key;
  }

  public LocalDateTime getFrom() {
    return from;
  }

  public void setFrom(LocalDateTime from) {
    this.from = from;
  }

  public LocalDateTime getTo() {
    return to;
  }

  public void setTo(LocalDateTime to) {
    this.to = to;
  }

  public Integer getRequestCount() {
    return requestCount;
  }

  public void setRequestCount(Integer requestCount) {
    this.requestCount = requestCount;
  }

  public Map<Country, Integer> getCountryAgg() {
    return countryAgg;
  }

  public void setCountryAgg(Map<Country, Integer> countryAgg) {
    this.countryAgg = countryAgg;
  }

  public Map<Integer, Integer> getResponseCodeAgg() {
    return responseCodeAgg;
  }

  public void setResponseCodeAgg(Map<Integer, Integer> responseCodeAgg) {
    this.responseCodeAgg = responseCodeAgg;
  }

  public Map<String, Integer> getAgentAgg() {
    return agentAgg;
  }

  public void setAgentAgg(Map<String, Integer> agentAgg) {
    this.agentAgg = agentAgg;
  }

  public Map<String, Integer> getRequestPatternAgg() {
    return requestPatternAgg;
  }

  public void setRequestPatternAgg(Map<String, Integer> requestPatternAgg) {
    this.requestPatternAgg = requestPatternAgg;
  }

  public Map<String, Integer> getOtherMetrics() {
    return otherMetrics;
  }

  public void setOtherMetrics(Map<String, Integer> otherMetrics) {
    this.otherMetrics = otherMetrics;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApiAnalytics)) return false;
    ApiAnalytics that = (ApiAnalytics) o;
    return Objects.equals(key, that.key)
           && Objects.equals(from, that.from)
           && Objects.equals(to, that.to)
           && Objects.equals(requestCount, that.requestCount)
           && Objects.equals(countryAgg, that.countryAgg)
           && Objects.equals(responseCodeAgg, that.responseCodeAgg)
           && Objects.equals(agentAgg, that.agentAgg)
           && Objects.equals(requestPatternAgg, that.requestPatternAgg)
           && Objects.equals(otherMetrics, that.otherMetrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, from, to, requestCount, countryAgg, responseCodeAgg, agentAgg, requestPatternAgg, otherMetrics);
  }
}