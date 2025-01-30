package life.catalogue.matching;

import com.github.dockerjava.api.model.Info;

import life.catalogue.common.lang.Exceptions;

import org.junit.Test;

import jakarta.ws.rs.ProcessingException;

import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.Assert.*;

public class DockerConfigTest {

  @Test(expected = UnknownHostException.class)
  public void newDockerClient() throws Throwable {
    var cfg = new DockerConfig();
    cfg.host = "tcp://noexist.gbif.org:2345";
    var docker = cfg.newDockerClient();
    try {
      Info info = docker.infoCmd().exec();
      System.out.println(info);
    } catch (RuntimeException e) {
      throw Exceptions.getRootCause(e);
    }
    fail("should throw");
  }
}