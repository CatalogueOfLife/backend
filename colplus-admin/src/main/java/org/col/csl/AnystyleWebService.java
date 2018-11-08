package org.col.csl;

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
 * <li>ruby-dev is installed. Linux: sudo apt install ruby-dev. This is necessary because
 * AnystyleParser depends on wapity, which needs native C headers provided by ruby-dev,
 * <li>Anystyle is installed. Linux: sudo gem install AnystyleParser. This may hang on installing
 * the doccumentation. In that case, try: sudo gem install anystyle-parser --no-ri --no-rdoc
 * <li>Sinatra is installed. Linux: sudo gem install sinatra
 * </ol>
 */
@Deprecated
class AnystyleWebService {

  static final int HTTP_PORT = 4567;
  static final String QUERY_PARAM_REF = "ref";

  private static final Logger LOG = LoggerFactory.getLogger(AnystyleWebService.class);

  private Process process;

  AnystyleWebService() {
  }

  void start() throws IOException, InterruptedException {
    if (isRunning() || isListening()) {
      throw new IllegalStateException(
          "Another instance of the Anystyle web service is still running");
    }
    LOG.info("Starting Anystyle web service: ruby -e \"{}\"", getRubyCode());
    process = new ProcessBuilder("ruby", "-e", getRubyCode()).start();
    waitUntilReady();
  }

  void stop() throws InterruptedException {
    if (process != null) {
      process.destroy();
      process.waitFor();
      LOG.info("Anystyle web service stopped");
    }
  }

  static boolean isRunning() {
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

  static boolean isListening() {
    InetSocketAddress addr = new InetSocketAddress("localhost", HTTP_PORT);
    try (Socket socket = new Socket()) {
      socket.connect(addr, 2000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static String getRubyCode() {
    StringBuilderWriter w = new StringBuilderWriter(200);
    try (PrintWriter p = new PrintWriter(w)) {
      p.print("require 'anystyle/parser';");
      p.print("require 'sinatra';");
      p.print("get '/' do;");
      p.printf("Anystyle.parse(params['%s'], :citeproc).to_json;", QUERY_PARAM_REF);
      p.print("end");
    }
    return w.toString();
  }

  private static void waitUntilReady() throws InterruptedException {
    for (int i = 0; i < 5; i++) {
      if (isRunning() && isListening()) {
        LOG.info("Anystyle web service ready");
        return;
      }
      LOG.info("Waiting for Anystyle web service to come alive ...");
      Thread.sleep(1000);
    }
    throw new IllegalStateException("Failed to start Anystyle web service");
  }

}
