package life.catalogue.es.ddl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An awkwardly named object within an Elasticsearch index definition ("index" is a heavily overloaded term in
 * Elasticsearch's terminology). In this case it's a kind of configuration object allowing you to fine-tune several
 * properties of the index. We have only included those that we are interested in.
 *
 */
public class Index {

  @JsonProperty("refresh_interval")
  private int refreshInterval;
  @JsonProperty("max_result_window")
  private int maxResultWindow;
  @JsonProperty("number_of_shards")
  private int numberOfShards;
  @JsonProperty("number_of_replicas")
  private int numberOfReplicas;

  public int getRefreshInterval() {
    return refreshInterval;
  }

  public void setRefreshInterval(int refreshInterval) {
    this.refreshInterval = refreshInterval;
  }

  public int getMaxResultWindow() {
    return maxResultWindow;
  }

  public void setMaxResultWindow(int maxResultWindow) {
    this.maxResultWindow = maxResultWindow;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

}
