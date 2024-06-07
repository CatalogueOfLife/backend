package life.catalogue.matching;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;

import javax.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Configs to inform about the docker environment to be used
 * for starting and accessing containers.
 */
public class DockerConfig {

  @NotNull
  public String registry = "docker.gbif.org";

  @NotNull
  public String host = "localhost";

  public DockerClientConfig toDockerCfg() {
    return DefaultDockerClientConfig.createDefaultConfigBuilder()
      .withDockerHost(host)
      .withRegistryUrl(registry)
      .build();
  }
}
