package life.catalogue.es;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.validation.constraints.NotNull;

public class EsConfig {

  /**
   * Name of the Elasticsearch index for name usages
   */
  public static final String ES_INDEX_NAME_USAGE = "nu";

  /**
   * Environment to prefix indices with to be able to share a single ES instance with multiple CoL+ installations. prod or
   * dev are sensible values.
   */
  @NotNull
  public String environment = "local";

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
   * Configuration settings for the name usage index
   */
  @NotNull
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
   * @return the index name prefixed with the configured environment
   */
  public String indexName(String name) {
    return environment == null || environment.equals("<none>") ? name : environment + "-" + name;
  }

  /**
   * An ES expression to match all index names of the configured environment
   */
  public String allIndices() {
    return environment == null ? "*" : environment + "-*";
  }

  @JsonIgnore
  public boolean isEmpty() {
    return hosts == null || nameUsage == null;
  }

}
