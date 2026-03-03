package life.catalogue.config;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class EsConfig {

  /**
   * Comma separated list of host:port entries for each ES node
   */
  @NotNull
  public String hosts = "localhost:9200";

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
  public IndexConfig index;

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
  public int socketTimeout = 900_000;

  /**
   * If true, connect to Elasticsearch via HTTPS (skipping certificate verification).
   * Required when the ES node uses TLS, e.g. the default Docker image since ES 8.
   */
  public boolean ssl = false;

  /**
   * Maximum number of connections per ES node (route).
   * Increase when heavy concurrent indexing saturates the default pool.
   */
  @Min(1)
  public int maxConnPerRoute = 10;

  /**
   * Maximum total connections across all ES nodes.
   * Should be >= maxConnPerRoute * number of nodes.
   */
  @Min(1)
  public int maxConnTotal = 30;

  /**
   * Optional path prefix for every request, e.g. "/es" when Elasticsearch is behind
   * a reverse proxy that exposes it under a sub-path.
   */
  @Nullable
  public String pathPrefix;

  /**
   * Number of parallel threads to use when indexing all datasets
   */
  @Min(1)
  public int indexingThreads = 4;

  @JsonIgnore
  public boolean isEmpty() {
    return hosts == null || index == null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("hosts", hosts)
      .add("user", user)
      .add("index", index)
      .add("threads", indexingThreads)
      .toString();
  }
}
