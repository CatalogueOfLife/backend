package org.col.common.io;

import java.io.IOException;
import java.net.ServerSocket;

public class PortUtil {
  
  public static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }
}
