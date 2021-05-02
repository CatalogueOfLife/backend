package life.catalogue.common.io;

import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class PortUtilsTest {
  
  @Test
  public void findFreePort() throws IOException {
    Set<Integer> ports = new HashSet<>();
    assertTrue(ports.add(PortUtils.findFreePort()));
    assertTrue(ports.add(PortUtils.findFreePort()));
    assertTrue(ports.add(PortUtils.findFreePort()));
  }
}