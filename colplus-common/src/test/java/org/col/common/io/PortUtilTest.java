package org.col.common.io;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PortUtilTest {

  @Test
  public void findFreePort() throws IOException {
    Set<Integer> ports = new HashSet<>();
    assertTrue( ports.add(PortUtil.findFreePort()) );
    assertTrue( ports.add(PortUtil.findFreePort()) );
    assertTrue( ports.add(PortUtil.findFreePort()) );
  }
}