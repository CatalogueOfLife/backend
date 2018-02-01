package org.col.dw.cors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import java.util.concurrent.TimeUnit;

public class CorsConfiguration {

  private static String DEFAULT_ALLOWED_ORIGINS = "*";
  private static String DEFAULT_URL_MAPPING = "/*";
  private static String DEFAULT_ALLOWED_HEADERS = "X-Requested-With,Content-Type,Accept,Origin";
  private static String DEFAULT_ALLOWED_METHODS = "OPTIONS,GET,PUT,POST,DELETE,HEAD,PATCH";
  private static long DEFAULT_PREFLIGHT_MAX_AGE_IN_SECONDS = TimeUnit.MINUTES.toSeconds(30);

  @NotEmpty
  private String allowedOrigins = DEFAULT_ALLOWED_ORIGINS;

  @NotEmpty
  private String allowedHeaders = DEFAULT_ALLOWED_HEADERS;

  @NotEmpty
  private String allowedMethods = DEFAULT_ALLOWED_METHODS;

  @NotEmpty
  private String urlMapping = DEFAULT_URL_MAPPING;

  @Range(min = 0, max = Long.MAX_VALUE)
  private long maxAgeInSeconds = DEFAULT_PREFLIGHT_MAX_AGE_IN_SECONDS;

  public String getAllowedOrigins() {
    return allowedOrigins;
  }

  @JsonProperty("allowedOrigins")
  public void setAllowedOrigins(String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public String getAllowedHeaders() {
    return allowedHeaders;
  }

  @JsonProperty("allowedHeaders")
  public void setAllowedHeaders(String allowedHeaders) {
    this.allowedHeaders = allowedHeaders;
  }

  public String getAllowedMethods() {
    return allowedMethods;
  }

  @JsonProperty("allowedMethods")
  public void setAllowedMethods(String allowedMethods) {
    this.allowedMethods = allowedMethods;
  }

  public String getUrlMapping() {
    return urlMapping;
  }

  @JsonProperty("urlMapping")
  public void setUrlMapping(String urlMapping) {
    this.urlMapping = urlMapping;
  }

  @JsonProperty("preflightMaxAge")
  public long getMaxAgeInSeconds() {
    return maxAgeInSeconds;
  }

  @JsonProperty("preflightMaxAge")
  public void setMaxAgeInSeconds(long maxAgeInSeconds) {
    this.maxAgeInSeconds = maxAgeInSeconds;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.NO_CLASS_NAME_STYLE);
  }
}