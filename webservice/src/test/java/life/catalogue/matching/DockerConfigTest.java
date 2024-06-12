package life.catalogue.matching;

import org.junit.Test;

import jakarta.ws.rs.ProcessingException;

import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.Assert.*;

public class DockerConfigTest {

  @Test(expected = ProcessingException.class)
  public void newDockerClient() {
    var cfg = new DockerConfig();
    cfg.host = "tcp://noexist.gbif.org:2345";
    var docker = cfg.newDockerClient();
    var info = docker.infoCmd().exec();
    System.out.println(info);
  }
}