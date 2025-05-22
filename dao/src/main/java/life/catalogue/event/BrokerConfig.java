package life.catalogue.event;

import jakarta.validation.constraints.NotNull;

public class BrokerConfig {

  @NotNull
  public String queueDir = "/tmp/clb-queue";

  @NotNull
  public String name = "main";
}
