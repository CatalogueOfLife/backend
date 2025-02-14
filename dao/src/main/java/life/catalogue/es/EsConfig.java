package life.catalogue.es;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EsConfig {

  /**
   * Comma separated list of hosts with ES nodes
   */
  @NotNull
  public String hosts = "localhost";

  /**
   * Comma separated list of ports matching the hosts list
   */
  @NotNull
  public String ports = "9200";

  /**
   * Username to use for authentication.
   * If NULL no auth will be added to the client.
   */
  public String user;

  public String password;

  /**
   * Configuration settings for the name usage index
   */
  @Nullable
  public IndexConfig nameUsage;

  /**
   * Determines the timeout in milliseconds until a RESTClient connection is established. A timeout value of zero is
   * interpreted as an infinite timeout. See
   * https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/_timeouts.html
   *
   */
  public int connectTimeout = 10000;

  /**
   * Defines the RESTClient socket timeout in milliseconds, which is the timeout for waiting for data or, put differently,
   * a maximum period inactivity between two consecutive data packets). See
   * https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/_timeouts.html
   *
   * Defaults here to 15 minutes = 900.000ms
   */
  public int socketTimeout = 900000;

  /**
   * Number of parallel threads to use when indexing all datasets
   */
  @Min(1)
  public int indexingThreads = 4;

  @JsonIgnore
  public boolean isEmpty() {
    return hosts == null || nameUsage == null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("hosts", hosts)
      .add("ports", ports)
      .add("user", user)
      .add("index", nameUsage)
      .add("threads", indexingThreads)
      .toString();
  }
}
