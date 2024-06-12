package life.catalogue.matching;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.jaxrs.JerseyDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import jakarta.validation.constraints.NotNull;

/**
 * Configs to inform about the docker environment to be used
 * for starting and accessing containers.
 */
public class DockerConfig {

  @NotNull
  public String host = "unix:///var/run/docker.sock"; // tcp://docker.baeldung.com:2376

  @NotNull
  public String registry = "docker.gbif.org";

  public String registryUsername;
  public String registryPassword;

  public DockerClient newDockerClient() {
    var dcfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
      .withDockerHost(host)
      .withRegistryUrl(registry)
      .withRegistryUsername(registryUsername)
      .withRegistryPassword(registryPassword)
      .build();

    DockerHttpClient hcl = new ApacheDockerHttpClient.Builder()
      .dockerHost(dcfg.getDockerHost())
      .build();

    return DockerClientBuilder.getInstance(dcfg)
      .withDockerHttpClient(hcl)
      .build();
  }
}
