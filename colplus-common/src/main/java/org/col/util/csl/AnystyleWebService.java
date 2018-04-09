package org.col.util.csl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.apache.commons.io.output.StringBuilderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a ruby web service which can be accessed at port 4567. The following is assumed:
 * <ol>
 * <li>Ruby is installed. Linux: sudo apt install ruby
 * <li>ruby-dev is installed. Linux: sudo apt install ruby-dev. This is necessary because anystyle
 * depends on wapity, which needs native C headers provided by ruby-dev,
 * <li>Anystyle is installed. Linux: sudo gem install anystyle. This may hang on installing the
 * doccumentation. In that case, try: sudo gem install anystyle --no-ri --no-rdoc
 * <li>Sinatra is installed. Linux: sudo gem install sinatra
 * </ol>
 */
final class AnystyleWebService {

  static final int HTTP_PORT = 4567;
  static final String QUERY_PARAM_REF = "ref";

  private static final Logger LOG = LoggerFactory.getLogger(AnystyleWebService.class);

  private final Process process;

  AnystyleWebService() {
    if (isRunning() || isListening()) {
      throw new IllegalStateException("Another instance of the Anystyle web service is still running");
    }
    try {
      process = new ProcessBuilder("ruby", "-e", getRubyCode()).start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    waitUntilReady();
  }

  void stop() {
    process.destroy();
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getRubyCode() {
    StringBuilderWriter w = new StringBuilderWriter(200);
    try (PrintWriter p = new PrintWriter(w)) {
      p.print("require 'anystyle/parser';");
      p.print("require 'sinatra';");
      p.print("get '/' do;");
      p.printf("Anystyle.parse(params['%s'], :bibtex).to_json;", QUERY_PARAM_REF);
      p.print("end");
    }
    return w.toString();
  }

  private static void waitUntilReady() {
    for (int i = 0; i < 5; i++) {
      if (isRunning() && isListening()) {
        return;
      }
      LOG.info("Waiting for Anystyle web service to come alive ...");
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    throw new IllegalStateException("Failed to start Anystyle web service");
  }

  private static boolean isRunning() {
    try {
      Process p = Runtime.getRuntime().exec("ps -ef");
      LineNumberReader lnr = new LineNumberReader(new InputStreamReader(p.getInputStream()));
      for (String line = lnr.readLine(); line != null; line = lnr.readLine()) {
        if (line.indexOf("require 'anystyle/parser'") != -1) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isListening() {
    InetSocketAddress addr = new InetSocketAddress("localhost", HTTP_PORT);
    try (Socket socket = new Socket()) {
      socket.connect(addr, 2000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

}
