package life.catalogue.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class BrokerConfig {

  @NotNull
  public String queueDir = "/tmp/clb-queue";

  @NotNull
  public String name = "main";

  @Min(1)
  public long pollingLatency = 10; // in millis

  @Min(1)
  public int poolSize = 2; // kryo pool

  @Min(64)
  public int bufferSize = 1024; // kryo buffer size

}
