package life.catalogue.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public class IndexConfig {

  @NotNull
  public String name;

  @Min(1)
  public int numShards = 1;

  @Min(0)
  public int numReplicas = 0;

  @Override
  public String toString() {
    return "IndexConfig{" + name + "}";
  }
}
