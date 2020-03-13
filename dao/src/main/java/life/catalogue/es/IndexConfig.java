package life.catalogue.es;

import javax.validation.constraints.NotNull;

public class IndexConfig {

  @NotNull
  public String name;

  public int numShards = 1;

  public int numReplicas = 0;

  @Override
  public String toString() {
    return "IndexConfig{" + name + "}";
  }
}
