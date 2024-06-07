package life.catalogue.dw.health;


import com.codahale.metrics.health.HealthCheck;
import com.github.dockerjava.api.DockerClient;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.matching.DockerConfig;
import life.catalogue.matching.NameIndex;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

/**
 * Checks that the docker environment is accessible.
 */
public class DockerHealthCheck extends HealthCheck {

  private final DockerClient docker;
  private final DockerConfig cfg;

  public DockerHealthCheck(DockerClient docker, DockerConfig cfg) {
    this.docker = docker;
    this.cfg = cfg;
  }
  
  @Override
  protected Result check() throws Exception {
    try {
      var info = docker.infoCmd().exec();
      return Result.healthy("docker host %s accessible. Running %s containers", cfg.host, info.getContainers());

    } catch (Exception e) {
      return Result.builder()
        .unhealthy(e)
        .withMessage("Cannot access docker host "+ cfg.host)
        .build();
    }
  }
}