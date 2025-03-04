package life.catalogue.command;

import life.catalogue.WsServerConfig;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;

import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command that connects to configured docker registry and lists all available images
 */
public class DockerCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(DockerCmd.class);
  private static final String ARG_QUERY = "q";

  public DockerCmd() {
    super("docker", "List all images from the configured docker registry");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("-"+ARG_QUERY)
      .dest(ARG_QUERY)
      .type(String.class)
      .required(false)
      .help("query to search for images in the registry");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("List all images from the configured docker registry %s.\n", cfg.docker.registry);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    DockerClient docker = cfg.docker.newDockerClient();

    String query = namespace.getString(ARG_QUERY);
    List<?> result;
    if (query == null) {
      result = docker.listImagesCmd().exec();
      System.out.println(String.format("Found %s images on docker host %s\n", result.size(), cfg.docker.host));
    } else {
      result = docker.searchImagesCmd(query).exec();
      System.out.println(String.format("Found %s images on docker registry %s for query >%s<\n", result.size(), cfg.docker.registry, query));
    }

    for (var obj : result) {
      System.out.println(obj);
    }
  }

}
