package life.catalogue.command;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import io.dropwizard.setup.Bootstrap;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.PgConfig;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;

/**
 * Command that connects to configured docker registry and lists all available images
 */
public class DockerCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DockerCmd.class);
  public DockerCmd() {
    super("docker", "List all images from the configured docker registry");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("List all images from the configured docker registry %s.\n", cfg.docker.registry);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    DockerClientConfig dockerCfg = cfg.docker.toDockerCfg();
    DockerClient docker = DockerClientBuilder.getInstance(dockerCfg).build();
    var images = docker.listImagesCmd().exec();
    System.out.println(String.format("Found %s images on docker registry %s\n", images.size(), cfg.docker.registry));
    for (var img : images) {
      System.out.println(img);
    }
  }

}
