package org.col.es.ddl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IndexTuning {

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
