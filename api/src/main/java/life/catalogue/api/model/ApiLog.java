package life.catalogue.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

public class ApiLog {

  private LocalDateTime date;
  private int duration; // in milliseconds
  private HttpMethod method;
  private String request;
  private Integer responseCode;
  private String agent;
  private Integer datasetKey;
  private Integer user;

  /**
   * https://en.wikipedia.org/wiki/HTTP#Request_methods
   */
  public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH,
    OTHER;

    public static HttpMethod from(String method) {
      if (StringUtils.isBlank(method)) return null;
      try {
        return valueOf(method.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return HttpMethod.OTHER;
      }
    }
  }

  public LocalDateTime getDate() {
    return date;
  }

  public void setDate(LocalDateTime date) {
    this.date = date;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public String getRequest() {
    return request;
  }

  public void setRequest(String request) {
    this.request = request;
  }

  public Integer getResponseCode() {
    return responseCode;
  }

  public void setResponseCode(Integer responseCode) {
    this.responseCode = responseCode;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

  public String getAgent() {
    return agent;
  }

  public void setAgent(String agent) {
    this.agent = agent;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getUser() {
    return user;
  }

  public void setUser(Integer user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ApiLog)) return false;

    ApiLog apiLog = (ApiLog) o;
    return duration == apiLog.duration &&
      Objects.equals(date, apiLog.date) &&
      method == apiLog.method &&
      Objects.equals(request, apiLog.request) &&
      Objects.equals(responseCode, apiLog.responseCode) &&
      Objects.equals(agent, apiLog.agent) &&
      Objects.equals(datasetKey, apiLog.datasetKey) &&
      Objects.equals(user, apiLog.user);
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, duration, method, request, responseCode, agent, datasetKey, user);
  }
}