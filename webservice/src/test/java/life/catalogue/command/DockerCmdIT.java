package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class DockerCmdIT extends CmdTestBase {

  public DockerCmdIT() {
    super(DockerCmd::new);
  }
  
  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("docker", "--prompt", "0", "-q", "docker.gbif.org/clb-listtools:0.0.1").isEmpty());
  }

}